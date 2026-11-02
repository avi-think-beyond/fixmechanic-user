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

test("Unauthenticated user: cannot read mechanics or emergency requests", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("mechanics").doc("some_id").get());
  await assertFails(unauthDb.collection("emergency_requests").doc("req_1").get());
});

test("Mechanic can create and update own profile, but cannot update another mechanic", async () => {
  const mechanicDb = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  
  // Can create own profile
  await assertSucceeds(
    mechanicDb.collection("mechanics").doc(MECHANIC_UID).set({
      userId: MECHANIC_UID,
      fullName: "Ramesh Sharma",
      mobileNumber: "+919876543210",
      registrationStatus: "APPROVED",
      isOnline: true,
      createdAt: new Date(),
    })
  );

  // Cannot update another mechanic's profile
  await assertFails(
    mechanicDb.collection("mechanics").doc(MECHANIC_2_UID).set({
      userId: MECHANIC_2_UID,
      fullName: "Impostor",
      mobileNumber: "+919876543210",
      registrationStatus: "APPROVED",
      isOnline: true,
      createdAt: new Date(),
    })
  );
});

test("Atomic claim: first mechanic wins, second mechanic fails", async () => {
  // Seed pending emergency request via admin context
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("emergency_requests").doc("req_101").set({
      customerId: CUSTOMER_UID,
      vehicleType: "TWO_WHEELER",
      problemCategory: "PUNCTURE_TYRE",
      status: "PENDING_DISPATCH",
      assignedMechanicId: null,
      createdAt: new Date(),
    });
  });

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  const m2Db = testEnv.authenticatedContext(MECHANIC_2_UID).firestore();

  // Mechanic 1 claims the job
  await assertSucceeds(
    m1Db.collection("emergency_requests").doc("req_101").update({
      status: "ACCEPTED",
      assignedMechanicId: MECHANIC_UID,
      assignedMechanicName: "Ramesh Sharma",
      assignedMechanicMobile: "+919876543210",
      updatedAt: new Date(),
    })
  );

  // Mechanic 2 tries to claim the same job (should FAIL because status is now ACCEPTED and assignedMechanicId is not null)
  await assertFails(
    m2Db.collection("emergency_requests").doc("req_101").update({
      status: "ACCEPTED",
      assignedMechanicId: MECHANIC_2_UID,
      assignedMechanicName: "Suresh Kumar",
      assignedMechanicMobile: "+919876543211",
      updatedAt: new Date(),
    })
  );
});

test("Assigned mechanic can advance status to EN_ROUTE", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("emergency_requests").doc("req_102").set({
      customerId: CUSTOMER_UID,
      vehicleType: "FOUR_WHEELER",
      problemCategory: "BATTERY_JUMPSTART",
      status: "ACCEPTED",
      assignedMechanicId: MECHANIC_UID,
      createdAt: new Date(),
    });
  });

  const m1Db = testEnv.authenticatedContext(MECHANIC_UID).firestore();
  await assertSucceeds(
    m1Db.collection("emergency_requests").doc("req_102").update({
      status: "EN_ROUTE",
      updatedAt: new Date(),
    })
  );
});
