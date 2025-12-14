package run.runnable.kage.service;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Service;
import run.runnable.kage.service.audio.GuildMusicManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 音乐播放服务
 */
@Slf4j
@Service
public class MusicService {

    private AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.playerManager = new DefaultAudioPlayerManager();
        
        // 优化配置：增加缓冲区大小，减少卡顿
        playerManager.getConfiguration().setFrameBufferFactory(
                NonAllocatingAudioFrameBuffer::new
        );
        
        // 设置缓冲时间（默认 5000ms，增加到 15 秒）
        playerManager.setFrameBufferDuration(15000);
        
        // 注册远程音源（HTTP、YouTube 等）
        AudioSourceManagers.registerRemoteSources(playerManager);
        // 注册本地音源
        AudioSourceManagers.registerLocalSource(playerManager);
        log.info("音乐服务初始化完成");
    }

    /**
     * 获取或创建服务器的音乐管理器
     */
    public GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), guildId -> {
            GuildMusicManager manager = new GuildMusicManager(playerManager);
            guild.getAudioManager().setSendingHandler(manager.getSendHandler());
            return manager;
        });
    }

    /**
     * 加入语音频道
     */
    public boolean joinVoiceChannel(Guild guild, VoiceChannel channel) {
        AudioManager audioManager = guild.getAudioManager();
        try {
            audioManager.openAudioConnection(channel);
            log.info("加入语音频道: {} ({})", channel.getName(), guild.getName());
            return true;
        } catch (Exception e) {
            log.error("加入语音频道失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 离开语音频道
     */
    public void leaveVoiceChannel(Guild guild) {
        AudioManager audioManager = guild.getAudioManager();
        audioManager.closeAudioConnection();
        
        // 清理资源
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager != null) {
            manager.getPlayer().stopTrack();
            manager.getScheduler().clearQueue();
        }
        log.info("离开语音频道: {}", guild.getName());
    }

    /**
     * 播放音乐
     */
    public void loadAndPlay(Guild guild, String trackUrl, Consumer<String> callback) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.getScheduler().queue(track);
                String msg = String.format("🎵 已添加到播放队列: **%s**\n⏱️ 时长: %s",
                        track.getInfo().title,
                        formatDuration(track.getDuration()));
                callback.accept(msg);
                log.info("加载音轨: {}", track.getInfo().title);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                // 如果是播放列表，只播放第一首
                AudioTrack firstTrack = playlist.getSelectedTrack();
                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }
                musicManager.getScheduler().queue(firstTrack);
                String msg = String.format("🎵 已添加到播放队列: **%s**\n📋 来自播放列表: %s",
                        firstTrack.getInfo().title,
                        playlist.getName());
                callback.accept(msg);
            }

            @Override
            public void noMatches() {
                callback.accept("❌ 找不到音频: " + trackUrl);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                callback.accept("❌ 加载失败: " + exception.getMessage());
                log.error("加载音频失败: {}", exception.getMessage());
            }
        });
    }

    /**
     * 跳过当前音轨
     */
    public void skip(Guild guild) {
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager != null) {
            manager.getScheduler().nextTrack();
        }
    }

    /**
     * 停止播放
     */
    public void stop(Guild guild) {
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager != null) {
            manager.getPlayer().stopTrack();
            manager.getScheduler().clearQueue();
        }
    }

    /**
     * 暂停/恢复播放
     */
    public boolean togglePause(Guild guild) {
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager != null) {
            boolean paused = !manager.getPlayer().isPaused();
            manager.getPlayer().setPaused(paused);
            return paused;
        }
        return false;
    }

    /**
     * 获取当前播放信息
     */
    public String getNowPlaying(Guild guild) {
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager == null) {
            return "❌ 当前没有播放任何音乐";
        }

        AudioTrack track = manager.getPlayer().getPlayingTrack();
        if (track == null) {
            return "❌ 当前没有播放任何音乐";
        }

        return String.format("🎵 **正在播放**\n\n🎶 %s\n⏱️ %s / %s\n📋 队列中还有 %d 首",
                track.getInfo().title,
                formatDuration(track.getPosition()),
                formatDuration(track.getDuration()),
                manager.getScheduler().getQueue().size());
    }

    /**
     * 设置音量
     */
    public void setVolume(Guild guild, int volume) {
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager != null) {
            manager.getPlayer().setVolume(Math.max(0, Math.min(100, volume)));
        }
    }

    /**
     * 获取播放队列列表
     */
    public String getQueueList(Guild guild) {
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager == null) {
            return "❌ 当前没有播放任何音乐";
        }

        AudioTrack currentTrack = manager.getPlayer().getPlayingTrack();
        var queue = manager.getScheduler().getQueue();

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **播放队列**\n\n");

        if (currentTrack != null) {
            sb.append("▶️ **正在播放:** ").append(currentTrack.getInfo().title)
              .append(" (").append(formatDuration(currentTrack.getPosition()))
              .append("/").append(formatDuration(currentTrack.getDuration())).append(")\n\n");
        } else {
            sb.append("▶️ 当前没有播放\n\n");
        }

        if (queue.isEmpty()) {
            sb.append("📭 队列为空");
        } else {
            sb.append("**接下来播放:**\n");
            int index = 1;
            for (AudioTrack track : queue) {
                if (index > 10) {
                    sb.append("... 还有 ").append(queue.size() - 10).append(" 首\n");
                    break;
                }
                sb.append(index).append(". ").append(track.getInfo().title)
                  .append(" (").append(formatDuration(track.getDuration())).append(")\n");
                index++;
            }
        }

        return sb.toString();
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
