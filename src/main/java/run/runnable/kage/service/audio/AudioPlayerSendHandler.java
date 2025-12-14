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
    
    // 预缓冲次数：100 次 * 20ms = 2 秒预缓冲时间
    private static final int PREBUFFER_COUNT = 100;
    
    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicInteger dataReadyCount = new AtomicInteger(0);
    private volatile boolean hasLoggedStart = false;
    private volatile boolean prebufferComplete = false;

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
        
        int count = callCount.incrementAndGet();
        
        // 预缓冲阶段：强制等待一段时间让 LavaPlayer 缓冲区填充
        if (!prebufferComplete) {
            if (result) {
                int ready = dataReadyCount.incrementAndGet();
                // 需要连续有数据 PREBUFFER_COUNT 次才认为缓冲区已填充
                if (ready >= PREBUFFER_COUNT) {
                    prebufferComplete = true;
                    log.info("预缓冲完成, 总调用次数: {}, 有效数据次数: {}", count, ready);
                }
            } else {
                // 如果中间断了，重置计数
                dataReadyCount.set(0);
            }
            
            if (count == 1) {
                log.info("音频预缓冲中...");
            }
            
            // 预缓冲阶段不发送数据，让缓冲区填充
            return false;
        }
        
        // 预缓冲结束后第一次有数据时打印日志
        if (result && !hasLoggedStart) {
            hasLoggedStart = true;
            log.info("音频发送开始, 数据长度: {}", frame.getDataLength());
        }
        
        // 如果连续返回 false，检查是否有正在播放的音轨
        // 只有在有音轨播放时才警告缓冲区空
        if (!result && count % 50 == 0 && audioPlayer.getPlayingTrack() != null) {
            log.warn("音频缓冲区空, 可能导致卡顿 (count={})", count);
        }
        
        return result;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        // 设置正确的 limit 为实际数据长度
        ((java.nio.Buffer) buffer).position(0);
        ((java.nio.Buffer) buffer).limit(frame.getDataLength());
        return buffer;
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
