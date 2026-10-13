package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The generated sources compile against the bundled Unity Ads SDK in tools/builtin-libs/verify_generated_code.py,
 * which has the SDK's jars; these check what generate() puts in them.
 */
public class UnityAdsClientSourceTest {

    @Test
    public void projectValuesAreInserted() {
        String source = UnityAdsClientSource.generate("com.example.jogo", " 5512345 ", true);
        assertTrue(source.startsWith("package com.example.jogo;"));
        assertTrue(source.contains("public static final String GAME_ID = \"5512345\";"));
        assertTrue(source.contains("public static final boolean TEST_MODE = true;"));
        assertFalse(source.contains("__"));
        assertTrue(UnityAdsClientSource.generate("a.b", "1", false).contains("TEST_MODE = false;"));
    }

    @Test
    public void onlyTheCurrentApiIsUsed() {
        String source = UnityAdsClientSource.generate("a.b", "1", false);
        // Deprecated in 4.19 and removed in 5.0
        assertFalse(source.contains("IUnityAdsLoadListener"));
        assertFalse(source.contains("IUnityAdsShowListener"));
        assertFalse(source.contains("UnityAds.load("));
        assertFalse(source.contains("UnityAds.show("));
        assertTrue(source.contains("new InitializationConfiguration.Builder("));
    }

    @Test
    public void theBannerWidgetIsAClassOfTheAppPackage() {
        assertTrue(UnityAdsClientSource.generateBannerView("com.example.jogo").startsWith("package com.example.jogo;"));
        assertEquals("com.example.jogo.UnityBannerView", UnityAdsClientSource.bannerViewClass("com.example.jogo"));
        assertEquals("UnityBannerView", UnityAdsClientSource.bannerViewClass(""));
    }
}
