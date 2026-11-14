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

public class ImageResourceManager extends LifecycleTask {
   public static ImageResourceManager f;

   public static ImageResourceManager g() {
      if (f == null) {
         synchronized(ImageResourceManager.class){}

         Throwable var10000;
         label144: {
            try {
               if (f == null) {
                  ImageResourceManager var0 = new ImageResourceManager();
                  f = var0;
               }
            } catch (Throwable var12) {
               var10000 = var12;
               boolean var10001 = false;
               break label144;
            }

            label141:
            try {
               return f;
            } catch (Throwable var11) {
               var10000 = var11;
               boolean var14 = false;
               break label141;
            }
         }

         while(true) {
            Throwable var13 = var10000;

            try {
               throw var13;
            } catch (Throwable var10) {
               var10000 = var10;
               boolean var15 = false;
               continue;
            }
         }
      } else {
         return f;
      }
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
         String var13 = var16;
         if (var2.resFullName.contains(".")) {
            var13 = var2.resFullName;
            var13 = var13.substring(var13.lastIndexOf(46));
            StringBuilder var11 = new StringBuilder();
            var11.append(var16);
            var11.append(var13);
            var13 = var11.toString();
         }

         var16 = var2.resFullName;
         StringBuilder var18 = new StringBuilder();
         var18.append(super.b);
         var18.append(File.separator);
         var18.append(var13);
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
            StringBuilder var12 = new StringBuilder();
            var12.append(WorkspacePathManager.d());
            var12.append(File.separator);
            var12.append(var1);
            var12.append(File.separator);
            var12.append(var2.resFullName);
            var1 = var12.toString();
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

         super.e.add(new CollectionBean(var2.resName, var13));
         if (var3) {
            ((LifecycleTask)this).e();
         }

      } else {
         throw new StringListStore("duplicate_name");
      }
   }

   public void a(String var1, boolean var2) {
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
            String var6 = var5.data;
            FileCryptoUtils var8 = super.c;
            StringBuilder var7 = new StringBuilder();
            var7.append(super.b);
            var7.append(File.separator);
            var7.append(var6);
            var8.c(var7.toString());
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
      var1.append("font");
      var1.append(File.separator);
      var1.append("list");
      super.a = var1.toString();
      var1 = new StringBuilder();
      var1.append(WorkspacePathManager.a());
      var1.append(File.separator);
      var1.append("font");
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
