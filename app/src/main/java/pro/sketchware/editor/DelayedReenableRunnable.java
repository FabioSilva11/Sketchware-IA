package pro.sketchware.editor;

import android.view.View;

public final class DelayedReenableRunnable implements Runnable {
   public final View a;

   public DelayedReenableRunnable(View var1) {
      this.a = var1;
   }

   public void run() {
      this.a.setEnabled(true);
   }
}
