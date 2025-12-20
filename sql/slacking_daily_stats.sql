-- 摸鱼排行榜每日统计表
CREATE TABLE slacking_daily_stats (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    user_name VARCHAR(128),
    stat_date DATE NOT NULL,
    message_count INTEGER DEFAULT 0,
    total_score INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE slacking_daily_stats IS '摸鱼排行榜每日统计';
COMMENT ON COLUMN slacking_daily_stats.guild_id IS 'Discord 服务器 ID';
COMMENT ON COLUMN slacking_daily_stats.user_id IS '用户 ID';
COMMENT ON COLUMN slacking_daily_stats.user_name IS '用户名';
COMMENT ON COLUMN slacking_daily_stats.stat_date IS '统计日期';
COMMENT ON COLUMN slacking_daily_stats.message_count IS '消息数量';
COMMENT ON COLUMN slacking_daily_stats.total_score IS '总积分';

-- 索引优化查询性能
CREATE INDEX idx_slacking_stats_guild_date ON slacking_daily_stats(guild_id, stat_date);
CREATE INDEX idx_slacking_stats_guild_user ON slacking_daily_stats(guild_id, user_id);
CREATE UNIQUE INDEX idx_slacking_stats_unique ON slacking_daily_stats(guild_id, user_id, stat_date);
