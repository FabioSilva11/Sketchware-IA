package pro.sketchware.compiler;

import java.io.OutputStream;

public class ApkSigningOutputStream extends OutputStream {
   public final ApkSigningManager a;

   public ApkSigningOutputStream(ApkSigningManager var1) {
      this.a = var1;
   }

   public void write(int var1) {
      this.a.b.put((byte)var1);
   }
}
