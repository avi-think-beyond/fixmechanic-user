const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "fixmechanic-firebase";
const MECHANIC_UID = "mechanic_123";
const MECHANIC_2_UID = "mechanic_456";
const CUSTOMER_UID = "customer_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any FixMechanic collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(MECHANIC_UID).get());
  await assertFails(unauthDb.collection("mechanicProfiles").doc(MECHANIC_UID).get());
  await assertFails(unauthDb.collection("serviceRequests").doc("req_1").get());
  await assertFails(unauthDb.collection("jobOffers").doc("offer_1").get());
});

test("Users collection: owner can create MECHANIC role, update own profile, but cannot escalate to ADMIN", async () => {
  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();

  await assertSucceeds(
    m1Db.collection("users").doc(MECHANIC_UID).set({
      uid: MECHANIC_UID,
      role: "MECHANIC",
      fullName: "Ramesh Sharma",
      disabled: false,
    })
  );

  await assertSucceeds(
    m1Db.collection("users").doc(MECHANIC_UID).update({
      fullName: "Ramesh K. Sharma",
    })
  );

  // Role escalation must fail
  await assertFails(
    m1Db.collection("users").doc(MECHANIC_UID).update({
      role: "ADMIN",
    })
  );

  // Reading another user's doc must fail
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();
  await assertFails(m2Db.collection("users").doc(MECHANIC_UID).get());
});

test("mechanicProfiles: mechanic can create PENDING profile, save address/map/PIN updates, and cannot self-approve or go online while PENDING", async () => {
  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();

  // Cannot create directly as APPROVED
  await assertFails(
    m1Db.collection("mechanicProfiles").doc(MECHANIC_UID).set({
      userId: MECHANIC_UID,
      fullName: "Ramesh Sharma",
      approvalStatus: "APPROVED",
      isOnline: true,
      isBusy: false,
    })
  );

  // Creating initial PENDING + offline profile succeeds
  await assertSucceeds(
    m1Db.collection("mechanicProfiles").doc(MECHANIC_UID).set({
      userId: MECHANIC_UID,
      fullName: "Ramesh Sharma",
      workshopName: "Sharma Auto Care",
      approvalStatus: "PENDING",
      isOnline: false,
      isBusy: false,
      baseCity: "Sector 56, Gurugram",
      pincode: "122002",
      servicePincodes: ["122002"],
      serviceRadiusKm: 15,
      lat: 28.4595,
      lng: 77.0266,
      hasCustomLocation: false,
    })
  );

  // Saving non-privileged profile details, map coordinates, pincode, and service radius succeeds
  await assertSucceeds(
    m1Db.collection("mechanicProfiles").doc(MECHANIC_UID).update({
      fullName: "Ramesh Sharma",
      workshopName: "Sharma Auto Works",
      baseCity: "Andheri West, Mumbai",
      serviceArea: "Andheri West, Mumbai",
      pincode: "400053",
      servicePincodes: ["400053", "400058"],
      serviceRadiusKm: 20,
      lat: 19.1363,
      lng: 72.8277,
      hasCustomLocation: true,
      currentLocation: {
        lat: 19.1363,
        lng: 72.8277,
        address: "Andheri West, Mumbai",
      },
    })
  );

  // Going online while still PENDING must fail
  await assertFails(
    m1Db.collection("mechanicProfiles").doc(MECHANIC_UID).update({
      isOnline: true,
    })
  );

  // Modifying privileged approvalStatus must fail
  await assertFails(
    m1Db.collection("mechanicProfiles").doc(MECHANIC_UID).update({
      approvalStatus: "APPROVED",
    })
  );
});

test("mechanicProfiles: APPROVED mechanic can toggle isOnline and update GPS currentLocation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("mechanicProfiles").doc(MECHANIC_UID).set({
      userId: MECHANIC_UID,
      fullName: "Ramesh Sharma",
      approvalStatus: "APPROVED",
      isOnline: false,
      isBusy: false,
    });
  });

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  await assertSucceeds(
    m1Db.collection("mechanicProfiles").doc(MECHANIC_UID).update({
      isOnline: true,
      currentLocation: { lat: 28.4595, lng: 77.0266 },
    })
  );

  // Another mechanic cannot update this mechanic's profile
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();
  await assertFails(
    m2Db.collection("mechanicProfiles").doc(MECHANIC_UID).update({
      isOnline: false,
    })
  );
});

test("serviceRequests & jobOffers: online mechanics read SEARCHING requests, first mechanic claims atomically, second mechanic is blocked", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("jobOffers").doc("offer_1").set({
      offerId: "offer_1",
      requestId: "req_1",
      mechanicId: MECHANIC_UID,
      status: "PENDING",
    });
    await context.firestore().collection("serviceRequests").doc("req_searching").set({
      requestId: "req_searching",
      customerId: CUSTOMER_UID,
      status: "SEARCHING",
      serviceCategory: "FLAT_TYRE",
      vehicleMakeModel: "Royal Enfield Classic 350",
      location: { lat: 19.3623573, lng: 72.8576045, landmark: "Yashwant Pride" },
    });
  });

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();

  // jobOffers remains mechanic-private and read-only
  await assertSucceeds(m1Db.collection("jobOffers").doc("offer_1").get());
  await assertFails(m2Db.collection("jobOffers").doc("offer_1").get());
  await assertFails(
    m1Db.collection("jobOffers").doc("offer_1").update({ status: "ACCEPTED" })
  );

  // Online mechanic can query SEARCHING serviceRequests directly
  const searchingSnap = await assertSucceeds(
    m1Db.collection("serviceRequests").where("status", "in", ["SEARCHING", "PENDING_DISPATCH"]).get()
  );
  if (searchingSnap.empty) {
    throw new Error("Expected online mechanic to read SEARCHING serviceRequests");
  }

  // First mechanic atomically claims the SEARCHING request -> succeeds
  await assertSucceeds(
    m1Db.collection("serviceRequests").doc("req_searching").update({
      status: "ASSIGNED",
      assignedMechanicId: MECHANIC_UID,
      mechanicId: MECHANIC_UID,
    })
  );

  // Second mechanic attempting to steal the now-ASSIGNED request -> blocked
  await assertFails(
    m2Db.collection("serviceRequests").doc("req_searching").get()
  );
  await assertFails(
    m2Db.collection("serviceRequests").doc("req_searching").update({
      status: "ASSIGNED",
      assignedMechanicId: MECHANIC_2_UID,
      mechanicId: MECHANIC_2_UID,
    })
  );

  // Assigned mechanic can advance job status to EN_ROUTE
  await assertSucceeds(
    m1Db.collection("serviceRequests").doc("req_searching").update({
      status: "EN_ROUTE",
    })
  );
});

test("jobChats & jobMessages: assigned participants can chat while OPEN, blocked when CLOSED or non-participant", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("jobChats").doc("req_1").set({
      requestId: "req_1",
      customerId: CUSTOMER_UID,
      mechanicId: MECHANIC_UID,
      status: "OPEN",
    });
    await context.firestore().collection("jobChats").doc("req_closed").set({
      requestId: "req_closed",
      customerId: CUSTOMER_UID,
      mechanicId: MECHANIC_UID,
      status: "CLOSED",
    });
  });

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();

  await assertSucceeds(
    m1Db.collection("jobChats").doc("req_1").collection("jobMessages").add({
      senderId: MECHANIC_UID,
      senderRole: "MECHANIC",
      text: "On my way!",
    })
  );

  // Non-participant cannot read or send messages
  await assertFails(
    m2Db.collection("jobChats").doc("req_1").collection("jobMessages").get()
  );

  // Sending message to CLOSED chat must fail
  await assertFails(
    m1Db.collection("jobChats").doc("req_closed").collection("jobMessages").add({
      senderId: MECHANIC_UID,
      senderRole: "MECHANIC",
      text: "After completion",
    })
  );
});

test("deviceTokens: mechanic can register and update own FCM device token", async () => {
  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();

  await assertSucceeds(
    m1Db.collection("deviceTokens").doc(`${MECHANIC_UID}_android`).set({
      userId: MECHANIC_UID,
      role: "MECHANIC",
      platform: "ANDROID",
      token: "fcm_sample_token",
    })
  );

  await assertFails(
    m2Db.collection("deviceTokens").doc(`${MECHANIC_UID}_android`).get()
  );
});
