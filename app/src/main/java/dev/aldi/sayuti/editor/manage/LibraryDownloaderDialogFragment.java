package dev.aldi.sayuti.editor.manage;

import static android.net.ConnectivityManager.NetworkCallback;

import android.content.Context;
import android.net.ConnectivityManager;
import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.createLibraryMap;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.R;
import pro.sketchware.databinding.LibraryDownloaderDialogBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.TranslationFunction;

public class LibraryDownloaderDialogFragment extends BottomSheetDialogFragment {
    private LibraryDownloaderDialogBinding binding;

    private DependencyDownloadAdapter dependencyAdapter;
    private final List<DependencyDownloadItem> downloadItems = new ArrayList<>();
    private final List<String> queuedDependencies = new ArrayList<>();
    /** Libraries saved without classes.dex: the build converts them, or says why it can't. */
    private final List<String> dexingFailures = new ArrayList<>();
    private ExecutorService downloadExecutor;

    private final Gson gson = new Gson();
    private BuildSettings buildSettings;

    private boolean notAssociatedWithProject;
    private String dependencyName;
    private String localLibFile;
    private int queuedDependencyIndex;
    private OnLibraryDownloadedTask onLibraryDownloadedTask;

    private ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LibraryDownloaderDialogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (downloadExecutor != null && !downloadExecutor.isShutdown()) {
            downloadExecutor.shutdownNow();
        }
        unregisterNetworkCallback();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() == null) return;

        dependencyAdapter = new DependencyDownloadAdapter();
        binding.dependenciesRecyclerView.setAdapter(dependencyAdapter);
        binding.dependenciesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        downloadExecutor = Executors.newSingleThreadExecutor();

        notAssociatedWithProject = getArguments().getBoolean("notAssociatedWithProject", false);
        buildSettings = (BuildSettings) getArguments().getSerializable("buildSettings");
        localLibFile = getArguments().getString("localLibFile");
        loadQueuedDependencies();
        applyQueuedDependencyState();

        binding.btnDownload.setOnClickListener(v -> initDownloadFlow());

        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (binding != null) {
                    requireActivity().runOnUiThread(() -> binding.btnDownload.setEnabled(true));
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (binding != null) {
                    requireActivity().runOnUiThread(() -> binding.btnDownload.setEnabled(false));
                }
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
        // Initial check
        binding.btnDownload.setEnabled(isNetworkAvailable());
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    public void setOnLibraryDownloadedTask(OnLibraryDownloadedTask onLibraryDownloadedTask) {
        this.onLibraryDownloadedTask = onLibraryDownloadedTask;
    }

    private void loadQueuedDependencies() {
        if (getArguments() == null) {
            return;
        }
        ArrayList<String> dependencies = getArguments().getStringArrayList("dependencies");
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        LinkedHashSet<String> uniqueDependencies = new LinkedHashSet<>();
        for (String dependency : dependencies) {
            if (dependency != null && !dependency.trim().isEmpty()) {
                uniqueDependencies.add(dependency.trim());
            }
        }
        queuedDependencies.clear();
        queuedDependencies.addAll(uniqueDependencies);
    }

    private void applyQueuedDependencyState() {
        if (queuedDependencies.isEmpty()) {
            return;
        }
        binding.title.setText(R.string.studio_action_dependencies);
        binding.dependencyInput.setText(queuedDependencies.get(0));
        binding.dependencyInput.setEnabled(false);
        binding.dependencyInputLayout.setHint(R.string.studio_dependencies_detected_input_hint);
        binding.dependencyInfo.setText(getString(R.string.studio_dependencies_queue_info, queuedDependencies.size()));
        binding.btnDownload.setText(R.string.studio_dependencies_download_all);
    }

    private void initDownloadFlow() {
        if (!queuedDependencies.isEmpty()) {
            showBatchDownloadConfirmationDialog();
            return;
        }

        dependencyName = Helper.getText(binding.dependencyInput);
        if (dependencyName.isEmpty()) {
            binding.dependencyInputLayout.setError("Please enter a dependency");
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        var parts = parseDependencyName(dependencyName);
        if (parts == null) {
            binding.dependencyInputLayout.setError("Invalid dependency format");
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        showDownloadConfirmationDialog(parts[0], parts[1], parts[2]);
    }

    private String[] parseDependencyName(String dependency) {
        if (dependency == null) {
            return null;
        }
        String[] parts = dependency.trim().split(":");
        if (parts.length != 3) {
            return null;
        }
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                return null;
            }
        }
        return new String[]{parts[0].trim(), parts[1].trim(), parts[2].trim()};
    }

    private void showBatchDownloadConfirmationDialog() {
        for (String dependency : queuedDependencies) {
            if (parseDependencyName(dependency) == null) {
                binding.dependencyInputLayout.setError(getString(R.string.studio_dependencies_invalid_format, dependency));
                binding.dependencyInputLayout.setErrorEnabled(true);
                return;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.studio_dependencies_confirm_batch_title)
                .setMessage(buildBatchConfirmationMessage())
                .setPositiveButton(R.string.studio_dependencies_download_all, (dialog, which) -> startQueuedDownloadProcess())
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private String buildBatchConfirmationMessage() {
        StringBuilder message = new StringBuilder(getString(R.string.studio_dependencies_download_batch_message, queuedDependencies.size()));
        int max = Math.min(12, queuedDependencies.size());
        for (int i = 0; i < max; i++) {
            message.append('\n').append("- ").append(queuedDependencies.get(i));
        }
        int remaining = queuedDependencies.size() - max;
        if (remaining > 0) {
            message.append('\n').append(getString(R.string.studio_dependencies_more_count, remaining));
        }
        return message.toString();
    }

    private void startQueuedDownloadProcess() {
        queuedDependencyIndex = 0;
        downloadItems.clear();
        dependencyAdapter.setDependencies(new ArrayList<>());
        startNextQueuedDependency();
    }

    private void startNextQueuedDependency() {
        if (queuedDependencyIndex >= queuedDependencies.size()) {
            finishDownloadFlow();
            return;
        }
        dependencyName = queuedDependencies.get(queuedDependencyIndex);
        binding.dependencyInput.setText(dependencyName);
        binding.dependencyInfo.setText(getString(
                R.string.studio_dependencies_downloading_item,
                queuedDependencyIndex + 1,
                queuedDependencies.size(),
                dependencyName
        ));
        String[] parts = parseDependencyName(dependencyName);
        if (parts == null) {
            setDownloadState(false);
            binding.dependencyInputLayout.setError(getString(R.string.studio_dependencies_invalid_format, dependencyName));
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }
        startDownloadProcess(parts[0], parts[1], parts[2]);
    }

    private void showDownloadConfirmationDialog(String group, String artifact, String version) {
        boolean skipSubdependencies = binding.cbSkipSubdependencies.isChecked();

        String message;
        if (skipSubdependencies) {
            message = "Are you sure you want to download " + dependencyName;
        } else {
            message = "Are you sure you want to download " + dependencyName + " and its sub-dependencies?";
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm Download")
                .setMessage(message)
                .setPositiveButton("Download", (dialog, which) -> startDownloadProcess(group, artifact, version))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownloadProcess(String group, String artifact, String version) {
        binding.dependencyInputLayout.setErrorEnabled(false);

        binding.dependencyInfo.setVisibility(queuedDependencies.isEmpty() ? View.GONE : View.VISIBLE);
        binding.overallProgress.setVisibility(View.VISIBLE);
        binding.dependenciesRecyclerView.setVisibility(View.VISIBLE);

        setDownloadState(true);

        var resolver = new DependencyResolver(group, artifact, version,
                binding.cbSkipSubdependencies.isChecked(), buildSettings);
        var handler = new Handler(Looper.getMainLooper());

        downloadExecutor.execute(() -> {
            BuiltInLibraries.maybeExtractAndroidJar((message, progress) ->
                    handler.post(() -> binding.overallProgress.setIndeterminate(true)));
            BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

            resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                @Override
                public void onResolving(@NonNull Artifact artifact, @NonNull Artifact dependency) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dependency);
                        item.setState(DependencyDownloadItem.DownloadState.RESOLVING);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onResolutionComplete(@NonNull Artifact dep) {
                    handler.post(() -> updateDependencyState(dep, DependencyDownloadItem.DownloadState.COMPLETED));
                }

                @Override
                public void onArtifactNotFound(@NonNull Artifact dep) {
                    handler.post(() -> {
                        setDownloadState(false);
                        SketchwareUtil.showAnErrorOccurredDialog(getActivity(), "Dependency '" + dep + "' not found");
                    });
                }

                @Override
                public void onSkippingResolution(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setState(DependencyDownloadItem.DownloadState.COMPLETED);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onVersionNotFound(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Version not available");
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onDependenciesNotFound(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Dependencies not found");
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onInvalidScope(@NonNull Artifact dep, @NonNull String scope) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Invalid scope: " + scope);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void invalidPackaging(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Invalid packaging");
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onDownloadStart(@NonNull Artifact dep) {
                    handler.post(() -> {
                        setDownloadState(true);
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setState(DependencyDownloadItem.DownloadState.DOWNLOADING);
                        dependencyAdapter.updateDependency(item);
                        updateOverallProgress();
                    });
                }

                @Override
                public void onDownloadEnd(@NonNull Artifact dep) {
                    handler.post(() -> {
                        updateDependencyState(dep, DependencyDownloadItem.DownloadState.COMPLETED);
                        updateOverallProgress();
                    });
                }

                @Override
                public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError(e.getMessage());
                        dependencyAdapter.updateDependency(item);
                        setDownloadState(false);
                        SketchwareUtil.showAnErrorOccurredDialog(getActivity(),
                                "Downloading dependency '" + dep + "' failed: " + Log.getStackTraceString(e));
                    });
                }

                @Override
                public void unzipping(@NonNull Artifact artifact) {
                    handler.post(() -> updateDependencyState(artifact, DependencyDownloadItem.DownloadState.UNZIPPING));
                }

                @Override
                public void dexing(@NonNull Artifact dep) {
                    handler.post(() -> updateDependencyState(dep, DependencyDownloadItem.DownloadState.DEXING));
                }

                @Override
                public void dexingFailed(@NonNull Artifact dependency, @NonNull Exception e) {
                    handler.post(() -> {
                        dexingFailures.add(dependency.toString());
                        DependencyDownloadItem item = findOrCreateDependencyItem(dependency);
                        item.setError("Dexing failed: " + e.getMessage());
                        dependencyAdapter.updateDependency(item);
                        setDownloadState(false);
                        SketchwareUtil.showAnErrorOccurredDialog(getActivity(),
                                "Dexing dependency '" + dependency + "' failed: " + Log.getStackTraceString(e));
                    });
                }

                @Override
                public void onTaskCompleted(@NonNull List<String> dependencies) {
                    handler.post(() -> onDependencyTaskCompleted(dependencies));
                }
            });
        });
    }

    private void onDependencyTaskCompleted(@NonNull List<String> dependencies) {
        appendDownloadedLibrariesToProject(dependencies, dependencyName);
        if (!queuedDependencies.isEmpty()) {
            queuedDependencyIndex++;
            if (queuedDependencyIndex < queuedDependencies.size()) {
                startNextQueuedDependency();
                return;
            }
        }
        finishDownloadFlow();
    }

    private void finishDownloadFlow() {
        if (dexingFailures.isEmpty()) {
            SketchwareUtil.toast("Library downloaded successfully");
        } else {
            // Saying "successfully" here hid libraries whose code was then left out of the APK
            SketchwareUtil.toastError("Downloaded, but " + String.join(", ", dexingFailures)
                    + " couldn't be converted to DEX. Building the project converts it again or says why it can't.",
                    Toast.LENGTH_LONG);
        }
        if (getActivity() == null) return;
        dismiss();
        if (onLibraryDownloadedTask != null) onLibraryDownloadedTask.invoke();
    }

    @SuppressWarnings("unchecked")
    private void appendDownloadedLibrariesToProject(@NonNull List<String> dependencies, String requestedDependency) {
        if (notAssociatedWithProject || localLibFile == null || localLibFile.trim().isEmpty()) {
            return;
        }

        ArrayList<HashMap<String, Object>> enabledLibs = new ArrayList<>();
        try {
            var fileContent = FileUtil.readFile(localLibFile);
            if (fileContent != null && !fileContent.trim().isEmpty()) {
                ArrayList<HashMap<String, Object>> parsed = gson.fromJson(fileContent, Helper.TYPE_MAP_LIST);
                if (parsed != null) {
                    enabledLibs = parsed;
                }
            }
        } catch (Exception ignored) {
        }

        Set<String> enabledNames = new LinkedHashSet<>();
        for (HashMap<String, Object> library : enabledLibs) {
            Object name = library.get("name");
            if (name instanceof String) {
                enabledNames.add((String) name);
            }
        }

        LinkedHashSet<String> downloadedNames = new LinkedHashSet<>();
        String requestedLibraryName = toLibraryFolderName(requestedDependency);
        if (!requestedLibraryName.isEmpty()) {
            downloadedNames.add(requestedLibraryName);
        }
        downloadedNames.addAll(dependencies);

        for (String name : downloadedNames) {
            if (name == null || name.trim().isEmpty() || enabledNames.contains(name)) {
                continue;
            }
            enabledLibs.add(createLibraryMap(name, requestedDependency));
            enabledNames.add(name);
        }

        FileUtil.writeFile(localLibFile, gson.toJson(enabledLibs));
    }

    private String toLibraryFolderName(String dependency) {
        String[] parts = parseDependencyName(dependency);
        if (parts == null) {
            return "";
        }
        return parts[1] + "-v" + parts[2];
    }

    private DependencyDownloadItem findOrCreateDependencyItem(Artifact artifact) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact)) {
                return item;
            }
        }
        DependencyDownloadItem newItem = new DependencyDownloadItem(artifact);
        downloadItems.add(newItem);
        dependencyAdapter.addDependency(newItem);
        return newItem;
    }

    private void updateDependencyState(Artifact artifact, DependencyDownloadItem.DownloadState state) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact)) {
                item.setState(state);
                dependencyAdapter.updateDependency(item);
                break;
            }
        }
    }

    private void updateOverallProgress() {
        int completed = 0;
        for (DependencyDownloadItem item : downloadItems) {
            if (item.isCompleted()) completed++;
        }

        if (!downloadItems.isEmpty()) {
            binding.overallProgress.setIndeterminate(false);
            binding.overallProgress.setProgress((completed * 100) / downloadItems.size());
        }
    }

    private void setDownloadState(boolean downloading) {
        if (downloading) {
            binding.btnDownload.setVisibility(View.GONE);
        } else {
            binding.btnDownload.setVisibility(View.VISIBLE);
            binding.btnDownload.setEnabled(true);
        }

        binding.dependencyInput.setEnabled(!downloading && queuedDependencies.isEmpty());
        binding.cbSkipSubdependencies.setEnabled(!downloading);
        setCancelable(!downloading);

        if (!downloading) {
            binding.dependencyInfo.setVisibility(View.VISIBLE);
            binding.overallProgress.setVisibility(View.GONE);
            binding.dependenciesRecyclerView.setVisibility(View.GONE);
            if (queuedDependencies.isEmpty()) {
                binding.dependencyInfo.setText(R.string.local_library_manager_dependency_info);
            } else {
                binding.dependencyInfo.setText(getString(R.string.studio_dependencies_queue_info, queuedDependencies.size()));
                binding.btnDownload.setText(R.string.studio_dependencies_download_all);
            }

            downloadItems.clear();
            dependencyAdapter.setDependencies(new ArrayList<>());
        }
    }

    private boolean isNetworkAvailable() {
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public interface OnLibraryDownloadedTask {
        void invoke();
    }
}
