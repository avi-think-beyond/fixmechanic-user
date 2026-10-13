# FixMechanic Partner (Mechanic Android Application)

FixMechanic Partner is the native Android application for emergency roadside mechanics across India, built using **Kotlin**, **Jetpack Compose**, **Material 3**, and **MVVM Clean Architecture**.

---

## 🇮🇳 India Roadside Platform Overview

- **Vehicle Capabilities:** Bike & Two-Wheeler, Car & Four-Wheeler, or Both.
- **Emergency Breakdown Specialties:**
  - Tyre Puncture & Tube Repair
  - 12V Battery Jumpstart & Boost
  - Emergency Petrol / Diesel Delivery (2–5 Litres)
  - Clutch, Brake & Chain Cable Repair
  - Key Lockout & Door Unlock
  - Engine Starting Trouble & Spark Plug
  - Radiator Overheating & Coolant Top-up
  - Tow Truck & Flatbed Recovery Assistance
- **Standardized Currency:** Indian Rupee (**₹ INR**).
- **Authentication:** Indian 10-digit Mobile Number with SMS OTP verification.

---

## 📱 Completed Screens & Workflows

1. **Splash & Brand Launch:** FixMechanic Partner India branding with automotive emblem.
2. **Onboarding Walkthrough:** 3-step value carousel covering emergency dispatches, ₹ INR payouts, and safety standards.
3. **Mobile Number Login:** 10-digit Indian phone number entry with `+91` validation and Demo fast persona logins.
4. **OTP Verification:** 6-digit SMS OTP entry with 30s resend timer and Demo OTP fallback (`123456`).
5. **Mechanic Registration:** Onboarding application with workshop details, vehicle capability (Bike / Car / Both), operating locality, years of experience, and Driving License / Aadhaar verification ID.
6. **KYC & Verification Audit:** Live status tracker (`APPROVED`, `PENDING`, `REJECTED`, `SUSPENDED`), audit checklist, and re-submission flow.
7. **Vehicles & Services Supported:** Capability radio selector (Bike / Car / Both) and checkboxes for all 8 Indian emergency service categories.
8. **Service Radius & Base Hub:** 5 km to 40 km interactive radius slider around base city hub (e.g. Indiranagar & Outer Ring Road, Bengaluru).
9. **Edit Profile:** Manage permitted fields (Mechanic name, Workshop name, Bike/Car capability, City locality, Radius).
10. **Partner Home Dashboard:**
    - Online/Offline toggle with immediate zero-trust eligibility gating.
    - Clear notification & location permission status card with runtime permission request launcher.
    - Active dispatch radar scanning within selected radius.
    - Today's earnings (`₹2,450`), response rate, and today's completed assistances.
    - Active emergency job banner card.
11. **Incoming Emergency Job Offer Screen:**
    - Vehicle type (Two-Wheeler / Four-Wheeler) and model.
    - Problem category and detailed description.
    - Approximate distance and locality (e.g. "2.8 km away • Outer Ring Road near Marathahalli Bridge").
    - Customer-provided notes.
    - Server-controlled 30-second ticking progress bar.
    - Upfront estimated payout in ₹ INR (`₹550 - ₹750`).
    - Customer personal phone number and WhatsApp identifier strictly masked.
12. **Accept / Reject & Authoritative Backend Rule:**
    - Submits acceptance to the backend.
    - If another mechanic claims the request first, displays:
      > *"This request has already been assigned"*
      and automatically dismisses the expired offer.
13. **Customer Location & GPS Navigation:**
    - Exact breakdown address unlocked only after confirmed assignment.
    - Direct Google Maps turn-by-turn intent launcher (`google.navigation:q=lat,lng`).
    - Clear disclaimer regarding simulated routing vs production background telemetry.
14. **Active Job State Machine:**
    Strict backend progression:
    `ACCEPTED` ➔ `EN_ROUTE` ➔ `ARRIVED` ➔ `IN_PROGRESS` ➔ `COMPLETED` (or `CANCELLED`).
    Arbitrary status skips are rejected by the server.
15. **In-App Quote Submission:**
    - Itemized spare parts and additional roadside labor form in ₹ INR.
    - Customer in-app authorization state tracking.
16. **Private In-App Chat:**
    - Masked customer name (e.g. "Rohan S."); direct phone numbers hidden.
    - Quick canned Indian roadside emergency safety responses.
    - Message delivery states (`SENT`, `DELIVERED`, `FAILED` with retry).
    - Automatically locked and disabled when the job completes or cancels.
17. **Job Completion & Sign-Off:**
    - Service log, diagnostic findings, and parts consumed.
    - Mandatory 3-photo checklist.
    - Customer digital acknowledgment token verification (`CUST-ACK-9102-OK`).
    - Final payout tally credited to today's earnings.
18. **History & Earnings (₹ INR):**
    - Filterable history (All, Completed, Cancelled).
    - Gross and net earnings breakdown (85% tech / 15% platform fee).
    - Customer ratings and feedback reviews.

---

## 🔒 Security & Eligibility Rules

1. **Zero-Trust Eligibility:**
   - Only mechanics with `APPROVED` status can go Online or receive/accept offers.
   - Mechanics with `PENDING`, `REJECTED`, or `SUSPENDED` status are blocked with explicit explanatory banners.
2. **Authoritative Backend Assignment:**
   - The Android client never decides who wins an emergency offer.
   - Server enforces atomic first-valid-acceptance. In case of race conditions, the client receives:
     `"This request has already been assigned"`.
3. **Sequential Workflow Enforcement:**
   - Mechanics cannot arbitrarily jump from `ACCEPTED` directly to `COMPLETED`. Every transition must follow the sequential state machine.
4. **Privacy Shield:**
   - Prior to confirmed assignment, customer phone, WhatsApp, and exact street numbers are hidden.
   - Communication channel terminates immediately when the job closes.

---

## 🧪 Demo Simulation Mode vs. Production

### What is currently in Demo Simulation:
- **Synthetic Personas:**
  - `Rajesh Kumar (Approved)`: Master Tech with full clearance to go Online and accept requests.
  - `Amit Sharma (Pending)`: New applicant under KYC audit. Tests eligibility blocking.
  - `Suresh Patil (Suspended)`: Account suspended due to policy audit.
- **Race Condition Testing:**
  - Toggle `"Competitor Win"` in the top banner. When enabled, tapping Accept triggers the server assignment loss:
    `"This request has already been assigned"`.
- **Simulated Offers:**
  - Trigger realistic Indian breakdown dispatches (Car Battery Jumpstart on Outer Ring Road, Bike Tyre Puncture at Koramangala, Swift Fuel Delivery on Old Airport Road).
- **Navigation:**
  - Launches external Google Maps intent to coordinates. Live background GPS tracking socket is mocked until production LocationManager service is enabled.

---

## ⚙️ Manual Configuration & Testing Instructions

### 1. Running Unit & Robolectric Tests:
```bash
gradle :app:testDebugUnitTest
```
Tests verify:
- Unapproved mechanics (`PENDING`) cannot accept emergency offers.
- Authoritative server assignment conflict displays `"This request has already been assigned"`.
- Strict sequential transitions (`ACCEPTED` ➔ `EN_ROUTE` ➔ `ARRIVED` ➔ `IN_PROGRESS` ➔ `COMPLETED`) reject arbitrary status skips.
- Chat session closes after completion or cancellation.

### 2. Manual Testing in the App:
1. **Test Registration & OTP:**
   - Open app ➔ Enter mobile `9876543210` ➔ Tap "Send Verification OTP".
   - Enter `123456` on the OTP screen ➔ Tap "Verify & Continue".
2. **Test Eligibility Blocking:**
   - In the top banner, tap `Amit (Pending)`.
   - Attempt to toggle Online ➔ Observe error: *"Only APPROVED mechanics can switch Online. Current status: Pending Verification"*.
   - Tap "Simulate Offer" ➔ Tap "Accept" ➔ Observe rejection: *"Your registration status is Pending Verification. Only APPROVED mechanics may accept emergency requests."*
3. **Test Competitor Win Race Condition:**
   - Switch back to `Rajesh (Approved)` in the top banner.
   - Turn ON the `Competitor Win` toggle switch.
   - Tap "Simulate Offer" ➔ Tap "Accept Offer".
   - Observe conflict dialog: **"This request has already been assigned"** and offer removal.
4. **Test Full Active Job Workflow:**
   - Turn OFF `Competitor Win`.
   - Tap "Simulate Offer" ➔ Tap "Accept Offer".
   - Observe assignment confirmation and transition to Active Job screen.
   - Step sequentially: `Start Navigation (En Route)` ➔ `Confirm Arrived on Site` ➔ `Begin Diagnostics & Repair` ➔ `Add Quote (₹)` ➔ `Work Done → Sign-Off` ➔ `Complete Job & Credit Payout`.
   - In Chat: Send a message containing "ETA" ➔ Observe simulated customer reply within 1.2s.
   - Complete the job ➔ Return to Chat ➔ Observe session closed notification.
