const fastify = require('fastify')({ logger: true });
const Database = require('better-sqlite3');
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { pipeline } = require('node:stream/promises');

const fastifyMultipart = require('@fastify/multipart');
const fastifyStatic = require('@fastify/static');

const dataDir = path.resolve(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}
const uploadsDir = path.resolve(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir, { recursive: true });
}
const dbPath = path.join(dataDir, 'mood.db');
const db = new Database(dbPath);
db.pragma('journal_mode = WAL');
db.exec(`
  CREATE TABLE IF NOT EXISTS profiles (
    name TEXT PRIMARY KEY,
    avatar TEXT NOT NULL,
    mood TEXT NOT NULL,
    isFocused INTEGER NOT NULL
  )
`);

// Add partner_id column if it doesn't exist (migration)
try {
  const tableInfo = db.pragma('table_info(profiles)');
  const hasPartnerColumn = tableInfo.some(col => col.name === 'partner_id');
  if (!hasPartnerColumn) {
    db.exec('ALTER TABLE profiles ADD COLUMN partner_id TEXT');
    fastify.log.info('Added partner_id column to profiles table');
  }
} catch (err) {
  fastify.log.error(err, 'Failed to add partner_id column');
}

const DEFAULT_MOOD = 'Chilling';

const toProfile = (row, includePartner = false) => {
  const profile = {
    name: row.name,
    avatar: row.avatar,
    mood: row.mood,
    isFocused: Boolean(row.isFocused),
    partner_id: row.partner_id || null,
  };

  // Optionally include full partner profile to avoid circular references
  if (includePartner && row.partner_id) {
    const partnerRow = db.prepare('SELECT * FROM profiles WHERE name = ?').get(row.partner_id);
    if (partnerRow) {
      profile.partner = {
        name: partnerRow.name,
        avatar: partnerRow.avatar,
        mood: partnerRow.mood,
        isFocused: Boolean(partnerRow.isFocused),
      };
    }
  }

  return profile;
};

const makeAvatar = (name) =>
  `https://placehold.co/1200x1200?text=${encodeURIComponent(name)}`;

const toSafeBase = (value) =>
  value.replace(/[^a-z0-9_-]+/gi, '_').replace(/^_+|_+$/g, '') || 'avatar';

const buildBaseUrl = (request) => {
  const proto = request.headers['x-forwarded-proto'] || request.protocol || 'http';
  const host = request.headers['x-forwarded-host'] || request.headers['host'] || request.hostname;
  return `${proto}://${host}`;
};

const toLocalUploadFilename = (avatarUrl) => {
  if (!avatarUrl || typeof avatarUrl !== 'string') return null;

  try {
    const parsed = new URL(avatarUrl);
    if (!parsed.pathname.startsWith('/uploads/')) {
      return null;
    }
    return path.basename(decodeURIComponent(parsed.pathname));
  } catch {
    if (avatarUrl.startsWith('/uploads/')) {
      return path.basename(decodeURIComponent(avatarUrl.slice('/uploads/'.length)));
    }
    return null;
  }
};

const removeFileIfExists = async (filePath) => {
  try {
    await fs.promises.unlink(filePath);
  } catch (err) {
    if (err?.code !== 'ENOENT') {
      throw err;
    }
  }
};

const listReferencedAvatarFiles = () => {
  const rows = db.prepare('SELECT avatar FROM profiles').all();
  const files = new Set();
  for (const row of rows) {
    const filename = toLocalUploadFilename(row.avatar);
    if (filename) {
      files.add(filename);
    }
  }
  return files;
};

const cleanupOrphanedUploads = async () => {
  const referencedFiles = listReferencedAvatarFiles();
  const entries = await fs.promises.readdir(uploadsDir, { withFileTypes: true });
  let removed = 0;

  for (const entry of entries) {
    if (!entry.isFile()) continue;
    if (referencedFiles.has(entry.name)) continue;
    await removeFileIfExists(path.join(uploadsDir, entry.name));
    removed += 1;
  }

  fastify.log.info(
    {
      referenced: referencedFiles.size,
      scanned: entries.length,
      removed,
    },
    'Avatar cleanup job completed'
  );
};

const startAvatarCleanupJob = () => {
  const intervalMs = 6 * 60 * 60 * 1000; // Every 6 hours
  const timer = setInterval(() => {
    cleanupOrphanedUploads().catch((err) => {
      fastify.log.error(err, 'Avatar cleanup job failed');
    });
  }, intervalMs);
  timer.unref();
};

fastify.register(fastifyMultipart, {
  limits: { fileSize: 25 * 1024 * 1024 }
});

fastify.register(fastifyStatic, {
  root: uploadsDir,
  prefix: '/uploads/',
  decorateReply: true
});

// Root route
fastify.get('/', async () => ({ message: 'Welcome to Mood Share API' }));

// Health check endpoint
fastify.get('/health', async () => ({ status: 'ok', timestamp: new Date().toISOString() }));

fastify.post('/upload', async (request, reply) => {
  let name = (request.query?.name ?? '').toString().trim();

  if (!request.isMultipart()) {
    reply.code(400);
    return { error: 'multipart form data is required' };
  }

  const filePart = await request.file();
  if (!filePart) {
    reply.code(400);
    return { error: 'file is required' };
  }

  if (!name) {
    const field = filePart.fields?.name;
    const value = Array.isArray(field) ? field[0]?.value : field?.value;
    name = (value ?? '').toString().trim();
  }

  if (!name) {
    reply.code(400);
    return { error: 'name is required' };
  }

  const existing = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name);
  if (!existing) {
    reply.code(404);
    return { error: `profile for "${name}" not found` };
  }

  const allowedTypes = new Map([
    ['image/jpeg', '.jpg'],
    ['image/png', '.png'],
    ['image/webp', '.webp'],
    ['image/gif', '.gif'],
  ]);
  const allowedExts = new Set(['.jpg', '.jpeg', '.png', '.webp', '.gif']);
  let ext = allowedTypes.get(filePart.mimetype);
  if (!ext) {
    const candidate = path.extname(filePart.filename || '').toLowerCase();
    if (allowedExts.has(candidate)) {
      ext = candidate;
    }
  }
  if (!ext) {
    reply.code(415);
    return { error: 'unsupported file type' };
  }

  const safeBase = toSafeBase(name);
  const avatarHash = createHash('sha256').update(name).digest('hex').slice(0, 16);
  const filename = `${safeBase}-${avatarHash}${ext}`;
  const targetPath = path.join(uploadsDir, filename);

  const oldFilename = toLocalUploadFilename(existing.avatar);
  if (oldFilename && oldFilename !== filename) {
    const oldPath = path.join(uploadsDir, oldFilename);
    await removeFileIfExists(oldPath);
  }

  await pipeline(filePart.file, fs.createWriteStream(targetPath));

  const baseUrl = buildBaseUrl(request);
  const avatarUrl = `${baseUrl}/uploads/${encodeURIComponent(filename)}`;
  db.prepare('UPDATE profiles SET avatar = ? WHERE name = ?').run(avatarUrl, name);

  const updated = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name);
  return toProfile(updated, true);
});

fastify.get('/download/:filename', async (request, reply) => {
  const filename = path.basename(request.params.filename || '');
  if (!filename) {
    reply.code(400);
    return { error: 'filename is required' };
  }
  const filePath = path.join(uploadsDir, filename);
  if (!fs.existsSync(filePath)) {
    reply.code(404);
    return { error: 'file not found' };
  }
  return reply.sendFile(filename);
});

fastify.post('/register', async (request, reply) => {
  const name = (request.body?.name ?? '').toString().trim();
  if (name.length === 0) {
    reply.code(400);
    return { error: 'name is required' };
  }

  const avatar = makeAvatar(name);
  const info = db
    .prepare(
      `INSERT INTO profiles (name, avatar, mood, isFocused)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(name) DO NOTHING`
    )
    .run(name, avatar, DEFAULT_MOOD, 0);

  fastify.log.info({ name, info }, 'User registered');
  const created = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name);
  return toProfile(created);
});

fastify.get('/me', async (request, reply) => {
  const name = (request.query?.name ?? '').toString().trim();
  if (name.length === 0) {
    reply.code(400);
    return { error: 'query parameter "name" is required' };
  }

  const row = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name);
  if (!row) {
    reply.code(404);
    return { error: `profile for "${name}" not found` };
  }

  fastify.log.info({ name }, 'GET /me');
  return toProfile(row, true); // Include partner profile
});

fastify.patch('/status', async (request, reply) => {
 
 const name = (request.body?.name ?? '').toString().trim()
  const focusValue = request.body?.isFocused
  const moodValue = request.body?.mood?.toString().trim()

  if (name.length === 0) {
    reply.code(400)
    return { error: 'name is required' }
  }

  if (focusValue === undefined && (!moodValue || moodValue.length === 0)) {
    reply.code(400)
    return { error: 'at least one of isFocused or mood must be provided' }
  }

  const existing = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name)
  if (!existing) {
    reply.code(404)
    return { error: `profile for "${name}" not found` }
  }

  let updatedMood = existing.mood
  let updatedFocused = existing.isFocused

  const isAutoMood = existing.mood === 'Focused' || existing.mood === 'Chilling'

  if (typeof focusValue === 'boolean') {
    updatedFocused = focusValue ? 1 : 0

    if (isAutoMood) {
      updatedMood = focusValue ? 'Focused' : 'Chilling'
    }
  }

  if (moodValue && moodValue.length > 0) {
    updatedMood = moodValue
  }

  db.prepare(
    'UPDATE profiles SET mood = ?, isFocused = ? WHERE name = ?'
  ).run(updatedMood, updatedFocused, name)

  fastify.log.info({ name, isFocused: typeof focusValue === 'boolean' ? focusValue : undefined, mood: moodValue }, 'PATCH /status');
  const updated = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name);
  return toProfile(updated, true);
});

// Search for users by name
fastify.get('/search', async (request, reply) => {
  const query = (request.query?.q ?? '').toString().trim().toLowerCase();
  if (query.length === 0) {
    reply.code(400);
    return { error: 'query parameter "q" is required' };
  }

  const rows = db
    .prepare('SELECT * FROM profiles WHERE LOWER(name) LIKE ? LIMIT 10')
    .all(`%${query}%`);

  fastify.log.info({ query, count: rows.length }, 'GET /search');
  return rows.map(row => toProfile(row, false));
});

// Set or update partner relationship
fastify.patch('/partner', async (request, reply) => {
  const name = (request.body?.name ?? '').toString().trim();
  const partnerId = request.body?.partner_id?.toString().trim() || null;

  if (name.length === 0) {
    reply.code(400);
    return { error: 'name is required' };
  }

  const existing = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name);
  if (!existing) {
    reply.code(404);
    return { error: `profile for "${name}" not found` };
  }

  // Validate partner exists if provided
  if (partnerId) {
    const partnerExists = db.prepare('SELECT * FROM profiles WHERE name = ?').get(partnerId);
    if (!partnerExists) {
      reply.code(404);
      return { error: `profile for partner "${partnerId}" not found` };
    }

    // Update both users to be each other's partners
    db.prepare('UPDATE profiles SET partner_id = ? WHERE name = ?').run(partnerId, name);
    db.prepare('UPDATE profiles SET partner_id = ? WHERE name = ?').run(name, partnerId);

    fastify.log.info({ name, partnerId }, 'Partners linked');
  } else {
    // Remove partner relationship for both users
    const oldPartnerId = existing.partner_id;
    db.prepare('UPDATE profiles SET partner_id = NULL WHERE name = ?').run(name);
    if (oldPartnerId) {
      db.prepare('UPDATE profiles SET partner_id = NULL WHERE name = ?').run(oldPartnerId);
    }

    fastify.log.info({ name, oldPartnerId }, 'Partner relationship removed');
  }

  const updated = db.prepare('SELECT * FROM profiles WHERE name = ?').get(name);
  return toProfile(updated, true);
});

// Start server
const start = async () => {
  try {
    await cleanupOrphanedUploads();
    startAvatarCleanupJob();
    await fastify.listen({ port: 3000, host: '0.0.0.0' });
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
};

start();
