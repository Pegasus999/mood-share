const fastify = require('fastify')({ logger: true });
const Database = require('better-sqlite3');
const fs = require('node:fs');
const path = require('node:path');

const dataDir = path.resolve(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
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
  `https://placehold.co/300?text=${encodeURIComponent(name)}`;

// Root route
fastify.get('/', async () => ({ message: 'Welcome to Mood Share API' }));

// Health check endpoint
fastify.get('/health', async () => ({ status: 'ok', timestamp: new Date().toISOString() }));

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
       ON CONFLICT(name) DO UPDATE SET avatar = excluded.avatar, mood = excluded.mood, isFocused = excluded.isFocused`
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
    await fastify.listen({ port: 3000, host: '0.0.0.0' });
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
};

start();
