package pro.sketchware.editor;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.CollectionBean;
import com.besome.sketch.beans.MoreBlockCollectionBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;

import pro.sketchware.beans.ProjectDataJsonCodec;
import pro.sketchware.core.LifecycleTask;
import pro.sketchware.core.StringListStore;
import pro.sketchware.workspace.WorkspacePathManager;

public class MoreBlockCollectionManager extends LifecycleTask {
   public static MoreBlockCollectionManager f;
   public Gson g = null;

   public MoreBlockCollectionManager() {
      this.i();
   }

   public static MoreBlockCollectionManager h() {
      if (f == null) {
         synchronized (MoreBlockCollectionManager.class) {
            if (f == null) {
               f = new MoreBlockCollectionManager();
            }
         }
      }
      return f;
   }

   public MoreBlockCollectionBean a(String var1) {
      for(CollectionBean var3 : super.e) {
         if (var3.name.equals(var1)) {
            return new MoreBlockCollectionBean(var3.name, var3.reserved1, ProjectDataJsonCodec.a(this.g, var3.data));
         }
      }

      return null;
   }

   public void a(String var1, String var2, ArrayList var3, boolean var4) {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      if (this.g == null) {
         this.i();
      }

      Iterator var5 = super.e.iterator();

      while(var5.hasNext()) {
         if (((CollectionBean)var5.next()).name.equals(var1)) {
            throw new StringListStore("duplicate_name");
         }
      }

      var5 = var3.iterator();

      StringBuilder var7;
      String var8;
      for(var8 = ""; var5.hasNext(); var8 = var7.toString()) {
         BlockBean var6 = (BlockBean)var5.next();
         var7 = new StringBuilder();
         var7.append(var8);
         var7.append(this.g.toJson(var6));
         var7.append("\n");
      }

      super.e.add(new CollectionBean(var1, var8, var2));
      if (var4) {
         ((LifecycleTask)this).e();
      }

   }

   public void a(String var1, String var2, boolean var3) {
      for(CollectionBean var5 : super.e) {
         if (var5.name.equals(var1)) {
            var5.name = var2;
            break;
         }
      }

      if (var3) {
         ((LifecycleTask)this).e();
      }

   }

   public void a(String var1, boolean var2) {
      int var3 = super.e.size();

      while(true) {
         int var4 = var3 - 1;
         if (var4 < 0) {
            break;
         }

         var3 = var4;
         if (((CollectionBean)super.e.get(var4)).name.equals(var1)) {
            super.e.remove(var4);
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
      var1.append("more_block");
      var1.append(File.separator);
      var1.append("list");
      super.a = var1.toString();
   }

   public void c() {
      this.e = new ArrayList();
      if (this.c.e(this.a)) {
         String var1 = this.c.g(this.a);
         BufferedReader var2 = null;
         try {
            var2 = new BufferedReader(new StringReader(var1));
            String var3 = var2.readLine();
            while (var3 != null) {
               if (var3.length() > 0) {
                  CollectionBean var4 = this.d.fromJson(var3, CollectionBean.class);
                  this.e.add(var4);
               }

               var3 = var2.readLine();
            }
         } catch (IOException var5) {
            var5.printStackTrace();
         } finally {
            if (var2 != null) {
               try {
                  var2.close();
               } catch (IOException ignored) {
               }
            }
         }
      }

   }

   public ArrayList f() {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      if (this.g == null) {
         this.i();
      }

      ArrayList var1 = new ArrayList();

      for(CollectionBean var3 : super.e) {
         var1.add(new MoreBlockCollectionBean(var3.name, var3.reserved1, ProjectDataJsonCodec.a(this.g, var3.data)));
      }

      return var1;
   }

   public ArrayList<String> g() {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      ArrayList var1 = new ArrayList();
      Iterator var2 = super.e.iterator();

      while(var2.hasNext()) {
         var1.add(((CollectionBean)var2.next()).name);
      }

      return var1;
   }

   public final void i() {
      this.g = (new GsonBuilder()).excludeFieldsWithoutExposeAnnotation().create();
   }
}
