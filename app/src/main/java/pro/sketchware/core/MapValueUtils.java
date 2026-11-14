package pro.sketchware.core;

import java.util.Map;

public class MapValueUtils {
   public static int a(Map var0, String var1, int var2) {
      Object var3 = a(var0, var1, (Object)var2);
      if (var3 instanceof Integer) {
         return (Integer)a(var0, var1, (Object)var2);
      } else {
         return var3 instanceof Double ? ((Double)a(var0, var1, (Object)var2)).intValue() : var2;
      }
   }

   public static Object a(Map var0, String var1, Object var2) {
      if (var0 == null) {
         return var2;
      } else {
         Object var3 = var0.get(var1);
         return var3 == null ? var2 : var3;
      }
   }

   public static boolean a(Map var0, String var1) {
      return (Boolean)a(var0, var1, false);
   }

   public static boolean a(Map var0, String var1, boolean var2) {
      return (Boolean)a(var0, var1, (Object)var2);
   }

   public static int b(Map var0, String var1) {
      return a(var0, var1, -1);
   }

   public static String c(Map var0, String var1) {
      return (String)a(var0, var1, "");
   }
}
