# 离线语音导航助手 / AMapVoiceAssistant Offline Paraformer

这是车机高德语音助手的离线识别版本。

## 功能

- 使用 sherpa-onnx
- 使用 Paraformer 中文/英文流式模型
- 本地离线识别，不依赖 Android 系统 SpeechRecognizer
- 识别“导航到萍乡北站 / 我要去润达国际”等短句
- 自动提取目的地
- 调起高德车机版：

```text
androidauto://keywordNavi?sourceApplication=高德地图&keywords=目的地&style=2
```

## 重要说明

项目源码里不直接放模型和 native so 文件，避免仓库太大。  
GitHub Actions 会在编译时自动下载：

- sherpa-onnx Android native libs
- sherpa-onnx-streaming-paraformer-bilingual-zh-en 模型

所以第一次编译会比较慢，APK 也会比较大。

## 上传到 GitHub

把这些文件上传到仓库根目录：

```text
.github
app
build.gradle.kts
settings.gradle.kts
README.md
.gitignore
```

然后运行：

```text
Actions → Build Android Offline Paraformer APK → Run workflow
```

构建完成后，从 Artifacts 下载：

```text
AMapVoiceAssistant-OfflineParaformer-debug-apk
```

## 使用方式

1. 安装 APK
2. 打开 App
3. 等待“模型加载完成”
4. 点击“开始离线识别”
5. 说：“导航到萍乡北站”
6. 点击“停止并导航”
7. App 会调起高德车机版并显示目标页面

## 当前限制

当前版本是“点击开始 → 说话 → 点击停止并导航”。  
后续可以继续升级：

- 自动端点检测后直接导航
- 加唤醒词“小初音”
- 加语音播报
- 加自动点击“开始导航”或改成坐标直达导航
