package run.runnable.kage.command.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.runnable.kage.command.CommandContext;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PingCommandTest {

    @Mock
    private CommandContext ctx;

    @Test
    @DisplayName("Ping命令测试")
    void execute_shouldReplyPong() {
        PingCommand command = new PingCommand();
        command.execute(ctx);
        verify(ctx).reply("Pong! 🏓");
    }
}
