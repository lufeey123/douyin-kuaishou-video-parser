package com.example.videoparser;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;

import com.example.videoparser.glass.AuroraDrawable;
import com.example.videoparser.glass.GlassSurface;
import com.example.videoparser.parser.BatchParser;
import com.example.videoparser.parser.VideoParser;
import com.example.videoparser.parser.VideoInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground batch parsing. Only an explicit user action creates download tasks. */
public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(28, 48, 57);
    private static final int MUTED = Color.rgb(82, 101, 112);
    private static final int ACCENT = Color.rgb(22, 104, 117);
    private static final int MINT = Color.rgb(225, 240, 241);
    private static final int LINE = Color.rgb(221, 231, 233);
    private static final int ERROR = Color.rgb(181, 71, 69);
    private static final int PAPER = Color.rgb(243, 247, 248);
    private static final int MAX_INPUT = 64000;
    private static final int MAX_BATCH = 20;
    private static final int WAITING = 0, RUNNING = 1, SUCCESS = 2, FAILED = 3, STOPPED = 4;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BatchItem> items = new ArrayList<>();
    private final Set<String> queuedInputs = new HashSet<>(), queuedUrls = new HashSet<>();
    private AtomicBoolean cancellation = new AtomicBoolean(false);
    private Future<?> parseTask, coverTask;
    private EditText input;
    private Button parseButton, pasteButton, clearButton, retryButton, copyAllButton, downloadAllButton, detailDownload;
    private TextView status, resultTitle, resultPlatform, resultSource, coverStatus, batchSummary, actionStatus;
    private TextView inputHint, successCount, failedCount, pendingCount, bottomHint;
    private ProgressBar progress;
    private LinearLayout batchCard, batchRows, emptyState, batchActions, batchStats;
    private FrameLayout rootLayout;
    private Dialog detailDialog;
    private TextView detailAction;
    private int detectedCount;
    private Runnable inputCheck;
    private ScrollView scroll;
    private ImageView cover;
    private BatchItem currentItem;
    private boolean destroyed, busy;
    private int generation, coverGeneration;

    private static final class BatchItem {
        final String input;
        VideoInfo info;
        String error = "";
        int state = WAITING;
        BatchItem(String input) { this.input = input; }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            // The root applies all insets explicitly on Android 10 as well.
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        buildUi();
        if (savedInstanceState != null) input.setText(savedInstanceState.getString("input", ""));
        else acceptIntent(getIntent());
    }

    private void buildUi() {
        rootLayout = new FrameLayout(this);
        rootLayout.setFocusableInTouchMode(true);
        rootLayout.setBackground(new AuroraDrawable());
        rootLayout.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
                return insets;
            }
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0, 0, 0, dp(16));
        rootLayout.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(8), dp(20), dp(24));
        scroll.addView(content);
        setContentView(rootLayout);
        rootLayout.requestApplyInsets();

        LinearLayout brand = horizontal();
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(icon(R.drawable.ic_brand, ACCENT, 22));
        logo.setContentDescription("拾影");
        brand.addView(logo, new LinearLayout.LayoutParams(dp(26), dp(48)));
        TextView name = text("拾影", 20, INK);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.setMarginStart(dp(9));
        brand.addView(name, nameParams);
        Button help = quietButton("帮助");
        help.setContentDescription("查看使用说明");
        help.setTextSize(12);
        help.setOnClickListener(v -> showHelp());
        brand.addView(help, new LinearLayout.LayoutParams(-2, -2));
        content.addView(brand);
        add(content, text("抖音 / 快手分享链接 · 每次最多 20 条", 12, MUTED), 4);

        LinearLayout editor = vertical();
        editor.setPadding(dp(16), dp(6), dp(16), dp(16));
        editor.setBackground(GlassSurface.material(this, 20));
        add(content, editor, 16);
        LinearLayout editorTitle = horizontal();
        TextView label = text("分享链接", 15, INK);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        editorTitle.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        pasteButton = quietButton("粘贴");
        pasteButton.setCompoundDrawables(icon(R.drawable.ic_paste, ACCENT, 16), null, null, null);
        pasteButton.setOnClickListener(v -> paste());
        editorTitle.addView(pasteButton, new LinearLayout.LayoutParams(-2, -2));
        editor.addView(editorTitle);
        input = new EditText(this);
        input.setTextSize(14);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setHint("粘贴链接或整段分享文案\n多个作品可换行粘贴");
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setVerticalScrollBarEnabled(true);
        input.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(MAX_INPUT)});
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, dp(10), 0, dp(6));
        editor.addView(input, new LinearLayout.LayoutParams(-1, dp(88)));
        input.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                v.getParent().requestDisallowInterceptTouchEvent(true);
            else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
        LinearLayout inputFooter = horizontal();
        inputHint = text("支持整段分享文案", 12, MUTED);
        inputHint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        inputHint.setMaxLines(2);
        inputHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        inputFooter.addView(inputHint, new LinearLayout.LayoutParams(0, -2, 1));
        clearButton = quietButton("清空");
        clearButton.setTextColor(MUTED);
        clearButton.setContentDescription("清空分享内容");
        clearButton.setOnClickListener(v -> { if (!busy) { reset(); input.setText(""); setStatus("已清空，可以粘贴新的分享内容。", false); } });
        inputFooter.addView(clearButton, new LinearLayout.LayoutParams(-2, -2));
        add(editor, inputFooter, 0);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleInputCheck(); }
            @Override public void afterTextChanged(Editable value) { }
        });

        View editorDivider = new View(this);
        editorDivider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.bottomMargin = dp(12);
        editor.addView(editorDivider, dividerParams);
        LinearLayout editorActions = horizontal();
        boolean stackedEditorActions = getResources().getConfiguration().screenWidthDp < 360
                || getResources().getConfiguration().fontScale >= 1.3f;
        if (stackedEditorActions) editorActions.setOrientation(LinearLayout.VERTICAL);
        bottomHint = text("粘贴后开始", 12, MUTED);
        bottomHint.setMaxLines(2);
        bottomHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hintParams = stackedEditorActions
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1);
        if (!stackedEditorActions) hintParams.setMarginEnd(dp(12));
        editorActions.addView(bottomHint, hintParams);
        parseButton = button("开始解析", true);
        parseButton.setMinWidth(dp(124));
        parseButton.setMinimumWidth(dp(124));
        parseButton.setMaxLines(2);
        parseButton.setCompoundDrawables(null, null, icon(R.drawable.ic_arrow, Color.WHITE, 17), null);
        parseButton.setOnClickListener(v -> { if (busy) cancelBatch(); else beginParse(); });
        LinearLayout.LayoutParams parseParams = new LinearLayout.LayoutParams(
                stackedEditorActions ? -1 : dp(136), -2);
        if (stackedEditorActions) parseParams.topMargin = dp(8);
        editorActions.addView(parseButton, parseParams);
        editor.addView(editorActions);

        status = text("", 13, MUTED);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(dp(4), dp(2), dp(4), dp(2));
        status.setVisibility(View.GONE);
        add(content, status, 12);
        emptyState = vertical();
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(18), dp(28), dp(18), dp(22));
        ImageView emptyIcon = new ImageView(this);
        emptyIcon.setImageDrawable(icon(R.drawable.ic_link, ACCENT, 27));
        emptyIcon.setPadding(dp(15), dp(15), dp(15), dp(15));
        emptyIcon.setBackground(shape(MINT, 22));
        emptyState.addView(emptyIcon, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView emptyTitle = text("解析结果会显示在这里", 14, INK);
        emptyTitle.setGravity(Gravity.CENTER);
        add(emptyState, emptyTitle, 14);
        TextView emptyCopy = text("粘贴分享内容，预览或保存喜欢的作品", 12, MUTED);
        emptyCopy.setGravity(Gravity.CENTER);
        add(emptyState, emptyCopy, 7);
        add(content, emptyState, 12);

        batchCard = vertical();
        add(content, batchCard, 20);
        batchCard.setVisibility(View.GONE);
        LinearLayout queueHeading = horizontal();
        TextView queueTitle = text("解析结果", 18, INK);
        queueTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        queueHeading.addView(queueTitle, new LinearLayout.LayoutParams(0, -2, 1));
        batchSummary = text("", 12, MUTED);
        queueHeading.addView(batchSummary);
        batchCard.addView(queueHeading);
        batchStats = horizontal();
        successCount = stat(batchStats, "成功", ACCENT, MINT);
        failedCount = stat(batchStats, "失败", ERROR, Color.rgb(251, 235, 229));
        pendingCount = stat(batchStats, "待处理", MUTED, PAPER);
        add(batchCard, batchStats, 8);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        progress.setVisibility(View.GONE);
        add(batchCard, progress, 6);
        batchRows = vertical();
        add(batchCard, batchRows, 2);
        batchActions = horizontal();
        copyAllButton = button("复制链接", false);
        copyAllButton.setCompoundDrawables(icon(R.drawable.ic_copy, ACCENT, 16), null, null, null);
        copyAllButton.setOnClickListener(v -> copyAll());
        downloadAllButton = button("下载全部", false);
        downloadAllButton.setCompoundDrawables(icon(R.drawable.ic_download, ACCENT, 16), null, null, null);
        downloadAllButton.setOnClickListener(v -> downloadAll());
        addPair(batchActions, copyAllButton, downloadAllButton);
        add(batchCard, batchActions, 12);
        retryButton = quietButton("重试失败与未完成项");
        retryButton.setCompoundDrawables(icon(R.drawable.ic_retry, ACCENT, 16), null, null, null);
        retryButton.setOnClickListener(v -> retryIncomplete());
        add(batchCard, retryButton, 6);
        actionStatus = text("", 12, ACCENT);
        actionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        actionStatus.setVisibility(View.GONE);
        add(batchCard, actionStatus, 7);

        TextView privacy = text("仅访问分享链接所属平台 · 不自动下载", 11, MUTED);
        privacy.setGravity(Gravity.CENTER);
        add(content, privacy, 20);
        updateInputHint();
        rootLayout.requestFocus();
    }

    private void scheduleInputCheck() {
        if (inputCheck != null) handler.removeCallbacks(inputCheck);
        inputCheck = () -> { if (!destroyed) updateInputHint(); };
        handler.postDelayed(inputCheck, 220);
    }

    private void updateInputHint() {
        String value = input.getText().toString();
        detectedCount = 0;
        if (value.trim().isEmpty()) {
            inputHint.setText("支持整段分享文案");
            inputHint.setTextColor(MUTED);
        } else {
            try {
                detectedCount = VideoParser.extractLinks(value, MAX_BATCH).size();
                inputHint.setText("已识别 " + detectedCount + " 条 · 已去重");
                inputHint.setTextColor(ACCENT);
            } catch (Exception error) {
                inputHint.setText(error.getMessage() == null ? "暂未识别到支持的链接" : error.getMessage());
                inputHint.setTextColor(ERROR);
            }
        }
        clearButton.setEnabled(!busy && !value.isEmpty());
        if (!busy) {
            parseButton.setEnabled(detectedCount > 0);
            parseButton.setText(detectedCount > 1 ? "解析 " + detectedCount + " 条" : "开始解析");
            bottomHint.setText(detectedCount > 0 ? "准备就绪" : "粘贴后开始");
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("使用拾影")
            .setMessage("复制抖音或快手分享内容，粘贴后开始解析。多个链接可换行粘贴，每次最多 20 条。\n\n解析过程中可以停止；失败或未完成的项目可以重试。下载需要主动点击，进度以系统下载界面为准。\n\n播放源不代表画面绝对无水印，请先预览确认，仅保存已获授权的作品。")
            .setNeutralButton("开源说明", (dialog, which) -> showOpenSource())
            .setPositiveButton("知道了", null).show();
    }

    private void showOpenSource() {
        String license;
        try (InputStream stream = getAssets().open("licenses/QmDeve-MIT.txt");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            license = new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception unavailable) {
            license = "MIT License · Copyright 2025–2026 Donny Yang / QmDeve";
        }
        new AlertDialog.Builder(this).setTitle("开源说明")
            .setMessage("源码收录了 QmDeve/AndroidLiquidGlassView 的 AGSL 渲染适配与 MIT 许可。当前界面使用静态玻璃材质和渐变背景，未启用实时液态玻璃采样。\n\nhttps://github.com/QmDeve/AndroidLiquidGlassView\n\n" + license)
            .setPositiveButton("关闭", null).show();
    }

    private void acceptIntent(Intent intent) {
        if (intent == null) return;
        String shared = null;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) shared = intent.getDataString();
        }
        if (shared != null) {
            reset();
            input.setText(shared.length() > MAX_INPUT ? shared.substring(0, MAX_INPUT) : shared);
            setStatus("分享内容已填入，点击开始解析。", false);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptIntent(intent);
    }

    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) { setStatus("剪贴板里没有可用的文本。", true); return; }
        CharSequence value = clip.getItemAt(0).getText();
        if (value == null || value.toString().trim().isEmpty()) { setStatus("请先复制分享文字或链接。", true); return; }
        reset();
        String shared = value.toString();
        input.setText(shared.length() > MAX_INPUT ? shared.substring(0, MAX_INPUT) : shared);
        input.setSelection(input.length());
        setStatus("已粘贴，点击开始解析。", false);
    }

    private void reset() {
        generation++;
        coverGeneration++;
        cancellation.set(true);
        if (parseTask != null) parseTask.cancel(true);
        if (coverTask != null) coverTask.cancel(true);
        currentItem = null;
        items.clear();
        batchRows.removeAllViews();
        showAction("");
        if (cover != null) cover.setImageDrawable(null);
        if (detailDialog != null) detailDialog.dismiss();
        batchCard.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        setBusy(false);
    }

    private void beginParse() {
        String value = input.getText().toString().trim();
        reset();
        try {
            List<String> links = VideoParser.extractLinks(value, MAX_BATCH);
            if (links.isEmpty()) throw new IllegalArgumentException("请粘贴抖音或快手分享链接。");
            for (String link : links) items.add(new BatchItem(link));
            batchCard.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            hideKeyboard();
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) indices.add(i);
            runItems(indices);
        } catch (Exception error) {
            setStatus(error.getMessage() == null ? "未找到可解析的分享链接。" : error.getMessage(), true);
            input.requestFocus();
        }
    }

    private void runItems(List<Integer> indices) {
        if (indices.isEmpty() || busy) return;
        generation++;
        int token = generation;
        cancellation = new AtomicBoolean(false);
        List<String> links = new ArrayList<>();
        for (int index : indices) {
            BatchItem item = items.get(index);
            item.state = WAITING;
            item.error = "";
            item.info = null;
            links.add(item.input);
        }
        items.get(indices.get(0)).state = RUNNING;
        setBusy(true);
        refreshBatch();
        setStatus("正在逐条解析，可随时停止。", false);
        WeakReference<MainActivity> reference = new WeakReference<>(this);
        Handler callback = handler;
        AtomicBoolean stop = cancellation;
        // Both index and input snapshots are independent of the Activity's mutable rows.
        List<Integer> targets = new ArrayList<>(indices);
        parseTask = executor.submit(() -> parseBatch(reference, callback, links, targets, stop, token));
    }

    private static void parseBatch(WeakReference<MainActivity> reference, Handler callback,
            List<String> links, List<Integer> targets, AtomicBoolean stop, int token) {
        String fatalError = null;
        try {
            BatchParser.run(links, value -> {
                VideoInfo info = new VideoParser().parse(value);
                if (info == null || !NetworkPolicy.isMediaUrl(info.videoUrl)) throw new IllegalArgumentException("没有有效的视频地址。");
                return info;
            }, stop, (index, value, info, error) -> {
                if (stop.get() || Thread.currentThread().isInterrupted()) return;
                callback.post(() -> {
                    MainActivity activity = reference.get();
                    if (activity == null || activity.destroyed || activity.generation != token) return;
                    activity.receiveItem(targets, index, info, error);
                });
            });
        } catch (Exception error) {
            fatalError = error.getMessage() == null ? "批量解析中断，请重试未完成项。" : error.getMessage();
        }
        final String failure = fatalError;
        callback.post(() -> {
            MainActivity activity = reference.get();
            if (activity == null || activity.destroyed || activity.generation != token) return;
            activity.finishBatch(targets, failure, stop.get());
        });
    }

    private void receiveItem(List<Integer> targets, int index, VideoInfo info, String error) {
        if (index < 0 || index >= targets.size()) return;
        BatchItem item = items.get(targets.get(index));
        item.info = info;
        item.error = error == null || error.trim().isEmpty() ? "未能读取该作品的视频地址。" : error;
        item.state = info == null ? FAILED : SUCCESS;
        if (index + 1 < targets.size()) items.get(targets.get(index + 1)).state = RUNNING;
        refreshBatch();
        if (items.size() == 1 && info != null) showResult(item, false);
    }

    private void finishBatch(List<Integer> targets, String failure, boolean stopped) {
        for (int index : targets) {
            BatchItem item = items.get(index);
            if (item.state == WAITING || item.state == RUNNING) {
                item.state = stopped ? STOPPED : FAILED;
                item.error = failure == null ? "未完成，请重试。" : failure;
            }
        }
        setBusy(false);
        refreshBatch();
        setStatus(stopped ? "已停止，已完成的结果仍可使用。" : "", false);
    }

    private void cancelBatch() {
        if (!busy) return;
        cancellation.set(true);
        if (parseTask != null) parseTask.cancel(true);
        generation++;
        for (BatchItem item : items) if (item.state == WAITING || item.state == RUNNING) item.state = STOPPED;
        setBusy(false);
        refreshBatch();
        setStatus("已停止，已完成的结果仍可使用；可重试未完成项。", false);
    }

    private void retryIncomplete() {
        if (busy) return;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (items.get(i).state == FAILED || items.get(i).state == STOPPED) indices.add(i);
        runItems(indices);
    }

    private void refreshBatch() {
        int success = 0, failed = 0, stopped = 0, downloadable = 0;
        for (BatchItem item : items) {
            if (item.state == SUCCESS) { success++; if (!isQueued(item)) downloadable++; }
            if (item.state == FAILED) failed++;
            if (item.state == STOPPED) stopped++;
        }
        batchSummary.setText(items.size() == 1
                ? (success == 1 ? "1 个视频" : busy ? "处理中" : failed > 0 ? "未完成" : "已停止")
                : (success + failed) + " / " + items.size() + " 已完成");
        batchStats.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
        successCount.setText(String.valueOf(success));
        failedCount.setText(String.valueOf(failed));
        pendingCount.setText(String.valueOf(items.size() - success - failed));
        progress.setMax(Math.max(1, items.size()));
        progress.setProgress(success + failed);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        batchActions.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(failed + stopped > 0 ? View.VISIBLE : View.GONE);
        retryButton.setEnabled(!busy && failed + stopped > 0);
        copyAllButton.setEnabled(success > 0);
        downloadAllButton.setEnabled(downloadable > 0);
        if (detailDownload != null && currentItem != null) {
            detailDownload.setText(isQueued(currentItem) ? "已加入下载队列" : "下载视频");
            detailDownload.setEnabled(!isQueued(currentItem));
        }
        batchRows.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            BatchItem item = items.get(i);
            final int index = i;
            LinearLayout row = card();
            LinearLayout rowHead = horizontal();
            TextView number = chip(String.format(java.util.Locale.ROOT, "%02d", i + 1), MUTED, PAPER);
            if (items.size() > 1) rowHead.addView(number);
            String platform = item.info != null ? item.info.platform : (item.input.contains("douyin") ? "抖音" : "快手");
            TextView platformLabel = text(platform, 12, MUTED);
            LinearLayout.LayoutParams platformParams = new LinearLayout.LayoutParams(0, -2, 1);
            platformParams.setMarginStart(items.size() > 1 ? dp(10) : 0);
            rowHead.addView(platformLabel, platformParams);
            String state = item.state == SUCCESS ? (isQueued(item) ? "已入队" : "解析成功") : item.state == FAILED ? "解析失败" : item.state == RUNNING ? "正在解析" : item.state == STOPPED ? "已停止" : "等待处理";
            int color = item.state == FAILED ? ERROR : item.state == SUCCESS ? ACCENT : MUTED;
            rowHead.addView(chip(state, color, item.state == FAILED ? Color.rgb(251, 235, 229) : item.state == SUCCESS ? MINT : PAPER));
            row.addView(rowHead);
            TextView title = text(item.info == null ? displayLink(item.input) : item.info.title, 16, INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            add(row, title, 12);
            if (item.state == FAILED) {
                TextView reason = text(limit(item.error, 130), 12, ERROR);
                reason.setMaxLines(3);
                reason.setEllipsize(android.text.TextUtils.TruncateAt.END);
                add(row, reason, 7);
                LinearLayout failedActions = horizontal();
                Button why = quietButton("查看原因"), again = button("重试此项", false);
                why.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("解析未完成").setMessage(item.error).setPositiveButton("知道了", null).show());
                again.setCompoundDrawables(icon(R.drawable.ic_retry, ACCENT, 16), null, null, null);
                again.setEnabled(!busy);
                again.setOnClickListener(v -> retryItem(index));
                addPair(failedActions, why, again);
                add(row, failedActions, 10);
            } else if (item.state == SUCCESS) {
                LinearLayout rowActions = horizontal();
                Button detail = quietButton("查看详情");
                detail.setCompoundDrawables(icon(R.drawable.ic_play, ACCENT, 16), null, null, null);
                detail.setOnClickListener(v -> showResult(item, true));
                Button download = button(isQueued(item) ? "已入队" : "下载视频", true);
                download.setCompoundDrawables(icon(isQueued(item) ? R.drawable.ic_check : R.drawable.ic_download, Color.WHITE, 16), null, null, null);
                download.setEnabled(!isQueued(item));
                download.setOnClickListener(v -> downloadItem(item, false));
                addPair(rowActions, detail, download);
                add(row, rowActions, 12);
            } else if (item.state == STOPPED) {
                add(row, text("已保留此前的结果，可重试继续处理。", 12, MUTED), 7);
            }
            add(batchRows, row, 12);
        }
    }

    private void retryItem(int index) {
        if (busy || index < 0 || index >= items.size()) return;
        List<Integer> indices = new ArrayList<>();
        indices.add(index);
        runItems(indices);
    }

    private static String displayLink(String value) {
        try {
            Uri uri = Uri.parse(value);
            return limit(uri.getHost() + (uri.getPath() == null ? "" : uri.getPath()), 140);
        } catch (Exception ignored) { return "分享链接"; }
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private void showResult(BatchItem item, boolean explicit) {
        if (item.info == null || destroyed || isFinishing()) return;
        if (detailDialog != null) detailDialog.dismiss();
        currentItem = item;
        coverGeneration++;
        if (coverTask != null) coverTask.cancel(true);
        int token = coverGeneration;
        VideoInfo info = item.info;
        Dialog dialog = new Dialog(this);
        detailDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(20), dp(10), dp(20), dp(20));
        // A translucent backing keeps the underlying activity's text from showing through.
        sheet.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{
                shape(Color.argb(245, 245, 251, 252), 24), GlassSurface.material(this, 24)}));
        sheet.setClipToOutline(true);
        View handle = new View(this);
        handle.setBackground(shape(LINE, 4));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, handleParams);
        LinearLayout heading = horizontal();
        resultPlatform = text("视频详情 · " + info.platform, 13, ACCENT);
        heading.addView(resultPlatform, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = quietButton("关闭");
        close.setCompoundDrawables(icon(R.drawable.ic_close, MUTED, 15), null, null, null);
        close.setOnClickListener(v -> dialog.dismiss());
        heading.addView(close);
        add(sheet, heading, 8);
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.setFillViewport(false);
        LinearLayout detailBody = vertical();
        detailScroll.addView(detailBody);
        sheet.addView(detailScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        resultTitle = text(info.title, 21, INK);
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resultTitle.setTextIsSelectable(true);
        add(detailBody, resultTitle, 7);
        resultSource = text(info.sourceNote, 12, MUTED);
        add(detailBody, resultSource, 10);
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.setContentDescription("视频封面");
        cover.setClipToOutline(true);
        cover.setBackground(shape(PAPER, 16));
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(-1, dp(220));
        coverParams.topMargin = dp(16);
        detailBody.addView(cover, coverParams);
        coverStatus = text("暂无封面", 12, MUTED);
        add(detailBody, coverStatus, 6);
        TextView detailNotice = text("链接可能有时效，请及时保存；画面水印以预览为准。", 12, MUTED);
        add(detailBody, detailNotice, 14);
        detailAction = text("", 12, ACCENT);
        detailAction.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        detailAction.setVisibility(View.GONE);
        add(detailBody, detailAction, 8);
        LinearLayout secondary = horizontal();
        Button play = button("播放", false), copy = button("复制链接", false);
        play.setCompoundDrawables(icon(R.drawable.ic_play, ACCENT, 16), null, null, null);
        copy.setCompoundDrawables(icon(R.drawable.ic_copy, ACCENT, 16), null, null, null);
        play.setOnClickListener(v -> play(info));
        copy.setOnClickListener(v -> copy(info));
        addPair(secondary, play, copy);
        add(sheet, secondary, 16);
        detailDownload = button(isQueued(item) ? "已加入下载队列" : "下载视频", true);
        detailDownload.setCompoundDrawables(icon(R.drawable.ic_download, Color.WHITE, 18), null, null, null);
        detailDownload.setEnabled(!isQueued(item));
        detailDownload.setOnClickListener(v -> downloadItem(item, false));
        add(sheet, detailDownload, 8);
        dialog.setContentView(sheet);
        dialog.setOnDismissListener(ignored -> {
            if (detailDialog == dialog) {
                coverGeneration++;
                if (coverTask != null) coverTask.cancel(true);
                detailDialog = null;
                currentItem = null;
                detailDownload = null;
                detailAction = null;
            }
        });
        Window window = dialog.getWindow();
        if (window != null) {
            // Floating details stay inside system bars; only the main window is edge-to-edge.
            if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(true);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.3f;
            params.gravity = Gravity.BOTTOM;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) {
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.82f);
            window.setLayout(-1, height);
        }
        if (info.coverUrl != null && NetworkPolicy.isMediaUrl(info.coverUrl)) {
            coverStatus.setText("正在加载封面…");
            WeakReference<MainActivity> reference = new WeakReference<>(this);
            Handler callback = handler;
            String address = info.coverUrl, referer = info.referer;
            coverTask = executor.submit(() -> loadCover(reference, callback, address, referer, token));
        } else cover.setVisibility(View.GONE);
    }

    private static void loadCover(WeakReference<MainActivity> reference, Handler callback,
                                  String coverUrl, String referer, int token) {
        Bitmap bitmap = null;
        try {
            String address = coverUrl;
            byte[] bytes = null;
            for (int redirects = 0; redirects <= 4; redirects++) {
                if (!NetworkPolicy.isMediaUrl(address)) break;
                HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", VideoParser.USER_AGENT);
                if (referer != null && !referer.isEmpty()) connection.setRequestProperty("Referer", referer);
                try {
                    int code = connection.getResponseCode();
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String location = connection.getHeaderField("Location");
                        if (location == null) break;
                        address = new URL(new URL(address), location).toString();
                        continue;
                    }
                    String type = connection.getContentType();
                    if (code != 200 || type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) break;
                    if (connection.getContentLengthLong() > 5 * 1024 * 1024) break;
                    try (InputStream stream = connection.getInputStream();
                         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = stream.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) return;
                            if (output.size() + count > 5 * 1024 * 1024) throw new java.io.IOException("image too large");
                            output.write(buffer, 0, count);
                        }
                        bytes = output.toByteArray();
                    }
                    break;
                } finally { connection.disconnect(); }
            }
            if (bytes != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (options.outWidth > 0 && options.outHeight > 0
                        && options.outWidth <= 20000 && options.outHeight <= 20000) {
                    options.inSampleSize = 1;
                    while (options.outWidth / options.inSampleSize > 1200
                            || options.outHeight / options.inSampleSize > 1200) options.inSampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                }
            }
        } catch (Exception ignored) { /* Cover failures must not discard a playable result. */ }
        if (Thread.currentThread().isInterrupted()) { if (bitmap != null) bitmap.recycle(); return; }
        final Bitmap decoded = bitmap;
        callback.post(() -> {
            MainActivity activity = reference.get();
            if (activity == null || activity.destroyed || activity.coverGeneration != token) {
                if (decoded != null) decoded.recycle();
                return;
            }
            if (decoded == null) activity.coverStatus.setText("封面暂时无法加载，仍可播放或下载视频。");
            else {
                activity.cover.setImageBitmap(decoded);
                activity.cover.setVisibility(View.VISIBLE);
                activity.coverStatus.setText("视频封面");
            }
        });
    }

    private void showAction(String message) {
        actionStatus.setText(message);
        actionStatus.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
        if (detailAction != null) {
            detailAction.setText(message);
            detailAction.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private boolean isQueued(BatchItem item) {
        return item.info != null && (queuedInputs.contains(item.input) || queuedUrls.contains(item.info.videoUrl));
    }

    private boolean downloadItem(BatchItem item, boolean quiet) {
        if (item.info == null) return false;
        if (isQueued(item)) {
            if (!quiet) showAction("此视频已加入下载队列，可在系统下载界面查看。");
            return false;
        }
        try {
            DownloadHelper.enqueue(this, item.info);
            queuedInputs.add(item.input);
            queuedUrls.add(item.info.videoUrl);
            if (!quiet) { showAction("已加入系统下载队列，进度与完成状态以系统下载界面为准。"); refreshBatch(); }
            return true;
        } catch (Exception error) {
            if (!quiet) showAction("无法创建下载任务，请检查系统下载管理器是否可用。");
            return false;
        }
    }

    private void downloadAll() {
        int queued = 0, failed = 0;
        for (BatchItem item : items) if (item.state == SUCCESS && !isQueued(item)) {
            if (downloadItem(item, true)) queued++; else failed++;
        }
        refreshBatch();
        showAction("已提交 " + queued + " 个下载任务" + (failed > 0 ? "，" + failed + " 项提交失败，可重试" : "，可在系统下载界面查看进度") + "。");
    }

    private void copyAll() {
        StringBuilder links = new StringBuilder();
        for (BatchItem item : items) if (item.state == SUCCESS && item.info != null) {
            if (links.length() > 0) links.append('\n');
            links.append(item.info.videoUrl);
        }
        if (links.length() > 0) copyText(links.toString(), "成功视频的播放链接已复制，每行一条。");
    }

    private void copy(VideoInfo info) {
        if (info != null) copyText(info.videoUrl, "播放链接已复制。");
    }

    private void copyText(String value, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("视频链接", value));
        showAction(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void play(VideoInfo info) {
        if (info == null || !NetworkPolicy.isMediaUrl(info.videoUrl)) return;
        try { startActivity(playbackIntent(info, true)); }
        catch (ActivityNotFoundException error) {
            try { startActivity(playbackIntent(info, false)); }
            catch (ActivityNotFoundException | SecurityException unavailable) { showAction("无法打开视频，可复制链接后使用浏览器打开。"); }
        } catch (SecurityException error) { showAction("系统不允许打开此链接，请尝试复制链接。"); }
    }

    private Intent playbackIntent(VideoInfo info, boolean withVideoType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(info.videoUrl);
        if (withVideoType) intent.setDataAndType(uri, "video/*"); else intent.setData(uri);
        Bundle headers = new Bundle();
        headers.putString("User-Agent", VideoParser.USER_AGENT);
        headers.putString("Referer", info.referer);
        intent.putExtra(Browser.EXTRA_HEADERS, headers);
        intent.putExtra("headers", new String[]{"User-Agent", VideoParser.USER_AGENT, "Referer", info.referer});
        return intent;
    }

    private void setBusy(boolean value) {
        busy = value;
        parseButton.setEnabled(true);
        pasteButton.setEnabled(!value);
        input.setEnabled(!value);
        parseButton.setText(value ? "停止解析" : "开始解析");
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        clearButton.setEnabled(!value && input.length() > 0);
        if (value) {
            bottomHint.setText("处理中，可停止");
            parseButton.setCompoundDrawables(null, null, icon(R.drawable.ic_close, Color.WHITE, 17), null);
        } else {
            parseButton.setCompoundDrawables(null, null, icon(R.drawable.ic_arrow, Color.WHITE, 17), null);
            updateInputHint();
        }
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(error ? ERROR : ACCENT);
        status.setBackgroundColor(Color.TRANSPARENT);
        status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout card() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(GlassSurface.material(this, 20));
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1);
        return view;
    }

    private TextView chip(String value, int color, int background) {
        TextView view = text(value, 11, color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackground(shape(background, 9));
        return view;
    }

    private TextView stat(LinearLayout row, String label, int color, int background) {
        LinearLayout cell = horizontal();
        cell.setPadding(0, dp(6), dp(8), dp(6));
        TextView value = text("0", 14, color);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cell.addView(value);
        TextView caption = text(label, 12, MUTED);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
        captionParams.setMarginStart(dp(5));
        cell.addView(caption, captionParams);
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    private Button quietButton(String label) {
        Button button = button(label, false);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14166875), shape(Color.TRANSPARENT, 12), shape(Color.WHITE, 12)));
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        return button;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{Color.rgb(131, 146, 149), primary ? Color.WHITE : ACCENT}));
        button.setBackgroundTintList(null);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, shape(Color.rgb(225, 233, 234), 14));
        states.addState(new int[]{}, shape(primary ? ACCENT : MINT, 14));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33FFFFFF : 0x18166875), states, shape(Color.WHITE, 14)));
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setMinimumHeight(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setCompoundDrawablePadding(dp(6));
        button.setStateListAnimator(null);
        return button;
    }

    private Drawable icon(int id, int color, int size) {
        Drawable drawable = getDrawable(id).mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, dp(size), dp(size));
        return drawable;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private GradientDrawable outline(int color, int radius, int stroke) {
        GradientDrawable shape = shape(color, radius);
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    private void addPair(LinearLayout row, Button left, Button right) {
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 1);
        rightParams.setMarginStart(dp(8));
        row.addView(right, rightParams);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("input", input.getText().toString());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        generation++;
        coverGeneration++;
        cancellation.set(true);
        if (parseTask != null) parseTask.cancel(true);
        if (coverTask != null) coverTask.cancel(true);
        executor.shutdownNow();
        if (detailDialog != null) detailDialog.dismiss();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
