# Chat App

A real-time messaging application built with a **native Android (Kotlin)** client and a **Node.js + Socket.io** backend. Users can sign up securely, chat with contacts instantly over WebSockets, see online status, share photos, and review full chat history.

## Features

| Feature | How it works |
| --- | --- |
| User Authentication | JWT-based register/login, bcrypt password hashing (REST API) |
| Real-Time Messaging | Socket.io WebSocket transport, message delivery with server ack |
| Online Status | Live presence events broadcast over sockets |
| Push Notifications | Foreground service maintains the connection in background and posts Android notifications for new messages |
| Media Sharing | Image picker + multipart upload, rendered inline in the conversation |
| Chat History | Messages persisted on the server and cached locally per conversation |

## Repository layout

```
chat-app/
├── android/          # Native Android app (Kotlin, Jetpack, Material 3)
│   └── app/src/main/java/com/chatapp/
│       ├── data/     # REST client (Retrofit), socket manager, session & cache
│       ├── service/  # Foreground notification service
│       └── ui/       # Login, Contacts, Chat screens
├── backend/          # Node.js server (Express REST + Socket.io)
│   ├── src/          # auth, socket handlers, media upload, JSON store
│   └── server.js     # entry point
├── apk/              # Prebuilt APK
│   └── ChatApp-v1.0.0-debug.apk
├── docs/             # Project report (PDF)
└── README.md
```

## Quick start

### 1. Run the backend

```bash
cd backend
npm install
npm start
```

The server listens on `http://0.0.0.0:3000`. Health check: `curl http://localhost:3000/health`.

Data is stored in `backend/data/` (JSON files); uploaded images land in `backend/uploads/`.

### 0. Demo accounts (optional)

Seed two ready-made accounts:

```bash
cd backend
node seed-demo.js
```

| Email | Password |
| --- | --- |
| `demo@chatapp.com` | `demo123` |
| `alice@chatapp.com` | `alice123` |

Or register any new account from the app — it is instant.

### 2. Build / install the Android app

- **Prebuilt:** install `apk/ChatApp-v1.0.0-debug.apk` on a device or emulator (enable "Install unknown apps").
- **From source:**

```bash
cd android
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

### 3. Configure the server address

The app targets the Android emulator host loopback by default (`10.0.2.2:3000`).

- **Emulator:** no change needed.
- **Physical device:** edit `android/app/src/main/java/com/chatapp/data/Config.kt` to your PC's LAN IP, e.g. `http://192.168.1.50:3000/`, then rebuild. Both devices must be on the same network.

### 4. Test with two users

1. Register account A and account B.
2. Both log in — the contacts list shows every registered user with a green online dot.
3. Open a conversation and send messages; they appear instantly on the other device.
4. Attach a photo with the paperclip button; it uploads and displays as an image bubble.
5. Press Home on one device — new messages arrive as push notifications.

## REST API

All endpoints under `/api/auth/*` require `Authorization: Bearer <token>` except register/login.

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/auth/register` | Create account `{name, email, password}` → `{token, user}` |
| POST | `/api/auth/login` | Log in `{email, password}` → `{token, user}` |
| GET | `/api/auth/users?q=` | List registered users with live `online` status |
| GET | `/api/auth/messages/:userId` | Full chat history between you and another user |
| POST | `/api/media/upload` | Multipart image upload → `{url}` |
| GET | `/uploads/:filename` | Served media files |
| GET | `/health` | Server health check |

## Socket.io events

Client authenticates with `{ auth: { token } }` in the handshake.

| Event (emit) | Payload | Event (listen) | Description |
| --- | --- | --- | --- |
| `message:send` | `{to, type, content?/mediaUrl?}` | `message:ack` + `message:new` | Send a message (server persists & broadcasts) |
| `message:read` | `{from}` | `message:read` | Mark all messages from a user as read |
| `typing:start` / `typing:stop` | `{to}` | `typing:start` / `typing:stop` | Typing indicator |
| — | — | `user:status` | Presence change `{id, online, lastSeen}` |

## Security notes

- Passwords hashed with bcrypt (10 rounds); never stored in plain text.
- JWT signed with a server secret (`JWT_SECRET` env var — set it in production).
- Socket connections are authenticated; the server rejects invalid/missing tokens.
- Media uploads limited to 10 MB and JPEG/PNG/GIF/WebP only.
- The sample `JWT_SECRET` and cleartext HTTP are for local development. For production, run behind HTTPS (TLS) with a strong `JWT_SECRET` and a real database.

## Tech stack

- **Android:** Kotlin, Jetpack (AppCompat, Material, ViewBinding, Lifecycle), Retrofit + OkHttp + Gson, Socket.io Java client, Coil (images).
- **Backend:** Node.js, Express, Socket.io, JWT (`jsonwebtoken`), `bcryptjs`, Multer, JSON file store (no external DB required).

## License

MIT — see [LICENSE](LICENSE).
