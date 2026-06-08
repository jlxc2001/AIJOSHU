package com.jlxc.amapvoiceassistant;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_RECORD_AUDIO = 1001;

    private SpeechRecognizer speechRecognizer;
    private TextView statusText;
    private TextView resultText;
    private EditText manualInput;
    private Button voiceButton;
    private Button navigateButton;

    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        ensureAudioPermission();
        initSpeechRecognizer();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 32, 48, 32);
        root.setBackgroundColor(Color.rgb(16, 24, 32));

        TextView title = new TextView(this);
        title.setText("高德车机语音助手");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setText("说：导航到萍乡北站 / 我要去润达国际");
        statusText.setTextColor(Color.rgb(160, 220, 220));
        statusText.setTextSize(20);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 16, 0, 12);
        root.addView(statusText);

        resultText = new TextView(this);
        resultText.setText("识别结果会显示在这里");
        resultText.setTextColor(Color.WHITE);
        resultText.setTextSize(24);
        resultText.setGravity(Gravity.CENTER);
        resultText.setPadding(0, 10, 0, 18);
        root.addView(resultText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        manualInput = new EditText(this);
        manualInput.setHint("也可以手动输入目的地，例如：萍乡北站");
        manualInput.setSingleLine(true);
        manualInput.setTextColor(Color.WHITE);
        manualInput.setHintTextColor(Color.rgb(135, 155, 165));
        manualInput.setTextSize(22);
        manualInput.setInputType(InputType.TYPE_CLASS_TEXT);
        manualInput.setPadding(20, 10, 20, 10);
        root.addView(manualInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, 22, 0, 0);

        voiceButton = new Button(this);
        voiceButton.setText("开始语音");
        voiceButton.setTextSize(22);
        voiceButton.setOnClickListener(v -> startListening());
        buttons.addView(voiceButton, new LinearLayout.LayoutParams(0, 72, 1));

        navigateButton = new Button(this);
        navigateButton.setText("导航");
        navigateButton.setTextSize(22);
        navigateButton.setOnClickListener(v -> {
            String text = manualInput.getText().toString().trim();
            if (text.length() == 0) {
                showStatus("请先输入或语音说出目的地");
                return;
            }
            navigateToKeyword(cleanDestination(text));
        });
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(0, 72, 1);
        navParams.setMargins(20, 0, 0, 0);
        buttons.addView(navigateButton, navParams);

        root.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView note = new TextView(this);
        note.setText("当前版本使用 androidauto://keywordNavi?keywords=目的地 调起高德车机版。");
        note.setTextColor(Color.rgb(130, 150, 160));
        note.setTextSize(16);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 22, 0, 0);
        root.addView(note);

        setContentView(root);
    }

    private void ensureAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        }
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showStatus("当前系统没有可用语音识别服务，可以先用手动输入测试导航");
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                isListening = true;
                voiceButton.setText("正在听...");
                showStatus("请说目的地，例如：导航到萍乡北站");
            }

            @Override public void onBeginningOfSpeech() {
                showStatus("正在识别...");
            }

            @Override public void onRmsChanged(float rmsdB) {}

            @Override public void onBufferReceived(byte[] buffer) {}

            @Override public void onEndOfSpeech() {
                isListening = false;
                voiceButton.setText("开始语音");
                showStatus("识别结束，正在处理...");
            }

            @Override public void onError(int error) {
                isListening = false;
                voiceButton.setText("开始语音");
                showStatus("语音识别失败，错误码：" + error + "。可以手动输入目的地测试。");
            }

            @Override public void onResults(Bundle results) {
                isListening = false;
                voiceButton.setText("开始语音");

                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list == null || list.isEmpty()) {
                    showStatus("没有识别到内容");
                    return;
                }

                String recognized = list.get(0);
                resultText.setText("识别：" + recognized);

                String destination = cleanDestination(recognized);
                manualInput.setText(destination);

                if (destination.length() == 0) {
                    showStatus("没有提取到目的地，请换一种说法");
                    return;
                }

                showStatus("准备导航到：" + destination);
                navigateToKeyword(destination);
            }

            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    resultText.setText("正在听：" + list.get(0));
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ensureAudioPermission();
            return;
        }

        if (speechRecognizer == null) {
            initSpeechRecognizer();
            if (speechRecognizer == null) {
                showStatus("当前系统没有可用语音识别服务");
                return;
            }
        }

        if (isListening) {
            speechRecognizer.stopListening();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说目的地");

        speechRecognizer.startListening(intent);
    }

    private String cleanDestination(String raw) {
        if (raw == null) return "";

        String s = raw.trim();

        String[] removeWords = new String[]{
                "小初音", "初音", "高德地图", "高德",
                "帮我", "请帮我", "麻烦",
                "导航到", "导航去", "导航", "带我去", "我要去", "我想去",
                "开车去", "去一下", "去"
        };

        for (String w : removeWords) {
            s = s.replace(w, "");
        }

        s = s.replace("。", "")
                .replace("，", "")
                .replace(",", "")
                .replace(".", "")
                .replace("！", "")
                .replace("!", "")
                .replace("？", "")
                .replace("?", "")
                .replace(" ", "")
                .trim();

        return s;
    }

    private void navigateToKeyword(String destination) {
        if (destination == null || destination.trim().length() == 0) {
            showStatus("目的地为空");
            return;
        }

        String appName = Uri.encode("高德地图");
        String keyword = Uri.encode(destination.trim());

        String uri = "androidauto://keywordNavi"
                + "?sourceApplication=" + appName
                + "&keywords=" + keyword
                + "&style=2";

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse(uri));

        try {
            startActivity(intent);
            showStatus("已发送到高德车机版：" + destination);
        } catch (ActivityNotFoundException e) {
            showStatus("没有找到可处理 androidauto:// 的高德车机版");
            Toast.makeText(this, "未找到高德车机版", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showStatus("调起高德失败：" + e.getMessage());
        }
    }

    private void showStatus(String text) {
        statusText.setText(text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
}
