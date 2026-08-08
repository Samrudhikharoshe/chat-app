const multer = require('multer');
const path = require('path');
const crypto = require('crypto');

const UPLOADS_DIR = path.join(__dirname, '..', 'uploads');

const ALLOWED = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/gif': '.gif',
  'image/webp': '.webp',
};

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOADS_DIR),
  filename: (req, file, cb) => {
    const ext = ALLOWED[file.mimetype] || path.extname(file.originalname || '').toLowerCase() || '.bin';
    cb(null, crypto.randomUUID() + ext);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (ALLOWED[file.mimetype]) return cb(null, true);
    cb(new Error('Only JPEG, PNG, GIF and WebP images are supported.'));
  },
});

module.exports = { upload, UPLOADS_DIR, ALLOWED };
