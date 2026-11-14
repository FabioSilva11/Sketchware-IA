package pro.sketchware.workspace;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import java.util.ArrayList;

public class ProjectDataProvider {
   public static int a(String var0) {
      byte var1;
      if (var0.hashCode() == 1424216003 && var0.equals("Basic List Item")) {
         var1 = 0;
      } else {
         var1 = -1;
      }

      return var1 != 0 ? -1 : 2131165293;
   }

   public static ViewBean a() {
      ViewBean var0 = new ViewBean("textview1", 4);
      var0.parent = "root";
      var0.index = 0;
      var0.preParentType = 0;
      var0.name = "textview1";
      var0.text.text = "TextView";
      LayoutBean var1 = var0.layout;
      var1.paddingTop = 8;
      var1.paddingBottom = 8;
      var1.paddingLeft = 8;
      var1.paddingRight = 8;
      return var0;
   }

   public static ArrayList b() {
      ArrayList var0 = new ArrayList();
      var0.add(i());
      return var0;
   }

   public static ArrayList b(String var0) {
      byte var1;
      if (var0.hashCode() == 1424216003 && var0.equals("Basic List Item")) {
         var1 = 0;
      } else {
         var1 = -1;
      }

      return var1 != 0 ? new ArrayList() : j();
   }

   public static int c(String var0) {
      byte var1;
      if (var0.hashCode() == 920477027 && var0.equals("Basic Drawer")) {
         var1 = 0;
      } else {
         var1 = -1;
      }

      return var1 != 0 ? -1 : 2131165293;
   }

   public static ArrayList c() {
      ArrayList var0 = new ArrayList();
      var0.add(g());
      return var0;
   }

   public static ArrayList d() {
      ArrayList var0 = new ArrayList();
      var0.add(k());
      var0.add(e());
      var0.add(m());
      return var0;
   }

   public static ArrayList d(String var0) {
      byte var1;
      if (var0.hashCode() == 920477027 && var0.equals("Basic Drawer")) {
         var1 = 0;
      } else {
         var1 = -1;
      }

      return var1 != 0 ? new ArrayList() : h();
   }

   public static int e(String var0) {
      int var2;
      label33: {
         var2 = var0.hashCode();
         if (var2 != -1014430975) {
            if (var2 != -464182078) {
               if (var2 == -230447966 && var0.equals("Text Activity")) {
                  var2 = 2;
                  break label33;
               }
            } else if (var0.equals("Empty Activity")) {
               var2 = 0;
               break label33;
            }
         } else if (var0.equals("Basic Activity")) {
            var2 = 1;
            break label33;
         }

         var2 = -1;
      }

      if (var2 != 0) {
         return var2 != 1 && var2 != 2 ? -1 : 2131165293;
      } else {
         return 2131165296;
      }
   }

   public static ProjectFileBean e() {
      return new ProjectFileBean(0, (String)null, "Basic Activity", 0, 0, true, false, false, false);
   }

   public static ArrayList f() {
      return new ArrayList();
   }

   public static ArrayList f(String var0) {
      int var2;
      label35: {
         var2 = var0.hashCode();
         if (var2 != -1014430975) {
            if (var2 != -464182078) {
               if (var2 == -230447966 && var0.equals("Text Activity")) {
                  var2 = 2;
                  break label35;
               }
            } else if (var0.equals("Empty Activity")) {
               var2 = 0;
               break label35;
            }
         } else if (var0.equals("Basic Activity")) {
            var2 = 1;
            break label35;
         }

         var2 = -1;
      }

      if (var2 != 0) {
         if (var2 != 1) {
            return var2 != 2 ? new ArrayList() : n();
         } else {
            return f();
         }
      } else {
         return l();
      }
   }

   public static ProjectFileBean g() {
      return new ProjectFileBean(2, (String)null, "Basic Drawer");
   }

   public static ArrayList h() {
      ArrayList var0 = new ArrayList();
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      return var0;
   }

   public static ProjectFileBean i() {
      return new ProjectFileBean(1, (String)null, "Basic List Item");
   }

   public static ArrayList j() {
      ArrayList var0 = new ArrayList();
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      return var0;
   }

   public static ProjectFileBean k() {
      return new ProjectFileBean(0, (String)null, "Empty Activity", 0, 0, false, true, false, false);
   }

   public static ArrayList l() {
      return new ArrayList();
   }

   public static ProjectFileBean m() {
      return new ProjectFileBean(0, (String)null, "Text Activity", 0, 0, true, false, false, false);
   }

   public static ArrayList n() {
      ArrayList var0 = new ArrayList();
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      var0.add(a());
      return var0;
   }
}
