package com.besome.sketch.editor.manage.lottie;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.OpenableColumns;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.lib.base.BaseDialogActivity;
import com.besome.sketch.lib.ui.EasyDeleteEditText;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import a.a.a.By;
import a.a.a.HB;
import a.a.a.MA;
import a.a.a.Op;
import a.a.a.PB;
import a.a.a.bB;
import a.a.a.oB;
import a.a.a.uq;
import a.a.a.xB;
import a.a.a.yy;
import a.a.a.wq;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;

public class AddLottieActivity extends BaseDialogActivity implements View.OnClickListener {
    private ArrayList<ProjectResourceBean> existingLotties;
    private TextView tv_add_lottie;
    private LottieAnimationView preview;
    private ArrayList<Uri> pickedLottieUris;
    private PB O;
    private EditText ed_input_edittext;
    private EasyDeleteEditText ed_input;
    private ImageView tv_desc;
    private CheckBox chk_collection;
    private String sc_id;
    private ArrayList<ProjectResourceBean> lotties;
    private boolean multipleLottiesPicked = false;
    private LinearLayout layout_lottie_inform = null;
    private LinearLayout layout_lottie_modify = null;
    private TextView tv_lottiecnt = null;
    private boolean B = false;
    private String lottieFilePath = null;
    private String dir_path = "";
    private boolean editing = false;
    private ProjectResourceBean lottie = null;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 215 && preview != null) {
            preview.setEnabled(true);
            if (resultCode == RESULT_OK) {
                tv_add_lottie.setVisibility(View.GONE);
                if (data.getClipData() == null) {
                    B = true;
                    multipleLottiesPicked = false;
                    setLottieFromUri(data.getData());
                    if (O != null) {
                        O.a(1);
                    }
                } else {
                    ClipData clipData = data.getClipData();
                    if (clipData.getItemCount() == 1) {
                        B = true;
                        multipleLottiesPicked = false;
                        setLottieFromUri(clipData.getItemAt(0).getUri());
                        if (O != null) {
                            O.a(1);
                        }
                    } else {
                        handleLottiePickClipData(clipData);
                        multipleLottiesPicked = true;
                        if (O != null) {
                            O.a(clipData.getItemCount());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.cancel_button) {
            setResult(RESULT_CANCELED);
            finish();
        } else if (id == R.id.common_dialog_cancel_button) {
            finish();
        } else if (id == R.id.common_dialog_ok_button) {
            save();
        } else if (id == R.id.lottie_selected) {
            preview.setEnabled(false);
            pickLotties(!editing);
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        e("Add Lottie Animation");
        d(xB.b().a(getApplicationContext(), R.string.common_word_save));
        setContentView(R.layout.manage_lottie_add);
        Intent intent = getIntent();
        existingLotties = intent.getParcelableArrayListExtra("lotties");
        sc_id = intent.getStringExtra("sc_id");
        dir_path = intent.getStringExtra("dir_path");
        lottie = intent.getParcelableExtra("edit_target");
        if (lottie != null) {
            editing = true;
        }
        layout_lottie_inform = findViewById(R.id.layout_lottie_inform);
        layout_lottie_modify = findViewById(R.id.layout_lottie_modify);
        chk_collection = findViewById(R.id.chk_collection);
        tv_desc = findViewById(R.id.tv_desc);
        tv_lottiecnt = findViewById(R.id.tv_lottiecnt);
        tv_add_lottie = findViewById(R.id.tv_add_photo);
        preview = findViewById(R.id.lottie_selected);
        ed_input = findViewById(R.id.ed_input);
        ed_input_edittext = ed_input.getEditText();
        ed_input_edittext.setPrivateImeOptions("defaultInputmode=english;");
        ed_input.setHint("Enter Lottie animation name");
        O = new PB(this, ed_input.getTextInputLayout(), uq.b, getReservedLottieNames());
        O.a(1);
        chk_collection.setText(xB.b().a(getApplicationContext(), R.string.design_manager_title_add_to_collection));
        tv_add_lottie.setText("Add Lottie Animation");
        preview.setOnClickListener(this);
        r.setOnClickListener(this);
        s.setOnClickListener(this);
        B = false;
        // dir_path already points to assets/lottie; ensure it exists
        new oB().f(dir_path);
        lotties = new ArrayList<>();
    }

    @Override
    public void onPostCreate(Bundle bundle) {
        super.onPostCreate(bundle);
        if (editing) {
            lottie.isEdited = true;
            e("Edit Lottie Animation");
            O = new PB(this, ed_input.getTextInputLayout(), uq.b, getReservedLottieNames(), lottie.resName);
            O.a(1);
            ed_input_edittext.setText(lottie.resName);
            ed_input_edittext.setEnabled(false);
            chk_collection.setEnabled(false);
            tv_add_lottie.setVisibility(View.GONE);
            if (lottie.savedPos == 0) {
                setLottieFromFile(a(lottie));
            } else {
                setLottieFromFile(lottie.resFullName);
            }
        }
    }

    private ArrayList<String> getReservedLottieNames() {
        var names = new ArrayList<String>();
        if (existingLotties != null) {
            for (var existingLottie : existingLotties) {
                names.add(existingLottie.resName);
            }
        }
        return names;
    }

    private void save() {
        if (a(O)) {
            new Handler().postDelayed(() -> {
                k();
                new SaveAsyncTask(this).execute();
            }, 500L);
        }
    }

    private void s() {
        if (tv_desc != null) {
            tv_desc.setVisibility(View.INVISIBLE);
        }
        if (layout_lottie_inform != null && layout_lottie_modify != null && tv_lottiecnt != null) {
            layout_lottie_inform.setVisibility(View.GONE);
            layout_lottie_modify.setVisibility(View.VISIBLE);
            tv_lottiecnt.setVisibility(View.GONE);
        }
    }

    private void pickLotties(boolean allowMultiple) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (allowMultiple) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            startActivityForResult(Intent.createChooser(intent, "Choose Lottie File"), 215);
        } catch (ActivityNotFoundException unused) {
            bB.b(this, xB.b().a(this, R.string.common_error_activity_not_found), bB.TOAST_NORMAL).show();
        }
    }

    private void setLottieFromFile(String path) {
        lottieFilePath = path;
        try {
            String json = readFileContents(path);
            preview.setAnimationFromJson(json, path);
            preview.setRepeatCount(LottieDrawable.INFINITE);
            preview.playAnimation();
        } catch (Exception ignored) {
            preview.cancelAnimation();
            preview.setImageResource(android.R.drawable.ic_media_play);
        }
        int indexOfFilenameExtension = path.lastIndexOf(".");
        if (ed_input_edittext != null && (ed_input_edittext.getText() == null || ed_input_edittext.getText().length() <= 0)) {
            ed_input_edittext.setText(path.substring(path.lastIndexOf("/") + 1, indexOfFilenameExtension));
        }
        s();
    }

    private void onMultipleLottiesPicked(int count) {
        if (layout_lottie_inform == null || layout_lottie_modify == null || tv_lottiecnt == null) {
            return;
        }
        layout_lottie_inform.setVisibility(View.VISIBLE);
        layout_lottie_modify.setVisibility(View.GONE);
        tv_lottiecnt.setVisibility(View.VISIBLE);
        tv_lottiecnt.setText("+ " + (count - 1) + " more");
    }

    private boolean a(PB pb) {
        if (!pb.b()) {
            return false;
        }
        if (B || lottieFilePath != null) {
            return true;
        }
        tv_desc.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ani_1));
        return false;
    }

    private void setLottieFromUri(Uri uri) {
        if (uri == null) return;
        String filePath = null;
        try {
            filePath = HB.a(this, uri);
        } catch (Throwable ignored) {
            // Fallback below
        }
        if (filePath == null) {
            filePath = resolveLottieFilePathFromUri(uri);
        }
        if (filePath != null) {
            setLottieFromFile(filePath);
        } else {
            bB.b(this, xB.b().a(this, R.string.collection_no_exist_file), bB.TOAST_NORMAL).show();
        }
    }

        private void handleLottiePickClipData(ClipData clipData) {
        if (clipData != null) {
            pickedLottieUris = new ArrayList<>();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                if (i == 0) {
                    setLottieFromUri(clipData.getItemAt(i).getUri());
                }
                pickedLottieUris.add(clipData.getItemAt(i).getUri());
            }
            onMultipleLottiesPicked(clipData.getItemCount());
        }
    }

    private String resolveLottieFilePathFromUri(Uri uri) {
        try {
            // Try HB again in case it succeeds silently
            String hbPath = null;
            try {
                hbPath = HB.a(getApplicationContext(), uri);
            } catch (Throwable ignored) {
            }
            if (hbPath != null) return hbPath;

            // Robust SAF fallback: copy to app cache and return absolute path
            String fileName = queryDisplayName(uri);
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "lottie_" + System.currentTimeMillis() + ".json";
            } else if (!fileName.toLowerCase().endsWith(".json")) {
                fileName = fileName + ".json";
            }

            File cacheDir = new File(getCacheDir(), "lottie_imports");
            if (!cacheDir.exists()) {
                // Ensure directory exists
                cacheDir.mkdirs();
            }
            File outFile = new File(cacheDir, fileName);

            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(outFile)) {
                if (is == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private String readFileContents(String path) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(new java.io.File(path));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toString("UTF-8");
        }
    }

    private String a(ProjectResourceBean projectResourceBean) {
        // Project-local path within assets/lottie
        return dir_path + File.separator + projectResourceBean.resFullName;
    }

    private void addToMyCollection(ProjectResourceBean lottie) throws By {
        String destDir = wq.a() + File.separator + "lottie" + File.separator + "data";
        new oB().f(destDir);
        String destName = lottie.resName.toLowerCase().endsWith(".json") ? lottie.resName : lottie.resName + ".json";
        File src = new File(lottie.resFullName);
        File dest = new File(destDir, destName);
        if (!src.exists()) {
            throw new By(xB.b().a(getApplicationContext(), R.string.collection_no_exist_file));
        }
        if (dest.exists()) {
            throw new By(xB.b().a(getApplicationContext(), R.string.collection_duplicated_name) + "[" + lottie.resName + "]");
        }
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new By(xB.b().a(getApplicationContext(), R.string.collection_failed_to_copy));
        }
    }

    private static class SaveAsyncTask extends MA {
        private final WeakReference<AddLottieActivity> activity;

        public SaveAsyncTask(AddLottieActivity activity) {
            super(activity.getApplicationContext());
            this.activity = new WeakReference<>(activity);
            activity.a(this);
        }

        @Override
        public void a() {
            var activity = this.activity.get();
            activity.h();
            Intent intent = new Intent();
            intent.putExtra("sc_id", activity.sc_id);
            if (activity.editing) {
                intent.putExtra("lottie", activity.lottie);
            } else {
                intent.putExtra("lotties", activity.lotties);
            }
            activity.setResult(RESULT_OK, intent);
            activity.finish();
        }

        @Override
        public void b() throws By {
            var activity = this.activity.get();
            try {
                publishProgress("Processing Lottie files...");
                String lottieDir = activity.dir_path;
                new oB().f(lottieDir);
                
                if (!activity.multipleLottiesPicked) {
                    if (!activity.editing) {
                        var lottie = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                                Helper.getText(activity.ed_input_edittext).trim(), activity.lottieFilePath);
                        lottie.savedPos = 1;
                        lottie.isNew = true;
                        if (activity.chk_collection.isChecked()) {
                            activity.addToMyCollection(lottie);
                        }
                        activity.lotties.add(lottie);
                    } else if (!activity.B) {
                        var lottie = activity.lottie;
                        lottie.isEdited = true;
                    } else {
                        var lottie = activity.lottie;
                        lottie.resFullName = activity.lottieFilePath;
                        lottie.savedPos = 1;
                        lottie.isEdited = true;
                    }
                } else {
                    var toAdd = new ArrayList<ProjectResourceBean>();
                    int i = 0;
                    while (i < activity.pickedLottieUris.size()) {
                        var uri = activity.pickedLottieUris.get(i);
                        var lottieName = Helper.getText(activity.ed_input_edittext).trim() + "_" + ++i;
                        var lottieFilePath = activity.resolveLottieFilePathFromUri(uri);
                        if (lottieFilePath == null) {
                            return;
                        }
                        var lottie = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                                lottieName, lottieFilePath);
                        lottie.savedPos = 1;
                        lottie.isNew = true;
                        toAdd.add(lottie);
                    }
                    if (activity.chk_collection.isChecked()) {
                        for (ProjectResourceBean l : toAdd) {
                            activity.addToMyCollection(l);
                        }
                    }
                    activity.multipleLottiesPicked = false;
                    activity.lotties.addAll(toAdd);
                }
            } catch (Exception e) {
                if (e instanceof yy yy) {
                    var errorMessage = yy.getMessage();
                    var code = switch (errorMessage) {
                        case "fail_to_copy" -> R.string.collection_failed_to_copy;
                        case "file_no_exist" -> R.string.collection_no_exist_file;
                        case "duplicate_name" -> R.string.collection_duplicated_name;
                        default -> 0;
                    };
                    var message = code != 0 ? xB.b().a(activity.getApplicationContext(), code) : null;

                    var a = yy.a();
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
                    throw new By(message);
                }
                e.printStackTrace();
                throw new By(e.getMessage());
            }
        }

        @Override
        public void a(String str) {
            activity.get().h();
        }
    }
}
