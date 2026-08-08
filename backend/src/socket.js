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

    function conversationRooms(message) {
      return ['user:' + message.from, 'user:' + message.to];
    }

    socket.on('message:send', (data, ack) => {
      const to = String((data && data.to) || '');
      const type = String((data && data.type) || 'text');
      const content = String((data && data.content) || '').trim();
      const mediaUrl = data && data.mediaUrl ? String(data.mediaUrl) : null;
      const msgId = data && data.id ? String(data.id) : null;

      const VALID_TYPES = ['text', 'image', 'video', 'voice', 'audio'];
      const NEEDS_MEDIA = ['image', 'video', 'voice', 'audio'];
      const finalType = VALID_TYPES.includes(type) ? type : 'text';

      if (!to) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Missing recipient.' });
        return;
      }
      if (finalType === 'text' && !content) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Empty message.' });
        return;
      }
      if (NEEDS_MEDIA.includes(finalType) && !mediaUrl) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Missing media URL.' });
        return;
      }

      const users = store.loadUsers();
      const recipient = users.find((u) => u.id === to);
      if (!recipient) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Recipient does not exist.' });
        return;
      }

      const messages = store.loadMessages();

      if (msgId) {
        const existing = messages.find((m) => m.id === msgId);
        if (existing) {
          if (typeof ack === 'function') ack({ ok: true, message: existing, duplicate: true });
          return;
        }
      }

      const message = {
        id: msgId || require('crypto').randomUUID(),
        from: userId,
        to,
        type: finalType,
        content: content || null,
        mediaUrl,
        duration: data && Number.isFinite(Number(data.duration)) ? Number(data.duration) : null,
        createdAt: nowIso(),
        read: false,
        readAt: null,
        edited: false,
        editedAt: null,
        deleted: false,
        deletedAt: null,
        reactions: {},
      };

      messages.push(message);
      store.saveMessages(messages);

      io.to('user:' + to).emit('message:new', message);
      socket.emit('message:ack', message);

      if (typeof ack === 'function') ack({ ok: true, message });
    });

    socket.on('message:edit', (data, ack) => {
      const id = String((data && data.id) || '');
      const content = String((data && data.content) || '').trim();

      if (!id) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Missing message id.' });
        return;
      }
      if (!content) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Empty message.' });
        return;
      }

      const messages = store.loadMessages();
      const message = messages.find((m) => m.id === id);
      if (!message) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Message not found.' });
        return;
      }
      if (message.from !== userId) {
        if (typeof ack === 'function') ack({ ok: false, error: 'You can only edit your own messages.' });
        return;
      }
      if (message.deleted) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Message was deleted.' });
        return;
      }

      message.content = content;
      message.edited = true;
      message.editedAt = nowIso();
      store.saveMessages(messages);

      conversationRooms(message).forEach((room) => io.to(room).emit('message:updated', message));
      if (typeof ack === 'function') ack({ ok: true, message });
    });

    socket.on('message:delete', (data, ack) => {
      const id = String((data && data.id) || '');
      if (!id) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Missing message id.' });
        return;
      }

      const messages = store.loadMessages();
      const message = messages.find((m) => m.id === id);
      if (!message) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Message not found.' });
        return;
      }
      if (message.from !== userId && message.to !== userId) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Not allowed.' });
        return;
      }
      if (message.deleted) {
        if (typeof ack === 'function') ack({ ok: true, message });
        return;
      }

      message.deleted = true;
      message.deletedAt = nowIso();
      message.content = null;
      message.mediaUrl = null;
      store.saveMessages(messages);

      conversationRooms(message).forEach((room) => io.to(room).emit('message:updated', message));
      if (typeof ack === 'function') ack({ ok: true, message });
    });

    socket.on('message:react', (data, ack) => {
      const id = String((data && data.id) || '');
      const emoji = String((data && data.emoji) || '');

      if (!id || !emoji) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Missing message id or emoji.' });
        return;
      }

      const messages = store.loadMessages();
      const message = messages.find((m) => m.id === id);
      if (!message) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Message not found.' });
        return;
      }
      if (message.from !== userId && message.to !== userId) {
        if (typeof ack === 'function') ack({ ok: false, error: 'Not allowed.' });
        return;
      }

      const reactions = message.reactions || {};
      const reactors = Array.isArray(reactions[emoji]) ? reactions[emoji] : [];
      const idx = reactors.indexOf(userId);
      if (idx >= 0) {
        reactors.splice(idx, 1);
        if (reactors.length === 0) delete reactions[emoji];
      } else {
        reactors.push(userId);
        reactions[emoji] = reactors;
      }
      message.reactions = reactions;
      store.saveMessages(messages);

      conversationRooms(message).forEach((room) => io.to(room).emit('message:updated', message));
      if (typeof ack === 'function') ack({ ok: true, message });
    });

    socket.on('user:avatar', (data) => {
      const avatarUrl = data && data.avatarUrl ? String(data.avatarUrl) : null;
      const users = store.loadUsers();
      const user = users.find((u) => u.id === userId);
      if (!user) return;
      user.avatar = avatarUrl;
      store.saveUsers(users);
      io.emit('user:status', publicUser(user));
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
