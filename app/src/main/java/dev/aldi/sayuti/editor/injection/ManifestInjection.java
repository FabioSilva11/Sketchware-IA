package dev.aldi.sayuti.editor.injection;

import java.util.ArrayList;

import pro.sketchware.compiler.ProjectBuildMetadata;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.xml.XmlBuilder;

public class ManifestInjection {

    public ArrayList arr;
    public ProjectBuildMetadata buildConfig;
    public String path;
    public String replace;
    public String value;

    public ManifestInjection(ProjectBuildMetadata projectBuildMetadata, ArrayList arrayList) {
        buildConfig = projectBuildMetadata;
        arr = arrayList;
    }

    public void b(XmlBuilder nx, String str, String str2) {
        path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + buildConfig.sc_id + "/injection/manifest/" + str;
        if (FileUtil.isExistFile(path)) {
            FileUtil.readFile(path).isEmpty();
        }
    }
}