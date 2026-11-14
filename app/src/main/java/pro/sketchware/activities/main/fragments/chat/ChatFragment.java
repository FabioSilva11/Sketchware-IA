package pro.sketchware.activities.main.fragments.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import pro.sketchware.activities.main.fragments.chat.ChatProjectsAdapter;
import com.besome.sketch.editor.manage.library.ProjectComparator;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.editor.StoragePermissionFragment;
import pro.sketchware.core.PreferenceStore;
import pro.sketchware.workspace.RecentProjectsRegistry;
import dev.chrisbanes.insetter.Insetter;
import pro.sketchware.R;
import pro.sketchware.chat.ChatActivity;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.databinding.MyprojectsBinding;
import pro.sketchware.utility.UI;

public class ChatFragment extends StoragePermissionFragment implements pro.sketchware.activities.main.MainSearchable {
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<HashMap<String, Object>> projectsList = new ArrayList<>();
    private MyprojectsBinding binding;
    private ChatProjectsAdapter projectsAdapter;
    private PreferenceStore preference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ViewPager2 handles tab visibility; MaterialFadeThrough leaves recycled views invisible.
    }

    @Override
    public void b(int requestCode) {
    }

    public void toChatActivity(String sc_id) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        requireActivity().startActivity(intent);
    }

    @Override
    public void c(int requestCode) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }

    @Override
    public void d() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).s();
        }
    }

    @Override
    public void e() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).s();
        }
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        binding = MyprojectsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshProjectsList();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        preference = new PreferenceStore(requireContext(), "project");

        ExtendedFloatingActionButton fab = requireActivity().findViewById(R.id.create_new_project);
        if (fab != null) {
            fab.hide(); // Esconder o FAB na aba de chat
        }

        binding.swipeRefresh.setOnRefreshListener(this::refreshProjectsList);

        // Criar adapter que abre ChatActivity ao invés de DesignActivity

        projectsAdapter = new ChatProjectsAdapter(this, projectsList);
        binding.myprojects.setAdapter(projectsAdapter);
        binding.myprojects.setHasFixedSize(true);

        refreshProjectsList();
        binding.myprojects.post(this::refreshProjectsList); // wait for RecyclerView to be ready

        UI.addSystemWindowInsetToPadding(binding.loadingContainer, true, false, true, true);
        UI.addSystemWindowInsetToPadding(binding.myprojects, true, false, true, true);

        // Esconder elementos desnecessários

        binding.iconSort.setVisibility(View.GONE);
        binding.specialActionContainer.setVisibility(View.GONE);
        binding.titleContainer.setVisibility(View.GONE);
    }

    public void scrollToBottom() {
        if (binding != null && binding.myprojects.getAdapter() != null) {
            int lastPosition = binding.myprojects.getAdapter().getItemCount() - 1;
            if (lastPosition >= 0) {
                binding.myprojects.scrollToPosition(lastPosition);
            }
        }
    }


    private String searchQuery = "";

    @Override
    public void onMainSearch(String query) {
        searchQuery = query == null ? "" : query;
        if (projectsAdapter != null) {
            projectsAdapter.filterData(searchQuery);
        }
    }

    public void refreshProjectsList() {
        // Check if the fragment is still attached to the activity
        if (binding == null || !isAdded()) return;

        // Don't load project list without having permissions
        if (!c()) {
            if (binding.swipeRefresh.isRefreshing()) binding.swipeRefresh.setRefreshing(false);
            if (getActivity() instanceof MainActivity mainActivity) {
                mainActivity.s(); // ask for permissions
            }
            return;
        }

        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        executorService.execute(() -> {
            List<HashMap<String, Object>> loadedProjects = RecentProjectsRegistry.a();
            loadedProjects.sort(new ProjectComparator(preference.d("sortBy"), preference.a("pinnedProject", "-1")));
            java.util.Collections.reverse(loadedProjects);

            var activity = getActivity();
            if (activity == null || activity.isFinishing()) return;
            activity.runOnUiThread(() -> {
                if (binding == null || !isAdded()) return;
                if (binding.swipeRefresh.isRefreshing()) binding.swipeRefresh.setRefreshing(false);
                binding.loadingContainer.setVisibility(View.GONE);
                binding.myprojects.setVisibility(View.VISIBLE);
                projectsList.clear();
                projectsList.addAll(loadedProjects);
                projectsAdapter.setAllProjects(projectsList);
                projectsAdapter.filterData(searchQuery);
                scrollToBottom();
            });
        });
    }
}

