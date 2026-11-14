package pro.sketchware.utility;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.HashMap;

import pro.sketchware.core.PreferenceStore;
import pro.sketchware.editor.ToastFactory;
import pro.sketchware.workspace.RecentProjectsRegistry;
import pro.sketchware.filemanagers.FileCryptoUtils;
import pro.sketchware.workspace.WorkspacePathManager;
import pro.sketchware.core.MapValueUtils;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.utility.TranslationFunction;

public class DataResetter {
    public static void a(Context context, boolean resetOnlySettings) {
        new PreferenceStore(context, "P2").a();
        new PreferenceStore(context, "P3").a();
        new PreferenceStore(context, "P1").a();
        new PreferenceStore(context, "P12").a();
        new PreferenceStore(context, "P99").a();
        new PreferenceStore(context, "P25").a();
        new PreferenceStore(context, "P26").a();
        new PreferenceStore(context, "P16").a("P16I0");

        FileCryptoUtils oBVar = new FileCryptoUtils();
        oBVar.b(WorkspacePathManager.r());
        oBVar.b(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.getAbsolutePath());

        if (!resetOnlySettings) {
            oBVar.b(WorkspacePathManager.q());
            oBVar.b(WorkspacePathManager.s());

            for (HashMap<String, Object> project : RecentProjectsRegistry.a()) {
                String sc_id = MapValueUtils.c(project, "sc_id");
                new PreferenceStore(context, "D01_" + sc_id).a();
                new PreferenceStore(context, "D02_" + sc_id).a();
                new PreferenceStore(context, "D03_" + sc_id).a();
                new PreferenceStore(context, "D04_" + sc_id).a();
                new PreferenceStore(context, "D05_" + sc_id).a();
            }
            new PreferenceStore(context, "P15").a();
        }

        ToastFactory.a(context, Helper.getResString(R.string.program_information_reset_system_complete_initialize), ToastFactory.TOAST_NORMAL).show();
        RecentProjectsRegistry.a(context, true);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Activity activity = (Activity) context;
        activity.startActivity(intent);
        activity.finishAffinity();
    }
}
