package mod.hey.studios.project.backup;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;

import pro.sketchware.workspace.RecentProjectsRegistry;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BackupRestoreManager {

    private final Activity act;

    // Needed to refresh the project list after restoring

    private ProjectsFragment projectsFragment;

    private HashMap<Integer, Boolean> backupDialogStates;

    public BackupRestoreManager(Activity act) {
        this.act = act;
    }

    public BackupRestoreManager(Activity act, ProjectsFragment projectsFragment) {
        this.act = act;
        this.projectsFragment = projectsFragment;
    }

    public static String getRestoreIntegratedLocalLibrariesMessage(boolean restoringMultipleBackups, int currentRestoringIndex, int totalAmountOfBackups, String filename) {
        if (!restoringMultipleBackups) {
            return "Looks like the backup file you selected contains some Local libraries. Do you want to copy them to your local_libs directory (if they do not already exist)?";
        } else {
            return "Looks like backup file " + filename + " (" + (currentRestoringIndex + 1) + " out of " + totalAmountOfBackups + ") contains some Local libraries. Do you want to copy them to your local_libs directory (if they do not already exist)?";
        }
    }

    public void backup(String sc_id, String project_name) {
        final String localLibrariesTag = "local libraries";
        final String customBlocksTag = "Custom Blocks";
        backupDialogStates = new HashMap<>();
        backupDialogStates.put(0, false);
        backupDialogStates.put(1, false);

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(act);
        dialog.setIcon(R.drawable.ic_backup);
        dialog.setTitle("Backup Options");

        LinearLayout checkboxContainer = new LinearLayout(act);
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);
        checkboxContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        int dip = (int) SketchwareUtil.getDip(8);
        checkboxContainer.setPadding(dip, dip, dip, dip);

        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            int index;
            Object tag = buttonView.getTag();
            if (tag instanceof String) {
                switch ((String) tag) {
                    case localLibrariesTag:
                        index = 0;
                        break;

                    case customBlocksTag:
                        index = 1;
                        break;

                    default:
                        return;
                }
                backupDialogStates.put(index, isChecked);
            }
        };

        CheckBox includeLocalLibraries = new CheckBox(act);
        includeLocalLibraries.setTag(localLibrariesTag);
        includeLocalLibraries.setText("Include used Local libraries");
        includeLocalLibraries.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        includeLocalLibraries.setOnCheckedChangeListener(listener);
        checkboxContainer.addView(includeLocalLibraries);

        CheckBox includeUsedCustomBlocks = new CheckBox(act);
        includeUsedCustomBlocks.setTag(customBlocksTag);
        includeUsedCustomBlocks.setText("Include used Custom Blocks");
        includeUsedCustomBlocks.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        includeUsedCustomBlocks.setOnCheckedChangeListener(listener);
        checkboxContainer.addView(includeUsedCustomBlocks);

        dialog.setView(checkboxContainer);
        dialog.setPositiveButton("Back up", (v, which) -> {
            v.dismiss();
            doBackup(sc_id, project_name);
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    public static void shareBackupFile(Context context, File swbFile) {
        if (context == null || swbFile == null || !swbFile.isFile()) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext();
            Uri uri = FileProvider.getUriForFile(
                    appContext,
                    appContext.getPackageName() + ".provider",
                    swbFile
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, swbFile.getName());
            intent.setClipData(ClipData.newRawUri("", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, context.getString(R.string.share_editable_project_swb));
            if (!(context instanceof Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(chooser);
        } catch (Exception e) {
            SketchwareUtil.toastError("Error: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    private void doBackup(String sc_id, String project_name) {
        new BackupAsyncTask(new WeakReference<>(act), sc_id, project_name, backupDialogStates)
                .execute("");
    }

    public void backupGameProject(String sc_id, String project_name) {
        HashMap<Integer, Boolean> options = new HashMap<>();
        options.put(0, false);
        options.put(1, false);
        new BackupAsyncTask(new WeakReference<>(act), sc_id, project_name, options).execute("");
    }

    public void backupAndroidStudioProject(String sc_id, String project_name) {
        final boolean[] includeLibs = {false};

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(act);
        dialog.setIcon(R.drawable.ic_backup);
        dialog.setTitle("Backup Options");

        LinearLayout checkboxContainer = new LinearLayout(act);
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);
        checkboxContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        int dip = (int) SketchwareUtil.getDip(8);
        checkboxContainer.setPadding(dip, dip, dip, dip);

        CheckBox includeLocalLibraries = new CheckBox(act);
        includeLocalLibraries.setText("Include used Local libraries");
        includeLocalLibraries.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        includeLocalLibraries.setOnCheckedChangeListener((buttonView, isChecked) -> includeLibs[0] = isChecked);
        checkboxContainer.addView(includeLocalLibraries);

        dialog.setView(checkboxContainer);
        dialog.setPositiveButton("Back up", (v, which) -> {
            v.dismiss();
            new AndroidStudioBackupAsyncTask(new WeakReference<>(act), sc_id, project_name, includeLibs[0]).execute("");
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    /*** Restore ***/

    public void restore() {
        FilePickerOptions options = new FilePickerOptions();
        options.setMultipleSelection(true);
        options.setExtensions(new String[]{BackupFactory.EXTENSION, BackupFactory.ANDROID_STUDIO_EXTENSION});
        options.setTitle("Select backups to restore (" + BackupFactory.EXTENSION + " or " + BackupFactory.ANDROID_STUDIO_EXTENSION + ")");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                for (int i = 0; i < files.size(); i++) {
                    String backupFilePath = files.get(i).getAbsolutePath();

                    if (BackupFactory.zipContainsFile(backupFilePath, "local_libs")) {
                        boolean restoringMultipleBackups = files.size() > 1;

                        new MaterialAlertDialogBuilder(act)
                                .setTitle("Warning")
                                .setMessage(getRestoreIntegratedLocalLibrariesMessage(restoringMultipleBackups, i, files.size(),
                                        FileUtil.getFileNameNoExtension(backupFilePath)))
                                .setPositiveButton("Copy", (dialog, which) -> doRestore(backupFilePath, true))
                                .setNegativeButton("Don't copy", (dialog, which) -> doRestore(backupFilePath, false))
                                .setNeutralButton(R.string.common_word_cancel, null)
                                .show();

                    } else if (isAndroidStudioBackup(backupFilePath)) {
                        doRestoreAndroidStudioProject(backupFilePath);
                    } else {
                        doRestore(backupFilePath, false);
                    }
                }
            }
        };

        new FilePickerDialogFragment(options, callback).show(projectsFragment.getChildFragmentManager(), "file_picker");
    }

    public void doRestore(String file, boolean restoreLocalLibs) {
        if (isAndroidStudioBackup(file)) {
            doRestoreAndroidStudioProject(file, restoreLocalLibs);
            return;
        }
        new RestoreAsyncTask(new WeakReference<>(act), file, restoreLocalLibs, projectsFragment).execute("");
    }

    public void doRestoreAndroidStudioProject(String file) {
        doRestoreAndroidStudioProject(file, false);
    }

    public void doRestoreAndroidStudioProject(String file, boolean restoreLocalLibs) {
        new AndroidStudioRestoreAsyncTask(new WeakReference<>(act), file, restoreLocalLibs, projectsFragment).execute("");
    }

    private static boolean isAndroidStudioBackup(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        if (BackupFactory.ANDROID_STUDIO_EXTENSION.equalsIgnoreCase(FileUtil.getFileExtension(filePath))) {
            return true;
        }
        return BackupFactory.isAndroidStudioArchive(new File(filePath));
    }

    private static void dismissSafely(WeakReference<Activity> activityRef, AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        Activity activity = activityRef.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    private static void notifyRestoreCompleted(WeakReference<Activity> activityRef, ProjectsFragment projectsFragment) {
        Activity activity = activityRef.get();
        if (activity instanceof MainActivity mainActivity) {
            mainActivity.n();
            SketchwareUtil.toast("Restored successfully");
        } else if (projectsFragment != null) {
            projectsFragment.refreshProjectsList();
            SketchwareUtil.toast("Restored successfully");
        } else {
            SketchwareUtil.toast("Restored successfully. Refresh to see the project", Toast.LENGTH_LONG);
        }
    }

    private static class BackupAsyncTask extends AsyncTask<String, Integer, String> {

        private final String sc_id;
        private final String project_name;
        private final HashMap<Integer, Boolean> options;
        private final WeakReference<Activity> activityWeakReference;
        private BackupFactory bm;
        private AlertDialog dlg;

        BackupAsyncTask(WeakReference<Activity> activityWeakReference, String sc_id, String project_name, HashMap<Integer, Boolean> options) {
            this.activityWeakReference = activityWeakReference;
            this.sc_id = sc_id;
            this.project_name = project_name;
            this.options = options;
        }

        @Override
        protected void onPreExecute() {
            Activity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            ProgressMsgBoxBinding loadingDialogBinding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(activity));
            loadingDialogBinding.tvProgress.setText("Creating backup...");
            dlg = new MaterialAlertDialogBuilder(activity)
                    .setTitle("Please wait")
                    .setCancelable(false)
                    .setView(loadingDialogBinding.getRoot())
                    .create();
            dlg.show();
        }

        @Override
        protected String doInBackground(String... params) {
            bm = new BackupFactory(sc_id);
            bm.setBackupLocalLibs(Boolean.TRUE.equals(options.get(0)));
            bm.setBackupCustomBlocks(Boolean.TRUE.equals(options.get(1)));

            bm.backup(activityWeakReference.get(), project_name);

            return "";
        }

        @Override
        protected void onPostExecute(String _result) {
            dismissSafely(activityWeakReference, dlg);

            if (bm.getOutFile() != null) {
                SketchwareUtil.toast("Successfully created backup to: " + bm.getOutFile().getAbsolutePath());
            } else {
                SketchwareUtil.toastError("Error: " + bm.error, Toast.LENGTH_LONG);
            }
        }
    }

    private static class AndroidStudioBackupAsyncTask extends AsyncTask<String, Integer, String> {

        private final String sc_id;
        private final String project_name;
        private final boolean includeLocalLibs;
        private final WeakReference<Activity> activityWeakReference;
        private BackupFactory bm;
        private AlertDialog dlg;

        AndroidStudioBackupAsyncTask(WeakReference<Activity> activityWeakReference, String sc_id, String project_name, boolean includeLocalLibs) {
            this.activityWeakReference = activityWeakReference;
            this.sc_id = sc_id;
            this.project_name = project_name;
            this.includeLocalLibs = includeLocalLibs;
        }

        @Override
        protected void onPreExecute() {
            Activity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            ProgressMsgBoxBinding loadingDialogBinding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(activity));
            loadingDialogBinding.tvProgress.setText("Creating backup...");
            dlg = new MaterialAlertDialogBuilder(activity)
                    .setTitle("Please wait")
                    .setCancelable(false)
                    .setView(loadingDialogBinding.getRoot())
                    .create();
            dlg.show();
        }

        @Override
        protected String doInBackground(String... params) {
            bm = new BackupFactory(sc_id);
            bm.setBackupLocalLibs(includeLocalLibs);
            bm.backupAndroidStudioProject(project_name);
            return "";
        }

        @Override
        protected void onPostExecute(String _result) {
            dismissSafely(activityWeakReference, dlg);

            if (bm.getOutFile() != null) {
                SketchwareUtil.toast("Successfully created backup to: " + bm.getOutFile().getAbsolutePath());
            } else {
                SketchwareUtil.toastError("Error: " + bm.error, Toast.LENGTH_LONG);
            }
        }
    }

    private static class RestoreAsyncTask extends AsyncTask<String, Integer, String> {

        private final WeakReference<Activity> activityWeakReference;
        private final String file;
        private final ProjectsFragment projectsFragment;
        private final boolean restoreLocalLibs;
        private BackupFactory bm;
        private AlertDialog dlg;
        private boolean error = false;

        RestoreAsyncTask(WeakReference<Activity> activityWeakReference, String file, boolean restoreLocalLibraries, ProjectsFragment projectsFragment) {
            this.activityWeakReference = activityWeakReference;
            this.file = file;
            this.projectsFragment = projectsFragment;
            restoreLocalLibs = restoreLocalLibraries;
        }

        @Override
        protected void onPreExecute() {
            Activity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            ProgressMsgBoxBinding loadingDialogBinding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(activity));
            loadingDialogBinding.tvProgress.setText("Restoring...");
            dlg = new MaterialAlertDialogBuilder(activity)
                    .setTitle("Please wait")
                    .setCancelable(false)
                    .setView(loadingDialogBinding.getRoot())
                    .create();
            dlg.show();
        }

        @Override
        protected String doInBackground(String... params) {
            bm = new BackupFactory(RecentProjectsRegistry.b());
            bm.setBackupLocalLibs(restoreLocalLibs);

            try {
                bm.restore(new File(file));
            } catch (Exception e) {
                bm.error = e.getMessage();
                error = true;
            }

            return "";
        }

        @Override
        protected void onPostExecute(String _result) {
            dismissSafely(activityWeakReference, dlg);

            if (!bm.isRestoreSuccess() || error) {
                SketchwareUtil.toastError("Couldn't restore: " + bm.error, Toast.LENGTH_LONG);
            } else {
                notifyRestoreCompleted(activityWeakReference, projectsFragment);
            }
        }
    }

    private static class AndroidStudioRestoreAsyncTask extends AsyncTask<String, Integer, String> {

        private final WeakReference<Activity> activityWeakReference;
        private final String file;
        private final boolean restoreLocalLibs;
        private final ProjectsFragment projectsFragment;
        private BackupFactory bm;
        private AlertDialog dlg;
        private boolean error = false;

        AndroidStudioRestoreAsyncTask(WeakReference<Activity> activityWeakReference, String file, boolean restoreLocalLibs, ProjectsFragment projectsFragment) {
            this.activityWeakReference = activityWeakReference;
            this.file = file;
            this.restoreLocalLibs = restoreLocalLibs;
            this.projectsFragment = projectsFragment;
        }

        @Override
        protected void onPreExecute() {
            Activity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            ProgressMsgBoxBinding loadingDialogBinding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(activity));
            loadingDialogBinding.tvProgress.setText("Restoring...");
            dlg = new MaterialAlertDialogBuilder(activity)
                    .setTitle("Please wait")
                    .setCancelable(false)
                    .setView(loadingDialogBinding.getRoot())
                    .create();
            dlg.show();
        }

        @Override
        protected String doInBackground(String... params) {
            bm = new BackupFactory(RecentProjectsRegistry.b());
            bm.setBackupLocalLibs(restoreLocalLibs);

            try {
                bm.restoreAndroidStudioProject(new File(file));
            } catch (Exception e) {
                bm.error = e.getMessage();
                error = true;
            }

            return "";
        }

        @Override
        protected void onPostExecute(String _result) {
            dismissSafely(activityWeakReference, dlg);

            if (!bm.isRestoreSuccess() || error) {
                SketchwareUtil.toastError("Couldn't restore: " + bm.error, Toast.LENGTH_LONG);
            } else {
                notifyRestoreCompleted(activityWeakReference, projectsFragment);
            }
        }
    }
}
