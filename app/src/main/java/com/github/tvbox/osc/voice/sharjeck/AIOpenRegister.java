package com.github.tvbox.osc.voice.sharjeck;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.github.tvbox.osc.util.LOG;

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

    /** 注册全部支持的场景(直播+影视) */
    public static void registerAll(Context context) {
        register(context, AIOpenConstant.SEMANTIC_LIVE);
        register(context, AIOpenConstant.SEMANTIC_VIDEO);
    }

    /** 按场景类别注册 */
    public static void register(Context context, long category) {
        for (String pkg : new String[]{AIOpenConstant.PKG_AISPEECH_CN, AIOpenConstant.PKG_AISPEECH_GLOBAL}) {
            if (!isInstalled(context, pkg)) continue;
            try {
                Intent intent = new Intent(AIOpenConstant.ACTION_APP_REGISTER);
                Bundle data = new Bundle();
                data.putString(AIOpenConstant.PARAM_PACKAGE_NAME, context.getPackageName());
                data.putLong(AIOpenConstant.PARAM_CATEGORY, category);
                intent.putExtras(data);
                intent.setPackage(pkg);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
                LOG.i("AIOpenRegister: 已向" + pkg + "注册场景 category=" + category);
            } catch (Throwable th) {
                LOG.e("AIOpenRegister: 注册失败 pkg=" + pkg + " category=" + category + " err=" + th.getMessage());
            }
        }
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
