package pro.sketchware.core;

import com.besome.sketch.beans.CollectionBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import pro.sketchware.filemanagers.FileCryptoUtils;

public abstract class LifecycleTask {
   public String a;
   public String b;
   public FileCryptoUtils c;
   public Gson d;
   public ArrayList<CollectionBean> e;

   public LifecycleTask() {
      this.a();
   }

   public void a() {
      this.b();
      this.c = new FileCryptoUtils();
      this.d = (new GsonBuilder()).create();
      this.c();
   }

   public abstract void b();

   public void c() {
      this.e = new ArrayList();
      BufferedReader var2 = null;
      try {
         String var1 = this.c.g(this.a);
         var2 = new BufferedReader(new StringReader(var1));
         String var3 = var2.readLine();
         while (var3 != null) {
            if (var3.length() > 0) {
               CollectionBean var4 = this.d.fromJson(var3, CollectionBean.class);
               if (this.c.e(this.b + File.separator + var4.data)) {
                  this.e.add(var4);
               }
            }

            var3 = var2.readLine();
         }

         var2.close();
      } catch (Exception var5) {
         var5.printStackTrace();
         if (var2 != null) {
            try {
               var2.close();
            } catch (IOException var6) {
               var6.printStackTrace();
            }
         }
      }

   }

   public void d() {
      ArrayList var1 = this.e;
      if (var1 != null) {
         var1.clear();
         this.e = null;
      }

   }

   public void e() {
      if (this.e != null) {
         StringBuilder var1 = new StringBuilder(1024);

         for(CollectionBean var3 : this.e) {
            var1.append(this.d.toJson(var3));
            var1.append("\n");
         }

         try {
            this.c.b(this.a);
            this.c.b(this.a, var1.toString());
         } catch (Exception var4) {
            var4.printStackTrace();
         }

      }
   }
}
