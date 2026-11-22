package com.github.tvbox.osc.event;

import com.github.tvbox.osc.voice.VoiceCommand;

/**
 * 语音指令事件。
 * 由 {@link com.github.tvbox.osc.voice.VoiceCommandManager} 发布,
 * LivePlayActivity(语音换台)与 PlayFragment(语音换剧集/播放控制)订阅执行。
 *
 * @author voice
 */
public class VoiceEvent {
    public final VoiceCommand command;

    public VoiceEvent(VoiceCommand command) {
        this.command = command;
    }
}
