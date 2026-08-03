const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Scheduled Cloud Function (runs daily at midnight UTC).
 * Sends a silent dry-run data payload to registered FCM tokens.
 * If FCM responds with 'messaging/registration-token-not-registered' or 'messaging/invalid-registration-token',
 * the token is marked as 'uninstalled' in Firestore collection 'fcm_tokens'.
 *
 * Viewable Metrics:
 * - Installed: Total documents in 'fcm_tokens'
 * - Uninstalled: Count of documents with status == 'uninstalled'
 * - Currently Having App: Total - Uninstalled
 * - Active Today: Firebase Analytics Dashboard Active Users
 */
exports.checkAppUninstalls = functions.pubsub
  .schedule("every 24 hours")
  .onRun(async (context) => {
    const db = admin.firestore();
    const messaging = admin.messaging();

    const snapshot = await db
      .collection("fcm_tokens")
      .where("status", "==", "active")
      .get();

    if (snapshot.empty) {
      console.log("No active FCM tokens found.");
      return null;
    }

    console.log(`Checking ${snapshot.size} active tokens for uninstalls...`);

    let activeCount = 0;
    let uninstalledCount = 0;

    for (const doc of snapshot.docs) {
      const data = doc.data();
      const token = data.token || doc.id;

      try {
        // Send a silent data-only push in dryRun mode (or real silent ping)
        await messaging.send(
          {
            token: token,
            data: { ping: "uninstall_check" },
          },
          true // dryRun = true: validates token without displaying notification to user
        );
        activeCount++;
      } catch (error) {
        if (
          error.code === "messaging/registration-token-not-registered" ||
          error.code === "messaging/invalid-registration-token"
        ) {
          console.log(`Token ${token} identified as uninstalled: ${error.code}`);
          await doc.ref.update({
            status: "uninstalled",
            uninstalledAt: admin.firestore.FieldValue.serverTimestamp(),
          });
          uninstalledCount++;
        } else {
          console.warn(`Error pinging token ${token}:`, error.message);
        }
      }
    }

    console.log(
      `Uninstall check complete. Active: ${activeCount}, Uninstalled: ${uninstalledCount}`
    );

    // Save aggregated stats summary to Firestore
    await db.collection("app_metrics").doc("uninstall_summary").set({
      lastCheckedAt: admin.firestore.FieldValue.serverTimestamp(),
      activeTokens: activeCount,
      uninstalledTokens: uninstalledCount,
    }, { merge: true });

    return null;
  });
