package com.github.tvbox.osc.voice;

import com.github.tvbox.osc.util.LOG;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * 语音调用日志。
 * <p>
 * <b>记录范围(仅限两类来源)</b>:
 * <ul>
 *   <li>电视/投影仪/电视盒子自带原厂语音服务 —— 通过系统标准搜索intent
 *   (SEARCH/GLOBAL_SEARCH/WEB_SEARCH/MEDIA_PLAY_FROM_SEARCH)调用的请求</li>
 *   <li>夏杰语音服务 —— 通过 tvbox://voice / tvbox://search 深链调用的请求</li>
 * </ul>
 * 其他通道(HTTP /voice接口、网页遥控语音输入框、自定义广播 com.github.tvbox.osc.VOICE_COMMAND)
 * 属于通用工具通道, 不记录。
 * <p>
 * 查看方式:
 * <ul>
 *   <li>手机浏览器: 网页遥控(http://IP:9978/)点"日志"按钮, 或直接访问 http://IP:9978/voiceLog</li>
 *   <li>logcat: adb logcat -s TVBox-runtime (前缀 VoiceLog)</li>
 * </ul>
 *
 * @author voice
 */
public class VoiceLogManager {
    /** 夏杰语音(深链 tvbox://voice / tvbox://search) */
    public static final String SOURCE_XIAJIE = "夏杰语音";
    /** 电视/投影仪/电视盒子自带原厂语音服务(系统标准搜索intent) */
    public static final String SOURCE_TV_BUILTIN = "电视原厂语音";
    /** 无法归类的来源(显式component调用等) */
    public static final String SOURCE_UNKNOWN = "未知来源";

    private static final int MAX_LOGS = 100;

    private static VoiceLogManager instance;

    private final LinkedList<VoiceLogItem> logs = new LinkedList<>();

    private VoiceLogManager() {
    }

    public static VoiceLogManager get() {
        if (instance == null) {
            synchronized (VoiceLogManager.class) {
                if (instance == null) {
                    instance = new VoiceLogManager();
                }
            }
        }
        return instance;
    }

    /** 记录一次语音调用及其解析执行结果 */
    public synchronized void record(String source, String action, String rawText, JsonObject result) {
        VoiceLogItem item = new VoiceLogItem();
        item.time = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        item.source = source;
        item.action = action == null ? "" : action;
        item.rawText = rawText == null ? "" : rawText;
        if (result != null) {
            item.code = result.has("code") && result.get("code").isJsonPrimitive() ? result.get("code").getAsInt() : -1;
            item.msg = result.has("msg") && result.get("msg").isJsonPrimitive() ? result.get("msg").getAsString() : "";
            item.command = result.has("command") && result.get("command").isJsonObject()
                    ? result.get("command").getAsJsonObject().toString() : "";
        } else {
            item.code = -1;
            item.msg = "未知结果";
        }
        logs.addFirst(item);
        while (logs.size() > MAX_LOGS) {
            logs.removeLast();
        }
        LOG.i("VoiceLog[" + item.source + "][" + item.action + "] text=" + item.rawText
                + " code=" + item.code + " msg=" + item.msg + (item.command.isEmpty() ? "" : " cmd=" + item.command));
    }

    /** 收到了语音服务的调用但未能提取到语音文本(便于排查助手侧配置问题) */
    public void recordMissing(String source, String action) {
        JsonObject result = new JsonObject();
        result.addProperty("code", -1);
        result.addProperty("msg", "未提取到语音文本, 请检查语音助手下发的参数");
        record(source, action, "", result);
    }

    /** 全部日志(JSON数组, 时间倒序) */
    public synchronized JsonArray toJsonArray() {
        JsonArray array = new JsonArray();
        for (VoiceLogItem item : logs) {
            array.add(item.toJson());
        }
        return array;
    }

    /** 全部日志(纯文本, 时间倒序) */
    public synchronized String toText() {
        if (logs.isEmpty()) return "暂无语音调用日志\n";
        StringBuilder sb = new StringBuilder();
        for (VoiceLogItem item : logs) {
            sb.append("[").append(item.time).append("] 来源:").append(item.source)
                    .append(" 动作:").append(item.action)
                    .append(" 文本:").append(item.rawText.isEmpty() ? "(空)" : item.rawText)
                    .append(" 结果:").append(item.code == 0 ? "成功" : "失败")
                    .append("(").append(item.msg).append(")");
            if (!item.command.isEmpty()) sb.append(" 指令:").append(item.command);
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 清空日志 */
    public synchronized void clear() {
        logs.clear();
    }

    /** 单条日志 */
    public static class VoiceLogItem {
        public String time;
        public String source;
        public String action;
        public String rawText;
        public int code;
        public String msg;
        public String command;

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("time", time);
            obj.addProperty("source", source);
            obj.addProperty("action", action);
            obj.addProperty("rawText", rawText);
            obj.addProperty("code", code);
            obj.addProperty("msg", msg);
            if (!command.isEmpty()) obj.addProperty("command", command);
            return obj;
        }
    }
}
