# Kage Bot (布布管家)

一个功能丰富的 Discord 机器人，基于 Spring Boot WebFlux 构建，集成 AI 对话和联网搜索能力。

[English](README.md)

## 🎯 立即体验

- **添加到你的服务器**: [安装 Kage Bot](https://discord.com/oauth2/authorize?client_id=1449365950947266670)
- **加入社区交流**: [Discord 摸鱼频道](https://discord.gg/UAC8NMsF)

## 📖 项目背景

我想打造一款自己能使用的中文 Discord 机器人，mee6 的机器人实在太贵了。现在有了 Claude，我相信没什么不能做到的。

## 🆕 最近更新

- **MCP 集成** - 添加 Jina MCP 支持，实现实时联网搜索和内容读取
- **Spring AI 1.1.0** - 升级到最新 Spring AI，原生支持 MCP 客户端
- **多轮对话** - AI 可以记住每个用户在每个服务器的对话上下文
- **命令系统重构** - 清晰的命令模式架构，易于扩展

## 🤝 参与贡献

欢迎大家积极贡献 PR！无论是新功能、Bug 修复还是改进建议，都非常欢迎。

## 功能特性

- 🤖 **AI 对话** - 集成 DeepSeek API，支持多轮对话上下文
- 🌐 **联网搜索** - 通过 MCP (Model Context Protocol) 集成 Jina AI，支持网页搜索和内容读取
- 🎰 **抽奖系统** - 发起抽奖、用户参与、自动开奖
- 📊 **投票系统** - 创建投票、多选项支持
- 📝 **消息记录** - 记录服务器消息，支持数据分析
- 🔧 **可扩展命令系统** - 支持传统命令和 Slash 命令

## 命令列表

### Slash 命令 (推荐)

| 命令 | 说明 |
|------|------|
| `/ask <问题>` | 向 AI 提问 |
| `/clear` | 清除 AI 对话历史 |
| `/lottery <奖品> <人数> <分钟>` | 发起抽奖 |
| `/poll <问题> <选项1> <选项2> ...` | 创建投票 |
| `/ping` | 测试机器人响应 |
| `/hello` | 打招呼 |
| `/help` | 显示帮助信息 |

### @提及对话

直接 @布布 即可与 AI 对话，支持联网搜索实时信息。

## 技术栈

- Java 17+
- Spring Boot 3.5.x (WebFlux)
- Spring AI 1.1.0
- JDA 5.x (Java Discord API)
- PostgreSQL (R2DBC)
- Redis
- DeepSeek API (OpenAI 兼容接口)
- Jina MCP (联网搜索)

## 架构

```
┌─────────────────┐     ┌──────────────────┐
│  Discord User   │────▶│  Discord Gateway │
└─────────────────┘     └────────┬─────────┘
                                 │
                        ┌────────▼─────────┐
                        │   Kage Bot       │
                        │  (Spring Boot)   │
                        └────────┬─────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        │                        │                        │
┌───────▼───────┐       ┌───────▼───────┐       ┌───────▼───────┐
│  DeepSeek AI  │       │   Jina MCP    │       │  PostgreSQL   │
│  (对话生成)    │       │  (联网搜索)    │       │   (数据存储)   │
└───────────────┘       └───────────────┘       └───────────────┘
```

## 快速开始

### 1. 环境要求

- JDK 17+
- PostgreSQL 14+
- Redis 6+
- Maven 3.8+

### 2. 配置

复制示例配置文件：

```bash
cp src/main/resources/application-dev.yaml.example src/main/resources/application-dev.yaml
```

编辑配置文件：

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/kage
    username: postgres
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password

spring.ai:
  openai:
    api-key: "your_deepseek_api_key"

jina:
  api-key: "your_jina_api_key"

discord:
  bot:
    token: "your_discord_bot_token"
```

### 3. 获取 API Keys

- **DeepSeek API Key**: [DeepSeek Platform](https://platform.deepseek.com/)
- **Jina API Key**: [Jina AI](https://jina.ai/) (用于联网搜索)
- **Discord Bot Token**: [Discord Developer Portal](https://discord.com/developers/applications)

### 4. 初始化数据库

```bash
psql -U postgres -d kage -f sql/chat_message.sql
psql -U postgres -d kage -f sql/user_message.sql
psql -U postgres -d kage -f sql/lottery.sql
psql -U postgres -d kage -f sql/poll.sql
```

### 5. 运行

```bash
# 开发环境
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 生产环境
./mvnw package -DskipTests
java -jar target/kage-*.jar
```

## MCP 工具列表

通过 Jina MCP 集成，AI 可以使用以下工具：

| 工具 | 说明 |
|------|------|
| `search_web` | 搜索网页内容 |
| `read_url` | 读取网页/PDF 内容 |
| `search_arxiv` | 搜索学术论文 (arXiv) |
| `search_ssrn` | 搜索社科论文 (SSRN) |
| `search_images` | 搜索图片 |
| `capture_screenshot_url` | 网页截图 |

## 添加新命令

实现 `Command` 接口：

```java
@Component
public class MyCommand implements Command {

    @Override
    public String getName() {
        return "mycommand";
    }

    @Override
    public String getDescription() {
        return "我的命令描述";
    }

    @Override
    public void execute(MessageCommandContext context) {
        context.reply("Hello!");
    }
}
```

## Docker 部署

```bash
# 构建镜像
./mvnw dockerfile:build

# 运行
docker run -d \
  -e DB_URL=r2dbc:postgresql://host:5432/kage \
  -e DEEPSEEK_API_KEY=xxx \
  -e JINA_API_KEY=xxx \
  -e DISCORD_BOT_TOKEN=xxx \
  kage:latest
```

## License

本项目采用自定义许可证，个人和非商业用途免费，商业用途需要获得授权。详见 [LICENSE](LICENSE) 文件。
