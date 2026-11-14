package pro.sketchware.editor;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ViewBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ViewHistoryManager {
   public static ViewHistoryManager a;
   public Map b;
   public Map c;
   public String d;

   public ViewHistoryManager(String var1) {
      this.d = var1;
      this.c = new HashMap();
      this.b = new HashMap();
   }

   public static void a() {
      ViewHistoryManager var0 = a;
      if (var0 != null) {
         var0.d = "";
         var0.c = null;
         var0.b = null;
      }

      a = null;
   }

   public static ViewHistoryManager c(String var0) {
      if (a == null) {
         synchronized (ViewHistoryManager.class) {
            if (a == null || a.d == null || !a.d.equals(var0)) {
               a = new ViewHistoryManager(var0);
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

   public final void a(String var1, HistoryViewBean var2) {
      if (!this.c.containsKey(var1)) {
         this.e(var1);
      }

      ArrayList var3 = (ArrayList)this.c.get(var1);
      var3.add(var2);
      if (var3.size() > 50) {
         var3.remove(0);
      } else {
         this.d(var1);
      }

   }

   public void a(String var1, ViewBean var2) {
      ArrayList var3 = new ArrayList();
      var3.add(var2);
      this.a(var1, var3);
   }

   public void a(String var1, ViewBean var2, ViewBean var3) {
      if (!var2.isEqual(var3)) {
         HistoryViewBean var4 = new HistoryViewBean();
         var4.actionUpdate(var2, var3);
         if (!this.c.containsKey(var1)) {
            this.e(var1);
         }

         this.a(var1);
         this.a(var1, var4);
      }
   }

   public void a(String var1, ArrayList var2) {
      HistoryViewBean var3 = new HistoryViewBean();
      var3.actionAdd(var2);
      if (!this.c.containsKey(var1)) {
         this.e(var1);
      }

      this.a(var1);
      this.a(var1, var3);
   }

   public final void b(String var1) {
      if (!this.b.containsKey(var1)) {
         this.e(var1);
      }

      int var2 = (Integer)this.b.get(var1);
      if (var2 != 0) {
         this.b.put(var1, var2 - 1);
      }
   }

   public void b(String var1, ViewBean var2) {
      HistoryViewBean var3 = new HistoryViewBean();
      var3.actionMove(var2);
      if (!this.c.containsKey(var1)) {
         this.e(var1);
      }

      this.a(var1);
      this.a(var1, var3);
   }

   public void b(String var1, ArrayList var2) {
      HistoryViewBean var3 = new HistoryViewBean();
      var3.actionRemove(var2);
      if (!this.c.containsKey(var1)) {
         this.e(var1);
      }

      this.a(var1);
      this.a(var1, var3);
   }

   public final void d(String var1) {
      if (!this.b.containsKey(var1)) {
         this.e(var1);
      }

      int var2 = (Integer)this.b.get(var1);
      this.b.put(var1, var2 + 1);
   }

   public void e(String var1) {
      this.c.put(var1, new ArrayList());
      this.b.put(var1, 0);
   }

   public boolean f(String var1) {
      if (!this.b.containsKey(var1)) {
         return false;
      } else {
         return (Integer)this.b.get(var1) < ((ArrayList)this.c.get(var1)).size();
      }
   }

   public boolean g(String var1) {
      if (!this.b.containsKey(var1)) {
         return false;
      } else {
         return (Integer)this.b.get(var1) > 0;
      }
   }

   public HistoryViewBean h(String var1) {
      if (!this.f(var1)) {
         return null;
      } else {
         int var2 = (Integer)this.b.get(var1);
         this.d(var1);
         return ((HistoryViewBean)((ArrayList)this.c.get(var1)).get(var2 - 1 + 1)).clone();
      }
   }

   public HistoryViewBean i(String var1) {
      if (!this.g(var1)) {
         return null;
      } else {
         int var2 = (Integer)this.b.get(var1);
         this.b(var1);
         return ((HistoryViewBean)((ArrayList)this.c.get(var1)).get(var2 - 1)).clone();
      }
   }
}
