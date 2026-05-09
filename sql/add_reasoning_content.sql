-- 添加 reasoning_content 列，用于存储 DeepSeek thinking 模型的推理过程
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS reasoning_content TEXT;

COMMENT ON COLUMN chat_message.reasoning_content IS 'DeepSeek thinking 模型的推理过程（仅 assistant 消息）';
