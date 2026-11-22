package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.voice.VoiceCommandManager;

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
 *
 * @author voice
 */
public class VoiceEntryActivity extends Activity {

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
        String text = VoiceCommandManager.extractText(intent);
        if (TextUtils.isEmpty(text)) {
            // 兼容路径形式: tvbox://voice/搜索狂飙
            Uri data = intent.getData();
            if (data != null && !TextUtils.isEmpty(data.getPath())) {
                text = data.getPath();
                if (text.startsWith("/")) text = text.substring(1);
            }
        }
        if (TextUtils.isEmpty(text)) return;
        text = text.trim();
        Uri data = intent.getData();
        String host = data == null || data.getHost() == null ? "" : data.getHost().toLowerCase();
        if (host.contains("search")) {
            // tvbox://search?word=xxx: 直接远程搜索(与网页遥控搜索一致)
            SearchReceiver.send(this, text);
        } else {
            // tvbox://voice?text=xxx: 完整语音指令解析(搜索/换台/换剧集/播放控制)
            VoiceCommandManager.get().handle(this, text);
        }
    }
}
