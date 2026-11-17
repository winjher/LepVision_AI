const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_breeder_01";
const BOB_UID = "bob_breeder_02";

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

test("Unauthenticated user cannot read or write classifications, batches, or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("classifications").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("batches").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("classifications").doc("rec_1").set({
      userId: ALICE_UID,
      task: "species",
      label: "Butterfly-Golden Birdwing",
      confidence: 0.92,
      identified: true,
      engineMode: "gemini_vision",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, list, partially update, and delete classification records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("classifications").doc("rec_1").set({
      userId: ALICE_UID,
      task: "full_scan",
      label: "Butterfly-Golden Birdwing",
      confidence: 0.94,
      identified: true,
      speciesLabel: "Butterfly-Golden Birdwing",
      stageLabel: "Adult",
      diseaseLabel: "N/A",
      instarLabel: "N/A",
      defectLabel: "N/A",
      sexLabel: "Female",
      notes: "Observed near Boac sanctuary nectar bushes",
      summary: "Healthy adult female Troides rhadamantus specimen.",
      engineMode: "gemini_vision",
      batchName: "Boac Cohort A",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("classifications").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("classifications").doc("rec_1").update({
      notes: "Updated host plant observation",
      batchName: "Boac Cohort B",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / @DocumentId id / tampered future timestamp / immutable task mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("classifications").doc("rec_1").update({
      task: "disease",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("classifications").doc("rec_2").set({
      id: "rec_2",
      userId: ALICE_UID,
      task: "species",
      label: "Butterfly-Paper Kite",
      confidence: 0.88,
      identified: true,
      engineMode: "gemini_vision",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("classifications").doc("rec_3").set({
      userId: ALICE_UID,
      task: "species",
      label: "Butterfly-Paper Kite",
      confidence: 0.88,
      identified: true,
      engineMode: "gemini_vision",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("classifications").doc("rec_1").delete()
  );
});

test("Cross-user access is strictly denied across all subcollections", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = new Date(Date.now() - 5000);
    await ctx.firestore().collection("users").doc(BOB_UID).collection("classifications").doc("bob_rec").set({
      userId: BOB_UID,
      task: "disease",
      label: "Healthy",
      confidence: 0.91,
      identified: true,
      engineMode: "gemini_vision",
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(BOB_UID).collection("batches").doc("bob_batch").set({
      userId: BOB_UID,
      name: "Bob Cohort",
      targetSpecies: "Butterfly-Clippers",
      initialCount: 50,
      healthyLarvaeCount: 44,
      diseasedLarvaeCount: 6,
      goodPupaeCount: 40,
      defectivePupaeCount: 4,
      predictedYieldPercent: 78.5,
      predictedAdultCount: 39,
      hostPlantHealth: "Optimal",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("classifications").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("classifications").doc("bob_rec").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("batches").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("batches").doc("bob_batch").delete());
});

test("Breeding batches and user preferences enforce strict schema bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("batches").doc("batch_1").set({
      userId: ALICE_UID,
      name: "Gasan Sanctuary Batch 1",
      targetSpecies: "Butterfly-Emerald Swallowtail",
      initialCount: 100,
      healthyLarvaeCount: 86,
      diseasedLarvaeCount: 14,
      goodPupaeCount: 78,
      defectivePupaeCount: 8,
      predictedYieldPercent: 76.2,
      predictedAdultCount: 76,
      hostPlantHealth: "Optimal",
      notes: "Morning misting active",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid hostPlantHealth rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("batches").doc("batch_2").set({
      userId: ALICE_UID,
      name: "Invalid Batch",
      targetSpecies: "Butterfly-Clippers",
      initialCount: 20,
      healthyLarvaeCount: 15,
      diseasedLarvaeCount: 5,
      goodPupaeCount: 12,
      defectivePupaeCount: 3,
      predictedYieldPercent: 60.0,
      predictedAdultCount: 12,
      hostPlantHealth: "UnknownState",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").set({
      userId: ALICE_UID,
      engineMode: "gemini_vision",
      customCnnUrl: "",
      driveFolderId: "1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN",
      driveDirectoryPath: "Drive/Butterfly Dataset/",
      speciesModelFile: "butterfly_species.keras",
      minConfidence: 0.5,
      updatedAt: now,
    })
  );
});
