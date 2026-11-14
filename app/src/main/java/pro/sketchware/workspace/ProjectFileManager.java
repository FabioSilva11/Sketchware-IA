package pro.sketchware.workspace;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;

import pro.sketchware.filemanagers.FileCryptoUtils;

public class ProjectFileManager {
   public ArrayList<String> a;
   public ArrayList<String> b;
   public ArrayList<ProjectFileBean> c;
   public ArrayList<ProjectFileBean> d;
   public String e;
   public FileCryptoUtils f;
   public Gson g;

   public ProjectFileManager(String var1) {
      this.e = var1;
      this.f = new FileCryptoUtils();
      this.a = new ArrayList();
      this.b = new ArrayList();
      this.g = (new GsonBuilder()).excludeFieldsWithoutExposeAnnotation().create();
      this.f();
      this.j();
   }

   public ProjectFileBean a(String var1) {
      for(ProjectFileBean var3 : this.c) {
         if (var3.getJavaName().equals(var1)) {
            return var3;
         }
      }

      return null;
   }

   public void a() {
      String var1 = WorkspacePathManager.a(this.e);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("file");
      var1 = var2.toString();
      this.f.c(var1);
   }

   public void a(int var1, String var2) {
      ProjectFileBean var3 = new ProjectFileBean(var1, var2);
      if (var1 == 0) {
         this.c.add(var3);
      } else {
         this.d.add(var3);
      }

   }

   public void a(ProjectLibraryManager var1) {
      ProjectLibraryBean var3 = var1.c();
      if (var3 == null || !var3.useYn.equals("Y")) {
         for(ProjectFileBean var4 : this.c) {
            if (var4.hasActivityOption(4)) {
               this.b(2, var4.getDrawerName());
               var4.setActivityOptions(1);
            }

            if (var4.hasActivityOption(8)) {
               var4.setActivityOptions(1);
            }
         }

         this.j();
      }
   }

   public void a(ProjectFileBean var1) {
      if (var1.fileType == 0) {
         this.c.add(var1);
      } else {
         this.d.add(var1);
      }

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

            this.j();
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

   public void a(String var1, String var2) {
      if (var1.equals("activity")) {
         var1 = var2;
         if (var2.length() <= 0) {
            return;
         }

         while(true) {
            int var3 = var1.indexOf("\n");
            if (var3 < 0 || var1.charAt(0) != '{') {
               break;
            }

            var2 = var1.substring(0, var3);
            ProjectFileBean var4 = (ProjectFileBean)this.g.fromJson(var2, ProjectFileBean.class);
            var4.setOptionsByTheme();
            if (!var4.fileName.equals("main")) {
               this.c.add(var4);
            } else {
                ProjectFileBean existingMain = null;
                
                for(ProjectFileBean mainBean : this.c) {
                    
                    if (mainBean.fileName.equals("main")) {
                        existingMain = mainBean;
                        break;
                        }
                    }

                if (existingMain != null) {
                    existingMain.copy(var4);
                } else {
                    this.c.add(0, var4);
                }
            }

            if (var3 >= var1.length() - 1) {
               break;
            }

            var1 = var1.substring(var3 + 1);
         }
      } else if (var1.equals("customview")) {
         if (var2.length() <= 0) {
            return;
         }

         this.d = new ArrayList();

         while(true) {
            int var12 = var2.indexOf("\n");
            if (var12 < 0 || var2.charAt(0) != '{') {
               break;
            }

            var1 = var2.substring(0, var12);
            ProjectFileBean var9 = (ProjectFileBean)this.g.fromJson(var1, ProjectFileBean.class);
            var9.setOptionsByTheme();
            this.d.add(var9);
            if (var12 >= var2.length() - 1) {
               break;
            }

            var2 = var2.substring(var12 + 1);
         }
      }

   }

   public final void a(StringBuffer var1) {
      var1.append("@");
      var1.append("activity");
      var1.append("\n");
      ArrayList<ProjectFileBean> var2 = this.c;
      if (var2 != null) {
         for(ProjectFileBean var4 : var2) {
            var1.append(this.g.toJson(var4, ProjectFileBean.class));
            var1.append("\n");
         }
      }

      var1.append("@");
      var1.append("customview");
      var1.append("\n");
      var2 = this.d;
      if (var2 != null) {
         for(ProjectFileBean var6 : var2) {
            var1.append(this.g.toJson(var6, ProjectFileBean.class));
            var1.append("\n");
         }
      }

   }

   public void a(ArrayList<ProjectFileBean> var1) {
      this.c = var1;
   }

   public ProjectFileBean b(String var1) {
      for(ProjectFileBean var3 : this.c) {
         if (var3.getXmlName().equals(var1)) {
            return var3;
         }
      }

      ArrayList<ProjectFileBean> var4 = this.d;
      if (var4 != null) {
         for(ProjectFileBean var5 : var4) {
            if (var5.getXmlName().equals(var1)) {
               return var5;
            }
         }
      }

      return null;
   }

   public ArrayList<ProjectFileBean> b() {
      if (this.c == null) {
         this.c = new ArrayList();
      }

      return this.c;
   }

   public void b(int var1, String var2) {
      if (var1 == 0) {
         for(ProjectFileBean var4 : this.c) {
            if (var4.fileType == var1 && var4.fileName.equals(var2)) {
               this.c.remove(var4);
               break;
            }
         }
      } else {
         for(ProjectFileBean var6 : this.d) {
            if (var6.fileType == var1 && var6.fileName.equals(var2)) {
               this.d.remove(var6);
               break;
            }
         }
      }

   }

   public void b(ArrayList<ProjectFileBean> var1) {
      this.d = var1;
   }

   public ArrayList<ProjectFileBean> c() {
      if (this.d == null) {
         this.d = new ArrayList();
      }

      return this.d;
   }

   public boolean c(String var1) {
      Iterator var2 = this.b.iterator();

      while(var2.hasNext()) {
         if (var1.equals((String)var2.next())) {
            return true;
         }
      }

      return false;
   }

   public ArrayList<String> d() {
      return this.b;
   }

   public boolean d(String var1) {
      Iterator var2 = this.a.iterator();

      while(var2.hasNext()) {
         if (var1.equals((String)var2.next())) {
            return true;
         }
      }

      return false;
   }

   public ArrayList<String> e() {
      return this.a;
   }

   public final void e(String var1) {
      StringBuffer var2 = new StringBuffer();
      this.a(var2);

      try {
         byte[] var4 = this.f.d(var2.toString());
         this.f.a(var1, var4);
      } catch (Exception var3) {
         var3.printStackTrace();
      }

   }

   public final void f() {
      this.c = new ArrayList();
      this.d = new ArrayList();
      this.a(0, "main");
   }

   public boolean g() {
      String var1 = WorkspacePathManager.a(this.e);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("file");
      String var3 = var2.toString();
      return this.f.e(var3);
   }

   public void h() {
      this.f();
      String var1 = WorkspacePathManager.a(this.e) + File.separator + "file";
      BufferedReader var2 = null;
      try {
         byte[] var3 = this.f.h(var1);
         String var4 = this.f.a(var3);
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
      this.j();
   }

   public void i() {
      this.f();
      String var1 = WorkspacePathManager.b(this.e) + File.separator + "file";
      if (!this.f.e(var1)) {
         return;
      }
      BufferedReader var2 = null;
      try {
         byte[] var3 = this.f.h(var1);
         String var4 = this.f.a(var3);
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

   public void j() {
      this.a.clear();
      this.b.clear();

      for(ProjectFileBean var2 : this.c) {
         if (var2.fileType == 0) {
            if (var2.fileName.equals("main")) {
               this.a.add(0, var2.getXmlName());
               this.b.add(0, var2.getJavaName());
            } else {
               this.a.add(var2.getXmlName());
               this.b.add(var2.getJavaName());
            }
         }
      }

      ArrayList<ProjectFileBean> var4 = this.d;
      if (var4 != null) {
         for(ProjectFileBean var5 : var4) {
            int var3 = var5.fileType;
            if (var3 == 1 || var3 == 2) {
               this.a.add(var5.getXmlName());
            }
         }
      }

   }

   public void k() {
      this.e = "";
      this.a = new ArrayList();
      this.b = new ArrayList();
      this.f();
   }

   public void l() {
      String var1 = WorkspacePathManager.a(this.e);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("file");
      this.e(var2.toString());
   }

   public void m() {
      String var1 = WorkspacePathManager.b(this.e);
      StringBuilder var2 = new StringBuilder();
      var2.append(var1);
      var2.append(File.separator);
      var2.append("file");
      this.e(var2.toString());
      this.a();
   }
}
