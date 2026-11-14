package pro.sketchware.workspace;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.editor.manage.view.ManageViewActivity;

public class ProjectFilesScrollListener extends RecyclerView.OnScrollListener {

    public final ProjectFilesFragment fragment;
    public final ProjectFilesFragment.Adapter adapter;

    public ProjectFilesScrollListener(ProjectFilesFragment.Adapter adapter, ProjectFilesFragment fragment) {
        this.adapter = adapter;
        this.fragment = fragment;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        if (fragment.getActivity() instanceof ManageViewActivity) {
            ManageViewActivity activity = (ManageViewActivity) fragment.getActivity();
            if (dy > 2) {
                if (activity.s.isEnabled()) {
                    activity.s.hide();
                }
            } else if (dy < -2 && activity.s.isEnabled()) {
                activity.s.show();
            }
        }
    }
}
