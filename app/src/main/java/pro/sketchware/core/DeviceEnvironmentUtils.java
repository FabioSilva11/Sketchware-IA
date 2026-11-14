package pro.sketchware.core;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.Build.VERSION;
import android.util.DisplayMetrics;
import com.google.android.gms.common.GoogleApiAvailability;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;

public class DeviceEnvironmentUtils {
   public static int a(Context var0) {
      return (int)ViewInflationUtils.a(var0, 48.0F);
   }

   public static String a() {
      String[] var2 = Build.SUPPORTED_ABIS;
      String var1;
      if (Build.VERSION.SDK_INT >= 21 && var2 != null && var2.length > 0) {
         var1 = var2[0];
      } else {
         var1 = Build.CPU_ABI;
      }

      return var1;
   }

   public static void a(Context var0, int var1) {
      String var2 = var0.getPackageManager().getLaunchIntentForPackage(var0.getPackageName()).getComponent().getClassName();
      Intent var3 = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
      var3.putExtra("badge_count", var1);
      var3.putExtra("badge_count_package_name", var0.getPackageName());
      var3.putExtra("badge_count_class_name", var2);
      var0.sendBroadcast(var3);
   }

   public static boolean a(Activity var0) {
      GoogleApiAvailability var1 = GoogleApiAvailability.getInstance();
      int var2 = var1.isGooglePlayServicesAvailable(var0);
      if (var2 != 0) {
         var1.isUserResolvableError(var2);
         return false;
      } else {
         return true;
      }
   }

   public static String b() {
      for(Field var3 : Build.VERSION_CODES.class.getFields()) {
         String var4 = var3.getName();

         int var6;
         int var7;
         try {
            Object var5 = new Object();
            var6 = var3.getInt(var5);
            var7 = VERSION.SDK_INT;
         } catch (Exception var8) {
            break;
         }

         if (var6 == var7) {
            return var4;
         }
      }

      return "";
   }

   public static String b(Context var0) {
      return "";
   }

   public static boolean b(Context var0, int var1) {
      boolean var2;
      if (((SensorManager)var0.getSystemService("sensor")).getDefaultSensor(var1) != null) {
         var2 = true;
      } else {
         var2 = false;
      }

      return var2;
   }

   public static float[] b(Activity var0) {
      DisplayMetrics var1 = new DisplayMetrics();
      var0.getWindowManager().getDefaultDisplay().getMetrics(var1);
      return new float[]{var1.xdpi, var1.ydpi};
   }

   public static long c() {
      try {
         StatFs var0 = new StatFs(Environment.getExternalStorageDirectory().getPath());
         long var1 = var0.getFreeBytes() / 1048576L;
         return var1;
      } catch (Exception var3) {
         return -1L;
      }
   }

   public static ArrayList c(Context var0) {
      ArrayList var1 = new ArrayList();
      Account[] var3 = AccountManager.get(var0).getAccountsByType("com.google");

      for(int var2 = 0; var2 < var3.length; ++var2) {
         if (var3[var2].type.equals("com.google")) {
            var1.add(var3[var2].name);
         }
      }

      return var1;
   }

   public static int[] c(Activity var0) {
      DisplayMetrics var1 = new DisplayMetrics();
      var0.getWindowManager().getDefaultDisplay().getMetrics(var1);
      return new int[]{var1.widthPixels, var1.heightPixels};
   }

   public static int d(Context var0) {
      int var1 = 0;

      int var3;
      try {
         String var2 = var0.getPackageName();
         var3 = var0.getPackageManager().getPackageInfo(var2, 0).versionCode;
      } catch (PackageManager.NameNotFoundException var4) {
         var4.printStackTrace();
         return var1;
      }

      var1 = var3;
      return var1;
   }

   public static String e(Context var0) {
      String var3;
      try {
         String var1 = var0.getPackageName();
         var3 = var0.getPackageManager().getPackageInfo(var1, 0).versionName;
      } catch (PackageManager.NameNotFoundException var2) {
         var2.printStackTrace();
         var3 = "";
      }

      return var3;
   }

   public static int f(Context var0) {
      int var1 = var0.getResources().getIdentifier("status_bar_height", "dimen", "android");
      if (var1 > 0) {
         var1 = var0.getResources().getDimensionPixelSize(var1);
      } else {
         var1 = 0;
      }

      return var1;
   }

   public static Locale g(Context var0) {
      Locale var1;
      if (VERSION.SDK_INT >= 24) {
         var1 = var0.getResources().getConfiguration().getLocales().get(0);
      } else {
         var1 = var0.getResources().getConfiguration().locale;
      }

      return var1;
   }

   public static boolean h(Context var0) {
      int[] var1 = new int[]{0, 1};

      int var2;
      ConnectivityManager var10;
      try {
         var10 = (ConnectivityManager)var0.getSystemService("connectivity");
         var2 = var1.length;
      } catch (Exception var9) {
         boolean var10001 = false;
         return false;
      }

      for(int var3 = 0; var3 < var2; ++var3) {
         int var4 = var1[var3];

         NetworkInfo var5;
         try {
            var5 = var10.getActiveNetworkInfo();
         } catch (Exception var8) {
            boolean var11 = false;
            break;
         }

         if (var5 != null) {
            int var6;
            try {
               var6 = var5.getType();
            } catch (Exception var7) {
               boolean var12 = false;
               break;
            }

            if (var6 == var4) {
               return true;
            }
         }
      }

      return false;
   }
}
