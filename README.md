# FixMechanic Partner (Mechanic Android Application)

FixMechanic Partner is the native Android application for verified emergency roadside mechanics across India, built using **Kotlin**, **Jetpack Compose**, **Material 3**, and **MVVM Clean Architecture**.

---

## Shared Backend & Firebase Spark Compatibility
- **Firebase Project**: `fixmechanic-firebase` (Project Number `111465055412`)
- **Cloud Firestore Database**: `(default)` (unified across Customer, Mechanic, and Admin apps)
- **Authentication on Spark**: Supports **Email/Password** and **Google Sign-In** (via Jetpack Credential Manager). *Firebase Phone SMS OTP requires upgrading to a Blaze billing plan.*
- **Spark Dispatch Backend (`http://10.0.2.2:5055`)**: Connects to the shared Spark HTTP Callable server (`fixmechanic-admin/functions/src/server.ts`) for atomic `acceptJobOffer`, `rejectJobOffer`, `updateJobStatus`, `sendJobChatMessage`, and `updateMechanicLocation` operations, enforcing zero-trust rules without deploying Blaze Cloud Functions.

---

## Synchronized Schema & Workflows
1. **Registration & Approval (`mechanicProfiles/{uid}`)**:
   - Registers mechanic profile with `approvalStatus = "PENDING"`, `isOnline = false`, `isBusy = false`, `vehicleTypes` (`TWO_WHEELER`, `FOUR_WHEELER_CAR`, etc.), and `serviceCategories` (`FLAT_TYRE`, `BATTERY_JUMPSTART`, `FUEL_DELIVERY`, `CLUTCH_BRAKE_CHAIN`, `LOCKOUT`, `OVERHEATING_COOLANT`, `TOWING`, `MINOR_MECHANICAL`).
   - Only `APPROVED` mechanics can switch online or accept offers.
2. **Real-Time Location & Offer Receipt (`jobOffers`)**:
   - Uses FusedLocationProvider (`MechanicLocationHelper`) + `updateMechanicLocation` to keep `currentLocation.{lat,lng,address,updatedAt}` fresh while online.
   - Listens to targeted `jobOffers` (`mechanicId == uid`, `status == "PENDING"`) with non-PII vehicle/service/locality preview fields before revealing exact customer details.
3. **Atomic Job Acceptance & State Machine**:
   - Calls `acceptJobOffer` (`db.runTransaction` on backend): first responder wins `ASSIGNED`, competing mechanics receive `"This request has already been assigned"`.
   - Advances state sequentially via `updateJobStatus`: `ASSIGNED` → `ACCEPTED` → `EN_ROUTE` → `ARRIVED` → `IN_PROGRESS` → `COMPLETED` (or `CANCELLED`).
4. **Scoped Job Chat & Push Alerts**:
   - Reads `jobChats/{requestId}/jobMessages` and sends via `sendJobChatMessage` (with server phone-number masking and auto-close on completion).
   - Registers FCM token in `deviceTokens/{uid}_android` via `FixMechanicMessagingService`.

---

## Local Build & Verification
```bash
# Place google-services.json in app/google-services.json locally (never commit)
gradle :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```
