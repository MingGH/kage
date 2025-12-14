package run.runnable.kage.service.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDA 音频发送处理器，将 LavaPlayer 的音频发送到 Discord
 */
public class AudioPlayerSendHandler implements AudioSendHandler {

    private static final Logger log = LoggerFactory.getLogger(AudioPlayerSendHandler.class);
    
    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;
    private final AtomicInteger callCount = new AtomicInteger(0);

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        // JDA 需要有 backing array 的 ByteBuffer，不能用 allocateDirect
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        // 每次提供前先清空 buffer
        buffer.clear();
        boolean result = audioPlayer.provide(frame);
        
        // 只在第一次调用时打印日志
        if (callCount.incrementAndGet() == 1) {
            log.info("音频发送开始, 数据长度: {}", result ? frame.getDataLength() : 0);
        }
        
        return result;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        // flip 后返回，position=0, limit=数据长度
        ((java.nio.Buffer) buffer).flip();
        return buffer;
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
