# Kage Bot

一个功能丰富的 Discord 机器人，基于 Spring Boot WebFlux 构建。

## 功能特性

- 🤖 **AI 对话** - 集成 DeepSeek API，支持多轮对话上下文
- 🎁 **抽奖系统** - 发起抽奖、用户参与、自动开奖
- 📝 **消息记录** - 记录服务器消息，支持数据分析
- 🔧 **可扩展命令系统** - 轻松添加新命令

## 命令列表

| 命令 | 说明 |
|------|------|
| `!ping` | 测试机器人响应 |
| `!hello` | 打招呼 |
| `!help` | 显示帮助信息 |
| `!ask <问题>` | 向 AI 提问 |
| `!clear` | 清除 AI 对话历史 |
| `!抽奖 <奖品> <人数> <分钟>` | 发起抽奖 |

## 技术栈

- Java 17+
- Spring Boot 3.x (WebFlux)
- JDA (Java Discord API)
- PostgreSQL (R2DBC)
- Redis
- DeepSeek API

## 快速开始

### 1. 环境要求

- JDK 17+
- PostgreSQL 14+
- Redis 6+
- Maven 3.8+

### 2. 配置

复制示例配置文件：

```bash
cp .env.example .env
```

编辑 `.env` 填入你的配置：

```env
DB_URL=r2dbc:postgresql://localhost:5432/kage
DB_USERNAME=postgres
DB_PASSWORD=your_password

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

DEEPSEEK_API_KEY=your_deepseek_api_key

DISCORD_BOT_TOKEN=your_discord_bot_token
```

### 3. 初始化数据库

执行 `sql/` 目录下的 SQL 文件：

```bash
psql -U postgres -d kage -f sql/chat_message.sql
psql -U postgres -d kage -f sql/user_message.sql
psql -U postgres -d kage -f sql/lottery.sql
```

### 4. 运行

```bash
# 使用 Maven
./mvnw spring-boot:run

# 或者打包后运行
./mvnw package
java -jar target/kage-*.jar
```

## Discord Bot 配置

1. 前往 [Discord Developer Portal](https://discord.com/developers/applications)
2. 创建新应用，获取 Bot Token
3. 开启以下 Intents：
   - MESSAGE CONTENT INTENT
   - SERVER MEMBERS INTENT
4. 生成邀请链接，添加 Bot 到你的服务器

## 添加新命令

实现 `Command` 接口并添加 `@Component` 注解：

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
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getChannel().sendMessage("Hello!").queue();
    }
}
```

## License

本项目采用自定义许可证，个人和非商业用途免费，商业用途需要获得授权。详见 [LICENSE](LICENSE) 文件。
