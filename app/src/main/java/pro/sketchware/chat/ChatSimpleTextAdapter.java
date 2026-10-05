package pro.sketchware.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Adapter simples de texto para listas em diálogos do chat. */
public class ChatSimpleTextAdapter extends RecyclerView.Adapter<ChatSimpleTextAdapter.Holder> {

    public interface OnItemClickListener {
        void onItemClick(int position, @NonNull String item);
    }

    private final List<String> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setItems(@NonNull List<String> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        String text = items.get(position);
        holder.textView.setText(text);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(holder.getBindingAdapterPosition(), text);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView textView;

        Holder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
