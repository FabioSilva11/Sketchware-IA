package pro.sketchware.workspace;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import pro.sketchware.filemanagers.FileCryptoUtils;

public class ProjectLibraryManager {
   public String a;
   public FileCryptoUtils b;
   public ProjectLibraryBean c;
   public ProjectLibraryBean d;
   public ProjectLibraryBean e;
   public ProjectLibraryBean f;
   public Gson g;

   public ProjectLibraryManager(String var1) {
      this.a = var1;
      this.b = new FileCryptoUtils();
      this.g = (new GsonBuilder()).excludeFieldsWithoutExposeAnnotation().create();
      this.f();
   }

   public void a() {
      String var1 = WorkspacePathManager.a(this.a);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("library");
      var1 = var2.toString();
      this.b.c(var1);
   }

   public void a(ProjectLibraryBean var1) {
      this.e = var1;
   }

   public void a(BufferedReader reader) throws Exception {
      StringBuffer buffer = new StringBuffer();
      String name = "";

      while(true) {
         String line = reader.readLine();
         if (line == null) {
            if (name.length() > 0) {
               this.a(name, buffer.toString());
            }
            break;
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

      if (this.c == null) {
         this.c = new ProjectLibraryBean(0);
      }

      if (this.d == null) {
         this.d = new ProjectLibraryBean(1);
      }

      if (this.e == null) {
         this.e = new ProjectLibraryBean(2);
      }

      if (this.f == null) {
         this.f = new ProjectLibraryBean(3);
      }
   }

   public final void a(String var1) {
      StringBuffer var2 = new StringBuffer();
      this.a(var2);

      try {
         byte[] var4 = this.b.d(var2.toString());
         this.b.a(var1, var4);
      } catch (Exception var3) {
         var3.printStackTrace();
      }

   }

   public void a(String name, String json) {
      if (json.length() > 0) {
         BufferedReader reader = null;

         try {
            reader = new BufferedReader(new StringReader(json));
            String line = reader.readLine();
            if (line.charAt(0) == '{') {
               ProjectLibraryBean bean = (ProjectLibraryBean)this.g.fromJson(line, ProjectLibraryBean.class);
               if (name.equals("firebaseDB")) {
                  this.c = bean;
               } else if (name.equals("compat")) {
                  this.d = bean;
               } else if (name.equals("admob")) {
                  this.e = bean;
               } else if (name.equals("googleMap")) {
                  this.f = bean;
               }

               reader.close();
            } else {
               throw new Exception("invalid view data string");
            }

         } catch (Exception e) {
            e.printStackTrace();

            if (reader != null) {
               try {
                  reader.close();
               } catch (Exception e2) {
                  e2.printStackTrace();
               }

            }
         }
      }
   }

   public final void a(StringBuffer var1) {
      if (this.c != null) {
         var1.append("@");
         var1.append("firebaseDB");
         var1.append("\n");
         var1.append(this.g.toJson(this.c, ProjectLibraryBean.class));
         var1.append("\n");
      }

      if (this.d != null) {
         var1.append("@");
         var1.append("compat");
         var1.append("\n");
         var1.append(this.g.toJson(this.d, ProjectLibraryBean.class));
         var1.append("\n");
      }

      if (this.e != null) {
         var1.append("@");
         var1.append("admob");
         var1.append("\n");
         var1.append(this.g.toJson(this.e, ProjectLibraryBean.class));
         var1.append("\n");
      }

      if (this.f != null) {
         var1.append("@");
         var1.append("googleMap");
         var1.append("\n");
         var1.append(this.g.toJson(this.f, ProjectLibraryBean.class));
         var1.append("\n");
      }

   }

   public ProjectLibraryBean b() {
      return this.e;
   }

   public void b(ProjectLibraryBean var1) {
      this.d = var1;
   }

   public ProjectLibraryBean c() {
      return this.d;
   }

   public void c(ProjectLibraryBean var1) {
      this.c = var1;
   }

   public ProjectLibraryBean d() {
      return this.c;
   }

   public void d(ProjectLibraryBean var1) {
      this.f = var1;
   }

   public ProjectLibraryBean e() {
      return this.f;
   }

   public final void f() {
      this.c = new ProjectLibraryBean(0);
      this.d = new ProjectLibraryBean(1);
      this.e = new ProjectLibraryBean(2);
      this.f = new ProjectLibraryBean(3);
   }

   public boolean g() {
      String var1 = WorkspacePathManager.a(this.a);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("library");
      String var3 = var2.toString();
      return this.b.e(var3);
   }

   public void h() {
      this.f();
      String path = WorkspacePathManager.a(this.a);
      StringBuilder sb = new StringBuilder();
      sb.append(path);
      sb.append(File.separator);
      sb.append("library");
      path = sb.toString();
      BufferedReader reader = null;

      try {
         byte[] data = this.b.h(path);
         String contents = this.b.a(data);
         reader = new BufferedReader(new StringReader(contents));
         this.a(reader);
         reader.close();
      } catch (Exception e) {
         e.printStackTrace();

         if (reader != null) {
            try {
               reader.close();
            } catch (IOException e2) {
               e2.printStackTrace();
            }

         }
      }

   }

   public void i() {
      this.f();
      String path = WorkspacePathManager.b(this.a);
      StringBuilder sb = new StringBuilder();
      sb.append(path);
      sb.append(File.separator);
      sb.append("library");
      path = sb.toString();
      if (!this.b.e(path)) {
         return;
      }

      BufferedReader reader = null;

      try {
         byte[] data = this.b.h(path);
         String contents = this.b.a(data);
         reader = new BufferedReader(new StringReader(contents));
         this.a(reader);
         reader.close();
      } catch (Exception e) {
         e.printStackTrace();

         if (reader != null) {
            try {
               reader.close();
            } catch (IOException e2) {
               e2.printStackTrace();
            }

         }
      }

   }

   public void j() {
      this.a = "";
      this.f();
   }

   public void k() {
      String var1 = WorkspacePathManager.a(this.a);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("library");
      this.a(var2.toString());
   }

   public void l() {
      String var1 = WorkspacePathManager.b(this.a);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("library");
      this.a(var2.toString());
      this.a();
   }
}

