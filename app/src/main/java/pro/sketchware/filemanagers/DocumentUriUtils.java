package pro.sketchware.filemanagers;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore.Images.Media;
import android.text.TextUtils;
import java.net.URLDecoder;

public final class DocumentUriUtils {
   public static String a(Context var0, Uri var1) {
      String var5;

      label56: {
         if (DocumentsContract.isDocumentUri(var0, var1)) {
            if (b(var1)) {
               String[] var4 = DocumentsContract.getDocumentId(var1).split(":");
               if ("primary".equalsIgnoreCase(var4[0])) {
                  StringBuilder var7 = new StringBuilder();
                  var7.append(Environment.getExternalStorageDirectory());
                  var7.append("/");
                  var7.append(var4[1]);
                  var5 = var7.toString();
                  break label56;
               }
            } else {
               if (a(var1)) {
                  String var10 = DocumentsContract.getDocumentId(var1);
                  if (!TextUtils.isEmpty(var10) && var10.startsWith("raw:")) {
                     return var10.replaceFirst("raw:", "");
                  }

                  var5 = a(var0, ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(var10)), (String)null, (String[])null);

                  break label56;
               }

               if (c(var1)) {
                  String[] var2 = DocumentsContract.getDocumentId(var1).split(":");
                  String var8 = var2[0];
                  Uri var9;
                  if ("image".equals(var8)) {
                     var9 = Media.EXTERNAL_CONTENT_URI;
                  } else if ("video".equals(var8)) {
                     var9 = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                  } else if ("audio".equals(var8)) {
                     var9 = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                  } else {
                     var9 = null;
                  }

                  var5 = a(var0, var9, "_id=?", new String[]{var2[1]});
                  break label56;
               }
            }
         } else {
            if ("content".equalsIgnoreCase(var1.getScheme())) {
               var5 = a(var0, var1, (String)null, (String[])null);
               break label56;
            }

            if ("file".equalsIgnoreCase(var1.getScheme())) {
               var5 = var1.getPath();
               break label56;
            }
         }

         var5 = null;
      }

      if (var5 != null) {
         try {
            String var6 = URLDecoder.decode(var5, "UTF-8");
            return var6;
         } catch (Exception var3) {
         }
      }

      return null;
   }

   public static String a(Context param0, Uri param1, String param2, String[] param3) {
      Cursor var6 = null;

      try {
         var6 = param0.getContentResolver().query(param1, null, param2, param3, null);
         if (var6 != null && var6.moveToFirst()) {
            int var7 = var6.getColumnIndex("_data");
            if (var7 >= 0) {
               return var6.getString(var7);
            }
         }
      } catch (Exception var4) {
      } finally {
         if (var6 != null) {
            var6.close();
         }
      }

      return null;
   }

   public static boolean a(Uri var0) {
      return "com.android.providers.downloads.documents".equals(var0.getAuthority());
   }

   public static boolean b(Uri var0) {
      return "com.android.externalstorage.documents".equals(var0.getAuthority());
   }

   public static boolean c(Uri var0) {
      return "com.android.providers.media.documents".equals(var0.getAuthority());
   }
}
