package com.besome.sketch.editor.manage.image;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.lib.base.BaseDialogActivity;
import com.besome.sketch.lib.ui.EasyDeleteEditText;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import pro.sketchware.beans.ProjectSettingsStore;
import pro.sketchware.filemanagers.DocumentUriUtils;
import pro.sketchware.core.BackgroundTask;
import pro.sketchware.filemanagers.SoundResourceManager;
import pro.sketchware.lib.validator.MultiSuffixNameInputValidator;
import pro.sketchware.editor.ToastFactory;
import pro.sketchware.filemanagers.BitmapUtils;
import pro.sketchware.editor.EditorViewUtils;
import pro.sketchware.editor.ComponentNameRegistry;
import pro.sketchware.workspace.WorkspacePathManager;
import pro.sketchware.core.TranslationManager;
import pro.sketchware.core.StringListStore;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.TranslationFunction;

public class AddImageCollectionActivity extends BaseDialogActivity implements View.OnClickListener {

    private TextView tv_add_photo;
    private ImageView preview;
    private MultiSuffixNameInputValidator imageNameValidator;
    private EditText ed_input_edittext;
    private EasyDeleteEditText ed_input;
    private ImageView tv_desc;
    private CheckBox chk_collection;
    private String sc_id;
    private ArrayList<ProjectResourceBean> images;
    private LinearLayout layout_img_inform = null;
    private LinearLayout layout_img_modify = null;
    private TextView tv_imgcnt = null;
    private boolean z = false;
    private String imageFilePath = null;
    private int imageRotationDegrees = 0;
    private int imageExifOrientation = 0;
    private int imageScaleY = 1;
    private int imageScaleX = 1;
    private boolean editing = false;
    private ProjectResourceBean editTarget = null;

    private void flipImageHorizontally() {
        if (imageFilePath != null && !imageFilePath.isEmpty()) {
            if (imageRotationDegrees != 90 && imageRotationDegrees != 270) {
                imageScaleX *= -1;
            } else {
                imageScaleY *= -1;
            }
            refreshPreview();
        }
    }

    private void flipImageVertically() {
        if (imageFilePath != null && !imageFilePath.isEmpty()) {
            if (imageRotationDegrees != 90 && imageRotationDegrees != 270) {
                imageScaleY *= -1;
            } else {
                imageScaleX *= -1;
            }
            refreshPreview();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 215 && preview != null) {
            preview.setEnabled(true);
            if (resultCode == RESULT_OK) {
                imageRotationDegrees = 0;
                imageScaleY = 1;
                imageScaleX = 1;
                z = true;
                setImageFromUri(data.getData());
                if (imageNameValidator != null) {
                    imageNameValidator.a(1);
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (!EditorViewUtils.a()) {
            int id = v.getId();
            if (id == R.id.cancel_button) {
                setResult(RESULT_CANCELED);
                finish();
            } else if (id == R.id.common_dialog_cancel_button) {
                finish();
            } else if (id == R.id.common_dialog_ok_button) {
                save();
            } else if (id == R.id.img_horizontal) {
                flipImageHorizontally();
            } else if (id == R.id.img_rotate) {
                setImageRotation(imageRotationDegrees + 90);
            } else if (id == R.id.img_selected) {
                preview.setEnabled(false);
                if (!editing) {
                    pickImage();
                }
            } else if (id == R.id.img_vertical) {
                flipImageVertically();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        e(TranslationManager.b().a(this, R.string.design_manager_image_title_add_image));
        d(TranslationManager.b().a(getApplicationContext(), R.string.common_word_save));
        setContentView(R.layout.manage_image_add);
        Intent intent = getIntent();
        images = intent.getParcelableArrayListExtra("images");
        sc_id = intent.getStringExtra("sc_id");
        editTarget = intent.getParcelableExtra("edit_target");
        if (editTarget != null) {
            editing = true;
        }
        layout_img_inform = findViewById(R.id.layout_img_inform);
        layout_img_modify = findViewById(R.id.layout_img_modify);
        chk_collection = findViewById(R.id.chk_collection);
        chk_collection.setVisibility(View.GONE);
        tv_desc = findViewById(R.id.tv_desc);
        tv_imgcnt = findViewById(R.id.tv_imgcnt);
        tv_add_photo = findViewById(R.id.tv_add_photo);
        preview = findViewById(R.id.img_selected);
        ImageView img_rotate = findViewById(R.id.img_rotate);
        ImageView img_vertical = findViewById(R.id.img_vertical);
        ImageView img_horizontal = findViewById(R.id.img_horizontal);
        ed_input = findViewById(R.id.ed_input);
        ed_input_edittext = ed_input.getEditText();
        ed_input_edittext.setPrivateImeOptions("defaultInputmode=english;");
        ed_input.setHint(TranslationManager.b().a(this, R.string.design_manager_image_hint_enter_image_name));
        imageNameValidator = new MultiSuffixNameInputValidator(this, ed_input.getTextInputLayout(), ComponentNameRegistry.b, getReservedImageNames());
        imageNameValidator.a(1);
        tv_add_photo.setText(TranslationManager.b().a(this, R.string.design_manager_image_title_add_image));
        preview.setOnClickListener(this);
        img_rotate.setOnClickListener(this);
        img_vertical.setOnClickListener(this);
        img_horizontal.setOnClickListener(this);
        r.setOnClickListener(this);
        s.setOnClickListener(this);
        z = false;
        imageRotationDegrees = 0;
        imageScaleY = 1;
        imageScaleX = 1;
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (editing) {
            editTarget.isEdited = true;
            e(TranslationManager.b().a(this, R.string.design_manager_image_title_edit_image_name));
            imageNameValidator = new MultiSuffixNameInputValidator(this, ed_input.getTextInputLayout(), ComponentNameRegistry.b, getReservedImageNames(), editTarget.resName);
            imageNameValidator.a(1);
            ed_input_edittext.setText(editTarget.resName);
            chk_collection.setVisibility(View.GONE);
            tv_add_photo.setVisibility(View.GONE);
            setImageFromFile(a(editTarget));
            layout_img_modify.setVisibility(View.GONE);
        }
    }

    private ArrayList<String> getReservedImageNames() {
        ArrayList<String> names = new ArrayList<>();
        names.add("app_icon");
        for (ProjectResourceBean image : images) {
            names.add(image.resName);
        }
        return names;
    }

    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, TranslationManager.b().a(this, R.string.common_word_choose)), 215);
        } catch (ActivityNotFoundException unused) {
            ToastFactory.b(this, TranslationManager.b().a(this, R.string.common_error_activity_not_found), ToastFactory.TOAST_NORMAL).show();
        }
    }

    private void refreshPreview() {
        preview.setImageBitmap(BitmapUtils.a(BitmapUtils.a(BitmapUtils.a(imageFilePath, 1024, 1024), imageExifOrientation), imageRotationDegrees, imageScaleX, imageScaleY));
    }

    private void save() {
        if (a(imageNameValidator)) {
            new Handler().postDelayed(() -> {
                k();
                new SaveAsyncTask(this).execute();
            }, 500L);
        }
    }

    private void t() {
        if (tv_desc != null) {
            tv_desc.setVisibility(View.INVISIBLE);
        }
        if (layout_img_inform != null && layout_img_modify != null && tv_imgcnt != null) {
            layout_img_inform.setVisibility(View.GONE);
            layout_img_modify.setVisibility(View.VISIBLE);
            tv_imgcnt.setVisibility(View.GONE);
        }
    }

    private boolean a(MultiSuffixNameInputValidator validator) {
        if (!validator.b()) {
            return false;
        }
        if (z || imageFilePath != null) {
            return true;
        }
        tv_desc.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ani_1));
        return false;
    }

    private void setImageFromFile(String path) {
        imageFilePath = path;
        preview.setImageBitmap(BitmapUtils.a(path, 1024, 1024));
        int indexOfFilenameExtension = path.lastIndexOf(".");
        if (path.endsWith(".9.png")) {
            indexOfFilenameExtension = path.lastIndexOf(".9.png");
        }
        if (ed_input_edittext != null && (ed_input_edittext.getText() == null || ed_input_edittext.getText().length() <= 0)) {
            ed_input_edittext.setText(path.substring(path.lastIndexOf("/") + 1, indexOfFilenameExtension));
        }
        try {
            imageExifOrientation = BitmapUtils.a(path);
            refreshPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
        t();
    }

    private void setImageRotation(int degrees) {
        if (imageFilePath != null && !imageFilePath.isEmpty()) {
            imageRotationDegrees = degrees;
            if (imageRotationDegrees == 360) {
                imageRotationDegrees = 0;
            }
            refreshPreview();
        }
    }

    private void setImageFromUri(Uri uri) {
        String filePath;
        if (uri != null && (filePath = DocumentUriUtils.a(this, uri)) != null) {
            setImageFromFile(filePath);
        }
    }

    private String a(ProjectResourceBean projectResourceBean) {
        return WorkspacePathManager.a() + File.separator + "image" + File.separator + "data" + File.separator + projectResourceBean.resFullName;
    }

    private static class SaveAsyncTask extends BackgroundTask {
        private final WeakReference<AddImageCollectionActivity> activity;

        public SaveAsyncTask(AddImageCollectionActivity activity) {
            super(activity.getApplicationContext());
            this.activity = new WeakReference<>(activity);
            activity.a(this);
        }

        @Override
        public void a() {
            var activity = this.activity.get();
            ToastFactory.a(activity.getApplicationContext(), TranslationManager.b().a(activity.getApplicationContext(),
                    activity.editing ? R.string.design_manager_message_edit_complete :
                            R.string.design_manager_message_add_complete), ToastFactory.TOAST_NORMAL).show();
            activity.h();
            activity.finish();
        }

        @Override
        public void b() throws ProjectSettingsStore {
            var activity = this.activity.get();
            try {
                publishProgress("Now processing..");
                if (!activity.editing) {
                    var image = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                            Helper.getText(activity.ed_input_edittext).trim(), activity.imageFilePath);
                    image.savedPos = 1;
                    image.isNew = true;
                    image.rotate = activity.imageRotationDegrees;
                    image.flipVertical = activity.imageScaleY;
                    image.flipHorizontal = activity.imageScaleX;
                    SoundResourceManager.g().a(activity.sc_id, image);
                } else {
                    SoundResourceManager.g().a(activity.editTarget, Helper.getText(activity.ed_input_edittext), false);
                }
            } catch (Exception e) {
                // the bytecode's lying
                // noinspection ConstantValue
                if (e instanceof StringListStore StringListStore) {
                    var messageId = switch (StringListStore.getMessage()) {
                        case "fail_to_copy" -> R.string.collection_failed_to_copy;
                        case "file_no_exist" -> R.string.collection_no_exist_file;
                        case "duplicate_name" -> R.string.collection_duplicated_name;
                        default -> 0;
                    };
                    var message = messageId != 0 ? TranslationManager.b().a(activity.getApplicationContext(), messageId) : "";

                    var a = StringListStore.a();
                    if (a != null && !a.isEmpty()) {
                        var names = "";
                        for (String name : a) {
                            if (!names.isEmpty()) {
                                names += ", ";
                            }
                            names += name;
                        }
                        message += "[" + names + "]";
                    }
                    throw new ProjectSettingsStore(message);
                }
                e.printStackTrace();
                throw new ProjectSettingsStore(e.getMessage());
            }
        }

        @Override
        public void a(String str) {
            activity.get().h();
        }
    }
}
