const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_snl_001";
const DEALER_A_UID = "dealer_karachi_001";
const DEALER_B_UID = "dealer_lahore_002";

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

function serverTimestamp() {
  return new Date();
}

test("Unauthenticated user: cannot read products, users, or orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").where("isActive", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(DEALER_A_UID).get());
  await assertFails(unauthDb.collection("orders").where("customerId", "==", DEALER_A_UID).get());
});

test("Dealer self-registration: succeeds with PENDING_APPROVAL and fails if spoofing SUPER_ADMIN or ACTIVE", async () => {
  const dealerDb = testEnv.authenticatedContext(DEALER_A_UID).firestore();
  const ts = serverTimestamp();

  // Attempt privilege escalation to SUPER_ADMIN -> must fail
  await assertFails(
    dealerDb.collection("users").doc(DEALER_A_UID).set({
      uid: DEALER_A_UID,
      fullName: "Ali Khan",
      businessName: "Ali Auto Lubes",
      phone: "+923001234567",
      email: "ali@example.com",
      role: "SUPER_ADMIN",
      customerCode: "DLR-001",
      city: "Karachi",
      address: "Plot 12, Shershah, Karachi",
      priceGroupId: "STANDARD_DEALER",
      accountStatus: "ACTIVE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Legitimate self-registration with PENDING_APPROVAL -> succeeds
  await assertSucceeds(
    dealerDb.collection("users").doc(DEALER_A_UID).set({
      uid: DEALER_A_UID,
      fullName: "Ali Khan",
      businessName: "Ali Auto Lubes",
      phone: "+923001234567",
      email: "ali@example.com",
      role: "DEALER",
      customerCode: "DLR-001",
      city: "Karachi",
      address: "Plot 12, Shershah, Karachi",
      priceGroupId: "STANDARD_DEALER",
      accountStatus: "PENDING_APPROVAL",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Dealer cannot update their own priceGroupId or role
  await assertFails(
    dealerDb.collection("users").doc(DEALER_A_UID).update({
      priceGroupId: "DISTRIBUTOR",
      updatedAt: serverTimestamp(),
    })
  );
});

test("PII Isolation: Dealer A cannot read Dealer B profile", async () => {
  const ts = serverTimestamp();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(DEALER_B_UID).set({
      uid: DEALER_B_UID,
      fullName: "Bilal Motors",
      businessName: "Bilal Traders",
      phone: "+923217654321",
      email: "bilal@example.com",
      role: "DEALER",
      customerCode: "DLR-002",
      city: "Lahore",
      address: "Badami Bagh, Lahore",
      priceGroupId: "STANDARD_DEALER",
      accountStatus: "ACTIVE",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const dealerADb = testEnv.authenticatedContext(DEALER_A_UID).firestore();
  await assertFails(dealerADb.collection("users").doc(DEALER_B_UID).get());
});

test("Email spoofing guard: unverified admin email is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "muhammad.dh52@gmail.com",
    email_verified: false,
  }).firestore();

  const ts = serverTimestamp();
  await assertFails(
    spoofDb.collection("priceGroups").doc("DISTRIBUTOR").set({
      groupCode: "DISTRIBUTOR",
      groupName: "Sole Distributor",
      description: "Official Pakistan Sole Distributor tier",
      defaultDiscountPercent: 0,
      minOrderQty: 1,
      isActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Order lifecycle: Customer creates order, cannot self-approve, rejected order disappears from Customer and stays in Admin Archive", async () => {
  const ts = serverTimestamp();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "admin@snl.pk",
      role: "SUPER_ADMIN",
      createdAt: ts,
    });
  });

  const dealerADb = testEnv.authenticatedContext(DEALER_A_UID).firestore();
  const dealerBDb = testEnv.authenticatedContext(DEALER_B_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  const orderPayload = {
    orderNumber: "SNL-20261107-1001",
    customerId: DEALER_A_UID,
    customerCode: "DLR-001",
    customerName: "Ali Khan",
    businessName: "Ali Auto Lubes",
    customerType: "DEALER",
    phone: "+923001234567",
    city: "Karachi",
    deliveryAddress: "Plot 12, Shershah, Karachi",
    orderInstructions: "Urgent dispatch",
    priceGroupId: "STANDARD_DEALER",
    itemsSummary: "Liquix L-5000+ (4ltr x 4) x 10",
    itemsCount: 1,
    totalCartons: 10,
    priceSnapshotText: "SNL-033|Liquix L-5000+|4ltr x 4|SN/CF 10w40|10|16313.0|163130.0",
    subtotal: 163130,
    discount: 0,
    deliveryCharges: 0,
    grandTotal: 163130,
    paymentMethod: "COD",
    paymentStatus: "UNPAID",
    orderStatus: "PENDING_ADMIN_APPROVAL",
    visibleToCustomer: true,
    rejectionReason: "",
    approvedBy: "",
    rejectedBy: "",
    createdAt: ts,
    updatedAt: ts,
  };

  // Dealer A creates order
  await assertSucceeds(dealerADb.collection("orders").doc("ord_1001").set(orderPayload));

  // Dealer B cannot read Dealer A's order
  await assertFails(dealerBDb.collection("orders").doc("ord_1001").get());

  // Dealer A cannot self-approve their order
  await assertFails(
    dealerADb.collection("orders").doc("ord_1001").update({
      orderStatus: "CONFIRMED",
      approvedBy: DEALER_A_UID,
      updatedAt: serverTimestamp(),
    })
  );

  // Admin rejects the order (sets orderStatus = REJECTED, visibleToCustomer = false)
  await assertSucceeds(
    adminDb.collection("orders").doc("ord_1001").update({
      orderStatus: "REJECTED",
      visibleToCustomer: false,
      rejectionReason: "Credit limit exceeded",
      rejectedBy: ADMIN_UID,
      updatedAt: serverTimestamp(),
    })
  );

  // Rejected order disappears from Dealer A's get and list queries!
  await assertFails(dealerADb.collection("orders").doc("ord_1001").get());

  // Admin can still read the Rejected Order in the Admin Archive!
  await assertSucceeds(adminDb.collection("orders").doc("ord_1001").get());

  // Terminal State Lock: Rejected order cannot be accepted later!
  await assertFails(
    adminDb.collection("orders").doc("ord_1001").update({
      orderStatus: "CONFIRMED",
      visibleToCustomer: true,
      approvedBy: ADMIN_UID,
      updatedAt: serverTimestamp(),
    })
  );
});
