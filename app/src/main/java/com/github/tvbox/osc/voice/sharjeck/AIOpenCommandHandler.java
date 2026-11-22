package com.github.tvbox.osc.voice.sharjeck;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.voice.VoiceCommand;
import com.github.tvbox.osc.voice.VoiceCommandManager;
import com.github.tvbox.osc.voice.VoiceCommandParser;
import com.github.tvbox.osc.voice.VoiceLogManager;
import com.google.gson.JsonObject;

/**
 * 夏杰语音AIOpen指令处理。
 * 接收夏杰语音分发的直播换台(action.live)/影视搜索控制(action.video)/重新注册
 * (app.register.require)指令, 转换为TVBox语音指令模型后统一调度
 * (复用 {@link VoiceCommandManager}, 换台/换剧集/搜索行为与本地语音完全一致),
 * 并记录语音调用日志(来源: 夏杰语音)。
 * <p>
 * 直播协议: common=search, keyword=标准频道名称(CCTV1/广东卫视)或频道号。
 * 影视协议: common=search(keyword+PlayIndex搜索后播放第N个) /
 *          common=control(action=ToNext/ToPre/Replay/Play/Pause/Exit/Return...,
 *          FastForward/FastBackward/SeekTo单位为秒)。
 *
 * @author voice
 */
public class AIOpenCommandHandler {

    public static void handle(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) return;
        Bundle data = intent.getExtras();
        try {
            switch (action) {
                case AIOpenConstant.ACTION_APP_REGISTER_REQUIRE: {
                    // 夏杰语音要求重新注册: 统一按组合场景(直播+影视)重新注册
                    AIOpenRegister.registerAll(context);
                    break;
                }
                case AIOpenConstant.ACTION_LIVE: {
                    handleLive(context, data);
                    break;
                }
                case AIOpenConstant.ACTION_VIDEO: {
                    handleVideo(context, data);
                    break;
                }
                case AIOpenConstant.ACTION_DEVICE_SCENE: {
                    // 自定义场景关键词命中: extras keyword=命中的固定短语, 走统一语音解析
                    String keyword = data == null ? null : data.getString(AIOpenConstant.PARAM_KEYWORD);
                    if (TextUtils.isEmpty(keyword)) {
                        VoiceLogManager.get().recordMissing(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_DEVICE_SCENE);
                        return;
                    }
                    keyword = keyword.trim();
                    JsonObject result = VoiceCommandManager.get().handleCommand(context, VoiceCommandParser.parse(keyword));
                    VoiceLogManager.get().record(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_DEVICE_SCENE, keyword, result);
                    break;
                }
                default:
                    break;
            }
        } catch (Throwable th) {
            LOG.e("AIOpenCommandHandler error: " + th.getMessage());
        }
    }

    /** 直播换台: common=search, keyword=标准频道名称或频道号 */
    private static void handleLive(Context context, Bundle data) {
        String keyword = data == null ? null : data.getString(AIOpenConstant.PARAM_KEYWORD);
        if (TextUtils.isEmpty(keyword)) {
            VoiceLogManager.get().recordMissing(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_LIVE);
            return;
        }
        keyword = keyword.trim();
        VoiceCommand cmd;
        if (keyword.matches("[0-9]+")) {
            cmd = new VoiceCommand(VoiceCommand.TYPE_LIVE_CHANNEL_BY_NUMBER);
            cmd.number = Integer.parseInt(keyword);
        } else {
            cmd = new VoiceCommand(VoiceCommand.TYPE_LIVE_CHANNEL_BY_NAME);
            cmd.keyword = VoiceCommandParser.normalizeChannelKeyword(keyword);
        }
        JsonObject result = VoiceCommandManager.get().handleCommand(context, cmd);
        VoiceLogManager.get().record(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_LIVE, keyword, result);
    }

    /** 影视: common=search 或 common=control */
    private static void handleVideo(Context context, Bundle data) {
        String common = data == null ? null : data.getString(AIOpenConstant.PARAM_COMMON);
        String action = data == null ? null : data.getString(AIOpenConstant.PARAM_ACTION);
        String keyword = data == null ? null : data.getString(AIOpenConstant.PARAM_KEYWORD);
        String playIndex = data == null ? null : data.getString(AIOpenConstant.PARAM_PLAY_INDEX);
        int fastForward = data == null ? 0 : data.getInt(AIOpenConstant.PARAM_FAST_FORWARD, 0);
        int fastBackward = data == null ? 0 : data.getInt(AIOpenConstant.PARAM_FAST_BACKWARD, 0);
        int seekTo = data == null ? 0 : data.getInt(AIOpenConstant.PARAM_SEEK_TO, 0);

        // 3.1 关键词搜索: common=search + keyword, PlayIndex=搜索后播放第几个
        if (AIOpenConstant.CMD_SEARCH.equals(common)) {
            if (TextUtils.isEmpty(keyword)) {
                VoiceLogManager.get().recordMissing(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_VIDEO);
                return;
            }
            VoiceCommand cmd = new VoiceCommand(VoiceCommand.TYPE_SEARCH);
            cmd.keyword = keyword.trim();
            cmd.number = parseIndex(playIndex);
            JsonObject result = VoiceCommandManager.get().handleCommand(context, cmd);
            VoiceLogManager.get().record(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_VIDEO,
                    keyword.trim() + (cmd.number > 0 ? " 第" + cmd.number + "个" : ""), result);
            return;
        }

        // 3.2 控制指令: common=control + action/FastForward/FastBackward/SeekTo/PlayIndex
        if (AIOpenConstant.CMD_CONTROL.equals(common)) {
            String logText = "control " + (action == null ? "" : action)
                    + (fastForward > 0 ? " +" + fastForward + "s" : "")
                    + (fastBackward > 0 ? " -" + fastBackward + "s" : "")
                    + (seekTo > 0 ? " to" + seekTo + "s" : "")
                    + (TextUtils.isEmpty(playIndex) ? "" : " PlayIndex=" + playIndex);
            // 夏杰"第N个"(搜索结果选择): control+PlayIndex, 无其他控制参数
            int pickIndex = parseIndex(playIndex);
            VoiceCommand cmd;
            if (pickIndex > 0 && fastForward <= 0 && fastBackward <= 0 && seekTo <= 0 && TextUtils.isEmpty(action)) {
                cmd = new VoiceCommand(VoiceCommand.TYPE_RESULT_PICK);
                cmd.number = pickIndex;
            } else {
                cmd = mapControlAction(action, fastForward, fastBackward, seekTo);
            }
            JsonObject result;
            if (cmd == null) {
                result = new JsonObject();
                result.addProperty("code", 0);
                result.addProperty("msg", "暂不支持该控制指令: " + action);
                JsonObject cmdJson = new JsonObject();
                cmdJson.addProperty("type", "unknown");
                result.add("command", cmdJson);
            } else {
                result = VoiceCommandManager.get().handleCommand(context, cmd);
            }
            VoiceLogManager.get().record(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_VIDEO, logText.trim(), result);
            return;
        }

        VoiceLogManager.get().recordMissing(VoiceLogManager.SOURCE_XIAJIE, AIOpenConstant.ACTION_VIDEO);
    }

    /** 夏杰影视控制action → TVBox语音指令模型; Fullscreen/ExitFullscreen/PrePage/NextPage暂不支持 */
    private static VoiceCommand mapControlAction(String action, int fastForward, int fastBackward, int seekTo) {
        if (fastForward > 0) {
            VoiceCommand cmd = new VoiceCommand(VoiceCommand.TYPE_SEEK_FORWARD);
            cmd.number = fastForward;
            return cmd;
        }
        if (fastBackward > 0) {
            VoiceCommand cmd = new VoiceCommand(VoiceCommand.TYPE_SEEK_BACK);
            cmd.number = fastBackward;
            return cmd;
        }
        if (seekTo > 0) {
            VoiceCommand cmd = new VoiceCommand(VoiceCommand.TYPE_SEEK_TO);
            cmd.number = seekTo;
            return cmd;
        }
        if (TextUtils.isEmpty(action)) return null;
        switch (action) {
            case "ToNext":
                return new VoiceCommand(VoiceCommand.TYPE_EPISODE_NEXT);
            case "ToPre":
                return new VoiceCommand(VoiceCommand.TYPE_EPISODE_PREV);
            case "Replay":
                return new VoiceCommand(VoiceCommand.TYPE_PLAY_REPLAY);
            case "Play":
                return new VoiceCommand(VoiceCommand.TYPE_PLAY_RESUME);
            case "Pause":
                return new VoiceCommand(VoiceCommand.TYPE_PLAY_PAUSE);
            case "Exit":
            case "Return":
                return new VoiceCommand(VoiceCommand.TYPE_PLAY_EXIT);
            default:
                return null;
        }
    }

    private static int parseIndex(String playIndex) {
        if (TextUtils.isEmpty(playIndex)) return -1;
        try {
            int idx = Integer.parseInt(playIndex.trim());
            return idx > 0 ? idx : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
