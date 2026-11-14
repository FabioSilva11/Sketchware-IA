package pro.sketchware.editor;

import android.content.Context;

import pro.sketchware.core.PreferenceStore;

public class DialogFactory {
   public PreferenceStore a;
   public int b;
   public int c;

   public DialogFactory(Context var1) {
      this.a = new PreferenceStore(var1, "U1");
      this.b = this.a.d("U1I0");
      int var2 = this.b;
      int var3 = var2;
      if (var2 > 3) {
         var3 = 3;
      }

      this.c = var3 * 20;
   }
}
