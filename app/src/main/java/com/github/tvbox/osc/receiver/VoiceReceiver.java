package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.github.tvbox.osc.voice.VoiceCommandManager;

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

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String act = intent.getAction();
        boolean accepted = action.equals(act)
                || ACTION_SEARCH.equals(act)
                || ACTION_GLOBAL_SEARCH.equals(act)
                || ACTION_WEB_SEARCH.equals(act);
        if (!accepted) return;
        String text = pickText(intent);
        if (!TextUtils.isEmpty(text)) {
            VoiceCommandManager.get().handle(context, text.trim());
        }
    }

    /** 从extras/uri中提取语音文本, 兼容各家助手的常用字段名 */
    private String pickText(Intent intent) {
        Bundle extras = intent.getExtras();
        String[] keys = {"text", "query", "word", "keyword", "voice_text", "command", android.app.SearchManager.QUERY};
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
            if (!TextUtils.isEmpty(value)) return value;
            if (ACTION_WEB_SEARCH.equals(intent.getAction())) return data.toString();
        }
        return null;
    }
}
