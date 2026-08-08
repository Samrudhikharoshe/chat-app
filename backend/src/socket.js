const store = require('./store');
const { verifyToken, publicUser } = require('./auth');

function nowIso() {
  return new Date().toISOString();
}

function createSocketServer(io) {
  const online = new Map();

  io.use((socket, next) => {
    try {
      const token =
        (socket.handshake.auth && socket.handshake.auth.token) ||
        (socket.handshake.headers && socket.handshake.headers.authorization
          ? socket.handshake.headers.authorization.replace('Bearer ', '')
          : null);
      if (!token) return next(new Error('Authentication required.'));
      const payload = verifyToken(token);
      socket.userId = payload.uid;
      socket.userName = payload.name;
      next();
    } catch (err) {
      next(new Error('Invalid or expired token.'));
    }
  });

  function setOnline(userId, isOnline) {
    const users = store.loadUsers();
    const user = users.find((u) => u.id === userId);
    if (!user) return;
    user.online = isOnline;
    user.lastSeen = nowIso();
    store.saveUsers(users);
    io.emit('user:status', publicUser(user));
  }

  io.on('connection', (socket) => {
    const userId = socket.userId;
    socket.join('user:' + userId);
    online.set(userId, socket.id);
    setOnline(userId, true);

    socket.emit('self:online', { online: true });

    socket.on('user:presence', (data) => {
      io.emit('user:presence', { userId, online: !!(data && data.online) });
    });

    socket.on('message:send', (data, ack) => {
      const to = String((data && data.to) || '');
      const type = String((data && data.type) || 'text');
      const content = String((data && data.content) || '').trim();
      const mediaUrl = data && data.mediaUrl ? String(data.mediaUrl) : null;

      if (!to) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Missing recipient.' });
        return;
      }
      if (type === 'text' && !content) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Empty message.' });
        return;
      }
      if (type === 'image' && !mediaUrl) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Missing media URL.' });
        return;
      }

      const users = store.loadUsers();
      const recipient = users.find((u) => u.id === to);
      if (!recipient) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Recipient does not exist.' });
        return;
      }

      const message = {
        id: require('crypto').randomUUID(),
        from: userId,
        to,
        type: ['text', 'image'].includes(type) ? type : 'text',
        content: content || null,
        mediaUrl,
        createdAt: nowIso(),
        read: false,
      };

      const messages = store.loadMessages();
      messages.push(message);
      store.saveMessages(messages);

      io.to('user:' + to).emit('message:new', message);
      socket.emit('message:ack', message);

      if (typeof ack === 'function') ack({ ok: true, message });
    });

    socket.on('message:read', (data) => {
      const fromId = String((data && data.from) || '');
      if (!fromId) return;
      const messages = store.loadMessages();
      let changed = false;
      for (const m of messages) {
        if (m.from === fromId && m.to === userId && !m.read) {
          m.read = true;
          m.readAt = nowIso();
          changed = true;
        }
      }
      if (changed) {
        store.saveMessages(messages);
        io.to('user:' + fromId).emit('message:read', { from: userId, to: fromId });
      }
    });

    socket.on('typing:start', (data) => {
      const to = String((data && data.to) || '');
      if (!to) return;
      io.to('user:' + to).emit('typing:start', { from: userId, name: socket.userName });
    });

    socket.on('typing:stop', (data) => {
      const to = String((data && data.to) || '');
      if (!to) return;
      io.to('user:' + to).emit('typing:stop', { from: userId });
    });

    socket.on('disconnect', () => {
      if (online.get(userId) === socket.id) {
        online.delete(userId);
        setOnline(userId, false);
      }
    });
  });
}

module.exports = { createSocketServer };
