package pro.sketchware.filemanagers;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import pro.sketchware.core.PreferenceStore;

public class ProjectResourceRegistry {
   public static volatile ProjectResourceRegistry a;
   public static int b;
   public HashMap c;
   public PreferenceStore d;

   public static ProjectResourceRegistry a() {
      if (a == null) {
         synchronized (ProjectResourceRegistry.class) {
            if (a == null) {
               a = new ProjectResourceRegistry();
            }
         }
      }
      return a;
   }

   public ArrayList a(String var1) {
      return (ArrayList)this.c.get(var1);
   }

   public void a(Context var1) {
      if (this.c == null) {
         this.c = new HashMap();
      }

      this.c.clear();
      if (this.d == null) {
         this.d = new PreferenceStore(var1, "P26");
      }

   }

   public void a(String var1, String var2) {
      ArrayList var3 = (ArrayList)this.c.get(var1);
      ArrayList var4 = var3;
      if (var3 == null) {
         var4 = new ArrayList();
         this.c.put(var1, var4);
      }

      if (var4.contains(var2)) {
         var4.remove(var2);
      }

      var4.add(0, var2);
      if (var4.size() > b) {
         var4.remove(var4.size() - 1);
      }

   }

   public void b() {
      Iterator var1 = this.c.keySet().iterator();
      String var2 = "";

      while(var1.hasNext()) {
         String var3 = (String)var1.next();

         for(String var5 : (ArrayList<String>)this.c.get(var3)) {
            StringBuilder var6 = new StringBuilder();
            var6.append(var2);
            var6.append(var5);
            var6.append(",");
            var2 = var6.toString();
         }

         this.d.a(var3, var2);
      }

   }

   public void b(String var1) {
      if ((ArrayList)this.c.get(var1) == null) {
         String[] var2 = this.d.f(var1).split(",");
         int var3 = var2.length;

         while(true) {
            int var4 = var3 - 1;
            if (var4 < 0) {
               break;
            }

            var3 = var4;
            if (!var2[var4].isEmpty()) {
               this.a(var1, var2[var4]);
               var3 = var4;
            }
         }
      }

   }
}
