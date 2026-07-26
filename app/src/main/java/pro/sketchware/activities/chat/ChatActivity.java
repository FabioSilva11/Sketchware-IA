package pro.sketchware.activities.chat;

import android.content.Intent;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import android.util.DisplayMetrics;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.utility.TranslationFunction;
import pro.sketchware.activities.chat.port.VoidPortChatThreadService;
import pro.sketchware.activities.chat.port.VoidPortModelCapabilities;
import pro.sketchware.activities.chat.port.VoidPortProviderMaxTokens;
import pro.sketchware.activities.chat.port.VoidPortRefreshModelService;
import pro.sketchware.activities.chat.port.VoidPortScmService;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.activities.settings.IaSettingsActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
public class ChatActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_REFERENCE_IMAGE = 9102;
    private static final int REQUEST_CAPTURE_REFERENCE_IMAGE = 9103;
    private static final int REQUEST_PICK_USER_AVATAR = 9104;
    private static final int REQUEST_PICK_REFERENCE_FILE = 9105;
    private static final int MAX_PENDING_REFERENCES = 8;
    private static final long STREAM_UI_UPDATE_INTERVAL_MS = 180L;
    private static final String PREF_USER_NAME = "user_name";
    private static final String PREF_LEGACY_USER_NAME = "user_display_name";
    private static final String PREF_AVATAR_TYPE = "avatar_type";
    private static final String PREF_AVATAR_VALUE = "avatar_value";
    private static final String PREF_MANAGED_REFERENCE_URI_GRANTS = "managed_reference_uri_grants";

    private String sc_id;
    private ViewPager chatViewPager;
    private EditText editTextMessage;
    private View btnSend;
    private View btnAttach;
    private View btnChatMode;
    private View btnModelSelector;
    private ImageView btnCancelRun;
    private ImageView btnMicrophone;
    private TextView textChatMode;
    private View layoutRunStatus;
    private KelivoTypingDotsView runStatusDots;
    private TextView textRunStatus;
    private TextView textCurrentModel;
    private TextView textFilesChanged;
    private TextView textSelectedContext;
    private HorizontalScrollView imagePreviewScroll;
    private LinearLayout imagePreviewList;
    private TabLayout chatPageTabs;
    private ChatMessageAdapter messageAdapter;
    private List<ChatMessage> messages;
    private final List<ChatReference> pendingReferences = new ArrayList<>();
    private ExecutorService executorService;
    private long lastMessageTime = 0; // Timestamp do Ãºltimo envio de mensagem
    private static final long MIN_MESSAGE_INTERVAL_MS = 2000; // Intervalo mÃ­nimo de 2 segundos entre mensagens
    private boolean isProcessing = false; // Flag para indicar se estÃ¡ processando uma mensagem
    private ChatHistoryManager historyManager;
    private String activeThreadId;
    private String projectDisplayName = "";
    private boolean showDebug = false; // Flag para controlar exibiÃ§Ã£o de mensagens de debug
    private boolean suppressMentionWatcher = false;
    private AgentManager agentManager;
    private ChatMessage currentDebugMessage;
    private ChatMessagesFragment chatMessagesFragment;
    private ChatDiffFragment chatDiffFragment;
    private ChatArtifactsFragment chatArtifactsFragment;
    private ChatPlanFragment chatPlanFragment;
    private ChatCompileLogFragment chatCompileLogFragment;
    private ChatCompileRunner chatCompileRunner;
    private String currentRunStatus = "";
    private final Handler streamUiHandler = new Handler(Looper.getMainLooper());
    private ChatMessage pendingStreamingUpdate;
    private boolean streamUpdateScheduled = false;
    private int streamUiRefreshCount = 0;
    private int historySaveCount = 0;
    private long historySaveTotalMs = 0L;
    private boolean debugHistoryDirty = false;
    private DrawerLayout drawerLayout;
    private TextView textChatTitle;
    private TextView textChatSubtitle;
    private TextView textTokenMeter;
    private TextView textTokenMeterPercent;
    private ProgressBar progressTokenMeter;
    private RecyclerView drawerThreadsList;
    private EditText drawerSearch;
    private ChatDrawerAdapter drawerAdapter;
    private ImageView imageChatModelIcon;
    private FrameLayout drawerUserAvatarContainer;
    private ImageView drawerUserAvatarImage;
    private TextView drawerUserAvatar;
    private TextView drawerUserName;
    private TextToSpeech textToSpeech;
    private List<ChatThread> drawerThreads = new ArrayList<>();
    private Uri pendingCameraImageUri;
    private File pendingCameraImageFile;

    @Override
    public Resources getResources() {
        return TranslationFunction.wrapResources(this, super.getResources());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null || sc_id.isEmpty()) {
            Toast.makeText(this, R.string.chat_project_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        executorService = Executors.newSingleThreadExecutor();
        historyManager = new ChatHistoryManager(this);
        activeThreadId = historyManager.ensureDefaultThread(sc_id);

        // Carregar preferÃªncia de debug
        SharedPreferences prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        showDebug = prefs.getBoolean("show_debug", false);

        setupViews();
        loadProjectInfo();

        // Inicializar AgentManager (Void-style logic)
        agentManager = new AgentManager(this, sc_id, messages, new AgentManager.AgentListener() {
            @Override
            public void onMessageAdded(ChatMessage message) {
                runOnUiThread(() -> {
                    messageAdapter.notifyDataSetChanged();
                    scrollToBottom();
                    if (isProcessing && message != null && message.isBot()) {
                        message.setStreaming(true);
                        return;
                    }
                    if (historyManager != null && sc_id != null) {
                        historyManager.saveMessage(sc_id, activeThreadId, message);
                    }
                    updateThreadSummary();
                    if (!isProcessing) {
                        updateChangedFilesSummary();
                    }
                });
            }

            @Override
            public void onMessageUpdated(ChatMessage message) {
                runOnUiThread(() -> {
                    if (isProcessing && message != null && !message.isTool()) {
                        message.setStreaming(true);
                        scheduleStreamingMessageUpdate(message);
                        return;
                    }
                    notifyMessageChanged(message);
                    persistChatState(false);
                });
            }

            @Override
            public void onMessageRemoved(ChatMessage message, int index) {
                runOnUiThread(() -> {
                    messageAdapter.notifyDataSetChanged();
                    saveChatHistory();
                    updateThreadSummary();
                });
            }

            @Override
            public void onStatusChanged(String status) {
                runOnUiThread(() -> updateRunStatus(status));
            }

            @Override
            public void onDebug(String message) {
                runOnUiThread(() -> appendDebugMessage(message));
            }

            @Override
            public void onProcessingFinished() {
                runOnUiThread(() -> {
                    clearStreamingFlags();
                    flushStreamingMessageUpdate();
                    appendPerfSummaryIfNeeded();
                    flushDebugHistoryIfNeeded();
                    isProcessing = false;
                    currentDebugMessage = null;
                    showProgress(false);
                    setInputEnabled(true);
                    updateRunStatus("");
                    persistChatState(true);
                    refreshSecondaryPanels();
                    resetPerfCounters();
                });
            }

            @Override
            public void onToolExecuted(String toolName, boolean isMutation) {
                runOnUiThread(() -> {
                    if (isMutation) {
                        persistChatState(true);
                        refreshSecondaryPanels();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    currentDebugMessage = null;
                    Toast.makeText(ChatActivity.this, error, Toast.LENGTH_LONG).show();
                    ChatMessage errorMsg = new ChatMessage("Error: " + error, false, System.currentTimeMillis());
                    messages.add(errorMsg);
                    messageAdapter.notifyDataSetChanged();
                    scrollToBottom();
                    saveChatHistory();
                    updateThreadSummary();
                    refreshSecondaryPanels();
                });
            }
        });

        // Carregar histÃ³rico do chat
        loadChatHistory();
        reconcileManagedReferenceGrants();
    }



    public void approveTool() {
        if (agentManager != null) {
            agentManager.approveTool();
        }
    }

    public void rejectTool() {
        if (agentManager != null) {
            agentManager.rejectTool();
        }
    }

    private void setupViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        chatViewPager = findViewById(R.id.chat_view_pager);
        chatPageTabs = findViewById(R.id.chat_page_tabs);
        editTextMessage = findViewById(R.id.edit_text_message);
        btnSend = findViewById(R.id.btn_send);
        btnAttach = findViewById(R.id.btn_attach);
        btnCancelRun = findViewById(R.id.btn_cancel_run);
        btnMicrophone = findViewById(R.id.btn_microphone);
        textFilesChanged = findViewById(R.id.text_files_changed);
        textSelectedContext = findViewById(R.id.text_selected_context);
        textTokenMeter = findViewById(R.id.text_token_meter);
        textTokenMeterPercent = findViewById(R.id.text_token_meter_percent);
        progressTokenMeter = findViewById(R.id.progress_token_meter);
        imagePreviewScroll = findViewById(R.id.selected_image_preview_scroll);
        imagePreviewList = findViewById(R.id.selected_image_preview_list);
        editTextMessage.setHint(R.string.kelivo_input_hint);

        messages = new ArrayList<>();
        messageAdapter = new ChatMessageAdapter(this, messages);
        messageAdapter.setMessageActionListener(new ChatMessageAdapter.MessageActionListener() {
            @Override
            public void onRegenerate(int position) {
                regenerateFromPosition(position);
            }

            @Override
            public void onEdit(int position) {
                editMessageAtPosition(position);
            }

            @Override
            public void onSpeak(String text) {
                speakMessage(text);
            }

            @Override
            public void onTranslate(String text) {
                openTranslate(text);
            }

            @Override
            public void onDelete(int position) {
                deleteMessageAtPosition(position);
            }

            @Override
            public void onReasoningVisibilityChanged(ChatMessage message) {
                saveChatHistory();
            }
        });
        setupKelivoUi();
        chatMessagesFragment = new ChatMessagesFragment();
        chatDiffFragment = ChatDiffFragment.newInstance(sc_id);
        chatArtifactsFragment = ChatArtifactsFragment.newInstance(sc_id);
        chatPlanFragment = ChatPlanFragment.newInstance(sc_id);
        chatCompileLogFragment = ChatCompileLogFragment.newInstance(sc_id);
        chatMessagesFragment.setAdapter(messageAdapter);
        chatArtifactsFragment.setMessages(messages);
        chatPlanFragment.setMessages(messages);
        chatViewPager.setAdapter(new ChatPagerAdapter(this, chatMessagesFragment, chatDiffFragment,
                chatArtifactsFragment, chatPlanFragment, chatCompileLogFragment));
        chatViewPager.setOffscreenPageLimit(5);
        if (chatPageTabs != null) {
            chatPageTabs.setupWithViewPager(chatViewPager);
        }

        // Configurar ícone de enviar e listener
        btnSend.setOnClickListener(v -> {
            String message = editTextMessage.getText().toString().trim();
            if (!message.isEmpty() || !pendingReferences.isEmpty()) {
                sendMessage(message);
                editTextMessage.setText("");
            }
        });

        if (btnCancelRun != null) {
            btnCancelRun.setOnClickListener(v -> cancelCurrentRun());
        }

        if (btnMicrophone != null) {
            btnMicrophone.setOnClickListener(v -> startVoiceInput());
        }

        if (textFilesChanged != null) {
            textFilesChanged.setOnClickListener(v -> showRecentChangesDialog());
        }
        if (btnAttach != null) {
            btnAttach.setOnClickListener(v -> showKelivoToolsSheet());
        }
        if (textSelectedContext != null) {
            textSelectedContext.setOnClickListener(v -> clearPendingReferences());
        }
        editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppressMentionWatcher || isProcessing || editable == null) {
                    return;
                }
                int cursor = editTextMessage.getSelectionStart();
                if (cursor > 0 && cursor <= editable.length() && editable.charAt(cursor - 1) == '@') {
                    showReferencePicker(true);
                }
            }
        });

        // Configurar Speech-to-Text
        // ConfiguraÃ§Ã£o do Seletor de Modelo
        btnChatMode = findViewById(R.id.btn_chat_mode);
        btnModelSelector = findViewById(R.id.btn_model_selector);
        textChatMode = findViewById(R.id.text_chat_mode);
        textCurrentModel = findViewById(R.id.text_current_model);

        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
        updateChatModeUI();
        updateModelUI();
        updateTokenMeter();
        updateRunStatus("");
        updateChangedFilesSummary();
        updateThreadSummary();
        updatePendingReferencesUi();

        if (btnChatMode != null) {
            btnChatMode.setOnClickListener(v -> showChatModeMenu(prefs));
        }
        if (textChatMode != null) {
            // The mode label is now a visible pill; tapping it opens the selector too.
            textChatMode.setOnClickListener(v -> showChatModeMenu(prefs));
        }

        layoutRunStatus = findViewById(R.id.layout_run_status);
        runStatusDots = findViewById(R.id.run_status_dots);
        textRunStatus = findViewById(R.id.text_run_status);

        btnModelSelector.setOnClickListener(v -> showModelSelectorMenu(prefs));

        View btnInputTools = findViewById(R.id.btn_input_tools);
        if (btnInputTools != null) {
            btnInputTools.setOnClickListener(v -> showModelCatalogDialog());
        }
    }

    private void setupKelivoUi() {
        drawerLayout = findViewById(R.id.drawer_layout);
        textChatTitle = findViewById(R.id.text_chat_title);
        textChatSubtitle = findViewById(R.id.text_chat_subtitle);
        imageChatModelIcon = findViewById(R.id.image_chat_model_icon);
        drawerThreadsList = findViewById(R.id.drawer_threads_list);
        drawerSearch = findViewById(R.id.drawer_search);

        if (drawerLayout != null) {
            View drawer = findViewById(R.id.drawer_content);
            if (drawer != null) {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int drawerWidth = (int) (metrics.widthPixels * 0.78f);
                DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams) drawer.getLayoutParams();
                params.width = drawerWidth;
                drawer.setLayoutParams(params);
            }
        }

        View menuButton = findViewById(R.id.btn_drawer_menu);
        if (menuButton != null && drawerLayout != null) {
            menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        View newChatButton = findViewById(R.id.btn_header_new_chat);
        if (newChatButton != null) {
            newChatButton.setOnClickListener(v -> createNewThread());
        }

        View mapButton = findViewById(R.id.btn_header_map);
        if (mapButton != null) {
            mapButton.setOnClickListener(v -> startChatCompile());
        }

        drawerUserName = findViewById(R.id.drawer_user_name);
        drawerUserAvatar = findViewById(R.id.drawer_user_avatar);
        drawerUserAvatarImage = findViewById(R.id.drawer_user_avatar_image);
        drawerUserAvatarContainer = findViewById(R.id.drawer_user_avatar_container);
        updateDrawerUserUi();
        if (drawerUserAvatarContainer != null) {
            drawerUserAvatarContainer.setOnClickListener(v -> showUserAvatarSheet());
        }
        if (drawerUserAvatar != null) {
            drawerUserAvatar.setOnClickListener(v -> showUserAvatarSheet());
        }
        if (drawerUserName != null) {
            drawerUserName.setOnClickListener(v -> showRenameUserDialog());
        }

        View drawerSettings = findViewById(R.id.btn_drawer_settings);
        if (drawerSettings != null) {
            drawerSettings.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                startActivity(new Intent(this, IaSettingsActivity.class));
            });
        }

        View drawerHistory = findViewById(R.id.btn_drawer_history);
        if (drawerHistory != null) {
            drawerHistory.setOnClickListener(v -> showThreadListDialog());
        }

        if (drawerThreadsList != null) {
            drawerThreadsList.setLayoutManager(new LinearLayoutManager(this));
            drawerAdapter = new ChatDrawerAdapter();
            drawerAdapter.setListener(new ChatDrawerAdapter.OnThreadClickListener() {
                @Override
                public void onThreadClick(ChatThread thread) {
                    if (drawerLayout != null) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                    switchThread(thread.id);
                }

                @Override
                public void onThreadLongClick(ChatThread thread) {
                    showThreadActionsSheet(thread);
                }
            });
            drawerThreadsList.setAdapter(drawerAdapter);
        }

        if (drawerSearch != null) {
            drawerSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterDrawerThreads(s == null ? "" : s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        refreshDrawerThreads();
        updateKelivoHeader();
    }

    private void startChatCompile() {
        if (chatCompileRunner != null && chatCompileRunner.isRunning()) {
            showCompileLogPage();
            Toast.makeText(this, R.string.chat_compile_already_running, Toast.LENGTH_SHORT).show();
            return;
        }
        showCompileLogPage();
        if (chatCompileLogFragment != null) {
            chatCompileLogFragment.startNewLog();
        }
        chatCompileRunner = new ChatCompileRunner(this, sc_id, new ChatCompileRunner.Listener() {
            @Override
            public void onCompileProgress(String line, int step) {
                runOnUiThread(() -> {
                    showCompileLogPage();
                    if (chatCompileLogFragment != null) {
                        chatCompileLogFragment.appendLine(line);
                    }
                    updateRunStatus(line);
                });
            }

            @Override
            public void onCompileFinished(boolean success, String message, String apkPath) {
                runOnUiThread(() -> {
                    if (chatCompileLogFragment != null) {
                        chatCompileLogFragment.appendLine("");
                        chatCompileLogFragment.appendLine(message);
                    }
                    updateRunStatus("");
                    Toast.makeText(ChatActivity.this,
                            success ? R.string.chat_compile_success : R.string.chat_compile_failed,
                            Toast.LENGTH_LONG).show();
                    if (success) {
                        openCompiledApkInstaller(apkPath);
                    }
                });
            }
        });
        chatCompileRunner.start();
    }

    private void openCompiledApkInstaller(String apkPath) {
        if (!ChatMessage.hasVisibleText(apkPath)) {
            Toast.makeText(this, R.string.chat_compile_apk_missing, Toast.LENGTH_LONG).show();
            return;
        }
        File apkFile = new File(apkPath);
        if (!apkFile.exists()) {
            Toast.makeText(this, R.string.chat_compile_apk_missing, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(
                getApplicationContext(),
                getApplicationContext().getPackageName() + ".provider",
                apkFile);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    private void showCompileLogPage() {
        if (chatViewPager != null) {
            chatViewPager.setCurrentItem(4, true);
        }
    }

    private String getDrawerUserName() {
        SharedPreferences prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        String name = prefs.getString(PREF_USER_NAME, "");
        if (!ChatMessage.hasVisibleText(name)) {
            name = prefs.getString(PREF_LEGACY_USER_NAME, getString(R.string.kelivo_default_user));
        }
        return name;
    }

    private void updateDrawerUserUi() {
        String userName = getDrawerUserName();
        if (drawerUserName != null) {
            drawerUserName.setText(userName);
        }
        if (drawerUserAvatarImage != null) {
            drawerUserAvatarImage.setVisibility(View.VISIBLE);
            drawerUserAvatarImage.setImageResource(R.drawable.kelivo_lucide_bot_message_square);
        }
        if (drawerUserAvatar != null) {
            drawerUserAvatar.setVisibility(View.GONE);
        }
    }

    private void showUserAvatarSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = createSheetRoot();
        TextView title = createSheetTitle(R.string.kelivo_profile_avatar_title);
        root.addView(title);
        root.addView(createSheetAction(R.drawable.kelivo_lucide_image, R.string.kelivo_profile_choose_image, v -> {
            dialog.dismiss();
            pickUserAvatarImage();
        }));
        root.addView(createSheetAction(R.drawable.ic_kelivo_emoji, R.string.kelivo_profile_choose_emoji, v -> {
            dialog.dismiss();
            showEmojiAvatarDialog();
        }));
        root.addView(createSheetAction(R.drawable.kelivo_lucide_x, R.string.kelivo_profile_remove_avatar, v -> {
            getSharedPreferences("chat_settings", MODE_PRIVATE)
                    .edit()
                    .remove(PREF_AVATAR_TYPE)
                    .remove(PREF_AVATAR_VALUE)
                    .apply();
            updateDrawerUserUi();
            if (messageAdapter != null) {
                messageAdapter.notifyDataSetChanged();
            }
            dialog.dismiss();
        }));
        dialog.setContentView(root);
        expandSheet(dialog, 0.42f);
        dialog.show();
    }

    private void showEmojiAvatarDialog() {
        String[] emojis = new String[]{
                "\uD83D\uDE00", "\uD83D\uDE0E", "\uD83E\uDD16", "\uD83D\uDE80", "\u2728",
                "\uD83D\uDD25", "\uD83D\uDCBB", "\uD83C\uDFA8", "\uD83E\uDDE0", "\u2B50"
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.kelivo_profile_choose_emoji)
                .setItems(emojis, (dialog, which) -> {
                    if (which < 0 || which >= emojis.length) {
                        return;
                    }
                    getSharedPreferences("chat_settings", MODE_PRIVATE)
                            .edit()
                            .putString(PREF_AVATAR_TYPE, "emoji")
                            .putString(PREF_AVATAR_VALUE, emojis[which])
                            .apply();
                    updateDrawerUserUi();
                    if (messageAdapter != null) {
                        messageAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickUserAvatarImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_USER_AVATAR);
        } catch (Exception firstFailure) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(fallback, getString(R.string.kelivo_profile_choose_image)),
                    REQUEST_PICK_USER_AVATAR);
        }
    }

    private void showRenameUserDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.kelivo_profile_name_hint);
        input.setText(getDrawerUserName());
        input.setSelectAllOnFocus(true);
        int padding = dp(18);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(padding, dp(6), padding, 0);
        frame.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.kelivo_profile_edit_name)
                .setView(frame)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (!ChatMessage.hasVisibleText(name)) {
                return;
            }
            getSharedPreferences("chat_settings", MODE_PRIVATE)
                    .edit()
                    .putString(PREF_USER_NAME, name)
                    .putString(PREF_LEGACY_USER_NAME, name)
                    .apply();
            updateDrawerUserUi();
            if (messageAdapter != null) {
                messageAdapter.notifyDataSetChanged();
            }
            Toast.makeText(this, R.string.kelivo_profile_name_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void refreshDrawerThreads() {
        if (historyManager == null || sc_id == null || drawerAdapter == null) {
            return;
        }
        drawerThreads = new ArrayList<>(historyManager.getThreads(sc_id));
        filterDrawerThreads(drawerSearch == null || drawerSearch.getText() == null
                ? ""
                : drawerSearch.getText().toString());
    }

    private void filterDrawerThreads(String query) {
        if (drawerAdapter == null) {
            return;
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (needle.isEmpty()) {
            drawerAdapter.submit(drawerThreads, activeThreadId);
            return;
        }
        List<ChatThread> filtered = new ArrayList<>();
        for (ChatThread thread : drawerThreads) {
            String title = ChatMessage.hasVisibleText(thread.title)
                    ? thread.title
                    : getString(R.string.chat_thread_new_title);
            String summary = ChatMessage.hasVisibleText(thread.summary) ? thread.summary : "";
            String haystack = (title + " " + summary).toLowerCase(Locale.getDefault());
            if (haystack.contains(needle)) {
                filtered.add(thread);
            }
        }
        drawerAdapter.submit(filtered, activeThreadId);
    }

    private void updateKelivoHeader() {
        if (textChatTitle != null) {
            textChatTitle.setText(buildThreadTitle());
        }
        if (textChatSubtitle != null) {
            SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
            String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
            String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
            if (ChatMessage.hasVisibleText(model)
                    && AiChatSettingsHelper.isCurrentSelectionValid(prefs, provider, model)) {
                textChatSubtitle.setText(provider + "/" + model);
                bindModelIcon(imageChatModelIcon, provider, model, false);
            } else {
                textChatSubtitle.setText(R.string.chat_no_models_available_short);
                bindModelIcon(imageChatModelIcon, "", "", false);
            }
        }
    }

    private void updateModelUI() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        String currentModel = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
        String currentProvider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        if (textCurrentModel != null) {
            if (ChatMessage.hasVisibleText(currentModel)
                    && AiChatSettingsHelper.isCurrentSelectionValid(prefs, currentProvider, currentModel)) {
                textCurrentModel.setText(currentModel);
                bindModelIcon(btnModelSelector instanceof ImageView ? (ImageView) btnModelSelector : null,
                        currentProvider, currentModel, true);
            } else {
                textCurrentModel.setText(R.string.chat_no_models_available_short);
                bindModelIcon(btnModelSelector instanceof ImageView ? (ImageView) btnModelSelector : null,
                        "", "", true);
            }
        }
        updateKelivoHeader();
        updateTokenMeter();
    }

    private void updateTokenMeter() {
        if (textTokenMeter == null || textTokenMeterPercent == null || progressTokenMeter == null) {
            return;
        }
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
        int limit = VoidPortProviderMaxTokens.resolve(prefs, provider, model,
                VoidPortProviderMaxTokens.DEFAULT_MAX_TOKENS);
        int used = estimateConversationTokens();
        int percent = limit <= 0 ? 0 : Math.min(100, Math.round((used * 100f) / limit));
        textTokenMeter.setText(getString(R.string.chat_token_meter_value,
                formatTokenCount(used),
                formatTokenCount(limit)));
        textTokenMeterPercent.setText(getString(R.string.chat_token_meter_percent, percent));
        progressTokenMeter.setProgress(percent);
    }

    private int estimateConversationTokens() {
        int chars = 0;
        if (messages != null) {
            for (ChatMessage message : messages) {
                if (message == null) {
                    continue;
                }
                chars += safeLength(message.getLlmContent());
                chars += safeLength(message.getDisplayContent());
                chars += safeLength(message.getToolArgs());
                chars += safeLength(message.getToolResult());
            }
        }
        return Math.max(0, Math.round(chars / 4f));
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String formatTokenCount(int tokens) {
        if (tokens >= 1_000_000) {
            return String.format(Locale.US, "%.2fM", tokens / 1_000_000f);
        }
        if (tokens >= 1_000) {
            return String.format(Locale.US, "%.1fk", tokens / 1_000f);
        }
        return String.valueOf(tokens);
    }

    private void bindModelIcon(ImageView imageView, String provider, String model, boolean useFallback) {
        if (imageView == null) {
            return;
        }
        int iconRes = KelivoModelIconResolver.resolve(provider, model);
        if (iconRes == 0) {
            if (!useFallback) {
                imageView.setVisibility(View.GONE);
                return;
            }
            iconRes = R.drawable.kelivo_icon_codex;
        }
        imageView.setImageResource(iconRes);
        imageView.setVisibility(View.VISIBLE);
    }

    private void updateChatModeUI() {
        if (textChatMode == null) {
            return;
        }
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        String chatMode = AiChatSettingsHelper.getChatMode(prefs);
        if ("normal".equals(chatMode)) {
            textChatMode.setText(R.string.chat_mode_chat);
        } else if ("gather".equals(chatMode)) {
            textChatMode.setText(R.string.chat_mode_gather);
        } else {
            textChatMode.setText(R.string.chat_mode_agent);
        }
    }

    private void showModelSelectorMenu(SharedPreferences prefs) {
        KelivoModelBottomSheet.show(this, (providerId, modelId) -> {
            updateModelUI();
            updateThreadSummary();
            Toast.makeText(this, getString(R.string.chat_model_changed, providerId + "/" + modelId), Toast.LENGTH_SHORT).show();
        });
    }

    private void showKelivoToolsSheet() {
        KelivoToolsBottomSheet.show(this, new KelivoToolsBottomSheet.Callback() {
            @Override
            public void onCamera() {
                pickReferenceImageFromCamera();
            }

            @Override
            public void onPhotos() {
                pickReferenceImage();
            }

            @Override
            public void onUpload() {
                pickReferenceFile();
            }

            @Override
            public void onInstructionInjection() {
                Toast.makeText(ChatActivity.this, R.string.kelivo_instruction_soon, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onContextManagement() {
                clearPendingReferences();
                Toast.makeText(ChatActivity.this, R.string.chat_thread_empty_summary, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pickReferenceImageFromCamera() {
        try {
            File imageFile = createCameraImageFile();
            Uri imageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    imageFile
            );
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            intent.setClipData(ClipData.newUri(getContentResolver(), "camera", imageUri));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pendingCameraImageFile = imageFile;
            pendingCameraImageUri = imageUri;
            startActivityForResult(intent, REQUEST_CAPTURE_REFERENCE_IMAGE);
        } catch (Exception e) {
            clearPendingCameraImage(false);
            Toast.makeText(this, R.string.chat_camera_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private File createCameraImageFile() throws Exception {
        File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            picturesDir = getCacheDir();
        }
        File directory = new File(picturesDir, "chat_camera");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create camera directory");
        }
        return File.createTempFile("chat_reference_", ".jpg", directory);
    }

    private void showChatModeMenu(SharedPreferences prefs) {
        PopupMenu popup = new PopupMenu(this, btnChatMode);
        popup.getMenu().add(0, 1, 0, getString(R.string.chat_mode_chat));
        popup.getMenu().add(0, 2, 1, getString(R.string.chat_mode_gather));
        popup.getMenu().add(0, 3, 2, getString(R.string.chat_mode_agent));

        // Show a radio mark on the currently selected mode.
        popup.getMenu().setGroupCheckable(0, true, true);
        String currentMode = AiChatSettingsHelper.getChatMode(prefs);
        int checkedId = "normal".equals(currentMode) ? 1 : "gather".equals(currentMode) ? 2 : 3;
        android.view.MenuItem checkedItem = popup.getMenu().findItem(checkedId);
        if (checkedItem != null) {
            checkedItem.setChecked(true);
        }

        popup.setOnMenuItemClickListener(item -> {
            String mode = "agent";
            int descriptionRes = R.string.chat_mode_detail_agent;
            if (item.getItemId() == 1) {
                mode = "normal";
                descriptionRes = R.string.chat_mode_detail_chat;
            } else if (item.getItemId() == 2) {
                mode = "gather";
                descriptionRes = R.string.chat_mode_detail_gather;
            }
            AiChatSettingsHelper.setChatMode(prefs, mode);
            updateChatModeUI();
            Toast.makeText(this, getString(descriptionRes), Toast.LENGTH_SHORT).show();
            return true;
        });
        popup.show();
    }

    private void loadProjectInfo() {
        HashMap<String, Object> projectInfo = lC.b(sc_id);
        if (projectInfo != null) {
            String projectName = yB.c(projectInfo, "my_ws_name");
            projectDisplayName = projectName;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.chat_title_with_project, projectName));
            }
        } else {
            projectDisplayName = getString(R.string.chat_default_project_name);
        }
    }

    private void loadChatHistory() {
        // Carregar histÃ³rico salvo
        List<ChatMessage> savedMessages = historyManager.loadHistory(sc_id, activeThreadId);

        if (savedMessages != null && !savedMessages.isEmpty()) {
            // Se jÃ¡ tem histÃ³rico, usar ele
            messages.addAll(savedMessages);
            messageAdapter.notifyDataSetChanged();
            scrollToBottom();
        } else {
            // Se nÃ£o tem histÃ³rico, adicionar mensagem de boas-vindas
            addWelcomeMessage();
        }
        updateThreadSummary();
        updateChangedFilesSummary();
        refreshSecondaryPanels();
    }

    private void addWelcomeMessage() {
        HashMap<String, Object> projectInfo = lC.b(sc_id);
        String projectName = projectInfo != null ? yB.c(projectInfo, "my_ws_name") : getString(R.string.chat_default_project_name);
        String welcomeMessage = getString(R.string.chat_welcome_message, projectName);
        ChatMessage welcomeMsg = new ChatMessage(welcomeMessage, false, System.currentTimeMillis());
        messages.add(welcomeMsg);
        messageAdapter.notifyDataSetChanged();
        scrollToBottom();

        if (historyManager != null && sc_id != null) {
            historyManager.saveMessage(sc_id, activeThreadId, welcomeMsg);
        }
    }

    private void saveChatHistory() {
        if (historyManager != null && sc_id != null) {
            long startedAt = System.currentTimeMillis();
            historyManager.saveHistoryAsync(sc_id, activeThreadId, messages);
            historySaveCount++;
            historySaveTotalMs += System.currentTimeMillis() - startedAt;
        }
        updateTokenMeter();
    }

    private void notifyMessageChanged(ChatMessage message) {
        int index = messages.indexOf(message);
        if (index != -1) {
            messageAdapter.notifyDataSetChanged();
        }
    }

    private void scheduleStreamingMessageUpdate(ChatMessage message) {
        pendingStreamingUpdate = message;
        if (streamUpdateScheduled) {
            return;
        }
        streamUpdateScheduled = true;
        streamUiHandler.postDelayed(this::flushStreamingMessageUpdate, STREAM_UI_UPDATE_INTERVAL_MS);
    }

    private void flushStreamingMessageUpdate() {
        streamUpdateScheduled = false;
        ChatMessage message = pendingStreamingUpdate;
        pendingStreamingUpdate = null;
        if (message != null) {
            streamUiRefreshCount++;
            notifyMessageChanged(message);
        }
    }

    private void clearStreamingFlags() {
        for (ChatMessage message : messages) {
            if (message != null && message.isStreaming()) {
                message.setStreaming(false);
                pendingStreamingUpdate = message;
            }
        }
    }

    private void persistChatState(boolean includeChangedFiles) {
        saveChatHistory();
        updateThreadSummary();
        if (includeChangedFiles) {
            updateChangedFilesSummary();
        }
        updateTokenMeter();
    }

    private void regenerateFromPosition(int position) {
        if (isProcessing || position < 0 || position >= messages.size()) {
            return;
        }
        ChatMessage target = messages.get(position);
        int userIndex = -1;
        if (target.isUser()) {
            userIndex = position;
        } else {
            for (int i = position - 1; i >= 0; i--) {
                if (messages.get(i).isUser()) {
                    userIndex = i;
                    break;
                }
            }
        }
        if (userIndex < 0) {
            return;
        }
        ChatMessage userMessage = messages.get(userIndex);
        String resend = ChatMessage.hasVisibleText(userMessage.getMessage())
                ? userMessage.getMessage()
                : userMessage.getDisplayContent();
        if (!ChatMessage.hasVisibleText(resend) && !userMessage.hasStagingSelections()) {
            return;
        }
        removeMessagesAfterPosition(userIndex);
        startContinuationFromEditedMessage(userMessage);
    }

    private void editMessageAtPosition(int position) {
        if (isProcessing || position < 0 || position >= messages.size()) {
            return;
        }
        ChatMessage message = messages.get(position);
        if (!message.isUser() && !message.isBot()) {
            return;
        }
        String text = ChatMessage.hasVisibleText(message.getMessage())
                ? message.getMessage()
                : message.getDisplayContent();
        if (!ChatMessage.hasVisibleText(text) && !message.hasStagingSelections()) {
            return;
        }
        showMessageEditSheet(position, text);
    }

    private void showMessageEditSheet(int position, String text) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = createSheetRoot();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView saveAndSend = new TextView(this);
        saveAndSend.setText(R.string.kelivo_message_edit_save_send);
        saveAndSend.setTextColor(getColor(R.color.chat_accent));
        saveAndSend.setTextSize(14f);
        saveAndSend.setGravity(Gravity.CENTER_VERTICAL);
        saveAndSend.setPadding(0, 0, dp(8), 0);
        header.addView(saveAndSend, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView title = new TextView(this);
        title.setText(R.string.kelivo_message_edit_title);
        title.setTextColor(getColor(R.color.chat_text_primary));
        title.setTextSize(16f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView save = new TextView(this);
        save.setText(R.string.common_word_save);
        save.setTextColor(getColor(R.color.chat_accent));
        save.setTextSize(14f);
        save.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        save.setPadding(dp(8), 0, 0, 0);
        header.addView(save, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(header);

        EditText editor = new EditText(this);
        editor.setText(text);
        editor.setSelectAllOnFocus(false);
        editor.setMinLines(8);
        editor.setMaxLines(14);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setTextColor(getColor(R.color.chat_text_primary));
        editor.setTextSize(15f);
        editor.setBackgroundResource(R.drawable.bg_outline_edittext);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(editor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        save.setOnClickListener(v -> {
            applyEditedMessage(position, editor.getText() == null ? "" : editor.getText().toString(), false);
            dialog.dismiss();
        });
        saveAndSend.setOnClickListener(v -> {
            applyEditedMessage(position, editor.getText() == null ? "" : editor.getText().toString(), true);
            dialog.dismiss();
        });
        dialog.setContentView(root);
        expandSheet(dialog, 0.72f);
        dialog.show();
        editor.requestFocus();
    }

    private void applyEditedMessage(int position, String editedText, boolean shouldSend) {
        if (position < 0 || position >= messages.size()) {
            return;
        }
        String cleanText = editedText == null ? "" : editedText.trim();
        ChatMessage message = messages.get(position);
        if ((!ChatMessage.hasVisibleText(cleanText) && !message.hasStagingSelections())
                || (!message.isUser() && !message.isBot())) {
            return;
        }
        message.setDisplayContent(cleanText);
        message.setStreaming(false);
        message.setBeingEdited(false);
        if (message.isUser()) {
            message.setLlmContent(ChatReferenceManager.buildLlmUserContent(cleanText, message.getContextPayload()));
        }
        messageAdapter.notifyDataSetChanged();
        saveChatHistory();
        updateThreadSummary();
        refreshSecondaryPanels();
        if (shouldSend) {
            removeMessagesAfterPosition(position);
            startContinuationFromEditedMessage(message);
        }
    }

    private void restorePendingReferences(ChatMessage message) {
        clearPendingReferences();
        if (message != null && message.hasStagingSelections()) {
            pendingReferences.addAll(message.getStagingSelections());
        }
        updatePendingReferencesUi();
    }

    private void removeMessagesFromPosition(int position) {
        if (position < 0 || position >= messages.size()) {
            return;
        }
        List<ChatReference> removedReferences = collectMessageReferences(
                new ArrayList<>(messages.subList(position, messages.size())));
        int removeCount = messages.size() - position;
        for (int i = messages.size() - 1; i >= position; i--) {
            messages.remove(i);
        }
        messageAdapter.notifyDataSetChanged();
        saveChatHistory();
        updateThreadSummary();
        refreshSecondaryPanels();
        releaseReferenceGrantsIfUnused(removedReferences);
    }

    private void removeMessagesAfterPosition(int position) {
        if (position < 0 || position >= messages.size() - 1) {
            saveChatHistory();
            updateThreadSummary();
            refreshSecondaryPanels();
            return;
        }
        int start = position + 1;
        List<ChatReference> removedReferences = collectMessageReferences(
                new ArrayList<>(messages.subList(start, messages.size())));
        int removeCount = messages.size() - start;
        for (int i = messages.size() - 1; i >= start; i--) {
            messages.remove(i);
        }
        messageAdapter.notifyDataSetChanged();
        saveChatHistory();
        updateThreadSummary();
        refreshSecondaryPanels();
        releaseReferenceGrantsIfUnused(removedReferences);
    }

    private void startContinuationFromEditedMessage(ChatMessage sourceMessage) {
        if (agentManager == null || isProcessing) {
            return;
        }
        clearPendingReferences();
        lastMessageTime = System.currentTimeMillis();
        isProcessing = true;
        setInputEnabled(false);
        showProgress(true);
        currentDebugMessage = null;
        resetPerfCounters();
        agentManager.continueFromExistingMessage(sourceMessage);
    }

    private void speakMessage(String text) {
        if (!ChatMessage.hasVisibleText(text)) {
            return;
        }
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                    textToSpeech.setLanguage(Locale.getDefault());
                    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kelivo_chat");
                }
            });
            return;
        }
        textToSpeech.setLanguage(Locale.getDefault());
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kelivo_chat");
    }

    private void openTranslate(String text) {
        if (!ChatMessage.hasVisibleText(text)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://translate.google.com/?sl=auto&tl=pt&text=" + Uri.encode(text)));
            startActivity(intent);
        } catch (Exception e) {
            copyToClipboard(text);
            Toast.makeText(this, R.string.kelivo_translate_fallback, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("chat", text));
            Toast.makeText(this, R.string.kelivo_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteMessageAtPosition(int position) {
        if (isProcessing || position < 0 || position >= messages.size()) {
            return;
        }
        ChatMessage removed = messages.remove(position);
        messageAdapter.notifyDataSetChanged();
        saveChatHistory();
        updateThreadSummary();
        releaseReferenceGrantsIfUnused(
                removed == null ? null : removed.getStagingSelections());
    }

    private void sendMessage(String message) {
        sendMessage(message, false);
    }

    private void sendMessage(String message, boolean skipRateLimit) {
        // Verificar se jÃ¡ estÃ¡ processando uma mensagem
        if (isProcessing) {
            Toast.makeText(this, R.string.chat_wait_processing, Toast.LENGTH_SHORT).show();
            return;
        }

        // Verificar rate limiting - intervalo mÃ­nimo entre mensagens
        long currentTime = System.currentTimeMillis();
        long timeSinceLastMessage = currentTime - lastMessageTime;

        if (!skipRateLimit && timeSinceLastMessage < MIN_MESSAGE_INTERVAL_MS && lastMessageTime > 0) {
            long remainingSeconds = (MIN_MESSAGE_INTERVAL_MS - timeSinceLastMessage) / 1000 + 1;
            Toast.makeText(this, getString(R.string.chat_wait_before_sending, remainingSeconds), Toast.LENGTH_SHORT).show();
            return;
        }

        // Atualizar timestamp do Ãºltimo envio
        lastMessageTime = currentTime;
        isProcessing = true;

        // Desabilitar input enquanto processa
        setInputEnabled(false);
        showProgress(true);
        currentDebugMessage = null;
        resetPerfCounters();

        // Delegar para AgentManager (streaming e agente, paridade Void)
        List<ChatReference> stagingSelections = new ArrayList<>(pendingReferences);
        List<ChatReference> imageReferences = ChatReferenceManager.getImageReferences(stagingSelections);
        String outgoingMessage = message == null ? "" : message.trim();
        if (outgoingMessage.isEmpty()) {
            outgoingMessage = imageReferences.isEmpty()
                    ? getString(R.string.chat_references_only_prompt)
                    : getString(R.string.chat_images_only_prompt);
        }
        if (showDebug) {
            appendDebugMessage("UI: referências adiadas para montagem em segundo plano"
                    + ", refs=" + stagingSelections.size()
                    + ", images=" + imageReferences.size());
        }
        // ContextBuilder runs in AgentManager's background context thread. Passing
        // null here avoids opening content:// streams and walking referenced
        // folders on Android's main thread.
        agentManager.processUserMessage(outgoingMessage, null, stagingSelections);
        clearPendingReferences();
    }

    private void setInputEnabled(boolean enabled) {
        editTextMessage.setEnabled(enabled);
        if (btnSend != null) btnSend.setEnabled(enabled);
        if (btnSend != null) btnSend.setAlpha(enabled ? 1f : 0.55f);
        if (btnAttach != null) btnAttach.setEnabled(enabled);
        if (btnAttach != null) btnAttach.setAlpha(enabled ? 1f : 0.55f);
    }

    private void showReferencePicker(boolean replaceAtTrigger) {
        List<ChatReferenceManager.ReferenceOption> allOptions = ChatReferenceManager.getProjectReferenceOptions(sc_id);
        if (allOptions.isEmpty()) {
            Toast.makeText(this, R.string.chat_reference_none, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, dp(8), padding, 0);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(R.string.chat_reference_search_hint);
        container.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        RecyclerView recyclerView = createDialogRecyclerView(dp(360));
        ChatSimpleTextAdapter adapter = new ChatSimpleTextAdapter();
        List<ChatReferenceManager.ReferenceOption> visibleOptions = new ArrayList<>();
        recyclerView.setAdapter(adapter);
        container.addView(recyclerView);

        Runnable refresh = () -> {
            String query = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase();
            visibleOptions.clear();
            List<String> labels = new ArrayList<>();
            for (ChatReferenceManager.ReferenceOption option : allOptions) {
                if (query.isEmpty() || option.filterText.contains(query)) {
                    visibleOptions.add(option);
                    labels.add(option.displayText);
                }
            }
            adapter.setItems(labels);
        };
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.chat_reference_picker_title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        adapter.setOnItemClickListener((position, item) -> {
            if (position < 0 || position >= visibleOptions.size()) {
                return;
            }
            ChatReference reference = visibleOptions.get(position).reference;
            if (addPendingReference(reference)) {
                insertMention(reference, replaceAtTrigger);
            }
            dialog.dismiss();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        });

        dialog.show();
    }

    private void pickReferenceImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_REFERENCE_IMAGE);
        } catch (Exception unavailable) {
            Toast.makeText(this, R.string.chat_reference_picker_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void pickReferenceFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_REFERENCE_FILE);
        } catch (Exception unavailable) {
            Toast.makeText(this, R.string.chat_reference_picker_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean addPendingReference(ChatReference reference) {
        if (reference == null) {
            return false;
        }
        for (ChatReference pending : pendingReferences) {
            if (pending != null && pending.stableKey().equals(reference.stableKey())) {
                updatePendingReferencesUi();
                return true;
            }
        }
        if (pendingReferences.size() >= MAX_PENDING_REFERENCES) {
            Toast.makeText(this, R.string.chat_reference_limit, Toast.LENGTH_SHORT).show();
            return false;
        }
        pendingReferences.add(reference);
        updatePendingReferencesUi();
        return true;
    }

    private void insertMention(ChatReference reference, boolean replaceAtTrigger) {
        if (reference == null || editTextMessage == null) {
            return;
        }
        Editable editable = editTextMessage.getText();
        int cursor = Math.max(0, editTextMessage.getSelectionStart());
        cursor = Math.min(cursor, editable.length());
        int start = replaceAtTrigger ? findAtTrigger(editable, cursor) : cursor;
        String insertion = reference.mentionText() + " ";
        suppressMentionWatcher = true;
        try {
            editable.replace(start, cursor, insertion);
            editTextMessage.setSelection(Math.min(start + insertion.length(), editable.length()));
        } finally {
            suppressMentionWatcher = false;
        }
    }

    private int findAtTrigger(Editable editable, int cursor) {
        if (editable != null && cursor > 0 && cursor <= editable.length() && editable.charAt(cursor - 1) == '@') {
            return cursor - 1;
        }
        return cursor;
    }

    private void clearPendingReferences() {
        List<ChatReference> removedReferences = new ArrayList<>(pendingReferences);
        pendingReferences.clear();
        updatePendingReferencesUi();
        releaseReferenceGrantsIfUnused(removedReferences);
    }

    private void removePendingReference(ChatReference reference) {
        if (reference == null) {
            return;
        }
        List<ChatReference> removedReferences = new ArrayList<>();
        String stableKey = reference.stableKey();
        for (int i = pendingReferences.size() - 1; i >= 0; i--) {
            ChatReference pending = pendingReferences.get(i);
            if (pending != null && pending.stableKey().equals(stableKey)) {
                removedReferences.add(pending);
                pendingReferences.remove(i);
            }
        }
        updatePendingReferencesUi();
        releaseReferenceGrantsIfUnused(removedReferences);
    }

    private void updatePendingReferencesUi() {
        updateImagePreviewUi();
        if (textSelectedContext == null) {
            return;
        }
        if (pendingReferences.isEmpty()) {
            textSelectedContext.setVisibility(View.GONE);
            textSelectedContext.setText("");
            return;
        }
        textSelectedContext.setVisibility(View.VISIBLE);
        textSelectedContext.setText(getString(
                R.string.chat_reference_context_label,
                ChatReferenceManager.summarizeReferences(pendingReferences)
        ));
    }

    private void updateImagePreviewUi() {
        if (imagePreviewScroll == null || imagePreviewList == null) {
            return;
        }
        imagePreviewList.removeAllViews();
        List<ChatReference> imageReferences = ChatReferenceManager.getImageReferences(pendingReferences);
        if (imageReferences.isEmpty()) {
            imagePreviewScroll.setVisibility(View.GONE);
            return;
        }

        imagePreviewScroll.setVisibility(View.VISIBLE);
        for (ChatReference reference : imageReferences) {
            imagePreviewList.addView(createImagePreviewItem(reference));
        }
    }

    private View createImagePreviewItem(ChatReference reference) {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        frameParams.setMarginEnd(dp(8));
        frame.setLayoutParams(frameParams);
        frame.setPadding(dp(2), dp(2), dp(2), dp(2));
        frame.setBackgroundResource(R.drawable.bg_round_outline);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (reference != null && reference.getUri() != null) {
            try {
                image.setImageURI(reference.getUri());
            } catch (Exception ignored) {
                image.setImageResource(R.drawable.kelivo_lucide_image);
            }
        } else {
            image.setImageResource(R.drawable.kelivo_lucide_image);
        }
        frame.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        TextView remove = new TextView(this);
        remove.setText("X");
        remove.setTextColor(0xFFFFFFFF);
        remove.setTextSize(10);
        remove.setGravity(Gravity.CENTER);
        remove.setContentDescription(getString(R.string.chat_remove_reference_image));
        remove.setBackgroundResource(R.drawable.bg_error_box);
        remove.setOnClickListener(v -> removePendingReference(reference));
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        frame.addView(remove, removeParams);
        return frame;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout createSheetRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_kelivo_bottom_sheet);
        root.setPadding(dp(18), dp(10), dp(18), dp(18));
        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.bg_kelivo_sheet_handle);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(14));
        root.addView(handle, handleParams);
        return root;
    }

    private TextView createSheetTitle(int titleRes) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(getColor(R.color.chat_text_primary));
        title.setTextSize(18f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(10));
        return title;
    }

    private TextView createSheetAction(int iconRes, int textRes, View.OnClickListener listener) {
        TextView item = new TextView(this);
        item.setText(textRes);
        item.setTextColor(getColor(R.color.chat_text_primary));
        item.setTextSize(15f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinHeight(dp(50));
        item.setPadding(dp(4), 0, dp(4), 0);
        item.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        item.setCompoundDrawablePadding(dp(14));
        item.setBackgroundResource(android.R.drawable.list_selector_background);
        item.setOnClickListener(listener);
        return item;
    }

    private void expandSheet(BottomSheetDialog dialog, float heightFraction) {
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) {
                return;
            }
            int targetHeight = (int) (getResources().getDisplayMetrics().heightPixels * heightFraction);
            sheet.getLayoutParams().height = targetHeight;
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            behavior.setPeekHeight(targetHeight);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
    }

    private RecyclerView createDialogRecyclerView(int heightPx) {
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightPx
        ));
        return recyclerView;
    }

    private void showProgress(boolean show) {
        if (btnCancelRun != null) {
            btnCancelRun.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnSend != null) {
            btnSend.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale algo...");
        try {
            startActivityForResult(intent, 1001);
        } catch (Exception e) {
            Toast.makeText(this, "Reconhecimento de voz não suportado", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRunStatus(String status) {
        String safeStatus = status == null ? "" : status.trim();
        currentRunStatus = safeStatus;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(safeStatus.isEmpty() ? null : safeStatus);
        }
        // Animated status banner above the input: pulsing dots while the agent
        // is thinking / running tools, hidden when idle.
        if (layoutRunStatus != null && textRunStatus != null && runStatusDots != null) {
            if (safeStatus.isEmpty()) {
                runStatusDots.stopAnimation();
                layoutRunStatus.setVisibility(View.GONE);
            } else {
                textRunStatus.setText(safeStatus);
                layoutRunStatus.setVisibility(View.VISIBLE);
                runStatusDots.startAnimation();
            }
        }
        refreshSecondaryPanels();
    }

    public void updateChangedFilesSummary() {
        if (textFilesChanged == null) {
            return;
        }
        int count = VoidPortScmService.changedFileCount(sc_id);
        textFilesChanged.setText(VoidPortChatThreadService.changedFilesLabel(count));
        textFilesChanged.setAlpha(count > 0 ? 1f : 0.7f);
        textFilesChanged.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (chatDiffFragment != null) {
            chatDiffFragment.refreshDiffs();
        }
        refreshSecondaryPanels();
    }

    private void updateThreadSummary() {
        if (historyManager == null || sc_id == null || activeThreadId == null) {
            return;
        }
        String title = buildThreadTitle();
        String summary = buildThreadSummary();
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
        String activeModel = ChatMessage.hasVisibleText(model) ? provider + "/" + model : "";
        historyManager.updateThreadSummary(sc_id, activeThreadId, title, summary, activeModel);
        updateKelivoHeader();
        refreshDrawerThreads();
        refreshSecondaryPanels();
    }

    private String buildThreadTitle() {
        if (messages == null || messages.isEmpty()) {
            return activeThreadId != null && activeThreadId.endsWith(":default")
                    ? getString(R.string.chat_thread_default_title)
                    : getString(R.string.chat_thread_new_title);
        }
        for (ChatMessage message : messages) {
            if (message != null && message.isUser() && ChatMessage.hasVisibleText(message.getMessage())) {
                return compact(message.getMessage(), 36);
            }
        }
        return activeThreadId != null && activeThreadId.endsWith(":default")
                ? getString(R.string.chat_thread_default_title)
                : getString(R.string.chat_thread_new_title);
    }

    private String buildThreadSummary() {
        if (messages == null || messages.isEmpty()) {
            return getString(R.string.chat_thread_empty_summary);
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message == null || message.isTool()) {
                continue;
            }
            String text = message.hasMessageContent() ? message.getMessage() : message.getStatus();
            if (ChatMessage.hasVisibleText(text)) {
                return compact(text, 96);
            }
        }
        return getString(R.string.chat_thread_empty_summary);
    }

    private String compact(String value, int maxChars) {
        String text = value == null ? "" : value.trim().replace('\n', ' ');
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private void refreshSecondaryPanels() {
        if (chatArtifactsFragment != null) {
            chatArtifactsFragment.setMessages(messages);
            chatArtifactsFragment.refreshArtifacts();
        }
        if (chatPlanFragment != null) {
            chatPlanFragment.setMessages(messages);
            chatPlanFragment.setRunState(isProcessing, currentRunStatus);
        }
    }

    private void showRecentChangesDialog() {
        if (chatDiffFragment != null) {
            chatDiffFragment.refreshDiffs();
        }
        if (chatViewPager != null) {
            chatViewPager.setCurrentItem(1, true);
        }
    }

    private void scrollToBottom() {
        if (messages.size() > 0 && chatMessagesFragment != null) {
            chatMessagesFragment.scrollToBottom();
        }
    }

    private void appendDebugMessage(String debugLine) {
        if (!showDebug || !ChatMessage.hasVisibleText(debugLine)) {
            return;
        }

        String formattedLine = "- [" + new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date()) + "] " + debugLine.trim();

        if (currentDebugMessage == null || !messages.contains(currentDebugMessage)) {
            currentDebugMessage = new ChatMessage(
                    formattedLine,
                    ChatMessage.TYPE_CHECKPOINT,
                    System.currentTimeMillis(),
                    "Debug"
            );
            messages.add(currentDebugMessage);
            messageAdapter.notifyDataSetChanged();
        } else {
            String previousText = currentDebugMessage.getMessage();
            if (ChatMessage.hasVisibleText(previousText)) {
                currentDebugMessage.setMessage(previousText + "\n" + formattedLine);
            } else {
                currentDebugMessage.setMessage(formattedLine);
            }
            currentDebugMessage.setTimestamp(System.currentTimeMillis());
            int index = messages.indexOf(currentDebugMessage);
            if (index != -1) {
                messageAdapter.notifyDataSetChanged();
            }
        }

        scrollToBottom();
        debugHistoryDirty = true;
        updateThreadSummary();
    }

    private void flushDebugHistoryIfNeeded() {
        if (!debugHistoryDirty) {
            return;
        }
        debugHistoryDirty = false;
        saveChatHistory();
    }

    private void removeDebugMessagesFromChat() {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message == null || !message.isCheckpoint()) {
                continue;
            }
            String status = message.getStatus();
            if (status != null && "Debug".equalsIgnoreCase(status.trim())) {
                messages.remove(i);
                messageAdapter.notifyDataSetChanged();
            }
        }
        saveChatHistory();
        updateThreadSummary();
    }

    private void appendPerfSummaryIfNeeded() {
        if (!showDebug) {
            return;
        }
        appendDebugMessage("UI: refreshes streaming=" + streamUiRefreshCount
                + ", intervalMs=" + STREAM_UI_UPDATE_INTERVAL_MS
                + ", historySaves=" + historySaveCount
                + ", historySaveTotalMs=" + historySaveTotalMs);
    }

    private void resetPerfCounters() {
        streamUiRefreshCount = 0;
        historySaveCount = 0;
        historySaveTotalMs = 0L;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        // Atualizar estado do checkbox de debug
        MenuItem debugItem = menu.findItem(R.id.menu_toggle_debug);
        if (debugItem != null) {
            debugItem.setChecked(showDebug);
            debugItem.setTitle(showDebug ? R.string.chat_menu_hide_debug : R.string.chat_menu_show_debug);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        } else if (item.getItemId() == R.id.menu_thread_list) {
            showThreadListDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_new_thread) {
            createNewThread();
            return true;
        } else if (item.getItemId() == R.id.menu_model_catalog) {
            showModelCatalogDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_clear_chat) {
            clearChat();
            return true;
        } else if (item.getItemId() == R.id.menu_rollback_checkpoint) {
            rollbackLastCheckpoint();
            return true;
        } else if (item.getItemId() == R.id.menu_toggle_debug) {
            showDebug = !showDebug;
            item.setChecked(showDebug);
            item.setTitle(showDebug ? R.string.chat_menu_hide_debug : R.string.chat_menu_show_debug);
            if (!showDebug) {
                currentDebugMessage = null;
                removeDebugMessagesFromChat();
            } else {
                appendDebugMessage("Debug ativado — métricas de todas as interações serão exibidas aqui.");
            }
            SharedPreferences prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
            prefs.edit().putBoolean("show_debug", showDebug).apply();
            Toast.makeText(this, showDebug ? R.string.chat_debug_enabled : R.string.chat_debug_disabled, Toast.LENGTH_SHORT).show();
            return true;
        } else if (item.getItemId() == R.id.menu_export_chat) {
            exportChatToTxt();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showThreadListDialog() {
        List<ChatThread> threads = historyManager.getThreads(sc_id);
        if (threads.isEmpty()) {
            Toast.makeText(this, R.string.chat_thread_none, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[threads.size()];
        for (int i = 0; i < threads.size(); i++) {
            labels[i] = formatThreadLine(threads.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_threads_title)
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < threads.size()) {
                        switchThread(threads.get(which).id);
                    }
                })
                .setPositiveButton(R.string.chat_thread_create, (dialog, which) -> createNewThread())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showThreadActionsSheet(ChatThread thread) {
        if (thread == null) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = createSheetRoot();
        TextView title = createSheetTitle(R.string.kelivo_thread_actions_title);
        root.addView(title);
        root.addView(createSheetAction(R.drawable.kelivo_lucide_edit, R.string.kelivo_thread_rename, v -> {
            dialog.dismiss();
            showRenameThreadDialog(thread);
        }));
        root.addView(createSheetAction(thread.pinned ? R.drawable.kelivo_lucide_pin_off : R.drawable.kelivo_lucide_pin,
                thread.pinned ? R.string.kelivo_thread_unpin : R.string.kelivo_thread_pin, v -> {
                    historyManager.setThreadPinned(sc_id, thread.id, !thread.pinned);
                    refreshDrawerThreads();
                    dialog.dismiss();
                }));
        root.addView(createSheetAction(R.drawable.kelivo_lucide_trash_2, R.string.kelivo_thread_delete, v -> {
            dialog.dismiss();
            confirmDeleteThread(thread);
        }));
        dialog.setContentView(root);
        expandSheet(dialog, 0.42f);
        dialog.show();
    }

    private void showRenameThreadDialog(ChatThread thread) {
        if (thread == null) {
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(ChatMessage.hasVisibleText(thread.title) ? thread.title : getString(R.string.chat_thread_new_title));
        input.setSelectAllOnFocus(true);
        int padding = dp(18);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(padding, dp(6), padding, 0);
        frame.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.kelivo_thread_rename)
                .setView(frame)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = input.getText() == null ? "" : input.getText().toString().trim();
            if (!ChatMessage.hasVisibleText(title)) {
                return;
            }
            historyManager.renameThread(sc_id, thread.id, title);
            updateKelivoHeader();
            refreshDrawerThreads();
            Toast.makeText(this, R.string.kelivo_thread_renamed, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void confirmDeleteThread(ChatThread thread) {
        if (thread == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.kelivo_thread_delete)
                .setMessage(R.string.kelivo_thread_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.kelivo_thread_delete, (dialog, which) -> deleteThread(thread))
                .show();
    }

    private void deleteThread(ChatThread thread) {
        if (thread == null || historyManager == null) {
            return;
        }
        List<ChatReference> removedReferences = collectMessageReferences(
                historyManager.loadHistory(sc_id, thread.id));
        boolean wasActive = thread.id.equals(activeThreadId);
        historyManager.deleteThread(sc_id, thread.id);
        if (wasActive) {
            List<ChatThread> remaining = historyManager.getThreads(sc_id);
            String nextThreadId = remaining.isEmpty()
                    ? historyManager.ensureDefaultThread(sc_id)
                    : remaining.get(0).id;
            activeThreadId = "";
            openThread(nextThreadId, false, false);
        } else {
            refreshDrawerThreads();
        }
        releaseReferenceGrantsIfUnused(removedReferences);
        Toast.makeText(this, R.string.kelivo_thread_deleted, Toast.LENGTH_SHORT).show();
    }

    private String formatThreadLine(ChatThread thread) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        String title = ChatMessage.hasVisibleText(thread.title)
                ? thread.title
                : getString(R.string.chat_thread_new_title);
        String summary = ChatMessage.hasVisibleText(thread.summary)
                ? thread.summary
                : getString(R.string.chat_thread_empty_summary);
        String model = ChatMessage.hasVisibleText(thread.activeModel) ? " | " + thread.activeModel : "";
        String current = thread.id.equals(activeThreadId) ? getString(R.string.chat_thread_current_prefix) : "";
        return current + title + "\n" + summary + "\n" + format.format(new Date(thread.updatedAt)) + model;
    }

    private void createNewThread() {
        if (isProcessing) {
            Toast.makeText(this, R.string.chat_wait_processing, Toast.LENGTH_SHORT).show();
            return;
        }
        saveChatHistory();
        String threadId = historyManager.createThread(sc_id);
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        switchThread(threadId);
        refreshDrawerThreads();
    }

    private void switchThread(String threadId) {
        openThread(threadId, true, true);
    }

    private void openThread(String threadId, boolean saveCurrent, boolean showToast) {
        if (!ChatMessage.hasVisibleText(threadId) || threadId.equals(activeThreadId)) {
            return;
        }
        if (isProcessing) {
            Toast.makeText(this, R.string.chat_wait_processing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (saveCurrent) {
            saveChatHistory();
        }
        activeThreadId = threadId;
        int oldCount = messages.size();
        messages.clear();
        if (oldCount > 0) {
            messageAdapter.notifyDataSetChanged();
        } else {
            messageAdapter.notifyDataSetChanged();
        }
        currentDebugMessage = null;
        loadChatHistory();
        refreshDrawerThreads();
        updateKelivoHeader();
        if (showToast) {
            Toast.makeText(this, R.string.chat_thread_switched, Toast.LENGTH_SHORT).show();
        }
    }

    private void showModelCatalogDialog() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        List<String> lines = buildModelCatalogLines(prefs);
        RecyclerView recyclerView = createDialogRecyclerView(dp(420));
        ChatSimpleTextAdapter adapter = new ChatSimpleTextAdapter();
        adapter.setItems(lines);
        recyclerView.setAdapter(adapter);
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_model_catalog_title)
                .setView(recyclerView)
                .setPositiveButton(R.string.chat_model_refresh_local, (dialog, which) -> refreshLocalModels())
                .setNeutralButton(R.string.chat_model_open_settings, (dialog, which) ->
                        startActivity(new Intent(this, IaSettingsActivity.class)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private List<String> buildModelCatalogLines(SharedPreferences prefs) {
        List<String> lines = new ArrayList<>();
        for (VoidPortSettings.ProviderGroup group : VoidPortSettings.getAllProviderGroups(prefs)) {
            if (!VoidPortSettings.isProviderSupportedInChat(group.providerId)) {
                continue;
            }
            boolean configured = VoidPortSettings.isProviderConfigured(prefs, group.providerId);
            String providerState = configured ? "OK" : "SETUP";
            if (group.models.isEmpty()) {
                lines.add(providerState + " - " + group.label + "\n" + getString(R.string.chat_model_no_catalog_models));
                continue;
            }
            for (String model : group.models) {
                VoidPortModelCapabilities.Capabilities capabilities =
                        VoidPortModelCapabilities.getModelCapabilities(group.providerId, model);
                lines.add(providerState + " - " + group.label + " / " + model + "\n" +
                        formatModelCapabilities(capabilities));
            }
        }
        if (lines.isEmpty()) {
            lines.add(getString(R.string.chat_no_models_available));
        }
        return lines;
    }

    private String formatModelCapabilities(VoidPortModelCapabilities.Capabilities capabilities) {
        String reasoning = capabilities.reasoningCapabilities.supportsReasoning
                ? capabilities.reasoningCapabilities.sliderType.name().toLowerCase(Locale.US)
                : "none";
        return "context=" + capabilities.contextWindow
                + " | output=" + capabilities.reservedOutputTokenSpace
                + " | tools=" + capabilities.toolFormat
                + " | reasoning=" + reasoning
                + " | fim=" + capabilities.supportsFim;
    }

    private void refreshLocalModels() {
        Toast.makeText(this, R.string.chat_model_refresh_started, Toast.LENGTH_SHORT).show();
        for (String provider : new String[]{"ollama", "vllm", "lm_studio"}) {
            VoidPortRefreshModelService.refreshProviderAsync(this, provider, true, result -> {
                updateModelUI();
                String message = result.state == VoidPortRefreshModelService.RefreshState.FINISHED
                        ? getString(R.string.chat_model_refresh_done, result.providerId, result.models.size())
                        : getString(R.string.chat_model_refresh_error, result.providerId, result.error);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void clearChat() {
        List<ChatReference> removedReferences = collectMessageReferences(messages);
        if (historyManager != null && sc_id != null) {
            historyManager.clearHistory(sc_id, activeThreadId);
        }
        messages.clear();
        messageAdapter.notifyDataSetChanged();
        addWelcomeMessage();
        scrollToBottom();
        updateThreadSummary();
        refreshSecondaryPanels();
        releaseReferenceGrantsIfUnused(removedReferences);
        Toast.makeText(this, R.string.chat_cleared, Toast.LENGTH_SHORT).show();
    }

    public void cancelCurrentRun() {
        if (agentManager == null || !agentManager.cancelCurrentRun()) {
            Toast.makeText(this, R.string.chat_nothing_to_cancel, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.chat_run_cancelled, Toast.LENGTH_SHORT).show();
    }

    private void exportChatToTxt() {
        if (messages == null || messages.isEmpty()) {
            Toast.makeText(this, R.string.chat_recent_changes_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        for (ChatMessage msg : messages) {
            if (msg.isCheckpoint() || msg.isAwaitingUser()) continue;

            String date = sdf.format(new Date(msg.getTimestamp()));
            String role;
            if (msg.isUser()) {
                role = "USER";
            } else if (msg.isBot()) {
                role = "AI";
            } else if (msg.isTool()) {
                role = "TOOL (" + msg.getToolName() + ")";
            } else {
                role = "SYSTEM";
            }

            sb.append("[").append(date).append("] ").append(role).append(":\n");
            
            if (msg.isTool()) {
                sb.append("Args: ").append(msg.getToolArgs()).append("\n");
                if (msg.getToolResult() != null) {
                    sb.append("Result: ").append(msg.getToolResult()).append("\n");
                }
            } else {
                sb.append(msg.getMessage()).append("\n");
            }

            if (msg.getReasoning() != null && !msg.getReasoning().isEmpty()) {
                sb.append("\nReasoning:\n").append(msg.getReasoning()).append("\n");
            }
            sb.append("\n------------------\n\n");
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Sketchware IA Chat Export - " + sc_id);
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, getString(R.string.chat_menu_export_chat)));
    }

    public void rollbackLastCheckpoint() {
        if (isProcessing) {
            Toast.makeText(this, R.string.chat_wait_processing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (agentManager == null) {
            return;
        }

        ChatCheckpointManager.RollbackResult result = agentManager.rollbackLastCheckpoint();
        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
        if (!result.success) {
            return;
        }

        ChatMessage rollbackMsg = new ChatMessage(
                result.message,
                ChatMessage.TYPE_CHECKPOINT,
                System.currentTimeMillis(),
                "Rollback"
        );
        messages.add(rollbackMsg);
        messageAdapter.notifyDataSetChanged();
        scrollToBottom();
        saveChatHistory();
        updateThreadSummary();
        refreshSecondaryPanels();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_REFERENCE_FILE) {
            if (resultCode == RESULT_OK && data != null) {
                int addedCount = 0;
                int failedCount = 0;
                for (Uri uri : collectResultUris(data)) {
                    if (!persistReferenceReadPermission(data, uri)) {
                        failedCount++;
                        continue;
                    }
                    ChatReference reference = ChatReferenceManager.fromDocumentUri(this, uri);
                    if (reference == null || reference.getUri() == null) {
                        failedCount++;
                        releaseManagedReferenceGrantIfUnused(uri.toString());
                    } else if (addPendingReference(reference)) {
                        addedCount++;
                    } else {
                        releaseManagedReferenceGrantIfUnused(uri.toString());
                    }
                }

                if (failedCount > 0) {
                    Toast.makeText(this,
                            getString(R.string.chat_reference_files_partial, addedCount, failedCount),
                            Toast.LENGTH_LONG).show();
                } else if (addedCount == 1) {
                    Toast.makeText(this, R.string.chat_reference_file_added, Toast.LENGTH_SHORT).show();
                } else if (addedCount > 1) {
                    Toast.makeText(this, getString(R.string.chat_reference_files_added, addedCount), Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if (requestCode == REQUEST_PICK_REFERENCE_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                int addedCount = 0;
                int failedCount = 0;
                for (Uri uri : collectResultUris(data)) {
                    if (!persistReferenceReadPermission(data, uri)) {
                        failedCount++;
                        continue;
                    }
                    ChatReference reference = ChatReferenceManager.fromImageUri(this, uri);
                    if (addPendingReference(reference)) {
                        addedCount++;
                    } else {
                        releaseManagedReferenceGrantIfUnused(uri.toString());
                    }
                }

                if (failedCount > 0) {
                    Toast.makeText(this,
                            getString(R.string.chat_reference_files_partial, addedCount, failedCount),
                            Toast.LENGTH_LONG).show();
                } else if (addedCount == 1) {
                    Toast.makeText(this, R.string.chat_reference_image_added, Toast.LENGTH_SHORT).show();
                } else if (addedCount > 1) {
                    Toast.makeText(this, getString(R.string.chat_reference_images_added, addedCount), Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if (requestCode == REQUEST_CAPTURE_REFERENCE_IMAGE) {
            if (resultCode == RESULT_OK && pendingCameraImageUri != null) {
                ChatReference reference = ChatReferenceManager.fromImageUri(this, pendingCameraImageUri);
                if (addPendingReference(reference)) {
                    Toast.makeText(this, R.string.chat_reference_image_added, Toast.LENGTH_SHORT).show();
                }
                clearPendingCameraImage(false);
            } else {
                clearPendingCameraImage(true);
            }
            return;
        }
        if (requestCode == 1001) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (result != null && !result.isEmpty()) {
                    String spokenText = result.get(0);
                    editTextMessage.setText(spokenText);
                    editTextMessage.setSelection(spokenText.length());
                }
            }
            return;
        }
        if (requestCode == REQUEST_PICK_USER_AVATAR) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                grantPersistableReadPermission(data, uri);
                getSharedPreferences("chat_settings", MODE_PRIVATE)
                        .edit()
                        .putString(PREF_AVATAR_TYPE, "file")
                        .putString(PREF_AVATAR_VALUE, uri.toString())
                        .apply();
                updateDrawerUserUi();
                if (messageAdapter != null) {
                    messageAdapter.notifyDataSetChanged();
                }
                Toast.makeText(this, R.string.kelivo_avatar_updated, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void clearPendingCameraImage(boolean deleteFile) {
        if (deleteFile && pendingCameraImageFile != null) {
            try {
                pendingCameraImageFile.delete();
            } catch (Exception ignored) {
            }
        }
        pendingCameraImageUri = null;
        pendingCameraImageFile = null;
    }

    private List<Uri> collectResultUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data == null) {
            return uris;
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null && !uris.contains(uri)) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    private boolean persistReferenceReadPermission(Intent data, Uri uri) {
        if (!grantPersistableReadPermission(data, uri)) {
            return false;
        }
        registerManagedReferenceGrant(uri);
        return true;
    }

    private boolean grantPersistableReadPermission(Intent data, Uri uri) {
        if (data == null || uri == null) {
            return false;
        }
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) {
            return false;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void registerManagedReferenceGrant(Uri uri) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences("chat_settings", MODE_PRIVATE);
        Set<String> managed = new HashSet<>(preferences.getStringSet(
                PREF_MANAGED_REFERENCE_URI_GRANTS, new HashSet<>()));
        managed.add(uri.toString());
        preferences.edit().putStringSet(PREF_MANAGED_REFERENCE_URI_GRANTS, managed).apply();
    }

    private void reconcileManagedReferenceGrants() {
        SharedPreferences preferences = getSharedPreferences("chat_settings", MODE_PRIVATE);
        Set<String> managed = new HashSet<>(preferences.getStringSet(
                PREF_MANAGED_REFERENCE_URI_GRANTS, new HashSet<>()));
        for (String uriValue : managed) {
            releaseManagedReferenceGrantIfUnused(uriValue);
        }
    }

    private void releaseReferenceGrantsIfUnused(List<ChatReference> references) {
        Set<String> uriValues = new HashSet<>();
        for (ChatReference reference : references == null ? new ArrayList<ChatReference>() : references) {
            if (reference != null && reference.getUri() != null) {
                uriValues.add(reference.getUri().toString());
            }
        }
        for (String uriValue : uriValues) {
            releaseManagedReferenceGrantIfUnused(uriValue);
        }
    }

    private void releaseManagedReferenceGrantIfUnused(String uriValue) {
        if (!ChatMessage.hasVisibleText(uriValue)) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences("chat_settings", MODE_PRIVATE);
        Set<String> managed = new HashSet<>(preferences.getStringSet(
                PREF_MANAGED_REFERENCE_URI_GRANTS, new HashSet<>()));
        if (!managed.contains(uriValue)
                || isPendingReferenceUri(uriValue)
                || isAvatarUri(uriValue)
                || (historyManager != null && historyManager.containsReferenceUri(uriValue))) {
            return;
        }
        try {
            getContentResolver().releasePersistableUriPermission(
                    Uri.parse(uriValue), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // The provider may already have revoked the grant. Remove stale bookkeeping too.
        }
        managed.remove(uriValue);
        preferences.edit().putStringSet(PREF_MANAGED_REFERENCE_URI_GRANTS, managed).apply();
    }

    private boolean isPendingReferenceUri(String uriValue) {
        for (ChatReference reference : pendingReferences) {
            if (reference != null
                    && reference.getUri() != null
                    && uriValue.equals(reference.getUri().toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAvatarUri(String uriValue) {
        SharedPreferences preferences = getSharedPreferences("chat_settings", MODE_PRIVATE);
        return "file".equals(preferences.getString(PREF_AVATAR_TYPE, ""))
                && uriValue.equals(preferences.getString(PREF_AVATAR_VALUE, ""));
    }

    private List<ChatReference> collectMessageReferences(List<ChatMessage> sourceMessages) {
        List<ChatReference> references = new ArrayList<>();
        for (ChatMessage message : sourceMessages == null ? new ArrayList<ChatMessage>() : sourceMessages) {
            if (message != null && message.hasStagingSelections()) {
                references.addAll(message.getStagingSelections());
            }
        }
        return references;
    }

    @Override
    protected void onDestroy() {
        List<ChatReference> abandonedReferences = new ArrayList<>(pendingReferences);
        pendingReferences.clear();
        releaseReferenceGrantsIfUnused(abandonedReferences);
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        flushStreamingMessageUpdate();
        saveChatHistory();
        streamUiHandler.removeCallbacksAndMessages(null);
        if (agentManager != null) {
            agentManager.cancelCurrentRun();
        }
        if (historyManager != null) {
            historyManager.shutdown();
        }
        clearPendingCameraImage(true);
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
