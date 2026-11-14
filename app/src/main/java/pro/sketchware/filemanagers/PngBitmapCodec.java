package pro.sketchware.filemanagers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.NinePatch;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;

public class PngBitmapCodec {
   public static Bitmap a(InputStream var0) throws IOException, NoSuchFieldException, IllegalAccessException {
      Bitmap var1 = BitmapFactory.decodeStream(var0);
      byte[] var2 = a(var1);
      if (NinePatch.isNinePatchChunk(var2)) {
         Bitmap var3 = Bitmap.createBitmap(var1, 1, 1, var1.getWidth() - 2, var1.getHeight() - 2);
         var1.recycle();
         Field var4 = var3.getClass().getDeclaredField("mNinePatchChunk");
         var4.setAccessible(true);
         var4.set(var3, var2);
         return var3;
      } else {
         return var1;
      }
   }

   public static Bitmap a(String var0) throws FileNotFoundException, IOException, NoSuchFieldException, IllegalAccessException {
      FileInputStream var1 = new FileInputStream(var0);
      try {
         return a((InputStream)var1);
      } finally {
         var1.close();
      }
   }

   public static void a(Bitmap var0, byte[] var1) {
      int[] var2 = new int[var0.getWidth() - 2];
      var0.getPixels(var2, 0, var2.length, 1, var0.getHeight() - 1, var2.length, 1);
      byte var3 = 0;

      for(int var4 = 0; var4 < var2.length; ++var4) {
         if (-16777216 == var2[var4]) {
            a(var1, 12, var4);
            break;
         }
      }

      for(int var6 = var2.length - 1; var6 >= 0; --var6) {
         if (-16777216 == var2[var6]) {
            a(var1, 16, var2.length - var6 - 2);
            break;
         }
      }

      var2 = new int[var0.getHeight() - 2];
      var0.getPixels(var2, 0, 1, var0.getWidth() - 1, 0, 1, var2.length);

      for(int var7 = var3; var7 < var2.length; ++var7) {
         if (-16777216 == var2[var7]) {
            a(var1, 20, var7);
            break;
         }
      }

      for(int var8 = var2.length - 1; var8 >= 0; --var8) {
         if (-16777216 == var2[var8]) {
            a(var1, 24, var2.length - var8 - 2);
            break;
         }
      }

   }

   public static void a(OutputStream var0, int var1) throws IOException {
      var0.write(var1 >> 0 & 255);
      var0.write(var1 >> 8 & 255);
      var0.write(var1 >> 16 & 255);
      var0.write(var1 >> 24 & 255);
   }

   public static void a(byte[] var0, int var1, int var2) {
      var0[var1 + 0] = (byte)(var2 >> 0);
      var0[var1 + 1] = (byte)(var2 >> 8);
      var0[var1 + 2] = (byte)(var2 >> 16);
      var0[var1 + 3] = (byte)(var2 >> 24);
   }

   public static byte[] a(Bitmap var0) throws IOException {
      int var1 = var0.getWidth();
      int var2 = var0.getHeight();
      ByteArrayOutputStream var3 = new ByteArrayOutputStream();

      for(int var4 = 0; var4 < 32; ++var4) {
         var3.write(0);
      }

      int var17 = var1 - 2;
      int[] var5 = new int[var17];
      var0.getPixels(var5, 0, var1, 1, 0, var17, 1);
      boolean var6;
      if (var5[0] == -16777216) {
         var6 = true;
      } else {
         var6 = false;
      }

      boolean var7;
      if (var5[var5.length - 1] == -16777216) {
         var7 = true;
      } else {
         var7 = false;
      }

      int var8 = var5.length;
      var1 = 0;
      int var9 = 0;

      int var11;
      for(var17 = 0; var1 < var8; var17 = var11) {
         int var10 = var9;
         var11 = var17;
         if (var9 != var5[var1]) {
            var11 = var17 + 1;
            a(var3, var1);
            var10 = var5[var1];
         }

         ++var1;
         var9 = var10;
      }

      var1 = var17;
      if (var7) {
         var1 = var17 + 1;
         a(var3, var5.length);
      }

      var11 = var1 + 1;
      var17 = var11;
      if (var6) {
         var17 = var11 - 1;
      }

      int var13 = var17;
      if (var7) {
         var13 = var17 - 1;
      }

      var17 = var2 - 2;
      var5 = new int[var17];
      var0.getPixels(var5, 0, 1, 0, 1, 1, var17);
      boolean var15;
      if (var5[0] == -16777216) {
         var15 = true;
      } else {
         var15 = false;
      }

      if (var5[var5.length - 1] == -16777216) {
         var7 = true;
      } else {
         var7 = false;
      }

      int var12 = var5.length;
      int var31 = 0;
      var2 = 0;

      for(var17 = 0; var31 < var12; var17 = var9) {
         var8 = var2;
         var9 = var17;
         if (var2 != var5[var31]) {
            var9 = var17 + 1;
            a(var3, var31);
            var8 = var5[var31];
         }

         ++var31;
         var2 = var8;
      }

      var31 = var17;
      if (var7) {
         var31 = var17 + 1;
         a(var3, var5.length);
      }

      var9 = var31 + 1;
      var17 = var9;
      if (var15) {
         var17 = var9 - 1;
      }

      var11 = var17;
      if (var7) {
         var11 = var17 - 1;
      }

      var17 = 0;

      while(true) {
         int var14 = var13 * var11;
         if (var17 >= var14) {
            byte[] var16 = var3.toByteArray();
            var16[0] = 1;
            var16[1] = (byte)var1;
            var16[2] = (byte)var31;
            var16[3] = (byte)var14;
            a(var0, var16);
            return var16;
         }

         a(var3, 1);
         ++var17;
      }
   }
}
