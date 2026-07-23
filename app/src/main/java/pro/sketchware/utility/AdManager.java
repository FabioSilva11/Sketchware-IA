package pro.sketchware.utility;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

public class AdManager {

    public static void loadBanner(Activity activity, ViewGroup container, String adUnitId) {
        AdView adView = new AdView(activity);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(adUnitId);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        container.removeAllViews();
        container.addView(adView);
    }
}
