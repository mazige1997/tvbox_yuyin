package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.github.tvbox.osc.voice.VoiceCommandManager;
import com.github.tvbox.osc.voice.VoiceLogManager;
import com.google.gson.JsonObject;

/**
 * 语音指令广播入口。
 * 供系统语音助手、手机遥控App、Home Assistant等外部工具直接下发语音指令:
 * adb shell am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "搜索狂飙"
 * <p>
 * 同时兼容系统级搜索action(GLOBAL_SEARCH/WEB_SEARCH/SEARCH), 部分电视端语音助手
 * (小米电视小爱/天猫魔盒语音遥控等)会以这类intent唤起第三方应用。
 * 指令解析与执行见 {@link VoiceCommandManager}, 接入方式详见 docs/VOICE_CONTROL.md。
 *
 * @author voice
 */
public class VoiceReceiver extends BroadcastReceiver {
    public static String action = "com.github.tvbox.osc.VOICE_COMMAND";
    private static final String ACTION_SEARCH = "android.intent.action.SEARCH";
    private static final String ACTION_GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH";
    private static final String ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String act = intent.getAction();
        boolean accepted = action.equals(act)
                || ACTION_SEARCH.equals(act)
                || ACTION_GLOBAL_SEARCH.equals(act)
                || ACTION_WEB_SEARCH.equals(act)
                || ACTION_MEDIA_PLAY_FROM_SEARCH.equals(act);
        if (!accepted) return;
        // 标准系统搜索intent来自电视/投影仪/盒子自带原厂语音服务, 记录语音调用日志;
        // 自定义广播(com.github.tvbox.osc.VOICE_COMMAND)是通用外部工具通道, 不记录。
        boolean builtinVoice = !action.equals(act);
        String text = pickText(intent);
        if (!TextUtils.isEmpty(text)) {
            text = text.trim();
            if (builtinVoice) {
                JsonObject result = VoiceCommandManager.get().handle(context, text);
                VoiceLogManager.get().record(VoiceLogManager.SOURCE_TV_BUILTIN, act, text, result);
            } else {
                VoiceCommandManager.get().handle(context, text);
            }
        } else if (builtinVoice) {
            VoiceLogManager.get().recordMissing(VoiceLogManager.SOURCE_TV_BUILTIN, act);
        }
    }

    /** 从extras/uri中提取语音文本, 兼容各家助手的常用字段名 */
    private String pickText(Intent intent) {
        String text = VoiceCommandManager.extractText(intent);
        if (TextUtils.isEmpty(text) && ACTION_WEB_SEARCH.equals(intent.getAction()) && intent.getData() != null) {
            text = intent.getData().toString();
        }
        return text;
    }
}
