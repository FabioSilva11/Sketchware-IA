package pro.sketchware.filemanagers;

import android.view.View;

import pro.sketchware.editor.FirebaseDbImportView;

public class FirebaseDbSettingsClickListener implements View.OnClickListener {
   public final FirebaseDbImportView a;

   public FirebaseDbSettingsClickListener(FirebaseDbImportView var1) {
      this.a = var1;
   }

   public void onClick(View var1) {
      this.a.toggleFirebaseEnabled();
   }
}
