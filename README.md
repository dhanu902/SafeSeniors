# fall-detction-web

Next.js dashboard for viewing fall-detection events in real time from Firebase Firestore.

## What this app does

- Connects to Firebase using browser-safe environment variables.
- Subscribes live to the Firestore collection `activity_logs`.
- Filters events to show only fall-related entries.
- Displays summary cards: total falls, alerts, and monitored devices.
- Renders a paginated incident timeline (latest first).
- Shows a latest activity panel for quick monitoring.

## Implementation goals

- Provide a responsive cloud dashboard for fall monitoring.
- Keep data updates real time with Firestore listeners.
- Fail gracefully when Firebase is not configured.
- Keep the UI simple for quick operational review.

## Tech stack

- Next.js 16
- React 19
- TypeScript
- Tailwind CSS 4
- Firebase SDK (Firestore)

## Project structure

```
fall-detction-web/
├── app/
│   ├── globals.css
│   ├── layout.tsx
│   └── page.tsx
├── src/
│   ├── lib/
│   │   └── firebase.ts
│   └── modules/
│       └── falls-history.tsx
├── api/
├── public/
├── package.json
└── README.md
```

## How Firebase works in this app

### 1. Initialization

`src/lib/firebase.ts` builds a Firebase config from environment variables:

- `NEXT_PUBLIC_FIREBASE_API_KEY`
- `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`
- `NEXT_PUBLIC_FIREBASE_PROJECT_ID`
- `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET`
- `NEXT_PUBLIC_FIREBASE_MSG_ID`
- `NEXT_PUBLIC_FIREBASE_APP_ID`

If all variables are present, the app initializes Firebase once and exports a Firestore instance as `db`. If any variable is missing, `db` is `null`.

### 2. Real-time subscription

`src/modules/falls-history.tsx`:

- Queries Firestore collection `activity_logs`.
- Orders by `timestamp` descending.
- Limits to 50 records.
- Uses `onSnapshot(...)` for live updates.

This means new events written by devices appear in the UI immediately without a page refresh.

### 3. Event processing and display

- An event is treated as a fall when `isAlert === true` or `activity` contains `fall`.
- The list is paginated with 10 items per page.
- Timestamps are formatted using `en-GB` locale.
- Connection and error states are shown in the dashboard status card.

### 4. Expected document shape

Collection: `activity_logs`

```json
{
  "activity": "fall_detected",
  "deviceId": "device-01",
  "isAlert": true,
  "timestamp": "2026-04-22T10:30:45Z"
}
```

## Setup

1. Install dependencies:

```bash
pnpm install
```

2. Configure environment values in `.env.local`:

```bash
NEXT_PUBLIC_FIREBASE_API_KEY=...
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=...
NEXT_PUBLIC_FIREBASE_PROJECT_ID=...
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=...
NEXT_PUBLIC_FIREBASE_MSG_ID=...
NEXT_PUBLIC_FIREBASE_APP_ID=...
```

3. Start dev server:

```bash
pnpm dev
```

Open http://localhost:3000.

## Scripts

- `pnpm dev` - start development server
- `pnpm build` - production build
- `pnpm start` - run production server
- `pnpm lint` - run ESLint
