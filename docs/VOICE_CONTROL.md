# 语音助手接入指南(小爱音箱 / 天猫精灵 / 小度智能音箱)

本项目在原有**远程搜索**链路基础上, 增加了统一的**语音指令**入口, 支持小爱音箱、天猫精灵、小度智能音箱等语音助手控制 TVBox:

- 语音搜索(与远程搜索一致: `搜索狂飙`、直接说片名)
- 语音换台(电视直播): `换台到CCTV5`、`中央一台`、`第5台`、`下一个台`
- 语音换剧集: `第12集`、`下一集`、`上一集`
- 播放控制: `暂停`、`继续播放`、`快进30秒`、`音量大点`、`静音`

## 一、支持的语音指令

| 类型 | 指令示例 | 说明 |
| --- | --- | --- |
| 搜索 | `搜索狂飙` / `帮我搜一下庆余年` / `狂飙` | 直接说片名也按搜索处理 |
| 换台(按名) | `换台到CCTV5` / `切换到湖南卫视` / `中央一台` / `CCTV5` | 中央/央视N台自动对应CCTV-N, 频道名模糊匹配 |
| 换台(按号) | `第5台` / `换到第35台` / `换台15` | 全量频道序号(跳过加密分组), 从1开始 |
| 换台(上下) | `下一个台` / `上个台` / `换台` | "换台"单独说=下一个台 |
| 换剧集 | `第12集` / `播放第三集` / `下一集` / `上一集` | 中文数字支持(一~百) |
| 播放控制 | `暂停` / `继续播放` / `快进30秒` / `快退2分钟` | 直播模式下不支持快进快退 |
| 音量 | `音量大点` / `小声点` / `静音` | 系统音量, 无需界面在前台 |

> 未识别的文本兜底按**搜索**处理, 与远程搜索交互习惯一致。
> 搜索关键词不做中文数字转换(《八佰》等片名不受影响); 换台指令会自动转换。

## 二、统一语音入口

所有入口最终都汇聚到 `VoiceCommandManager`(解析: `VoiceCommandParser`, 事件分发: `VoiceEvent`):

```
小爱音箱/天猫精灵/小度 ──云技能──► 你的技能服务端 ──HTTP──► http://<TVBox IP>:9978/voice?text=...
夏杰语音 ──AI智能体/自定义指令(打开网址)──► tvbox://voice?text=...
电视自带语音服务 ──标准搜索intent──► VoiceEntryActivity / VoiceReceiver
手机遥控App / Home Assistant ──广播──► am broadcast -a com.github.tvbox.osc.VOICE_COMMAND
网页遥控(http://<IP>:9978/) ──语音指令输入框──► POST /action?do=voice
```

### 1. HTTP API(推荐, 云技能服务端调用)

```bash
# GET
curl "http://192.168.1.100:9978/voice?text=%E6%90%9C%E7%B4%A2%E7%8B%82%E9%A3%99"

# POST(表单)
curl -X POST "http://192.168.1.100:9978/action" -d "do=voice&text=换台到CCTV5"
```

响应(JSON):

```json
{
  "code": 0,
  "msg": "正在切换到频道: CCTV5",
  "command": { "type": "live_channel", "keyword": "CCTV5" }
}
```

`command.type` 取值: `search` / `live_channel` / `live_next` / `live_prev` / `episode` / `episode_next` / `episode_prev` / `pause` / `resume` / `seek_forward` / `seek_back` / `volume_up` / `volume_down` / `mute` / `unknown`。

> 端口: 默认 `9978`, 被占用时自动递增到 9999; 电视与音箱/服务端需在同一局域网。

### 2. 广播入口(adb / 手机App / Home Assistant)

```bash
adb shell am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "搜索狂飙"
adb shell am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "换台到CCTV5"
adb shell am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "第12集"
```

extras 兼容字段: `text` / `query` / `word` / `keyword` / `voice_text` / `command` / `query(SearchManager.QUERY)`。
同时兼容系统搜索 intent: `android.intent.action.SEARCH`、`android.search.action.GLOBAL_SEARCH`、`android.intent.action.WEB_SEARCH`(部分电视端内置语音助手会以这类 intent 唤起第三方应用)。

## 三、三大音箱接入步骤(云技能)

三家音箱厂商均要求通过各自的开放平台注册技能, 由技能服务端接收语音文本后转发给 TVBox 的 HTTP 接口。

### 小爱音箱

1. 登录 [小爱技能开放平台](https://developers.xiaoai.mi.com), 创建**自定义技能**;
2. 配置意图(如: 搜片/换台/换剧集), 槽位为识别文本 `query`, 服务地址填你的技能服务端;
3. 技能服务端收到请求后, 把用户说的原句转发到 TVBox:

```python
# Flask 示例: 小爱自定义技能服务端
from flask import Flask, request, jsonify
import requests

app = Flask(__name__)
TVBOX = "http://192.168.1.100:9978/voice"   # 电视盒子的局域网地址

@app.route("/xiaoai", methods=["POST"])
def xiaoai():
    req = request.get_json(force=True)
    query = req.get("request", {}).get("query", "")
    if query:
        requests.get(TVBOX, params={"text": query}, timeout=3)
    return jsonify({"version": "1.0", "response": {
        "to_speak": {"type": 0, "text": "好的, 已在电视上执行"},
        "open_mic": False
    }})

app.run(host="0.0.0.0", port=8080)
```

### 天猫精灵

1. 登录 [天猫精灵开放平台(AliGenie)](https://www.aligenie.com), 创建**语音技能**(技能应用平台支持安卓生态应用/屏端设备接入);
2. 配置意图与槽位, 云端服务选择你部署的服务端;
3. 服务端把解析出的指令文本转发到 TVBox(与上面小爱示例同理):

```javascript
// Node.js 示例: AliGenie 云端服务
const express = require('express');
const app = express();
app.use(express.json());

app.post('/aligenie', async (req, res) => {
  const text = req.body.intentName || '';       // 或取槽位里的完整语句
  if (text) {
    await fetch('http://192.168.1.100:9978/voice?text=' + encodeURIComponent(text));
  }
  res.json({ returnCode: 'SUCCESS', returnErrorSolution: '', returnMsg: '' });
});
app.listen(8080);
```

### 小度智能音箱

1. 登录 [DuerOS 开放平台](https://dueros.baidu.com), 创建**自定义技能**;
2. 配置意图(搜索/换台/换剧集), 会话服务地址填你的技能服务端;
3. 服务端取出 `request.content.query`(用户原话)后转发到 TVBox, 处理逻辑与小爱/天猫精灵一致。

> 三家平台均需实名/审核, 并按各自规范填写服务端地址与返回报文; 服务端只需做一件事——把语音文本原样转给 `http://<TVBox IP>:9978/voice?text=<文本>`。

## 四、夏杰语音接入

[夏杰语音](http://app.shafa.com/apk/xiajieyuyin.html)是电视端即装即用的AI语音助手(支持20多种方言、内置AI大模型), **没有自带语音服务的电视/盒子可安装它**。它通过遥控器语音键采集音频→识别成文本→分发指令执行。TVBox 已预留三种对接通道:

### 方式1(推荐): AI智能体/自定义指令 + 深链

在夏杰语音中创建自定义指令/AI智能体(语音说一句话 → 执行"打开网址"):

| 语音说法 | 执行动作(打开网址) |
| --- | --- |
| "播放狂飙" | `tvbox://voice?text=搜索狂飙` |
| "换到CCTV5" | `tvbox://voice?text=换台到CCTV5` |
| "下一集" | `tvbox://voice?text=下一集` |
| "暂停" | `tvbox://voice?text=暂停` |

TVBox 收到深链后立即解析执行并自动退出入口页。搜索/换台/换剧集/播放控制全部可用。

### 方式2: 执行命令广播

若使用夏杰语音的"执行命令/终端指令"能力, 可直接发广播:

```
am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "搜索狂飙"
```

### 方式3: 语音唤起 + 标准搜索intent

直接说"打开TVBox搜索狂飙"。夏杰语音识别后会拉起应用并以标准搜索 intent(`SEARCH`/`GLOBAL_SEARCH`/`WEB_SEARCH`/`MEDIA_PLAY_FROM_SEARCH`)或语音输入法把文本送进 TVBox, 两条路径均已兼容。

### 验证(无需夏杰语音先自测)

```bash
adb shell am start -a android.intent.action.VIEW -d "tvbox://voice?text=搜索狂飙"
adb shell am start -a android.intent.action.VIEW -d "tvbox://search?word=狂飙"
```

## 五、电视自带语音服务接入

小米电视(小爱)、天猫魔盒、海信(小聚)、创维(小维)、TCL(小T)、长虹等电视自带的语音服务, 对第三方 App 的接入程度分两类:

**A. 遵循 Android 标准协议的入口(已自动兼容)**

TVBox 已注册以下标准 intent, 语音助手以任一方式分发均可到达:

| intent | 用途 |
| --- | --- |
| `android.media.action.MEDIA_PLAY_FROM_SEARCH` | "用TVBox播放狂飙"(Android官方媒体搜索协议) |
| `android.search.action.GLOBAL_SEARCH` | Android TV 全局搜索分发 |
| `android.intent.action.SEARCH` / `WEB_SEARCH` | 常规搜索唤起 |
| `com.github.tvbox.osc.VOICE_COMMAND`(广播) | 自定义指令广播 |

搜索词兼容 extras/URI 参数: `text`/`query`/`word`/`keyword`/`title`。

**B. 封闭型助手(只搜自家内容源)的桥接方法**

海信小聚/创维小维/TCL小T等把语音结果优先导向自家聚合平台, 不对第三方开放搜索分发。可行做法:

1. 在电视语音助手的**自定义指令/场景**里配置"打开网址": `tvbox://voice?text=<语音文本>`(与夏杰语音方式1相同);
2. 或走云技能方案(见第三节), 音箱与电视通用;
3. 或用手机遥控App/Home Assistant 发自定义广播(见第二节广播入口)。

> 注: 各厂商语音对第三方应用的支持因型号/系统版本而异, 以实际设备为准; 标准协议入口无需任何配置即已生效。

## 六、执行逻辑说明

| 指令 | 条件 | 行为 |
| --- | --- | --- |
| 搜索 | 任意界面 | 复用远程搜索链路(SearchReceiver), 打开搜索页 |
| 换台(指定频道) | 直播页在栈内 | 直接切换并播放 |
| 换台(指定频道) | 不在直播页 | 自动拉起直播页, 频道列表加载完成后切换 |
| 换台(上下台) | 需已在直播页 | 上/下一个台 |
| 换剧集/快进快退 | 需已在详情/播放页 | PlayFragment 切换 |
| 暂停/继续 | 直播或点播页 | VideoView 暂停/恢复 |
| 音量/静音 | 任意界面 | AudioManager 调节 |

## 七、网页遥控测试

无需音箱即可联调: 手机浏览器打开 `http://<TVBox IP>:9978/`, 搜索栏下方新增了**语音指令**输入框, 输入 `换台到CCTV5`、`第12集` 等点"发送"即可走与音箱完全相同的链路。

## 八、语音调用日志(仅限原厂语音/夏杰语音通道)

用于排查"语音助手到底有没有把指令送到TVBox、送了什么文本、解析执行结果如何"。

### 记录范围

| 来源 | 触发条件 |
| --- | --- |
| 电视原厂语音 | 电视/投影仪/盒子自带语音服务以系统标准搜索intent调用(SEARCH / GLOBAL_SEARCH / WEB_SEARCH / MEDIA_PLAY_FROM_SEARCH, activity或广播) |
| 夏杰语音 | tvbox://voice / tvbox://search 深链调用 |
| 未知来源 | 显式component方式打开 VoiceEntryActivity(如 adb 指定组件名) |

**不记录**: HTTP `/voice` 接口、网页遥控语音输入框、自定义广播 `com.github.tvbox.osc.VOICE_COMMAND`(通用工具通道)。
另外: 即使收到调用但未能提取到语音文本也会记录一条"未提取到语音文本", 便于发现助手侧参数配置问题。

### 查看方式

1. **网页遥控**(推荐, 手机浏览器即可): 打开 `http://<TVBox IP>:9978/`, 语音指令输入框旁点 **"日志"** 按钮;
2. **HTTP接口**:
   - `GET /voiceLog` → JSON数组(时间倒序)
   - `GET /voiceLog?format=text` → 纯文本
   - `GET /voiceLog?clear=1` → 清空日志
3. **logcat**: `adb logcat -s TVBox-runtime`, 每条调用输出前缀 `VoiceLog[来源][动作]`。

### 单条日志字段

`time`(时间) / `source`(来源) / `action`(intent action) / `rawText`(语音文本) / `code`(0成功-1失败) / `msg`(执行结果) / `command`(解析出的指令)。

> 日志仅保存在内存(最近100条), 应用退出后清空; 如需长期留存, 用 `adb logcat -s TVBox-runtime > voice.log` 抓取。

## 九、源码位置

- 解析器: `app/src/main/java/com/github/tvbox/osc/voice/VoiceCommandParser.java`
- 调度中心: `app/src/main/java/com/github/tvbox/osc/voice/VoiceCommandManager.java`(含 extractText 文本提取)
- 调用日志: `app/src/main/java/com/github/tvbox/osc/voice/VoiceLogManager.java`
- 指令模型/事件: `voice/VoiceCommand.java`、`event/VoiceEvent.java`
- 直播语音执行: `ui/activity/LivePlayActivity.java`(`onVoiceCommand`)
- 点播语音执行: `ui/fragment/PlayFragment.java`(`onVoiceCommand`)
- HTTP 入口: `server/RemoteServer.java`(`/voice`、`/voiceLog`)、`server/InputRequestProcess.java`(`do=voice`)
- 广播入口: `receiver/VoiceReceiver.java`
- 深链入口: `ui/activity/VoiceEntryActivity.java`(`tvbox://voice` / `tvbox://search`)
