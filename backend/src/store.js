const fs = require('fs');
const path = require('path');

const DATA_DIR = path.join(__dirname, '..', 'data');
const USERS_FILE = path.join(DATA_DIR, 'users.json');
const MESSAGES_FILE = path.join(DATA_DIR, 'messages.json');

function ensureDirs() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.mkdirSync(path.join(DATA_DIR, '..', 'uploads'), { recursive: true });
}

function load(file, fallback) {
  ensureDirs();
  try {
    if (fs.existsSync(file)) {
      return JSON.parse(fs.readFileSync(file, 'utf-8'));
    }
  } catch (err) {
    console.error('Failed to read', file, err);
  }
  return fallback;
}

function save(file, data) {
  ensureDirs();
  const tmp = file + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf-8');
  fs.renameSync(tmp, file);
}

function loadUsers() {
  const users = load(USERS_FILE, []);
  if (!Array.isArray(users)) return [];
  return users;
}

function saveUsers(users) {
  save(USERS_FILE, users);
}

function loadMessages() {
  const messages = load(MESSAGES_FILE, []);
  if (!Array.isArray(messages)) return [];
  return messages;
}

function saveMessages(messages) {
  save(MESSAGES_FILE, messages);
}

module.exports = {
  loadUsers,
  saveUsers,
  loadMessages,
  saveMessages,
  DATA_DIR,
};
