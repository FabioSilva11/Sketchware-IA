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

public class SoundResourceManager extends LifecycleTask {
   public static volatile SoundResourceManager f;

   public static SoundResourceManager g() {
      if (f == null) {
         synchronized (SoundResourceManager.class) {
            if (f == null) {
               f = new SoundResourceManager();
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

      ArrayList<String> var4 = new ArrayList<String>();

      for(CollectionBean var6 : super.e) {
         if (var6.name.equals(var2.resName)) {
            var4.add(var6.name);
            break;
         }
      }

      if (var4.size() <= 0) {
         String var12 = var2.resName;
         if (var2.isNinePatch()) {
            StringBuilder var14 = new StringBuilder();
            var14.append(var12);
            var14.append(".9.png");
            var12 = var14.toString();
         } else {
            StringBuilder var15 = new StringBuilder();
            var15.append(var12);
            var15.append(".png");
            var12 = var15.toString();
         }

         StringBuilder var16 = new StringBuilder();
         var16.append(super.b);
         var16.append(File.separator);
         var16.append(var12);
         String var17 = var16.toString();
         if (var2.savedPos == 1) {
            var1 = var2.resFullName;
            if (!super.c.e(var1)) {
               throw new StringListStore("file_no_exist");
            }

            try {
               super.c.f(super.b);
               BitmapUtils.a(var1, var17, var2.rotate, var2.flipHorizontal, var2.flipVertical);
            } catch (Exception var8) {
               throw new StringListStore("fail_to_copy");
            }
         } else {
            StringBuilder var11 = new StringBuilder();
            var11.append(WorkspacePathManager.g());
            var11.append(File.separator);
            var11.append(var1);
            var11.append(File.separator);
            var11.append(var2.resFullName);
            var1 = var11.toString();
            if (!super.c.e(var1)) {
               throw new StringListStore("file_no_exist");
            }

            try {
               super.c.f(super.b);
               super.c.a(var1, var17);
            } catch (Exception var7) {
               throw new StringListStore("fail_to_copy");
            }
         }

         super.e.add(new CollectionBean(var2.resName, var12));
         if (var3) {
            ((LifecycleTask)this).e();
         }

      } else {
         throw new StringListStore("duplicate_name");
      }
   }

   public void a(String var1, ArrayList<ProjectResourceBean> var2, boolean var3) {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      ArrayList<String> var4 = new ArrayList<String>();

      for(CollectionBean var6 : super.e) {
         for(ProjectResourceBean var8 : var2) {
            if (var6.name.equals(var8.resName)) {
               var4.add(var6.name);
            }
         }
      }

      if (var4.size() > 0) {
         StringListStore var14 = new StringListStore("duplicate_name");
         var14.a(var4);
         throw var14;
      } else {
         ArrayList<String> var26 = new ArrayList<String>();

         for(ProjectResourceBean var30 : var2) {
            String var19;
            if (var30.savedPos == 0) {
               StringBuilder var18 = new StringBuilder();
               var18.append(WorkspacePathManager.g());
               var18.append(File.separator);
               var18.append(var1);
               var18.append(File.separator);
               var18.append(var30.resFullName);
               var19 = var18.toString();
            } else {
               var19 = var30.resFullName;
            }

            if (!super.c.e(var19)) {
               var26.add(var30.resName);
            }
         }

         if (var26.size() > 0) {
            StringListStore var13 = new StringListStore("file_no_exist");
            var13.a(var26);
            throw var13;
         } else {
            var26 = new ArrayList<String>();
            ArrayList<String> var31 = new ArrayList<String>();

            for(ProjectResourceBean var32 : var2) {
               String var15 = var32.resName;
               if (var32.isNinePatch()) {
                  StringBuilder var20 = new StringBuilder();
                  var20.append(var15);
                  var20.append(".9.png");
                  var15 = var20.toString();
               } else {
                  StringBuilder var21 = new StringBuilder();
                  var21.append(var15);
                  var21.append(".png");
                  var15 = var21.toString();
               }

               String var23;
               if (var32.savedPos == 0) {
                  StringBuilder var22 = new StringBuilder();
                  var22.append(WorkspacePathManager.g());
                  var22.append(File.separator);
                  var22.append(var1);
                  var22.append(File.separator);
                  var22.append(var32.resFullName);
                  var23 = var22.toString();
               } else {
                  var23 = var32.resFullName;
               }

               StringBuilder var9 = new StringBuilder();
               var9.append(super.b);
               var9.append(File.separator);
               var9.append(var15);
               String var33 = var9.toString();

               try {
                  super.c.f(super.b);
                  BitmapUtils.a(var23, var33, var32.rotate, var32.flipHorizontal, var32.flipVertical);
                  ArrayList var10 = super.e;
                  CollectionBean var24 = new CollectionBean(var32.resName, var15);
                  var10.add(var24);
                  var31.add(var33);
               } catch (Exception var11) {
                  var26.add(var32.resName);
               }
            }

            if (var26.size() <= 0) {
               if (var3) {
                  ((LifecycleTask)this).e();
               }

            } else {
               StringListStore var12 = new StringListStore("fail_to_copy");
               var12.a(var26);
               if (var31.size() > 0) {
                  for(String var17 : var31) {
                     super.c.c(var17);
                  }
               }

               throw var12;
            }
         }
      }
   }

   public void a(String var1, boolean var2) {
      int var3 = super.e.size();

      while(true) {
         int var4 = var3 - 1;
         if (var4 < 0) {
            if (var2) {
               ((LifecycleTask)this).e();
            }

            return;
         }

         CollectionBean var5 = (CollectionBean)super.e.get(var4);
         var3 = var4;
         if (var5.name.equals(var1)) {
            String var7 = var5.data;
            StringBuilder var6 = new StringBuilder();
            var6.append(super.b);
            var6.append(File.separator);
            var6.append(var7);
            var7 = var6.toString();
            super.c.c(var7);
            super.e.remove(var4);
            var3 = var4;
         }
      }
   }

   public void b() {
      StringBuilder var1 = new StringBuilder();
      var1.append(WorkspacePathManager.a());
      var1.append(File.separator);
      var1.append("image");
      var1.append(File.separator);
      var1.append("list");
      super.a = var1.toString();
      var1 = new StringBuilder();
      var1.append(WorkspacePathManager.a());
      var1.append(File.separator);
      var1.append("image");
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
