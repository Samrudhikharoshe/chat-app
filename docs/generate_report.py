#!/usr/bin/env python3
"""Generates Chat App Project Report PDF (docs/Chat_App_Project_Report.pdf)."""

import os
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import (
    Image, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle,
)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "Chat_App_Project_Report.pdf")

PRIMARY = colors.HexColor("#4F46E5")
DARK = colors.HexColor("#1E1B4B")
GRAY = colors.HexColor("#475569")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    "TitleX", parent=styles["Title"], fontSize=26, textColor=DARK,
    spaceAfter=6, alignment=TA_CENTER,
))
styles.add(ParagraphStyle(
    "SubTitle", parent=styles["Normal"], fontSize=13, textColor=GRAY,
    alignment=TA_CENTER, spaceAfter=4,
))
styles.add(ParagraphStyle(
    "H1", parent=styles["Heading1"], fontSize=15, textColor=PRIMARY,
    spaceBefore=14, spaceAfter=6,
))
styles.add(ParagraphStyle(
    "H2", parent=styles["Heading2"], fontSize=12, textColor=DARK,
    spaceBefore=8, spaceAfter=4,
))
styles.add(ParagraphStyle(
    "Body", parent=styles["BodyText"], fontSize=10, leading=15, textColor=colors.HexColor("#1F2937"),
))
styles.add(ParagraphStyle(
    "Small", parent=styles["BodyText"], fontSize=8.5, leading=12, textColor=GRAY,
))


def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(PRIMARY)
    canvas.rect(0, A4[1] - 18, A4[0], 18, stroke=0, fill=1)
    canvas.setFillColor(colors.white)
    canvas.setFont("Helvetica-Bold", 9)
    canvas.drawString(2 * cm, A4[1] - 12.5, "Chat Application - Project Report")
    canvas.setFont("Helvetica", 8)
    canvas.drawRightString(A4[0] - 2 * cm, A4[1] - 12.5, "Real-Time Messaging")
    canvas.setFillColor(GRAY)
    canvas.setFont("Helvetica", 8)
    canvas.drawCentredString(A4[0] / 2, 1.2 * cm, f"Page {doc.page}")
    canvas.restoreState()


story = []

# ---------------- Title page ----------------
story.append(Spacer(1, 3.5 * cm))
story.append(Paragraph("Chat Application", styles["Title"]))
story.append(Paragraph("Real-Time Messaging with Secure Authentication", styles["SubTitle"]))
story.append(Spacer(1, 0.4 * cm))
story.append(Paragraph("Project Report", styles["SubTitle"]))
story.append(Spacer(1, 1.2 * cm))

try:
    icon = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_icon.png")
    if os.path.exists(icon):
        story.append(Image(icon, width=3 * cm, height=3 * cm))
        story.append(Spacer(1, 1.2 * cm))
except Exception:
    pass

meta = Table(
    [
        ["Document", "Project Report"],
        ["Version", "1.0"],
        ["Date", "August 2026"],
        ["Stack", "Android (Kotlin) + Node.js / Socket.io"],
        ["Deliverables", "Source code, README, APK, this report"],
    ],
    colWidths=[4.5 * cm, 11 * cm],
)
meta.setStyle(TableStyle([
    ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
    ("FONTNAME", (1, 0), (1, -1), "Helvetica"),
    ("FONTSIZE", (0, 0), (-1, -1), 10),
    ("TEXTCOLOR", (0, 0), (0, -1), PRIMARY),
    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F1F5F9")),
    ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#E2E8F0")),
    ("INNERGRID", (0, 0), (-1, -1), 0.25, colors.HexColor("#E2E8F0")),
    ("TOPPADDING", (0, 0), (-1, -1), 8),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
    ("LEFTPADDING", (0, 0), (-1, -1), 10),
]))
story.append(meta)
story.append(PageBreak())

# ---------------- Abstract ----------------
story.append(Paragraph("Abstract", styles["H1"]))
story.append(Paragraph(
    "This project presents a real-time messaging application that enables users to exchange text and "
    "media instantly over a WebSocket connection while protecting accounts with token-based "
    "authentication. The system is split into a native Android client written in Kotlin and a "
    "lightweight Node.js backend that combines a REST API (authentication, contacts, chat history, "
    "media upload) with a Socket.io server for live message delivery, presence, typing indicators and "
    "read receipts. Users register or log in with an email and password that is hashed using bcrypt, "
    "receive a signed JWT, and then communicate with any other registered user in real time. Messages "
    "are persisted on the server so full chat history can be replayed at any time, and a foreground "
    "service delivers push-style notifications when new messages arrive while the app is in the "
    "background. The deliverables include the complete source code, this report, a public repository, "
    "and a ready-to-install APK.",
    styles["Body"],
))

# ---------------- Introduction ----------------
story.append(Paragraph("1. Introduction", styles["H1"]))
story.append(Paragraph(
    "Instant messaging is one of the most widely used forms of digital communication. Modern chat "
    "applications are expected to deliver messages with low latency, show whether contacts are online, "
    "support photo sharing, keep a searchable history, and notify users when they are not actively "
    "looking at the screen. This project implements all of these capabilities with a clean, layered "
    "architecture that is easy to run locally and extend in the future.",
    styles["Body"],
))

# ---------------- Objectives ----------------
story.append(Paragraph("2. Objectives", styles["H1"]))
objectives = [
    "Implement secure user registration and login with password hashing and JWT sessions.",
    "Provide real-time, low-latency message exchange using Socket.io over WebSockets.",
    "Broadcast live online/offline status across all connected clients.",
    "Send push-style notifications for messages received while the app is backgrounded.",
    "Allow image sharing through a picker, upload endpoint and inline rendering.",
    "Persist messages on the server and cache them locally for full chat history.",
    "Ship a documented codebase, a prebuilt APK and a project report.",
]
for i, obj in enumerate(objectives, 1):
    story.append(Paragraph(f"{i}. {obj}", styles["Body"]))
story.append(Paragraph(
    "The application is also designed to demonstrate end-to-end networking on a single machine: two "
    "Android emulator instances (or an emulator and a physical device) can chat through one local "
    "Node.js server.",
    styles["Body"],
))

# ---------------- Tech stack ----------------
story.append(Paragraph("3. Technology Stack", styles["H1"]))
tech = Table(
    [
        ["Layer", "Technology", "Purpose"],
        ["Android client", "Kotlin", "Primary app language"],
        ["UI", "Jetpack / Material 3 / ViewBinding", "Screens, components, typed view access"],
        ["Networking (REST)", "Retrofit + OkHttp + Gson", "Auth, contacts, history, media upload"],
        ["Real-time layer", "Socket.io Java client", "Live messages, presence, typing, receipts"],
        ["Images", "Coil", "Async image loading in chat bubbles"],
        ["Backend", "Node.js + Express", "REST endpoints and static media"],
        ["Realtime server", "Socket.io (Node)", "Bidirectional event transport"],
        ["Auth", "jsonwebtoken + bcryptjs", "JWT sessions and password hashing"],
        ["Uploads", "Multer", "Multipart image upload handling"],
        ["Storage", "JSON file store + SharedPreferences", "Server persistence & local cache"],
    ],
    colWidths=[3.4 * cm, 6.2 * cm, 5.9 * cm],
)
tech.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), PRIMARY),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
    ("FONTSIZE", (0, 0), (-1, -1), 9),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#CBD5E1")),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAFC")]),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ("LEFTPADDING", (0, 0), (-1, -1), 6),
]))
story.append(tech)

# ---------------- Architecture ----------------
story.append(Paragraph("4. System Architecture", styles["H1"]))
story.append(Paragraph(
    "The system follows a classic client-server model. The Android app is the only client; the Node.js "
    "process hosts both the REST API and the Socket.io engine on the same port. Communication is split "
    "into two channels:",
    styles["Body"],
))
arch = [
    ("REST channel", "Used for account creation, login, listing contacts, fetching chat history and "
     "uploading images. Every request is authenticated with a JWT sent in the Authorization header."),
    ("WebSocket channel", "Used for everything real-time: sending/receiving messages, online status "
     "broadcasts, typing indicators and read receipts. The client passes its JWT in the socket "
     "handshake, which the server validates before the connection is accepted."),
]
for title, text in arch:
    story.append(Paragraph(f"<b>{title}.</b> {text}", styles["Body"]))

story.append(Paragraph("Data flow", styles["H2"]))
story.append(Paragraph(
    "When user A sends a message, the client emits a <font face='Courier'>message:send</font> event. "
    "The server validates the payload, persists the message to the JSON store, broadcasts "
    "<font face='Courier'>message:new</font> to the recipient's room and confirms to the sender with a "
    "server-side acknowledgement. Presence changes are propagated with "
    "<font face='Courier'>user:status</font> events so every client always sees accurate online "
    "indicators. Read receipts travel in the reverse direction via "
    "<font face='Courier'>message:read</font>.",
    styles["Body"],
))

story.append(Paragraph("Android client architecture", styles["H2"]))
story.append(Paragraph(
    "The Android app is organised into a data layer (Retrofit service, SocketManager singleton, "
    "session store and message cache) and a UI layer (Login, Contacts and Chat screens). The "
    "SocketManager is a process-wide singleton created on app start; activities register and "
    "unregister listeners so the same live connection serves every screen. Chat history is loaded from "
    "the server on opening a conversation and mirrored to a local cache so the app still shows recent "
    "messages offline.",
    styles["Body"],
))

# ---------------- Features ----------------
story.append(Paragraph("5. Feature Implementation", styles["H1"]))

features = [
    ("5.1 User Authentication",
     "Registration and login are REST calls to /api/auth/register and /api/auth/login. Passwords are "
     "hashed with bcrypt (10 salt rounds) and never stored or transmitted in plain text. A successful "
     "request returns a signed JWT (30-day expiry by default) plus the user profile. The token is "
     "stored in SharedPreferences, attached to every REST request as a Bearer token, and used to "
     "authenticate the socket handshake. Validation covers required fields, email format and minimum "
     "password length, with per-field error messages in the UI."),
    ("5.2 Real-Time Messaging",
     "Messages are delivered over the persistent WebSocket connection, giving near-instant delivery "
     "without HTTP polling. The sender's message appears after the server acknowledges it; incoming "
     "messages are pushed to the open conversation in real time. Conversations automatically mark "
     "incoming messages as read and the sender sees a tick/double-tick receipt indicator."),
    ("5.3 Online Status",
     "The server tracks the socket id of every connected user. On connect/disconnect it updates the "
     "user record and broadcasts a user:status event containing the user id, the online flag and a "
     "last-seen timestamp. The contacts screen renders a green dot for online users and refreshes "
     "instantly when a user comes online or goes offline; the chat header shows the same live status."),
    ("5.4 Push Notifications",
     "A foreground service (ChatConnectionService) keeps the socket alive while the app is in the "
     "background. When a message arrives and the app is not visible, the service builds a high-priority "
     "notification with a pending intent that opens the correct conversation. Notifications require the "
     "POST_NOTIFICATIONS runtime permission on Android 13+ and are requested automatically after "
     "login."),
    ("5.5 Media Sharing",
     "The attach button opens the system image picker (ActivityResultContracts.GetContent). The chosen "
     "image is uploaded as a multipart request to /api/media/upload, which validates type and size "
     "(JPEG/PNG/GIF/WebP, max 10 MB) and returns a public URL. The client then sends an image-type "
     "message containing that URL; both bubbles render the photo with Coil and tap-through opens the "
     "full image."),
    ("5.6 Chat History",
     "Every message is persisted by the server in backend/data/messages.json with sender, recipient, "
     "type, content, timestamp and read state. The REST endpoint /api/auth/messages/:userId returns the "
     "complete thread between two users (latest N messages, capped at 500). The Android client caches "
     "threads locally in SharedPreferences, so history is available immediately when reopening a "
     "conversation and is then reconciled with the server."),
]
for title, text in features:
    story.append(Paragraph(title, styles["H2"]))
    story.append(Paragraph(text, styles["Body"]))

# ---------------- API & Events ----------------
story.append(Paragraph("6. API and Event Reference", styles["H1"]))
story.append(Paragraph("REST endpoints (all under /api/auth/* require a Bearer token):", styles["Body"]))
api = Table(
    [
        ["Method", "Endpoint", "Description"],
        ["POST", "/api/auth/register", "Create an account, returns {token, user}"],
        ["POST", "/api/auth/login", "Authenticate, returns {token, user}"],
        ["GET", "/api/auth/users?q=", "List users with live online status"],
        ["GET", "/api/auth/messages/:userId", "Full chat history with a user"],
        ["POST", "/api/media/upload", "Multipart image upload, returns {url}"],
        ["GET", "/uploads/:filename", "Serve uploaded media"],
        ["GET", "/health", "Server health check"],
    ],
    colWidths=[2.6 * cm, 5.6 * cm, 7.3 * cm],
)
api.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), PRIMARY),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
    ("FONTSIZE", (0, 0), (-1, -1), 8.5),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#CBD5E1")),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAFC")]),
    ("TOPPADDING", (0, 0), (-1, -1), 4),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
]))
story.append(api)
story.append(Spacer(1, 0.3 * cm))

story.append(Paragraph("Key Socket.io events:", styles["Body"]))
ev = Table(
    [
        ["Emit", "Payload", "Receives", "Purpose"],
        ["message:send", "{to, type, content/mediaUrl}", "message:new, message:ack", "Send a message"],
        ["message:read", "{from}", "message:read", "Read receipts"],
        ["typing:start / stop", "{to}", "typing:start / stop", "Typing indicator"],
        ["-", "-", "user:status", "Presence broadcast"],
    ],
    colWidths=[4.2 * cm, 3.6 * cm, 4.2 * cm, 3.5 * cm],
)
ev.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), PRIMARY),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
    ("FONTSIZE", (0, 0), (-1, -1), 8.5),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#CBD5E1")),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAFC")]),
    ("TOPPADDING", (0, 0), (-1, -1), 4),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
]))
story.append(ev)

# ---------------- Security ----------------
story.append(Paragraph("7. Security Considerations", styles["H1"]))
security = [
    "Passwords are hashed with bcrypt; the plaintext is never persisted or logged.",
    "JWTs are signed with a server-side secret and carry the user id, name and email.",
    "Socket handshakes are authenticated and rejected when the token is missing, invalid or expired.",
    "All REST endpoints except register/login validate the Bearer token.",
    "Uploaded files are restricted to image MIME types and a 10 MB limit; filenames are regenerated to "
    "avoid path traversal.",
    "Cleartext HTTP is enabled for local development only; production deployments should use HTTPS and "
    "a strong, environment-provided JWT_SECRET.",
]
for s in security:
    story.append(Paragraph(f"- {s}", styles["Body"]))

# ---------------- Testing ----------------
story.append(Paragraph("8. Testing", styles["H1"]))
story.append(Paragraph(
    "The backend was validated end-to-end with an automated script that exercises the full user "
    "journey against a running server. The following scenarios passed:",
    styles["Body"],
))
tests = [
    "Registration returns 201 with a token and profile; duplicate email returns 409.",
    "Login returns 200 with a token; wrong credentials return 401.",
    "Two socket clients connect with JWT handshake auth.",
    "Sending a message returns a success acknowledgement to the sender.",
    "The recipient receives the message in real time over the socket.",
    "Read receipts propagate back to the sender.",
    "GET /api/auth/messages/:userId replays the stored chat history.",
    "Disconnecting a client broadcasts an accurate offline presence event.",
    "Multipart image upload returns a 201 with a publicly servable URL.",
]
for t in tests:
    story.append(Paragraph(f"- {t}", styles["Body"]))
story.append(Paragraph(
    "The Android app builds cleanly with Gradle (AGP 8.9.2, Kotlin 2.0.21, compileSdk 36) and "
    "produces a debug APK of approximately 7.4 MB.",
    styles["Body"],
))

# ---------------- Deliverables ----------------
story.append(Paragraph("9. Deliverables", styles["H1"]))
deliv = [
    "Public GitHub repository containing the full project.",
    "Complete source code for the Android client (Kotlin) and Node.js backend.",
    "README documentation covering setup, configuration and usage.",
    "Prebuilt APK at apk/ChatApp-v1.0.0-debug.apk.",
    "This project report (PDF).",
]
for d in deliv:
    story.append(Paragraph(f"- {d}", styles["Body"]))

# ---------------- Conclusion ----------------
story.append(Paragraph("10. Conclusion and Future Scope", styles["H1"]))
story.append(Paragraph(
    "The Chat Application meets every stated requirement: secure authentication, real-time messaging, "
    "online status, push notifications, media sharing and persistent chat history. Its layered design "
    "keeps the networking, data and UI concerns separate, which makes the codebase readable and "
    "extensible. Because the backend stores data as JSON files and requires no external services, the "
    "entire system can be demonstrated on a single machine in minutes.",
    styles["Body"],
))
story.append(Paragraph(
    "Future work could replace the JSON file store with a production database (PostgreSQL or MongoDB), "
    "add end-to-end encryption for message payloads, integrate Firebase Cloud Messaging for delivery "
    "even when the app process is killed, support voice/video calls and group chats, add push-to-refresh "
    "pagination for very long threads, and publish the app to Google Play with a release-signed APK.",
    styles["Body"],
))

story.append(Spacer(1, 0.6 * cm))
story.append(Paragraph("--- End of report ---", styles["Small"]))


doc = SimpleDocTemplate(
    OUT, pagesize=A4,
    leftMargin=2 * cm, rightMargin=2 * cm, topMargin=2.2 * cm, bottomMargin=2 * cm,
    title="Chat Application - Project Report", author="Chat App",
)
doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
print(f"Report generated: {OUT}")
