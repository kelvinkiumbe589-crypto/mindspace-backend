# Design: Brokered User↔Therapist Connection, Online (WebRTC) & In‑Person Sessions

Status: **DRAFT for review** — no code written yet.
Author: (design) · Date: 2026-07-07

## 1. Goals & principles

1. **No personal contact is ever exchanged.** A client never sees a therapist's phone/email and vice‑versa. All coordination happens *through the platform* (in‑app chat + video room). Identities are shown by display name only.
2. **The `Booking` is the connection.** Access to any channel (chat, video room, in‑person address) is derived from a booking and its `status`, and restricted to that booking's two parties.
3. **Reuse what exists.** Booking already has `client`, `therapist`, `sessionType` (`ONLINE`/`PHYSICAL`), `scheduledAt`, and the `PENDING_PAYMENT → AWAITING_APPROVAL → APPROVED → DONE / FAILED` lifecycle. We build on it, not around it.
4. **Fail safe.** If signaling/TURN is down, sessions degrade gracefully (chat still works; clear error messaging in the room).

### Current-state notes (verified in code)
- `TherapistProfile` has `priceOnline` only — **no physical price, no address**. Personal contact is only on `User.email`; there is no phone field. So "hide contacts" primarily means: never serialize `User.email` into any client/therapist‑facing DTO.
- `BookingDto.Response` exposes `sessionType`, `scheduledAt`, ids — we will extend it with connection info (room availability, masked names, address for in‑person) but **never** raw emails.

---

## 2. Data model changes

### 2.1 `TherapistProfile` (in‑person support)
Add:
- `pricePhysical` (int, KES, nullable → null means "no in‑person offered")
- `practiceAddress` (String, ≤255) — clinic/office address shown to the client **after approval**
- `practiceMapUrl` (String, ≤255, optional) — a maps link
- `practiceNotes` (String, ≤255, optional) — "3rd floor, ring buzzer 4", etc.

### 2.2 `Booking` (session coordination)
Add:
- `roomId` (UUID, unique, nullable) — generated when an `ONLINE` booking is approved; the WebRTC/chat room key.
- `checkInCode` (String, 6 chars, nullable) — for `PHYSICAL`: client shows/says it at arrival, therapist confirms → session validated.
- (optional) `endedAt` (timestamp) — when a session room was closed.

### 2.3 New: `SessionMessage` (per‑booking chat)
```
id UUID
booking_id UUID  (FK)
sender_role  ENUM(CLIENT, THERAPIST)   // never store which User for display; role is enough
text TEXT
created_at timestamp
```
Chat is scoped to a booking; either party may post once `status = APPROVED` (and until some retention window after `DONE`).

### 2.4 New: `SessionEvent` (optional, audit)
Lightweight log: `booking_id, type (JOINED/LEFT/ENDED/CHECKED_IN), actor_role, at`. Useful for support/disputes. Can be phase 3.

---

## 3. Part A — Brokered connection + in‑app chat

### Access rule (single source of truth)
`canAccess(user, booking)` = `user == booking.client || user == booking.therapist`, AND `booking.status == APPROVED` (chat) / `APPROVED` within the session window (video). Enforced in the service on **every** chat/room/signaling call — never trust the client.

### Masked identity
- Client sees therapist as `TherapistProfile.name` + title.
- Therapist sees client as **first name only** (or a stable pseudonym like "Client · A."). Never the client's email.
- No DTO anywhere includes `User.email` for the counterparty. (Add a code‑review checklist item.)

### Chat API (REST, phase 1)
- `GET  /api/sessions/{bookingId}/messages` → list (both parties)
- `POST /api/sessions/{bookingId}/messages` `{text}` → append
- Polling every 4–5s (matches the existing SupportChat pattern) for phase 1; upgrade to WebSocket push in phase 2 (reuse the signaling socket).

### Emails / notifications
- Remove any therapist contact detail from booking confirmation emails. Instead: "Your session is confirmed — open it in MindSpace." Link to the booking.
- Reuse the new **Notification** system: notify the counterparty on new chat message / booking approval / session starting soon.

---

## 4. Part B — Online sessions (WebRTC)

### 4.1 Topology
1:1 calls (client ↔ therapist). Mesh not needed. Media flows **peer‑to‑peer**; our server only does **signaling** + serves **STUN/TURN** config.

```
Browser A  ──WebSocket signaling──▶  Spring backend  ◀──WebSocket signaling──  Browser B
    │                                (relays SDP + ICE)                              │
    └──────────────── media (SRTP), P2P or via TURN relay ───────────────-----------─┘
                              coturn (self-hosted) for TURN/STUN
```

### 4.2 Signaling (Spring WebSocket)
- Add dependency `spring-boot-starter-websocket`.
- Endpoint: `wss://<backend>/ws/session?bookingId=…&token=<JWT>`.
- **Auth on handshake:** a `HandshakeInterceptor` validates the JWT (reuse `JwtUtil`), resolves the user, and asserts `canAccess(user, booking)` + booking is `ONLINE`/`APPROVED`. Reject otherwise. This is the security boundary — signaling is useless without room access.
- Room = `bookingId`. Server keeps an in‑memory map `roomId → {clientSession, therapistSession}` (max 2). Messages are relayed only to the *other* peer in the same room.
- Message types (JSON): `join`, `offer`, `answer`, `ice-candidate`, `peer-joined`, `peer-left`, `hangup`, `chat` (lets us push chat over the same socket in phase 2).

> Note on Render free tier: WebSockets are supported; an **active** call keeps the instance awake. Cold start only affects the first connect (~a few seconds). Acceptable; document it.

### 4.3 TURN — self‑hosted coturn (chosen)
WebRTC fails between users on different NATs without a relay. We self‑host **coturn** on a small VPS (e.g. Hetzner/DigitalOcean/Oracle free tier).

**Provisioning outline** (goes in an ops runbook, not app code):
- Install: `apt install coturn`.
- Open ports: `3478/tcp+udp` (STUN/TURN), `5349/tcp+udp` (TLS), and a UDP relay range e.g. `49152–65535`.
- Config `/etc/turnserver.conf` (key lines):
  ```
  listening-port=3478
  tls-listening-port=5349
  fingerprint
  use-auth-secret
  static-auth-secret=<LONG_RANDOM_SECRET>
  realm=turn.mindspace.<domain>
  total-quota=100
  cert=/etc/letsencrypt/live/turn.<domain>/fullchain.pem
  pkey=/etc/letsencrypt/live/turn.<domain>/privkey.pem
  no-cli
  ```
- TLS cert via certbot for `turn.<domain>`.
- Use **time‑limited credentials** (`use-auth-secret`): the backend, holding `static-auth-secret`, mints short‑lived TURN creds per session so we never ship a static TURN password to browsers.

**Backend:** `GET /api/sessions/{bookingId}/ice` (auth + access‑checked) returns:
```json
{ "iceServers": [
  { "urls": "stun:turn.<domain>:3478" },
  { "urls": ["turn:turn.<domain>:3478","turns:turn.<domain>:5349"],
    "username": "<unixExpiry>:mindspace",
    "credential": "<HMAC-SHA1(secret, username) base64>" }
]}
```
Credentials computed with the coturn REST‑auth scheme (username = `expiry:label`, credential = base64(HMAC‑SHA1(secret, username))), TTL ~1 hour.

### 4.4 Session lifecycle & gating
- Room becomes joinable from `scheduledAt − 10 min` until `scheduledAt + session length` (or until either party hangs up / therapist marks `DONE`).
- Either party opens the room → WS `join` → server tells the other `peer-joined` → the therapist (deterministic "polite/impolite" rule: therapist = caller) creates the `offer` → normal SDP/ICE exchange.
- Controls: mic mute, camera toggle, hang up. `hangup` → notify peer, close pc. Therapist can then mark the booking `DONE` (existing flow).
- Reconnect: on transient WS drop, retry join for ~30s before showing "call ended".

### 4.5 Frontend (both apps)
Shared logic (duplicated or published as a tiny local module in each app since they're separate repos):
- `useWebRTCSession(bookingId, role)` hook: opens WS, fetches ICE, manages `RTCPeerConnection`, exposes `localStream`, `remoteStream`, `status`, `toggleMic/Cam`, `hangUp`.
- `SessionRoom` page/component: two `<video>` tiles, control bar, connection status, and the in‑app chat side panel.
- **User app** (`landingpage`): route `/session/:bookingId`, reachable from the booking card once `APPROVED` and within the time window.
- **Therapist app** (`mindspace-therapist`): equivalent room page reachable from the therapist's schedule.

---

## 5. Part C — In‑person sessions

### Flow (state machine on a `PHYSICAL` booking)
1. Client books `PHYSICAL` (pays `pricePhysical`). → `AWAITING_APPROVAL`.
2. Therapist approves; on approval the backend:
   - snapshots the therapist's `practiceAddress` / `practiceMapUrl` / `practiceNotes` into the booking response (address is only revealed **after** approval), and
   - generates a `checkInCode`.
3. Client sees: date/time, **practice address + map link**, notes, and their `checkInCode`. No personal phone/email.
4. Coordination (reschedule questions, "running late") happens over the **in‑app chat** — never personal contact.
5. At the session, client gives the `checkInCode`; therapist enters it → booking validated → after the session therapist marks `DONE`.
6. Reminders: reuse the reminder/notification infra to nudge both parties `scheduledAt − 24h` and `− 1h`.

### Why an address, not a call
The chosen model shares the therapist's **fixed practice address** (a business location), which keeps personal contact private while giving the client what they need to attend. Optional later: masked voice via a telephony proxy (Twilio/Africa's Talking) — out of scope for v1.

---

## 6. API surface (new)

```
# Chat (both parties, APPROVED bookings)
GET   /api/sessions/{bookingId}/messages
POST  /api/sessions/{bookingId}/messages        {text}

# WebRTC
GET   /api/sessions/{bookingId}/ice             -> iceServers (time-limited TURN creds)
WS    /ws/session?bookingId=..&token=..         -> signaling (join/offer/answer/ice/hangup/chat)

# In-person
GET   /api/sessions/{bookingId}                  -> session detail (address+code AFTER approval)
POST  /api/sessions/{bookingId}/checkin          {code}   (therapist confirms attendance)

# Therapist profile (practice address) — in the therapist app / admin
PUT   /api/therapist/profile   { ..., pricePhysical, practiceAddress, practiceMapUrl, practiceNotes }
```
All access‑checked by `canAccess` + status. Signaling authorized at handshake.

---

## 7. Security & privacy checklist
- [ ] No DTO returns a counterparty's `User.email`/personal identifiers.
- [ ] Every chat/ice/detail/checkin call verifies the caller is a party on the booking.
- [ ] WS handshake validates JWT **and** booking membership + `ONLINE`/`APPROVED`.
- [ ] TURN uses short‑lived HMAC credentials; `static-auth-secret` lives only in backend env, never shipped to the browser.
- [ ] Address revealed only when `status == APPROVED`.
- [ ] Rate‑limit chat + signaling join attempts.
- [ ] Media is P2P/relayed and never touches our app server; we store only text chat + metadata.

---

## 8. Phased rollout (recommended even though doc-first)
- **Phase 1 — Connection + chat:** data model (`SessionMessage`, booking access rules), chat REST API, booking-card "Message" button in both apps, strip contacts from emails. *Ships value with zero infra.*
- **Phase 2 — WebRTC online room:** coturn provisioning, `spring-boot-starter-websocket` signaling, `/ice` endpoint, `SessionRoom` in both apps, time-window gating.
- **Phase 3 — In-person:** `pricePhysical`/practice-address fields + therapist profile UI, approval snapshot + `checkInCode`, client session detail view, reminders, optional `SessionEvent` audit.

---

## 9. Open questions / risks
1. **coturn host** — which VPS/provider? (affects the runbook + DNS `turn.<domain>`). Domain for TLS?
2. **Session length** — fixed (e.g. 50 min) or per‑therapist? Drives the room time window.
3. **Chat retention** — how long after `DONE` does chat stay open/visible?
4. **Group sessions** — 1:1 only for v1? (mesh/ SFU would be a much bigger build.)
5. **Recording** — explicitly out of scope (privacy). Confirm.
6. **Render WebSocket** — free tier is fine for low volume; if call quality/uptime matters, budget for the paid always‑on instance.
7. **Therapist app auth** — confirm it uses the same JWT so the shared room hook works unchanged.
```
