package pro.sketchware.workspace;

import android.view.View;
import androidx.annotation.NonNull;

import com.besome.sketch.beans.ProjectFileBean;

public class ProjectFileRowClickListener implements View.OnClickListener {

    public final ProjectFilesFragment.Adapter adapter;
    public final ProjectFilesFragment.Adapter.ViewHolder holder;

    public ProjectFileRowClickListener(ProjectFilesFragment.Adapter.ViewHolder holder, ProjectFilesFragment.Adapter adapter) {
        this.holder = holder;
        this.adapter = adapter;
    }

    @Override
    public void onClick(View v) {
        adapter.selectedPosition = holder.getLayoutPosition();
        if (holder.fragment().isSelectionMode()) {
            holder.itemBinding.chkSelect.setChecked(!holder.itemBinding.chkSelect.isChecked());
            ProjectFileBean fileBean = holder.fragment().customViews.get(adapter.selectedPosition);
            fileBean.isSelected = holder.itemBinding.chkSelect.isChecked();
            adapter.notifyItemChanged(adapter.selectedPosition);
        }
    }
}
