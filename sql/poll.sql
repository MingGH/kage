-- 投票表
CREATE TABLE poll (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    creator_id VARCHAR(64) NOT NULL,
    title VARCHAR(500) NOT NULL,
    end_time TIMESTAMP NOT NULL,
    multiple_choice BOOLEAN DEFAULT FALSE,
    anonymous BOOLEAN DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE poll IS '投票';
COMMENT ON COLUMN poll.title IS '投票标题';
COMMENT ON COLUMN poll.multiple_choice IS '是否允许多选';
COMMENT ON COLUMN poll.anonymous IS '是否匿名投票';
COMMENT ON COLUMN poll.status IS 'ACTIVE/ENDED';

CREATE INDEX idx_poll_guild ON poll(guild_id);
CREATE INDEX idx_poll_status_end ON poll(status, end_time);

-- 投票选项表
CREATE TABLE poll_option (
    id BIGSERIAL PRIMARY KEY,
    poll_id BIGINT NOT NULL REFERENCES poll(id),
    option_index INT NOT NULL,
    content VARCHAR(200) NOT NULL,
    UNIQUE(poll_id, option_index)
);

COMMENT ON TABLE poll_option IS '投票选项';

CREATE INDEX idx_poll_option_poll ON poll_option(poll_id);

-- 投票记录表
CREATE TABLE poll_vote (
    id BIGSERIAL PRIMARY KEY,
    poll_id BIGINT NOT NULL REFERENCES poll(id),
    option_id BIGINT NOT NULL REFERENCES poll_option(id),
    user_id VARCHAR(64) NOT NULL,
    user_name VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(poll_id, option_id, user_id)
);

COMMENT ON TABLE poll_vote IS '投票记录';

CREATE INDEX idx_poll_vote_poll ON poll_vote(poll_id);
CREATE INDEX idx_poll_vote_option ON poll_vote(option_id);
