const store = require('./src/store');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');

const accounts = [
  { name: 'Demo User', email: 'demo@chatapp.com', password: 'demo123' },
  { name: 'Alice', email: 'alice@chatapp.com', password: 'alice123' },
];

const users = store.loadUsers();
for (const acc of accounts) {
  const email = acc.email.toLowerCase();
  if (users.some((u) => u.email === email)) {
    console.log('exists:', email);
    continue;
  }
  users.push({
    id: crypto.randomUUID(),
    name: acc.name,
    email,
    passwordHash: bcrypt.hashSync(acc.password, 10),
    avatar: null,
    online: false,
    lastSeen: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  });
  console.log('created:', email);
}
store.saveUsers(users);
console.log('Done. Total users:', store.loadUsers().length);
