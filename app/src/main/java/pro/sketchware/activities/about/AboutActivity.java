package pro.sketchware.activities.about;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.about.fragments.BetaChangesFragment;
import pro.sketchware.activities.about.fragments.ChangeLogFragment;
import pro.sketchware.activities.about.fragments.TeamFragment;
import pro.sketchware.activities.about.models.AboutAppViewModel;
import pro.sketchware.activities.about.models.AboutResponseModel;
import pro.sketchware.databinding.ActivityAboutAppBinding;
import pro.sketchware.utility.AdManager;
import pro.sketchware.utility.Network;

public class AboutActivity extends BaseAppCompatActivity {

    private static final int MAX_TEAM_MEMBERS = 40;
    private static final int MAX_RELEASES = 25;

    private final Network network = new Network();
    private final Gson gson = new Gson();

    public AboutAppViewModel aboutAppData;
    private ActivityAboutAppBinding binding;
    private SharedPreferences sharedPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityAboutAppBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        aboutAppData = new ViewModelProvider(this).get(AboutAppViewModel.class);
        sharedPref = getSharedPreferences("AppData", Activity.MODE_PRIVATE);

        initViews();
        initData();
        AdManager.loadBanner(this, binding.adContainer, "ca-app-pub-6598765502914364/2525937709");
    }

    private void initViews() {
        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.discordButton.setOnClickListener(v -> openCommunityLink());

        AboutAdapter adapter = new AboutAdapter(this);
        binding.viewPager.setOffscreenPageLimit(3);
        binding.viewPager.setAdapter(adapter);

        String[] tabTitles = new String[]{
                Helper.getResString(R.string.about_team_title),
                Helper.getResString(R.string.about_changelog_title),
                Helper.getResString(R.string.about_beta_changes_title)
        };

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> tab.setText(tabTitles[position])).attach();

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    binding.discordButton.extend();
                } else {
                    binding.discordButton.shrink();
                }
            }
        });

        String toSelect = getIntent().getStringExtra("select");
        if ("changelog".equals(toSelect)) {
            binding.viewPager.setCurrentItem(1);
        } else if ("betaChanges".equals(toSelect)) {
            binding.viewPager.setCurrentItem(2);
        }
    }

    private void initData() {
        String cachedPayload = sharedPref.getString("aboutData", null);
        if (handleAboutPayload(cachedPayload, false)) {
            // Opcional: Ainda carregar do GitHub em segundo plano para atualizar o cache
            loadGitHubFallbackData();
            return;
        }

        loadGitHubFallbackData();
    }

    private void loadGitHubFallbackData() {
        network.get(Helper.getResString(R.string.link_github_contributors_url), contributorsResponse ->
                network.get(Helper.getResString(R.string.link_github_releases_url), releasesResponse -> {
                    String fallbackPayload = buildFallbackAboutPayload(contributorsResponse, releasesResponse);
                    if (!handleAboutPayload(fallbackPayload, true)) {
                        String cachedPayload = sharedPref.getString("aboutData", null);
                        handleAboutPayload(cachedPayload, false);
                    }
                }));
    }

    private boolean handleAboutPayload(String response, boolean cachePayload) {
        AboutResponseModel aboutResponseModel = parseAboutPayload(response);
        if (aboutResponseModel == null) {
            return false;
        }

        if (cachePayload && response != null && !response.trim().isEmpty()) {
            sharedPref.edit().putString("aboutData", response).apply();
        }

        applyAboutData(aboutResponseModel);
        return true;
    }

    private AboutResponseModel parseAboutPayload(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        try {
            AboutResponseModel model = gson.fromJson(response, AboutResponseModel.class);
            if (model == null) {
                return null;
            }

            boolean hasTeam = model.getTeam() != null && !model.getTeam().isEmpty();
            boolean hasChangelog = model.getChangelog() != null && !model.getChangelog().isEmpty();
            return hasTeam || hasChangelog ? model : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyAboutData(AboutResponseModel aboutResponseModel) {
        aboutAppData.setDiscordInviteLink(resolveCommunityLink(aboutResponseModel.getDiscordInviteLink()));
        aboutAppData.setTeamMembers(aboutResponseModel.getTeam() != null
                ? aboutResponseModel.getTeam()
                : new ArrayList<>());
        aboutAppData.setChangelog(aboutResponseModel.getChangelog() != null
                ? aboutResponseModel.getChangelog()
                : new ArrayList<>());
    }

    private void openCommunityLink() {
        String inviteLink = aboutAppData.getDiscordInviteLink().getValue();
        inviteLink = resolveCommunityLink(inviteLink);

        if (inviteLink == null || inviteLink.trim().isEmpty()) {
            return;
        }

        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(inviteLink)));
    }

    private String resolveCommunityLink(String linkFromPayload) {
        if (linkFromPayload != null && !linkFromPayload.trim().isEmpty()) {
            return linkFromPayload;
        }

        String discordLink = Helper.getResString(R.string.link_discord_invite);
        if (discordLink != null && !discordLink.trim().isEmpty()) {
            return discordLink;
        }

        return Helper.getResString(R.string.link_telegram_invite);
    }

    private String buildFallbackAboutPayload(String contributorsResponse, String releasesResponse) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("discordInviteLink", resolveCommunityLink(null));
            payload.put("team", buildTeamArray(contributorsResponse));
            payload.put("changelog", buildChangelogArray(releasesResponse));
            return payload.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONArray buildTeamArray(String contributorsResponse) {
        JSONArray result = new JSONArray();
        if (contributorsResponse == null || contributorsResponse.trim().isEmpty()) {
            return result;
        }

        String repoOwner = extractRepoOwner();

        try {
            JSONArray contributors = new JSONArray(contributorsResponse);
            for (int i = 0; i < contributors.length() && result.length() < MAX_TEAM_MEMBERS; i++) {
                JSONObject contributor = contributors.optJSONObject(i);
                if (contributor == null) {
                    continue;
                }

                String login = contributor.optString("login", "").trim();
                if (login.isEmpty()) {
                    continue;
                }

                boolean isOwner = login.equalsIgnoreCase(repoOwner);
                int contributions = contributor.optInt("contributions", 0);

                JSONObject teamMember = new JSONObject();
                teamMember.put("user_username", login);
                teamMember.put("user_img", contributor.optString("avatar_url", ""));
                teamMember.put("description", isOwner
                        ? "Maintainer do repositório atual."
                        : contributions + " contribuições neste repositório.");
                teamMember.put("is_core_team", isOwner);
                teamMember.put("is_active", true);
                result.put(teamMember);
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private JSONArray buildChangelogArray(String releasesResponse) {
        JSONArray result = new JSONArray();
        if (releasesResponse == null || releasesResponse.trim().isEmpty()) {
            return result;
        }

        try {
            JSONArray releases = new JSONArray(releasesResponse);
            for (int i = 0; i < releases.length() && result.length() < MAX_RELEASES; i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null) {
                    continue;
                }

                String title = release.optString("name", "").trim();
                if (title.isEmpty()) {
                    title = release.optString("tag_name", "Atualização").trim();
                }

                String body = release.optString("body", "").trim();
                if (body.isEmpty()) {
                    body = "Release publicada no GitHub.";
                }

                JSONObject releaseNote = new JSONObject();
                releaseNote.put("title", title);
                releaseNote.put("description", body);
                releaseNote.put("releaseDate", parseEpochMillis(release.optString("published_at", "")));
                releaseNote.put("isBeta", release.optBoolean("prerelease", false));
                releaseNote.put("isTitled", true);
                result.put(releaseNote);
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private long parseEpochMillis(String publishedAt) {
        if (publishedAt == null || publishedAt.trim().isEmpty()) {
            return 0L;
        }

        try {
            return OffsetDateTime.parse(publishedAt).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String extractRepoOwner() {
        try {
            Uri repoUri = Uri.parse(Helper.getResString(R.string.link_github_url));
            List<String> pathSegments = repoUri.getPathSegments();
            if (!pathSegments.isEmpty()) {
                return pathSegments.get(0);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static class AboutAdapter extends FragmentStateAdapter {
        public AboutAdapter(AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 1 -> new ChangeLogFragment();
                case 2 -> new BetaChangesFragment();
                default -> new TeamFragment();
            };
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
