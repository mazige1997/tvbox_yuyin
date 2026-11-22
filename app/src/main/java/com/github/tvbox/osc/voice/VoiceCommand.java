package com.github.tvbox.osc.voice;

/**
 * 语音指令模型。
 * 由 {@link VoiceCommandParser} 解析语音文本得到, 交给 {@link VoiceCommandManager} 执行。
 *
 * @author voice
 */
public class VoiceCommand {
    public static final int TYPE_SEARCH = 0;               // 搜索影视
    public static final int TYPE_LIVE_CHANNEL_BY_NAME = 1; // 直播: 按频道名换台
    public static final int TYPE_LIVE_CHANNEL_BY_NUMBER = 2;// 直播: 按台号换台
    public static final int TYPE_LIVE_NEXT = 3;            // 直播: 下一个台
    public static final int TYPE_LIVE_PREV = 4;            // 直播: 上一个台
    public static final int TYPE_EPISODE_GOTO = 5;         // 剧集: 跳到第N集
    public static final int TYPE_EPISODE_NEXT = 6;         // 剧集: 下一集
    public static final int TYPE_EPISODE_PREV = 7;         // 剧集: 上一集
    public static final int TYPE_PLAY_PAUSE = 8;           // 暂停
    public static final int TYPE_PLAY_RESUME = 9;          // 继续播放
    public static final int TYPE_SEEK_FORWARD = 10;        // 快进N秒
    public static final int TYPE_SEEK_BACK = 11;           // 快退N秒
    public static final int TYPE_VOLUME_UP = 12;           // 音量调大
    public static final int TYPE_VOLUME_DOWN = 13;         // 音量调小
    public static final int TYPE_VOLUME_MUTE = 14;         // 静音切换
    public static final int TYPE_SEEK_TO = 15;             // 跳转到指定秒
    public static final int TYPE_PLAY_REPLAY = 16;         // 重播当前
    public static final int TYPE_PLAY_EXIT = 17;           // 退出播放
    public static final int TYPE_UNKNOWN = -1;

    public int type = TYPE_UNKNOWN;
    /** 搜索关键词或频道名 */
    public String keyword = "";
    /** 台号/集数/快进秒数 */
    public int number = -1;
    /** 原始语音文本 */
    public String rawText = "";

    public VoiceCommand() {
    }

    public VoiceCommand(int type) {
        this.type = type;
    }

    /** 指令类型的机器名, 供HTTP接口回显给调用方 */
    public String getTypeName() {
        switch (type) {
            case TYPE_SEARCH:
                return "search";
            case TYPE_LIVE_CHANNEL_BY_NAME:
            case TYPE_LIVE_CHANNEL_BY_NUMBER:
                return "live_channel";
            case TYPE_LIVE_NEXT:
                return "live_next";
            case TYPE_LIVE_PREV:
                return "live_prev";
            case TYPE_EPISODE_GOTO:
                return "episode";
            case TYPE_EPISODE_NEXT:
                return "episode_next";
            case TYPE_EPISODE_PREV:
                return "episode_prev";
            case TYPE_PLAY_PAUSE:
                return "pause";
            case TYPE_PLAY_RESUME:
                return "resume";
            case TYPE_SEEK_FORWARD:
                return "seek_forward";
            case TYPE_SEEK_BACK:
                return "seek_back";
            case TYPE_VOLUME_UP:
                return "volume_up";
            case TYPE_VOLUME_DOWN:
                return "volume_down";
            case TYPE_VOLUME_MUTE:
                return "mute";
            case TYPE_SEEK_TO:
                return "seek_to";
            case TYPE_PLAY_REPLAY:
                return "replay";
            case TYPE_PLAY_EXIT:
                return "exit";
            default:
                return "unknown";
        }
    }
}
