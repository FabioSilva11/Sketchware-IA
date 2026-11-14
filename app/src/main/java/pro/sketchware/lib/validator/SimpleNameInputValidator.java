package pro.sketchware.lib.validator;

import android.content.Context;
import android.text.Spanned;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.regex.Pattern;

import pro.sketchware.core.TranslationManager;

public class SimpleNameInputValidator extends TextInputValidator {
   public String[] f;
   public ArrayList g;
   public String h;
   public Pattern i = Pattern.compile("^[a-z][a-z0-9_]*");

   public SimpleNameInputValidator(Context var1, TextInputLayout var2, String[] var3, ArrayList var4) {
      super(var1, var2);
      this.f = var3;
      this.g = var4;
   }

   public SimpleNameInputValidator(Context var1, TextInputLayout var2, String[] var3, ArrayList var4, String var5) {
      super(var1, var2);
      this.f = var3;
      this.g = var4;
      this.h = var5;
   }

   public CharSequence filter(CharSequence var1, int var2, int var3, Spanned var4, int var5, int var6) {
      return null;
   }

   public void onTextChanged(CharSequence var1, int var2, int var3, int var4) {
      String var5 = var1.toString().trim();
      if (var5.length() < 3) {
         super.b.setErrorEnabled(true);
         super.b.setError(TranslationManager.b().a(super.a, 2131625433, new Object[]{3}));
         super.d = false;
      } else if (var5.length() > 70) {
         super.b.setErrorEnabled(true);
         super.b.setError(TranslationManager.b().a(super.a, 2131625432, new Object[]{70}));
         super.d = false;
      } else if (!var5.equals("default_image") && !"NONE".toLowerCase().equals(var5.toLowerCase())) {
         if (!var5.equals(this.h) && this.g.indexOf(var5) >= 0) {
            super.b.setErrorEnabled(true);
            super.b.setError(TranslationManager.b().a(super.a, 2131624950));
            super.d = false;
         } else {
            String[] var6 = this.f;
            var3 = var6.length;
            var2 = 0;

            while(true) {
               if (var2 >= var3) {
                  var2 = 0;
                  break;
               }

               var5 = var6[var2];
               if (var1.toString().equals(var5)) {
                  var2 = 1;
                  break;
               }

               ++var2;
            }

            if (var2 == 1) {
               super.b.setErrorEnabled(true);
               super.b.setError(TranslationManager.b().a(super.a, 2131625495));
               super.d = false;
            } else if (!Character.isLetter(var1.charAt(0))) {
               super.b.setErrorEnabled(true);
               super.b.setError(TranslationManager.b().a(super.a, 2131625497));
               super.d = false;
            } else {
               if (this.i.matcher(var1.toString()).matches()) {
                  super.b.setErrorEnabled(false);
                  super.d = true;
               } else {
                  super.b.setErrorEnabled(true);
                  super.b.setError(TranslationManager.b().a(super.a, 2131625437));
                  super.d = false;
               }

            }
         }
      } else {
         super.b.setErrorEnabled(true);
         super.b.setError(TranslationManager.b().a(super.a, 2131624950));
         super.d = false;
      }
   }
}
