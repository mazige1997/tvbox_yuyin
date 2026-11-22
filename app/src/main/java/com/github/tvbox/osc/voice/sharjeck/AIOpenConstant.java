package com.github.tvbox.osc.voice.sharjeck;

/**
 * 夏杰语音AIOpen开放对接协议常量。
 * 依据官方 LiveTVDemo(直播V1.2文档)/VideoDemo(影视V1.4文档) 与
 * com.peasun.aispeech.aiopen 开源对接源码整理。
 *
 * @author voice
 */
public class AIOpenConstant {
    /** 夏杰语音AIOpen对接权限(AndroidManifest需声明uses-permission) */
    public static final String AI_OPEN_PERMISSION = "com.peasun.aispeech.aiopen.control";

    /** 动态注册action: 对接软件主动向夏杰语音发送注册信息 */
    public static final String ACTION_APP_REGISTER = "com.peasun.aispeech.action.app.register";
    /** 夏杰语音要求对接软件重新注册的action */
    public static final String ACTION_APP_REGISTER_REQUIRE = "com.peasun.aispeech.action.app.register.require";
    /** 直播换台指令action(夏杰语音→对接软件) */
    public static final String ACTION_LIVE = "com.peasun.aispeech.action.live";
    /** 影视搜索/控制指令action(夏杰语音→对接软件) */
    public static final String ACTION_VIDEO = "com.peasun.aispeech.action.video";

    /** 场景类别: 直播 */
    public static final long SEMANTIC_LIVE = 0x1 << 2; // 0x4
    /** 场景类别: 影视 */
    public static final long SEMANTIC_VIDEO = 0x1 << 3; // 0x8
    /** 场景类别: 自定义场景(可携带keywords关键词数组, 夏杰按包含匹配后派发action.device.scene) */
    public static final long SEMANTIC_DEVICE_SCENE = 576460752303423488L;

    /** 自定义场景派发action(夏杰→对接软件, extras: keyword=命中的关键词) */
    public static final String ACTION_DEVICE_SCENE = "com.peasun.aispeech.action.device.scene";
    /** 注册时的关键词数组参数名 */
    public static final String PARAM_KEYWORDS = "keywords";

    /** 夏杰语音大陆版包名 */
    public static final String PKG_AISPEECH_CN = "com.peasun.aispeech";
    /** 夏杰语音国际版包名 */
    public static final String PKG_AISPEECH_GLOBAL = "com.peasun.aispeechgl";

    /** 影视指令: 语音搜索 */
    public static final String CMD_SEARCH = "search";
    /** 影视指令: 语音控制 */
    public static final String CMD_CONTROL = "control";

    /** 协议参数名 */
    public static final String PARAM_COMMON = "common";
    public static final String PARAM_KEYWORD = "keyword";
    public static final String PARAM_ACTION = "action";
    public static final String PARAM_CATEGORY = "category";
    public static final String PARAM_PACKAGE_NAME = "package_name";
    public static final String PARAM_PLAY_INDEX = "PlayIndex";
    public static final String PARAM_FAST_FORWARD = "FastForward";
    public static final String PARAM_FAST_BACKWARD = "FastBackward";
    public static final String PARAM_SEEK_TO = "SeekTo";
}
