const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const MECHANIC_UID = "mechanic_123";
const MECHANIC_2_UID = "mechanic_456";
const CUSTOMER_UID = "customer_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read mechanics or customer requests", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("mechanics").doc("some_id").get());
  await assertFails(unauthDb.collection("emergency_requests").doc("req_1").get());
  await assertFails(unauthDb.collection("service_requests").doc("req_2").get());
});

test("Mechanic can create and update own profile, but cannot update another mechanic", async () => {
  const mechanicDb = testEnv.authenticatedContext(MECHANIC_UID).firestore();

  await assertSucceeds(
    mechanicDb.collection("mechanics").doc(MECHANIC_UID).set({
      userId: MECHANIC_UID,
      fullName: "Ramesh Sharma",
      mobileNumber: "+919876543210",
      registrationStatus: "APPROVED",
      isOnline: true,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    mechanicDb.collection("mechanics").doc(MECHANIC_2_UID).set({
      userId: MECHANIC_2_UID,
      fullName: "Impostor",
      mobileNumber: "+919876543210",
      registrationStatus: "APPROVED",
      isOnline: true,
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Live dispatch query: mechanic can query SEARCHING service_requests and PENDING_DISPATCH emergency_requests", async () => {
  const custDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();
  await assertSucceeds(
    custDb.collection("service_requests").doc("srv_1").set({
      userId: CUSTOMER_UID,
      serviceType: "Tyre Puncture",
      vehicleType: "TWO_WHEELER",
      status: "SEARCHING_FOR_MECHANICS",
      mechanicId: "",
      latitude: 12.9716,
      longitude: 77.5946,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const res = await assertSucceeds(
    m1Db.collection("service_requests").where("status", "in", [
      "PENDING_DISPATCH",
      "SEARCHING",
      "SEARCHING_FOR_MECHANICS",
      "PENDING",
      "OPEN",
    ]).get()
  );
  if (res.empty) {
    throw new Error("Expected mechanic query to find customer SEARCHING_FOR_MECHANICS request");
  }

  // Unfiltered list query without open status predicate must fail
  await assertFails(m1Db.collection("service_requests").get());
});

test("Atomic claim: first mechanic wins across both emergency_requests and service_requests, second mechanic fails", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("service_requests").doc("srv_101").set({
      userId: CUSTOMER_UID,
      vehicleType: "TWO_WHEELER",
      serviceType: "PUNCTURE_TYRE",
      status: "SEARCHING",
      mechanicId: "",
      createdAt: new Date(Date.now() - 5000),
    });
  });

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();

  await assertSucceeds(
    m1Db.collection("service_requests").doc("srv_101").update({
      status: "ACCEPTED",
      assignedMechanicId: MECHANIC_UID,
      mechanicId: MECHANIC_UID,
      assignedMechanicName: "Ramesh Sharma",
      mechanicName: "Ramesh Sharma",
      assignedMechanicMobile: "+919876543210",
      mechanicPhone: "+919876543210",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    m2Db.collection("service_requests").doc("srv_101").update({
      status: "ACCEPTED",
      assignedMechanicId: MECHANIC_2_UID,
      mechanicId: MECHANIC_2_UID,
      assignedMechanicName: "Suresh Kumar",
      mechanicName: "Suresh Kumar",
      assignedMechanicMobile: "+919876543211",
      mechanicPhone: "+919876543211",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Assigned mechanic can advance status to EN_ROUTE and send private chat", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("service_requests").doc("srv_102").set({
      customerId: CUSTOMER_UID,
      vehicleType: "FOUR_WHEELER",
      problemCategory: "BATTERY_JUMPSTART",
      status: "ACCEPTED",
      assignedMechanicId: MECHANIC_UID,
      createdAt: new Date(Date.now() - 5000),
    });
  });

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();

  await assertSucceeds(
    m1Db.collection("service_requests").doc("srv_102").update({
      status: "EN_ROUTE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    m1Db.collection("service_requests").doc("srv_102").collection("messages").add({
      jobId: "srv_102",
      senderId: MECHANIC_UID,
      senderRole: "MECHANIC",
      senderName: "Ramesh Sharma",
      text: "On my way!",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Unassigned mechanic cannot read chat
  await assertFails(
    m2Db.collection("service_requests").doc("srv_102").collection("messages").get()
  );
});

test("Mechanic service area, pincode, and coordinates persist cleanly to mechanics and users for customer visibility", async () => {
  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const custDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();

  // Seed legacy mechanic doc lacking createdAt & some fields
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("mechanics").doc(MECHANIC_UID).set({
      userId: MECHANIC_UID,
      fullName: "Rajesh",
      mobileNumber: "",
      registrationStatus: "APPROVED",
      isOnline: true,
    });
  });

  // Update full location / service area / PIN compatibility fields on mechanics/{uid} & users/{uid}
  await assertSucceeds(
    m1Db.collection("mechanics").doc(MECHANIC_UID).set(
      {
        userId: MECHANIC_UID,
        role: "mechanic",
        baseCityLocality: "Andheri West, Mumbai",
        locality: "Andheri West, Mumbai",
        address: "Andheri West, Mumbai",
        serviceArea: "Andheri West, Mumbai",
        pincode: "400053",
        servicePincodes: ["400053", "400058"],
        serviceRadiusKm: 20,
        radiusKm: 20,
        latitude: 19.1363,
        longitude: 72.8277,
        lat: 19.1363,
        lng: 72.8277,
        shopLat: 19.1363,
        shopLng: 72.8277,
        hasCustomLocation: true,
        isAvailable: true,
        updatedAt: new Date(Date.now() - 500),
      },
      { merge: true }
    )
  );

  await assertSucceeds(
    m1Db.collection("users").doc(MECHANIC_UID).set(
      {
        userId: MECHANIC_UID,
        role: "mechanic",
        userType: "MECHANIC",
        baseCityLocality: "Andheri West, Mumbai",
        locality: "Andheri West, Mumbai",
        address: "Andheri West, Mumbai",
        serviceArea: "Andheri West, Mumbai",
        pincode: "400053",
        servicePincodes: ["400053", "400058"],
        serviceRadiusKm: 20,
        radiusKm: 20,
        latitude: 19.1363,
        longitude: 72.8277,
        lat: 19.1363,
        lng: 72.8277,
        shopLat: 19.1363,
        shopLng: 72.8277,
        isOnline: true,
        isAvailable: true,
        updatedAt: new Date(Date.now() - 500),
      },
      { merge: true }
    )
  );

  // Customer app can query updated mechanic area from both mechanics and users
  const custMechSnap = await assertSucceeds(custDb.collection("mechanics").where("isOnline", "==", true).get());
  if (custMechSnap.empty || custMechSnap.docs[0].data().pincode !== "400053") {
    throw new Error("Expected customer query on mechanics to return updated PIN 400053");
  }

  const custUsersSnap = await assertSucceeds(custDb.collection("users").where("role", "==", "mechanic").get());
  if (custUsersSnap.empty || custUsersSnap.docs[0].data().pincode !== "400053") {
    throw new Error("Expected customer query on users to return updated PIN 400053");
  }
});

