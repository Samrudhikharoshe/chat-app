const multer = require('multer');
const path = require('path');
const crypto = require('crypto');

const UPLOADS_DIR = path.join(__dirname, '..', 'uploads');

const ALLOWED = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/gif': '.gif',
  'image/webp': '.webp',
  'video/mp4': '.mp4',
  'video/webm': '.webm',
  'video/3gpp': '.3gp',
  'video/quicktime': '.mov',
  'audio/mpeg': '.mp3',
  'audio/mp4': '.m4a',
  'audio/x-m4a': '.m4a',
  'audio/webm': '.webm',
  'audio/ogg': '.ogg',
  'audio/x-wav': '.wav',
  'audio/wav': '.wav',
  'audio/aac': '.aac',
  'audio/amr': '.amr',
};

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOADS_DIR),
  filename: (req, file, cb) => {
    const ext =
      ALLOWED[file.mimetype] ||
      path.extname(file.originalname || '').toLowerCase() ||
      '.bin';
    cb(null, crypto.randomUUID() + ext);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 50 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (ALLOWED[file.mimetype]) return cb(null, true);
    cb(
      new Error(
        'Only JPEG, PNG, GIF, WebP, MP4, WebM, 3GP, MOV and common audio formats are supported.'
      )
    );
  },
});

module.exports = { upload, UPLOADS_DIR, ALLOWED };
