package pro.sketchware.beans;

import android.util.Pair;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;

import pro.sketchware.editor.FileHistoryOrdinalMap;

public class ProjectDataJsonCodec {
   public final String parserTag = "dataParser";
   public String b;
   public String c;
   public a d;
   public Gson e = (new GsonBuilder()).excludeFieldsWithoutExposeAnnotation().create();

   public ProjectDataJsonCodec(String var1) throws Exception {
      try {
         this.f(var1);
      } catch (Exception ex) {
         throw ex;
      }
   }

   public static ArrayList a(Gson gson, String param1) {
      ArrayList list = new ArrayList();
      BufferedReader reader = null;

      try {
         reader = new BufferedReader(new StringReader(param1));
         String line;

         while((line = reader.readLine()) != null) {
            if (line.trim().length() <= 0 || line.trim().charAt(0) != '{') {
               continue;
            }

            list.add((BlockBean)gson.fromJson(line, BlockBean.class));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         }
      }

      return list;
   }

   public static ArrayList b(Gson gson, String param1) {
      ArrayList list = new ArrayList();
      BufferedReader reader = null;

      try {
         reader = new BufferedReader(new StringReader(param1));
         String line;

         while((line = reader.readLine()) != null) {
            if (line.trim().length() <= 0 || line.trim().charAt(0) != '{') {
               continue;
            }

            list.add((ViewBean)gson.fromJson(line, ViewBean.class));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         }
      }

      return list;
   }

   public a a() {
      return this.d;
   }

   public Object a(String var1) {
      switch (FileHistoryOrdinalMap.a[this.d.ordinal()]) {
         case 1:
            return b(this.e, var1);
         case 2:
            return this.d(var1);
         case 3:
            return this.h(var1);
         case 4:
            return this.g(var1);
         case 5:
            return this.b(var1);
         case 6:
            return this.c(var1);
         case 7:
            return this.e(var1);
         case 8:
            return a(this.e, var1);
         default:
            return null;
      }
   }

   public String b() {
      return this.b;
   }

   public ArrayList b(String param1) {
      ArrayList list = new ArrayList();
      BufferedReader reader = null;

      try {
         reader = new BufferedReader(new StringReader(param1));
         String line;

         while((line = reader.readLine()) != null) {
            if (line.trim().length() <= 0 || line.trim().charAt(0) != '{') {
               continue;
            }

            list.add((ComponentBean)this.e.fromJson(line, ComponentBean.class));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         }
      }

      return list;
   }

   public String c() {
      return this.c;
   }

   public ArrayList c(String param1) {
      ArrayList list = new ArrayList();
      BufferedReader reader = null;

      try {
         reader = new BufferedReader(new StringReader(param1));
         String line;

         while((line = reader.readLine()) != null) {
            if (line.trim().length() <= 0 || line.trim().charAt(0) != '{') {
               continue;
            }

            list.add((EventBean)this.e.fromJson(line, EventBean.class));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         }
      }

      return list;
   }

   public ViewBean d(String var1) {
      return var1.trim().length() > 0 && var1.trim().charAt(0) == '{' ? (ViewBean)this.e.fromJson(var1, ViewBean.class) : new ViewBean("_fab", 16);
   }

   public ArrayList e(String param1) {
      ArrayList list = new ArrayList();
      BufferedReader reader = null;

      try {
         reader = new BufferedReader(new StringReader(param1));
         String line;

         while((line = reader.readLine()) != null) {
            if (line.trim().length() <= 0 || !line.contains(":")) {
               continue;
            }

            String first = line.substring(0, line.indexOf(":"));
            String second = line.substring(line.indexOf(":") + 1);
            list.add(new Pair(first, second));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         }
      }

      return list;
   }

   public final void f(String var1) throws Exception {
      String var2 = var1.trim();
      if (var2.contains(".xml")) {
         int var3 = var2.indexOf(".xml") + 4;
         this.b = var2.substring(0, var3);
         if (var1.length() == var3) {
            this.d = ProjectDataJsonCodec.a.a;
         } else {
            var1 = var1.substring(var3);
            if (var1.charAt(0) != '_') {
               throw new Exception("invalid key : No separator");
            }

            if (!var1.substring(1).equals("fab")) {
               throw new Exception("invalid key : Unknown type string");
            }

            this.d = ProjectDataJsonCodec.a.b;
         }
      } else {
         if (!var2.contains(".java")) {
            throw new Exception("invalid key : No filename");
         }

         int var7 = var2.indexOf(".java") + 5;
         this.b = var2.substring(0, var7);
         if (var2.length() == var7) {
            throw new Exception("invalid key : No data type");
         }

         var1 = var2.substring(var7);
         if (var1.charAt(0) != '_') {
            throw new Exception("invalid key : No separator");
         }

         var1 = var1.substring(1);
         var7 = -1;
         switch (var1) {
            case "events":
               var7 = 3;
               break;
            case "components":
               var7 = 2;
               break;
            case "var":
               var7 = 0;
               break;
            case "func":
               var7 = 4;
               break;
            case "list":
               var7 = 1;
         }

         if (var7 != 0) {
            if (var7 != 1) {
               if (var7 != 2) {
                  if (var7 != 3) {
                     if (var7 != 4) {
                        this.d = ProjectDataJsonCodec.a.h;
                        this.c = var1;
                     } else {
                        this.d = ProjectDataJsonCodec.a.g;
                     }
                  } else {
                     this.d = ProjectDataJsonCodec.a.f;
                  }
               } else {
                  this.d = ProjectDataJsonCodec.a.e;
               }
            } else {
               this.d = ProjectDataJsonCodec.a.d;
            }
         } else {
            this.d = ProjectDataJsonCodec.a.c;
         }
      }

   }

   public ArrayList g(String param1) {
      ArrayList list = new ArrayList();
      BufferedReader reader = null;

      try {
         reader = new BufferedReader(new StringReader(param1));
         String line;

         while((line = reader.readLine()) != null) {
            if (line.trim().length() <= 0 || !line.contains(":")) {
               continue;
            }

            int ordinal = Integer.valueOf(line.substring(0, line.indexOf(":")));
            String name = line.substring(line.indexOf(":") + 1);
            list.add(new Pair(ordinal, name));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         }
      }

      return list;
   }

   public ArrayList h(String param1) {
      ArrayList list = new ArrayList();
      BufferedReader reader = null;

      try {
         reader = new BufferedReader(new StringReader(param1));
         String line;

         while((line = reader.readLine()) != null) {
            if (line.trim().length() <= 0 || !line.contains(":")) {
               continue;
            }

            int ordinal = Integer.valueOf(line.substring(0, line.indexOf(":")));
            String name = line.substring(line.indexOf(":") + 1);
            list.add(new Pair(ordinal, name));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         }
      }

      return list;
   }

   public static enum a {
      a,
      b,
      c,
      d,
      e,
      f,
      g,
      h;

      public static final a[] i = new a[]{a, b, c, d, e, f, g, h};
   }
}



