package pro.sketchware.lib.validator;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import com.google.android.material.textfield.TextInputLayout;

public class FloatRangeInputValidator extends TextInputValidator {
   public int f;
   public int g;

   public FloatRangeInputValidator(Context var1, TextInputLayout var2, int var3, int var4) {
      super(var1, var2);
      this.f = var3;
      this.g = var4;
      super.c = var2.getEditText();
      super.c.setFilters(new InputFilter[]{this});
      super.c.addTextChangedListener(this);
   }

   public void afterTextChanged(Editable var1) {
   }

   public CharSequence filter(CharSequence var1, int var2, int var3, Spanned var4, int var5, int var6) {
      return null;
   }

   public void onTextChanged(CharSequence var1, int var2, int var3, int var4) {
      String var7 = var1.toString();
      if (var7.isEmpty()) {
         super.b.setErrorEnabled(true);
         super.b.setError(String.format("%d ~ %d", this.f, this.g));
         super.d = false;
      } else {
         try {
            float var5 = Float.parseFloat(var7);
            if (var5 >= (float)this.f && var5 <= (float)this.g) {
               super.b.setErrorEnabled(false);
               super.d = true;
            } else {
               super.b.setErrorEnabled(true);
               super.b.setError(String.format("%d ~ %d", this.f, this.g));
               super.d = false;
            }
         } catch (NumberFormatException var6) {
            super.b.setErrorEnabled(true);
            super.b.setError(String.format("%d ~ %d", this.f, this.g));
            super.d = false;
         }
      }

   }
}
