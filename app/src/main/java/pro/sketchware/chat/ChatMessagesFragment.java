package pro.sketchware.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import pro.sketchware.R;

public class ChatMessagesFragment extends Fragment {
    private RecyclerView recyclerView;
    private ChatMessageAdapter adapter;
    private boolean pendingScrollToBottom;
    private final RecyclerView.AdapterDataObserver pagingObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    completePendingScroll();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    completePendingScroll();
                }
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.recycler_view_messages);
        // The conversation always opens on its latest page. Restoring RecyclerView's
        // old pixel position can win the race against the history reload and leave
        // the final assistant answer just below the viewport.
        recyclerView.setSaveEnabled(false);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView view, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE || adapter == null) return;
                ChatFlowLogger.event("list", "scroll_idle", "first="
                        + layoutManager.findFirstVisibleItemPosition() + ", last="
                        + layoutManager.findLastVisibleItemPosition() + ", total="
                        + adapter.getItemCount() + ", canUp=" + view.canScrollVertically(-1)
                        + ", canDown=" + view.canScrollVertically(1));
            }
        });
        bindAdapterFromHost();
        if (adapter != null) {
            recyclerView.setAdapter(adapter);
            refreshMessages();
        }
    }

    @Override
    public void onDestroyView() {
        recyclerView = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindAdapterFromHost();
        refreshMessages();
    }

    private void bindAdapterFromHost() {
        if (getActivity() instanceof ChatActivity) {
            ChatMessageAdapter hostAdapter = ((ChatActivity) getActivity()).getMessageAdapterForFragments();
            if (hostAdapter != null && hostAdapter != adapter) {
                setAdapter(hostAdapter);
            }
        }
    }

    public void setAdapter(ChatMessageAdapter adapter) {
        if (this.adapter == adapter) {
            if (recyclerView != null && recyclerView.getAdapter() != adapter) {
                recyclerView.setAdapter(adapter);
            }
            return;
        }
        if (this.adapter != null) {
            this.adapter.unregisterAdapterDataObserver(pagingObserver);
        }
        this.adapter = adapter;
        if (this.adapter != null) {
            this.adapter.registerAdapterDataObserver(pagingObserver);
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
            refreshMessages();
        }
    }

    public void scrollToBottom() {
        RecyclerView list = recyclerView;
        ChatMessageAdapter currentAdapter = adapter;
        if (list == null || currentAdapter == null) {
            return;
        }
        pendingScrollToBottom = true;
        if (currentAdapter.getItemCount() == 0) return;

        list.post(() -> list.postOnAnimation(() -> {
            if (recyclerView != list || adapter != currentAdapter) {
                return;
            }
            int lastPosition = currentAdapter.getItemCount() - 1;
            if (lastPosition >= 0) {
                pendingScrollToBottom = false;
                RecyclerView.LayoutManager manager = list.getLayoutManager();
                if (manager instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) manager).scrollToPosition(lastPosition);
                    list.postOnAnimation(() -> alignLastItemBottom(
                            list, currentAdapter, (LinearLayoutManager) manager, lastPosition));
                } else {
                    list.scrollToPosition(lastPosition);
                }
            }
        }));
    }

    /** Mantém os deltas novos visíveis sem puxar a lista se o usuário rolou para cima. */
    public boolean isAtBottom() {
        RecyclerView list = recyclerView;
        return list == null || !list.canScrollVertically(1);
    }

    private void alignLastItemBottom(
            @NonNull RecyclerView list,
            @NonNull ChatMessageAdapter currentAdapter,
            @NonNull LinearLayoutManager manager,
            int lastPosition
    ) {
        if (recyclerView != list || adapter != currentAdapter) return;
        View lastItem = manager.findViewByPosition(lastPosition);
        if (lastItem == null) {
            manager.scrollToPosition(lastPosition);
            return;
        }
        int viewportBottom = list.getHeight() - list.getPaddingBottom();
        int delta = lastItem.getBottom() - viewportBottom;
        if (delta > 0) list.scrollBy(0, delta);
    }

    public void refreshMessages() {
        if (adapter == null) {
            return;
        }
        adapter.refresh();
        if (recyclerView != null) {
            recyclerView.post(this::scrollToBottom);
        }
    }

    private void completePendingScroll() {
        if (pendingScrollToBottom && recyclerView != null && adapter != null
                && adapter.getItemCount() > 0) {
            recyclerView.post(this::scrollToBottom);
        }
    }
}
