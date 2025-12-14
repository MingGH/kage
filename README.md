# Kage Bot (布布管家)

A feature-rich Discord bot built with Spring Boot WebFlux, featuring AI conversations and web search capabilities.

[中文文档](README_CN.md)

## 🎯 Try It Out

- **Add to your server**: [Install Kage Bot](https://discord.com/oauth2/authorize?client_id=1449365950947266670)
- **Join our community**: [Discord Server ｜ 996Ninja 摸鱼忍者](https://discord.gg/UAC8NMsF)

## 📸 Screenshots

### AI-Powered Command Suggestions
![](https://img.996.ninja/ninjutsu/238f10d9328988b03f9e626d8403139d.png)

### Welcome New Members
![](https://img.996.ninja/ninjutsu/178ccf4fb65cd01d84624334c6194db3.png)

### AI + Jina MCP Web Search
![](https://img.996.ninja/ninjutsu/878d2a48da82c83d00169148a87404d5.png)

### Lottery System
![](https://img.996.ninja/ninjutsu/8f305fac4319af5244468abecf887f80.png)

### Low Memory Footprint
![](https://img.996.ninja/ninjutsu/5248ce3463eef8e6a4837bf5bc7920b5.png)

## 📖 Background

I wanted to build a Chinese Discord bot that I could actually use. Mee6 is way too expensive, and with Claude by my side, I believe nothing is impossible. This project is the result of that vision.

## 🆕 Recent Updates

- **MCP Integration** - Added Jina MCP support for real-time web search and content reading
- **Spring AI 1.1.0** - Upgraded to latest Spring AI with native MCP client support
- **Multi-turn Conversations** - AI remembers conversation context per user per server
- **Refactored Command System** - Clean command pattern architecture with easy extensibility

## 🤝 Contributing

PRs are welcome! Feel free to contribute new features, bug fixes, or improvements.

## Features

- 🤖 **AI Chat** - Powered by DeepSeek API with multi-turn conversation support
- 🌐 **Web Search** - Integrated with Jina AI via MCP (Model Context Protocol) for real-time web search and content reading
- 🎰 **Lottery System** - Create lotteries, user participation, automatic drawing
- 📊 **Poll System** - Create polls with multiple options, supports multiple choice & anonymous voting
- ⏰ **Off-work Countdown** - Set countdown timer with periodic reminders
- 🔮 **Daily Fortune** - Check your daily fortune and slacking index
- 📝 **Message Logging** - Record server messages for analytics
- 🔧 **Extensible Command System** - Support for both traditional and slash commands

## Commands

### Slash Commands (Recommended)

| Command | Description |
|---------|-------------|
| `/ask <question>` | Ask AI a question |
| `/clear` | Clear AI conversation history |
| `/lottery <prize> <winners> <minutes>` | Start a lottery |
| `/poll <title> <option1> <option2> ... <minutes>` | Create a poll (supports multiple choice & anonymous) |
| `/countdown <time>` | Set off-work countdown (e.g. `/countdown 18:00`) |
| `/countdown-cancel` | Cancel off-work countdown |
| `/fortune` | Check today's fortune and slacking index |
| `/ping` | Test bot response |
| `/hello` | Say hello |
| `/help` | Show help information |

### @Mention Commands

You can also use commands by mentioning the bot:

| Command | Description |
|---------|-------------|
| `@BuBu ask <question>` | Ask AI a question |
| `@BuBu 抽奖 <prize> <winners> <minutes>` | Start a lottery |
| `@BuBu 投票 <title> \| <opt1> \| <opt2> \| <minutes>` | Create a poll |
| `@BuBu countdown <time>` | Set off-work countdown |
| `@BuBu fortune` | Check today's fortune |

### @Mention Chat

Simply @BuBu to chat with AI, with support for real-time web search.

## Tech Stack

- Java 17+
- Spring Boot 3.5.x (WebFlux)
- Spring AI 1.1.0
- JDA 5.x (Java Discord API)
- PostgreSQL (R2DBC)
- Redis
- DeepSeek API (OpenAI-compatible)
- Jina MCP (Web Search)

## Architecture

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
│   (Chat AI)   │       │ (Web Search)  │       │  (Database)   │
└───────────────┘       └───────────────┘       └───────────────┘
```

## Quick Start

### 1. Requirements

- JDK 17+
- PostgreSQL 14+
- Redis 6+
- Maven 3.8+

### 2. Configuration

Copy the example config file:

```bash
cp src/main/resources/application-dev.yaml.example src/main/resources/application-dev.yaml
```

Edit the configuration:

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

### 3. Get API Keys

- **DeepSeek API Key**: [DeepSeek Platform](https://platform.deepseek.com/)
- **Jina API Key**: [Jina AI](https://jina.ai/) (for web search)
- **Discord Bot Token**: [Discord Developer Portal](https://discord.com/developers/applications)

### 4. Initialize Database

```bash
psql -U postgres -d kage -f sql/chat_message.sql
psql -U postgres -d kage -f sql/user_message.sql
psql -U postgres -d kage -f sql/lottery.sql
psql -U postgres -d kage -f sql/poll.sql
```

### 5. Run

```bash
# Development
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production
./mvnw package -DskipTests
java -jar target/kage-*.jar
```

## MCP Tools

AI can use the following tools via Jina MCP integration:

| Tool | Description |
|------|-------------|
| `search_web` | Search web content |
| `read_url` | Read webpage/PDF content |
| `search_arxiv` | Search academic papers (arXiv) |
| `search_ssrn` | Search social science papers (SSRN) |
| `search_images` | Search images |
| `capture_screenshot_url` | Capture webpage screenshot |

## Adding New Commands

Implement the `Command` interface:

```java
@Component
public class MyCommand implements Command {

    @Override
    public String getName() {
        return "mycommand";
    }

    @Override
    public String getDescription() {
        return "My command description";
    }

    @Override
    public void execute(MessageCommandContext context) {
        context.reply("Hello!");
    }
}
```

## Docker Deployment

```bash
# Build image
./mvnw dockerfile:build

# Run
docker run -d \
  -e DB_URL=r2dbc:postgresql://host:5432/kage \
  -e DEEPSEEK_API_KEY=xxx \
  -e JINA_API_KEY=xxx \
  -e DISCORD_BOT_TOKEN=xxx \
  kage:latest
```

## License

This project uses a custom license. Free for personal and non-commercial use. Commercial use requires authorization. See [LICENSE](LICENSE) for details.
