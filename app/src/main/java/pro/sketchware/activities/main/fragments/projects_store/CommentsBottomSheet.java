package pro.sketchware.activities.main.fragments.projects_store;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import pro.sketchware.activities.main.fragments.projects_store.adapters.CommentsAdapter;
import pro.sketchware.activities.main.fragments.projects_store.api.SketchwareStoreApi;
import pro.sketchware.databinding.FragmentStoreProjectPreviewCommentsBinding;

public class CommentsBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_PUBLICATION_SLUG = "publication_slug";

    private FragmentStoreProjectPreviewCommentsBinding binding;
    private CommentsAdapter adapter;
    private final SketchwareStoreApi storeApi = new SketchwareStoreApi();

    public static CommentsBottomSheet newInstance(String slug) {
        CommentsBottomSheet sheet = new CommentsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PUBLICATION_SLUG, slug);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStoreProjectPreviewCommentsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CommentsAdapter();
        binding.recyclerView.setAdapter(adapter);
        loadComments();

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int first = lm.findFirstCompletelyVisibleItemPosition();
                int last = lm.findLastCompletelyVisibleItemPosition();
                int total = adapter.getItemCount();
                binding.dividerTop.setVisibility(first > 0 ? View.VISIBLE : View.GONE);
                binding.dividerBottom.setVisibility(last < total - 1 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void loadComments() {
        Bundle args = getArguments();
        String slug = args == null ? null : args.getString(ARG_PUBLICATION_SLUG);
        storeApi.getComments(slug, comments -> {
            if (binding == null || comments == null) {
                return;
            }
            adapter.setComments(comments);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
