package pro.sketchware.filemanagers;

import com.besome.sketch.beans.ProjectResourceBean;
import com.bumptech.glide.signature.StringSignature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.io.StringReader;import java.util.Iterator;

import pro.sketchware.workspace.WorkspacePathManager;

public class ProjectResourceManager {
   public static StringSignature a;
   public ArrayList<ProjectResourceBean> b;
   public ArrayList<ProjectResourceBean> c;
   public ArrayList<ProjectResourceBean> d;
   public String e;
   public String f;
   public String g;
   public FileCryptoUtils h;
   public String i;
   public Gson j;

   public ProjectResourceManager(String var1) {
      StringBuilder var2 = new StringBuilder();
      var2.append(WorkspacePathManager.g());
      var2.append(File.separator);
      var2.append(var1);
      String var5 = var2.toString();
      StringBuilder var3 = new StringBuilder();
      var3.append(WorkspacePathManager.t());
      var3.append(File.separator);
      var3.append(var1);
      String var4 = var3.toString();
      var3 = new StringBuilder();
      var3.append(WorkspacePathManager.d());
      var3.append(File.separator);
      var3.append(var1);
      this.e = "";
      this.f = "";
      this.g = "";
      this.e = var5;
      this.f = var4;
      this.g = var3.toString();
      this.i = var1;
      z();
      this.h = new FileCryptoUtils(false);
      this.b = new ArrayList<ProjectResourceBean>();
      this.c = new ArrayList<ProjectResourceBean>();
      this.d = new ArrayList<ProjectResourceBean>();
      this.j = (new GsonBuilder()).excludeFieldsWithoutExposeAnnotation().create();
   }

   public ProjectResourceManager(String var1, String var2, String var3, String var4) {
      this.e = "";
      this.f = "";
      this.g = "";
      this.e = var2;
      this.f = var3;
      this.g = var4;
      this.i = var1;
      z();
      this.h = new FileCryptoUtils(false);
      this.b = new ArrayList();
      this.c = new ArrayList();
      this.d = new ArrayList();
      this.j = (new GsonBuilder()).excludeFieldsWithoutExposeAnnotation().create();
   }

   public static StringSignature n() {
      if (a == null) {
         z();
      }

      return a;
   }

   public static void z() {
      a = new StringSignature(String.valueOf(System.currentTimeMillis()));
   }

   public void a() {
      this.c();
      this.d();
      this.b();
   }

   public void a(BufferedReader reader) {
      StringBuffer buffer = new StringBuffer();
      String name = "";

      while(true) {
         String line;
         try {
            line = reader.readLine();
         } catch (IOException e) {
            line = null;
         }
         if (line == null) {
            if (name.length() > 0) {
               this.a(name, buffer.toString());
            }

            return;
         }

         if (line.length() > 0) {
            if (line.charAt(0) == '@') {
               StringBuffer next = buffer;
               if (name.length() > 0) {
                  this.a(name, buffer.toString());
                  next = new StringBuffer();
               }

               name = line.substring(1);
               buffer = next;
            } else {
               buffer.append(line);
               buffer.append("\n");
            }
         }
      }
   }

   public void a(String var1) {
      ArrayList var2 = this.d;
      if (var2 != null && var2.size() > 0) {
         File var7 = new File(var1);
         if (!var7.exists()) {
            var7.mkdirs();
         }

         for(ProjectResourceBean var3 : this.d) {
            StringBuilder var4 = new StringBuilder();
            var4.append(this.g);
            var4.append(File.separator);
            var4.append(var3.resFullName.toLowerCase());
            String var10 = var4.toString();
            StringBuilder var5 = new StringBuilder();
            var5.append(var1);
            var5.append(File.separator);
            var5.append(var3.resFullName.toLowerCase());
            String var9 = var5.toString();

            try {
               this.h.a(var10, var9);
            } catch (Exception var6) {
               var6.printStackTrace();
            }
         }
      }

   }

   public void a(String param1, String param2) {
      if (param2.trim().length() == 0) {
         return;
      }
      BufferedReader var3 = null;
      try {
         var3 = new BufferedReader(new StringReader(param2));
         String var4 = var3.readLine();
         while (var4 != null) {
            if (var4.trim().charAt(0) != '{') {
               var4 = var3.readLine();
               continue;
            }
            ProjectResourceBean var5 = (ProjectResourceBean)this.j.fromJson(var4, ProjectResourceBean.class);
            if (param1.equals("images")) {
               this.b.add(var5);
            } else if (param1.equals("sounds")) {
               this.c.add(var5);
            } else if (param1.equals("fonts")) {
               this.d.add(var5);
            }
            var4 = var3.readLine();
         }
         var3.close();
      } catch (Exception var6) {
         var6.printStackTrace();
         if (var3 != null) {
            try {
               var3.close();
            } catch (Exception var7) {
            }
         }
      }
   }

   public final void a(StringBuffer var1) {
      var1.append("@");
      var1.append("images");
      var1.append("\n");

      for(ProjectResourceBean var3 : this.b) {
         var1.append(this.j.toJson(var3, ProjectResourceBean.class));
         var1.append("\n");
      }

      var1.append("@");
      var1.append("sounds");
      var1.append("\n");

      for(ProjectResourceBean var4 : this.c) {
         var1.append(this.j.toJson(var4, ProjectResourceBean.class));
         var1.append("\n");
      }

      var1.append("@");
      var1.append("fonts");
      var1.append("\n");

      for(ProjectResourceBean var5 : this.d) {
         var1.append(this.j.toJson(var5, ProjectResourceBean.class));
         var1.append("\n");
      }

   }

   public void a(ArrayList var1) {
      this.d = var1;
   }

   public void b() {
      File[] var1 = (new File(this.g)).listFiles();
      if (var1 != null && var1.length > 0) {
         for(File var4 : var1) {
            var4.isDirectory();
            if (var4.isFile()) {
               Iterator var5 = this.d.iterator();

               boolean var6;
               while(true) {
                  if (var5.hasNext()) {
                     if (!((ProjectResourceBean)var5.next()).resFullName.equals(var4.getName())) {
                        continue;
                     }

                     var6 = true;
                     break;
                  }

                  var6 = false;
                  break;
               }

               if (!var6) {
                  var4.delete();
               }
            }
         }
      }

   }

   public void b(String var1) {
      ArrayList var2 = this.b;
      if (var2 != null && var2.size() > 0) {
         File var7 = new File(var1);
         if (!var7.exists()) {
            var7.mkdirs();
         }

         for(ProjectResourceBean var3 : this.b) {
            StringBuilder var4 = new StringBuilder();
            var4.append(this.e);
            var4.append(File.separator);
            var4.append(var3.resFullName.toLowerCase());
            String var10 = var4.toString();
            StringBuilder var5 = new StringBuilder();
            var5.append(var1);
            var5.append(File.separator);
            var5.append(var3.resFullName.toLowerCase());
            String var9 = var5.toString();

            try {
               this.h.a(var10, var9);
            } catch (Exception var6) {
               var6.printStackTrace();
            }
         }
      }

   }

   public void b(ArrayList var1) {
      this.b = var1;
   }

   public void c() {
      File[] var1 = (new File(this.e)).listFiles();
      if (var1 != null && var1.length > 0) {
         for(File var4 : var1) {
            var4.isDirectory();
            if (var4.isFile()) {
               Iterator var5 = this.b.iterator();

               boolean var6;
               while(true) {
                  if (var5.hasNext()) {
                     if (!((ProjectResourceBean)var5.next()).resFullName.equals(var4.getName())) {
                        continue;
                     }

                     var6 = true;
                     break;
                  }

                  var6 = false;
                  break;
               }

               if (!var6) {
                  var4.delete();
               }
            }
         }
      }

   }

   public void c(String var1) {
      ArrayList var2 = this.c;
      if (var2 != null && var2.size() > 0) {
         File var7 = new File(var1);
         if (!var7.exists()) {
            var7.mkdirs();
         }

         for(ProjectResourceBean var3 : this.c) {
            StringBuilder var4 = new StringBuilder();
            var4.append(this.f);
            var4.append(File.separator);
            var4.append(var3.resFullName.toLowerCase());
            String var10 = var4.toString();
            StringBuilder var5 = new StringBuilder();
            var5.append(var1);
            var5.append(File.separator);
            var5.append(var3.resFullName.toLowerCase());
            String var9 = var5.toString();

            try {
               this.h.a(var10, var9);
            } catch (Exception var6) {
               var6.printStackTrace();
            }
         }
      }

   }

   public void c(ArrayList var1) {
      this.c = var1;
   }

   public String d(String var1) {
      ArrayList var2 = this.d;
      if (var2 != null && var2.size() > 0) {
         for(ProjectResourceBean var5 : this.d) {
            if (var5.resName.equals(var1)) {
               StringBuilder var4 = new StringBuilder();
               var4.append(this.g);
               var4.append(File.separator);
               var4.append(var5.resFullName);
               return var4.toString();
            }
         }
      }

      return "";
   }

   public void d() {
      File[] var1 = (new File(this.f)).listFiles();
      if (var1 != null && var1.length > 0) {
         for(File var4 : var1) {
            var4.isDirectory();
            if (var4.isFile()) {
               Iterator var5 = this.c.iterator();

               boolean var6;
               while(true) {
                  if (var5.hasNext()) {
                     if (!((ProjectResourceBean)var5.next()).resFullName.equals(var4.getName())) {
                        continue;
                     }

                     var6 = true;
                     break;
                  }

                  var6 = false;
                  break;
               }

               if (!var6) {
                  var4.delete();
               }
            }
         }
      }

   }

   public ProjectResourceBean e(String var1) {
      for(ProjectResourceBean var3 : this.d) {
         if (var3.resName.equals(var1)) {
            return var3;
         }
      }

      return null;
   }

   public void e() {
      String var1 = WorkspacePathManager.u();

      try {
         this.h.b(var1);
         FileCryptoUtils var2 = this.h;
         File var3 = new File(this.g);
         File var4 = new File(var1);
         var2.a(var3, var4);
      } catch (Exception var5) {
         var5.printStackTrace();
      }

   }

   public String f(String var1) {
      ArrayList var2 = this.b;
      if (var2 != null && var2.size() > 0) {
         for(ProjectResourceBean var5 : this.b) {
            if (var5.resName.equals(var1)) {
               StringBuilder var4 = new StringBuilder();
               var4.append(this.e);
               var4.append(File.separator);
               var4.append(var5.resFullName);
               return var4.toString();
            }
         }
      }

      return "";
   }

   public void f() {
      String var1 = WorkspacePathManager.v();

      try {
         this.h.b(var1);
         FileCryptoUtils var2 = this.h;
         File var3 = new File(this.e);
         File var4 = new File(var1);
         var2.a(var3, var4);
      } catch (Exception var5) {
         var5.printStackTrace();
      }

   }

   public ProjectResourceBean g(String var1) {
      for(ProjectResourceBean var3 : this.b) {
         if (var3.resName.equals(var1)) {
            return var3;
         }
      }

      return null;
   }

   public void g() {
      String var1 = WorkspacePathManager.w();

      try {
         this.h.b(var1);
         FileCryptoUtils var2 = this.h;
         File var3 = new File(this.f);
         File var4 = new File(var1);
         var2.a(var3, var4);
      } catch (Exception var5) {
         var5.printStackTrace();
      }

   }

   public int h(String var1) {
      ArrayList var2 = this.b;
      if (var2 != null && var2.size() > 0) {
         for(ProjectResourceBean var3 : this.b) {
            if (var3.resName.equals(var1)) {
               return var3.resType;
            }
         }
      }

      return -1;
   }

   public void h() {
      String var1 = WorkspacePathManager.v();
      String var2 = WorkspacePathManager.w();
      String var3 = WorkspacePathManager.u();

      try {
         this.h.b(var1);
         this.h.b(var2);
         this.h.b(var3);
      } catch (Exception var4) {
         var4.printStackTrace();
      }

   }

   public String i(String var1) {
      ArrayList var2 = this.c;
      if (var2 != null && var2.size() > 0) {
         for(ProjectResourceBean var5 : this.c) {
            if (var5.resName.equals(var1)) {
               StringBuilder var4 = new StringBuilder();
               var4.append(this.f);
               var4.append(File.separator);
               var4.append(var5.resFullName);
               return var4.toString();
            }
         }
      }

      return "";
   }

   public void i() {
      String var1 = WorkspacePathManager.a(this.i);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("resource");
      var1 = var2.toString();
      this.h.c(var1);
   }

   public ProjectResourceBean j(String var1) {
      for(ProjectResourceBean var3 : this.c) {
         if (var3.resName.equals(var1)) {
            return var3;
         }
      }

      return null;
   }

   public String j() {
      return this.g;
   }

   public ArrayList<String> k() {
      ArrayList<String> var1 = new ArrayList<String>();
      Iterator var2 = this.d.iterator();

      while(var2.hasNext()) {
         var1.add(((ProjectResourceBean)var2.next()).resName);
      }

      return var1;
   }

   public boolean k(String var1) {
      Iterator var2 = this.d.iterator();

      while(var2.hasNext()) {
         if (((ProjectResourceBean)var2.next()).resName.equals(var1)) {
            return true;
         }
      }

      return false;
   }

   public String l() {
      return this.e;
   }

   public boolean l(String var1) {
      Iterator var2 = this.b.iterator();

      while(var2.hasNext()) {
         if (((ProjectResourceBean)var2.next()).resName.equals(var1)) {
            return true;
         }
      }

      return false;
   }

   public ArrayList<String> m() {
      ArrayList<String> var1 = new ArrayList<String>();
      Iterator var2 = this.b.iterator();

      while(var2.hasNext()) {
         var1.add(((ProjectResourceBean)var2.next()).resName);
      }

      return var1;
   }

   public boolean m(String var1) {
      Iterator var2 = this.c.iterator();

      while(var2.hasNext()) {
         if (((ProjectResourceBean)var2.next()).resName.equals(var1)) {
            return true;
         }
      }

      return false;
   }

   public String o() {
      return this.f;
   }

   public ArrayList<String> p() {
      ArrayList<String> var1 = new ArrayList<String>();
      Iterator var2 = this.c.iterator();

      while(var2.hasNext()) {
         var1.add(((ProjectResourceBean)var2.next()).resName);
      }

      return var1;
   }

   public boolean q() {
      String var1 = WorkspacePathManager.a(this.i);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("resource");
      var1 = var2.toString();
      return this.h.e(var1);
   }

   public void r() {
      this.b = new ArrayList();
      this.c = new ArrayList();
      this.d = new ArrayList();
      String var1 = WorkspacePathManager.a(this.i) + File.separator + "resource";
      BufferedReader var2 = null;
      try {
         byte[] var3 = this.h.h(var1);
         String var4 = this.h.a(var3);
         var2 = new BufferedReader(new StringReader(var4));
         this.a(var2);
         var2.close();
      } catch (Exception var5) {
         var5.printStackTrace();
         if (var2 != null) {
            try {
               var2.close();
            } catch (IOException var6) {
               var6.printStackTrace();
            }
         }
      }
   }

   public void s() {
      this.b = new ArrayList();
      this.c = new ArrayList();
      this.d = new ArrayList();
      String var1 = WorkspacePathManager.b(this.i) + File.separator + "resource";
      if (!this.h.e(var1)) {
         return;
      }
      BufferedReader var2 = null;
      try {
         byte[] var3 = this.h.h(var1);
         String var4 = this.h.a(var3);
         var2 = new BufferedReader(new StringReader(var4));
         this.a(var2);
         var2.close();
      } catch (Exception var5) {
         var5.printStackTrace();
         if (var2 != null) {
            try {
               var2.close();
            } catch (IOException var6) {
               var6.printStackTrace();
            }
         }
      }
   }

   public void t() {
      this.i = "";
      this.e = "";
      this.f = "";
      this.g = "";
      this.b = new ArrayList();
      this.c = new ArrayList();
      this.d = new ArrayList();
   }

   public void u() {
      String var1 = WorkspacePathManager.u();

      try {
         FileCryptoUtils var2 = this.h;
         File var3 = new File(this.g);
         var2.a(var3);
         var2 = this.h;
         var3 = new File(var1);
         File var5 = new File(this.g);
         var2.a(var3, var5);
      } catch (Exception var4) {
         var4.printStackTrace();
      }

   }

   public void v() {
      String var1 = WorkspacePathManager.v();

      try {
         FileCryptoUtils var2 = this.h;
         File var3 = new File(this.e);
         var2.a(var3);
         var2 = this.h;
         var3 = new File(var1);
         File var5 = new File(this.e);
         var2.a(var3, var5);
      } catch (Exception var4) {
         var4.printStackTrace();
      }

   }

   public void w() {
      String var1 = WorkspacePathManager.w();

      try {
         FileCryptoUtils var2 = this.h;
         File var3 = new File(this.f);
         var2.a(var3);
         var2 = this.h;
         var3 = new File(var1);
         File var5 = new File(this.f);
         var2.a(var3, var5);
      } catch (Exception var4) {
         var4.printStackTrace();
      }

   }

   public void x() {
      String var1 = WorkspacePathManager.b(this.i);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("resource");
      String var6 = var2.toString();
      StringBuffer var4 = new StringBuffer();
      this.a(var4);

      try {
         byte[] var5 = this.h.d(var4.toString());
         this.h.a(var6, var5);
      } catch (Exception var3) {
         var3.printStackTrace();
      }

      this.i();
   }

   public void y() {
      String var1 = WorkspacePathManager.a(this.i);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("resource");
      var1 = var2.toString();
      StringBuffer var5 = new StringBuffer();
      this.a(var5);

      try {
         byte[] var6 = this.h.d(var5.toString());
         this.h.a(var1, var6);
      } catch (Exception var3) {
         var3.printStackTrace();
      }

   }
}
