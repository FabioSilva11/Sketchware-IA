package pro.sketchware.editor;

public class BlockTypeUtils {
   public static boolean a;
   public static long[] b = new long[10];

   public static long a(int var0) {
      if (b == null) {
         b = new long[10];
      }

      return System.currentTimeMillis() - b[var0];
   }

   public static boolean b(int var0) {
      if (b == null) {
         b = new long[10];
      }

      long var1 = a(var0);
      boolean var3;
      if (b[var0] != 0L && var1 <= 30000L) {
         var3 = false;
      } else {
         var3 = true;
      }

      return var3;
   }
}
