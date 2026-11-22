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
    /** 目标: 直播页(LivePlayActivity) */
    public static final String TARGET_LIVE = "live";
    /** 目标: 点播播放页(PlayFragment) */
    public static final String TARGET_VOD = "vod";

    public final VoiceCommand command;
    /** 指令目标界面: null=不限(订阅者均响应), 否则仅匹配的界面响应 */
    public String target;

    public VoiceEvent(VoiceCommand command) {
        this.command = command;
    }

    public VoiceEvent(VoiceCommand command, String target) {
        this.command = command;
        this.target = target;
    }
}
