package pro.sketchware.activities.main.fragments.projects_store;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.main.MainSearchable;
import pro.sketchware.databinding.FragmentP2pStoreBinding;
import pro.sketchware.store.StoreRuntime;
import pro.sketchware.store.core.Catalog;
import pro.sketchware.store.core.Records;
import pro.sketchware.store.core.StoreEngine;
import pro.sketchware.store.ui.ListingAdapter;
import pro.sketchware.store.ui.StoreProfileActivity;
import pro.sketchware.store.ui.StoreProjectActivity;
import pro.sketchware.store.ui.StoreSettingsDialog;

/**
 * The Store tab: projects shared on the P2P network, as far as this device has learned about them.
 */
public class ProjectsStoreFragment extends Fragment implements MainSearchable {

    private FragmentP2pStoreBinding binding;
    private StoreRuntime runtime;
    private ListingAdapter adapter;
    private String query = "";
    private final Runnable onStoreChanged = this::refresh;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentP2pStoreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        runtime = StoreRuntime.get(requireContext());
        adapter = new ListingAdapter(listing -> startActivity(StoreProjectActivity.intent(requireContext(),
                listing.author, listing.id)));
        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        binding.listings.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        binding.listings.setAdapter(adapter);
        binding.kindChips.setOnCheckedStateChangeListener((group, ids) -> refresh());
        binding.sortChips.setOnCheckedStateChangeListener((group, ids) -> refresh());
        binding.storeRefresh.setOnRefreshListener(() -> {
            StoreEngine engine = runtime.engine();
            if (engine != null) {
                engine.syncSoon();
            }
            binding.storeRefresh.setRefreshing(false);
        });
        binding.storeSettings.setOnClickListener(v -> StoreSettingsDialog.show(requireActivity()));
        // The user's own store page, next to the search bar while this tab is shown
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.p2p_store_menu, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() != R.id.store_profile) {
                    return false;
                }
                StoreEngine engine = runtime.engine();
                if (engine != null) {
                    startActivity(StoreProfileActivity.intent(requireContext(), engine.identity().id()));
                }
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        runtime.start(engine -> refresh());
        refresh();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (runtime != null) {
            runtime.addListener(onStoreChanged);
            refresh();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (runtime != null) {
            runtime.removeListener(onStoreChanged);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onMainSearch(String query) {
        this.query = query == null ? "" : query;
        refresh();
    }

    private void refresh() {
        if (binding == null) {
            return;
        }
        StoreEngine engine = runtime.engine();
        if (engine == null) {
            String error = runtime.startError();
            binding.networkStatus.setText(error == null ? getString(R.string.p2p_store_starting)
                    : getString(R.string.p2p_store_start_failed, error));
            showEmpty(true, error == null);
            adapter.submit(java.util.Collections.emptyList());
            return;
        }
        StoreEngine.Status status = engine.status();
        String networkText;
        if (!status.networkAllowed) {
            networkText = getString(R.string.p2p_store_status_waiting_wifi);
        } else {
            networkText = getString(R.string.p2p_store_status, status.dhtNodes, status.users,
                    getString(status.sharing ? R.string.p2p_store_status_sharing : R.string.p2p_store_status_not_sharing))
                    + (status.syncing ? getString(R.string.p2p_store_status_syncing) : "");
        }
        binding.networkStatus.setText(networkText);

        String kind = null;
        int kindId = binding.kindChips.getCheckedChipId();
        if (kindId == R.id.chip_sketchware) {
            kind = Records.KIND_SKETCHWARE;
        } else if (kindId == R.id.chip_android_studio) {
            kind = Records.KIND_ANDROID_STUDIO;
        }
        String sort = Catalog.SORT_POPULAR;
        int sortId = binding.sortChips.getCheckedChipId();
        if (sortId == R.id.sort_newest) {
            sort = Catalog.SORT_NEWEST;
        } else if (sortId == R.id.sort_likes) {
            sort = Catalog.SORT_LIKES;
        } else if (sortId == R.id.sort_downloads) {
            sort = Catalog.SORT_DOWNLOADS;
        }
        List<Catalog.Listing> listings = engine.catalog().listings(query, kind, sort, null);
        adapter.submit(listings);
        binding.resultSummary.setText(getString(R.string.p2p_store_results, listings.size()));
        if (listings.isEmpty()) {
            boolean searching = status.syncing || status.users <= 1;
            binding.emptyTitle.setText(searching ? R.string.p2p_store_empty_searching : R.string.p2p_store_empty_none);
            binding.emptyMessage.setText(searching ? R.string.p2p_store_empty_searching_message
                    : R.string.p2p_store_empty_none_message);
            showEmpty(true, searching);
        } else {
            showEmpty(false, false);
        }
    }

    private void showEmpty(boolean empty, boolean searching) {
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.emptyProgress.setVisibility(searching ? View.VISIBLE : View.GONE);
        binding.listings.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
