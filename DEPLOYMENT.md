# FixMechanic Unified Backend & Apps — Setup & Deployment Guide

## 1. Shared Firebase Project
All apps connect to:
- **Firebase Project ID**: `fixmechanic-firebase`
- **Project Number**: `111465055412`
- **Firestore Database**: `(default)`

> Place each app's downloaded `google-services.json` locally in `app/google-services.json` (git-ignored, never committed).

---

## 2. Running on Firebase Spark (No-Cost Plan)

### 2.1 Firebase Console (Spark)
1. Enable **Cloud Firestore** (`(default)` database).
2. Enable **Authentication** sign-in providers: **Email/Password** and **Google**.
   - *Phone SMS OTP requires upgrading to Blaze billing; on Spark, all apps use Email/Password and Google Sign-In.*
3. Deploy Firestore rules and indexes from `fixmechanic-admin`:
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes --project fixmechanic-firebase
   ```

### 2.2 Running the Spark Dispatch Backend Server Locally (or on Any Node Host)
Because Cloud Functions deployment requires Blaze, the TypeScript dispatch engine runs on Spark via the standalone HTTP Callable server (`functions/src/server.ts`):
```bash
cd functions
npm install
npm run build
# Against local Firebase Emulators:
FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 GCLOUD_PROJECT=fixmechanic-firebase PORT=5055 node lib/server.js
```
All three Android apps default `BuildConfig.BACKEND_BASE_URL` to `http://10.0.2.2:5055` for Android Emulator testing.

### 2.3 Local Firebase Emulators & Automated Verification
```bash
# Terminal 1: Start Auth (9099) & Firestore (8085) emulators
firebase emulators:start --config firebase.json --only auth,firestore --project fixmechanic-firebase

# Terminal 2: Start Spark dispatch server (5055)
FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 GCLOUD_PROJECT=fixmechanic-firebase PORT=5055 node functions/lib/server.js

# Terminal 3: Run backend unit + E2E tests
node --test functions/tests/test_suite.js functions/tests/e2e_emulator.test.js
```

### 2.4 Web Admin Console (`/web`)
Serve `web/` locally (or deploy via `firebase deploy --only hosting`):
```bash
npx serve web -p 5173
```
When opened on `localhost` / `127.0.0.1`, `web/firebase-config.js` automatically connects to local Auth (`9099`), Firestore (`8085`), and Spark Backend (`5055`). Enter your Firebase Web API key / App ID in the Auth Gate runtime settings dialog when targeting a live Firebase project.

---

## 3. Upgrading to Blaze (Optional Production Cloud Functions)
When ready to deploy serverless Cloud Functions directly to Google Cloud:
1. Upgrade `fixmechanic-firebase` to Blaze in Firebase Console.
2. Run `firebase deploy --only functions --project fixmechanic-firebase`.
3. Optionally enable Firebase Phone Authentication if SMS OTP is desired.
