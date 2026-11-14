package pro.sketchware.core;

public class ColorUtils {
   public static final String[] a = new String[]{"Default", "NoActionBar", "FullScreen"};
   public static final String[] b = new String[]{"Portrait", "Landscape", "Both"};

   public static String a(int var0) {
      var0 &= 15;
      String var1;
      if (var0 == 1) {
         var1 = "stateVisible";
      } else if (var0 == 2) {
         var1 = "stateHidden";
      } else {
         var1 = "";
      }

      return var1;
   }
}
