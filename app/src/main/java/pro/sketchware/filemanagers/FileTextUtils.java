package pro.sketchware.filemanagers;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;

import pro.sketchware.core.DeviceEnvironmentUtils;
import pro.sketchware.lib.validator.TextInputValidator;
import pro.sketchware.workspace.ProjectArchiveManager;

public class FileTextUtils {
   public static int a(char var0) {
      if (var0 >= '0' && var0 <= '9') {
         return var0 - 48;
      } else {
         byte var1 = 65;
         if (var0 < 'A' || var0 > 'F') {
            var1 = 97;
            if (var0 < 'a' || var0 > 'f') {
               StringBuilder var2 = new StringBuilder("invalid hex digit '");
               var2.append(var0);
               var2.append("'");
               throw new IllegalArgumentException(var2.toString());
            }
         }

         return var0 - var1 + 10;
      }
   }

   public static String a() {
      Random var0 = new Random();

      int var1;
      for(var1 = var0.nextInt(100000); var1 < 10000 || var1 > 99999; var1 = var0.nextInt(100000)) {
      }

      return String.valueOf(var1);
   }

   public static String a(int var0) {
      if (var0 < 0) {
         return "0";
      } else {
         float var1 = (float)var0;
         String var6;
         if (var1 >= 1024.0F && var1 < 1048576.0F) {
            var1 /= 1024.0F;
            var6 = (new DecimalFormat("#.#KB")).format((double)var1);
         } else if (var1 >= 1048576.0F && var1 < 1.0737418E9F) {
            var1 /= 1048576.0F;
            var6 = (new DecimalFormat("#.#MB")).format((double)var1);
         } else if (var1 >= 1.0737418E9F && var1 < 1.0995116E12F) {
            var1 /= 1.0737418E9F;
            var6 = (new DecimalFormat("#.#GB")).format((double)var1);
         } else {
            StringBuilder var2 = new StringBuilder();
            var2.append(String.valueOf(var0));
            var2.append("B");
            var6 = var2.toString();
         }

         return var6;
      }
   }

   public static String a(byte[] var0) {
      StringBuffer var1 = new StringBuffer(var0.length * 2);

      for(int var2 = 0; var2 < var0.length; ++var2) {
         if ((var0[var2] & 255) < 16) {
            var1.append("0");
         }

         var1.append(Long.toString((long)(var0[var2] & 255), 16));
      }

      return var1.toString();
   }

   public static void a(Context var0, String var1, String var2) {
      ((ClipboardManager)var0.getSystemService("clipboard")).setPrimaryClip(ClipData.newPlainText(var1, var2));
   }

   public static byte[] a(String var0) {
      int var1 = var0.length();
      byte[] var2 = new byte[(var1 + 1) / 2];
      int var3 = 0;
      int var4 = 1;
      if (var1 % 2 == 1) {
         var2[0] = (byte)a(var0.charAt(0));
         var3 = 1;
      } else {
         var4 = 0;
      }

      while(var3 < var1) {
         int var5 = var3 + 1;
         var2[var4] = (byte)(a(var0.charAt(var3)) << 4 | a(var0.charAt(var5)));
         ++var4;
         var3 = var5 + 1;
      }

      return var2;
   }

   public static String b(int var0) {
      float var1 = (float)var0;
      String var2;
      if (var1 >= 1000.0F && var1 < 1000000.0F) {
         var1 /= 1000.0F;
         var2 = (new DecimalFormat("#.#K")).format((double)var1);
      } else if (var1 >= 1000000.0F && var1 < 1.0E9F) {
         var1 /= 1000000.0F;
         var2 = (new DecimalFormat("#.#M")).format((double)var1);
      } else if (var1 >= 1.0E9F && var1 < 1.0E12F) {
         var1 /= 1.0E9F;
         var2 = (new DecimalFormat("#.#G")).format((double)var1);
      } else {
         var2 = String.valueOf(var0);
      }

      return var2;
   }

   public static boolean b(String var0) {
      try {
         Double.parseDouble(var0);
         return true;
      } catch (NumberFormatException var1) {
         return false;
      }
   }

   public static String c(int var0) {
      return (new DecimalFormat("#,###")).format((long)var0);
   }

   public static ArrayList<String> c(String var0) {
      ArrayList var1 = new ArrayList();
      a var3 = new a(var0);

      while(!var3.a()) {
         String var2 = var3.b();
         if (var2.length() > 0) {
            var1.add(var2);
         }
      }

      return var1;
   }

   public static String d(String var0) {
      String var1 = "";

      for(int var2 = 0; var2 < var0.length(); ++var2) {
         char var3 = var0.charAt(var2);
         if (var3 == '\\') {
            StringBuilder var4 = new StringBuilder();
            var4.append(var1);
            ++var2;
            var4.append(var0.charAt(var2));
            var1 = var4.toString();
         } else {
            StringBuilder var5 = new StringBuilder();
            var5.append(var1);
            var5.append(var3);
            var1 = var5.toString();
         }
      }

      return var1;
   }

   public static class a {
      public String a;
      public int b;

      public a(String var1) {
         this.a = var1;
         this.b = 0;
      }

      public boolean a() {
         boolean var1;
         if (this.b >= this.a.length()) {
            var1 = true;
         } else {
            var1 = false;
         }

         return var1;
      }

      public String b() {
         this.c();
         boolean var1 = this.a();
         String var2 = "";
         if (var1) {
            return "";
         } else {
            boolean var3 = false;
            int var4 = this.b;

            while(this.b < this.a.length() && this.a.charAt(this.b) != ' ') {
               char var5 = this.a.charAt(this.b);
               if (var5 == '\\') {
                  StringBuilder var7 = new StringBuilder();
                  var7.append(var2);
                  var7.append(var5 + this.a.charAt(this.b + 1));
                  var2 = var7.toString();
                  this.b += 2;
               } else {
                  if (var5 == '%') {
                     if (this.b > var4) {
                        break;
                     }

                     var3 = true;
                  }

                  if (var3 && (var5 == '?' || var5 == '-')) {
                     break;
                  }

                  StringBuilder var6 = new StringBuilder();
                  var6.append(var2);
                  var6.append(var5);
                  var2 = var6.toString();
                  ++this.b;
               }
            }

            return var2;
         }
      }

      public void c() {
         while(this.b < this.a.length() && this.a.charAt(this.b) == ' ') {
            ++this.b;
         }

      }
   }
}
