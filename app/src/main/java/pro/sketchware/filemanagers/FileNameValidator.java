package pro.sketchware.filemanagers;

public class FileNameValidator {
   public static boolean a(String var0) {
      return true;
   }

   public static boolean b(String var0) {
      int var1;
      int var2;
      try {
         if (Integer.valueOf(var0) < Integer.valueOf("200")) {
            return false;
         }

         var1 = Integer.valueOf(var0);
         var2 = Integer.valueOf("600");
      } catch (Exception var3) {
         return false;
      }

      if (var1 < var2) {
         return true;
      } else {
         return false;
      }
   }
}
