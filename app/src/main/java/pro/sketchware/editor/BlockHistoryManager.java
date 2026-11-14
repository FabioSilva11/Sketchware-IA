package pro.sketchware.editor;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.HistoryBlockBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BlockHistoryManager {
   public static BlockHistoryManager a;
   public Map b;
   public Map c;
   public String d;

   public BlockHistoryManager(String var1) {
      this.d = var1;
      this.c = new HashMap();
      this.b = new HashMap();
   }

   public static String a(String var0, String var1, String var2) {
      StringBuilder var3 = new StringBuilder();
      var3.append(var0);
      var3.append("_");
      var3.append(var1);
      var3.append("_");
      var3.append(var2);
      return var3.toString();
   }

   public static void a() {
      BlockHistoryManager var0 = a;
      if (var0 != null) {
         var0.d = "";
         var0.c = null;
         var0.b = null;
      }

      a = null;
   }

   public static BlockHistoryManager d(String var0) {
      if (a == null) {
         synchronized (BlockHistoryManager.class) {
            if (a == null || a.d == null || !a.d.equals(var0)) {
               a = new BlockHistoryManager(var0);
            }
         }
      }
      return a;
   }

   public final void a(String var1) {
      if (this.b.containsKey(var1)) {
         ArrayList var2 = (ArrayList)this.c.get(var1);
         int var3 = (Integer)this.b.get(var1);
         if (var2 != null) {
            for(int var4 = var2.size(); var4 > var3; --var4) {
               var2.remove(var4 - 1);
            }

         }
      }
   }

   public void a(String var1, BlockBean var2, int var3, int var4, BlockBean var5, BlockBean var6) {
      ArrayList var7 = new ArrayList();
      var7.add(var2);
      this.a(var1, var7, var3, var4, var5, var6);
   }

   public void a(String var1, BlockBean var2, BlockBean var3) {
      if (!var2.isEqual(var3)) {
         HistoryBlockBean var4 = new HistoryBlockBean();
         var4.actionUpdate(var2, var3);
         if (!this.c.containsKey(var1)) {
            this.f(var1);
         }

         this.a(var1);
         this.a(var1, var4);
      }
   }

   public final void a(String var1, HistoryBlockBean var2) {
      if (!this.c.containsKey(var1)) {
         this.f(var1);
      }

      ArrayList var3 = (ArrayList)this.c.get(var1);
      var3.add(var2);
      if (var3.size() > 50) {
         var3.remove(0);
      } else {
         this.e(var1);
      }

   }

   public void a(String var1, ArrayList var2, int var3, int var4, BlockBean var5, BlockBean var6) {
      HistoryBlockBean var7 = new HistoryBlockBean();
      var7.actionAdd(var2, var3, var4, var5, var6);
      if (!this.c.containsKey(var1)) {
         this.f(var1);
      }

      this.a(var1);
      this.a(var1, var7);
   }

   public void a(String var1, ArrayList var2, ArrayList var3, int var4, int var5, int var6, int var7, BlockBean var8, BlockBean var9, BlockBean var10, BlockBean var11) {
      HistoryBlockBean var12 = new HistoryBlockBean();
      var12.actionMove(var2, var3, var4, var5, var6, var7, var8, var9, var10, var11);
      if (!this.c.containsKey(var1)) {
         this.f(var1);
      }

      this.a(var1);
      this.a(var1, var12);
   }

   public void b(String var1) {
      if (this.c.containsKey(var1)) {
         this.c.remove(var1);
         this.b.remove(var1);
      }

   }

   public void b(String var1, ArrayList var2, int var3, int var4, BlockBean var5, BlockBean var6) {
      HistoryBlockBean var7 = new HistoryBlockBean();
      var7.actionRemove(var2, var3, var4, var5, var6);
      if (!this.c.containsKey(var1)) {
         this.f(var1);
      }

      this.a(var1);
      this.a(var1, var7);
   }

   public final void c(String var1) {
      if (!this.b.containsKey(var1)) {
         this.f(var1);
      }

      int var2 = (Integer)this.b.get(var1);
      if (var2 != 0) {
         this.b.put(var1, var2 - 1);
      }
   }

   public final void e(String var1) {
      if (!this.b.containsKey(var1)) {
         this.f(var1);
      }

      int var2 = (Integer)this.b.get(var1);
      this.b.put(var1, var2 + 1);
   }

   public void f(String var1) {
      this.c.put(var1, new ArrayList());
      this.b.put(var1, 0);
   }

   public boolean g(String var1) {
      if (!this.b.containsKey(var1)) {
         return false;
      } else {
         return (Integer)this.b.get(var1) < ((ArrayList)this.c.get(var1)).size();
      }
   }

   public boolean h(String var1) {
      if (!this.b.containsKey(var1)) {
         return false;
      } else {
         return (Integer)this.b.get(var1) > 0;
      }
   }

   public HistoryBlockBean i(String var1) {
      if (!this.g(var1)) {
         return null;
      } else {
         int var2 = (Integer)this.b.get(var1);
         this.e(var1);
         return ((HistoryBlockBean)((ArrayList)this.c.get(var1)).get(var2 - 1 + 1)).clone();
      }
   }

   public HistoryBlockBean j(String var1) {
      if (!this.h(var1)) {
         return null;
      } else {
         int var2 = (Integer)this.b.get(var1);
         this.c(var1);
         return ((HistoryBlockBean)((ArrayList)this.c.get(var1)).get(var2 - 1)).clone();
      }
   }
}
