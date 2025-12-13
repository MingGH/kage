CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE chat_message IS 'AI 对话历史';
COMMENT ON COLUMN chat_message.guild_id IS 'Discord 服务器 ID';
COMMENT ON COLUMN chat_message.user_id IS 'Discord 用户 ID';
COMMENT ON COLUMN chat_message.role IS 'user 或 assistant';
COMMENT ON COLUMN chat_message.content IS '消息内容';
COMMENT ON COLUMN chat_message.deleted IS '是否已删除';

CREATE INDEX idx_chat_message_guild_user ON chat_message(guild_id, user_id);
CREATE INDEX idx_chat_message_created_at ON chat_message(created_at);
CREATE INDEX idx_chat_message_active ON chat_message(guild_id, user_id, created_at DESC) WHERE deleted = FALSE;

