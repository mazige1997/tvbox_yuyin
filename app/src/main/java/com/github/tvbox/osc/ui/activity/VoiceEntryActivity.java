package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.voice.VoiceCommandManager;
import com.github.tvbox.osc.voice.VoiceLogManager;
import com.google.gson.JsonObject;

/**
 * 语音/搜索指令深链入口(无界面, 打开后立即执行并退出)。
 * 供夏杰语音等电视端语音助手的"打开网址/AI智能体自定义指令"、网页、第三方App调用:
 * <pre>
 *   tvbox://voice?text=搜索狂飙      → 按语音指令解析执行(搜索/换台/换剧集/播放控制)
 *   tvbox://search?word=狂飙         → 直接执行远程搜索
 *   tvbox://voice/搜索狂飙           → 路径形式同样支持
 * </pre>
 * 同时兼容直接以extras携带文本打开本页(text/query/word/keyword/title),
 * 部分电视语音助手唤起应用时会把搜索词放在extras里。
 * 广播入口见 {@link com.github.tvbox.osc.receiver.VoiceReceiver}, 接入方式详见 docs/VOICE_CONTROL.md。
 * <p>
 * 日志: 本入口的调用会记录到语音调用日志({@link VoiceLogManager}), 来源按 intent 归属:
 * 系统标准搜索intent → 电视原厂语音; tvbox:// 深链 → 夏杰语音; 其余显式调用 → 未知来源。
 *
 * @author voice
 */
public class VoiceEntryActivity extends Activity {
    private static final String ACTION_SEARCH = "android.intent.action.SEARCH";
    private static final String ACTION_GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH";
    private static final String ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        finish();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String source = detectSource(intent);
        String action = intent.getAction() == null ? "" : intent.getAction();
        String text = VoiceCommandManager.extractText(intent);
        if (TextUtils.isEmpty(text)) {
            // 兼容路径形式: tvbox://voice/搜索狂飙
            Uri data = intent.getData();
            if (data != null && !TextUtils.isEmpty(data.getPath())) {
                text = data.getPath();
                if (text.startsWith("/")) text = text.substring(1);
            }
        }
        if (TextUtils.isEmpty(text)) {
            VoiceLogManager.get().recordMissing(source, action);
            return;
        }
        text = text.trim();
        Uri data = intent.getData();
        String host = data == null || data.getHost() == null ? "" : data.getHost().toLowerCase();
        if (host.contains("search")) {
            // tvbox://search?word=xxx: 直接远程搜索(与网页遥控搜索一致)
            SearchReceiver.send(this, text);
            JsonObject result = new JsonObject();
            result.addProperty("code", 0);
            result.addProperty("msg", "已执行远程搜索: " + text);
            JsonObject cmdJson = new JsonObject();
            cmdJson.addProperty("type", "search");
            cmdJson.addProperty("keyword", text);
            result.add("command", cmdJson);
            VoiceLogManager.get().record(source, action, text, result);
        } else {
            // tvbox://voice?text=xxx: 完整语音指令解析(搜索/换台/换剧集/播放控制)
            JsonObject result = VoiceCommandManager.get().handle(this, text);
            VoiceLogManager.get().record(source, action, text, result);
        }
    }

    /** 按intent归属语音来源: 标准搜索intent→电视原厂语音; tvbox://深链→夏杰语音; 其余→未知 */
    private String detectSource(Intent intent) {
        String act = intent.getAction();
        if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(act)
                || ACTION_GLOBAL_SEARCH.equals(act)
                || ACTION_SEARCH.equals(act)
                || ACTION_WEB_SEARCH.equals(act)) {
            return VoiceLogManager.SOURCE_TV_BUILTIN;
        }
        if (intent.getData() != null) {
            return VoiceLogManager.SOURCE_XIAJIE;
        }
        return VoiceLogManager.SOURCE_UNKNOWN;
    }
}
