CREATE TABLE user_message (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    user_name VARCHAR(128),
    content TEXT NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_message IS '用户消息记录';
COMMENT ON COLUMN user_message.guild_id IS 'Discord 服务器 ID';
COMMENT ON COLUMN user_message.channel_id IS '频道 ID';
COMMENT ON COLUMN user_message.user_id IS '用户 ID';
COMMENT ON COLUMN user_message.user_name IS '用户名';
COMMENT ON COLUMN user_message.content IS '消息内容';
COMMENT ON COLUMN user_message.message_id IS 'Discord 消息 ID';

CREATE INDEX idx_user_message_guild ON user_message(guild_id);
CREATE INDEX idx_user_message_user ON user_message(guild_id, user_id);
CREATE INDEX idx_user_message_created ON user_message(created_at);
CREATE UNIQUE INDEX idx_user_message_msg_id ON user_message(message_id);
