package pro.sketchware.workspace;

import pro.sketchware.beans.ProjectDataManager;
import pro.sketchware.filemanagers.ProjectResourceManager;

public class ProjectDataManagerRegistry {

    public static ProjectDataManager a;
    public static ProjectFileManager b;
    public static ProjectResourceManager c;
    public static ProjectLibraryManager d;

    public static void a() {
        a = null;
        b = null;
        c = null;
        d = null;
    }

    public static void b() {
        a.i();
        a = null;
    }

    public static void c() {
        b.k();
        b = null;
    }

    public static void d() {
        d.j();
        d = null;
    }

    public static void e() {
        c.t();
        c = null;
    }

    public static synchronized ProjectFileManager b(String str) {
        return b(str, true);
    }

    public static synchronized ProjectLibraryManager c(String str) {
        return c(str, true);
    }

    public static synchronized ProjectResourceManager d(String str) {
        return d(str, true);
    }

    public static synchronized ProjectFileManager b(String str, boolean z) {
        if (b != null && !str.equals(b.e)) {
            c();
        }
        if (b == null) {
            b = new ProjectFileManager(str);
            if (!z) {
                b.i();
            } else if (b.g()) {
                b.h();
            } else {
                b.i();
            }
        }
        return b;
    }

    public static synchronized ProjectLibraryManager c(String str, boolean z) {
        if (d != null && !str.equals(d.a)) {
            d();
        }
        if (d == null) {
            d = new ProjectLibraryManager(str);
            if (!z) {
                d.i();
            } else if (d.g()) {
                d.h();
            } else {
                d.i();
            }
        }
        return d;
    }

    public static synchronized ProjectResourceManager d(String str, boolean z) {
        if (c != null && !str.equals(c.i)) {
            e();
        }
        if (c == null) {
            c = new ProjectResourceManager(str);
            if (!z) {
                c.s();
            } else if (c.q()) {
                c.r();
            } else {
                c.s();
            }
        }
        return c;
    }

    public static synchronized ProjectDataManager a(String str) {
        return a(str, true);
    }

    public static synchronized ProjectDataManager a(String str, boolean z) {
        if (a != null && !str.equals(a.a)) {
            b();
        }
        if (a == null) {
            a = new ProjectDataManager(str);
            if (!z) {
                a.g();
                a.e();
            } else {
                if (a.d()) {
                    a.h();
                } else {
                    a.g();
                }
                if (a.c()) {
                    a.f();
                } else {
                    a.e();
                }
            }
        }
        return a;
    }
}
