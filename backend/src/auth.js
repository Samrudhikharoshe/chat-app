const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const store = require('./store');

const SECRET = process.env.JWT_SECRET || 'dev-secret-change-me-in-production';
const TOKEN_TTL = '30d';

function hashPassword(plain) {
  return bcrypt.hashSync(plain, 10);
}

function verifyPassword(plain, hash) {
  return bcrypt.compareSync(plain, hash);
}

function signToken(user) {
  return jwt.sign(
    { uid: user.id, email: user.email, name: user.name },
    SECRET,
    { expiresIn: TOKEN_TTL }
  );
}

function verifyToken(token) {
  return jwt.verify(token, SECRET);
}

function publicUser(user) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    avatar: user.avatar || null,
    online: !!user.online,
    lastSeen: user.lastSeen || null,
    createdAt: user.createdAt,
  };
}

function sanitizeSearch(q) {
  return String(q || '').trim().toLowerCase();
}

function createRouter() {
  const router = express.Router();

  router.post('/register', (req, res) => {
    const name = String(req.body.name || '').trim();
    const email = String(req.body.email || '').trim().toLowerCase();
    const password = String(req.body.password || '');

    if (!name || !email || !password) {
      return res.status(400).json({ error: 'Name, email and password are required.' });
    }
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      return res.status(400).json({ error: 'Invalid email address.' });
    }
    if (password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters.' });
    }

    const users = store.loadUsers();
    if (users.some((u) => u.email === email)) {
      return res.status(409).json({ error: 'An account with this email already exists.' });
    }

    const user = {
      id: crypto.randomUUID(),
      name,
      email,
      passwordHash: hashPassword(password),
      avatar: null,
      online: false,
      lastSeen: new Date().toISOString(),
      createdAt: new Date().toISOString(),
    };
    users.push(user);
    store.saveUsers(users);

    const token = signToken(user);
    return res.status(201).json({ token, user: publicUser(user) });
  });

  router.post('/login', (req, res) => {
    const email = String(req.body.email || '').trim().toLowerCase();
    const password = String(req.body.password || '');

    const users = store.loadUsers();
    const user = users.find((u) => u.email === email);
    if (!user || !verifyPassword(password, user.passwordHash)) {
      return res.status(401).json({ error: 'Invalid email or password.' });
    }

    const token = signToken(user);
    return res.json({ token, user: publicUser(user) });
  });

  router.get('/me', (req, res) => {
    const auth = (req.headers.authorization || '').split(' ');
    if (auth[0] !== 'Bearer' || !auth[1]) {
      return res.status(401).json({ error: 'Missing bearer token.' });
    }
    try {
      const payload = verifyToken(auth[1]);
      const user = store.loadUsers().find((u) => u.id === payload.uid);
      if (!user) return res.status(401).json({ error: 'User not found.' });
      return res.json({ user: publicUser(user) });
    } catch (err) {
      return res.status(401).json({ error: 'Invalid or expired token.' });
    }
  });

  router.get('/users', (req, res) => {
    const auth = (req.headers.authorization || '').split(' ');
    if (auth[0] !== 'Bearer' || !auth[1]) {
      return res.status(401).json({ error: 'Missing bearer token.' });
    }
    try {
      const payload = verifyToken(auth[1]);
      const me = store.loadUsers().find((u) => u.id === payload.uid);
      if (!me) return res.status(401).json({ error: 'User not found.' });

      let list = store.loadUsers().filter((u) => u.id !== me.id);
      const q = sanitizeSearch(req.query.q);
      if (q) {
        list = list.filter(
          (u) =>
            u.name.toLowerCase().includes(q) ||
            u.email.toLowerCase().includes(q)
        );
      }
      return res.json({ users: list.map(publicUser) });
    } catch (err) {
      return res.status(401).json({ error: 'Invalid or expired token.' });
    }
  });

  router.get('/messages/:userId', (req, res) => {
    const auth = (req.headers.authorization || '').split(' ');
    if (auth[0] !== 'Bearer' || !auth[1]) {
      return res.status(401).json({ error: 'Missing bearer token.' });
    }
    try {
      const payload = verifyToken(auth[1]);
      const meId = payload.uid;
      const peerId = String(req.params.userId);
      const limit = Math.min(parseInt(req.query.limit, 10) || 200, 500);
      const q = sanitizeSearch(req.query.q);

      const all = store.loadMessages();
      let history = all
        .filter(
          (m) =>
            (m.from === meId && m.to === peerId) ||
            (m.from === peerId && m.to === meId)
        )
        .slice(-limit);

      if (q) {
        history = history.filter((m) => !m.deleted && (m.content || '').toLowerCase().includes(q));
      }

      return res.json({ messages: history });
    } catch (err) {
      return res.status(401).json({ error: 'Invalid or expired token.' });
    }
  });

  return router;
}

module.exports = {
  createRouter,
  signToken,
  verifyToken,
  publicUser,
};
