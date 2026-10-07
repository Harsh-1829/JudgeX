# JudgeX — Smart Screening & Evaluation Management System

JudgeX is a native Android application built in **Java** with **Material 3 / XML layouts**, **Firebase Authentication**, **Cloud Firestore**, and **ZXing QR Code generation & scanning**. It streamlines the judging and screening workflow for hackathons (like Smart India Hackathon internal screenings) and college project competitions.

---

## 🌟 Key Features & User Roles

### 1. 👑 Admin Role
* **Event Lifecycle Management**: Create, edit, switch active events, and manage statuses (`ACTIVE`, `COMPLETED`, `ARCHIVED`).
* **Evaluation Locking**: Toggle lock/unlock state to freeze or open evaluation submissions.
* **Panel Management**: Create judging panels and assign physical venues/classrooms.
* **Judge Provisioning**: Register judge accounts securely without logging out the administrator.
* **Team Registration & QR Badges**: Register competing teams, assign them to panels, and generate unique QR codes for badges.
* **Configurable Rubric & Weighting**: Configure criteria with customizable maximum marks and percentage weights (e.g., 25% each).
* **Live Progress Tracking**: Monitor real-time completion percentages per team and overall event progress.
* **Leaderboards & Ranking**: View live rankings with tie-breaking, filter by panel, view criteria-level score breakdowns, and share/export standings.

### 2. 👨‍⚖️ Judge Role
* **Assigned Panel Overview**: View assigned panels, venues, and assigned team lists.
* **Camera QR Scanner**: Scan team QR badges with integrated flashlight and autofocus.
* **Panel Security Enforcement**: Strict validation ensures judges only grade teams assigned to their panel.
* **Dynamic Grading & Validation**: Enter scores for each configured criterion with instant range validation ($0 \le \text{score} \le \text{maxScore}$).
* **Per-Criterion & Overall Feedback**: Add specific comments for each criterion as well as overall remarks.
* **Draft Saving & Resumption**: Save unfinished evaluations as drafts and resume grading seamlessly.
* **Duplicate Prevention & Submission Receipt**: Atomic submission prevents duplicate entries and generates a receipt.

---

## 📊 Scoring & Ranking Logic

The scoring engine implements the weighted percentage formula:

$$\text{Criterion Contribution} = \left( \frac{\text{Score Awarded}}{\text{Maximum Score}} \right) \times \text{Criterion Weight}$$

$$\text{Final Score (out of 100)} = \sum \text{Criterion Contributions}$$

### Multi-Judge Averaging & Ties:
* **Team Average**: Calculated strictly from valid submitted evaluations ($\frac{\sum \text{Submitted Scores}}{\text{Number of Submitted Evaluations}}$). Missing/pending evaluations are **never** counted as zero.
* **Ties**: Teams with identical average scores share the same rank consistently (e.g. two #1s followed by #3).
* **Provisional vs Final**: Results are labeled as `PROVISIONAL` until all assigned evaluations are completed or the Admin locks/finalizes the event.

---

## 🗂️ Project Structure

```
com.example.judgex
├── adapters/
│   ├── CriteriaAdapter.java
│   ├── EvaluationCriteriaAdapter.java
│   ├── EventAdapter.java
│   ├── JudgeAdapter.java
│   ├── PanelAdapter.java
│   ├── ProgressAdapter.java
│   ├── RankingAdapter.java
│   └── TeamAdapter.java
├── admin/
│   ├── AdminDashboardActivity.java
│   ├── CriteriaManagerActivity.java
│   ├── EvaluationProgressActivity.java
│   ├── EventManagerActivity.java
│   ├── JudgeManagerActivity.java
│   ├── PanelManagerActivity.java
│   ├── ResultsRankingActivity.java
│   └── TeamManagerActivity.java
├── auth/
│   ├── LoginActivity.java
│   └── SplashActivity.java
├── data/
│   └── FirebaseManager.java
├── judge/
│   ├── EvaluationFormActivity.java
│   ├── JudgeDashboardActivity.java
│   ├── QRScannerActivity.java
│   └── SubmissionConfirmationActivity.java
├── models/
│   ├── Criterion.java
│   ├── Evaluation.java
│   ├── Event.java
│   ├── Panel.java
│   ├── Team.java
│   ├── TeamResult.java
│   └── User.java
├── qr/
│   ├── QRCodeGenerator.java
│   └── QRViewActivity.java
└── utils/
    ├── Constants.java
    └── UIUtils.java
```

---

## 🔒 Firebase Security Rules

The security rules in [`firestore.rules`](firestore.rules) enforce:
1. **Authentication Required**: Protected collections require valid Firebase Auth tokens.
2. **Role Integrity**: Clients cannot alter their own roles.
3. **Admin Exclusivity**: Only admins can manage events, panels, teams, criteria, and delete records.
4. **Atomic Evaluations**: Judges can only submit evaluations for their own identity and cannot overwrite submitted evaluations.

---

## 🚀 Getting Started

1. Ensure **`app/google-services.json`** is in place.
2. Enable **Email/Password** authentication in the Firebase Console.
3. Publish the rules from `firestore.rules` in your Firestore **Rules** tab.
4. Open the project in **Android Studio** and click **Run** (`Shift + F10`).
