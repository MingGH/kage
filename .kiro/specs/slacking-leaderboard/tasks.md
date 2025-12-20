# Implementation Plan: 摸鱼排行榜 (Slacking Leaderboard)

## Overview

本实现计划将摸鱼排行榜功能分解为可执行的编码任务，按照数据层 → 服务层 → 命令层 → 定时任务 → 测试的顺序逐步实现。

## Tasks

- [x] 1. 创建数据库表和实体类
  - [x] 1.1 创建 SQL 脚本 `sql/slacking_daily_stats.sql`
    - 定义 `slacking_daily_stats` 表结构
    - 创建索引：`idx_slacking_stats_guild_date`、`idx_slacking_stats_guild_user`、`idx_slacking_stats_unique`
    - _Requirements: 4.1, 4.4, 4.5_
  - [x] 1.2 创建实体类 `SlackingDailyStats.java`
    - 包含字段：id, guildId, userId, userName, statDate, messageCount, totalScore, createdAt, updatedAt
    - 使用 Lombok @Data @Builder 注解
    - _Requirements: 4.3_
  - [x] 1.3 创建 DTO 类 `LeaderboardEntry.java` 和 `UserStats.java`
    - LeaderboardEntry: rank, userId, userName, messageCount, totalScore
    - UserStats: 今日/周/月积分和排名
    - _Requirements: 3.5, 6.2, 6.3_

- [x] 2. 创建 Repository 层
  - [x] 2.1 创建 `SlackingDailyStatsRepository.java`
    - 实现 upsert 方法（更新或插入统计记录）
    - 实现按日期查询排行榜方法
    - 实现按日期范围汇总查询方法（周榜、月榜）
    - 实现查询用户排名方法
    - _Requirements: 2.1, 2.2, 4.2, 4.6, 4.7_

- [x] 3. 创建积分计算器
  - [x] 3.1 创建 `ScoreCalculator.java` 组件
    - 实现 `calculateScore(String content)` 方法
    - 去除空白字符后计算长度
    - 根据长度返回 1-4 分
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_
  - [x] 3.2 创建 `ScoreCalculatorTest.java` 单元测试
    - 测试各长度区间的积分计算
    - 测试空白字符处理
    - 测试边界值
    - _Requirements: 8.1_

- [x] 4. 创建排行榜统计服务
  - [x] 4.1 创建 `LeaderboardStatsService.java`
    - 实现 `recordMessage()` 方法：计算积分并更新统计
    - 实现 `getDailyLeaderboard()` 方法：获取日榜
    - 实现 `getWeeklyLeaderboard()` 方法：获取周榜
    - 实现 `getMonthlyLeaderboard()` 方法：获取月榜
    - 实现 `getUserStats()` 方法：获取用户个人统计
    - 实现 `getUserRank()` 方法：获取用户排名
    - _Requirements: 2.1, 2.3, 2.4, 3.5, 4.6, 4.7, 6.2, 6.3, 6.4_
  - [x] 4.2 创建 `LeaderboardStatsServiceTest.java` 单元测试
    - 测试排行榜排序逻辑
    - 测试周/月日期范围计算
    - 测试用户排名查找
    - _Requirements: 8.2_

- [x] 5. 集成消息监听器
  - [x] 5.1 修改 `DiscordMessageListener.java`
    - 注入 LeaderboardStatsService
    - 在 `pushUserMessage()` 后调用 `recordMessage()` 更新统计
    - _Requirements: 2.1_

- [x] 6. Checkpoint - 确保数据层和服务层正常工作
  - 确保所有测试通过，如有问题请询问用户

- [x] 7. 创建排行榜命令
  - [x] 7.1 创建 `RankCommand.java`
    - 实现 UnifiedCommand 接口
    - 支持 Slash 命令 `/rank [period]`
    - 支持传统命令 `rank [day|week|month|me]`
    - 解析 period 参数（默认 day）
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 6.1_
  - [x] 7.2 实现排行榜展示逻辑
    - 使用 Discord Embed 格式
    - 前三名使用奖牌 emoji（🥇🥈🥉）
    - 显示统计时间范围
    - 第一名显示"摸鱼忍者王"称号
    - 显示用户自己的排名（如果不在前 10）
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 3.6_
  - [x] 7.3 实现个人积分查询（/rank me）
    - 显示今日/周/月积分和排名
    - 显示今日消息数量
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [x] 7.4 处理空数据情况
    - 排行榜为空时显示友好提示
    - 用户无记录时显示提示
    - _Requirements: 3.7_

- [x] 8. 创建每日播报功能
  - [x] 8.1 添加配置项到 `application.yaml`
    - 添加 `discord.leaderboard.broadcast-channel` 配置
    - _Requirements: 7.6_
  - [x] 8.2 创建 `LeaderboardBroadcastScheduler.java`
    - 使用 `@Scheduled(cron = "0 30 5 * * ?")` 定时执行
    - 查询前一天的排行榜数据
    - 生成播报消息（前三名 + 摸鱼忍者王称号）
    - 发送到配置的频道
    - 处理无数据情况
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 9. Checkpoint - 确保命令和播报功能正常工作
  - 确保所有测试通过，如有问题请询问用户

- [ ] 10. 完善测试覆盖
  - [ ] 10.1 创建 `RankCommandTest.java` 单元测试
    - 测试参数解析逻辑
    - 测试 Embed 格式生成
    - _Requirements: 8.3_
  - [ ] 10.2 创建 `LeaderboardBroadcastSchedulerTest.java` 单元测试
    - 测试播报消息格式
    - 测试空数据处理
    - _Requirements: 8.3_

- [x] 11. Final Checkpoint - 确保所有功能完整
  - 确保所有测试通过，如有问题请询问用户

## Notes

- All tasks are required for complete test coverage
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- 使用 Java 17 语法特性
- 遵循项目现有的代码风格和命名规范
