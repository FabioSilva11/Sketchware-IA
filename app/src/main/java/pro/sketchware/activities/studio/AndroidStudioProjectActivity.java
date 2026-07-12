package pro.sketchware.activities.studio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.gson.Gson;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.lC;
import a.a.a.ProjectBuilder;
import a.a.a.yq;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.build.BuildSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import mod.jbk.diagnostic.MissingFileException;
import mod.hey.studios.code.SrcCodeEditor;
import mod.pranav.dependency.resolver.DependencyResolver;
import dev.aldi.sayuti.editor.manage.LibraryDownloaderDialogFragment;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import org.cosmic.ide.dependency.resolver.api.Artifact;
import pro.sketchware.activities.chat.port.VoidPortAiAutocompleteLanguage;
import pro.sketchware.activities.chat.port.GitHubProjectSyncService;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityAndroidStudioProjectBinding;
import pro.sketchware.databinding.ItemStudioFileTreeBinding;
import pro.sketchware.util.ProjectPathResolver;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class AndroidStudioProjectActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private static final int MENU_SAVE = 1;
    private static final int MENU_FORMAT = 2;
    private static final int MENU_THEME = 3;
    private static final int MENU_UNDO = 4;
    private static final int MENU_REDO = 5;
    private static final int MENU_BUILD = 6;
    private static final int MENU_ERRORS = 9;
    private static final int MENU_NEW_FILE = 10;
    private static final int MENU_RENAME = 11;
    private static final int MENU_DELETE = 12;
    private static final int MENU_ADD_RESOURCE = 13;
    private static final int MENU_ADD_ICON = 14;
    private static final int MENU_TEXT_COLOR = 15;
    private static final int MENU_OPEN = 16;
    private static final int MENU_COPY_PATH = 17;
    private static final int MENU_DEPENDENCIES = 18;
    private static final int MENU_SEARCH = 19;
    private static final int MENU_SYNTAX = 20;
    private static final int MENU_PREVIOUS_POSITION = 21;
    private static final int MENU_NEXT_POSITION = 22;
    private static final int MENU_JUMP_TO_LINE = 23;
    private static final int MENU_WORD_WRAP = 24;
    private static final int MENU_READ_ONLY = 25;
    private static final int MENU_SMOOTH_MODE = 26;
    private static final int MENU_PREFERENCES = 27;
    private static final int MENU_GITHUB = 28;
    private static final int REQUEST_PICK_STUDIO_ICON = 23051;
    private static final int MAX_TREE_NODES = 900;
    private static final int MAX_INITIAL_SCAN_FILES = 1200;
    private static final int MAX_NAVIGATION_HISTORY = 80;
    private static final String EDITOR_PREFS = "hsce";
    private static final String EDITOR_PREF_PREFIX = "studio";
    private static final long MAX_OPEN_BYTES = 1_500_000L;
    private static final String GRADLE_DEPENDENCY_CONFIG =
            "(?:implementation|api|compileOnly|runtimeOnly|annotationProcessor|kapt|ksp|debugImplementation|releaseImplementation|coreLibraryDesugaring)";
    private static final String FIREBASE_ANALYTICS_DEPENDENCY = "com.google.firebase:firebase-analytics:23.2.0";
    private static final Pattern GRADLE_STRING_DEPENDENCY_PATTERN = Pattern.compile(
            "\\b" + GRADLE_DEPENDENCY_CONFIG + "\\s*(?:\\(|\\s)\\s*[\"']([^\"']+:[^\"']+:[^\"']+)[\"']"
    );
    private static final Pattern GRADLE_MAP_GROOVY_DEPENDENCY_PATTERN = Pattern.compile(
            "\\b" + GRADLE_DEPENDENCY_CONFIG + "\\s*(?:\\(|\\s)\\s*group\\s*:\\s*[\"']([^\"']+)[\"']\\s*,\\s*name\\s*:\\s*[\"']([^\"']+)[\"']\\s*,\\s*version\\s*:\\s*[\"']([^\"']+)[\"']"
    );
    private static final Pattern GRADLE_MAP_KTS_DEPENDENCY_PATTERN = Pattern.compile(
            "\\b" + GRADLE_DEPENDENCY_CONFIG + "\\s*\\(\\s*group\\s*=\\s*[\"']([^\"']+)[\"']\\s*,\\s*name\\s*=\\s*[\"']([^\"']+)[\"']\\s*,\\s*version\\s*=\\s*[\"']([^\"']+)[\"']"
    );

    private ActivityAndroidStudioProjectBinding binding;
    private ActionBarDrawerToggle drawerToggle;
    private FileTreeAdapter fileTreeAdapter;
    private final List<FileNode> visibleNodes = new ArrayList<>();
    private final List<OpenFileTab> openFileTabs = new ArrayList<>();
    private final List<EditorNavigationEntry> navigationHistory = new ArrayList<>();
    private final Set<String> expandedDirs = new HashSet<>();
    private CodeEditor activeEditor;
    private BottomSheetBehavior<LinearLayout> outputSheetBehavior;
    private SharedPreferences editorPrefs;

    private String scId;
    private File projectRoot;
    private File currentFile;
    private File selectedNodeFile;
    private String lastSavedContent = "";
    private String lastBuildErrors = "";
    private String treeQuery = "";
    private int discoveredFileCount;
    private boolean changingFile;
    private boolean currentFileEditable;
    private boolean showingImage;
    private boolean buildRunning;
    private boolean selectingFileTab;
    private boolean readOnlyMode;
    private boolean smoothMode;
    private boolean restoringNavigationPosition;
    private boolean searchCaseSensitive;
    private boolean searchWholeWord;
    private int navigationHistoryIndex = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        HashMap<String, Object> project = scId == null ? null : lC.b(scId);
        String projectName = valueOf(project, "my_ws_name", getString(R.string.studio_editor_title));
        String appName = valueOf(project, "my_app_name", projectName);

        editorPrefs = getSharedPreferences(EDITOR_PREFS, MODE_PRIVATE);
        readOnlyMode = editorPrefs.getBoolean(EDITOR_PREF_PREFIX + "_ro", false);
        smoothMode = editorPrefs.getBoolean(EDITOR_PREF_PREFIX + "_smooth", true);
        searchCaseSensitive = editorPrefs.getBoolean(EDITOR_PREF_PREFIX + "_search_case", false);
        searchWholeWord = editorPrefs.getBoolean(EDITOR_PREF_PREFIX + "_search_word", false);

        binding = ActivityAndroidStudioProjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(projectName, appName);
        setupEditor();
        setupFileTree();
        loadProject(projectName);
        SketchwareUtil.toast(getString(R.string.studio_project_selected));
    }

    @Override
    public void onResume() {
        super.onResume();
        logAnalyticsScreenView();
    }

    private void logAnalyticsScreenView() {
        if (mAnalytics == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "Android Studio Project");
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "AndroidStudioProjectActivity");
        bundle.putString("project_kind", "android_studio");
        mAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
    }

    private void logStudioAction(String action) {
        if (mAnalytics == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("studio_action", action);
        bundle.putString("project_kind", "android_studio");
        if (currentFile != null) {
            bundle.putString("file_extension", extensionOf(currentFile));
        }
        mAnalytics.logEvent("android_studio_action", bundle);
    }

    private void setupToolbar(String projectName, String appName) {
        binding.studioToolbar.setTitle(projectName);
        binding.studioToolbar.setSubtitle(appName);
        drawerToggle = new ActionBarDrawerToggle(
                this,
                binding.studioDrawerLayout,
                binding.studioToolbar,
                R.string.studio_action_files,
                R.string.studio_action_files
        );
        binding.studioDrawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        Menu menu = binding.studioToolbar.getMenu();
        addToolbarAction(menu, MENU_UNDO, R.string.studio_action_undo, R.drawable.ic_mtrl_undo);
        addToolbarAction(menu, MENU_REDO, R.string.studio_action_redo, R.drawable.ic_mtrl_redo);
        addToolbarAction(menu, MENU_FORMAT, R.string.studio_action_format, R.drawable.ic_mtrl_formattext);
        addToolbarAction(menu, MENU_THEME, R.string.studio_action_theme, R.drawable.ic_mtrl_palette);
        addToolbarAction(menu, MENU_BUILD, R.string.studio_action_build, R.drawable.ic_mtrl_run);
        addToolbarAction(menu, MENU_SAVE, R.string.studio_action_save, R.drawable.ic_mtrl_save);
        addToolbarAction(menu, MENU_SEARCH, R.string.studio_action_search, R.drawable.ic_mtrl_search, MenuItem.SHOW_AS_ACTION_NEVER);
        addToolbarAction(menu, MENU_SYNTAX, R.string.studio_action_syntax, R.drawable.ic_mtrl_code, MenuItem.SHOW_AS_ACTION_NEVER);
        addToolbarAction(menu, MENU_PREVIOUS_POSITION, R.string.studio_action_previous_position, R.drawable.ic_mtrl_arrow_left, MenuItem.SHOW_AS_ACTION_NEVER);
        addToolbarAction(menu, MENU_NEXT_POSITION, R.string.studio_action_next_position, R.drawable.ic_mtrl_arrow_right, MenuItem.SHOW_AS_ACTION_NEVER);
        addToolbarAction(menu, MENU_JUMP_TO_LINE, R.string.studio_action_jump_to_line, R.drawable.ic_format_list_numbered_grey600_24dp, MenuItem.SHOW_AS_ACTION_NEVER);
        addToolbarAction(menu, MENU_WORD_WRAP, R.string.studio_action_word_wrap, R.drawable.ic_mtrl_formattext, MenuItem.SHOW_AS_ACTION_NEVER)
                .setCheckable(true);
        addToolbarAction(menu, MENU_READ_ONLY, R.string.studio_action_read_only_mode, R.drawable.ic_mtrl_shield_lock, MenuItem.SHOW_AS_ACTION_NEVER)
                .setCheckable(true);
        addToolbarAction(menu, MENU_SMOOTH_MODE, R.string.studio_action_smooth_mode, R.drawable.ic_mtrl_history, MenuItem.SHOW_AS_ACTION_NEVER)
                .setCheckable(true);
        addToolbarAction(menu, MENU_PREFERENCES, R.string.studio_action_preferences, R.drawable.ic_mtrl_settings, MenuItem.SHOW_AS_ACTION_NEVER);
        addToolbarAction(menu, MENU_DEPENDENCIES, R.string.studio_action_dependencies, R.drawable.ic_mtrl_package, MenuItem.SHOW_AS_ACTION_NEVER);
        addToolbarAction(menu, MENU_GITHUB, "GitHub", R.drawable.ic_github, MenuItem.SHOW_AS_ACTION_NEVER);

        binding.studioToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SAVE) {
                logStudioAction("save");
                saveCurrentFile(true);
                return true;
            }
            if (item.getItemId() == MENU_BUILD) {
                logStudioAction("build");
                buildProject();
                return true;
            }
            if (item.getItemId() == MENU_ERRORS) {
                logStudioAction("errors");
                showCompileErrorGuide();
                return true;
            }
            if (item.getItemId() == MENU_NEW_FILE) {
                logStudioAction("new_file");
                showCreateFileDialog();
                return true;
            }
            if (item.getItemId() == MENU_RENAME) {
                logStudioAction("rename");
                showRenameDialog();
                return true;
            }
            if (item.getItemId() == MENU_DELETE) {
                logStudioAction("delete");
                showDeleteDialog();
                return true;
            }
            if (item.getItemId() == MENU_ADD_RESOURCE) {
                logStudioAction("add_resource");
                showAddResourceDialog();
                return true;
            }
            if (item.getItemId() == MENU_ADD_ICON) {
                logStudioAction("add_icon");
                pickIconResource();
                return true;
            }
            if (item.getItemId() == MENU_TEXT_COLOR) {
                logStudioAction("text_color");
                addTextColorToCurrentLayout();
                return true;
            }
            if (item.getItemId() == MENU_DEPENDENCIES) {
                logStudioAction("dependencies");
                showDependencyActions();
                return true;
            }
            if (item.getItemId() == MENU_GITHUB) {
                logStudioAction("github");
                showGitHubVersioningDialog();
                return true;
            }
            if (item.getItemId() == MENU_SEARCH) {
                logStudioAction("search");
                beginEditorSearch();
                return true;
            }
            if (item.getItemId() == MENU_SYNTAX) {
                logStudioAction("syntax");
                showSyntaxDialog();
                return true;
            }
            if (item.getItemId() == MENU_PREVIOUS_POSITION) {
                logStudioAction("previous_position");
                navigateEditorPosition(-1);
                return true;
            }
            if (item.getItemId() == MENU_NEXT_POSITION) {
                logStudioAction("next_position");
                navigateEditorPosition(1);
                return true;
            }
            if (item.getItemId() == MENU_JUMP_TO_LINE) {
                logStudioAction("jump_to_line");
                showJumpToLineDialog();
                return true;
            }
            if (item.getItemId() == MENU_WORD_WRAP) {
                logStudioAction("word_wrap");
                setWordWrapEnabled(!getActiveEditor().isWordwrap());
                return true;
            }
            if (item.getItemId() == MENU_READ_ONLY) {
                logStudioAction("read_only");
                setReadOnlyMode(!readOnlyMode);
                return true;
            }
            if (item.getItemId() == MENU_SMOOTH_MODE) {
                logStudioAction("smooth_mode");
                setSmoothMode(!smoothMode);
                return true;
            }
            if (item.getItemId() == MENU_PREFERENCES) {
                logStudioAction("preferences");
                showEditorPreferences();
                return true;
            }
            if (item.getItemId() == MENU_FORMAT) {
                logStudioAction("format");
                formatCurrentFile();
                return true;
            }
            if (item.getItemId() == MENU_THEME) {
                logStudioAction("theme");
                SrcCodeEditor.showSwitchThemeDialog(this, getActiveEditor(), (dialog, which) -> {
                    SrcCodeEditor.selectTheme(getActiveEditor(), which);
                    dialog.dismiss();
                });
                return true;
            }
            if (item.getItemId() == MENU_UNDO) {
                logStudioAction("undo");
                if (getActiveEditor().canUndo()) {
                    getActiveEditor().undo();
                }
                return true;
            }
            if (item.getItemId() == MENU_REDO) {
                logStudioAction("redo");
                if (getActiveEditor().canRedo()) {
                    getActiveEditor().redo();
                }
                return true;
            }
            return false;
        });
        updateToolbarMenus();
    }

    private MenuItem addToolbarAction(Menu menu, int id, int titleRes, int iconRes) {
        return addToolbarAction(menu, id, titleRes, iconRes, MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private MenuItem addToolbarAction(Menu menu, int id, int titleRes, int iconRes, int showAsAction) {
        MenuItem item = menu.add(Menu.NONE, id, Menu.NONE, titleRes);
        item.setIcon(iconRes);
        item.setShowAsAction(showAsAction);
        return item;
    }

    private MenuItem addToolbarAction(Menu menu, int id, String title, int iconRes, int showAsAction) {
        MenuItem item = menu.add(Menu.NONE, id, Menu.NONE, title);
        item.setIcon(iconRes);
        item.setShowAsAction(showAsAction);
        return item;
    }

    private void updateToolbarMenus() {
        if (binding == null) {
            return;
        }

        Menu menu = binding.studioToolbar.getMenu();
        boolean projectLoaded = projectRoot != null && projectRoot.isDirectory();
        boolean codeFileVisible = currentFile != null && currentFileEditable && !showingImage;
        boolean xmlFile = currentFile != null && currentFile.getName().toLowerCase(Locale.US).endsWith(".xml");
        boolean layoutXml = codeFileVisible && isLayoutXml(currentFile);
        File selected = getSelectedTarget();
        boolean canModifySelected = projectLoaded && selected != null && !selected.equals(projectRoot);

        setToolbarItem(menu, MENU_UNDO, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_REDO, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_SAVE, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_FORMAT, codeFileVisible && xmlFile, codeFileVisible && xmlFile);
        setToolbarItem(menu, MENU_THEME, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_TEXT_COLOR, layoutXml, layoutXml);
        setToolbarItem(menu, MENU_SEARCH, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_SYNTAX, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_PREVIOUS_POSITION, codeFileVisible, canNavigateEditorPosition(-1));
        setToolbarItem(menu, MENU_NEXT_POSITION, codeFileVisible, canNavigateEditorPosition(1));
        setToolbarItem(menu, MENU_JUMP_TO_LINE, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_WORD_WRAP, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_READ_ONLY, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_SMOOTH_MODE, codeFileVisible, codeFileVisible);
        setToolbarItem(menu, MENU_PREFERENCES, codeFileVisible, codeFileVisible);
        setToolbarItemChecked(menu, MENU_WORD_WRAP, codeFileVisible && getActiveEditor().isWordwrap());
        setToolbarItemChecked(menu, MENU_READ_ONLY, readOnlyMode);
        setToolbarItemChecked(menu, MENU_SMOOTH_MODE, smoothMode);

        setToolbarItem(menu, MENU_BUILD, projectLoaded, projectLoaded && !buildRunning);
        setToolbarItem(menu, MENU_ERRORS, false, false);
        setToolbarItem(menu, MENU_NEW_FILE, projectLoaded, projectLoaded);
        setToolbarItem(menu, MENU_ADD_RESOURCE, projectLoaded, projectLoaded);
        setToolbarItem(menu, MENU_ADD_ICON, projectLoaded, projectLoaded);
        setToolbarItem(menu, MENU_DEPENDENCIES, projectLoaded, projectLoaded);
        setToolbarItem(menu, MENU_GITHUB, projectLoaded, projectLoaded);
        setToolbarItem(menu, MENU_RENAME, canModifySelected, canModifySelected);
        setToolbarItem(menu, MENU_DELETE, canModifySelected, canModifySelected);
    }

    private void setToolbarItem(Menu menu, int itemId, boolean visible, boolean enabled) {
        MenuItem item = menu.findItem(itemId);
        if (item == null) {
            return;
        }
        item.setVisible(visible);
        item.setEnabled(enabled);
    }

    private void setToolbarItemChecked(Menu menu, int itemId, boolean checked) {
        MenuItem item = menu.findItem(itemId);
        if (item == null) {
            return;
        }
        item.setChecked(checked);
    }

    private void showGitHubVersioningDialog() {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            SketchwareUtil.toast("Projeto Android Studio não encontrado");
            return;
        }
        SharedPreferences prefs = getSharedPreferences(VoidPortSettings.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getString(VoidPortSettings.PREF_GITHUB_TOKEN, "").trim().isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("GitHub")
                    .setMessage("Configure primeiro o token em GitHub Settings.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        GitHubProjectSyncService.Binding saved = GitHubProjectSyncService.loadBinding(prefs, scId);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                getResources().getDisplayMetrics());
        form.setPadding(pad, 0, pad, 0);
        EditText owner = githubField("Usuário ou organização", saved.owner);
        EditText repo = githubField("Repositório", saved.repo.isEmpty() ? safeRepositoryName() : saved.repo);
        EditText branch = githubField("Branch", saved.branch);
        EditText message = githubField("Mensagem do commit", "Atualiza projeto Android Studio");
        form.addView(owner);
        form.addView(repo);
        form.addView(branch);
        form.addView(message);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Versionar no GitHub")
                .setMessage("Somente o projeto Android Studio será enviado. Builds, caches e segredos serão ignorados.")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Criar repositório", null)
                .setPositiveButton(saved.isValid() ? "Commit e push" : "Conectar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String repository = repo.getText().toString().trim();
                if (repository.isEmpty()) {
                    repo.setError("Informe o repositório");
                    return;
                }
                setGitHubDialogBusy(dialog, true, "Criando…");
                new Thread(() -> {
                    try {
                        GitHubProjectSyncService.Binding created = GitHubProjectSyncService.createRepository(
                                prefs, scId, repository, branch.getText().toString(), true);
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            SketchwareUtil.toast("Repositório " + created.owner + "/" + created.repo + " criado");
                            showGitHubVersioningDialog();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showGitHubDialogError(dialog, e));
                    }
                }, "github-create-repository").start();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String nextOwner = owner.getText().toString();
                String nextRepo = repo.getText().toString();
                String nextBranch = branch.getText().toString();
                boolean sameTarget = saved.owner.equals(nextOwner.trim())
                        && saved.repo.equals(nextRepo.trim()) && saved.branch.equals(nextBranch.trim());
                GitHubProjectSyncService.Binding binding = new GitHubProjectSyncService.Binding(
                        nextOwner, nextRepo, nextBranch, sameTarget ? saved.lastCommitSha : "");
                if (!binding.isValid()) {
                    owner.setError(binding.owner.isEmpty() ? "Obrigatório" : null);
                    repo.setError(binding.repo.isEmpty() ? "Obrigatório" : null);
                    return;
                }
                saveCurrentFile(false);
                setGitHubDialogBusy(dialog, true, saved.isValid() ? "Enviando…" : "Conectando…");
                new Thread(() -> {
                    try {
                        if (!saved.isValid()) {
                            GitHubProjectSyncService.verifyAndSaveBinding(prefs, scId, binding);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                SketchwareUtil.toast("Projeto conectado ao GitHub");
                                showGitHubVersioningDialog();
                            });
                            return;
                        }
                        GitHubProjectSyncService.SyncResult result = GitHubProjectSyncService.pushSnapshot(
                                prefs, scId, projectRoot, binding, message.getText().toString());
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("Push concluído")
                                    .setMessage(result.fileCount + " arquivos em um único commit\n" +
                                            result.commitSha.substring(0, Math.min(7, result.commitSha.length())))
                                    .setNegativeButton("Fechar", null)
                                    .setPositiveButton("Abrir GitHub", (d, which) ->
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.url))))
                                    .show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showGitHubDialogError(dialog, e));
                    }
                }, "github-project-sync").start();
            });
        });
        dialog.show();
    }

    private EditText githubField(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setText(value == null ? "" : value);
        return field;
    }

    private String safeRepositoryName() {
        CharSequence title = binding == null ? "" : binding.studioToolbar.getTitle();
        String value = title == null ? "android-studio-project" : title.toString();
        value = value.trim().replaceAll("[^A-Za-z0-9_.-]", "-").replaceAll("-+", "-");
        return value.isEmpty() ? "android-studio-project" : value;
    }

    private void setGitHubDialogBusy(AlertDialog dialog, boolean busy, String label) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!busy);
        if (busy) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(label);
    }

    private void showGitHubDialogError(AlertDialog dialog, Exception error) {
        setGitHubDialogBusy(dialog, false, "Tentar novamente");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Falha no GitHub")
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private void setupEditor() {
        activeEditor = binding.studioEditor;
        configureEditor(binding.studioEditor);
        setupFileTabs();
        setupOutputPanel();
        setupSearchReplacePanel();
    }

    private void setupOutputPanel() {
        outputSheetBehavior = BottomSheetBehavior.from(binding.studioOutputSheet);
        outputSheetBehavior.setHideable(false);
        outputSheetBehavior.setPeekHeight(getResources().getDimensionPixelSize(R.dimen.studio_output_sheet_peek_height));
        outputSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        outputSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                updateOutputPanelState(newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });
        binding.studioOutputHeader.setOnClickListener(view -> toggleOutputPanel());
        binding.studioOutputToggle.setOnClickListener(view -> toggleOutputPanel());
        binding.studioOutputClear.setOnClickListener(view -> {
            binding.studioOutput.setText("");
            binding.studioOutputSummary.setText(R.string.studio_output_ready);
        });
        updateOutputPanelState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void toggleOutputPanel() {
        if (outputSheetBehavior == null) {
            return;
        }
        outputSheetBehavior.setState(
                outputSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                        ? BottomSheetBehavior.STATE_COLLAPSED
                        : BottomSheetBehavior.STATE_EXPANDED
        );
    }

    private void expandOutputPanel() {
        if (outputSheetBehavior != null) {
            outputSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private boolean collapseOutputPanel() {
        if (outputSheetBehavior == null
                || outputSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            return false;
        }
        outputSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        return true;
    }

    private void updateOutputPanelState(int state) {
        boolean expanded = state != BottomSheetBehavior.STATE_COLLAPSED;
        binding.studioOutputToggle.setImageResource(
                expanded ? R.drawable.ic_mtrl_arrow_down : R.drawable.ic_mtrl_arrow_up
        );
        binding.studioOutputToggle.setContentDescription(getString(
                expanded ? R.string.studio_output_collapse : R.string.studio_output_expand
        ));
    }

    private void setupFileTabs() {
        binding.studioFileTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!selectingFileTab) {
                    switchToOpenTab(tab.getPosition(), true);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void configureEditor(CodeEditor editor) {
        editor.setTypefaceText(EditorUtils.getTypeface(this));
        editor.setTextSize(14);
        editor.setEditorLanguage(new EmptyLanguage());
        SrcCodeEditor.loadCESettings(this, editor, "studio", true);
        applyDefaultEditorTheme(editor);
        applyEditorPreferenceState(editor);
    }

    private CodeEditor getActiveEditor() {
        return activeEditor == null ? binding.studioEditor : activeEditor;
    }

    private void setupSearchReplacePanel() {
        refreshSearchToggleState();
        binding.studioSearchFind.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSearchQuery(true);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.studioSearchCase.setOnClickListener(v -> {
            searchCaseSensitive = !searchCaseSensitive;
            editorPrefs.edit().putBoolean(EDITOR_PREF_PREFIX + "_search_case", searchCaseSensitive).apply();
            refreshSearchToggleState();
            updateSearchQuery(true);
        });
        binding.studioSearchWord.setOnClickListener(v -> {
            searchWholeWord = !searchWholeWord;
            editorPrefs.edit().putBoolean(EDITOR_PREF_PREFIX + "_search_word", searchWholeWord).apply();
            refreshSearchToggleState();
            updateSearchQuery(true);
        });
        binding.studioSearchClose.setOnClickListener(v -> hideSearchReplacePanel());
        binding.studioSearchPrev.setOnClickListener(v -> navigateSearchResult(false));
        binding.studioSearchNext.setOnClickListener(v -> navigateSearchResult(true));
        binding.studioSearchReplaceOne.setOnClickListener(v -> replaceCurrentSearchMatch());
        binding.studioSearchReplaceAll.setOnClickListener(v -> replaceAllSearchMatches());
    }

    private void beginEditorSearch() {
        if (!isCodeEditorVisible()) {
            return;
        }
        rememberCurrentPosition();
        showSearchReplacePanel();
    }

    private void showSearchReplacePanel() {
        binding.studioSearchReplacePanel.setVisibility(View.VISIBLE);
        updateSearchQuery(false);
        focusSearchFind(true);
    }

    private void hideSearchReplacePanel() {
        if (binding.studioSearchReplacePanel.getVisibility() == View.VISIBLE) {
            binding.studioSearchReplacePanel.setVisibility(View.GONE);
        }
        getActiveEditor().getSearcher().stopSearch();
        binding.studioSearchResultLabel.setText(R.string.studio_search_results_empty);
    }

    private void updateSearchQuery(boolean jumpToFirstResult) {
        if (!isCodeEditorVisible()) {
            return;
        }
        boolean keepFindFocus = binding.studioSearchFind.hasFocus();
        String query = binding.studioSearchFind.getText().toString();
        EditorSearcher searcher = getActiveEditor().getSearcher();
        if (query.isEmpty()) {
            searcher.stopSearch();
            binding.studioSearchResultLabel.setText(R.string.studio_search_results_empty);
            if (keepFindFocus) {
                focusSearchFind(false);
            }
            return;
        }
        try {
            int searchType = searchWholeWord
                    ? EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                    : EditorSearcher.SearchOptions.TYPE_NORMAL;
            searcher.search(query, new EditorSearcher.SearchOptions(searchType, !searchCaseSensitive));
            getActiveEditor().postDelayed(() -> {
                if (jumpToFirstResult && hasSearchQuery()) {
                    try {
                        if (searcher.getMatchedPositionCount() > 0 && !searcher.isMatchedPositionSelected()) {
                            searcher.gotoNext();
                        }
                    } catch (IllegalStateException ignored) {
                    }
                }
                refreshSearchResultLabel();
                if (keepFindFocus) {
                    focusSearchFind(false);
                }
            }, 140);
        } catch (Exception e) {
            searcher.stopSearch();
            binding.studioSearchResultLabel.setText(e.getMessage());
            if (keepFindFocus) {
                focusSearchFind(false);
            }
        }
    }

    private boolean hasSearchQuery() {
        return !binding.studioSearchFind.getText().toString().isEmpty();
    }

    private void navigateSearchResult(boolean next) {
        if (!ensureSearchQuery()) {
            return;
        }
        try {
            if (next) {
                getActiveEditor().getSearcher().gotoNext();
            } else {
                getActiveEditor().getSearcher().gotoPrevious();
            }
        } catch (IllegalStateException ignored) {
            updateSearchQuery(false);
        }
        getActiveEditor().postDelayed(() -> {
            refreshSearchResultLabel();
            focusSearchFind(false);
        }, 60);
    }

    private void replaceCurrentSearchMatch() {
        if (!ensureSearchQuery() || isSearchReadOnly()) {
            return;
        }
        try {
            getActiveEditor().getSearcher().replaceCurrentMatch(binding.studioSearchReplace.getText().toString());
        } catch (IllegalStateException ignored) {
            updateSearchQuery(false);
        }
        getActiveEditor().postDelayed(() -> {
            updateSearchQuery(false);
            refreshSearchResultLabel();
        }, 90);
    }

    private void replaceAllSearchMatches() {
        if (!ensureSearchQuery() || isSearchReadOnly()) {
            return;
        }
        try {
            getActiveEditor().getSearcher().replaceAll(binding.studioSearchReplace.getText().toString(), () -> {
                updateSearchQuery(false);
                refreshSearchResultLabel();
            });
        } catch (IllegalStateException ignored) {
            updateSearchQuery(false);
        }
    }

    private boolean ensureSearchQuery() {
        if (hasSearchQuery()) {
            return true;
        }
        SketchwareUtil.toast(getString(R.string.studio_search_no_query));
        focusSearchFind(true);
        return false;
    }

    private void focusSearchFind(boolean showKeyboard) {
        binding.studioSearchFind.requestFocus();
        binding.studioSearchFind.setSelection(binding.studioSearchFind.getText().length());
        if (!showKeyboard) {
            return;
        }
        binding.studioSearchFind.postDelayed(() -> {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(binding.studioSearchFind, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 120);
    }

    private boolean isSearchReadOnly() {
        if (!readOnlyMode && getActiveEditor().isEditable()) {
            return false;
        }
        SketchwareUtil.toastError(getString(R.string.studio_read_only_enabled));
        return true;
    }

    private void refreshSearchResultLabel() {
        if (!isCodeEditorVisible() || !hasSearchQuery()) {
            binding.studioSearchResultLabel.setText(R.string.studio_search_results_empty);
            return;
        }
        try {
            EditorSearcher searcher = getActiveEditor().getSearcher();
            if (!searcher.hasQuery()) {
                binding.studioSearchResultLabel.setText(R.string.studio_search_results_empty);
                return;
            }
            int count = searcher.getMatchedPositionCount();
            int index = count == 0 ? 0 : searcher.getCurrentMatchedPositionIndex() + 1;
            if (index < 0) {
                index = 0;
            }
            binding.studioSearchResultLabel.setText(getString(R.string.studio_search_results_count, index, count));
        } catch (IllegalStateException ignored) {
            binding.studioSearchResultLabel.setText(R.string.studio_search_results_empty);
        }
    }

    private void refreshSearchToggleState() {
        int accent = getResources().getColor(R.color.studio_accent, getTheme());
        int secondary = getResources().getColor(R.color.studio_text_secondary, getTheme());
        binding.studioSearchCase.setTextColor(searchCaseSensitive ? accent : secondary);
        binding.studioSearchCase.setTypeface(searchCaseSensitive ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        binding.studioSearchWord.setTextColor(searchWholeWord ? accent : secondary);
        binding.studioSearchWord.setTypeface(searchWholeWord ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void showSyntaxDialog() {
        if (!isCodeEditorVisible()) {
            return;
        }
        String[] syntaxItems = {
                getString(R.string.studio_syntax_plain_text),
                getString(R.string.studio_syntax_java),
                getString(R.string.studio_syntax_kotlin),
                getString(R.string.studio_syntax_xml)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.studio_action_syntax)
                .setSingleChoiceItems(syntaxItems, syntaxIndexForCurrentFile(), (dialog, which) -> {
                    applySyntaxSelection(which);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private int syntaxIndexForCurrentFile() {
        if (currentFile == null) {
            return 0;
        }
        String name = currentFile.getName().toLowerCase(Locale.US);
        if (name.endsWith(".java")) {
            return 1;
        }
        if (name.endsWith(".kt") || name.endsWith(".kts")) {
            return 2;
        }
        if (name.endsWith(".xml")) {
            return 3;
        }
        return 0;
    }

    private void applySyntaxSelection(int which) {
        if (!isCodeEditorVisible()) {
            return;
        }
        CodeEditor editor = getActiveEditor();
        editor.setEditorLanguage(new EmptyLanguage());
        String languageName = "text";
        int labelRes = R.string.studio_syntax_plain_text;
        if (which == 1) {
            applyJavaLanguageSafely(currentFile);
            languageName = "java";
            labelRes = R.string.studio_syntax_java;
        } else if (which == 2) {
            applyTextMateLanguageSafely(currentFile, CodeEditorLanguages.SCOPE_NAME_KOTLIN, "Kotlin");
            languageName = "kotlin";
            labelRes = R.string.studio_syntax_kotlin;
        } else if (which == 3) {
            applyXmlLanguageSafely(currentFile);
            languageName = "xml";
            labelRes = R.string.studio_syntax_xml;
        }
        wrapActiveLanguage(languageName);
        updateStatus(getString(R.string.studio_syntax_selected, getString(labelRes)));
    }

    private void wrapActiveLanguage(String languageName) {
        CodeEditor editor = getActiveEditor();
        Language language = editor.getEditorLanguage();
        editor.setEditorLanguage(VoidPortAiAutocompleteLanguage.wrap(
                this,
                scId,
                currentFile == null ? "" : currentFile.getAbsolutePath(),
                languageName,
                language
        ));
    }

    private void showJumpToLineDialog() {
        if (!isCodeEditorVisible()) {
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.studio_line_number_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.studio_action_jump_to_line)
                .setView(input)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> jumpToLine(input.getText().toString()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void jumpToLine(String value) {
        if (!isCodeEditorVisible()) {
            return;
        }
        try {
            int requestedLine = Integer.parseInt(value.trim());
            if (requestedLine <= 0) {
                throw new NumberFormatException();
            }
            CodeEditor editor = getActiveEditor();
            rememberCurrentPosition();
            int line = Math.min(requestedLine, editor.getLineCount()) - 1;
            moveEditorToPosition(editor, line, 0);
            addNavigationPosition(currentFile, line, 0);
            updateStatus(getString(R.string.studio_line_selected, requestedLine));
        } catch (NumberFormatException e) {
            SketchwareUtil.toastError(getString(R.string.studio_invalid_line_number));
        }
    }

    private void setWordWrapEnabled(boolean enabled) {
        editorPrefs.edit().putBoolean(EDITOR_PREF_PREFIX + "_ww", enabled).apply();
        for (OpenFileTab openFileTab : openFileTabs) {
            openFileTab.editor.setWordwrap(enabled);
        }
        binding.studioEditor.setWordwrap(enabled);
        updateStatus(getString(enabled ? R.string.studio_word_wrap_enabled : R.string.studio_word_wrap_disabled));
        updateToolbarMenus();
    }

    private void setReadOnlyMode(boolean enabled) {
        readOnlyMode = enabled;
        editorPrefs.edit().putBoolean(EDITOR_PREF_PREFIX + "_ro", enabled).apply();
        applyEditorPreferenceStateToAllEditors();
        updateStatus(getString(enabled ? R.string.studio_read_only_enabled : R.string.studio_read_only_disabled));
        updateToolbarMenus();
    }

    private void setSmoothMode(boolean enabled) {
        smoothMode = enabled;
        editorPrefs.edit().putBoolean(EDITOR_PREF_PREFIX + "_smooth", enabled).apply();
        applyEditorPreferenceStateToAllEditors();
        updateStatus(getString(enabled ? R.string.studio_smooth_mode_enabled : R.string.studio_smooth_mode_disabled));
        updateToolbarMenus();
    }

    private void showEditorPreferences() {
        if (!isCodeEditorVisible()) {
            return;
        }
        boolean[] checked = {
                getActiveEditor().isWordwrap(),
                readOnlyMode,
                smoothMode
        };
        String[] items = {
                getString(R.string.studio_action_word_wrap),
                getString(R.string.studio_action_read_only_mode),
                getString(R.string.studio_action_smooth_mode)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.studio_action_preferences)
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    if (which == 0) {
                        setWordWrapEnabled(isChecked);
                    } else if (which == 1) {
                        setReadOnlyMode(isChecked);
                    } else if (which == 2) {
                        setSmoothMode(isChecked);
                    }
                })
                .setPositiveButton(R.string.common_word_ok, null)
                .show();
    }

    private boolean isCodeEditorVisible() {
        return currentFile != null && currentFileEditable && !showingImage;
    }

    private void applyEditorPreferenceState(CodeEditor editor) {
        editor.setEditable(!readOnlyMode);
        editor.setCursorAnimationEnabled(smoothMode);
    }

    private void applyEditorPreferenceStateToAllEditors() {
        Set<CodeEditor> editors = new HashSet<>();
        for (OpenFileTab openFileTab : openFileTabs) {
            editors.add(openFileTab.editor);
        }
        if (binding != null) {
            editors.add(binding.studioEditor);
        }
        for (CodeEditor editor : editors) {
            applyEditorPreferenceState(editor);
        }
    }

    private void rememberCurrentPosition() {
        if (!isCodeEditorVisible()) {
            return;
        }
        CodeEditor editor = getActiveEditor();
        addNavigationPosition(currentFile, editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
    }

    private void addNavigationPosition(File file, int line, int column) {
        if (restoringNavigationPosition || file == null) {
            return;
        }
        String path = canonicalPath(file);
        if (navigationHistoryIndex >= 0 && navigationHistoryIndex < navigationHistory.size()) {
            EditorNavigationEntry current = navigationHistory.get(navigationHistoryIndex);
            if (current.matches(path, line, column)) {
                return;
            }
        }
        while (navigationHistory.size() > navigationHistoryIndex + 1) {
            navigationHistory.remove(navigationHistory.size() - 1);
        }
        navigationHistory.add(new EditorNavigationEntry(path, line, column));
        if (navigationHistory.size() > MAX_NAVIGATION_HISTORY) {
            navigationHistory.remove(0);
        }
        navigationHistoryIndex = navigationHistory.size() - 1;
        updateToolbarMenus();
    }

    private boolean canNavigateEditorPosition(int direction) {
        if (direction < 0) {
            return navigationHistoryIndex > 0;
        }
        return navigationHistoryIndex >= 0 && navigationHistoryIndex < navigationHistory.size() - 1;
    }

    private void navigateEditorPosition(int direction) {
        if (!canNavigateEditorPosition(direction)) {
            return;
        }
        navigationHistoryIndex += direction < 0 ? -1 : 1;
        restoreNavigationPosition(navigationHistory.get(navigationHistoryIndex));
        updateToolbarMenus();
    }

    private void restoreNavigationPosition(EditorNavigationEntry entry) {
        File file = new File(entry.path);
        if (!file.isFile()) {
            updateStatus(getString(R.string.studio_open_failed));
            return;
        }
        restoringNavigationPosition = true;
        if (!isCurrentFile(file)) {
            int tabIndex = findOpenTabIndex(file);
            if (tabIndex >= 0) {
                switchToOpenTab(tabIndex, true);
            } else {
                openFile(file, true);
            }
        }
        getActiveEditor().post(() -> {
            moveEditorToPosition(getActiveEditor(), entry.line, entry.column);
            updateStatus(getString(R.string.studio_line_selected, entry.line + 1));
            restoringNavigationPosition = false;
            updateToolbarMenus();
        });
    }

    private void moveEditorToPosition(CodeEditor editor, int line, int column) {
        int safeLine = Math.max(0, Math.min(line, editor.getLineCount() - 1));
        int safeColumn = Math.max(0, Math.min(column, editor.getText().getColumnCount(safeLine)));
        editor.setSelection(safeLine, safeColumn, false);
        editor.ensurePositionVisible(safeLine, safeColumn, !smoothMode);
    }

    private void setupFileTree() {
        fileTreeAdapter = new FileTreeAdapter();
        binding.studioFileTree.setLayoutManager(new LinearLayoutManager(this));
        binding.studioFileTree.setAdapter(fileTreeAdapter);
        binding.studioFileSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                treeQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                rebuildVisibleNodes();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadProject(String projectName) {
        if (scId == null || scId.trim().isEmpty()) {
            showMissingProject();
            return;
        }

        projectRoot = ProjectPathResolver.getAndroidStudioProjectRoot(scId);
        selectedNodeFile = projectRoot;
        binding.studioToolbar.setSubtitle(ProjectPathResolver.toDisplayPath(scId, projectRoot));
        binding.studioProjectPath.setText(ProjectPathResolver.toDisplayPath(scId, projectRoot));

        if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
            showMissingProject();
            return;
        }

        expandedDirs.clear();
        expandDirectory(projectRoot);
        expandDirectory(new File(projectRoot, "app"));
        expandDirectory(new File(projectRoot, "app/src"));
        expandDirectory(new File(projectRoot, "app/src/main"));
        expandDirectory(new File(projectRoot, "app/src/main/java"));
        expandDirectory(new File(projectRoot, "app/src/main/kotlin"));
        expandDirectory(new File(projectRoot, "app/src/main/res"));
        expandDirectory(new File(projectRoot, "app/src/main/res/layout"));

        rebuildVisibleNodes();
        File initialFile = findInitialFile(projectRoot);
        if (initialFile != null) {
            openFile(initialFile, false);
            return;
        }

        binding.studioSelectedFile.setText(projectName);
        binding.studioSelectedPath.setText(ProjectPathResolver.toDisplayPath(scId, projectRoot));
        setOutput(getString(R.string.studio_no_file_message), false);
        updateStage();
        updateStatus(getString(R.string.studio_no_file_title));
    }

    private void showMissingProject() {
        currentFile = null;
        selectedNodeFile = null;
        currentFileEditable = false;
        showingImage = false;
        visibleNodes.clear();
        discoveredFileCount = 0;
        fileTreeAdapter.submit(new ArrayList<>());
        binding.studioEmptyTitle.setText(R.string.studio_project_missing);
        binding.studioEmptyMessage.setText(ProjectPathResolver.toDisplayPath(scId, projectRoot));
        binding.studioSelectedFile.setText(R.string.studio_project_missing);
        binding.studioSelectedPath.setText("");
        setOutput(getString(R.string.studio_project_missing), false);
        updateStage();
        updateStatus(getString(R.string.studio_project_missing));
        SketchwareUtil.toast(getString(R.string.studio_project_missing));
    }

    private void rebuildVisibleNodes() {
        visibleNodes.clear();
        discoveredFileCount = 0;
        if (treeQuery.isEmpty()) {
            appendChildren(projectRoot, 0);
        } else {
            appendSearchMatches(projectRoot, 0);
        }
        fileTreeAdapter.submit(new ArrayList<>(visibleNodes));
        binding.studioFilePanelTitle.setText(
                treeQuery.isEmpty()
                        ? getString(R.string.studio_files) + " (" + discoveredFileCount + ")"
                        : getString(R.string.studio_search_results) + " (" + visibleNodes.size() + ")"
        );
    }

    private void appendChildren(File directory, int depth) {
        if (directory == null || visibleNodes.size() >= MAX_TREE_NODES) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        List<File> childList = new ArrayList<>();
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            childList.add(child);
        }
        childList.sort(Comparator
                .comparing((File file) -> !file.isDirectory())
                .thenComparing(file -> file.getName().toLowerCase(Locale.US)));

        for (File child : childList) {
            if (!child.isDirectory()) {
                discoveredFileCount++;
            }
            FileNode node = new FileNode(child, depth, child.isDirectory());
            visibleNodes.add(node);
            if (visibleNodes.size() >= MAX_TREE_NODES) {
                return;
            }
            if (child.isDirectory() && expandedDirs.contains(canonicalPath(child))) {
                appendChildren(child, depth + 1);
            }
        }
    }

    private void appendSearchMatches(File directory, int depth) {
        if (directory == null || visibleNodes.size() >= MAX_TREE_NODES) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        List<File> childList = new ArrayList<>();
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            childList.add(child);
        }
        childList.sort(Comparator
                .comparing((File file) -> !file.isDirectory())
                .thenComparing(file -> file.getName().toLowerCase(Locale.US)));

        for (File child : childList) {
            if (!child.isDirectory()) {
                discoveredFileCount++;
            }
            String relative = relativePath(child).toLowerCase(Locale.US);
            if (child.getName().toLowerCase(Locale.US).contains(treeQuery) || relative.contains(treeQuery)) {
                visibleNodes.add(new FileNode(child, depth, child.isDirectory()));
                if (visibleNodes.size() >= MAX_TREE_NODES) {
                    return;
                }
            }
            if (child.isDirectory()) {
                appendSearchMatches(child, depth + 1);
            }
        }
    }

    private void openFile(File file, boolean savePrevious) {
        if (file == null || !file.isFile()) {
            return;
        }

        selectedNodeFile = file;

        if (isPreviewableImage(file)) {
            openImageFile(file, savePrevious);
            return;
        }

        if (!canOpen(file)) {
            if (savePrevious && hasUnsavedChanges()) {
                saveCurrentFile(false);
            }
            showUnsupportedFile(file);
            return;
        }

        openTextFile(file, savePrevious);
    }

    private void openTextFile(File file, boolean savePrevious) {
        if (file == null || !file.isFile()) {
            return;
        }

        int existingTab = findOpenTabIndex(file);
        if (existingTab >= 0) {
            switchToOpenTab(existingTab, savePrevious);
            if (binding.studioDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.studioDrawerLayout.closeDrawer(GravityCompat.START);
            }
            return;
        }

        if (savePrevious && hasUnsavedChanges()) {
            saveCurrentFile(false);
        }

        changingFile = true;
        binding.studioProgress.setVisibility(View.VISIBLE);

        try {
            if (!isInsideProject(file)) {
                throw new IOException("File is outside the project root");
            }

            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            CodeEditor editor = createEditorForTab();
            activeEditor = editor;
            currentFile = file;
            selectedNodeFile = file;
            currentFileEditable = true;
            lastSavedContent = content;

            showingImage = false;

            editor.setEditorLanguage(new EmptyLanguage());
            editor.setText("");
            editor.setText(content);

            applyDefaultEditorTheme(editor);
            applyLanguage(file);
            OpenFileTab openFileTab = new OpenFileTab(file, editor, content);
            openFileTabs.add(openFileTab);
            TabLayout.Tab tab = binding.studioFileTabs.newTab()
                    .setText(file.getName())
                    .setIcon(iconFor(file, false));
            binding.studioFileTabs.addTab(tab, false);
            switchToOpenTab(openFileTabs.size() - 1, false);
            updateFileHeader(file);
            showEditor();

            if (binding.studioDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.studioDrawerLayout.closeDrawer(GravityCompat.START);
            }

            setOutput("Opened " + relativePath(file) + " (" + formatBytes(file.length()) + ")", false);
            updateStatus(relativePath(file) + " - " + countLines(content) + " lines");
            logStudioAction("open_file");

            fileTreeAdapter.notifyDataSetChanged();

        } catch (Exception e) {
            setOutput(getString(R.string.studio_open_failed) + ": " + e.getMessage(), true);
            SketchwareUtil.toast(getString(R.string.studio_open_failed));
        } finally {
            binding.studioProgress.setVisibility(View.GONE);
            changingFile = false;
            updateStage();
        }
    }

    private CodeEditor createEditorForTab() {
        if (openFileTabs.isEmpty()) {
            binding.studioEditor.setVisibility(View.GONE);
            return binding.studioEditor;
        }

        CodeEditor editor = new CodeEditor(this);
        editor.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        editor.setVisibility(View.GONE);
        configureEditor(editor);
        binding.studioEditorStack.addView(editor);
        return editor;
    }

    private int findOpenTabIndex(File file) {
        if (file == null) {
            return -1;
        }
        String path = canonicalPath(file);
        for (int i = 0; i < openFileTabs.size(); i++) {
            if (canonicalPath(openFileTabs.get(i).file).equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private OpenFileTab findOpenTab(File file) {
        int index = findOpenTabIndex(file);
        return index >= 0 ? openFileTabs.get(index) : null;
    }

    private void switchToOpenTab(int index, boolean savePrevious) {
        if (index < 0 || index >= openFileTabs.size()) {
            return;
        }
        if (savePrevious && hasUnsavedChanges()) {
            saveCurrentFile(false);
        }

        OpenFileTab tab = openFileTabs.get(index);
        for (OpenFileTab openFileTab : openFileTabs) {
            openFileTab.editor.setVisibility(View.GONE);
        }
        tab.editor.setVisibility(View.VISIBLE);

        activeEditor = tab.editor;
        currentFile = tab.file;
        selectedNodeFile = tab.file;
        currentFileEditable = true;
        lastSavedContent = tab.lastSavedContent;
        showingImage = false;

        updateFileHeader(tab.file);
        updateStatus(relativePath(tab.file) + " - " + countLines(tab.editor.getText().toString()) + " lines");

        if (binding.studioFileTabs.getTabCount() > index
                && binding.studioFileTabs.getSelectedTabPosition() != index) {
            selectingFileTab = true;
            TabLayout.Tab selectedTab = binding.studioFileTabs.getTabAt(index);
            if (selectedTab != null) {
                selectedTab.select();
            }
            selectingFileTab = false;
        }

        updateStage();
        fileTreeAdapter.notifyDataSetChanged();
    }

    private void showUnsupportedFile(File file) {
        currentFile = file;
        selectedNodeFile = file;
        currentFileEditable = false;
        lastSavedContent = "";
        showingImage = false;

        updateFileHeader(file);
        binding.studioEmptyTitle.setText(R.string.studio_file_not_editable_title);
        binding.studioEmptyMessage.setText(R.string.studio_file_not_editable);

        setOutput(
                getString(R.string.studio_file_not_editable_title) + "\n\n"
                        + relativePath(file) + "\n"
                        + getString(R.string.studio_file_size_label) + ": " + formatBytes(file.length()) + "\n\n"
                        + getString(R.string.studio_file_not_editable),
                true
        );

        updateStatus(getString(R.string.studio_file_not_editable));
        updateStage();
        fileTreeAdapter.notifyDataSetChanged();
    }

    private void openImageFile(File file, boolean savePrevious) {
        if (savePrevious && hasUnsavedChanges()) {
            saveCurrentFile(false);
        }
        changingFile = true;
        binding.studioProgress.setVisibility(View.VISIBLE);
        try {
            if (!isInsideProject(file)) {
                throw new IOException("File is outside the project root");
            }
            currentFile = file;
            selectedNodeFile = file;
            currentFileEditable = false;
            lastSavedContent = "";
            showingImage = true;
            binding.studioImagePreview.setImageURI(Uri.fromFile(file));
            updateFileHeader(file);
            updateStage();
            if (binding.studioDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.studioDrawerLayout.closeDrawer(GravityCompat.START);
            }
            setOutput("Preview " + relativePath(file) + " (" + formatBytes(file.length()) + ")", false);
            updateStatus(relativePath(file) + " - " + getString(R.string.studio_image_preview));
            fileTreeAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            setOutput(getString(R.string.studio_open_failed) + ": " + e.getMessage(), true);
            SketchwareUtil.toast(getString(R.string.studio_open_failed));
        } finally {
            binding.studioProgress.setVisibility(View.GONE);
            changingFile = false;
            updateStage();
        }
    }

    private void applyDefaultEditorTheme() {
        applyDefaultEditorTheme(getActiveEditor());
    }

    private void applyDefaultEditorTheme(CodeEditor editor) {
        editor.setColorScheme(new EditorColorScheme());
        SrcCodeEditor.selectTheme(editor, 0);
    }

    private void applyLanguage(File file) {
        try {
            CodeEditor editor = getActiveEditor();
            editor.setEditorLanguage(new EmptyLanguage());

            String name = file == null ? "" : file.getName().toLowerCase(Locale.US);

            if (name.endsWith(".java")) {
                applyJavaLanguageSafely(file);
            } else if (name.endsWith(".kt") || name.endsWith(".kts")) {
                applyTextMateLanguageSafely(file, CodeEditorLanguages.SCOPE_NAME_KOTLIN, "Kotlin");
            } else if (name.endsWith(".xml")) {
                applyXmlLanguageSafely(file);
            } else {
                editor.setEditorLanguage(new EmptyLanguage());
            }
            editor.setEditorLanguage(VoidPortAiAutocompleteLanguage.wrap(
                    this,
                    scId,
                    file == null ? "" : file.getAbsolutePath(),
                    languageNameFor(file),
                    editor.getEditorLanguage()
            ));
        } catch (Exception ignored) {
            getActiveEditor().setEditorLanguage(new EmptyLanguage());
        }
    }

    private void applyJavaLanguageSafely(File file) {
        try {
            CodeEditor editor = getActiveEditor();
            Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_JAVA);
            if (language instanceof EmptyLanguage) {
                language = new JavaLanguage();
                applyDefaultEditorTheme(editor);
            } else {
                applyTextMateTheme();
            }
            editor.setEditorLanguage(language);
        } catch (Throwable throwable) {
            try {
                applyDefaultEditorTheme();
                getActiveEditor().setEditorLanguage(new JavaLanguage());
            } catch (Throwable fallback) {
                getActiveEditor().setEditorLanguage(new EmptyLanguage());
                setOutput("Java syntax highlight failed: " + fallback.getMessage(), false);
            }
        }
    }

    private void applyTextMateLanguageSafely(File file, String scopeName, String label) {
        try {
            CodeEditor editor = getActiveEditor();
            Language language = CodeEditorLanguages.loadTextMateLanguage(scopeName);
            if (language instanceof EmptyLanguage) {
                throw new IllegalStateException(label + " grammar unavailable");
            }
            applyTextMateTheme();
            editor.setEditorLanguage(language);
        } catch (Throwable throwable) {
            getActiveEditor().setEditorLanguage(new EmptyLanguage());
            getActiveEditor().post(() -> {
                if (isCurrentFile(file)) {
                    updateStatus(relativePath(file) + " - " + label + " highlight unavailable");
                }
            });
        }
    }

    private void applyTextMateTheme() {
        try {
            getActiveEditor().setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(
                    ThemeUtils.isDarkThemeEnabled(getApplicationContext())
                            ? CodeEditorColorSchemes.THEME_DRACULA
                            : CodeEditorColorSchemes.THEME_GITHUB
            ));
        } catch (Throwable ignored) {
            applyDefaultEditorTheme();
        }
    }

    private void applyXmlLanguageSafely(File file) {
        applyTextMateLanguageSafely(file, CodeEditorLanguages.SCOPE_NAME_XML, "XML");
    }

    private boolean isCurrentFile(File file) {
        return currentFile != null && file != null && canonicalPath(currentFile).equals(canonicalPath(file));
    }

    private String languageNameFor(File file) {
        if (file == null) {
            return "text";
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".java")) {
            return "java";
        }
        if (name.endsWith(".kt") || name.endsWith(".kts")) {
            return "kotlin";
        }
        if (name.endsWith(".xml")) {
            return "xml";
        }
        if (name.endsWith(".gradle")) {
            return "gradle";
        }
        return "text";
    }

    private boolean saveCurrentFile(boolean showToast) {
        if (currentFile == null) {
            return false;
        }
        if (!currentFileEditable) {
            return false;
        }
        try {
            if (!isInsideProject(currentFile)) {
                throw new IOException("File is outside the project root");
            }
            String content = getActiveEditor().getText().toString();
            Files.write(currentFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            lastSavedContent = content;
            OpenFileTab openFileTab = findOpenTab(currentFile);
            if (openFileTab != null) {
                openFileTab.lastSavedContent = content;
            }
            updateStatus(relativePath(currentFile) + " - saved");
            setOutput("Saved " + relativePath(currentFile), false);
            if (showToast) {
                SketchwareUtil.toast(getString(R.string.studio_saved));
            }
            return true;
        } catch (Exception e) {
            setOutput(getString(R.string.studio_save_failed) + ": " + e.getMessage(), true);
            if (showToast) {
                SketchwareUtil.toast(getString(R.string.studio_save_failed));
            }
            return false;
        }
    }

    private void formatCurrentFile() {
        if (currentFile == null) {
            return;
        }
        if (!currentFileEditable) {
            setOutput(getString(R.string.studio_file_not_editable), true);
            return;
        }
        if (!currentFile.getName().toLowerCase(Locale.US).endsWith(".xml")) {
            setOutput(getString(R.string.studio_no_xml_preview), true);
            return;
        }
        try {
            String formatted = SrcCodeEditor.prettifyXml(getActiveEditor().getText().toString(), 4, null);
            if (formatted == null) {
                throw new IOException("XML formatter returned no result");
            }
            getActiveEditor().setText(formatted);
            updateStatus(relativePath(currentFile) + " - formatted");
        } catch (Exception e) {
            setOutput(getString(R.string.studio_format_failed) + ": " + e.getMessage(), true);
        }
    }

    private void buildProject() {
        if (buildRunning) {
            return;
        }
        if (projectRoot == null || !projectRoot.isDirectory()) {
            setOutput(getString(R.string.studio_project_missing), true);
            return;
        }
        if (hasUnsavedChanges()) {
            saveCurrentFile(false);
        }

        showOutput();
        setOutput(getString(R.string.studio_build_started) + "...\n" + projectRoot.getAbsolutePath(), false);
        setBuildRunning(true);

        new Thread(() -> {
            try {
                File apk = runStudioBuild();
                appendBuildOutput("");
                appendBuildOutput(getString(R.string.studio_build_finished) + ": " + apk.getAbsolutePath());
                lastBuildErrors = "";
                runOnUiThread(() -> {
                    SketchwareUtil.toast(getString(R.string.studio_build_finished));
                    showInstallBuiltApkDialog(apk);
                });
            } catch (Throwable throwable) {
                String failure = describeBuildFailure(throwable) + "\n" + Log.getStackTraceString(throwable);
                lastBuildErrors = failure;
                appendBuildOutput("");
                appendBuildOutput(getString(R.string.studio_build_failed) + ": " + failure);
                runOnUiThread(() -> SketchwareUtil.toast(getString(R.string.studio_build_failed)));
            } finally {
                runOnUiThread(() -> setBuildRunning(false));
            }
        }).start();
    }

    private void showInstallBuiltApkDialog(File apk) {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_apk_install)
                .setTitle(R.string.studio_install_apk_title)
                .setMessage(getString(R.string.studio_install_apk_message) + "\n" + apk.getAbsolutePath())
                .setPositiveButton(R.string.studio_install_apk_button, (dialog, which) -> requestPackageInstallerInstall(apk))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void requestPackageInstallerInstall(File apk) {
        if (apk == null || !apk.exists()) {
            setOutput(getString(R.string.studio_install_failed) + ": " + getString(R.string.studio_apk_missing), true);
            SketchwareUtil.toast(getString(R.string.studio_apk_missing));
            return;
        }
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    getApplicationContext(),
                    getApplicationContext().getPackageName() + ".provider",
                    apk
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (Exception e) {
            setOutput(getString(R.string.studio_install_failed) + ": " + e.getMessage(), true);
            SketchwareUtil.toast(getString(R.string.studio_install_failed));
        }
    }

    private File runStudioBuild() throws Throwable {
        HashMap<String, Object> project = lC.b(scId);
        yq workspace = new yq(getApplicationContext(), projectRoot.getAbsolutePath(), project);
        configureStudioBuildWorkspace(workspace);

        BuildProgressReceiver receiver = (progress, step) -> {
            String line = step >= 0 ? "[" + step + "] " + progress : progress;
            appendBuildOutput(line);
        };

        receiver.onProgress("Deleting temporary files...", 1);
        workspace.f();
        workspace.e();
        ensureStudioBuildDirectories(workspace);
        ensureLauncherActivityExported(new File(workspace.androidManifestPath));

        syncGradleDependenciesForStudioBuild(receiver);
        ProjectBuilder builder = new ProjectBuilder(receiver, getApplicationContext(), workspace);
        builder.buildBuiltInLibraryInformation();

        receiver.onProgress("Preparing AAPT2...", 2);
        builder.maybeExtractAapt2();

        receiver.onProgress("Extracting built-in libraries...", 3);
        BuiltInLibraries.extractCompileAssets(receiver);

        receiver.onProgress("AAPT2 is running...", 8);
        builder.compileResources();

        receiver.onProgress("Generating view binding...", 11);
        builder.generateViewBinding();

        KotlinCompilerBridge.compileKotlinCodeIfPossible(receiver, builder);

        receiver.onProgress("Java is compiling...", 13);
        builder.compileJavaCode();

        receiver.onProgress(builder.getDxRunningText(), 17);
        builder.createDexFilesFromClasses();

        receiver.onProgress("Merging DEX files...", 18);
        builder.getDexFilesReady();

        receiver.onProgress("Building APK...", 19);
        builder.buildApk();

        receiver.onProgress("Signing APK...", 20);
        builder.signDebugApk();

        return new File(workspace.finalToInstallApkPath);
    }

    private void ensureLauncherActivityExported(File manifestFile) throws IOException {
        if (manifestFile == null || !manifestFile.isFile()) {
            return;
        }
        String manifest = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
        String updated = addExportedToLauncherActivity(manifest);
        if (!manifest.equals(updated)) {
            Files.write(manifestFile.toPath(), updated.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String addExportedToLauncherActivity(String manifest) {
        int searchFrom = 0;
        while (searchFrom < manifest.length()) {
            int mainAction = manifest.indexOf("android.intent.action.MAIN", searchFrom);
            if (mainAction == -1) {
                return manifest;
            }
            int intentFilterEnd = manifest.indexOf("</intent-filter>", mainAction);
            int launcherCategory = manifest.indexOf("android.intent.category.LAUNCHER", mainAction);
            if (intentFilterEnd != -1 && launcherCategory != -1 && launcherCategory < intentFilterEnd) {
                int activityStart = manifest.lastIndexOf("<activity", mainAction);
                int activityTagEnd = manifest.indexOf(">", activityStart);
                if (activityStart == -1 || activityTagEnd == -1) {
                    return manifest;
                }
                String activityTag = manifest.substring(activityStart, activityTagEnd);
                if (activityTag.contains("android:exported")) {
                    return manifest;
                }
                return manifest.substring(0, activityTagEnd)
                        + "\n            android:exported=\"true\""
                        + manifest.substring(activityTagEnd);
            }
            searchFrom = mainAction + 1;
        }
        return manifest;
    }

    private void ensureStudioBuildDirectories(yq workspace) throws IOException {
        ensureDirectory(workspace.binDirectoryPath);
        ensureDirectory(workspace.compiledClassesPath);
        ensureDirectory(workspace.rJavaDirectoryPath);
        ensureDirectory(workspace.resDirectoryPath);
        ensureDirectory(workspace.importedSoundsPath);
        ensureDirectory(workspace.assetsPath);
        ensureDirectory(workspace.fontsPath);
    }

    private void ensureDirectory(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        File directory = new File(path);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create directory: " + directory.getAbsolutePath());
        }
    }

    private String describeBuildFailure(Throwable throwable) {
        if (throwable instanceof MissingFileException) {
            MissingFileException exception = (MissingFileException) throwable;
            File missingFile = exception.getMissingFile();
            String type = exception.isMissingDirectory() ? "Missing directory" : "Missing file";
            return type + ": " + (missingFile == null ? throwable.getMessage() : missingFile.getAbsolutePath());
        }
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private void configureStudioBuildWorkspace(yq workspace) {
        workspace.N.packageName = workspace.packageName;
        workspace.N.projectName = workspace.applicationName;
        workspace.N.versionCode = workspace.versionCode;
        workspace.N.versionName = workspace.versionName;
        workspace.N.sc_id = scId;
        workspace.N.isDebugBuild = true;
        workspace.N.g = usesAndroidxOrMaterial();
    }

    private void syncGradleDependenciesForStudioBuild(BuildProgressReceiver receiver) throws IOException {
        List<String> dependencies = detectProjectDependencies();
        if (dependencies.isEmpty()) {
            return;
        }

        ArrayList<HashMap<String, Object>> enabledLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        LinkedHashSet<String> enabledNames = new LinkedHashSet<>();
        for (HashMap<String, Object> library : enabledLibraries) {
            Object name = library.get("name");
            if (name instanceof String) {
                enabledNames.add((String) name);
            }
        }

        boolean changed = false;
        for (String dependency : dependencies) {
            String normalized = normalizeMavenCoordinate(dependency);
            if (normalized.isEmpty() || shouldSkipAutomaticDependency(normalized)) {
                continue;
            }

            String libraryName = toLocalLibraryFolderName(normalized);
            if (libraryName.isEmpty()) {
                continue;
            }

            LinkedHashSet<String> resolvedLibraryNames = new LinkedHashSet<>();
            resolvedLibraryNames.add(libraryName);

            if (!isLocalLibraryDownloaded(libraryName)) {
                if (receiver != null) {
                    receiver.onProgress("Downloading dependency: " + normalized, 4);
                }
                resolvedLibraryNames.addAll(downloadDependencyForStudioBuild(normalized));
            }

            for (String resolvedLibraryName : resolvedLibraryNames) {
                if (resolvedLibraryName == null || resolvedLibraryName.trim().isEmpty() || enabledNames.contains(resolvedLibraryName)) {
                    continue;
                }
                HashMap<String, Object> library = LocalLibrariesUtil.createLibraryMap(resolvedLibraryName, normalized);
                if (hasUsableLocalLibraryArtifact(library)) {
                    enabledLibraries.add(library);
                    enabledNames.add(resolvedLibraryName);
                    changed = true;
                }
            }
        }

        if (changed) {
            File localLibFile = LocalLibrariesUtil.getLocalLibFile(scId);
            File parent = localLibFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileUtil.writeFile(localLibFile.getAbsolutePath(), new Gson().toJson(enabledLibraries));
        }
    }

    private List<String> downloadDependencyForStudioBuild(String dependency) throws IOException {
        String[] parts = dependency.split(":");
        if (parts.length != 3) {
            return new ArrayList<>();
        }

        ArrayList<String> resolvedLibraries = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        DependencyResolver resolver = new DependencyResolver(parts[0], parts[1], parts[2], false, new BuildSettings(scId));
        resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
            @Override
            public void onDownloadError(@NonNull Artifact artifact, @NonNull Throwable error) {
                errors.add("Downloading dependency '" + artifact + "' failed: " + error.getMessage());
            }

            @Override
            public void dexingFailed(@NonNull Artifact artifact, @NonNull Exception error) {
                errors.add("Dexing dependency '" + artifact + "' failed: " + error.getMessage());
            }

            @Override
            public void onTaskCompleted(@NonNull List<String> dependencies) {
                resolvedLibraries.addAll(dependencies);
            }
        });

        if (!errors.isEmpty()) {
            throw new IOException(errors.get(0));
        }
        return resolvedLibraries;
    }

    private boolean shouldSkipAutomaticDependency(String dependency) {
        String[] parts = dependency.split(":");
        if (parts.length != 3) {
            return true;
        }
        String group = parts[0];
        String artifact = parts[1];
        return ("androidx.appcompat".equals(group) && "appcompat".equals(artifact))
                || ("com.google.android.material".equals(group) && "material".equals(artifact))
                || artifact.endsWith("-bom");
    }

    private String toLocalLibraryFolderName(String dependency) {
        String[] parts = dependency.split(":");
        if (parts.length != 3) {
            return "";
        }
        return parts[1].trim() + "-v" + parts[2].trim();
    }

    private boolean isLocalLibraryDownloaded(String libraryName) {
        File libraryDirectory = getLocalLibraryDirectory(libraryName);
        return new File(libraryDirectory, "classes.jar").exists()
                || new File(libraryDirectory, "classes.dex").exists()
                || new File(libraryDirectory, "res").isDirectory();
    }

    private File getLocalLibraryDirectory(String libraryName) {
        return new File(FileUtil.getExternalStorageDir(), ".sketchware/libs/local_libs/" + libraryName);
    }

    private boolean hasUsableLocalLibraryArtifact(HashMap<String, Object> library) {
        return library.containsKey("jarPath")
                || library.containsKey("dexPath")
                || library.containsKey("resPath")
                || library.containsKey("assetsPath");
    }

    private boolean usesAndroidxOrMaterial() {
        return fileContains(new File(projectRoot, "app" + File.separator + "build.gradle"), "androidx")
                || fileContains(new File(projectRoot, "app" + File.separator + "build.gradle"), "appcompat")
                || fileContains(new File(projectRoot, "app" + File.separator + "build.gradle"), "material")
                || fileContains(new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "AndroidManifest.xml"), "AppCompat")
                || fileContains(new File(projectRoot, "app" + File.separator + "src" + File.separator + "main"), "androidx.appcompat")
                || fileContains(new File(projectRoot, "app" + File.separator + "src" + File.separator + "main"), "com.google.android.material");
    }

    private boolean fileContains(File file, String needle) {
        if (file == null || !file.exists() || needle == null) {
            return false;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return false;
            }
            for (File child : children) {
                if (child.isDirectory() && shouldSkip(child)) {
                    continue;
                }
                if (child.isFile() && canOpen(child) && fileContains(child, needle)) {
                    return true;
                }
                if (child.isDirectory() && fileContains(child, needle)) {
                    return true;
                }
            }
            return false;
        }
        if (file.length() > MAX_OPEN_BYTES) {
            return false;
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains(needle);
        } catch (IOException ignored) {
            return false;
        }
    }

    private final StringBuilder buildOutputBuffer = new StringBuilder();
    private final java.util.concurrent.atomic.AtomicBoolean buildOutputFlushScheduled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** Keep only the tail of the log in the TextView to bound rendering cost. */
    private static final int MAX_OUTPUT_CHARS = 200_000;

    private void appendBuildOutput(String message) {
        // ANR FIX: previously this read the ENTIRE output TextView and re-set it
        // (getText() + setText(current + message)) on every single build line —
        // O(n²) string work + a fullScroll relayout per line. On big builds the
        // main-thread message queue floods and Android shows "Sketchware não está
        // respondendo" even though the build runs fine on a worker thread.
        //
        // Now lines are buffered and flushed at most every ~150 ms with a single
        // incremental append() and one scroll.
        synchronized (buildOutputBuffer) {
            buildOutputBuffer.append(message).append('\n');
        }
        if (buildOutputFlushScheduled.compareAndSet(false, true)) {
            binding.studioOutput.postDelayed(this::flushBuildOutput, 150L);
        }
    }

    private void flushBuildOutput() {
        buildOutputFlushScheduled.set(false);
        String pending;
        synchronized (buildOutputBuffer) {
            if (buildOutputBuffer.length() == 0) {
                return;
            }
            pending = buildOutputBuffer.toString();
            buildOutputBuffer.setLength(0);
        }
        binding.studioOutput.append(pending);
        CharSequence full = binding.studioOutput.getText();
        if (full.length() > MAX_OUTPUT_CHARS) {
            int cut = full.length() - MAX_OUTPUT_CHARS;
            binding.studioOutput.setText(full.subSequence(cut, full.length()));
        }
        int lastLineBreak = pending.lastIndexOf('\n', pending.length() - 2);
        updateOutputSummary(lastLineBreak >= 0 ? pending.substring(lastLineBreak + 1) : pending);
        binding.studioOutputContainer.post(() -> binding.studioOutputContainer.fullScroll(View.FOCUS_DOWN));
    }

    private void setBuildRunning(boolean running) {
        buildRunning = running;
        binding.studioProgress.setVisibility(running ? View.VISIBLE : View.GONE);
        binding.studioOutputTitle.setText(running ? R.string.studio_build_started : R.string.studio_build_output);
        MenuItem buildItem = binding.studioToolbar.getMenu().findItem(MENU_BUILD);
        if (buildItem != null) {
            buildItem.setEnabled(!running);
        }
        if (running) {
            expandOutputPanel();
        }
        updateStatus(running ? getString(R.string.studio_build_started) : getString(R.string.studio_output_ready));
    }

    private void updateFileHeader(File file) {
        if (file == null) {
            binding.studioSelectedFile.setText(R.string.studio_select_file);
            binding.studioSelectedPath.setText("");
            binding.studioSelectedIcon.setImageResource(R.drawable.ic_file_default);
            binding.studioSelectedIcon.setImageTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.studio_file_default, getTheme())
            ));
            return;
        }
        boolean directory = file.isDirectory();
        binding.studioSelectedFile.setText(file.getName());
        binding.studioSelectedPath.setText(ProjectPathResolver.toDisplayPath(scId, file));
        binding.studioSelectedIcon.clearColorFilter();
        binding.studioSelectedIcon.setImageTintList(null);
        binding.studioSelectedIcon.setImageResource(iconFor(file, directory));
        binding.studioSelectedIcon.setImageTintList(ColorStateList.valueOf(iconColorFor(file, directory)));
    }

    private void showEditor() {
        showingImage = false;
        updateStage();
    }

    private void showOutput() {
        expandOutputPanel();
    }

    private void updateStage() {
        boolean hasFile = currentFile != null;
        boolean showCode = hasFile && currentFileEditable && !showingImage;
        boolean showImage = hasFile && showingImage;
        boolean showEmpty = !showCode && !showImage;

        if (!hasFile) {
            binding.studioEmptyTitle.setText(R.string.studio_no_file_title);
            binding.studioEmptyMessage.setText(R.string.studio_no_file_message);
        }

        binding.studioEditorStack.setVisibility(showCode ? View.VISIBLE : View.GONE);
        binding.studioFileTabs.setVisibility(openFileTabs.isEmpty() ? View.GONE : View.VISIBLE);
        binding.studioImagePreviewContainer.setVisibility(showImage ? View.VISIBLE : View.GONE);
        binding.studioEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (!showCode && binding.studioSearchReplacePanel.getVisibility() == View.VISIBLE) {
            hideSearchReplacePanel();
        } else if (showCode && binding.studioSearchReplacePanel.getVisibility() == View.VISIBLE) {
            updateSearchQuery(false);
        }
        updateToolbarMenus();
    }

    private void setOutput(String message, boolean selectOutput) {
        binding.studioOutput.setText(message);
        updateOutputSummary(message);
        if (selectOutput) {
            showOutput();
        }
    }

    private void updateOutputSummary(String message) {
        String summary = message == null ? "" : message.trim();
        if (summary.isEmpty()) {
            return;
        }
        int lineBreak = summary.indexOf('\n');
        binding.studioOutputSummary.setText(lineBreak >= 0 ? summary.substring(0, lineBreak) : summary);
    }

    private void updateStatus(String status) {
        binding.studioStatus.setText(status);
    }

    private boolean hasUnsavedChanges() {
        return currentFileEditable && currentFile != null && !lastSavedContent.equals(getActiveEditor().getText().toString());
    }

    private void showCompileErrorGuide() {
        String source = lastBuildErrors;
        CharSequence output = binding.studioOutput.getText();
        if ((source == null || source.trim().isEmpty()) && output != null) {
            source = output.toString();
        }
        String guide = buildErrorGuide(source);
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_warning)
                .setTitle(R.string.studio_action_errors)
                .setMessage(guide)
                .setPositiveButton(R.string.common_word_ok, null)
                .show();
    }

    private String buildErrorGuide(String log) {
        if (log == null || log.trim().isEmpty()) {
            return getString(R.string.studio_errors_empty);
        }
        StringBuilder guide = new StringBuilder();
        String[] lines = log.split("\\r?\\n");
        int count = 0;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.contains("error") || lower.contains("exception") || lower.contains("missing") || lower.contains("failed")) {
                guide.append("- ").append(line.trim()).append('\n');
                count++;
                if (count >= 12) {
                    break;
                }
            }
        }
        if (count == 0) {
            guide.append(log.length() > 1600 ? log.substring(0, 1600) : log);
        } else {
            guide.append('\n').append(getString(R.string.studio_errors_hint));
        }
        return guide.toString().trim();
    }

    private void showDependencyActions() {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            SketchwareUtil.toast(getString(R.string.studio_project_missing));
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(14));
        sheet.setBackground(createSheetBackground());

        TextView title = new TextView(this);
        title.setText(R.string.studio_action_dependencies);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getResources().getColor(R.color.studio_text_primary, getTheme()));
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.studio_dependencies_sheet_subtitle);
        subtitle.setTextSize(12);
        subtitle.setTextColor(getResources().getColor(R.color.studio_text_secondary, getTheme()));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(2), 0, dp(10));
        sheet.addView(subtitle, subtitleParams);

        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_sync, R.string.studio_dependencies_detect, true, this::showDetectedDependencies);
        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_download, R.string.studio_dependencies_manual, true, () -> showDependencyDownloader(new ArrayList<>()));
        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_firebase, R.string.studio_dependencies_firebase_analytics, true, this::showFirebaseAnalyticsDownloader);

        dialog.setContentView(sheet);
        dialog.setOnShowListener(shownDialog -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void showDetectedDependencies() {
        logStudioAction("detect_dependencies");
        List<String> dependencies = detectProjectDependencies();
        if (dependencies.isEmpty()) {
            setOutput(getString(R.string.studio_dependencies_none), true);
            SketchwareUtil.toast(getString(R.string.studio_dependencies_none));
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_package)
                .setTitle(getString(R.string.studio_dependencies_detected_title, dependencies.size()))
                .setMessage(formatDetectedDependencies(dependencies))
                .setPositiveButton(R.string.studio_dependencies_download_all, (dialog, which) -> showDependencyDownloader(dependencies))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showFirebaseAnalyticsDownloader() {
        ArrayList<String> dependencies = new ArrayList<>();
        dependencies.add(FIREBASE_ANALYTICS_DEPENDENCY);
        logStudioAction("add_firebase_analytics");
        showDependencyDownloader(dependencies);
    }

    private String formatDetectedDependencies(List<String> dependencies) {
        StringBuilder message = new StringBuilder(getString(R.string.studio_dependencies_detected_message));
        int max = Math.min(18, dependencies.size());
        for (int i = 0; i < max; i++) {
            message.append('\n').append("- ").append(dependencies.get(i));
        }
        int remaining = dependencies.size() - max;
        if (remaining > 0) {
            message.append('\n').append(getString(R.string.studio_dependencies_more_count, remaining));
        }
        return message.toString();
    }

    private void showDependencyDownloader(List<String> dependencies) {
        if (getSupportFragmentManager().findFragmentByTag("studio_dependency_downloader") != null) {
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putBoolean("notAssociatedWithProject", false);
        bundle.putSerializable("buildSettings", new BuildSettings(scId));
        bundle.putString("localLibFile", LocalLibrariesUtil.getLocalLibFile(scId).getAbsolutePath());
        if (dependencies != null && !dependencies.isEmpty()) {
            bundle.putStringArrayList("dependencies", new ArrayList<>(dependencies));
        }

        LibraryDownloaderDialogFragment fragment = new LibraryDownloaderDialogFragment();
        fragment.setArguments(bundle);
        fragment.setOnLibraryDownloadedTask(() -> {
            updateStatus(getString(R.string.studio_dependencies_download_finished));
            SketchwareUtil.toast(getString(R.string.studio_dependencies_download_finished));
        });
        fragment.show(getSupportFragmentManager(), "studio_dependency_downloader");
    }

    private List<String> detectProjectDependencies() {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        List<File> gradleFiles = new ArrayList<>();
        collectGradleFiles(projectRoot, 0, gradleFiles);
        for (File gradleFile : gradleFiles) {
            collectGradleDependencies(readTextFile(gradleFile), dependencies);
        }
        collectVersionCatalogDependencies(dependencies);
        return new ArrayList<>(dependencies);
    }

    private void collectGradleFiles(File directory, int depth, List<File> gradleFiles) {
        if (directory == null || depth > 4 || shouldSkip(directory)) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectGradleFiles(child, depth + 1, gradleFiles);
                continue;
            }
            String name = child.getName().toLowerCase(Locale.US);
            if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                gradleFiles.add(child);
            }
        }
    }

    private void collectGradleDependencies(String content, Set<String> dependencies) {
        if (content == null || content.isEmpty()) {
            return;
        }
        Matcher stringMatcher = GRADLE_STRING_DEPENDENCY_PATTERN.matcher(content);
        while (stringMatcher.find()) {
            addResolvableDependency(dependencies, stringMatcher.group(1));
        }

        Matcher groovyMapMatcher = GRADLE_MAP_GROOVY_DEPENDENCY_PATTERN.matcher(content);
        while (groovyMapMatcher.find()) {
            addResolvableDependency(dependencies,
                    groovyMapMatcher.group(1) + ":" + groovyMapMatcher.group(2) + ":" + groovyMapMatcher.group(3));
        }

        Matcher ktsMapMatcher = GRADLE_MAP_KTS_DEPENDENCY_PATTERN.matcher(content);
        while (ktsMapMatcher.find()) {
            addResolvableDependency(dependencies,
                    ktsMapMatcher.group(1) + ":" + ktsMapMatcher.group(2) + ":" + ktsMapMatcher.group(3));
        }
    }

    private void collectVersionCatalogDependencies(Set<String> dependencies) {
        File catalog = new File(projectRoot, "gradle" + File.separator + "libs.versions.toml");
        String content = readTextFile(catalog);
        if (content.isEmpty()) {
            return;
        }

        HashMap<String, String> versions = new HashMap<>();
        String section = "";
        String[] lines = content.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = stripTomlComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            if ("versions".equals(section)) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = unquote(line.substring(separator + 1).trim());
                if (!key.isEmpty() && !value.isEmpty()) {
                    versions.put(key, value);
                }
            }
        }

        section = "";
        for (String rawLine : lines) {
            String line = stripTomlComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            if (!"libraries".equals(section)) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String coordinate = parseVersionCatalogLibrary(line.substring(separator + 1).trim(), versions);
            addResolvableDependency(dependencies, coordinate);
        }
    }

    private String parseVersionCatalogLibrary(String value, HashMap<String, String> versions) {
        String simpleCoordinate = unquote(value);
        if (simpleCoordinate.split(":").length == 3) {
            return simpleCoordinate;
        }

        String module = findTomlStringValue(value, "module");
        String group = findTomlStringValue(value, "group");
        String name = findTomlStringValue(value, "name");
        String version = findTomlStringValue(value, "version");
        String versionRef = findTomlStringValue(value, "version.ref");
        if ((version == null || version.isEmpty()) && versionRef != null) {
            version = versions.get(versionRef);
        }

        if (module != null && !module.isEmpty()) {
            String[] moduleParts = module.split(":");
            if (moduleParts.length == 3) {
                return module;
            }
            if (moduleParts.length == 2 && version != null && !version.isEmpty()) {
                return module + ":" + version;
            }
        }
        if (group != null && name != null && version != null) {
            return group + ":" + name + ":" + version;
        }
        return "";
    }

    private String findTomlStringValue(String source, String key) {
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_.-])" + Pattern.quote(key) + "\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String stripTomlComment(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '#' && !inSingleQuote && !inDoubleQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private String unquote(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private void addResolvableDependency(Set<String> dependencies, String coordinate) {
        String normalized = normalizeMavenCoordinate(coordinate);
        if (!normalized.isEmpty()) {
            dependencies.add(normalized);
        }
    }

    private String normalizeMavenCoordinate(String coordinate) {
        if (coordinate == null) {
            return "";
        }
        String normalized = coordinate.trim();
        int artifactSuffix = normalized.indexOf('@');
        if (artifactSuffix > 0) {
            normalized = normalized.substring(0, artifactSuffix);
        }
        String[] parts = normalized.split(":");
        if (parts.length != 3) {
            return "";
        }
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()
                    || part.contains("$")
                    || part.contains("+")
                    || part.contains("[")
                    || part.contains("]")) {
                return "";
            }
        }
        if ("com.android.tools.build".equals(parts[0]) && "gradle".equals(parts[1])) {
            return "";
        }
        return parts[0].trim() + ":" + parts[1].trim() + ":" + parts[2].trim();
    }

    private String readTextFile(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_OPEN_BYTES) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private void showCreateFileDialog() {
        File directory = getTargetDirectory();
        EditText input = createDialogInput(getString(R.string.studio_new_file_hint));
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_add)
                .setTitle(getString(R.string.studio_action_new_file) + "\n" + relativePath(directory))
                .setView(input)
                .setPositiveButton(R.string.common_word_create, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    createFileIn(directory, name, "");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showRenameDialog() {
        File target = getSelectedTarget();
        if (target == null || target.equals(projectRoot)) {
            SketchwareUtil.toast(getString(R.string.studio_select_file));
            return;
        }
        EditText input = createDialogInput(target.getName());
        input.setText(target.getName());
        input.setSelectAllOnFocus(true);
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_edit)
                .setTitle(R.string.studio_action_rename)
                .setView(input)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> renameTarget(target, input.getText().toString().trim()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showDeleteDialog() {
        File target = getSelectedTarget();
        if (target == null || target.equals(projectRoot)) {
            SketchwareUtil.toast(getString(R.string.studio_select_file));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_delete)
                .setTitle(R.string.studio_action_delete)
                .setMessage(getString(R.string.studio_delete_confirm) + "\n" + relativePath(target))
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> deleteTarget(target))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showAddResourceDialog() {
        String[] labels = {
                getString(R.string.studio_resource_layout),
                getString(R.string.studio_resource_drawable),
                getString(R.string.studio_resource_values),
                getString(R.string.studio_resource_raw)
        };
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_file_present)
                .setTitle(R.string.studio_action_add_resource)
                .setItems(labels, (dialog, which) -> showResourceNameDialog(which))
                .show();
    }

    private void showResourceNameDialog(int type) {
        EditText input = createDialogInput(type == 0 ? "activity_example.xml" : "resource_name.xml");
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_file_present)
                .setTitle(R.string.studio_action_add_resource)
                .setView(input)
                .setPositiveButton(R.string.common_word_create, (dialog, which) -> createResource(type, input.getText().toString().trim()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void pickIconResource() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_STUDIO_ICON);
    }

    private void addTextColorToCurrentLayout() {
        ensureTextColorResources();
        if (currentFile == null || !currentFileEditable || !isLayoutXml(currentFile)) {
            openFile(getValuesFile("colors.xml"), true);
            return;
        }
        String content = getActiveEditor().getText().toString();
        String updated = addTextColorAttribute(content, "TextView");
        updated = addTextColorAttribute(updated, "Button");
        updated = addTextColorAttribute(updated, "EditText");
        if (!updated.equals(content)) {
            getActiveEditor().setText(updated);
            updateStatus(relativePath(currentFile) + " - " + getString(R.string.studio_text_color_added));
        } else {
            setOutput(getString(R.string.studio_text_color_exists), true);
        }
    }

    private String addTextColorAttribute(String content, String tagName) {
        int start = content.indexOf("<" + tagName);
        while (start != -1) {
            int end = content.indexOf(">", start);
            if (end == -1) {
                return content;
            }
            String tag = content.substring(start, end);
            if (!tag.contains("android:textColor")) {
                return content.substring(0, end)
                        + "\n        android:textColor=\"@color/textColorPrimary\""
                        + content.substring(end);
            }
            start = content.indexOf("<" + tagName, end);
        }
        return content;
    }

    private void ensureTextColorResources() {
        File colorsFile = getValuesFile("colors.xml");
        File parent = colorsFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        String content = colorsFile.exists() ? FileUtil.readFile(colorsFile.getAbsolutePath()) : "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n";
        if (!content.contains("name=\"textColorPrimary\"")) {
            content = content.replace("</resources>", "    <color name=\"textColorPrimary\">#1C1B1F</color>\n</resources>");
        }
        if (!content.contains("name=\"textColorSecondary\"")) {
            content = content.replace("</resources>", "    <color name=\"textColorSecondary\">#5F5E62</color>\n</resources>");
        }
        FileUtil.writeFile(colorsFile.getAbsolutePath(), content);
        rebuildVisibleNodes();
    }

    private EditText createDialogInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));
        return input;
    }

    private File getSelectedTarget() {
        if (selectedNodeFile != null) {
            return selectedNodeFile;
        }
        if (currentFile != null) {
            return currentFile;
        }
        return projectRoot;
    }

    private File getTargetDirectory() {
        File target = getSelectedTarget();
        if (target == null) {
            return projectRoot;
        }
        return target.isDirectory() ? target : target.getParentFile();
    }

    private void createFileIn(File directory, String name, String content) {
        try {
            if (directory == null || !directory.isDirectory()) {
                directory = projectRoot;
            }
            File target = new File(directory, name);
            if (!isInsideProject(target)) {
                throw new IOException("File is outside the project root");
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!target.exists()) {
                Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }
            selectedNodeFile = target;
            expandDirectory(target.getParentFile());
            rebuildVisibleNodes();
            if (target.isFile()) {
                openFile(target, true);
            }
        } catch (Exception e) {
            setOutput(getString(R.string.studio_create_failed) + ": " + e.getMessage(), true);
        }
    }

    private void createResource(int type, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return;
        }
        String name = sanitizeResourceFileName(rawName);
        File directory;
        String content;
        switch (type) {
            case 0 -> {
                directory = getResDirectory("layout");
                if (!name.endsWith(".xml")) {
                    name += ".xml";
                }
                content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"match_parent\"\n"
                        + "    android:orientation=\"vertical\" />\n";
            }
            case 1 -> {
                directory = getResDirectory("drawable");
                if (!name.endsWith(".xml")) {
                    name += ".xml";
                }
                content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <solid android:color=\"@color/colorPrimary\" />\n"
                        + "    <corners android:radius=\"12dp\" />\n"
                        + "</shape>\n";
            }
            case 2 -> {
                directory = getResDirectory("values");
                if (!name.endsWith(".xml")) {
                    name += ".xml";
                }
                content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n";
            }
            default -> {
                directory = getResDirectory("raw");
                content = "";
            }
        }
        createFileIn(directory, name, content);
    }

    private void renameTarget(File target, String newName) {
        if (newName == null || newName.isEmpty() || target == null) {
            return;
        }
        try {
            File renamed = new File(target.getParentFile(), newName);
            if (!isInsideProject(renamed)) {
                throw new IOException("File is outside the project root");
            }
            Files.move(target.toPath(), renamed.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (currentFile != null && canonicalPath(currentFile).equals(canonicalPath(target))) {
                currentFile = renamed;
            }
            selectedNodeFile = renamed;
            rebuildVisibleNodes();
            updateFileHeader(renamed);
            updateStatus(getString(R.string.studio_renamed));
        } catch (Exception e) {
            setOutput(getString(R.string.studio_rename_failed) + ": " + e.getMessage(), true);
        }
    }

    private void deleteTarget(File target) {
        try {
            if (target == null || !isInsideProject(target)) {
                return;
            }
            FileUtil.deleteFile(target.getAbsolutePath());
            if (currentFile != null && canonicalPath(currentFile).equals(canonicalPath(target))) {
                currentFile = null;
                currentFileEditable = false;
                selectedNodeFile = projectRoot;
                setOutput(getString(R.string.studio_deleted), false);
            }
            rebuildVisibleNodes();
            updateStage();
            updateStatus(getString(R.string.studio_deleted));
        } catch (Exception e) {
            setOutput(getString(R.string.studio_delete_failed) + ": " + e.getMessage(), true);
        }
    }

    private File getResDirectory(String name) {
        return new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "res" + File.separator + name);
    }

    private File getValuesFile(String name) {
        return new File(getResDirectory("values"), name);
    }

    private String sanitizeResourceFileName(String raw) {
        String value = raw.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9_.]+", "_");
        while (value.startsWith("_") || value.startsWith(".")) {
            value = value.substring(1);
        }
        return value.isEmpty() ? "resource.xml" : value;
    }

    private File findInitialFile(File root) {
        List<File> candidates = new ArrayList<>();
        collectCandidateFiles(root, candidates);
        candidates.sort(Comparator
                .comparingInt(this::priorityForInitialFile)
                .thenComparing(this::relativePath));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void collectCandidateFiles(File directory, List<File> candidates) {
        if (directory == null || candidates.size() >= MAX_INITIAL_SCAN_FILES) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            if (child.isDirectory()) {
                collectCandidateFiles(child, candidates);
            } else if (canOpen(child)) {
                candidates.add(child);
            }
            if (candidates.size() >= MAX_INITIAL_SCAN_FILES) {
                return;
            }
        }
    }

    private int priorityForInitialFile(File file) {
        String relative = relativePath(file).toLowerCase(Locale.US);
        String name = file.getName().toLowerCase(Locale.US);
        if (name.equals("mainactivity.java")) {
            return 0;
        }
        if (relative.contains("/src/main/java/") && name.endsWith(".java")) {
            return 1;
        }
        if (relative.contains("/src/main/kotlin/") && name.endsWith(".kt")) {
            return 2;
        }
        if (name.equals("androidmanifest.xml")) {
            return 3;
        }
        if (relative.contains("/src/main/res/layout/") && name.endsWith(".xml")) {
            return 4;
        }
        if (name.endsWith(".java") || name.endsWith(".kt")) {
            return 5;
        }
        if (name.endsWith(".xml")) {
            return 6;
        }
        return 7;
    }

    private boolean shouldSkip(File file) {
        if (!file.isDirectory()) {
            return false;
        }
        String name = file.getName();
        return name.equals("build")
                || name.equals(".gradle")
                || name.equals(".git")
                || name.equals(".idea")
                || name.equals(".cxx");
    }

    private boolean canOpen(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_OPEN_BYTES) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.US);
        return hasKnownTextExtension(name) || isLikelyTextFile(file);
    }

    private boolean hasKnownTextExtension(String name) {
        return name.endsWith(".java")
                || name.endsWith(".kt")
                || name.endsWith(".kts")
                || name.endsWith(".xml")
                || name.endsWith(".gradle")
                || name.endsWith(".html")
                || name.endsWith(".htm")
                || name.endsWith(".css")
                || name.endsWith(".scss")
                || name.endsWith(".js")
                || name.endsWith(".jsx")
                || name.endsWith(".ts")
                || name.endsWith(".tsx")
                || name.endsWith(".svg")
                || name.endsWith(".aidl")
                || name.endsWith(".c")
                || name.endsWith(".cpp")
                || name.endsWith(".h")
                || name.endsWith(".hpp")
                || name.endsWith(".sh")
                || name.endsWith(".bat")
                || name.endsWith(".properties")
                || name.endsWith(".pro")
                || name.endsWith(".txt")
                || name.endsWith(".md")
                || name.endsWith(".json")
                || name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".toml")
                || name.equals(".gitignore");
    }

    private boolean isLikelyTextFile(File file) {
        if (file.length() == 0) {
            return true;
        }
        int sampleSize = (int) Math.min(2048, file.length());
        byte[] buffer = new byte[sampleSize];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read = inputStream.read(buffer);
            if (read <= 0) {
                return true;
            }
            int controlChars = 0;
            for (int i = 0; i < read; i++) {
                int value = buffer[i] & 0xFF;
                if (value == 0) {
                    return false;
                }
                boolean isAllowedWhitespace = value == '\n' || value == '\r' || value == '\t';
                if (value < 32 && !isAllowedWhitespace) {
                    controlChars++;
                }
            }
            return controlChars < read / 10;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void expandDirectory(File directory) {
        if (directory != null && directory.exists() && directory.isDirectory()) {
            expandedDirs.add(canonicalPath(directory));
        }
    }

    private boolean isInsideProject(File file) throws IOException {
        if (projectRoot == null || file == null) {
            return false;
        }
        String rootPath = projectRoot.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private String relativePath(File file) {
        if (file == null) {
            return "";
        }
        if (projectRoot == null) {
            return file.getName();
        }
        try {
            return projectRoot.toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize())
                    .toString()
                    .replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return file.getName();
        }
    }

    private String parentRelativePath(File file) {
        File parent = file.getParentFile();
        if (parent == null || parent.equals(projectRoot)) {
            return "";
        }
        return relativePath(parent);
    }

    private String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    private int iconFor(File file, boolean directory) {
        if (file == null) {
            return directory ? R.drawable.ic_folder_default : R.drawable.ic_file_default;
        }
        String name = file.getName().toLowerCase(Locale.US);
        String relative = relativePath(file).toLowerCase(Locale.US);

        if (directory) {
            if (name.equals("app")) return R.drawable.ic_folder_app;
            if (name.equals("src")) return R.drawable.ic_folder_source;
            if (name.equals("main")) return R.drawable.ic_folder_source;
            if (name.equals("java")) return R.drawable.ic_folder_java;
            if (name.equals("kotlin")) return R.drawable.ic_folder_kotlin;
            if (name.equals("res")) return R.drawable.ic_folder_res;
            if (name.equals("layout")) return R.drawable.ic_folder_layout;
            if (name.startsWith("drawable")) return R.drawable.ic_folder_image;
            if (name.startsWith("mipmap")) return R.drawable.ic_folder_image;
            if (name.equals("values")) return R.drawable.ic_folder_values;
            if (name.equals("raw")) return R.drawable.ic_folder_raw;
            if (name.equals("assets")) return R.drawable.ic_folder_assets;
            if (name.equals("gradle") || name.equals(".gradle")) return R.drawable.ic_folder_gradle;
            if (name.equals("build")) return R.drawable.ic_folder_build;

            return R.drawable.ic_folder_default;
        }

        if (name.equals("androidmanifest.xml")) return R.drawable.ic_file_manifest;
        if (name.endsWith(".kt") || name.endsWith(".kts")) return R.drawable.ic_file_kotlin;
        if (name.endsWith(".java")) return R.drawable.ic_file_java;
        if (name.endsWith(".gradle")) return R.drawable.ic_file_gradle;
        if (name.endsWith(".properties")) return R.drawable.ic_file_properties;
        if (name.endsWith(".json")) return R.drawable.ic_file_json;

        if (name.endsWith(".xml")) {
            if (relative.contains("/res/layout/")) return R.drawable.ic_file_layout;
            if (relative.contains("/res/drawable")) return R.drawable.ic_file_image_xml;
            if (relative.contains("/res/mipmap")) return R.drawable.ic_file_image_xml;
            if (relative.contains("/res/values/")) return R.drawable.ic_file_values;
            return R.drawable.ic_file_xml;
        }

        if (isPreviewableImage(file)) return R.drawable.ic_file_image;
        if (name.endsWith(".bat") || name.endsWith(".sh")) return R.drawable.ic_file_terminal;
        if (name.endsWith(".txt") || name.endsWith(".md")) return R.drawable.ic_file_text;
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".css") || name.endsWith(".js")
                || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx")) {
            return R.drawable.ic_file_web;
        }

        return R.drawable.ic_file_default;
    }

    private int iconColorFor(File file, boolean directory) {
        if (file == null) {
            return getResources().getColor(
                    directory ? R.color.studio_folder_default : R.color.studio_file_default,
                    getTheme()
            );
        }
        String name = file.getName().toLowerCase(Locale.US);

        if (directory) {
            if (name.equals("res") || name.equals("layout") || name.startsWith("drawable")
                    || name.startsWith("mipmap") || name.equals("values") || name.equals("raw")
                    || name.equals("assets")) {
                return getResources().getColor(R.color.studio_folder_resource, getTheme());
            }

            if (name.equals("java") || name.equals("kotlin") || name.equals("src")
                    || name.equals("main") || name.equals("app")) {
                return getResources().getColor(R.color.studio_folder_code, getTheme());
            }

            if (name.equals("gradle") || name.equals(".gradle") || name.equals("build")) {
                return getResources().getColor(R.color.studio_folder_config, getTheme());
            }

            return getResources().getColor(R.color.studio_folder_default, getTheme());
        }

        if (name.endsWith(".kt") || name.endsWith(".kts")) {
            return getResources().getColor(R.color.studio_file_kotlin, getTheme());
        }

        if (name.endsWith(".java")) {
            return getResources().getColor(R.color.studio_file_java, getTheme());
        }

        if (name.endsWith(".xml")) {
            return getResources().getColor(R.color.studio_file_xml, getTheme());
        }

        if (name.endsWith(".gradle") || name.endsWith(".properties") || name.endsWith(".toml") || name.endsWith(".pro")) {
            return getResources().getColor(R.color.studio_file_config, getTheme());
        }

        if (name.endsWith(".json")) {
            return getResources().getColor(R.color.studio_file_json, getTheme());
        }

        if (isPreviewableImage(file)) {
            return getResources().getColor(R.color.studio_file_image, getTheme());
        }

        return getResources().getColor(R.color.studio_file_default, getTheme());
    }

    private String extensionLabel(File file) {
        if (file == null || file.isDirectory()) {
            return "";
        }

        String name = file.getName().toLowerCase(Locale.US);
        String relative = relativePath(file).toLowerCase(Locale.US);

        if (name.equals("androidmanifest.xml")) return "MANIFEST";
        if (name.endsWith(".kt")) return "KOTLIN";
        if (name.endsWith(".kts")) return "KOTLIN";
        if (name.endsWith(".java")) return "JAVA";
        if (name.endsWith(".gradle")) return "GRADLE";
        if (name.endsWith(".properties")) return "PROPERTIES";
        if (name.endsWith(".json")) return "JSON";

        if (name.endsWith(".xml")) {
            if (relative.contains("/res/layout/")) return "LAYOUT";
            if (relative.contains("/res/values/")) return "VALUES";
            if (relative.contains("/res/drawable")) return "DRAWABLE";
            if (relative.contains("/res/mipmap")) return "MIPMAP";
            return "XML";
        }

        if (isPreviewableImage(file)) return "IMAGE";
        if (name.endsWith(".bat")) return "BAT";
        if (name.endsWith(".sh")) return "SH";
        if (name.endsWith(".txt")) return "TEXT";
        if (name.endsWith(".md")) return "MD";

        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toUpperCase(Locale.US) : "FILE";
    }

    private boolean canOpenQuick(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_OPEN_BYTES) {
            return false;
        }
        return hasKnownTextExtension(file.getName().toLowerCase(Locale.US));
    }

    private boolean isLayoutXml(File file) {
        if (file == null) {
            return false;
        }
        String relative = relativePath(file).toLowerCase(Locale.US);
        return relative.contains("/src/main/res/layout/") && file.getName().toLowerCase(Locale.US).endsWith(".xml");
    }

    private boolean isPreviewableImage(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp")
                || name.endsWith(".gif")
                || name.endsWith(".bmp");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_STUDIO_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            savePickedIcon(data.getData());
        }
    }

    private void savePickedIcon(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Could not open selected image");
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                throw new IOException("Unsupported image");
            }
            String name = sanitizeResourceFileName(nameFromUri(uri));
            if (name.endsWith(".png")) {
                name = name.substring(0, name.length() - 4);
            }
            if (!name.matches("[a-z][a-z0-9_]*")) {
                name = "icon_" + System.currentTimeMillis();
            }
            File target = new File(getResDirectory("drawable"), name + ".png");
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream outputStream = new FileOutputStream(target)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
            selectedNodeFile = target;
            expandDirectory(target.getParentFile());
            rebuildVisibleNodes();
            openImageFile(target, true);
            SketchwareUtil.toast(getString(R.string.studio_icon_added));
        } catch (Exception e) {
            setOutput(getString(R.string.studio_icon_failed) + ": " + e.getMessage(), true);
        }
    }

    private String nameFromUri(Uri uri) {
        String raw = uri == null ? "" : uri.getLastPathSegment();
        if (raw == null || raw.trim().isEmpty()) {
            return "icon";
        }
        int slash = raw.lastIndexOf('/');
        if (slash >= 0 && slash < raw.length() - 1) {
            raw = raw.substring(slash + 1);
        }
        int colon = raw.lastIndexOf(':');
        if (colon >= 0 && colon < raw.length() - 1) {
            raw = raw.substring(colon + 1);
        }
        int dot = raw.lastIndexOf('.');
        if (dot > 0) {
            raw = raw.substring(0, dot);
        }
        return raw;
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    private String extensionOf(File file) {
        if (file == null) {
            return "none";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "none";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String valueOf(HashMap<String, Object> map, String key, String fallback) {
        if (map == null) {
            return fallback;
        }
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? fallback : text;
    }

    private void closeAfterSave() {
        if (hasUnsavedChanges()) {
            saveCurrentFile(false);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        if (binding.studioDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.studioDrawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (collapseOutputPanel()) {
            return;
        }
        closeAfterSave();
    }

    @Override
    public void onDestroy() {
        if (binding != null) {
            Set<CodeEditor> releasedEditors = new HashSet<>();
            for (OpenFileTab openFileTab : openFileTabs) {
                if (releasedEditors.add(openFileTab.editor)) {
                    openFileTab.editor.release();
                }
            }
            if (releasedEditors.add(binding.studioEditor)) {
                binding.studioEditor.release();
            }
        }
        super.onDestroy();
    }

    private void toggleDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        String key = canonicalPath(directory);
        if (expandedDirs.contains(key)) {
            expandedDirs.remove(key);
        } else {
            expandedDirs.add(key);
        }
        selectedNodeFile = directory;
        rebuildVisibleNodes();
    }

    private void showNodeActionsSheet(View anchor, File target) {
        if (target == null) {
            return;
        }
        selectedNodeFile = target;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(14));
        sheet.setBackground(createSheetBackground());

        TextView title = new TextView(this);
        title.setText(target.getName());
        title.setSingleLine(true);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getResources().getColor(R.color.studio_text_primary, getTheme()));
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView path = new TextView(this);
        path.setText(ProjectPathResolver.toDisplayPath(scId, target));
        path.setSingleLine(true);
        path.setTextSize(12);
        path.setTextColor(getResources().getColor(R.color.studio_text_secondary, getTheme()));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pathParams.setMargins(0, dp(2), 0, dp(10));
        sheet.addView(path, pathParams);

        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_file, R.string.studio_action_open, true, () -> {
                if (target.isDirectory()) {
                    toggleDirectory(target);
                } else {
                    openFile(target, true);
                }
        });
        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_add, R.string.studio_action_new_file, true, () -> {
            selectedNodeFile = target.isDirectory() ? target : target.getParentFile();
            showCreateFileDialog();
        });
        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_edit, R.string.studio_action_rename, !target.equals(projectRoot), this::showRenameDialog);
        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_delete, R.string.studio_action_delete, !target.equals(projectRoot), this::showDeleteDialog);
        addSheetAction(sheet, dialog, R.drawable.ic_mtrl_file_present, R.string.studio_action_add_resource, shouldOfferAddResource(target), this::showAddResourceDialog);
        addSheetAction(sheet, dialog, R.drawable.ic_kelivo_copy, R.string.studio_action_copy_path, true, () -> copyPathToClipboard(target));

        dialog.setContentView(sheet);
        dialog.setOnShowListener(shownDialog -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void addSheetAction(LinearLayout sheet, BottomSheetDialog dialog, int iconRes, int titleRes, boolean enabled, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setAlpha(enabled ? 1f : 0.45f);
        row.setEnabled(enabled);
        if (enabled) {
            row.setBackgroundResource(resolveSelectableItemBackground());
            row.setOnClickListener(v -> {
                dialog.dismiss();
                action.run();
                updateToolbarMenus();
            });
        }

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.studio_text_secondary, getTheme())));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.setMargins(0, 0, dp(16), 0);
        row.addView(icon, iconParams);

        TextView text = new TextView(this);
        text.setText(titleRes);
        text.setTextSize(15);
        text.setTextColor(getResources().getColor(R.color.studio_text_primary, getTheme()));
        row.addView(text, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        sheet.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private GradientDrawable createSheetBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(getResources().getColor(R.color.studio_background, getTheme()));
        float radius = dp(24);
        background.setCornerRadii(new float[]{
                radius, radius,
                radius, radius,
                0, 0,
                0, 0
        });
        return background;
    }

    private int resolveSelectableItemBackground() {
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return outValue.resourceId;
    }

    private boolean shouldOfferAddResource(File target) {
        if (projectRoot == null || target == null) {
            return false;
        }
        File resDirectory = getResDirectory("");
        File candidate = target.isDirectory() ? target : target.getParentFile();
        if (candidate == null) {
            return false;
        }
        try {
            return isInsideProject(candidate)
                    && (candidate.equals(projectRoot)
                    || relativePath(candidate).startsWith("app/src/main")
                    || candidate.getCanonicalPath().startsWith(resDirectory.getCanonicalPath()));
        } catch (IOException ignored) {
            return false;
        }
    }

    private void copyPathToClipboard(File target) {
        if (target == null) {
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("path", target.getAbsolutePath()));
        }
        SketchwareUtil.toast(getString(R.string.studio_path_copied));
        updateStatus(getString(R.string.studio_path_copied));
    }

    private final class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.FileNodeHolder> {

        private final List<FileNode> nodes = new ArrayList<>();

        void submit(List<FileNode> newNodes) {
            nodes.clear();
            nodes.addAll(newNodes);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FileNodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemStudioFileTreeBinding itemBinding = ItemStudioFileTreeBinding.inflate(
                    LayoutInflater.from(parent.getContext()),
                    parent,
                    false
            );
            return new FileNodeHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull FileNodeHolder holder, int position) {
            FileNode node = nodes.get(position);

            // ── Indentation ────────────────────────────────────────────────────
            // Drive the indent spacer to (depth * 18dp) so the row root keeps
            // match_parent width and content never overflows the screen edge.
            ViewGroup.LayoutParams indentParams = holder.binding.treeIndent.getLayoutParams();
            indentParams.width = dp(node.depth * 18);
            holder.binding.treeIndent.setLayoutParams(indentParams);

            // ── Tree connector line (shown only when depth > 0) ─────────────
            holder.binding.treeConnector.setVisibility(node.depth > 0 ? View.VISIBLE : View.INVISIBLE);

            // ── Chevron (expand / collapse indicator for directories) ────────
            // Keep INVISIBLE for files so name aligns with directories.
            if (node.directory) {
                holder.binding.fileChevron.setVisibility(View.VISIBLE);
                holder.binding.fileChevron.setImageResource(
                        expandedDirs.contains(canonicalPath(node.file))
                                ? R.drawable.ic_mtrl_arrow_down
                                : R.drawable.ic_mtrl_chevron_right_24
                );
            } else {
                holder.binding.fileChevron.setVisibility(View.INVISIBLE);
            }

            // ── Icon ─────────────────────────────────────────────────────────
            holder.binding.fileIcon.clearColorFilter();
            holder.binding.fileIcon.setImageTintList(null);
            holder.binding.fileIcon.setImageResource(iconFor(node.file, node.directory));
            holder.binding.fileIcon.setImageTintList(
                    ColorStateList.valueOf(iconColorFor(node.file, node.directory)));

            // ── Name ─────────────────────────────────────────────────────────
            holder.binding.fileName.setText(node.file.getName());

            // ── Badge ────────────────────────────────────────────────────────
            holder.binding.fileBadge.setVisibility(View.VISIBLE);
            holder.binding.fileBadge.setText(node.directory ? "DIR" : extensionLabel(node.file));

            // ── Meta (secondary line) ─────────────────────────────────────────
            // For directories show only the last two path segments to avoid
            // the long relative path that previously disappeared off-screen.
            // For files show "EXTENSION  size".
            String meta;
            if (node.directory) {
                String rel = relativePath(node.file);
                int slash = rel.lastIndexOf('/', rel.length() - 1);
                if (slash > 0) {
                    int prevSlash = rel.lastIndexOf('/', slash - 1);
                    meta = prevSlash >= 0 ? "…/" + rel.substring(prevSlash + 1) : rel;
                } else {
                    meta = rel;
                }
            } else {
                meta = extensionLabel(node.file) + "  " + formatBytes(node.file.length());
            }
            holder.binding.fileMeta.setText(meta);

            // ── Active-file highlight ─────────────────────────────────────────
            holder.binding.getRoot().setBackgroundResource(
                    currentFile != null && !node.directory
                            && canonicalPath(currentFile).equals(canonicalPath(node.file))
                            ? R.drawable.bg_studio_file_selected
                            : 0
            );

            // ── Dimmed for non-openable files ────────────────────────────────
            holder.binding.getRoot().setAlpha(
                    node.directory || canOpenQuick(node.file) || isPreviewableImage(node.file)
                            ? 1f : 0.55f);

            // ── Click: toggle dir / open file ─────────────────────────────────
            holder.binding.getRoot().setOnClickListener(v -> {
                selectedNodeFile = node.file;
                if (node.directory) {
                    toggleDirectory(node.file);
                } else {
                    openFile(node.file, true);
                }
            });

            // ── Long-press: context menu ──────────────────────────────────────
            holder.binding.getRoot().setOnLongClickListener(v -> {
                showNodeActionsSheet(v, node.file);
                return true;
            });

            holder.binding.fileActions.setOnClickListener(
                    v -> showNodeActionsSheet(v, node.file));
        }

        @Override
        public int getItemCount() {
            return nodes.size();
        }

        private final class FileNodeHolder extends RecyclerView.ViewHolder {

            private final ItemStudioFileTreeBinding binding;

            private FileNodeHolder(ItemStudioFileTreeBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static final class OpenFileTab {

        private final File file;
        private final CodeEditor editor;
        private String lastSavedContent;

        private OpenFileTab(File file, CodeEditor editor, String lastSavedContent) {
            this.file = file;
            this.editor = editor;
            this.lastSavedContent = lastSavedContent;
        }
    }

    private static final class EditorNavigationEntry {

        private final String path;
        private final int line;
        private final int column;

        private EditorNavigationEntry(String path, int line, int column) {
            this.path = path;
            this.line = line;
            this.column = column;
        }

        private boolean matches(String otherPath, int otherLine, int otherColumn) {
            return path.equals(otherPath) && line == otherLine && column == otherColumn;
        }
    }

    private static final class FileNode {

        private final File file;
        private final int depth;
        private final boolean directory;

        private FileNode(File file, int depth, boolean directory) {
            this.file = file;
            this.depth = depth;
            this.directory = directory;
        }
    }
}
