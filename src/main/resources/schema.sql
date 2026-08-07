-- PostgreSQL-compatible schema matching the supplied ER diagram.
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    user_name VARCHAR(120) NOT NULL,
    user_email VARCHAR(320) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    gender VARCHAR(40),
    date_of_birth TIMESTAMPTZ
);

CREATE TABLE groups (
    group_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    creator_user_id BIGINT NOT NULL REFERENCES users(user_id),
    draw_date TIMESTAMPTZ NOT NULL
);

CREATE TABLE participants (
    group_id VARCHAR(64) NOT NULL REFERENCES groups(group_id),
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    friend_user_id BIGINT REFERENCES users(user_id),
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE chat (
    chat_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    friend_user_id BIGINT NOT NULL REFERENCES users(user_id),
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
