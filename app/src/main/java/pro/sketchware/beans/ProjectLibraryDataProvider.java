package pro.sketchware.beans;

import com.besome.sketch.beans.ProjectLibraryBean;

public interface ProjectLibraryDataProvider {
   void a(ProjectLibraryBean var1);

   String getDocUrl();

   boolean isValid();

   void setData(ProjectLibraryBean var1);
}
