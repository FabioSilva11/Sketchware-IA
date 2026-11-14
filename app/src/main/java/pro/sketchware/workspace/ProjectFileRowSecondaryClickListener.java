package pro.sketchware.workspace;

import android.content.Intent;
import android.view.View;
import androidx.annotation.NonNull;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.editor.manage.view.PresetSettingActivity;

import pro.sketchware.editor.EditorViewUtils;

public class ProjectFileRowSecondaryClickListener implements View.OnClickListener {

    public final ProjectFilesFragment.Adapter adapter;
    public final ProjectFilesFragment.Adapter.ViewHolder holder;

    public ProjectFileRowSecondaryClickListener(ProjectFilesFragment.Adapter.ViewHolder holder, ProjectFilesFragment.Adapter adapter) {
        this.holder = holder;
        this.adapter = adapter;
    }

    @Override
    public void onClick(View v) {
        if (!EditorViewUtils.a()) {
            adapter.selectedPosition = holder.getLayoutPosition();
            Intent intent = new Intent(holder.fragment().getActivity(), PresetSettingActivity.class);
            int requestCode;
            ProjectFileBean fileBean = holder.fragment().customViews.get(adapter.selectedPosition);
            if (fileBean.fileType == 1) {
                requestCode = 277;
            } else {
                requestCode = 278;
            }

            intent.putExtra("request_code", requestCode);
            intent.putExtra("edit_mode", true);
            holder.fragment().startActivityForResult(intent, requestCode);
        }
    }
}
