package com.github.tvbox.osc.voice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import com.github.tvbox.osc.event.VoiceEvent;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
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
        return handleCommand(context, VoiceCommandParser.parse(rawText));
    }

    /**
     * 执行已解析的语音指令(供夏杰语音AIOpen等结构化指令入口直接调用), 返回JSON结果。
     */
    public JsonObject handleCommand(Context context, VoiceCommand cmd) {
        JsonObject result = new JsonObject();
        JsonObject cmdJson = new JsonObject();
        cmdJson.addProperty("type", cmd.getTypeName());
        if (!TextUtils.isEmpty(cmd.keyword)) cmdJson.addProperty("keyword", cmd.keyword);
        if (cmd.number >= 0) cmdJson.addProperty("number", cmd.number);
        result.add("command", cmdJson);
        // 语音控制总开关(设置页可开关)
        if (!Hawk.get(HawkConfig.VOICE_CONTROL, true)) {
            result.addProperty("code", 1);
            result.addProperty("msg", "语音控制已关闭, 请到 设置-配置中心 开启");
            return result;
        }
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

    /**
     * 从语音相关intent中提取语音文本。
     * 兼容各家语音助手(小爱/天猫精灵/小度/夏杰语音/电视自带语音)常用的字段名与URI参数。
     */
    public static String extractText(Intent intent) {
        if (intent == null) return null;
        Bundle extras = intent.getExtras();
        String[] keys = {"text", "query", "word", "keyword", "title", "voice_text", "command", android.app.SearchManager.QUERY};
        if (extras != null) {
            for (String key : keys) {
                Object value = extras.get(key);
                if (value instanceof String && !TextUtils.isEmpty((String) value)) {
                    return (String) value;
                }
            }
        }
        Uri data = intent.getData();
        if (data != null) {
            String value = data.getQueryParameter("text");
            if (TextUtils.isEmpty(value)) value = data.getQueryParameter("query");
            if (TextUtils.isEmpty(value)) value = data.getQueryParameter("word");
            if (TextUtils.isEmpty(value)) value = data.getQueryParameter("keyword");
            if (!TextUtils.isEmpty(value)) return value;
        }
        return null;
    }

    private String dispatch(Context context, VoiceCommand cmd) {
        switch (cmd.type) {
            case VoiceCommand.TYPE_SEARCH:
                SearchReceiver.send(context, cmd.keyword, cmd.number);
                return "已开始搜索: " + cmd.keyword + (cmd.number > 0 ? "(将播放第" + cmd.number + "个)" : "");
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
            case VoiceCommand.TYPE_SEEK_TO:
            case VoiceCommand.TYPE_PLAY_REPLAY:
            case VoiceCommand.TYPE_PLAY_EXIT:
                return dispatchPlayback(context, cmd);
            case VoiceCommand.TYPE_RESULT_PICK:
                return dispatchResultPick(context, cmd);
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
        AppManager appManager = AppManager.getInstance();
        Activity current = appManager.isActivity() ? appManager.currentActivity() : null;
        boolean detailTop = current instanceof DetailActivity;
        boolean liveTop = current instanceof LivePlayActivity;
        boolean isEpisode = cmd.type == VoiceCommand.TYPE_EPISODE_GOTO
                || cmd.type == VoiceCommand.TYPE_EPISODE_NEXT
                || cmd.type == VoiceCommand.TYPE_EPISODE_PREV;
        boolean isSeek = cmd.type == VoiceCommand.TYPE_SEEK_FORWARD || cmd.type == VoiceCommand.TYPE_SEEK_BACK;
        if (cmd.type == VoiceCommand.TYPE_PLAY_EXIT) {
            // 退出播放: 直接结束栈顶的直播页/详情页(等价于遥控器返回键)
            if (liveTop || detailTop) {
                runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AppManager.getInstance().currentActivity().finish();
                        } catch (Throwable ignored) {
                        }
                    }
                });
                return "已退出播放";
            }
            return "当前没有正在播放的内容";
        }
        if (isEpisode && !detailTop) return "当前没有正在播放的剧集";
        if ((isSeek || cmd.type == VoiceCommand.TYPE_SEEK_TO) && liveTop) return "直播模式不支持快进快退";
        if (!detailTop && !liveTop) return "当前没有正在播放的内容";
        // 按栈顶界面定向分发, 避免直播/点播两层同时响应
        VoiceEvent event = new VoiceEvent(cmd, liveTop ? VoiceEvent.TARGET_LIVE : VoiceEvent.TARGET_VOD);
        EventBus.getDefault().post(event);
        return playbackResultMsg(cmd);
    }

    /** 夏杰"第N个": 结果页在栈内→直接选第N个; 否则用最近一次语音搜索词重新搜索并选第N个 */
    private String dispatchResultPick(Context context, VoiceCommand cmd) {
        if (cmd.number <= 0) return "无效的序号";
        boolean searchActive = AppManager.getInstance().getActivity(
                com.github.tvbox.osc.ui.activity.FastSearchActivity.class) != null;
        if (searchActive) {
            EventBus.getDefault().post(new VoiceEvent(cmd));
            return "正在选择第" + cmd.number + "个结果";
        }
        String lastKeyword = Hawk.get(HawkConfig.VOICE_LAST_SEARCH, "");
        if (!TextUtils.isEmpty(lastKeyword)) {
            SearchReceiver.send(context, lastKeyword, cmd.number);
            return "重新搜索" + lastKeyword + "并播放第" + cmd.number + "个";
        }
        return "请先搜索影片后再说第几个";
    }

    private void runOnMain(Runnable runnable) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            runnable.run();
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
        }
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
            case VoiceCommand.TYPE_SEEK_TO:
                return "跳转到第" + cmd.number + "秒";
            case VoiceCommand.TYPE_PLAY_REPLAY:
                return "重播当前";
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
