-- SQLite schema matching the supplied ER diagram.
CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_name TEXT NOT NULL,
    user_email TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    gender TEXT,
    date_of_birth TEXT
);

CREATE TABLE IF NOT EXISTS groups (
    group_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    creator_user_id INTEGER NOT NULL REFERENCES users(user_id),
    created_date TEXT,
    draw_date TEXT
);

CREATE TABLE IF NOT EXISTS participants (
    group_id TEXT NOT NULL REFERENCES groups(group_id),
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    joined_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    friend_user_id INTEGER REFERENCES users(user_id),
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS chat (
    chat_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    friend_user_id INTEGER NOT NULL REFERENCES users(user_id),
    message TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
