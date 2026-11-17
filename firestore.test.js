const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_123";
const BOB_UID = "bob_owner_456";

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

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user: cannot read or write any business collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("invoices").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      name: "Ceiling Fan",
      sku: "FAN-01",
      barcode: "890123456",
      category: "Electrical",
      hsnSac: "8414",
      unit: "PCS",
      purchasePrice: 1100,
      sellingPrice: 1500,
      gstRate: 18,
      stockQty: 12,
      lowStockThreshold: 3,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("BusinessConfig: create, profile update, password update, and ghost-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Sharma Electronics",
      businessPhone: "9876543210",
      businessAddress: "MG Road, Bengaluru",
      gstin: "29ABCDE1234F1Z5",
      stateName: "Karnataka",
      upiId: "sharma@okicici",
      upiMerchantName: "Sharma Electronics",
      invoicePrefix: "INV",
      nextInvoiceNumber: 1001,
      invoiceTemplate: "MODERN",
      ownerPasswordHash: "b04820202d6193cc263cc8717176cefe0fb4b0ca3bf37d871113c603753e6d73",
      ownerPasswordSalt: "BharatBill_Owner_Salt_2026",
      mustChangeInitialPassword: true,
      twoFactorEnabled: false,
      appVersion: "2.4.0",
      autoUpdateEnabled: true,
      maintenanceMode: false,
      voiceBillingEnabled: true,
      gstBillingEnabled: true,
      upiQrEnabled: true,
      purchaseModuleEnabled: true,
      autoBackupEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Action 1: Business settings update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      businessName: "Sharma Electricals & Appliances",
      upiId: "sharma.biz@ybl",
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isSuperAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject tampering createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Reject deleting business profile
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).delete());
});

test("Cross-user isolation: Bob cannot read or write Alice's products, invoices, or payments", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      name: "LED Bulb 9W",
      sku: "BLB-09",
      barcode: "890111222",
      category: "Lighting",
      hsnSac: "8539",
      unit: "PCS",
      purchasePrice: 60,
      sellingPrice: 100,
      gstRate: 12,
      stockQty: 50,
      lowStockThreshold: 10,
      createdAt: pastDate(10000),
      updatedAt: pastDate(10000),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").update({
      sellingPrice: 1,
      updatedAt: pastDate(1000),
    })
  );
});

test("Products, Customers, Invoices & Payments: Kotlin-mirrored CRUD & UPI verification guard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  // Create Product (NO id field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_fan").set({
      userId: ALICE_UID,
      name: "Crompton Ceiling Fan",
      sku: "FAN-01",
      barcode: "890987654",
      category: "Appliances",
      hsnSac: "8414",
      unit: "PCS",
      purchasePrice: 1150,
      sellingPrice: 1500,
      gstRate: 18,
      stockQty: 20,
      lowStockThreshold: 5,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject product containing forbidden `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_bad").set({
      id: "prod_bad",
      userId: ALICE_UID,
      name: "Bad Product",
      sku: "BAD",
      barcode: "",
      category: "Misc",
      hsnSac: "",
      unit: "PCS",
      purchasePrice: 10,
      sellingPrice: 20,
      gstRate: 0,
      stockQty: 5,
      lowStockThreshold: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create Invoice
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1001").set({
      userId: ALICE_UID,
      invoiceNumber: "INV-1001",
      invoiceType: "GST",
      taxMode: "INTRA_STATE",
      customerId: "cust_1",
      customerName: "Ramesh Kumar",
      customerPhone: "9988776655",
      customerAddress: "Indiranagar, Bengaluru",
      customerGstin: "",
      items: [
        {
          productId: "prod_fan",
          name: "Crompton Ceiling Fan",
          hsnSac: "8414",
          unit: "PCS",
          qty: 1,
          unitPrice: 1500,
          discountPercent: 0,
          gstRate: 18,
          taxableAmount: 1500,
          cgst: 135,
          sgst: 135,
          igst: 0,
          total: 1770,
        },
      ],
      subtotal: 1500,
      discountAmount: 0,
      taxableTotal: 1500,
      cgstTotal: 135,
      sgstTotal: 135,
      igstTotal: 0,
      grandTotal: 1770,
      paidAmount: 0,
      balanceDue: 1770,
      paymentStatus: "UNPAID",
      paymentMethod: "UPI_QR",
      upiTransactionRef: "",
      notes: "Thank you for your business",
      templateStyle: "MODERN",
      invoiceEpochMs: Date.now() - 5000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // CRITICAL: UPI_QR payment CANNOT be created as VERIFIED without owner verification & UTR ref
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_bad").set({
      userId: ALICE_UID,
      invoiceId: "inv_1001",
      invoiceNumber: "INV-1001",
      customerId: "cust_1",
      customerName: "Ramesh Kumar",
      amount: 1770,
      method: "UPI_QR",
      upiApp: "GPay",
      upiId: "sharma@okicici",
      transactionRef: "",
      verificationStatus: "VERIFIED",
      verifiedByOwner: false,
      notes: "Auto marked",
      paymentEpochMs: Date.now() - 4000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // UPI_QR payment starts PENDING
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_1").set({
      userId: ALICE_UID,
      invoiceId: "inv_1001",
      invoiceNumber: "INV-1001",
      customerId: "cust_1",
      customerName: "Ramesh Kumar",
      amount: 1770,
      method: "UPI_QR",
      upiApp: "GPay",
      upiId: "sharma@okicici",
      transactionRef: "",
      verificationStatus: "PENDING",
      verifiedByOwner: false,
      notes: "QR generated for customer",
      paymentEpochMs: Date.now() - 3000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner verifies UPI payment with UTR reference
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_1").update({
      transactionRef: "UTR432198765432",
      verificationStatus: "VERIFIED",
      verifiedByOwner: true,
      notes: "Verified in bank app",
      updatedAt: pastDate(1000),
    })
  );

  // List queries succeed for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("invoices").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("payments").get());
});
