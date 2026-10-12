package pro.sketchware.supabase;

import android.content.Context;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.LibraryItemView;

import pro.sketchware.R;

/** The Supabase row of the library screen: ON once the project has a URL and a key. */
public class SupabaseLibraryItemView extends LibraryItemView {
    private final String scId;

    public SupabaseLibraryItemView(Context context, String scId) {
        super(context);
        this.scId = scId;
    }

    @Override
    public void setData(@Nullable ProjectLibraryBean projectLibraryBean) {
        boolean configured = SupabaseSettings.isConfigured(scId);
        icon.setImageResource(R.drawable.ic_mtrl_supabase);
        title.setText(R.string.supabase_library_title);
        description.setText(configured ? SupabaseSettings.getUrl(scId)
                : context.getString(R.string.supabase_library_item_description));
        enabled.setText(configured ? "ON" : "OFF");
        enabled.setSelected(configured);
    }
}
