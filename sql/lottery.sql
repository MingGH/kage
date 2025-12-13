-- 抽奖活动表
CREATE TABLE lottery (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64),
    creator_id VARCHAR(64) NOT NULL,
    prize VARCHAR(500) NOT NULL,
    winner_count INT NOT NULL DEFAULT 1,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lottery IS '抽奖活动';
COMMENT ON COLUMN lottery.prize IS '奖品描述';
COMMENT ON COLUMN lottery.winner_count IS '中奖人数';
COMMENT ON COLUMN lottery.status IS 'ACTIVE/ENDED/CANCELLED';

CREATE INDEX idx_lottery_guild ON lottery(guild_id);
CREATE INDEX idx_lottery_status_end ON lottery(status, end_time);

-- 抽奖参与者表
CREATE TABLE lottery_participant (
    id BIGSERIAL PRIMARY KEY,
    lottery_id BIGINT NOT NULL REFERENCES lottery(id),
    user_id VARCHAR(64) NOT NULL,
    user_name VARCHAR(128),
    is_winner BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(lottery_id, user_id)
);

COMMENT ON TABLE lottery_participant IS '抽奖参与者';

CREATE INDEX idx_participant_lottery ON lottery_participant(lottery_id);
