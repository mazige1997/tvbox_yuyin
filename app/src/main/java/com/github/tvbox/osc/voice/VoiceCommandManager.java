package com.github.tvbox.osc.voice;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.text.TextUtils;

import com.github.tvbox.osc.event.VoiceEvent;
import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.util.AppManager;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;

/**
 * 语音指令统一调度入口。
 * 语音文本可来自: 1)本机HTTP接口(/voice) 2)语音广播(VoiceReceiver) 3)云技能服务端转发。
 * 小爱音箱/天猫精灵/小度音箱等语音助手接入方式详见 docs/VOICE_CONTROL.md。
 * <p>
 * 分发规则:
 * - 搜索: 复用远程搜索链路(SearchReceiver广播)
 * - 换台: 直播页在栈内→EventBus通知; 不在栈内且指定了频道→拉起直播页自动切换
 * - 换剧集/播放控制: 详情页在栈内→EventBus通知PlayFragment执行
 * - 音量/静音: 直接通过AudioManager调节, 不依赖界面
 *
 * @author voice
 */
public class VoiceCommandManager {
    private static VoiceCommandManager instance;

    private VoiceCommandManager() {
    }

    public static VoiceCommandManager get() {
        if (instance == null) {
            synchronized (VoiceCommandManager.class) {
                if (instance == null) {
                    instance = new VoiceCommandManager();
                }
            }
        }
        return instance;
    }

    /**
     * 解析并执行语音指令, 返回JSON结果供HTTP接口回显。
     */
    public JsonObject handle(Context context, String rawText) {
        VoiceCommand cmd = VoiceCommandParser.parse(rawText);
        JsonObject result = new JsonObject();
        JsonObject cmdJson = new JsonObject();
        cmdJson.addProperty("type", cmd.getTypeName());
        if (!TextUtils.isEmpty(cmd.keyword)) cmdJson.addProperty("keyword", cmd.keyword);
        if (cmd.number >= 0) cmdJson.addProperty("number", cmd.number);
        result.add("command", cmdJson);
        String msg;
        try {
            msg = dispatch(context.getApplicationContext(), cmd);
            result.addProperty("code", 0);
        } catch (Throwable th) {
            result.addProperty("code", -1);
            msg = "执行失败: " + (th.getMessage() == null ? th.toString() : th.getMessage());
        }
        result.addProperty("msg", msg);
        return result;
    }

    private String dispatch(Context context, VoiceCommand cmd) {
        switch (cmd.type) {
            case VoiceCommand.TYPE_SEARCH:
                SearchReceiver.send(context, cmd.keyword);
                return "已开始搜索: " + cmd.keyword;
            case VoiceCommand.TYPE_LIVE_CHANNEL_BY_NAME:
            case VoiceCommand.TYPE_LIVE_CHANNEL_BY_NUMBER:
            case VoiceCommand.TYPE_LIVE_NEXT:
            case VoiceCommand.TYPE_LIVE_PREV:
                return dispatchLive(context, cmd);
            case VoiceCommand.TYPE_EPISODE_GOTO:
            case VoiceCommand.TYPE_EPISODE_NEXT:
            case VoiceCommand.TYPE_EPISODE_PREV:
            case VoiceCommand.TYPE_PLAY_PAUSE:
            case VoiceCommand.TYPE_PLAY_RESUME:
            case VoiceCommand.TYPE_SEEK_FORWARD:
            case VoiceCommand.TYPE_SEEK_BACK:
                return dispatchPlayback(context, cmd);
            case VoiceCommand.TYPE_VOLUME_UP:
            case VoiceCommand.TYPE_VOLUME_DOWN:
            case VoiceCommand.TYPE_VOLUME_MUTE:
                return controlVolume(context, cmd);
            default:
                return "无法识别的指令, 请换个说法";
        }
    }

    private String dispatchLive(Context context, VoiceCommand cmd) {
        boolean liveActive = AppManager.getInstance().getActivity(LivePlayActivity.class) != null;
        if (liveActive) {
            EventBus.getDefault().post(new VoiceEvent(cmd));
            return liveResultMsg(cmd);
        }
        // 未打开直播时, 仅支持直接指定频道(先拉起直播页, 待频道列表加载完成后自动切换)
        if (cmd.type == VoiceCommand.TYPE_LIVE_CHANNEL_BY_NAME || cmd.type == VoiceCommand.TYPE_LIVE_CHANNEL_BY_NUMBER) {
            Intent intent = new Intent(context, LivePlayActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (cmd.type == VoiceCommand.TYPE_LIVE_CHANNEL_BY_NAME) {
                intent.putExtra("voiceChannelName", cmd.keyword);
            } else {
                intent.putExtra("voiceChannelNumber", cmd.number);
            }
            context.startActivity(intent);
            return "正在打开直播并切换到: " + (cmd.type == VoiceCommand.TYPE_LIVE_CHANNEL_BY_NAME ? cmd.keyword : "第" + cmd.number + "台");
        }
        return "请先打开电视直播再使用语音换台";
    }

    private String liveResultMsg(VoiceCommand cmd) {
        switch (cmd.type) {
            case VoiceCommand.TYPE_LIVE_NEXT:
                return "已切换到下一个台";
            case VoiceCommand.TYPE_LIVE_PREV:
                return "已切换到上一个台";
            case VoiceCommand.TYPE_LIVE_CHANNEL_BY_NUMBER:
                return "正在切换到第" + cmd.number + "台";
            default:
                return "正在切换到频道: " + cmd.keyword;
        }
    }

    private String dispatchPlayback(Context context, VoiceCommand cmd) {
        boolean detailActive = AppManager.getInstance().getActivity(DetailActivity.class) != null;
        boolean liveActive = AppManager.getInstance().getActivity(LivePlayActivity.class) != null;
        boolean isEpisode = cmd.type == VoiceCommand.TYPE_EPISODE_GOTO
                || cmd.type == VoiceCommand.TYPE_EPISODE_NEXT
                || cmd.type == VoiceCommand.TYPE_EPISODE_PREV;
        boolean isSeek = cmd.type == VoiceCommand.TYPE_SEEK_FORWARD || cmd.type == VoiceCommand.TYPE_SEEK_BACK;
        if (isEpisode && !detailActive) return "当前没有正在播放的剧集";
        if (isSeek && liveActive) return "直播模式不支持快进快退";
        if (!detailActive && !liveActive) return "当前没有正在播放的内容";
        EventBus.getDefault().post(new VoiceEvent(cmd));
        return playbackResultMsg(cmd);
    }

    private String playbackResultMsg(VoiceCommand cmd) {
        switch (cmd.type) {
            case VoiceCommand.TYPE_EPISODE_GOTO:
                return "正在切换到第" + cmd.number + "集";
            case VoiceCommand.TYPE_EPISODE_NEXT:
                return "正在切换到下一集";
            case VoiceCommand.TYPE_EPISODE_PREV:
                return "正在切换到上一集";
            case VoiceCommand.TYPE_PLAY_PAUSE:
                return "已暂停";
            case VoiceCommand.TYPE_PLAY_RESUME:
                return "继续播放";
            case VoiceCommand.TYPE_SEEK_FORWARD:
                return "快进" + cmd.number + "秒";
            case VoiceCommand.TYPE_SEEK_BACK:
                return "快退" + cmd.number + "秒";
            default:
                return "已执行";
        }
    }

    private String controlVolume(Context context, VoiceCommand cmd) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return "无法获取音频服务";
        if (cmd.type == VoiceCommand.TYPE_VOLUME_UP) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            return "已调大音量";
        }
        if (cmd.type == VoiceCommand.TYPE_VOLUME_DOWN) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            return "已调小音量";
        }
        return toggleMute(audioManager);
    }

    @SuppressWarnings("deprecation")
    private String toggleMute(AudioManager audioManager) {
        // 静音切换: API23+用ADJUST_TOGGLE_MUTE, 低版本退回setStreamMute
        if (Build.VERSION.SDK_INT >= 23) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
            return "已切换静音状态";
        }
        boolean muted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, !muted);
        return muted ? "已取消静音" : "已静音";
    }
}
