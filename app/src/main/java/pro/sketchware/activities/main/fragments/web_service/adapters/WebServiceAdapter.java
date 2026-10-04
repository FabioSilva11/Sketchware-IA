package pro.sketchware.activities.main.fragments.web_service.adapters;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import pro.sketchware.R;
import pro.sketchware.databinding.ViewWebServiceItemBinding;

public class WebServiceAdapter extends RecyclerView.Adapter<WebServiceAdapter.ViewHolder> {

    private final FragmentActivity context;
    
    private static final class WebService {
        final String title;
        final String subtitle;
        final String url;
        final int iconResId;
        
        WebService(String title, String subtitle, String url, int iconResId) {
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.iconResId = iconResId;
        }
    }
    
    private static final WebService[] WEB_SERVICES = {
        new WebService("JSONPlaceholder", "jsonplaceholder.typicode.com", "https://jsonplaceholder.typicode.com/", R.drawable.ic_mtrl_web),
        new WebService("DummyJSON", "dummyjson.com", "https://dummyjson.com/", R.drawable.ic_mtrl_code),
        new WebService("REST Countries", "restcountries.com", "https://restcountries.com/", R.drawable.ic_mtrl_link),
        new WebService("Open-Meteo", "open-meteo.com", "https://open-meteo.com/en/docs", R.drawable.ic_mtrl_link_check),
        new WebService("Firebase", "firebase.google.com", "https://firebase.google.com/docs", R.drawable.ic_mtrl_firebase),
        new WebService("Android Docs", "developer.android.com", "https://developer.android.com/", R.drawable.ic_mtrl_android),
        new WebService("Material 3", "developer.android.com", "https://developer.android.com/develop/ui/compose/designsystems/material3", R.drawable.ic_mtrl_material3),
        new WebService("GitHub API", "docs.github.com", "https://docs.github.com/en/rest", R.drawable.ic_github)
    };

    private final java.util.List<WebService> shown = new java.util.ArrayList<>(java.util.Arrays.asList(WEB_SERVICES));

    public WebServiceAdapter(FragmentActivity context) {
        this.context = context;
    }

    /** Shows the services whose name or address contains {@code query}. */
    public void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        shown.clear();
        for (WebService service : WEB_SERVICES) {
            if (needle.isEmpty() || service.title.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || service.subtitle.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                shown.add(service);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ViewWebServiceItemBinding binding = ViewWebServiceItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WebService webService = shown.get(position);
        
        holder.binding.webServiceTitle.setText(webService.title);
        holder.binding.webServiceUrl.setText(webService.subtitle);
        holder.binding.webServiceIcon.setImageResource(webService.iconResId);
        
        holder.itemView.setOnClickListener(v -> openUrl(webService.url));
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(url));
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.web_service_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewWebServiceItemBinding binding;

        public ViewHolder(@NonNull ViewWebServiceItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

