<<<<<<<<<<<<<<json接口示例>>>>>>>>>>>>>
# TVBox

=== Source Code - Editing the app default settings ===

    //searchable:搜索开关	        0:关闭 1:启用
    //filterable:首页可选	        0:否 1:是
    //playerType:播放器类型	        0:系统 1:IJK 2:EXO
    //采集接口类型		        0:xml 1:json 3:jar 4:remote
    //parses解析类型		        0:嗅探,自带播放器 1:解析,返回直链
    //直播参数说明                   ua:用户自定义ua epg:节目网址 logo:台标网址
    
    {
	"spider": "./your.jar",
	"wallpaper": "./api/img",
	"sites": [],
	"parses": [],
	"hosts": [
		"cache.ott.ystenlive.itv.cmvideo.cn=base-v4-free-mghy.e.cdn.chinamobile.com",
		"cache.ott.bestlive.itv.cmvideo.cn=ip"
	],
	"lives": [],
	"rules": [],
	"doh": [
		{
			"name": "騰訊",
			"url": "https://doh.pub/dns-query"
		},
		{
			"name": "阿里",
			"url": "https://dns.alidns.com/dns-query"
		},
		{
			"name": "360",
			"url": "https://doh.360.cn/dns-query"
		}
	]
    }

=== 语音控制(小爱音箱/天猫精灵/小度/夏杰语音/电视自带语音) ===

    支持语音搜索/语音换台(电视直播)/语音换剧集/播放控制
    夏杰语音: AIOpen协议动态对接, 运行即自动注册, 直接支持语音换台/搜片/播放控制
    统一语音入口:
        HTTP:  http://<TVBox IP>:9978/voice?text=<语音文本>
        广播:  am broadcast -a com.github.tvbox.osc.VOICE_COMMAND --es text "<语音文本>"
        深链:  tvbox://voice?text=<语音文本>   (夏杰语音AI智能体/自定义指令"打开网址")
        标准:  MEDIA_PLAY_FROM_SEARCH / GLOBAL_SEARCH 等系统搜索intent已自动兼容
    各类语音服务接入步骤与指令清单见 docs/VOICE_CONTROL.md

=== 自动编译(GitHub Actions) ===

    push到master/main / 打v*标签 / 手动触发 时自动编译:
    - java flavor(debug+release, 双ABI) 上传artifact: TVBox-java-apk
    - python flavor(release, 需Chaquopy+Python3.8) artifact: TVBox-python-apk
    release APK未签名, 安装前需自行签名; debug APK可直接安装
    APK命名含ABI标识: -universal(32+64位合并) / -arm32(32位专用) / -arm64(64位专用)
    产物: java版(debug/release universal + release java32/java64) + python版(release python32/python64)
    APK同时发布到GitHub Release:
    - push到master/main → 自动更新 latest 预发布(pre-release)
    - 打v*标签 → 正式Release(以标签命名)
