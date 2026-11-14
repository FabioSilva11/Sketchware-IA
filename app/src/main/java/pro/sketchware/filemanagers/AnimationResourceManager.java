package pro.sketchware.filemanagers;

import com.besome.sketch.beans.CollectionBean;
import com.besome.sketch.beans.ProjectResourceBean;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import pro.sketchware.core.LifecycleTask;
import pro.sketchware.core.StringListStore;
import pro.sketchware.workspace.WorkspacePathManager;

public class AnimationResourceManager extends LifecycleTask {
   public static volatile AnimationResourceManager f;

   public static AnimationResourceManager g() {
      if (f == null) {
         synchronized (AnimationResourceManager.class) {
            if (f == null) {
               f = new AnimationResourceManager();
            }
         }
      }
      return f;
   }

   public ProjectResourceBean a(String var1) {
      for(ProjectResourceBean var3 : this.f()) {
         if (var3.resName.equals(var1)) {
            return var3;
         }
      }

      return null;
   }

   public void a(ProjectResourceBean var1, String var2, boolean var3) {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      int var4 = super.e.size();

      while(true) {
         --var4;
         if (var4 < 0) {
            break;
         }

         CollectionBean var5 = (CollectionBean)super.e.get(var4);
         if (var5.name.equals(var1.resName)) {
            var5.name = var2;
            break;
         }
      }

      if (var3) {
         ((LifecycleTask)this).e();
      }

   }

   public void a(String var1, ProjectResourceBean var2) {
      this.a(var1, var2, true);
   }

   public void a(String var1, ProjectResourceBean var2, boolean var3) {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      ArrayList var4 = new ArrayList();

      for(CollectionBean var6 : super.e) {
         if (var6.name.equals(var2.resName)) {
            var4.add(var6.name);
            break;
         }
      }

      if (var4.size() <= 0) {
         String var16 = var2.resName;
         String var11 = var16;
         if (var2.resFullName.contains(".")) {
            var11 = var2.resFullName;
            var11 = var11.substring(var11.lastIndexOf(46));
            StringBuilder var14 = new StringBuilder();
            var14.append(var16);
            var14.append(var11);
            var11 = var14.toString();
         }

         var16 = var2.resFullName;
         StringBuilder var18 = new StringBuilder();
         var18.append(super.b);
         var18.append(File.separator);
         var18.append(var11);
         String var19 = var18.toString();
         if (var2.savedPos == 1) {
            var1 = var2.resFullName;
            if (!super.c.e(var1)) {
               throw new StringListStore("file_no_exist");
            }

            try {
               super.c.f(super.b);
               super.c.a(var1, var19);
            } catch (Exception var8) {
               throw new StringListStore("fail_to_copy");
            }
         } else {
            StringBuilder var15 = new StringBuilder();
            var15.append(WorkspacePathManager.t());
            var15.append(File.separator);
            var15.append(var1);
            var15.append(File.separator);
            var15.append(var2.resFullName);
            var1 = var15.toString();
            if (!super.c.e(var1)) {
               throw new StringListStore("file_no_exist");
            }

            try {
               super.c.f(super.b);
               super.c.a(var1, var19);
            } catch (Exception var7) {
               throw new StringListStore("fail_to_copy");
            }
         }

         super.e.add(new CollectionBean(var2.resName, var11));
         if (var3) {
            ((LifecycleTask)this).e();
         }

      } else {
         throw new StringListStore("duplicate_name");
      }
   }

   public void a(String var1, boolean var2) {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      int var3 = super.e.size();

      while(true) {
         int var4 = var3 - 1;
         if (var4 < 0) {
            break;
         }

         CollectionBean var5 = (CollectionBean)super.e.get(var4);
         var3 = var4;
         if (var5.name.equals(var1)) {
            super.e.remove(var4);
            StringBuilder var6 = new StringBuilder();
            var6.append(super.b);
            var6.append(File.separator);
            var6.append(var5.data);
            String var7 = var6.toString();
            super.c.c(var7);
            break;
         }
      }

      if (var2) {
         ((LifecycleTask)this).e();
      }

   }

   public void b() {
      StringBuilder var1 = new StringBuilder();
      var1.append(WorkspacePathManager.a());
      var1.append(File.separator);
      var1.append("sound");
      var1.append(File.separator);
      var1.append("list");
      super.a = var1.toString();
      var1 = new StringBuilder();
      var1.append(WorkspacePathManager.a());
      var1.append(File.separator);
      var1.append("sound");
      var1.append(File.separator);
      var1.append("data");
      super.b = var1.toString();
   }

   public boolean b(String var1) {
      Iterator var2 = this.f().iterator();

      while(var2.hasNext()) {
         if (((ProjectResourceBean)var2.next()).resName.equals(var1)) {
            return true;
         }
      }

      return false;
   }

   public void d() {
      super.d();
      f = null;
   }

   public ArrayList<ProjectResourceBean> f() {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      ArrayList<ProjectResourceBean> var1 = new ArrayList<ProjectResourceBean>();

      for(CollectionBean var3 : super.e) {
         var1.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, var3.name, var3.data));
      }

      return var1;
   }
}
