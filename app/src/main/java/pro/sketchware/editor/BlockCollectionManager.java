package pro.sketchware.editor;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.BlockCollectionBean;
import com.besome.sketch.beans.CollectionBean;
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

public class BlockCollectionManager extends LifecycleTask {
   public static BlockCollectionManager f;
   public Gson g = null;

   public BlockCollectionManager() {
      this.i();
   }

   public static BlockCollectionManager h() {
      if (f == null) {
         synchronized(BlockCollectionManager.class){}

         Throwable var10000;
         label144: {
            try {
               if (f == null) {
                  BlockCollectionManager var0 = new BlockCollectionManager();
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

   public BlockCollectionBean a(String var1) {
      for(CollectionBean var3 : super.e) {
         if (var3.name.equals(var1)) {
            return new BlockCollectionBean(var3.name, ProjectDataJsonCodec.a(this.g, var3.data));
         }
      }

      return null;
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

   public void a(String var1, ArrayList var2, boolean var3) {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      if (this.g == null) {
         this.i();
      }

      Iterator var4 = super.e.iterator();

      while(var4.hasNext()) {
         if (((CollectionBean)var4.next()).name.equals(var1)) {
            throw new StringListStore("duplicate_name");
         }
      }

      var4 = var2.iterator();

      StringBuilder var6;
      String var7;
      for(var7 = ""; var4.hasNext(); var7 = var6.toString()) {
         BlockBean var5 = (BlockBean)var4.next();
         var6 = new StringBuilder();
         var6.append(var7);
         var6.append(this.g.toJson(var5));
         var6.append("\n");
      }

      super.e.add(new CollectionBean(var1, var7));
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
      var1.append("block");
      var1.append(File.separator);
      var1.append("list");
      super.a = var1.toString();
      var1 = new StringBuilder();
      var1.append(WorkspacePathManager.a());
      var1.append(File.separator);
      var1.append("block");
      var1.append(File.separator);
      var1.append("data");
      super.b = var1.toString();
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

   public ArrayList<BlockCollectionBean> f() {
      if (super.e == null) {
         ((LifecycleTask)this).a();
      }

      if (this.g == null) {
         this.i();
      }

      ArrayList<BlockCollectionBean> var1 = new ArrayList<BlockCollectionBean>();

      for(CollectionBean var3 : super.e) {
         var1.add(new BlockCollectionBean(var3.name, ProjectDataJsonCodec.a(this.g, var3.data)));
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
