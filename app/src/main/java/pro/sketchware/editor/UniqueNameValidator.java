package pro.sketchware.editor;

import android.content.Context;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.utility.TranslationFunction;

import pro.sketchware.lib.validator.TextInputValidator;

public class UniqueNameValidator extends TextInputValidator {

    private final ArrayList<String> preDefNames;

    public UniqueNameValidator(Context var1, TextInputLayout var2, ArrayList<String> names) {
        super(var1, var2);
        preDefNames = names;
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
        String inputValue = charSequence.toString();
        if (preDefNames.contains(inputValue)) {
            b.setErrorEnabled(true);
            b.setError(a.getString(R.string.common_message_name_unavailable));
            d = false;
        } else {
            b.setErrorEnabled(false);
            d = true;
        }
    }
}
