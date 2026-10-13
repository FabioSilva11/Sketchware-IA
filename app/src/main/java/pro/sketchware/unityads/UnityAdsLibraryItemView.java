package pro.sketchware.unityads;

import android.content.Context;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.LibraryItemView;

import pro.sketchware.R;

/** The Unity Ads row of the library screen: ON once the project has a game ID. */
public class UnityAdsLibraryItemView extends LibraryItemView {
    private final String scId;

    public UnityAdsLibraryItemView(Context context, String scId) {
        super(context);
        this.scId = scId;
    }

    @Override
    public void setData(@Nullable ProjectLibraryBean projectLibraryBean) {
        boolean configured = UnityAdsSettings.isConfigured(scId);
        icon.setImageResource(R.drawable.ic_mtrl_unity_ads);
        title.setText(R.string.unity_ads_library_title);
        description.setText(configured
                ? context.getString(R.string.unity_ads_library_item_game_id, UnityAdsSettings.getGameId(scId))
                : context.getString(R.string.unity_ads_library_item_description));
        enabled.setText(configured ? "ON" : "OFF");
        enabled.setSelected(configured);
    }
}
