package pro.sketchware.supabase;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import pro.sketchware.R;

public class SupabaseSettingsTest {

    @Test
    public void projectUrlsAreAccepted() {
        assertEquals(0, SupabaseSettings.urlProblem("https://abcd.supabase.co"));
        assertEquals(0, SupabaseSettings.urlProblem("  https://abcd.supabase.co/ "));
        // A local Supabase (supabase start) while developing
        assertEquals(0, SupabaseSettings.urlProblem("http://192.168.0.10:54321"));
        assertEquals("https://abcd.supabase.co", SupabaseSettings.normalizeUrl(" https://abcd.supabase.co// "));
    }

    @Test
    public void brokenUrlsSayWhy() {
        assertEquals(R.string.supabase_error_url_empty, SupabaseSettings.urlProblem("   "));
        assertEquals(R.string.supabase_error_url_scheme, SupabaseSettings.urlProblem("abcd.supabase.co"));
        assertEquals(R.string.supabase_error_url_invalid, SupabaseSettings.urlProblem("https://"));
        assertEquals(R.string.supabase_error_url_invalid, SupabaseSettings.urlProblem("https://abcd supabase.co"));
    }
}
