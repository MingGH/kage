package run.runnable.kage.service.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 音轨调度器，管理播放队列
 */
@Slf4j
public class TrackScheduler extends AudioEventAdapter {

    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
    }

    /**
     * 添加音轨到队列，如果当前没有播放则立即播放
     */
    public void queue(AudioTrack track) {
        boolean started = player.startTrack(track, true);
        log.info("尝试播放音轨: {}, 结果: {}", track.getInfo().title, started ? "开始播放" : "加入队列");
        if (!started) {
            queue.offer(track);
        }
    }

    /**
     * 跳过当前音轨
     */
    public void nextTrack() {
        player.startTrack(queue.poll(), false);
    }

    /**
     * 获取队列
     */
    public BlockingQueue<AudioTrack> getQueue() {
        return queue;
    }

    /**
     * 清空队列
     */
    public void clearQueue() {
        queue.clear();
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // 只有在可以开始下一首的情况下才继续
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }
}
