package pro.sketchware.chat;


import pro.sketchware.BuildConfig;
import android.content.Intent;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.paging.PagingData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;

import pro.sketchware.chat.KelivoModelSheetAdapter;

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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.chat.ProjectManager;
import pro.sketchware.chat.MapUtils;
import pro.sketchware.R;
import pro.sketchware.utility.TranslationFunction;
import pro.sketchware.chat.port.VoidPortChatThreadService;
import pro.sketchware.chat.port.VoidPortConvertToLlmMessageService;
import pro.sketchware.chat.port.VoidPortModelCapabilities;
import pro.sketchware.chat.port.VoidPortLlmMessage;
import pro.sketchware.chat.port.VoidPortRefreshModelService;
import pro.sketchware.chat.port.VoidPortScmService;
import pro.sketchware.chat.port.VoidPortSettings;
import pro.sketchware.chat.analytics.AxionAnalytics;
import pro.sketchware.chat.agent.MultiAgentPolicy;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
public class ChatActivity extends BaseAppCompatActivity {
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
    private ImageView btnOnlineSearch;
    private ImageView btnMcpServers;
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
    private String autoFixPreviousChatMode;
    private ChatHistoryManager historyManager;
    private LiveData<PagingData<ChatPagingItem>> activePagingData;
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
    private static final long STREAM_CHECKPOINT_INTERVAL_MS = 750L;
    private ChatMessage pendingStreamingUpdate;
    private boolean streamUpdateScheduled = false;
    private long lastStreamCheckpointAtMs = 0L;
    private String currentLocalOperationId = "";
    private String currentLocalRunThreadId = "";
    private int streamUiRefreshCount = 0;
    private int historySaveCount = 0;
    private long historySaveTotalMs = 0L;
    private long currentRunStartedAtMs = 0L;
    private boolean currentRunFailed = false;
    private boolean currentRunCancelled = false;
    private boolean activityDestroying = false;
    private long projectBuildStartedAtMs = 0L;
    private boolean debugHistoryDirty = false;
    private DrawerLayout drawerLayout;
    private TextView textChatTitle;
    private TextView textChatSubtitle;
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
    private final Runnable liveModelsListener = () -> runOnUiThread(() -> {
        SharedPreferences aiPrefs = AiChatSettingsHelper.prefs(this);
        AiChatSettingsHelper.ensureValidCurrentSelection(aiPrefs);
        updateModelUI();
    });

    @Override
    public Resources getResources() {
        return TranslationFunction.wrapResources(this, super.getResources());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long startTime = System.currentTimeMillis();
        ChatFlowLogger.event("activity", "created", "chat activity started");
        Log.d("ChatActivity", "=== onCreate: INÍCIO (tempo 0ms) ===");
        
        super.onCreate(savedInstanceState);
        Log.d("ChatActivity", "onCreate: super.onCreate em " + (System.currentTimeMillis() - startTime) + "ms");
        
        long layoutTime = System.currentTimeMillis();
        setContentView(R.layout.activity_chat);
        Log.d("ChatActivity", "onCreate: setContentView em " + (System.currentTimeMillis() - layoutTime) + "ms (total: " + (System.currentTimeMillis() - startTime) + "ms)");

        sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null || sc_id.isEmpty()) {
            ChatFlowLogger.event("activity", "closed", "missing project id");
            Toast.makeText(this, R.string.chat_project_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Providers and conversations saved by the chat Sketchware had before this engine (runs once)
        pro.sketchware.chat.legacy.LegacyChatMigration.run(this);
        // The project's own files are the agent's workspace (native projects: scoped and decrypted)
        if (!SketchwareWorkspace.activate(sc_id)) {
            ChatFlowLogger.event("activity", "closed", "project not found: " + sc_id);
            Toast.makeText(this, R.string.chat_project_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        executorService = Executors.newSingleThreadExecutor();
        historyManager = new ChatHistoryManager(this);
        activeThreadId = historyManager.getCurrentThreadId(sc_id);
        Log.d("ChatActivity", "onCreate: Managers criados em " + (System.currentTimeMillis() - startTime) + "ms");

        // Carregar preferência de debug
        SharedPreferences prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        showDebug = prefs.getBoolean("show_debug", false);

        long setupTime = System.currentTimeMillis();
        setupViews();
        bindPagingThread(true);
        Log.d("ChatActivity", "onCreate: setupViews em " + (System.currentTimeMillis() - setupTime) + "ms (total: " + (System.currentTimeMillis() - startTime) + "ms)");
        
        refreshManagedAiState(false);
        loadProjectInfo();
        Log.d("ChatActivity", "=== onCreate: CONCLUÍDO em " + (System.currentTimeMillis() - startTime) + "ms ===");

        // Inicializar o runtime v2 (AgentRuntime + AxionAgentGateway) e o
        // controller de UI. O AgentManager vira apenas adapter de UI — o
        // motor de execução é EXCLUSIVAMENTE o AgentRuntime v2 (migração
        // itens 1–3), e os eventos estruturados do run chegam à UI pelo
        // mesmo EventStream do runtime (itens 8/9).
        agentManager = AgentManager.forUi(this, sc_id, messages,
                pro.sketchware.chat.agentsdk.AgentRuntimeFactory.createForChat(this),
                new AgentManager.AgentListener() {
            @Override
            public void onMessageAdded(ChatMessage message) {
                ChatFlowLogger.event("ui", "message_added", message == null ? "null"
                        : "bot=" + message.isBot() + ", tool=" + message.isTool());
                runOnUiThread(() -> {
                    if (isProcessing && message != null && message.isBot()) {
                        message.setStreaming(true);
                    }
                    renderLatestMessages(true);
                    if (isProcessing && message != null && message.isBot()) {
                        return;
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
                    renderLatestMessages(false);
                    updateThreadSummary();
                });
            }

            @Override
            public void onStatusChanged(String status) {
                ChatFlowLogger.event("ui", "status", status);
                runOnUiThread(() -> updateRunStatus(status));
            }

            @Override
            public void onDebug(String message) {
                ChatFlowLogger.event("agent", "debug", message);
                runOnUiThread(() -> appendDebugMessage(message));
            }

            @Override
            public void onProcessingFinished() {
                runOnUiThread(() -> {
                    if (activityDestroying) return;
                    clearStreamingFlags();
                    flushStreamingMessageUpdate();
                    finishLocalOperation();
                    if (currentRunStartedAtMs > 0L) {
                        String result = currentRunCancelled
                                ? "cancelled"
                                : currentRunFailed ? "failure" : "success";
                        AxionAnalytics.logEvent(
                                ChatActivity.this,
                                AxionAnalytics.Events.CHAT_RUN_RESULT,
                                AxionAnalytics.params(
                                        AxionAnalytics.Params.RESULT,
                                        result,
                                        AxionAnalytics.Params.DURATION_MS,
                                        Math.max(0L, SystemClock.elapsedRealtime() - currentRunStartedAtMs),
                                        AxionAnalytics.Params.MESSAGE_COUNT,
                                        messages == null ? 0 : messages.size()));
                        currentRunStartedAtMs = 0L;
                        ChatFlowLogger.event("ui", "run_finished", "result=" + result);
                        currentRunFailed = false;
                        currentRunCancelled = false;
                    }
                    // Rebind the final non-streaming state and keep the completed
                    // assistant answer visible even after long tool/model cycles.
                    renderLatestMessages(true);
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
                    // restoreChatModeAfterAutoFix removed - no embedded compiler
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
            public void onCompactionStateChanged(String summary, int compactedUntil) {
                runOnUiThread(() -> {
                    if (historyManager != null && ChatMessage.hasVisibleText(activeThreadId)) {
                        historyManager.saveCompactionState(
                                sc_id, activeThreadId, summary, compactedUntil);
                    }
                });
            }

            @Override
            public void onUserFacingError(UserFacingError error, String requestId) {
                runOnUiThread(() -> showUserFacingAiError(error, requestId));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    UserFacingError fallback = UserFacingError.builder()
                            .title(getString(R.string.chat_error_could_not_complete_title))
                            .message(error == null || error.trim().isEmpty()
                                    ? getString(R.string.chat_error_operation_generic)
                                    : error.trim())
                            .canRetry(true)
                            .build();
                    showUserFacingAiError(fallback, null);
                });
            }
        });
        // Apply the persisted permission mode to the freshly built runtime
        // (the factory defaults to WORKSPACE + ON_REQUEST). Full access is
        // never applied unless the user explicitly confirmed it.
        SharedPreferences settingsPrefs = AiChatSettingsHelper.prefs(this);
        agentManager.setPermissionConfig(VoidPortSettings.permissionConfigForMode(
                VoidPortSettings.getPermissionMode(settingsPrefs)));
        reconcileManagedReferenceGrants();
        applyPlanUi();
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
        imagePreviewScroll = findViewById(R.id.selected_image_preview_scroll);
        imagePreviewList = findViewById(R.id.selected_image_preview_list);
        editTextMessage.setHint(R.string.kelivo_input_hint);

        messages = new ArrayList<>();
        messageAdapter = new ChatMessageAdapter(messages);
        messageAdapter.setMessageActionListener(new ChatMessageAdapter.MessageActionListener() {
            @Override
            public void onRegenerate(ChatMessage message) {
                regenerateFromPosition(messages.indexOf(message));
            }

            @Override
            public void onEdit(ChatMessage message) {
                editMessageAtPosition(messages.indexOf(message));
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
            public void onDelete(ChatMessage message) {
                deleteMessageAtPosition(messages.indexOf(message));
            }
        });
        setupKelivoUi();
        chatMessagesFragment = new ChatMessagesFragment();
        chatDiffFragment = ChatDiffFragment.newInstance(sc_id);
        chatArtifactsFragment = ChatArtifactsFragment.newInstance(sc_id);
        chatPlanFragment = ChatPlanFragment.newInstance(sc_id);
        chatMessagesFragment.setAdapter(messageAdapter);
        chatArtifactsFragment.setMessages(messages);
        chatPlanFragment.setMessages(messages);
        chatCompileLogFragment = ChatCompileLogFragment.newInstance(sc_id);
        ChatPagerAdapter pagerAdapter = new ChatPagerAdapter(
                this, chatMessagesFragment, chatDiffFragment, chatArtifactsFragment,
                chatPlanFragment, chatCompileLogFragment);
        chatViewPager.setAdapter(pagerAdapter);
        chatViewPager.setOffscreenPageLimit(Math.max(1, pagerAdapter.getCount() - 1));
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
                updateSendButtonState();
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
        View btnPermissions = findViewById(R.id.btn_permissions);
        btnModelSelector = findViewById(R.id.btn_model_selector);
        btnOnlineSearch = findViewById(R.id.btn_online_search);
        btnMcpServers = findViewById(R.id.btn_mcp_servers);
        textChatMode = findViewById(R.id.text_chat_mode);
        textCurrentModel = findViewById(R.id.text_current_model);

        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
        updateChatModeUI();
        updateModelUI();
        updatePermissionsUi(prefs);
        updateRunStatus("");
        updateChangedFilesSummary();
        updateThreadSummary();
        updatePendingReferencesUi();
        updateSendButtonState();

        if (btnChatMode != null) {
            btnChatMode.setOnClickListener(v -> showChatModeMenu(prefs));
        }
        if (textChatMode != null) {
            // The mode label is now a visible pill; tapping it opens the selector too.
            textChatMode.setOnClickListener(v -> showChatModeMenu(prefs));
        }
        if (btnOnlineSearch != null) {
            btnOnlineSearch.setOnClickListener(v -> showOnlineSearchSheet());
        }
        if (btnMcpServers != null) {
            btnMcpServers.setOnClickListener(v -> showMcpServersSheet());
        }
        if (btnPermissions != null) {
            btnPermissions.setOnClickListener(v -> showPermissionsMenu(prefs));
        }

        layoutRunStatus = findViewById(R.id.layout_run_status);
        runStatusDots = findViewById(R.id.run_status_dots);
        textRunStatus = findViewById(R.id.text_run_status);

        btnModelSelector.setOnClickListener(v -> showModelSelectorMenu(prefs));
        updateComposerToolUi();
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

        View compileButton = findViewById(R.id.btn_header_compile);
        if (compileButton != null) {
            compileButton.setOnClickListener(v -> startChatCompile());
        }



        drawerUserName = findViewById(R.id.drawer_user_name);
        drawerUserAvatar = findViewById(R.id.drawer_user_avatar);
        drawerUserAvatarImage = findViewById(R.id.drawer_user_avatar_image);
        drawerUserAvatarContainer = findViewById(R.id.drawer_user_avatar_container);
        updateDrawerUserUi();

        View drawerSettings = findViewById(R.id.btn_drawer_settings);
        if (drawerSettings != null) {
            drawerSettings.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                startActivity(new Intent(this, pro.sketchware.chat.provider.IaSettingsActivity.class));
            });
        }
        // The runtime applies the saved skills on every run; this is where they are created and switched on/off
        View drawerSkills = findViewById(R.id.btn_drawer_skills);
        if (drawerSkills != null) {
            drawerSkills.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                startActivity(new Intent(this, pro.sketchware.chat.skills.SkillsActivity.class));
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

                @Override
                public void onThreadDelete(ChatThread thread) {
                    confirmDeleteThread(thread);
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






    private String getDrawerUserName() {
        SharedPreferences prefs = getSharedPreferences("chat_settings", MODE_PRIVATE);
        String name = prefs.getString(PREF_USER_NAME, "");
        if (!ChatMessage.hasVisibleText(name)) {
            name = prefs.getString(PREF_LEGACY_USER_NAME, getString(R.string.kelivo_default_user));
        }
        return name;
    }

    private String getDrawerUserInitial(String userName) {
        String trimmed = userName == null ? "" : userName.trim();
        if (trimmed.isEmpty()) {
            return "f";
        }
        return String.valueOf(Character.toLowerCase(trimmed.charAt(0)));
    }

    private void updateDrawerUserUi() {
        String userName = getDrawerUserName();
        if (drawerUserName != null) {
            drawerUserName.setText(userName);
        }
        if (drawerUserAvatarImage != null) {
            drawerUserAvatarImage.setImageResource(R.drawable.kelivo_lucide_bot_message_square);
            drawerUserAvatarImage.setVisibility(View.VISIBLE);
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
        // Subtítulo (provider/model) e ícone do modelo (ponto cinza) foram REMOVIDOS do header.
        // Garantir que sempre fiquem GONE em runtime, mesmo se XML ou fluxos antigos tentarem setar VISIBLE.
        if (textChatSubtitle != null) {
            textChatSubtitle.setVisibility(View.GONE);
        }
        if (imageChatModelIcon != null) {
            imageChatModelIcon.setVisibility(View.GONE);
        }
        View subRow = findViewById(R.id.chat_subtitle_row);
        if (subRow != null) {
            subRow.setVisibility(View.GONE);
        }
    }

    private void updateModelUI() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        String currentModel = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
        String currentProvider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        if (textCurrentModel != null) {
            if (ChatMessage.hasVisibleText(currentModel)
                    && AiChatSettingsHelper.isCurrentSelectionValid(prefs, currentProvider, currentModel)) {
                String displayModel = currentModel;
                String iconProvider = currentProvider;
                textCurrentModel.setText(displayModel);
                bindModelIcon(btnModelSelector instanceof ImageView ? (ImageView) btnModelSelector : null,
                        iconProvider, currentModel, true);
            } else {
                textCurrentModel.setText(R.string.chat_no_models_available_short);
                bindModelIcon(btnModelSelector instanceof ImageView ? (ImageView) btnModelSelector : null,
                        "", "", true);
            }
        }
        updateComposerToolUi();
        updateKelivoHeader();
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
        updateComposerToolUi();
    }

    private void updatePermissionsUi(SharedPreferences prefs) {
        if (prefs == null) {
            prefs = AiChatSettingsHelper.prefs(this);
        }
        // Item: the permission mode used to be shown as a small shield-icon
        // badge next to the "+" button, duplicating the shield already used
        // by btn_permissions in the same row. It now shows as a plain, quiet
        // caption below the message input box instead (no icon).
        TextView caption = findViewById(R.id.text_permission_caption);
        if (caption == null) {
            return;
        }
        String mode = VoidPortSettings.getPermissionMode(prefs);
        int labelRes;
        if (VoidPortSettings.PERMISSION_MODE_READ.equals(mode)) {
            labelRes = R.string.chat_permission_read_only;
        } else if (VoidPortSettings.PERMISSION_MODE_FULL.equals(mode)) {
            labelRes = R.string.chat_permission_full_access;
        } else {
            labelRes = R.string.chat_permission_ask_approval;
        }
        String label = getString(labelRes);
        caption.setText(getString(R.string.chat_permission_mode_format, label));
        caption.setContentDescription(getString(R.string.chat_permission_mode_format, label));
        caption.setVisibility(View.VISIBLE);
    }

    private void showModelSelectorMenu(SharedPreferences prefs) {
        refreshManagedAiState(false);
        KelivoModelBottomSheet.show(this, (providerId, modelId) -> {
            AxionAnalytics.logEvent(
                    this,
                    AxionAnalytics.Events.MODEL_SELECTED,
                    AxionAnalytics.params(
                            AxionAnalytics.Params.SOURCE,
                            "model_sheet",
                            AxionAnalytics.Params.ENABLED,
                            KelivoModelSheetAdapter.supportsImageInput(
                                    this,
                                    providerId,
                                    modelId)));
            updateModelUI();
            updateThreadSummary();
            String selectedLabel = providerId + " / " + modelId;
            Toast.makeText(this, getString(R.string.chat_model_changed, selectedLabel), Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshManagedAiState(boolean notifyErrors) {
        SharedPreferences preferences = AiChatSettingsHelper.prefs(this);
        AiChatSettingsHelper.ensureValidCurrentSelection(preferences);
        updateModelUI();
    }

    private void showKelivoToolsSheet() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
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
        });
    }

    private void showOnlineSearchSheet() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        boolean supported = isGeminiSelection(prefs);
        boolean checked = supported && VoidPortSettings.isChatWebSearchEnabled(prefs);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = createSheetRoot();
        root.addView(createSheetTitle(R.string.chat_online_search));
        root.addView(createSheetDescription(
                supported
                        ? R.string.chat_online_search_description
                        : R.string.chat_online_search_requires_gemini));

        View toggleRow = createSheetToggleRow(
                R.drawable.ic_kelivo_globe,
                getString(R.string.chat_online_search),
                getString(R.string.chat_online_search_description),
                checked,
                supported,
                (button, enabled) -> {
                    VoidPortSettings.setChatWebSearchEnabled(prefs, enabled);
                    updateComposerToolUi();
                    Toast.makeText(this,
                            enabled
                                    ? R.string.chat_online_search_enabled
                                    : R.string.chat_online_search_disabled,
                            Toast.LENGTH_SHORT).show();
                });
        root.addView(toggleRow);

        dialog.setContentView(root);
        expandSheet(dialog, 0.34f);
        dialog.show();
    }

    private void showMcpServersSheet() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        JSONObject config = VoidPortSettings.readMcpConfigObject(prefs);
        JSONObject servers = config.optJSONObject("mcpServers");
        JSONArray names = servers == null ? null : servers.names();

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = createSheetRoot();
        root.addView(createSheetTitle(R.string.chat_mcp_servers));
        root.addView(createSheetDescription(R.string.chat_mcp_servers_description));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new android.widget.ScrollView.LayoutParams(
                android.widget.ScrollView.LayoutParams.MATCH_PARENT,
                android.widget.ScrollView.LayoutParams.WRAP_CONTENT));

        List<String> serverNames = new ArrayList<>();
        for (int i = 0; names != null && i < names.length(); i++) {
            String name = names.optString(i, "").trim();
            if (!name.isEmpty()) {
                serverNames.add(name);
            }
        }
        java.util.Collections.sort(serverNames, String.CASE_INSENSITIVE_ORDER);

        if (serverNames.isEmpty()) {
            list.addView(createSheetDescription(R.string.chat_mcp_servers_empty));
        } else {
            for (String serverName : serverNames) {
                JSONObject server = servers == null ? null : servers.optJSONObject(serverName);
                boolean enabled = server != null && server.optBoolean("enabled", true);
                String endpoint = server == null ? "" : server.optString("url", "").trim();
                if (endpoint.isEmpty() && server != null) {
                    endpoint = server.optString("command", "").trim();
                }
                if (endpoint.isEmpty()) {
                    endpoint = getString(R.string.chat_mcp_agent_only);
                }
                list.addView(createSheetToggleRow(
                        R.drawable.ic_kelivo_hammer,
                        serverName,
                        endpoint,
                        enabled,
                        true,
                        (button, value) -> {
                            VoidPortSettings.setMcpServerEnabled(prefs, serverName, value);
                            updateComposerToolUi();
                        }));
            }
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(createSheetAction(
                R.drawable.ic_kelivo_hammer,
                R.string.chat_mcp_advanced_config,
                v -> {
                    dialog.dismiss();
                    showMcpJsonEditor();
                }));
        root.addView(createSheetDescription(R.string.chat_mcp_agent_only));

        dialog.setContentView(root);
        expandSheet(dialog, 0.68f);
        dialog.show();
    }

    private void showMcpJsonEditor() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        EditText input = new EditText(this);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(9);
        input.setMaxLines(16);
        input.setHorizontallyScrolling(true);
        input.setTextSize(13f);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        try {
            input.setText(VoidPortSettings.readMcpConfigObject(prefs).toString(2));
        } catch (Exception ignored) {
            input.setText(VoidPortSettings.DEFAULT_MCP_CONFIG);
        }

        FrameLayout frame = new FrameLayout(this);
        int margin = dp(20);
        frame.setPadding(margin, 0, margin, 0);
        frame.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.chat_mcp_json_title)
                .setMessage(R.string.chat_mcp_json_hint)
                .setView(frame)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String raw = input.getText() == null ? "" : input.getText().toString();
                    if (!VoidPortSettings.saveMcpConfig(prefs, raw)) {
                        input.setError(getString(R.string.chat_mcp_json_invalid));
                        return;
                    }
                    updateComposerToolUi();
                    Toast.makeText(this, R.string.chat_mcp_saved, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private TextView createSheetDescription(int textRes) {
        TextView text = new TextView(this);
        text.setText(textRes);
        text.setTextColor(getColor(R.color.chat_text_secondary));
        text.setTextSize(13f);
        text.setLineSpacing(0f, 1.08f);
        text.setPadding(dp(4), 0, dp(4), dp(12));
        return text;
    }

    private View createSheetToggleRow(int iconRes, String title, String subtitle,
                                      boolean checked, boolean enabled,
                                      android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(8), dp(9));
        row.setMinimumHeight(dp(62));
        row.setBackgroundResource(R.drawable.bg_kelivo_tool_tile);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(getColor(
                checked ? R.color.chat_accent : R.color.chat_text_secondary)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(8), 0);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColor(R.color.chat_text_primary));
        titleView.setTextSize(15f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(titleView);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(getColor(R.color.chat_text_secondary));
        subtitleView.setTextSize(11f);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(subtitleView);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        SwitchMaterial toggle = new SwitchMaterial(this);
        toggle.setChecked(checked);
        toggle.setEnabled(enabled);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.55f);
        row.setOnClickListener(enabled ? v -> toggle.setChecked(!toggle.isChecked()) : null);
        return row;
    }

    private boolean isGeminiSelection(SharedPreferences prefs) {
        String providerId = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        VoidPortLlmMessage.ProviderConfig config =
                VoidPortLlmMessage.resolveProviderConfig(prefs, providerId);
        return config != null
                && config.family == VoidPortLlmMessage.ProviderFamily.GEMINI;
    }

    private void updateComposerToolUi() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        boolean gemini = isGeminiSelection(prefs);
        boolean webSearch = gemini && VoidPortSettings.isChatWebSearchEnabled(prefs);
        if (btnOnlineSearch != null) {
            btnOnlineSearch.setAlpha(gemini ? 1f : 0.55f);
            btnOnlineSearch.setImageTintList(ColorStateList.valueOf(getColor(
                    webSearch ? R.color.chat_accent : R.color.chat_text_secondary)));
        }
        if (btnMcpServers != null) {
            boolean active = VoidPortSettings.countEnabledMcpServers(prefs) > 0;
            btnMcpServers.setImageTintList(ColorStateList.valueOf(getColor(
                    active ? R.color.chat_accent : R.color.chat_text_secondary)));
        }
        if (btnChatMode instanceof ImageView) {
            boolean agentMode = "agent".equalsIgnoreCase(AiChatSettingsHelper.getChatMode(prefs));
            ((ImageView) btnChatMode).setImageTintList(ColorStateList.valueOf(getColor(
                    agentMode ? R.color.chat_accent : R.color.chat_text_secondary)));
        }
    }

    private void updateSendButtonState() {
        if (btnSend == null || editTextMessage == null) {
            return;
        }
        boolean hasText = !editTextMessage.getText().toString().trim().isEmpty();
        boolean canSend = !isProcessing && (hasText || !pendingReferences.isEmpty());
        btnSend.setEnabled(canSend);
        btnSend.setAlpha(1f);
        btnSend.setBackgroundTintList(ColorStateList.valueOf(getColor(
                canSend ? R.color.chat_accent : R.color.kelivo_send_disabled)));
        if (btnSend instanceof ImageView) {
            ((ImageView) btnSend).setImageTintList(ColorStateList.valueOf(getColor(
                    canSend ? android.R.color.white : R.color.kelivo_send_icon_disabled)));
        }
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
        popup.getMenu().add(1, 1, 0, getString(R.string.chat_mode_chat));
        popup.getMenu().add(1, 2, 1, getString(R.string.chat_mode_gather));
        popup.getMenu().add(1, 3, 2, getString(R.string.chat_mode_agent));
        popup.getMenu().add(0, 10, 3, getString(R.string.chat_multi_agent_section))
                .setEnabled(false);
        popup.getMenu().add(2, 11, 4, getString(R.string.chat_multi_agent_auto));
        popup.getMenu().add(2, 12, 5, getString(R.string.chat_multi_agent_always));
        popup.getMenu().add(2, 13, 6, getString(R.string.chat_multi_agent_off));

        // Show independent radio marks for chat behavior and orchestration.
        popup.getMenu().setGroupCheckable(1, true, true);
        popup.getMenu().setGroupCheckable(2, true, true);
        String currentMode = AiChatSettingsHelper.getChatMode(prefs);
        int checkedId = "normal".equals(currentMode) ? 1 : "gather".equals(currentMode) ? 2 : 3;
        android.view.MenuItem checkedItem = popup.getMenu().findItem(checkedId);
        if (checkedItem != null) {
            checkedItem.setChecked(true);
        }
        String currentMultiAgentMode = AiChatSettingsHelper.getMultiAgentMode(prefs);
        int checkedMultiAgentId = MultiAgentPolicy.MODE_ALWAYS.equals(currentMultiAgentMode)
                ? 12
                : MultiAgentPolicy.MODE_OFF.equals(currentMultiAgentMode) ? 13 : 11;
        android.view.MenuItem checkedMultiAgentItem = popup.getMenu().findItem(checkedMultiAgentId);
        if (checkedMultiAgentItem != null) {
            checkedMultiAgentItem.setChecked(true);
        }

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() >= 11 && item.getItemId() <= 13) {
                String multiAgentMode = MultiAgentPolicy.MODE_AUTO;
                int descriptionRes = R.string.chat_multi_agent_detail_auto;
                if (item.getItemId() == 12) {
                    multiAgentMode = MultiAgentPolicy.MODE_ALWAYS;
                    descriptionRes = R.string.chat_multi_agent_detail_always;
                } else if (item.getItemId() == 13) {
                    multiAgentMode = MultiAgentPolicy.MODE_OFF;
                    descriptionRes = R.string.chat_multi_agent_detail_off;
                }
                AiChatSettingsHelper.setMultiAgentMode(prefs, multiAgentMode);
                Toast.makeText(this, getString(descriptionRes), Toast.LENGTH_SHORT).show();
                return true;
            }
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
            AxionAnalytics.logEvent(
                    this,
                    AxionAnalytics.Events.CHAT_MODE_CHANGED,
                    AxionAnalytics.params(AxionAnalytics.Params.MODE, mode));
            updateChatModeUI();
            Toast.makeText(this, getString(descriptionRes), Toast.LENGTH_SHORT).show();
            return true;
        });
        popup.show();
    }

    private void loadProjectInfo() {
        HashMap<String, Object> projectInfo = ProjectManager.b(sc_id);
        if (projectInfo != null) {
            String projectName = MapUtils.c(projectInfo, "my_ws_name");
            projectDisplayName = projectName;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.chat_title_with_project, projectName));
            }
        } else {
            projectDisplayName = getString(R.string.chat_default_project_name);
        }
    }

    private void showPermissionsMenu(SharedPreferences prefs) {
        View anchor = findViewById(R.id.btn_permissions);
        if (anchor == null) {
            anchor = textRunStatus;
        }
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(1, 1, 0, getString(R.string.chat_permission_ask_approval));
        popup.getMenu().add(1, 2, 1, getString(R.string.chat_permission_read_only));
        popup.getMenu().add(1, 3, 2, getString(R.string.chat_permission_full_access));
        popup.getMenu().setGroupCheckable(1, true, true);

        String mode = VoidPortSettings.getPermissionMode(prefs);
        int checked = VoidPortSettings.PERMISSION_MODE_READ.equals(mode)
                ? 2
                : VoidPortSettings.PERMISSION_MODE_FULL.equals(mode) ? 3 : 1;
        android.view.MenuItem item = popup.getMenu().findItem(checked);
        if (item != null) {
            item.setChecked(true);
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 3) {
                showFullAccessConfirmation(prefs);
                return true;
            }
            if (menuItem.getItemId() == 2) {
                setPermissionMode(prefs, VoidPortSettings.PERMISSION_MODE_READ);
            } else if (menuItem.getItemId() == 1) {
                setPermissionMode(prefs, VoidPortSettings.PERMISSION_MODE_ASK);
            }
            return true;
        });
        popup.show();
    }

    private void showFullAccessConfirmation(SharedPreferences prefs) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_full_access_dialog_title)
                .setMessage(R.string.chat_full_access_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.chat_activate, (dialog, which) ->
                        setPermissionMode(prefs, VoidPortSettings.PERMISSION_MODE_FULL))
                .show();
    }

    private void setPermissionMode(SharedPreferences prefs, String mode) {
        VoidPortSettings.setPermissionMode(prefs, mode);
        pro.sketchware.chat.agentsdk.PermissionConfig config =
                VoidPortSettings.permissionConfigForMode(mode);
        if (agentManager != null) {
            agentManager.setPermissionConfig(config);
        }
        updatePermissionsUi(prefs);
    }

    private void loadChatHistory() {
        // A running row belongs to a process that no longer owns this newly
        // created AgentManager. Keep its final local checkpoint and never
        // silently send the request again.
        historyManager.interruptRunningOperations(
                sc_id,
                activeThreadId,
                getString(R.string.chat_run_interrupted_local));
        List<ChatMessage> savedMessages = historyManager.loadHistory(sc_id, activeThreadId);

        if (savedMessages != null && !savedMessages.isEmpty()) {
            // Defensive reset: this method is also used after thread changes and
            // must never append a second copy of an already loaded conversation.
            messages.clear();
            boolean recoveredInterruptedRun = false;
            for (ChatMessage saved : savedMessages) {
                if (saved != null && saved.isStreaming()) {
                    recoverInterruptedMessage(saved);
                    recoveredInterruptedRun = true;
                }
                messages.add(saved);
            }
            renderLatestMessages(true);
            if (recoveredInterruptedRun) {
                saveChatHistoryNow();
            }
        } else {
            // Se nÃ£o tem histÃ³rico, adicionar mensagem de boas-vindas
            addWelcomeMessage();
        }
        updateThreadSummary();
        updateChangedFilesSummary();
        refreshSecondaryPanels();
        restoreLocalCompactionState();
    }

    private void recoverInterruptedMessage(ChatMessage message) {
        String recovery = getString(R.string.chat_run_interrupted_local);
        String partial = message.getDisplayContent() == null
                ? "" : message.getDisplayContent().trim();
        message.setStreaming(false);
        message.setStatus(recovery);
        if (message.isTool()) {
            message.setToolRunning(false);
            message.setToolError(true);
            message.setToolResult(recovery);
        }
        if (ChatMessage.hasVisibleText(partial)) {
            message.setLlmContent(partial);
            if (!partial.contains(recovery)) {
                message.setDisplayContent(partial + "\n\n" + recovery);
            }
        } else {
            message.setDisplayContent(recovery);
            message.setLlmContent("[Local Android run interrupted before a final response]");
        }
    }

    private void restoreLocalCompactionState() {
        if (historyManager == null || agentManager == null
                || !ChatMessage.hasVisibleText(activeThreadId)) return;
        SqliteChatStorage.CompactionState state =
                historyManager.loadCompactionState(sc_id, activeThreadId);
        agentManager.restoreCompactionState(state.summary, state.compactedUntil);
    }

    private void resetLocalCompactionState() {
        if (historyManager != null && ChatMessage.hasVisibleText(activeThreadId)) {
            historyManager.clearCompactionState(sc_id, activeThreadId);
        }
        if (agentManager != null) {
            agentManager.restoreCompactionState("", 0);
        }
    }

    private void addWelcomeMessage() {
        HashMap<String, Object> projectInfo = ProjectManager.b(sc_id);
        String projectName = projectInfo != null ? MapUtils.c(projectInfo, "my_ws_name") : getString(R.string.chat_default_project_name);
        String welcomeMessage = getString(R.string.chat_welcome_message, projectName);
        ChatMessage welcomeMsg = new ChatMessage(welcomeMessage, false, System.currentTimeMillis());
        messages.add(welcomeMsg);
        renderLatestMessages(true);
    }

    private void saveChatHistory() {
        if (historyManager != null && sc_id != null) {
            long startedAt = System.currentTimeMillis();
            historyManager.saveHistoryAsync(sc_id, activeThreadId, messages);
            historySaveCount++;
            historySaveTotalMs += System.currentTimeMillis() - startedAt;
        }
    }

    private void notifyMessageChanged(ChatMessage message) {
        int index = messages.indexOf(message);
        if (index != -1) {
            messageAdapter.notifyMessageChangedAt(index);
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
            boolean followLatest = chatMessagesFragment == null
                    || chatMessagesFragment.isAtBottom();
            streamUiRefreshCount++;
            notifyMessageChanged(message);
            if (followLatest) {
                scrollToBottom();
            }
            persistStreamingCheckpoint(false);
        }
    }

    private void persistStreamingCheckpoint(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastStreamCheckpointAtMs < STREAM_CHECKPOINT_INTERVAL_MS) return;
        lastStreamCheckpointAtMs = now;
        if (historyManager == null || !ChatMessage.hasVisibleText(currentLocalRunThreadId)) return;
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage candidate = messages.get(index);
            if (candidate != null && candidate.isStreaming()) {
                historyManager.saveMessageAtOrdinal(
                        sc_id, currentLocalRunThreadId, index, candidate);
                break;
            }
        }
        historyManager.updateOperation(
                sc_id,
                currentLocalRunThreadId,
                currentLocalOperationId,
                "running",
                currentRunStatus);
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
    }

    private void beginLocalOperation(@Nullable String operationId, String requestText) {
        if (!ChatMessage.hasVisibleText(operationId) || historyManager == null
                || !ChatMessage.hasVisibleText(activeThreadId)) return;
        currentLocalOperationId = operationId.trim();
        currentLocalRunThreadId = activeThreadId;
        lastStreamCheckpointAtMs = 0L;
        String status = ChatMessage.hasVisibleText(currentRunStatus)
                ? currentRunStatus : getString(R.string.chat_status_thinking);
        historyManager.beginOperation(
                sc_id,
                currentLocalRunThreadId,
                currentLocalOperationId,
                requestText,
                status);
        // The user message and initial thinking row are durable before the app
        // can move to the background.
        saveChatHistoryNow();
        ChatRunForegroundService.start(
                this,
                sc_id,
                currentLocalRunThreadId,
                currentLocalOperationId,
                status);
    }

    private void finishLocalOperation() {
        if (!ChatMessage.hasVisibleText(currentLocalOperationId)) return;
        persistStreamingCheckpoint(true);
        saveChatHistoryNow();
        String state = currentRunCancelled
                ? "cancelled" : currentRunFailed ? "failed" : "completed";
        historyManager.updateOperation(
                sc_id,
                currentLocalRunThreadId,
                currentLocalOperationId,
                state,
                state);
        ChatRunForegroundService.stop(this, currentLocalOperationId);
        currentLocalOperationId = "";
        currentLocalRunThreadId = "";
        lastStreamCheckpointAtMs = 0L;
    }

    private void interruptLocalOperation() {
        if (!ChatMessage.hasVisibleText(currentLocalOperationId)) return;
        persistStreamingCheckpoint(true);
        historyManager.updateOperation(
                sc_id,
                currentLocalRunThreadId,
                currentLocalOperationId,
                "interrupted",
                getString(R.string.chat_run_interrupted_local));
        ChatRunForegroundService.stop(this, currentLocalOperationId);
        currentLocalOperationId = "";
        currentLocalRunThreadId = "";
        lastStreamCheckpointAtMs = 0L;
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
        saveAndSend.setPaddingRelative(0, 0, dp(8), 0);
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
        save.setPaddingRelative(dp(8), 0, 0, 0);
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
        resetLocalCompactionState();
        messageAdapter.notifyMessageChangedAt(position);
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
        resetLocalCompactionState();
        messageAdapter.notifyFullListChanged();
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
        resetLocalCompactionState();
        messageAdapter.notifyFullListChanged();
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
        currentRunStartedAtMs = SystemClock.elapsedRealtime();
        currentRunFailed = false;
        currentRunCancelled = false;
        agentManager.continueFromExistingMessage(sourceMessage);
        beginLocalOperation(
                agentManager.getCurrentOperationId(),
                sourceMessage == null ? "" : sourceMessage.getDisplayContent());
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
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.chat_clipboard_label), text));
            Toast.makeText(this, R.string.kelivo_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteMessageAtPosition(int position) {
        if (isProcessing || position < 0 || position >= messages.size()) {
            return;
        }
        ChatMessage removed = messages.remove(position);
        resetLocalCompactionState();
        messageAdapter.notifyFullListChanged();
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
            ChatFlowLogger.event("ui", "send_blocked", "already_processing");
            Toast.makeText(this, R.string.chat_wait_processing, Toast.LENGTH_SHORT).show();
            return;
        }

        List<ChatReference> stagingSelections = new ArrayList<>(pendingReferences);
        if (!validatePlanForSend(stagingSelections)) {
            return;
        }

        // Verificar rate limiting - intervalo mÃ­nimo entre mensagens
        long currentTime = System.currentTimeMillis();
        long timeSinceLastMessage = currentTime - lastMessageTime;

        if (!skipRateLimit && timeSinceLastMessage < MIN_MESSAGE_INTERVAL_MS && lastMessageTime > 0) {
            ChatFlowLogger.event("ui", "send_blocked", "rate_limit");
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
        List<ChatReference> imageReferences = ChatReferenceManager.getImageReferences(stagingSelections);

        if (!imageReferences.isEmpty()) {
            SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
            String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
            String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
            if (!KelivoModelSheetAdapter.supportsImageInput(this, provider, model)) {
                Toast.makeText(this,
                        R.string.chat_model_no_image_input,
                        Toast.LENGTH_LONG).show();
                setInputEnabled(true);
                showProgress(false);
                isProcessing = false;
                return;
            }
        }

        SharedPreferences analyticsPrefs = AiChatSettingsHelper.prefs(this);
        AxionAnalytics.logEvent(
                this,
                AxionAnalytics.Events.CHAT_MESSAGE_SENT,
                AxionAnalytics.params(
                        AxionAnalytics.Params.MODE,
                        AiChatSettingsHelper.getChatMode(analyticsPrefs),
                        AxionAnalytics.Params.SOURCE,
                        skipRateLimit ? "automatic_repair" : "user",
                        AxionAnalytics.Params.HAS_TEXT,
                        message != null && !message.trim().isEmpty(),
                        AxionAnalytics.Params.HAS_ATTACHMENTS,
                        !stagingSelections.isEmpty()));
        currentRunStartedAtMs = SystemClock.elapsedRealtime();
        currentRunFailed = false;
        currentRunCancelled = false;

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
        ChatFlowLogger.event("ui", "message_sent", "chars=" + outgoingMessage.length()
                + ", refs=" + stagingSelections.size() + ", automatic=" + skipRateLimit);
        // ContextBuilder runs in AgentManager's background context thread. Passing
        // null here avoids opening content:// streams and walking referenced
        // folders on Android's main thread.
        agentManager.processUserMessage(outgoingMessage, null, stagingSelections);
        beginLocalOperation(agentManager.getCurrentOperationId(), outgoingMessage);
        clearPendingReferences();
    }

    private boolean validatePlanForSend(List<ChatReference> stagingSelections) {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
        String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");

        if (!VoidPortSettings.isProviderAllowedByPlan(prefs, provider)) {
            showPlanRestriction(R.string.plan_provider_not_available);
            updateModelUI();
            return false;
        }
        if (!AiChatSettingsHelper.isCurrentSelectionValid(prefs, provider, model)) {
            Toast.makeText(this, R.string.chat_no_models_available, Toast.LENGTH_LONG).show();
            updateModelUI();
            return false;
        }

        return true;
    }

    private void showPlanRestriction(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
    }

    private void setInputEnabled(boolean enabled) {
        editTextMessage.setEnabled(enabled);
        updateSendButtonState();
        if (btnAttach != null) btnAttach.setEnabled(enabled);
        if (btnAttach != null) btnAttach.setAlpha(enabled ? 1f : 0.55f);
    }

    private void showAttachMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.chat_attach_project_reference));
        popup.getMenu().add(0, 3, 1, getString(R.string.chat_attach_reference_file));
        popup.getMenu().add(0, 2, 2, getString(R.string.chat_attach_reference_image));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showReferencePicker(false);
                return true;
            }
            if (item.getItemId() == 2) {
                pickReferenceImage();
                return true;
            }
            if (item.getItemId() == 3) {
                pickReferenceFile();
                return true;
            }
            return false;
        });
        popup.show();
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
            String query = search.getText() == null
                    ? ""
                    : search.getText().toString().trim().toLowerCase(Locale.US);
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
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        String provider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String model = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");
        if (!KelivoModelSheetAdapter.supportsImageInput(this, provider, model)) {
            Toast.makeText(this,
                    R.string.chat_model_no_image_input,
                    Toast.LENGTH_LONG).show();
            return;
        }
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
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);

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
        AxionAnalytics.logEvent(
                this,
                AxionAnalytics.Events.CHAT_REFERENCE_ADDED,
                AxionAnalytics.params(
                        AxionAnalytics.Params.ATTACHMENT_TYPE,
                        analyticsReferenceType(reference)));
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
        updateSendButtonState();
        if (textSelectedContext == null) {
            return;
        }
        List<ChatReference> imageReferences =
                ChatReferenceManager.getImageReferences(pendingReferences);
        if (pendingReferences.isEmpty() || imageReferences.size() == pendingReferences.size()) {
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

        ShapeableImageView image = new ShapeableImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setAllCornerSizes(dp(10))
                .build());
        image.setStrokeColor(ColorStateList.valueOf(getColor(R.color.chat_divider)));
        image.setStrokeWidth(dp(1));
        ChatImageThumbnailLoader.load(
                image,
                reference == null ? null : reference.getUri(),
                dp(64),
                R.drawable.kelivo_lucide_image);
        frame.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        TextView remove = new TextView(this);
        remove.setText(R.string.chat_remove_reference_button);
        remove.setTextColor(0xFFFFFFFF);
        remove.setTextSize(11);
        remove.setGravity(Gravity.CENTER);
        remove.setContentDescription(getString(R.string.chat_remove_reference_image));
        remove.setBackgroundResource(R.drawable.bg_kelivo_image_remove);
        remove.setOnClickListener(v -> removePendingReference(reference));
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                dp(20), dp(20), Gravity.TOP | Gravity.END);
        removeParams.setMargins(0, dp(3), dp(3), 0);
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
        item.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
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
        // While a run is in progress, the send button must be hidden and
        // replaced by the stop/cancel button — never both visible at once.
        // Previously only btnCancelRun's visibility was toggled here, so
        // btnSend stayed visible (just disabled/dimmed) the whole time,
        // making both controls show together during a run.
        if (btnSend != null) {
            btnSend.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (btnCancelRun != null) {
            btnCancelRun.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.chat_voice_prompt));
        try {
            startActivityForResult(intent, 1001);
        } catch (Exception e) {
            Toast.makeText(this, R.string.chat_voice_recognition_unsupported, Toast.LENGTH_SHORT).show();
        }
    }

    private void showUserFacingAiError(UserFacingError error, String requestId) {
        currentRunFailed = true;
        currentDebugMessage = null;
        UserFacingError safeError = error == null
                ? UserFacingError.builder()
                        .title(getString(R.string.chat_error_could_not_complete_title))
                        .message(getString(R.string.chat_error_operation_generic))
                        .canRetry(true)
                        .build()
                : error;

        StringBuilder visibleMessage = new StringBuilder()
                .append(safeError.getTitle()).append("\n")
                .append(safeError.getMessage());
        if (safeError.canRetry()) {
            visibleMessage.append("\n\n").append(getString(R.string.common_retry))
                    .append(": envie a mensagem novamente.");
        }
        if (requestId != null && !requestId.trim().isEmpty()) {
            visibleMessage.append("\n\n")
                    .append(getString(R.string.chat_diagnostic_code_label, requestId.trim()));
        }
        if (safeError.getTechnicalCode() != null
                && !safeError.getTechnicalCode().trim().isEmpty()) {
            visibleMessage.append("\n")
                    .append(getString(R.string.chat_technical_code_label,
                            safeError.getTechnicalCode().trim()));
        }
        if (BuildConfig.DEBUG && safeError.getTechnicalDetails() != null
                && !safeError.getTechnicalDetails().trim().isEmpty()) {
            visibleMessage.append("\n\n").append(safeError.getTechnicalDetails().trim());
        }
        ChatMessage errorMsg = new ChatMessage(visibleMessage.toString(), false, System.currentTimeMillis());
        errorMsg.setStatus(getString(R.string.chat_status_error));
        messages.add(errorMsg);
        renderLatestMessages(true);
        saveChatHistory();
        updateThreadSummary();
        refreshSecondaryPanels();

        ChatFlowLogger.event("ui", "error_in_chat", "requestId=" + requestId
                + ", code=" + safeError.getTechnicalCode() + ", retry=" + safeError.canRetry());
    }

    private void updateRunStatus(String status) {
        String safeStatus = status == null ? "" : status.trim();
        boolean statusChanged = !safeStatus.equals(currentRunStatus);
        currentRunStatus = safeStatus;
        if (statusChanged && ChatMessage.hasVisibleText(currentLocalOperationId)) {
            // O checkpoint de streaming já persiste a operação a cada 750 ms.
            // Aqui basta atualizar a notificação quando o texto realmente muda;
            // gravar SQLite a cada status mantinha I/O síncrono na main thread.
            ChatRunForegroundService.update(this, currentLocalOperationId, safeStatus);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(null);
        }
        // O andamento aparece apenas no banner de status do compositor. O texto é
        // derivado do RunStatusReducer (TurnStarted mantém THINKING; ações de
        // ferramenta/aprovação retornam exatamente uma vez). Quando o status é
        // vazio (READY/COMPLETED) o banner esconde junto com os pontos.
        if (layoutRunStatus != null && textRunStatus != null && runStatusDots != null) {
            if (ChatMessage.hasVisibleText(safeStatus)) {
                textRunStatus.setText(safeStatus);
                layoutRunStatus.setVisibility(View.VISIBLE);
                runStatusDots.startAnimation();
            } else {
                runStatusDots.stopAnimation();
                layoutRunStatus.setVisibility(View.GONE);
            }
        }
        // Status de streaming muda muitas vezes por segundo. Atualizar todos os
        // painéis aqui reconstruía Artefatos fora da tela, recalculava diffs e
        // decodificava imagens na main thread, causando ANR. O plano é o único
        // painel auxiliar que realmente precisa acompanhar esse estado.
        if (statusChanged && chatPlanFragment != null) {
            chatPlanFragment.setRunState(isProcessing, currentRunStatus);
        }
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
        }
        if (chatPlanFragment != null) {
            chatPlanFragment.setMessages(messages);
            chatPlanFragment.setRunState(isProcessing, currentRunStatus);
        }
    }

    private void bindPagingThread(boolean scrollAfterLoad) {
        if (historyManager == null || messageAdapter == null || sc_id == null
                || !ChatMessage.hasVisibleText(activeThreadId)) {
            return;
        }
        if (activePagingData != null) {
            activePagingData.removeObservers(this);
        }
        messageAdapter.submitData(getLifecycle(), PagingData.empty());
        activePagingData = historyManager.pagingData(sc_id, activeThreadId);
        activePagingData.observe(this, pagingData -> {
            messageAdapter.submitData(getLifecycle(), pagingData);
            if (scrollAfterLoad && chatMessagesFragment != null) {
                chatMessagesFragment.scrollToBottom();
            }
        });
    }

    private void renderLatestMessages(boolean scrollToBottom) {
        if (messageAdapter == null || historyManager == null || sc_id == null
                || !ChatMessage.hasVisibleText(activeThreadId)) {
            return;
        }
        // SQLite is the UI source of truth. The write invalidates the active
        // PagingSource when committed; the adapter then diffs only loaded rows.
        historyManager.saveHistoryAsync(sc_id, activeThreadId, messages);
        if (scrollToBottom) {
            scrollToBottom();
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
            renderLatestMessages(true);
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
                messageAdapter.notifyMessageChangedAt(index);
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
                messageAdapter.notifyFullListChanged();
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
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
            return;
        }
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
                appendDebugMessage(getString(R.string.chat_debug_enabled_message));
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

    private void showModelCatalogDialog() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        RecyclerView recyclerView = new RecyclerView(this);
        int pad = dp(16);
        recyclerView.setPadding(pad, pad, pad, pad);
        recyclerView.setClipToPadding(false);
        List<String> lines = buildModelCatalogLines(prefs);
        ChatSimpleTextAdapter adapter = new ChatSimpleTextAdapter();
        adapter.setItems(lines);
        recyclerView.setAdapter(adapter);
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_model_catalog_title)
                .setView(recyclerView)
                .setPositiveButton(R.string.chat_model_refresh_local, (dialog, which) -> refreshLocalModels())
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
            String providerState = configured
                    ? getString(R.string.chat_model_provider_state_ok)
                    : getString(R.string.chat_model_provider_state_setup);
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
            AxionAnalytics.logEvent(this, AxionAnalytics.Events.CHAT_THREAD_RENAMED);
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
        if (isProcessing && thread.id.equals(activeThreadId)) {
            Toast.makeText(this, R.string.chat_wait_processing, Toast.LENGTH_SHORT).show();
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
        if (isProcessing && thread.id.equals(activeThreadId)) {
            Toast.makeText(this, R.string.chat_wait_processing, Toast.LENGTH_SHORT).show();
            return;
        }
        List<ChatReference> removedReferences = collectMessageReferences(
                historyManager.loadHistory(sc_id, thread.id));
        boolean wasActive = thread.id.equals(activeThreadId);
        historyManager.deleteThread(sc_id, thread.id);
        AxionAnalytics.logEvent(this, AxionAnalytics.Events.CHAT_THREAD_DELETED);
        if (wasActive) {
            List<ChatThread> remaining = historyManager.getThreads(sc_id);
            String nextThreadId = remaining.isEmpty()
                    ? historyManager.createThread(sc_id)
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
        saveChatHistoryNow();
        String threadId = historyManager.createThread(sc_id);
        AxionAnalytics.logEvent(
                this,
                AxionAnalytics.Events.CHAT_THREAD_CREATED,
                AxionAnalytics.params(
                        AxionAnalytics.Params.THREAD_COUNT,
                        historyManager.getThreads(sc_id).size()));
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
            // Thread switching is a persistence boundary: commit the old thread
            // synchronously so a lifecycle shutdown cannot cancel the snapshot.
            saveChatHistoryNow();
        }
        if (agentManager != null) {
            agentManager.resetConversationState();
        }
        activeThreadId = threadId;
        historyManager.setCurrentThreadId(sc_id, activeThreadId);
        AxionAnalytics.logEvent(this, AxionAnalytics.Events.CHAT_THREAD_OPENED);
        messages.clear();
        bindPagingThread(true);
        loadChatHistory();
        loadProjectInfo();
        updateModelUI();
        updateChatModeUI();
        updateKelivoHeader();
        refreshDrawerThreads();
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
            historyManager.clearCompactionState(sc_id, activeThreadId);
        }
        if (agentManager != null) {
            agentManager.resetConversationState();
            agentManager.restoreCompactionState("", 0);
        }
        messages.clear();
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
        currentRunCancelled = true;
        AxionAnalytics.logEvent(this, AxionAnalytics.Events.CHAT_RUN_CANCELLED);
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
        AxionAnalytics.logEvent(
                this,
                AxionAnalytics.Events.CHAT_EXPORTED,
                AxionAnalytics.params(
                        AxionAnalytics.Params.MESSAGE_COUNT,
                        messages.size()));
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
        renderLatestMessages(true);
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
        if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            return false;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return true;
        } catch (SecurityException ignored) {
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



    private static String analyticsReferenceType(ChatReference reference) {
        if (reference == null) {
            return "unknown";
        }
        switch (reference.getType()) {
            case ChatReference.TYPE_IMAGE:
                return "image";
            case ChatReference.TYPE_FOLDER:
                return "folder";
            case ChatReference.TYPE_CODE_SELECTION:
                return "code_selection";
            case ChatReference.TYPE_FILE:
            default:
                return reference.isExternalFile() ? "external_file" : "project_file";
        }
    }






    @Override
    protected void onStart() {
        super.onStart();
        applyPlanUi();
    }

    private void applyPlanUi() {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(this);
        AiChatSettingsHelper.ensureValidCurrentSelection(prefs);
        if (btnAttach != null) {
            btnAttach.setVisibility(View.VISIBLE);
        }
        updateModelUI();
        updatePendingReferencesUi();
        updateComposerToolUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPlanUi();
        // O servidor é a fonte de verdade do plano e da disponibilidade. Revalide
        // provedores/modelos sempre que a tela principal voltar ao primeiro plano.
        refreshManagedAiState(false);
        // FragmentStatePagerAdapter can restore the old Fragment instances after
        // process recreation. Rebind every page to this Activity's live state so
        // the restored RecyclerView does not come back with a null adapter.
        rebindChatPages();
        restorePersistedMessagesIfNeeded();
        if (chatMessagesFragment != null) {
            chatMessagesFragment.refreshMessages();
        }
        if (chatDiffFragment != null) {
            chatDiffFragment.refreshDiffs();
        }
    }

    @Override
    protected void onStop() {
        // Persist before the app is backgrounded. onDestroy is not guaranteed
        // when Android later reclaims the process, and asynchronous work can be
        // cancelled during that transition.
        saveChatHistoryNow();
        persistStreamingCheckpoint(true);
        Log.d("ChatActivity", "Chat saved onStop: thread=" + activeThreadId
                + ", messages=" + (messages == null ? 0 : messages.size()));
        super.onStop();
    }

    private void saveChatHistoryNow() {
        if (historyManager != null && sc_id != null && messages != null) {
            // Persist messages only; active-thread selection is stored separately.
            historyManager.saveHistory(sc_id, activeThreadId, messages);
        }
    }

    /** Builds the project's APK from the chat, streaming the build log to its own page. */
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
                    if (chatCompileLogFragment != null) {
                        chatCompileLogFragment.appendLine(line);
                    }
                });
            }

            @Override
            public void onCompileFinished(boolean success, String message, String apkPath) {
                runOnUiThread(() -> {
                    if (chatCompileLogFragment != null) {
                        chatCompileLogFragment.appendLine("");
                        chatCompileLogFragment.appendLine(message);
                    }
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
        File apkFile = apkPath == null || apkPath.trim().isEmpty() ? null : new File(apkPath);
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, R.string.chat_compile_apk_missing, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(getApplicationContext(),
                getApplicationContext().getPackageName() + ".provider", apkFile);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    private void showCompileLogPage() {
        if (chatViewPager != null) {
            chatViewPager.setCurrentItem(4, true);
        }
    }

    /** Called by ChatPagerAdapter for both newly-created and restored pages. */
    void onChatPageInstantiated(int position, androidx.fragment.app.Fragment fragment) {
        if (fragment == null) return;
        switch (position) {
            case 0:
                if (fragment instanceof ChatMessagesFragment) {
                    chatMessagesFragment = (ChatMessagesFragment) fragment;
                    chatMessagesFragment.setAdapter(messageAdapter);
                }
                break;
            case 1:
                if (fragment instanceof ChatDiffFragment) {
                    chatDiffFragment = (ChatDiffFragment) fragment;
                }
                break;
            case 2:
                if (fragment instanceof ChatArtifactsFragment) {
                    chatArtifactsFragment = (ChatArtifactsFragment) fragment;
                    chatArtifactsFragment.setMessages(messages);
                }
                break;
            case 3:
                if (fragment instanceof ChatPlanFragment) {
                    chatPlanFragment = (ChatPlanFragment) fragment;
                    chatPlanFragment.setMessages(messages);
                    chatPlanFragment.setRunState(isProcessing, currentRunStatus);
                }
                break;
            case 4:
                if (fragment instanceof ChatCompileLogFragment) {
                    chatCompileLogFragment = (ChatCompileLogFragment) fragment;
                }
                break;
            default:
                break;
        }
    }

    ChatMessageAdapter getMessageAdapterForFragments() {
        return messageAdapter;
    }

    List<ChatMessage> getMessagesForFragments() {
        return messages;
    }

    boolean isChatProcessingForFragments() {
        return isProcessing;
    }

    String getRunStatusForFragments() {
        return currentRunStatus;
    }

    private void rebindChatPages() {
        if (chatMessagesFragment != null) {
            chatMessagesFragment.setAdapter(messageAdapter);
        }
        if (chatArtifactsFragment != null) {
            chatArtifactsFragment.setMessages(messages);
        }
        if (chatPlanFragment != null) {
            chatPlanFragment.setMessages(messages);
            chatPlanFragment.setRunState(isProcessing, currentRunStatus);
        }
    }

    private void restorePersistedMessagesIfNeeded() {
        if (isProcessing || historyManager == null || messages == null
                || !messages.isEmpty() || !ChatMessage.hasVisibleText(activeThreadId)) {
            return;
        }
        List<ChatMessage> persisted = historyManager.loadHistory(sc_id, activeThreadId);
        if (persisted == null || persisted.isEmpty()) {
            return;
        }
        // Do not overwrite a newer in-memory conversation. This recovery path is
        // only for a view/process recreation that left the visible list empty or
        // shorter than the lifecycle snapshot written in onStop().
        if (!persisted.isEmpty()) {
            messages.clear();
            messages.addAll(persisted);
            renderLatestMessages(false);
            refreshSecondaryPanels();
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroying = true;

        List<ChatReference> abandonedReferences = new ArrayList<>(pendingReferences);
        pendingReferences.clear();
        releaseReferenceGrantsIfUnused(abandonedReferences);
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        flushStreamingMessageUpdate();
        // Do not queue the last save and immediately cancel that queue below.
        // This is the last reliable lifecycle point before the process can die.
        saveChatHistoryNow();
        streamUiHandler.removeCallbacksAndMessages(null);
        if (isProcessing) {
            interruptLocalOperation();
        }
        if (agentManager != null) {
            agentManager.release();
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
