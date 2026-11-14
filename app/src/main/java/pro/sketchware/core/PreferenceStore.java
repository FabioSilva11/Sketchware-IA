package pro.sketchware.core;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;

public class PreferenceStore {
   public SharedPreferences a;
   public SharedPreferences.Editor b;

   public PreferenceStore(Context var1, String var2) {
      this.a = var1.getSharedPreferences(var2, 0);
      this.b = this.a.edit();
   }

   public int a(String var1, int var2) {
      return this.a.getInt(var1, var2);
   }

   public String a(String var1, String var2) {
      return this.a.getString(var1, var2);
   }

   public void a(String var1, Object var2) {
      this.a(var1, var2, true);
   }

   public void a(String var1, Object var2, boolean var3) {
      if (var2 instanceof String) {
         this.b.putString(var1, (String)var2);
      } else if (var2 instanceof Integer) {
         this.b.putInt(var1, (Integer)var2);
      } else if (var2 instanceof Long) {
         this.b.putLong(var1, (Long)var2);
      } else if (var2 instanceof Boolean) {
         this.b.putBoolean(var1, (Boolean)var2);
      }

      if (var3) {
         this.b.commit();
      }

   }

   public void a(String var1, HashMap var2) {
      this.a(var1, (Object)MapJsonCodec.a(var2));
   }

   public boolean a() {
      this.b.clear();
      return this.b.commit();
   }

   public boolean a(String var1) {
      this.b.remove(var1);
      return this.b.commit();
   }

   public boolean a(String var1, boolean var2) {
      return this.a.getBoolean(var1, var2);
   }

   public boolean b() {
      return this.b.commit();
   }

   public boolean b(String var1) {
      return this.a.contains(var1);
   }

   public HashMap c() {
      HashMap var1;
      try {
         var1 = (HashMap)this.a.getAll();
      } catch (Exception var2) {
         var1 = new HashMap();
      }

      return var1;
   }

   public boolean c(String var1) {
      return this.a.getBoolean(var1, false);
   }

   public int d(String var1) {
      return this.a(var1, 0);
   }

   public long e(String var1) {
      return this.a.getLong(var1, 0L);
   }

   public String f(String var1) {
      return this.a(var1, "");
   }

   public HashMap g(String var1) {
      var1 = this.f(var1);
      return var1.isEmpty() ? new HashMap() : MapJsonCodec.a(var1);
   }
}
