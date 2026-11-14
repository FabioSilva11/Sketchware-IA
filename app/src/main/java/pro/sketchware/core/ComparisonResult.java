package pro.sketchware.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ComparisonResult {
   public String toString(ComparisonResult var1) {
      StringBuffer var2 = new StringBuffer();
      var2.append("[");

      for(Field var6 : var1.getClass().getFields()) {
         if (!Modifier.isStatic(var6.getModifiers()) && Modifier.isPublic(var6.getModifiers())) {
            try {
               var2.append(var6.getName());
               var2.append("=");
               var2.append(var6.get(var1));
               var2.append(",");
            } catch (Exception var7) {
               var7.printStackTrace();
            }
         }
      }

      var2.append("]");
      return var2.toString();
   }
}
