const http = require('http');
const path = require('path');
const fs = require('fs');
const express = require('express');
const cors = require('cors');
const { Server } = require('socket.io');

const { createRouter } = require('./src/auth');
const { upload, UPLOADS_DIR } = require('./src/media');
const { createSocketServer } = require('./src/socket');
const store = require('./src/store');

const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

store.loadUsers();
store.loadMessages();

const app = express();
app.use(cors());
app.use(express.json());

app.use('/api/auth', createRouter());

app.post('/api/media/upload', upload.single('file'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded.' });
  }
  const base = `${req.protocol}://${req.get('host')}`;
  return res.status(201).json({
    url: `${base}/uploads/${req.file.filename}`,
    filename: req.file.filename,
    size: req.file.size,
    mimetype: req.file.mimetype,
  });
});

app.use('/uploads', express.static(UPLOADS_DIR, { maxAge: '7d' }));

app.get('/health', (req, res) => res.json({ status: 'ok', uptime: process.uptime() }));

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
  maxHttpBufferSize: 5e6,
});

createSocketServer(io);

server.listen(PORT, HOST, () => {
  console.log(`Chat backend listening on http://${HOST}:${PORT}`);
  console.log(`Media uploads served from ${UPLOADS_DIR}`);
  console.log('Directories:', fs.existsSync(path.join(__dirname, 'data')) ? 'ready' : 'created');
});

process.on('uncaughtException', (err) => {
  console.error('Uncaught exception:', err);
});
process.on('unhandledRejection', (err) => {
  console.error('Unhandled rejection:', err);
});
