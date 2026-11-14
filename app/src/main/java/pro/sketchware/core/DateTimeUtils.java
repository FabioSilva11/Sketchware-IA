package pro.sketchware.core;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeUtils {
   public long a() {
      return (new Date()).getTime();
   }

   public String a(long var1, String var3) {
      return (new SimpleDateFormat(var3, Locale.ENGLISH)).format(new Date(var1));
   }

   public String a(String var1) {
      SimpleDateFormat var4 = new SimpleDateFormat(var1);
      String var5;
      try {
         Date var2 = new Date();
         var5 = var4.format(var2);
      } catch (Exception var3) {
         var5 = null;
      }

      return var5;
   }

   public String a(String var1, String var2) {
      SimpleDateFormat var3 = new SimpleDateFormat(var2);

      try {
         long var4 = var3.parse(var1).getTime();
         long var6 = (long)TimeZone.getDefault().getOffset(var4);
         Date var9 = new Date();
         var9.setTime(var4 + var6);
         var2 = var3.format(var9);
      } catch (Exception var8) {
         return var1;
      }

      var1 = var2;
      return var1;
   }

   public String a(String var1, String var2, String var3) {
      SimpleDateFormat var10 = new SimpleDateFormat(var2);
      String var12;
      try {
         long var4 = var10.parse(var1).getTime();
         long var6 = (long)TimeZone.getDefault().getOffset(var4);
         Date var8 = new Date();
         var8.setTime(var4 + var6);
         var10 = new SimpleDateFormat(var3);
         var12 = var10.format(var8);
      } catch (Exception var9) {
         return var1;
      }

      var1 = var12;
      return var1;
   }

   public long b(String var1, String var2) {
      SimpleDateFormat var6 = new SimpleDateFormat(var2, Locale.ENGLISH);

      try {
         long var3 = var6.parse(var1).getTime();
         return var3;
      } catch (ParseException var5) {
         return 0L;
      }
   }

   public String b(String var1) {
      SimpleDateFormat var2 = new SimpleDateFormat(var1);
      var2.setTimeZone(TimeZone.getTimeZone("GMT"));

      try {
         Date var5 = new Date();
         var1 = var2.format(var5);
      } catch (Exception var3) {
         var1 = null;
      }

      return var1;
   }
}
