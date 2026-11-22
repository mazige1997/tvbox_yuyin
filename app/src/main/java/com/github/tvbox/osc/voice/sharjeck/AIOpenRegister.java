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
 * 对接软件主动向夏杰语音发送注册信息(service方式, 4.0.6.0以上版本支持),
 * 声明本应用支持的场景类别(直播SEMANTIC_LIVE/影视SEMANTIC_VIDEO);
 * 注册后夏杰语音会把对应场景的语音指令分发到本应用的
 * SharjeckService/SharjeckReceiver(action.live/action.video)。
 * 收到 app.register.require 广播时需按category重新注册。
 * 大陆版/国际版夏杰语音均已安装时同时注册。
 *
 * @author voice
 */
public class AIOpenRegister {

    /** 注册全部支持的场景(直播+影视, category为位标志合并后一次注册) */
    public static void registerAll(Context context) {
        if (!Hawk.get(HawkConfig.VOICE_CONTROL, true)) {
            LOG.i("AIOpenRegister: 语音控制已关闭, 跳过注册");
            VoiceLogManager.get().record(VoiceLogManager.SOURCE_XIAJIE, "AIOpen注册",
                    "语音控制已关闭, 跳过注册", simpleResult(0, "语音控制已关闭, 跳过注册"));
            return;
        }
        register(context, AIOpenConstant.SEMANTIC_LIVE | AIOpenConstant.SEMANTIC_VIDEO);
    }

    /** 按场景类别注册: 同时走广播与服务两个动态注册接口(广播为老版本兼容通道) */
    public static void register(Context context, long category) {
        for (String pkg : new String[]{AIOpenConstant.PKG_AISPEECH_CN, AIOpenConstant.PKG_AISPEECH_GLOBAL}) {
            if (!isInstalled(context, pkg)) continue;
            String mode = "无";
            String err = "";
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
                    err = err.isEmpty() ? (th.getMessage() == null ? th.toString() : th.getMessage()) : err;
                }
                LOG.i("AIOpenRegister: pkg=" + pkg + " category=0x" + Long.toHexString(category)
                        + " mode=" + mode + (err.isEmpty() ? "" : " err=" + err));
            } finally {
                JsonObject result = new JsonObject();
                result.addProperty("code", err.isEmpty() ? 0 : -1);
                result.addProperty("msg", err.isEmpty()
                        ? "已向" + pkg + "注册场景(直播+影视), 接口: " + mode
                        : "向" + pkg + "注册异常: " + err);
                VoiceLogManager.get().record(VoiceLogManager.SOURCE_XIAJIE, "AIOpen注册",
                        "category=0x" + Long.toHexString(category), result);
            }
        }
    }

    private static JsonObject simpleResult(int code, String msg) {
        JsonObject result = new JsonObject();
        result.addProperty("code", code);
        result.addProperty("msg", msg);
        return result;
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
