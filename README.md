# 高德车机语音助手 / AMapVoiceAssistant

这是一个给安卓车机用的最小可用语音助手 App。

它的第一版目标：

- 语音识别目的地
- 自动提取“导航到 XXX”里的 XXX
- 调起高德车机版
- 使用你已经实测可用的接口：

```text
androidauto://keywordNavi?sourceApplication=高德地图&keywords=目的地&style=2
```

## 已知前提

你的车机高德包名是：

```text
com.autonavi.amapauto
```

并且你已经用 ADB 测试通过：

```bat
adb shell "am start -W -a android.intent.action.VIEW -c android.intent.category.DEFAULT -d 'androidauto://keywordNavi?sourceApplication=高德地图&keywords=萍乡北站&style=2'"
```

## 使用方式

打开 App 后可以：

1. 点击「开始语音」
2. 说：“导航到萍乡北站”
3. App 会提取目的地“萍乡北站”
4. 自动调起高德车机版的目的地页面

也可以手动输入目的地，然后点「导航」。

## GitHub 上传方式

把这些文件夹/文件上传到你的 GitHub 仓库根目录：

```text
.github
app
build.gradle.kts
settings.gradle.kts
README.md
```

上传后进入：

```text
Actions → Build Android APK → Run workflow
```

打包完成后，在 Actions 的 Artifacts 里下载：

```text
AMapVoiceAssistant-debug-apk
```

里面就是 APK。

## 注意

当前版本不自动点击“开始导航”按钮。  
它只负责把目的地传给高德车机版，并显示导航目的地页面。

如果后续需要自动点击开始导航，有两条路线：

1. 用无障碍服务识别“开始导航”按钮并点击
2. 用高德 POI 查询把文字转成坐标，再调用 `androidauto://navi`

第一版先保证“语音目的地 → 高德目的地页面”跑通。
