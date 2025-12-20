# Requirements Document

## Introduction

本功能为 Discord 机器人添加"摸鱼排行榜"命令，让用户可以查看服务器内的"摸鱼忍者王"。系统基于用户发送的消息计算摸鱼积分，支持日榜、周榜、月榜三种统计维度。积分计算不仅考虑消息数量，还会根据消息长度给予额外积分奖励。

## Glossary

- **Leaderboard_System**: 摸鱼排行榜系统，负责统计和展示用户摸鱼积分排名
- **Slacking_Score**: 摸鱼积分，根据用户消息数量和内容长度计算得出的分数
- **Daily_Stats**: 每日统计数据，记录用户当天的摸鱼积分
- **Score_Calculator**: 积分计算器，根据消息内容计算摸鱼积分
- **Stats_Aggregator**: 统计聚合器，负责汇总日/周/月的积分数据
- **Redis_Cache**: Redis 缓存层，用于缓存热点查询数据
- **Slacking_Daily_Stats**: 每日统计表，存储用户每天的摸鱼积分数据
- **Broadcast_Scheduler**: 播报调度器，负责定时发送摸鱼王播报

## Requirements

### Requirement 1: 摸鱼积分计算

**User Story:** As a Discord 用户, I want 系统能够合理计算我的摸鱼积分, so that 排行榜能够公平反映每个人的摸鱼程度。

#### Acceptance Criteria

1. WHEN 用户发送一条消息 THEN THE Score_Calculator SHALL 为该消息计算基础积分 1 分
2. WHEN 用户发送的消息长度超过 20 个字符 THEN THE Score_Calculator SHALL 额外增加 1 分作为长消息奖励
3. WHEN 用户发送的消息长度超过 50 个字符 THEN THE Score_Calculator SHALL 额外增加 2 分（总计 3 分）
4. WHEN 用户发送的消息长度超过 100 个字符 THEN THE Score_Calculator SHALL 额外增加 3 分（总计 4 分）
5. WHEN 计算积分时 THEN THE Score_Calculator SHALL 忽略消息中的空白字符进行长度计算

### Requirement 2: 实时积分统计

**User Story:** As a 系统管理员, I want 系统能够实时统计用户的摸鱼积分, so that 用户可以随时查看最新的排行榜。

#### Acceptance Criteria

1. WHEN 用户发送消息后 THEN THE Leaderboard_System SHALL 立即计算积分并更新到 Slacking_Daily_Stats 表
2. WHEN 更新统计数据时 THEN THE Leaderboard_System SHALL 使用 guild_id、user_id 和日期作为唯一标识
3. WHEN 当天记录不存在时 THEN THE Leaderboard_System SHALL 创建新记录
4. WHEN 当天记录已存在时 THEN THE Leaderboard_System SHALL 累加消息数和积分

### Requirement 3: 排行榜查询命令

**User Story:** As a Discord 用户, I want 使用 /rank 命令查看摸鱼排行榜, so that 我可以知道谁是今天的摸鱼忍者王。

#### Acceptance Criteria

1. WHEN 用户执行 /rank 命令不带参数 THEN THE Leaderboard_System SHALL 显示当天的摸鱼排行榜
2. WHEN 用户执行 /rank day 命令 THEN THE Leaderboard_System SHALL 显示当天的摸鱼排行榜
3. WHEN 用户执行 /rank week 命令 THEN THE Leaderboard_System SHALL 显示本周的摸鱼排行榜
4. WHEN 用户执行 /rank month 命令 THEN THE Leaderboard_System SHALL 显示本月的摸鱼排行榜
5. WHEN 显示排行榜时 THEN THE Leaderboard_System SHALL 展示前 10 名用户的排名、用户名和积分
6. WHEN 显示排行榜时 THEN THE Leaderboard_System SHALL 在底部显示当前用户的排名和积分（如果不在前 10）
7. WHEN 排行榜数据为空时 THEN THE Leaderboard_System SHALL 显示友好的提示信息

### Requirement 6: 个人积分查询

**User Story:** As a Discord 用户, I want 查询自己的摸鱼积分详情, so that 我可以了解自己的摸鱼情况。

#### Acceptance Criteria

1. WHEN 用户执行 /rank me 命令 THEN THE Leaderboard_System SHALL 显示用户自己的积分详情
2. WHEN 显示个人积分时 THEN THE Leaderboard_System SHALL 展示今日积分、本周积分、本月积分
3. WHEN 显示个人积分时 THEN THE Leaderboard_System SHALL 展示用户在各时间维度的排名
4. WHEN 显示个人积分时 THEN THE Leaderboard_System SHALL 展示今日发送的消息数量

### Requirement 4: 数据持久化与统计表

**User Story:** As a 系统管理员, I want 系统能够持久化统计数据到数据库表, so that 周榜和月榜能够准确计算且数据不会丢失。

#### Acceptance Criteria

1. THE Leaderboard_System SHALL 使用 slacking_daily_stats 表存储每日统计数据
2. WHEN 用户发送消息时 THEN THE Leaderboard_System SHALL 实时更新当天的统计记录（使用 upsert 操作）
3. WHEN 存储统计数据时 THEN THE Stats_Snapshot SHALL 记录 guild_id、user_id、user_name、统计日期、消息数、总积分
4. THE Stats_Snapshot SHALL 在 guild_id 和 stat_date 字段上创建索引以优化查询性能
5. THE Stats_Snapshot SHALL 在 (guild_id, user_id, stat_date) 上创建唯一索引防止重复记录
6. WHEN 查询周榜时 THEN THE Stats_Aggregator SHALL 汇总本周一至今的所有每日统计数据
7. WHEN 查询月榜时 THEN THE Stats_Aggregator SHALL 汇总本月 1 日至今的所有每日统计数据

### Requirement 5: 排行榜展示格式

**User Story:** As a Discord 用户, I want 排行榜以美观的格式展示, so that 我可以清晰地看到排名信息。

#### Acceptance Criteria

1. WHEN 展示排行榜时 THEN THE Leaderboard_System SHALL 使用 Discord Embed 格式展示
2. WHEN 展示排行榜时 THEN THE Leaderboard_System SHALL 为前三名使用特殊的奖牌 emoji（🥇🥈🥉）
3. WHEN 展示排行榜时 THEN THE Leaderboard_System SHALL 显示统计时间范围（如"今日"、"本周"、"本月"）
4. WHEN 展示排行榜时 THEN THE Leaderboard_System SHALL 显示第一名为"摸鱼忍者王"称号
5. WHEN 用户查看自己的排名时 THEN THE Leaderboard_System SHALL 高亮显示用户自己的排名信息

### Requirement 7: 每日摸鱼王播报

**User Story:** As a Discord 服务器成员, I want 每天自动收到摸鱼忍者王的播报, so that 我可以知道昨天谁是最能摸鱼的人。

#### Acceptance Criteria

1. THE Leaderboard_System SHALL 每天早上 5:30 自动发送前一天的摸鱼忍者王播报
2. WHEN 发送播报时 THEN THE Leaderboard_System SHALL 发送到服务器的指定频道（可配置）
3. WHEN 发送播报时 THEN THE Leaderboard_System SHALL 展示前三名用户及其积分
4. WHEN 发送播报时 THEN THE Leaderboard_System SHALL 为第一名授予"摸鱼忍者王"称号
5. IF 前一天没有任何消息记录 THEN THE Leaderboard_System SHALL 发送"昨天没有人摸鱼"的提示
6. WHEN 配置播报频道时 THEN THE Leaderboard_System SHALL 支持通过配置文件指定频道名称

### Requirement 8: 单元测试覆盖

**User Story:** As a 开发者, I want 后端代码有完善的单元测试, so that 代码质量有保障且便于维护。

#### Acceptance Criteria

1. THE Score_Calculator SHALL 有单元测试覆盖所有积分计算规则
2. THE Stats_Aggregator SHALL 有单元测试覆盖日/周/月统计汇总逻辑
3. THE Leaderboard_System SHALL 有单元测试覆盖排行榜排序和格式化逻辑
4. WHEN 运行测试时 THEN 所有测试用例 SHALL 通过
5. THE 单元测试 SHALL 使用 JUnit 5 和 Mockito 框架
