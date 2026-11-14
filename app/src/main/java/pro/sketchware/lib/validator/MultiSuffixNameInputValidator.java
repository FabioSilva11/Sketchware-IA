package pro.sketchware.lib.validator;

import android.content.Context;
import android.text.Spanned;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;

import pro.sketchware.core.TranslationManager;

public class MultiSuffixNameInputValidator extends TextInputValidator {
   public String[] f;
   public ArrayList g;
   public String h;
   public int i;
   public Pattern j = Pattern.compile("^[a-z][a-z0-9_]*");

   public MultiSuffixNameInputValidator(Context var1, TextInputLayout var2, String[] var3, ArrayList var4) {
      super(var1, var2);
      this.f = var3;
      this.g = var4;
      this.i = 1;
   }

   public MultiSuffixNameInputValidator(Context var1, TextInputLayout var2, String[] var3, ArrayList var4, String var5) {
      super(var1, var2);
      this.f = var3;
      this.g = var4;
      this.h = var5;
      this.i = 1;
   }

   public void a(int var1) {
      this.i = var1;
      if (((TextInputValidator)this).a().length() > 0) {
         this.b(((TextInputValidator)this).a());
      }

   }

   public final void b(String var1) {
      if (var1.length() < 3) {
         super.b.setErrorEnabled(true);
         super.b.setError(TranslationManager.b().a(super.a, 2131625433, new Object[]{3}));
         super.d = false;
      } else if (var1.length() > 70) {
         super.b.setErrorEnabled(true);
         super.b.setError(TranslationManager.b().a(super.a, 2131625432, new Object[]{70}));
         super.d = false;
      } else if (!var1.equals("default_image") && !"NONE".toLowerCase().equals(var1.toLowerCase())) {
         if (this.i == 1) {
            if (!var1.equals(this.h) && this.g.indexOf(var1) >= 0) {
               super.b.setErrorEnabled(true);
               super.b.setError(TranslationManager.b().a(super.a, 2131624950));
               super.d = false;
               return;
            }
         } else {
            ArrayList<String> var2 = new ArrayList<String>();

            for(int var3 = 1; var3 <= this.i; ++var3) {
               StringBuilder var4 = new StringBuilder();
               var4.append(var1);
               var4.append("_");
               var4.append(var3);
               var2.add(var4.toString());
            }

            ArrayList var14 = new ArrayList();

            for(String var10 : var2) {
               if (this.g.indexOf(var10) >= 0) {
                  var14.add(var10);
               }
            }

            if (var14.size() > 0) {
               super.b.setErrorEnabled(true);
               String var11 = TranslationManager.b().a(super.a, 2131624950);
               Iterator var19 = var14.iterator();
               StringBuilder var9;

               for(var1 = ""; var19.hasNext(); var1 = var9.toString()) {
                  String var6 = (String)var19.next();
                  String var16 = var1;
                  if (var1.length() > 0) {
                     StringBuilder var17 = new StringBuilder();
                     var17.append(var1);
                     var17.append(", ");
                     var16 = var17.toString();
                  }

                  var9 = new StringBuilder();
                  var9.append(var16);
                  var9.append(var6);
               }

               TextInputLayout var20 = super.b;
               StringBuilder var18 = new StringBuilder();
               var18.append(var11);
               var18.append("\n[");
               var18.append(var1);
               var18.append("]");
               var20.setError(var18.toString());
               super.d = false;
               return;
            }
         }

         String[] var15 = this.f;
         int var7 = var15.length;
         int var12 = 0;

         while(true) {
            if (var12 >= var7) {
               var12 = 0;
               break;
            }

            if (var1.equals(var15[var12])) {
               var12 = 1;
               break;
            }

            ++var12;
         }

         if (var12 == 1) {
            super.b.setErrorEnabled(true);
            super.b.setError(TranslationManager.b().a(super.a, 2131625495));
            super.d = false;
         } else if (!Character.isLetter(var1.charAt(0))) {
            super.b.setErrorEnabled(true);
            super.b.setError(TranslationManager.b().a(super.a, 2131625497));
            super.d = false;
         } else {
            if (this.j.matcher(var1.toString()).matches()) {
               super.b.setErrorEnabled(false);
               super.d = true;
            } else {
               super.b.setErrorEnabled(true);
               super.b.setError(TranslationManager.b().a(super.a, 2131625437));
               super.d = false;
            }

         }
      } else {
         super.b.setErrorEnabled(true);
         super.b.setError(TranslationManager.b().a(super.a, 2131624950));
         super.d = false;
      }
   }

   public CharSequence filter(CharSequence var1, int var2, int var3, Spanned var4, int var5, int var6) {
      return null;
   }

   public void onTextChanged(CharSequence var1, int var2, int var3, int var4) {
      this.b(var1.toString().trim());
   }
}
