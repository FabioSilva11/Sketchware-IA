package pro.sketchware.workspace;

import android.view.View;
import androidx.annotation.NonNull;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.editor.manage.view.ManageViewActivity;

public class ProjectFileRowLongClickListener implements View.OnLongClickListener {

    public final ProjectFilesFragment.Adapter adapter;
    public final ProjectFilesFragment.Adapter.ViewHolder holder;

    public ProjectFileRowLongClickListener(ProjectFilesFragment.Adapter.ViewHolder holder, ProjectFilesFragment.Adapter adapter) {
        this.holder = holder;
        this.adapter = adapter;
    }

    @Override
    public boolean onLongClick(View v) {
        if (holder.fragment().getActivity() instanceof ManageViewActivity) {
            ((ManageViewActivity) holder.fragment().getActivity()).a(true);
        }
        adapter.selectedPosition = holder.getLayoutPosition();
        holder.itemBinding.chkSelect.setChecked(!holder.itemBinding.chkSelect.isChecked());
        ProjectFileBean fileBean = holder.fragment().customViews.get(adapter.selectedPosition);
        fileBean.isSelected = holder.itemBinding.chkSelect.isChecked();
        return true;
    }
}
