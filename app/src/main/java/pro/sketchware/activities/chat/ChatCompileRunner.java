package pro.sketchware.activities.chat;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.ProjectBuilder;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yq;
import a.a.a.zy;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.hey.studios.util.ProjectMapUtils;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.diagnostic.MissingFileException;
import pro.sketchware.utility.FileUtil;

final class ChatCompileRunner implements BuildProgressReceiver {
    interface Listener {
        void onCompileProgress(String line, int step);
        void onCompileFinished(boolean success, String message, String apkPath);
    }

    private static final int TOTAL_STEPS = 20;

    private final WeakReference<Context> contextRef;
    private final String scId;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;

    ChatCompileRunner(Context context, String scId, Listener listener) {
        this.contextRef = new WeakReference<>(context.getApplicationContext());
        this.scId = scId;
        this.listener = listener;
    }

    boolean isRunning() {
        return running;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        executor.execute(this::runBuild);
    }

    private void runBuild() {
        Context context = contextRef.get();
        if (context == null) {
            finish(false, "Context unavailable.", null);
            return;
        }
        try {
            new CompileErrorSaver(scId).startBuildLog();
            yq workspace = new yq(context, scId);
            onProgress("Deleting temporary files...", 1);
            FileUtil.deleteFile(workspace.projectMyscPath);

            workspace.c(context);
            workspace.a();
            workspace.a(context, wq.e("600"));
            if (ProjectMapUtils.getBoolean(lC.b(scId), "custom_icon")) {
                workspace.aa(wq.e() + File.separator + scId + File.separator + "mipmaps");
                if (ProjectMapUtils.getBoolean(lC.b(scId), "isIconAdaptive", false)) {
                    workspace.createLauncherIconXml("""
                            <?xml version="1.0" encoding="utf-8"?>
                            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android" >
                            <background android:drawable="@mipmap/ic_launcher_background"/>
                            <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                            <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                            </adaptive-icon>""");
                } else {
                    workspace.a(wq.e() + File.separator + scId + File.separator + "icon.png");
                }
            }

            onProgress("Generating source code...", 2);
            kC resourceManager = jC.d(scId);
            resourceManager.b(workspace.resDirectoryPath + File.separator + "drawable-xhdpi");
            resourceManager = jC.d(scId);
            resourceManager.c(workspace.resDirectoryPath + File.separator + "raw");
            resourceManager = jC.d(scId);
            resourceManager.a(workspace.assetsPath + File.separator + "fonts");

            ProjectBuilder builder = new ProjectBuilder(this, context, workspace);
            var fileManager = jC.b(scId);
            var dataManager = jC.a(scId);
            var libraryManager = jC.c(scId);
            workspace.a(libraryManager, fileManager, dataManager);
            builder.buildBuiltInLibraryInformation();
            workspace.b(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());
            workspace.f();
            workspace.e();

            builder.maybeExtractAapt2();
            onProgress("Extracting built-in libraries...", 3);
            BuiltInLibraries.extractCompileAssets(this);
            onProgress("AAPT2 is running...", 8);
            builder.compileResources();
            onProgress("Generating view binding...", 11);
            builder.generateViewBinding();
            KotlinCompilerBridge.compileKotlinCodeIfPossible(this, builder);
            onProgress("Java is compiling...", 13);
            builder.compileJavaCode();
            new StringfogHandler(scId).start(this, builder);
            new ProguardHandler(scId).start(this, builder);
            onProgress(builder.getDxRunningText(), 17);
            builder.createDexFilesFromClasses();
            onProgress("Merging DEX files...", 18);
            builder.getDexFilesReady();
            onProgress("Building APK...", 19);
            builder.buildApk();
            onProgress("Signing APK...", 20);
            builder.signDebugApk();
            finish(true, "Build finished: " + workspace.finalToInstallApkPath, workspace.finalToInstallApkPath);
        } catch (MissingFileException e) {
            finish(false, "Missing " + (e.isMissingDirectory() ? "directory" : "file") + ": "
                    + e.getMissingFile().getAbsolutePath(), null);
        } catch (zy e) {
            new CompileErrorSaver(scId).appendLog("BUILD FAILED:\n" + e.getMessage());
            finish(false, e.getMessage(), null);
        } catch (Throwable t) {
            String stackTrace = Log.getStackTraceString(t);
            new CompileErrorSaver(scId).appendLog("BUILD FAILED:\n" + stackTrace);
            finish(false, stackTrace, null);
        }
    }

    @Override
    public void onProgress(String progress, int step) {
        new CompileErrorSaver(scId).appendLog("STEP " + step + "/" + TOTAL_STEPS + ": " + progress);
        if (listener != null) {
            int percent = step <= 0 ? 0 : (step * 100) / TOTAL_STEPS;
            listener.onCompileProgress(progress + " (" + percent + "%)", step);
        }
    }

    private void finish(boolean success, String message, String apkPath) {
        running = false;
        if (listener != null) {
            listener.onCompileFinished(success, message, apkPath);
        }
    }
}
