package pro.sketchware.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

public class ChatDrawerAdapter extends RecyclerView.Adapter<ChatDrawerAdapter.ThreadViewHolder> {

    public interface OnThreadClickListener {
        void onThreadClick(ChatThread thread);

        void onThreadLongClick(ChatThread thread);

        void onThreadDelete(ChatThread thread);
    }

    private final List<ChatThread> threads = new ArrayList<>();
    private String activeThreadId = "";
    private OnThreadClickListener listener;

    public void setListener(OnThreadClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<ChatThread> items, String activeId) {
        threads.clear();
        if (items != null) {
            threads.addAll(items);
        }
        activeThreadId = activeId == null ? "" : activeId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThreadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_kelivo_drawer_thread, parent, false);
        return new ThreadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThreadViewHolder holder, int position) {
        ChatThread thread = threads.get(position);
        String title = ChatMessage.hasVisibleText(thread.title)
                ? thread.title
                : holder.itemView.getContext().getString(R.string.chat_thread_new_title);
        holder.title.setText(title);
        holder.pin.setVisibility(thread.pinned ? View.VISIBLE : View.GONE);
        boolean selected = thread.id.equals(activeThreadId);
        holder.itemView.setBackgroundResource(selected
                ? R.drawable.bg_kelivo_drawer_selected
                : android.R.color.transparent);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onThreadClick(thread);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onThreadLongClick(thread);
                return true;
            }
            return false;
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onThreadDelete(thread);
            }
        });
    }

    @Override
    public int getItemCount() {
        return threads.size();
    }

    static class ThreadViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView pin;
        final ImageView delete;

        ThreadViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_thread_title);
            pin = itemView.findViewById(R.id.image_thread_pin);
            delete = itemView.findViewById(R.id.image_thread_delete);
        }
    }
}
