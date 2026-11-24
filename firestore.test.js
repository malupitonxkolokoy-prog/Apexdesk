const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_trader_123";
const BOB_UID = "bob_trader_456";

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

test("Unauthenticated users cannot read or write any ApexDesk collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/mt5_accounts`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/risk_settings`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/journal_entries`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/audit_logs`).get());
});

test("MT5 Account: owner create, partial update, cross-user denial, and @DocumentId guard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/mt5_accounts/acc_demo_1`).set({
      userId: ALICE_UID,
      label: "Primary Demo",
      brokerName: "Apex Sandbox MT5",
      accountLogin: "50129481",
      accountType: "DEMO",
      currency: "USD",
      balance: 25000.0,
      equity: 25000.0,
      marginUsed: 0.0,
      leverage: 100,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/mt5_accounts`).get()
  );

  // Partial update of balance & equity succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/mt5_accounts/acc_demo_1`).update({
      balance: 25420.5,
      equity: 25420.5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost "id" field matching @DocumentId exclusion rule
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/mt5_accounts/acc_bad_id`).set({
      id: "acc_bad_id",
      userId: ALICE_UID,
      label: "Bad ID",
      brokerName: "Broker",
      accountLogin: "12345",
      accountType: "DEMO",
      currency: "USD",
      balance: 1000,
      equity: 1000,
      marginUsed: 0,
      leverage: 100,
      active: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.doc(`users/${ALICE_UID}/mt5_accounts/acc_demo_1`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/mt5_accounts/acc_demo_1`).update({
      balance: 999999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("RiskSettings: owner create/update within bounds, rejects invalid bounds & delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/risk_settings/default`).set({
      userId: ALICE_UID,
      maxRiskPerTradePct: 1.0,
      maxDailyLossPct: 3.0,
      maxOpenPositions: 3,
      minRiskRewardRatio: 1.5,
      requireStopLoss: true,
      requireBiometricForLive: true,
      requireTwoFactorForLive: true,
      disclaimerAccepted: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Out-of-bounds maxRiskPerTradePct (> 25.0) must fail
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/risk_settings/default`).update({
      maxRiskPerTradePct: 90.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete forbidden
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/risk_settings/default`).delete());
});

test("JournalEntry: terminal state locking prevents modifying PnL/prices after CLOSED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/journal_entries/trade_1`).set({
      userId: ALICE_UID,
      symbol: "EURUSD",
      direction: "BUY",
      accountType: "DEMO",
      entryPrice: 1.085,
      exitPrice: 0.0,
      stopLoss: 1.081,
      takeProfit: 1.093,
      lotSize: 0.25,
      pnlAmount: 0.0,
      setupTag: "EMA Confluence",
      notes: "Clean pullback",
      emotion: "Calm",
      status: "OPEN",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Closing an OPEN trade succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/journal_entries/trade_1`).update({
      exitPrice: 1.093,
      pnlAmount: 200.0,
      status: "CLOSED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Tampering with pnlAmount after CLOSED must fail
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/journal_entries/trade_1`).update({
      pnlAmount: 99999.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Editing journal notes after CLOSED succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/journal_entries/trade_1`).update({
      notes: "Followed plan precisely",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("AuditLog: strictly append-only (create succeeds, update & delete fail)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/audit_logs/log_1`).set({
      userId: ALICE_UID,
      eventType: "ORDER_EXECUTED",
      symbol: "XAUUSD",
      accountType: "DEMO",
      summary: "Executed BUY 0.10 XAUUSD @ 2642.50",
      metadataSummary: "Risk 0.8%, R:R 2.2",
      biometricVerified: true,
      createdAt: now,
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/audit_logs/log_future`).set({
      userId: ALICE_UID,
      eventType: "ORDER_EXECUTED",
      symbol: "XAUUSD",
      accountType: "DEMO",
      summary: "Tampered future log",
      metadataSummary: "",
      biometricVerified: false,
      createdAt: new Date(Date.now() + 60000),
    })
  );

  // Update & delete strictly prohibited
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/audit_logs/log_1`).update({
      summary: "Altered audit log",
    })
  );
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/audit_logs/log_1`).delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/audit_logs`).get());
});
