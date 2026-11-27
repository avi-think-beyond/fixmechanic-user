# FixMechanic Unified Backend Architecture & Security Specification

## 1. System Overview & Shared Firebase Project
All three FixMechanic applications share **one Firebase project (`fixmechanic-firebase`, project number `111465055412`)**, **one `(default)` Cloud Firestore database**, and **one zero-trust dispatch backend**:

1. **FixMechanic Customer App** (`fixmechanic-customer`)
2. **FixMechanic Mechanic Partner App** (`fixmechanic-user`)
3. **FixMechanic Admin Ops App & Web Console** (`fixmechanic-admin`)

---

## 2. Firebase Spark Plan Architecture vs. Production Blaze Upgrade
The platform runs end-to-end on the **Firebase Spark (no-cost)** plan without requiring Blaze billing:
- **Firebase Authentication**: Uses **Email/Password** and **Google Sign-In** (via Jetpack Credential Manager on Android). *Note: Firebase Phone SMS OTP requires a Blaze billing plan and is intentionally gated with a clear notice on Spark.*
- **Cloud Firestore (`(default)`) & Zero-Trust Rules**: Enforces strict owner/participant/admin boundaries (`firestore.rules`).
- **Spark HTTP Dispatch Server (`functions/src/server.ts`, port `5055`)**: Runs the exact same TypeScript dispatch, geospatial matching, atomic transaction, state machine, chat, and rating handlers (`createServiceRequest`, `acceptJobOffer`, `rejectJobOffer`, `updateJobStatus`, `sendJobChatMessage`, `submitJobRating`, `updateMechanicStatus`, `updateMechanicLocation`, `retryServiceRequest`, `triggerOfferExpiryCheck`, `adminCancelOrReassign`, `processMechanicsCsv`, `updateApplicationConfig`).
  - Android emulators reach the local Spark server at `http://10.0.2.2:5055` (`BuildConfig.BACKEND_BASE_URL`).
  - When upgrading to **Blaze**, the exact same handlers in `functions/src/index.ts` deploy directly to Firebase Cloud Functions with zero client schema changes.

---

## 3. Cloud Firestore Schema Specification (`(default)` database)

| Collection | Document ID | Purpose | Authorization Policy |
| :--- | :--- | :--- | :--- |
| `users` | `{userId}` | Identity & role (`CUSTOMER`, `MECHANIC`, `ADMIN`) | Owner read; Admin read/write; client cannot escalate `role` or `disabled` |
| `customerProfiles` | `{customerId}` | Customer contact & vehicle defaults | Owner/Admin read; assigned mechanic read-only |
| `mechanicProfiles` | `{mechanicId}` | `approvalStatus` (`PENDING`/`APPROVED`/`REJECTED`/`SUSPENDED`), `isOnline`, `isBusy`, `vehicleTypes`, `serviceCategories`, `currentLocation` | Authenticated read for nearby discovery; mechanic can only update allowed non-privileged fields; Admin manages `approvalStatus` |
| `serviceRequests` | `{requestId}` | Breakdown request with `vehicleType`, `serviceCategory`, `issueTitle`, `location.{lat,lng,address,landmark,city}`, `status`, `assignedMechanicPhoneMasked`, `ratingGiven` | Customer, assigned mechanic, and Admin read; writes strictly via backend (`createServiceRequest`, `updateJobStatus`) |
| `jobOffers` | `{offerId}` | Targeted dispatch offers with non-PII preview (`vehicleType`, `vehicleMakeModel`, `serviceCategory`, `issueTitle`, `locality`, `estimatedPriceInr`, `distanceKm`) | Mechanic reads only offers where `mechanicId == uid`; Admin reads all |
| `jobAssignments` | `{requestId}` | Authoritative winner assignment record | Customer, assigned mechanic, Admin read |
| `jobStatusEvents` | `{eventId}` | Immutable audit trail of status transitions | Participants and Admin read |
| `jobChats` | `{requestId}` | Scoped chat container (`OPEN` / `CLOSED`) | Customer, assigned mechanic, Admin read |
| `jobMessages` | `{messageId}` (subcollection under `jobChats/{requestId}`) | Messages with `senderId`, `senderRole`, `text`, `timestamp` (phone numbers masked) | Participants read; writes via `sendJobChatMessage` while `OPEN` |
| `ratings` | `{requestId}` | Customer star reviews (`1..5`) | Authenticated read; customer creates via `submitJobRating` |
| `deviceTokens` | `{tokenId}` | FCM registration tokens (`userId`, `role`, `platform`, `token`) | Owner and Admin read/write |
| `adminAuditLogs` | `{logId}` | Admin operation logs | Admin read; backend write |
| `applicationConfig` | `current` | Radius, batch size, timeout, staleness config | Authenticated/Public read; Admin write via backend |

---

## 4. Canonical Enums
- **`VehicleType`**: `TWO_WHEELER`, `FOUR_WHEELER_CAR`, `EV_TWO_WHEELER`, `EV_FOUR_WHEELER`, `COMMERCIAL_LIGHT`
- **`ServiceCategory`**: `FLAT_TYRE`, `BATTERY_JUMPSTART`, `FUEL_DELIVERY`, `CLUTCH_BRAKE_CHAIN`, `LOCKOUT`, `OVERHEATING_COOLANT`, `TOWING`, `MINOR_MECHANICAL`, `OTHER`
- **`ServiceRequestStatus`**: `REQUESTED`, `SEARCHING`, `ASSIGNED`, `ACCEPTED`, `EN_ROUTE`, `ARRIVED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `EXPIRED`

---

## 5. Authoritative Dispatch & Atomic Acceptance Flow
1. Customer calls `createServiceRequest` -> creates `serviceRequests/{id}` in `SEARCHING`, encodes geohash, logs `jobStatusEvents`, and invokes `dispatchServiceRequest`.
2. `dispatchServiceRequest` queries `mechanicProfiles` where `approvalStatus == "APPROVED"`, `isOnline == true`, `isBusy == false`, filters by `vehicleTypes`, `serviceCategories`, fresh `currentLocation` (within `maxMechanicLocationStalenessMinutes`), and Haversine distance <= `searchRadiusKm`, then creates `jobOffers` with non-PII preview fields and sends FCM alerts.
3. Mechanic accepts via `acceptJobOffer` (`db.runTransaction`): verifies offer `PENDING` & unexpired, mechanic `APPROVED` & `!isBusy`, request `SEARCHING` & unassigned; atomically assigns mechanic, sets `isBusy = true`, opens `jobChats/{requestId}`, marks competing offers `LOST`, and notifies participants.
4. Mechanic progresses state strictly (`ASSIGNED -> ACCEPTED -> EN_ROUTE -> ARRIVED -> IN_PROGRESS -> COMPLETED`) via `updateJobStatus`. Upon `COMPLETED`, `isBusy` resets to `false`, `totalCompletedJobs` increments, and `jobChats/{requestId}` closes.
