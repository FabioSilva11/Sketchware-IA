package com.besome.sketch.editor.property;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import a.a.a.Kw;
import a.a.a.jC;
import a.a.a.mB;
import a.a.a.wB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.InjectAttributes;
import pro.sketchware.utility.ProjectStrings;

/**
 * Property row for one {@link WidgetAttributes.Spec}: shows the current value and edits it with the
 * dialog that fits the attribute (choices, switch, number, dimension, color, drawable, font or text).
 * The value is written to the bean by {@link ViewPropertyItems} when it applies the panel.
 */
@SuppressLint("ViewConstructor")
public class PropertyAttributeItem extends RelativeLayout implements View.OnClickListener {

    private static final Pattern DIMEN = Pattern.compile("-?\\d+(\\.\\d+)?(dp|sp|px|in|mm|pt)|@dimen/[A-Za-z0-9_.]+|\\?attr/[A-Za-z0-9_.]+");
    private static final String NOT_SET = "Not set";

    private WidgetAttributes.Spec spec;
    private String scId;
    private ViewBean bean;
    private String value = "";
    private boolean dirty;
    private TextView tvName;
    private TextView tvValue;
    private ImageView imgLeftIcon;
    private View propertyItem;
    private View propertyMenuItem;
    private Kw valueChangeListener;

    public PropertyAttributeItem(Context context) {
        super(context);
        wB.a(context, this, R.layout.property_selector_item);
        tvName = findViewById(R.id.tv_name);
        tvValue = findViewById(R.id.tv_value);
        imgLeftIcon = findViewById(R.id.img_left_icon);
        propertyItem = findViewById(R.id.property_item);
        propertyMenuItem = findViewById(R.id.property_menu_item);
    }

    public void setSpec(@NonNull WidgetAttributes.Spec spec, @NonNull String scId) {
        this.spec = spec;
        this.scId = scId;
        tvName.setText(spec.label);
        imgLeftIcon.setImageResource(spec.icon);
        ((ImageView) findViewById(R.id.img_icon)).setImageResource(spec.icon);
        ((TextView) findViewById(R.id.tv_title)).setText(spec.label);
        setTag(spec.key());
    }

    public WidgetAttributes.Spec getSpec() {
        return spec;
    }

    public String getKey() {
        return spec.key();
    }

    /** Reads the current value from the bean (inject or parent attributes). */
    public void setBean(@NonNull ViewBean bean) {
        this.bean = bean;
        dirty = false;
        showValue(readValue(bean));
    }

    @NonNull
    private String readValue(@NonNull ViewBean bean) {
        String raw = spec.layoutParam ? bean.parentAttributes.get(spec.name) : InjectAttributes.get(bean, spec.name);
        return raw == null ? "" : raw;
    }

    private void showValue(@NonNull String raw) {
        value = raw;
        String display = raw;
        if (spec.kind == WidgetAttributes.Kind.STRING && ProjectStrings.isReference(raw)) {
            display = ProjectStrings.resolve(scId, raw);
        }
        tvValue.setText(display.isEmpty() ? NOT_SET : display);
        TextView subtitle = findViewById(R.id.tv_sub_title);
        if (subtitle != null) {
            subtitle.setVisibility(display.isEmpty() ? GONE : VISIBLE);
            subtitle.setText(display);
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Writes the edited value into {@code target}. STRING attributes become string resources owned by
     * the widget.
     */
    public void applyTo(@NonNull ViewBean target) {
        if (!dirty) return;
        String newValue = value;
        if (spec.kind == WidgetAttributes.Kind.STRING && !newValue.isEmpty() && !ProjectStrings.isReference(newValue)) {
            newValue = ProjectStrings.assign(scId, target.id, spec.stringField(), readValue(target), newValue);
        }
        if (spec.layoutParam) {
            if (newValue.isEmpty()) {
                target.parentAttributes.remove(spec.name);
            } else {
                target.parentAttributes.put(spec.name, newValue);
            }
        } else {
            InjectAttributes.set(target, spec.name, newValue);
        }
        dirty = false;
    }

    public void setOnPropertyValueChangeListener(Kw listener) {
        valueChangeListener = listener;
    }

    public void setOrientationItem(int orientation) {
        if (orientation == LinearLayout.HORIZONTAL) {
            propertyItem.setVisibility(GONE);
            propertyMenuItem.setVisibility(VISIBLE);
            propertyItem.setOnClickListener(null);
            propertyMenuItem.setOnClickListener(this);
        } else {
            propertyItem.setVisibility(VISIBLE);
            propertyMenuItem.setVisibility(GONE);
            propertyItem.setOnClickListener(this);
            propertyMenuItem.setOnClickListener(null);
        }
    }

    @Override
    public void onClick(View v) {
        if (mB.a() || spec == null) return;
        switch (spec.kind) {
            case ENUM -> showChoices(spec.options);
            case BOOLEAN -> showChoices(new String[]{"true", "false"});
            case FLAGS -> showFlags();
            case COLOR -> showColorPicker(v);
            case DRAWABLE -> showChoices(projectDrawables());
            case FONT -> showChoices(fonts());
            case LAYOUT -> showChoices(projectLayouts());
            default -> showInput();
        }
    }

    private void commit(@NonNull String newValue) {
        value = newValue.trim();
        dirty = true;
        String display = value;
        if (spec.kind == WidgetAttributes.Kind.STRING && ProjectStrings.isReference(value)) {
            display = ProjectStrings.resolve(scId, value);
        }
        tvValue.setText(display.isEmpty() ? NOT_SET : display);
        if (valueChangeListener != null) {
            valueChangeListener.a(spec.key(), value);
        }
    }

    private MaterialAlertDialogBuilder dialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(spec.label)
                .setIcon(spec.icon);
        return builder;
    }

    private void showChoices(@NonNull String[] options) {
        View root = wB.a(getContext(), R.layout.property_popup_choice_attribute);
        TextView help = root.findViewById(R.id.help);
        help.setText(spec.help == null ? "" : spec.help);
        help.setVisibility(spec.help == null ? GONE : VISIBLE);
        android.widget.RadioGroup group = root.findViewById(R.id.choices);
        String[] items = new String[options.length + 1];
        items[0] = NOT_SET;
        System.arraycopy(options, 0, items, 1, options.length);
        var alert = dialog().setView(root).setNegativeButton(R.string.common_word_cancel, null).create();
        for (int i = 0; i < items.length; i++) {
            android.widget.RadioButton button = new android.widget.RadioButton(getContext());
            button.setText(items[i]);
            button.setId(View.generateViewId());
            boolean selected = i == 0 ? value.isEmpty() : items[i].equals(value);
            group.addView(button);
            button.setChecked(selected);
            int index = i;
            button.setOnClickListener(b -> {
                commit(index == 0 ? "" : options[index - 1]);
                alert.dismiss();
            });
        }
        alert.show();
    }

    private void showFlags() {
        String[] options = spec.options;
        boolean[] checked = new boolean[options.length];
        List<String> current = List.of(value.split("\\|"));
        for (int i = 0; i < options.length; i++) {
            checked[i] = current.contains(options[i]);
        }
        dialog().setMultiChoiceItems(options, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.common_word_save, (d, w) -> {
                    StringBuilder joined = new StringBuilder();
                    for (int i = 0; i < options.length; i++) {
                        if (!checked[i]) continue;
                        if (joined.length() > 0) joined.append('|');
                        joined.append(options[i]);
                    }
                    commit(joined.toString());
                })
                .setNeutralButton(NOT_SET, (d, w) -> commit(""))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showInput() {
        View root = wB.a(getContext(), R.layout.property_popup_input_attribute);
        TextInputLayout layout = root.findViewById(R.id.input_layout);
        TextInputEditText input = root.findViewById(R.id.input);
        TextView help = root.findViewById(R.id.help);
        help.setText(spec.help == null ? "" : spec.help);
        help.setVisibility(spec.help == null ? GONE : VISIBLE);
        String current = value;
        if (spec.kind == WidgetAttributes.Kind.STRING && ProjectStrings.isReference(current)) {
            current = ProjectStrings.resolve(scId, current);
        }
        input.setText(current);
        switch (spec.kind) {
            case INT -> input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            case FLOAT -> input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            case DIMEN -> layout.setSuffixText(null);
            default -> input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        var alert = dialog().setView(root)
                .setPositiveButton(R.string.common_word_save, null)
                .setNeutralButton(NOT_SET, (d, w) -> commit(""))
                .setNegativeButton(R.string.common_word_cancel, null)
                .create();
        alert.setOnShowListener(d -> alert.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String text = Helper.getText(input).trim();
            String error = validate(text);
            if (error != null) {
                layout.setError(error);
                return;
            }
            commit(text);
            alert.dismiss();
        }));
        alert.show();
    }

    @Nullable
    private String validate(@NonNull String text) {
        if (text.isEmpty()) return null;
        switch (spec.kind) {
            case DIMEN:
                if (text.matches("-?\\d+(\\.\\d+)?")) return "Add a unit, e.g. " + text + "dp";
                return DIMEN.matcher(text).matches() ? null : "Use a dimension like 8dp or 14sp";
            case INT:
                try {
                    Integer.parseInt(text);
                    return null;
                } catch (NumberFormatException e) {
                    return "Enter a whole number";
                }
            case FLOAT:
                try {
                    Float.parseFloat(text);
                    return null;
                } catch (NumberFormatException e) {
                    return "Enter a number, e.g. 0.5";
                }
            default:
                return null;
        }
    }

    private void showColorPicker(View anchor) {
        String current = value.isEmpty() ? "#FF000000" : value;
        ColorPickerDialog picker = new ColorPickerDialog((Activity) getContext(), current, true, true, scId);
        picker.a(new ColorPickerDialog.b() {
            @Override
            public void a(int color) {
                commit(String.format("#%08X", color));
            }

            @Override
            public void a(String name, int color) {
                commit("@color/" + name);
            }
        });
        picker.materialColorAttr((attr, color) -> commit("?" + attr));
        picker.showAtLocation(anchor, Gravity.CENTER, 0, 0);
    }

    @NonNull
    private String[] projectLayouts() {
        List<String> layouts = new ArrayList<>();
        try {
            for (com.besome.sketch.beans.ProjectFileBean file : jC.b(scId).b()) {
                layouts.add("@layout/" + file.getXmlName().replace(".xml", ""));
            }
            for (com.besome.sketch.beans.ProjectFileBean file : jC.b(scId).c()) {
                layouts.add("@layout/" + file.getXmlName().replace(".xml", ""));
            }
        } catch (Exception ignored) {
        }
        return layouts.toArray(new String[0]);
    }

    @NonNull
    private String[] projectDrawables() {
        List<String> names = new ArrayList<>();
        try {
            for (String name : jC.d(scId).m()) {
                if (name != null && !name.isEmpty() && !"NONE".equals(name) && !"default_image".equals(name)) {
                    names.add("@drawable/" + name.replace(".9", ""));
                }
            }
        } catch (Exception ignored) {
        }
        return names.toArray(new String[0]);
    }

    @NonNull
    private String[] fonts() {
        List<String> fonts = new ArrayList<>(List.of("sans-serif", "sans-serif-medium", "sans-serif-light",
                "sans-serif-thin", "sans-serif-black", "sans-serif-condensed", "serif", "monospace", "cursive"));
        try {
            ArrayList<String> files = new ArrayList<>();
            pro.sketchware.utility.FileUtil.listDir(new pro.sketchware.utility.FilePathUtil().getPathResource(scId) + File.separator + "font", files);
            for (String path : files) {
                String name = new File(path).getName();
                int dot = name.lastIndexOf('.');
                fonts.add("@font/" + (dot > 0 ? name.substring(0, dot) : name));
            }
        } catch (Exception ignored) {
        }
        return fonts.toArray(new String[0]);
    }
}
