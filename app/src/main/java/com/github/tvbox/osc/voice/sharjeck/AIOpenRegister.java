package com.github.tvbox.osc.voice.sharjeck;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.voice.VoiceLogManager;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

/**
 * 夏杰语音动态注册。
 * 对接软件主动向夏杰语音发送注册信息, 声明本应用支持的场景类别;
 * 注册后夏杰语音把对应场景的语音指令分发到本应用的
 * SharjeckService/SharjeckReceiver(action.live/action.video)。
 * <p>
 * 反编译夏杰语音4.6.1.3确认的实现细节(与官方文档一致):
 * <ul>
 *   <li>注册action: com.peasun.aispeech.action.app.register, extras: package_name(String) + category(Long);
 *       <b>category为精确匹配**(4=直播, 8=影视), 不支持位合并, 因此直播与影视需分别注册</b></li>
 *   <li>注册接口: startService(4.0.6.0+) 与 sendBroadcast(全版本兼容) 均可, 本类两者都发</li>
 *   <li>夏杰语音按包名登记到 open.applist, 换台/影视指令以
 *       startForegroundService(setPackage(本应用)+action.live/action.video) 派发</li>
 * </ul>
 * 收到 app.register.require 广播时需按category重新注册。
 *
 * @author voice
 */
public class AIOpenRegister {

    /** 注册全部支持的场景(直播与影视分别注册, category必须为精确值) */
    public static void registerAll(Context context) {
        if (!Hawk.get(HawkConfig.VOICE_CONTROL, true)) {
            LOG.i("AIOpenRegister: 语音控制已关闭, 跳过注册");
            record(context, "语音控制已关闭, 跳过注册", true);
            return;
        }
        for (String pkg : new String[]{AIOpenConstant.PKG_AISPEECH_CN, AIOpenConstant.PKG_AISPEECH_GLOBAL}) {
            if (!isInstalled(context, pkg)) continue;
            register(context, pkg, AIOpenConstant.SEMANTIC_LIVE);
            register(context, pkg, AIOpenConstant.SEMANTIC_VIDEO);
            registerDeviceScene(context, pkg);
        }
    }

    /**
     * 自定义场景关键词(反编译夏杰语义引擎确认):
     * DEVICE_SCENE注册的关键词在语义主任务里最先匹配(utterance包含即命中, 与前台应用无关),
     * 命中后以 action.device.scene + keyword=命中词 派发给本应用。
     * 用于覆盖夏杰内置换台(模拟按键, 部分设备不可用)的能力缺口。
     * 注意: 命中只回传关键词本身, 不回传整句, 因此只注册固定短语, 不注册"我要看/换台到"类带参说法。
     */
    public static final String[] DEVICE_SCENE_KEYWORDS = {
            "下一台", "上一个台", "上一个频道", "换个台",
            "暂停", "暂停播放", "继续播放",
            "下一集", "上一集", "重播", "重新播放", "退出播放",
            "第一个", "第二个", "第三个", "第四个", "第五个", "第六个", "第七个", "第八个", "第九个", "第十个",
            "第十一个", "第十二个", "第十三个", "第十四个", "第十五个", "第十六个", "第十七个", "第十八个", "第十九个", "第二十个",
            "第1个", "第2个", "第3个", "第4个", "第5个", "第6个", "第7个", "第8个", "第9个", "第10个",
            "第11个", "第12个", "第13个", "第14个", "第15个", "第16个", "第17个", "第18个", "第19个", "第20个"
    };

    /** 注册自定义场景(关键词数组): extras含keywords(String[]) */
    private static void registerDeviceScene(Context context, String pkg) {
        String err = "";
        String mode = "无";
        try {
            Intent intent = new Intent(AIOpenConstant.ACTION_APP_REGISTER);
            Bundle data = new Bundle();
            data.putString(AIOpenConstant.PARAM_PACKAGE_NAME, context.getPackageName());
            data.putLong(AIOpenConstant.PARAM_CATEGORY, AIOpenConstant.SEMANTIC_DEVICE_SCENE);
            data.putStringArray(AIOpenConstant.PARAM_KEYWORDS, DEVICE_SCENE_KEYWORDS);
            intent.putExtras(data);
            intent.setPackage(pkg);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
                mode = "service";
            } catch (Throwable th) {
                err = th.getMessage() == null ? th.toString() : th.getMessage();
            }
            try {
                context.sendBroadcast(intent);
                mode = mode.equals("service") ? "service+broadcast" : "broadcast";
            } catch (Throwable th) {
                if (err.isEmpty()) err = th.getMessage() == null ? th.toString() : th.getMessage();
            }
            LOG.i("AIOpenRegister: pkg=" + pkg + " 自定义场景关键词" + DEVICE_SCENE_KEYWORDS.length + "个 mode=" + mode
                    + (err.isEmpty() ? "" : " err=" + err));
        } finally {
            record(context, (err.isEmpty() ? "已向" + pkg + "注册自定义场景关键词" + DEVICE_SCENE_KEYWORDS.length + "个, 接口: " + mode
                    : "向" + pkg + "注册自定义场景异常: " + err), err.isEmpty());
        }
    }

    /** 向指定夏杰语音包注册单个场景类别(精确值: 4直播/8影视) */
    public static void register(Context context, String pkg, long category) {
        String err = "";
        String mode = "无";
        try {
            Intent intent = new Intent(AIOpenConstant.ACTION_APP_REGISTER);
            Bundle data = new Bundle();
            data.putString(AIOpenConstant.PARAM_PACKAGE_NAME, context.getPackageName());
            data.putLong(AIOpenConstant.PARAM_CATEGORY, category);
            intent.putExtras(data);
            intent.setPackage(pkg);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
                mode = "service";
            } catch (Throwable th) {
                err = th.getMessage() == null ? th.toString() : th.getMessage();
            }
            try {
                context.sendBroadcast(intent);
                mode = mode.equals("service") ? "service+broadcast" : "broadcast";
            } catch (Throwable th) {
                if (err.isEmpty()) err = th.getMessage() == null ? th.toString() : th.getMessage();
            }
            LOG.i("AIOpenRegister: pkg=" + pkg + " category=0x" + Long.toHexString(category)
                    + " mode=" + mode + (err.isEmpty() ? "" : " err=" + err));
        } finally {
            record(context, (err.isEmpty() ? "已向" + pkg + "注册场景" + sceneName(category)
                    + ", 接口: " + mode : "向" + pkg + "注册异常: " + err), err.isEmpty());
        }
    }

    private static String sceneName(long category) {
        if (category == AIOpenConstant.SEMANTIC_LIVE) return "(直播)";
        if (category == AIOpenConstant.SEMANTIC_VIDEO) return "(影视)";
        return "(0x" + Long.toHexString(category) + ")";
    }

    private static void record(Context context, String msg, boolean ok) {
        JsonObject result = new JsonObject();
        result.addProperty("code", ok ? 0 : -1);
        result.addProperty("msg", msg);
        VoiceLogManager.get().record(VoiceLogManager.SOURCE_XIAJIE, "AIOpen注册",
                "category=" + (ok ? "4直播/8影视" : msg), result);
    }

    private static boolean isInstalled(Context context, String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
