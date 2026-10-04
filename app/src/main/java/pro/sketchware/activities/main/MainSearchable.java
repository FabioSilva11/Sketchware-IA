package pro.sketchware.activities.main;

/**
 * A main screen tab whose content the shared search bar filters.
 */
public interface MainSearchable {

    /** The search text changed (empty when cleared) while this tab is shown. */
    void onMainSearch(String query);
}
