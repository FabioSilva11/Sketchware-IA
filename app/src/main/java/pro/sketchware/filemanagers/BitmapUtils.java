package pro.sketchware.filemanagers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
public class BitmapUtils {
   public static int a(BitmapFactory.Options var0, int var1, int var2) {
      int var3 = var0.outWidth;
      int var4 = var0.outHeight;
      int var5 = 1;
      int var6 = 1;
      if (var4 > var2 || var3 > var1) {
         var4 /= 2;
         var3 /= 2;

         while(true) {
            var5 = var6;
            if (var4 / var6 < var2) {
               break;
            }

            var5 = var6;
            if (var3 / var6 < var1) {
               break;
            }

            var6 *= 2;
         }
      }

      return var5;
   }

   public static int a(String var0) {
      int var1;

      try {
         var1 = (new ExifInterface(var0)).getAttributeInt("Orientation", -1);
      } catch (Exception e) {
         return 0;
      }
      if (var1 != 3) {
         if (var1 != 6) {
            if (var1 != 8) {
               var1 = 0;
            } else {
               var1 = 270;
            }
         } else {
            var1 = 90;
         }
      } else {
         var1 = 180;
      }

      return var1;
   }

   public static Bitmap a(Bitmap var0, int var1) {
      Matrix var2 = new Matrix();
      var2.postRotate((float)var1);
      return Bitmap.createBitmap(var0, 0, 0, var0.getWidth(), var0.getHeight(), var2, false);
   }

   public static Bitmap a(Bitmap var0, int var1, int var2, int var3) {
      Matrix var4 = new Matrix();
      var4.setScale((float)var2, (float)var3);
      var4.postRotate((float)var1);
      return Bitmap.createBitmap(var0, 0, 0, var0.getWidth(), var0.getHeight(), var4, false);
   }

   public static Bitmap a(String var0, int var1) {
      BitmapFactory.Options var2 = new BitmapFactory.Options();
      var2.inSampleSize = var1;
      return BitmapFactory.decodeFile(var0, var2);
   }

   public static Bitmap a(String var0, int var1, int var2) {
      BitmapFactory.Options var3 = new BitmapFactory.Options();
      var3.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(var0, var3);
      var3.inSampleSize = a(var3, var1, var2);
      var3.inJustDecodeBounds = false;
      return BitmapFactory.decodeFile(var0, var3);
   }

   public static void a(String param0, String param1, int param2, int param3, int param4) {
      Bitmap var5 = a(param0, 512, 512);
      int var6 = 0;
      try {
         var6 = a(param0);
      } catch (Exception var0) {
         var0.printStackTrace();
      }
      if (var6 > 0) {
         var5 = a(var5, var6);
      }
      Bitmap var7 = a(var5, param2, param3, param4);
      FileOutputStream var8 = null;
      try {
         var8 = new FileOutputStream(new File(param1));
         var7.compress(Bitmap.CompressFormat.PNG, 100, var8);
         var8.flush();
         var7.recycle();
         var8.close();
      } catch (Exception var1) {
         var1.printStackTrace();
         if (var8 != null) {
            try {
               var8.close();
            } catch (Exception var9) {
            }
         }
      }
   }
}
