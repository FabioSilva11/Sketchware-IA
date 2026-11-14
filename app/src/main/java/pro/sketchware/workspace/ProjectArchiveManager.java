package pro.sketchware.workspace;

import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProjectArchiveManager {
   public static void a(Context param0, String param1, String param2) throws IOException {
      byte[] var3 = new byte[1024];
      String var4 = param2;
      if (!var4.endsWith(File.separator)) {
         var4 = var4 + File.separator;
      }
      File var5 = new File(var4);
      if (!var5.exists()) {
         if (!var5.mkdirs()) {
            var5.mkdir();
         }
      }
      BufferedInputStream var6 = null;
      ZipInputStream var7 = null;
      try {
         var6 = new BufferedInputStream(param0.getAssets().open(param1));
         var7 = new ZipInputStream(var6);
         ZipEntry var8 = var7.getNextEntry();
         while (var8 != null) {
            File var9 = new File(var4 + var8.getName());
            if (var8.isDirectory()) {
               if (!var9.isDirectory()) {
                  var9.mkdirs();
               }
            } else {
               File var10 = var9.getParentFile();
               if (var10 != null && !var10.isDirectory()) {
                  var10.mkdirs();
               }
               FileOutputStream var11 = new FileOutputStream(var9, false);
               int var12 = var7.read(var3, 0, var3.length);
               while (var12 > 0) {
                  var11.write(var3, 0, var12);
                  var12 = var7.read(var3, 0, var3.length);
               }
               var7.closeEntry();
               var11.flush();
               var11.close();
            }
            var8 = var7.getNextEntry();
         }
      } catch (Exception var13) {
         Log.e("DEBUG", var13.getMessage(), var13);
         throw var13;
      } finally {
         try {
            if (var7 != null) {
               var7.close();
            }
         } catch (Exception var14) {
            var14.printStackTrace();
         }
         try {
            if (var6 != null) {
               var6.close();
            }
         } catch (Exception var15) {
            var15.printStackTrace();
         }
      }
   }

   public int a(String var1, File var2, ZipOutputStream var3, ArrayList var4) {
      File[] var5 = var2.listFiles();
      if (var5 == null) {
         return 0;
      } else {
         if (var5.length == 0) {
            String var13 = var2.getAbsolutePath();
            this.a(var1, var13.substring(var1.length(), var13.length()), var3);
         }

         int var6 = var5.length;
         int var7 = 0;

         int var8;
         int var9;
         for(var8 = 0; var7 < var6; var8 = var9) {
            var2 = var5[var7];
            if (var2.isDirectory()) {
               this.a(var1, var2, var3, var4);
            }

            var9 = var8;
            if (var2.isFile()) {
               String var10 = var2.getAbsolutePath();
               String var15 = var10.substring(var1.length(), var10.length());
               Iterator var11 = var4.iterator();

               boolean var12;
               while(true) {
                  if (var11.hasNext()) {
                     if (!var10.contains((String)var11.next())) {
                        continue;
                     }

                     var12 = true;
                     break;
                  }

                  var12 = false;
                  break;
               }

               var9 = var8;
               if (!var12) {
                  var9 = var8;
                  if (this.a(var1, var15, var3)) {
                     var9 = var8 + 1;
                  }
               }
            }

            ++var7;
         }

         return var8;
      }
   }

   public void a(InputStream param1, String param2) throws IOException {
      byte[] var3 = new byte[1024];
      String var4 = param2;
      if (!var4.endsWith(File.separator)) {
         var4 = var4 + File.separator;
      }
      File var5 = new File(var4);
      if (!var5.exists()) {
         var5.mkdirs();
      }
      BufferedInputStream var6 = null;
      ZipInputStream var7 = null;
      try {
         var6 = new BufferedInputStream(param1);
         var7 = new ZipInputStream(var6);
         ZipEntry var8 = var7.getNextEntry();
         while (var8 != null) {
            File var9 = new File(var4 + var8.getName());
            if (var8.isDirectory()) {
               if (!var9.isDirectory()) {
                  var9.mkdirs();
               }
            } else {
               File var10 = var9.getParentFile();
               if (var10 != null && !var10.isDirectory()) {
                  var10.mkdirs();
               }
               FileOutputStream var11 = new FileOutputStream(var9, false);
               int var12 = var7.read(var3, 0, var3.length);
               while (var12 > 0) {
                  var11.write(var3, 0, var12);
                  var12 = var7.read(var3, 0, var3.length);
               }
               var7.closeEntry();
               var11.flush();
               var11.close();
            }
            var8 = var7.getNextEntry();
         }
      } catch (Exception var13) {
         var13.printStackTrace();
      } finally {
         try {
            if (var7 != null) {
               var7.close();
            }
         } catch (Exception var14) {
            var14.printStackTrace();
         }
         try {
            if (var6 != null) {
               var6.close();
            }
         } catch (Exception var15) {
            var15.printStackTrace();
         }
      }
   }

   public void a(String var1, String var2) throws IOException {
      this.a((InputStream)(new FileInputStream(var1)), var2);
   }

   public void a(String param1, ArrayList param2, ArrayList param3) throws IOException {
      ZipOutputStream var4 = null;
      FileOutputStream var5 = null;
      try {
         var5 = new FileOutputStream(param1);
         var4 = new ZipOutputStream(var5);
         Iterator var6 = param2.iterator();
         while (var6.hasNext()) {
            String var7 = (String)var6.next();
            this.a(var7, new File(var7), var4, param3);
         }
         var4.close();
      } catch (Exception var7) {
         var7.printStackTrace();
         throw var7;
      } finally {
         try {
            if (var4 != null) {
               var4.close();
            }
         } catch (Exception var8) {
            var8.printStackTrace();
         }
         try {
            if (var5 != null) {
               var5.close();
            }
         } catch (Exception var9) {
            var9.printStackTrace();
         }
      }
   }

   public boolean a(String param1, String param2, ZipOutputStream param3) {
      File var4 = new File(param1 + param2);
      if (!var4.isFile()) {
         try {
            if (param3 != null) {
               param3.closeEntry();
            }
         } catch (Exception var5) {
            var5.printStackTrace();
         }
         return false;
      }
      FileInputStream var6 = null;
      BufferedInputStream var7 = null;
      try {
         var6 = new FileInputStream(var4);
         var7 = new BufferedInputStream(var6);
         param3.putNextEntry(new ZipEntry(param2));
         byte[] var8 = new byte[1024];
         int var9 = var7.read(var8);
         while (var9 > 0) {
            param3.write(var8, 0, var9);
            var9 = var7.read(var8);
         }
      } catch (Exception var10) {
         var10.printStackTrace();
      } finally {
         try {
            if (param3 != null) {
               param3.closeEntry();
            }
         } catch (Exception var11) {
            var11.printStackTrace();
         }
         try {
            if (var6 != null) {
               var6.close();
            }
         } catch (Exception var12) {
            var12.printStackTrace();
         }
         try {
            if (var7 != null) {
               var7.close();
            }
         } catch (Exception var13) {
            var13.printStackTrace();
         }
      }
      return true;
   }

   public byte[] a(String param1) throws IOException {
      ByteArrayOutputStream var2 = null;
      try {
         var2 = new ByteArrayOutputStream();
         ZipOutputStream var3 = new ZipOutputStream(var2);
         File var4 = new File(param1);
         ArrayList var5 = new ArrayList();
         if (this.a(param1, var4, var3, var5) > 0) {
            var3.close();
         }
         return var2.toByteArray();
      } finally {
         try {
            if (var2 != null) {
               var2.close();
            }
         } catch (Exception var6) {
         }
      }
   }
}
