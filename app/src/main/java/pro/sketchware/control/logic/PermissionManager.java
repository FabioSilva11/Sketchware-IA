package pro.sketchware.control.logic;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;

import java.util.ArrayList;
import java.util.Map.Entry;

import pro.sketchware.compiler.ActivitySourceGenerator;
import pro.sketchware.workspace.ProjectDataManagerRegistry;
import pro.sketchware.compiler.ProjectBuildMetadata;

public class PermissionManager {

    private final String javaName;
    private final String sc_id;
    public boolean hasPermission = false;

    public PermissionManager(String id, String javaName) {
        this.javaName = javaName;
        sc_id = id;
    }

    private ArrayList<String> addedPermissions() {
        ArrayList<String> permList = new ArrayList<>();
        for (Entry<String, ArrayList<BlockBean>> blocks : ProjectDataManagerRegistry.a(sc_id).b(javaName).entrySet()) {
            for (BlockBean block : blocks.getValue()) {
                if (block.opCode.equals("addPermission")) {
                    String firstParam = block.parameters.get(0);
                    if (!firstParam.trim().isEmpty()) {
                        if (firstParam.startsWith("Manifest")) {
                            permList.add(firstParam);
                        } else {
                            permList.add("Manifest.permission." + firstParam);
                        }
                    }
                }
            }
        }
        return permList;
    }

    private String formatPermission(boolean isAppCompat, String permission) {
        if (isAppCompat) {
            return "ContextCompat.checkSelfPermission(this, " + permission + ") == PackageManager.PERMISSION_DENIED";
        } else {
            return "checkSelfPermission(" + permission + ") == PackageManager.PERMISSION_DENIED";
        }
    }

    private void addReqPermission(boolean isAppCompat, ArrayList<String> checkPerm, ArrayList<String> reqPerm) {
        for (String permission : addedPermissions()) {
            checkPerm.add(formatPermission(isAppCompat, permission));
            reqPerm.add(permission);
        }
    }

    private void removePermission(boolean isAppCompat, ArrayList<String> checkPerm, ArrayList<String> reqPerm) {
        for (Entry<String, ArrayList<BlockBean>> blocks : ProjectDataManagerRegistry.a(sc_id).b(javaName).entrySet()) {
            for (BlockBean block : blocks.getValue()) {
                if (block.opCode.equals("removePermission") && !block.parameters.get(0).trim().isEmpty()) {
                    String permission = block.parameters.get(0).startsWith("Manifest") ? block.parameters.get(0) : ("Manifest.permission." + block.parameters.get(0));
                    checkPerm.remove(formatPermission(isAppCompat, permission));
                    reqPerm.remove(permission);
                }
            }
        }
    }

    /**
     * Android 13+ never grants READ/WRITE_EXTERNAL_STORAGE to apps targeting API 33+, so checking
     * them there would request permissions on every launch.
     */
    private void onlyCheckGrantableStoragePermissions(boolean isAppCompat, ArrayList<String> checkPerm) {
        for (String permission : new String[]{"Manifest.permission.READ_EXTERNAL_STORAGE", "Manifest.permission.WRITE_EXTERNAL_STORAGE"}) {
            int index = checkPerm.indexOf(formatPermission(isAppCompat, permission));
            if (index >= 0) {
                checkPerm.set(index, "(Build.VERSION.SDK_INT < 33 || getApplicationInfo().targetSdkVersion < 33) && " + checkPerm.get(index));
            }
        }
    }

    public boolean hasNewPermission() {
        return !addedPermissions().isEmpty();
    }

    private boolean usesFusedLocationManager() {
        for (ComponentBean componentBean : ProjectDataManagerRegistry.a(sc_id).e(javaName)) {
            if (componentBean.type == ComponentBean.COMPONENT_TYPE_FUSED_LOCATION_MANAGER) return true;
        }
        return false;
    }

    public String writePermission(boolean isAppCompat, int var1) {
        ArrayList<String> checkPerm = new ArrayList<>();
        ArrayList<String> addPerm = new ArrayList<>();
        StringBuilder permissionCode = new StringBuilder();

        addReqPermission(isAppCompat, checkPerm, addPerm);

        if (usesFusedLocationManager()) {
            String coarseLocation = isAppCompat
                    ? "ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED"
                    : "checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED";
            if (!checkPerm.contains(coarseLocation)) checkPerm.add(coarseLocation);
            if (!addPerm.contains("Manifest.permission.ACCESS_COARSE_LOCATION")) addPerm.add("Manifest.permission.ACCESS_COARSE_LOCATION");
        }

        if (isAppCompat) {
            if ((var1 & ProjectBuildMetadata.PERMISSION_CALL_PHONE) == ProjectBuildMetadata.PERMISSION_CALL_PHONE) {
                checkPerm.add("ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.CALL_PHONE");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_CAMERA) == ProjectBuildMetadata.PERMISSION_CAMERA) {
                checkPerm.add("ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.CAMERA");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_READ_EXTERNAL_STORAGE) == ProjectBuildMetadata.PERMISSION_READ_EXTERNAL_STORAGE) {
                checkPerm.add("ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.READ_EXTERNAL_STORAGE");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_WRITE_EXTERNAL_STORAGE) == ProjectBuildMetadata.PERMISSION_WRITE_EXTERNAL_STORAGE) {
                checkPerm.add("ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.WRITE_EXTERNAL_STORAGE");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_RECORD_AUDIO) == ProjectBuildMetadata.PERMISSION_RECORD_AUDIO) {
                checkPerm.add("ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.RECORD_AUDIO");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_ACCESS_FINE_LOCATION) == ProjectBuildMetadata.PERMISSION_ACCESS_FINE_LOCATION) {
                checkPerm.add("ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.ACCESS_FINE_LOCATION");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_ACTIVITY_RECOGNITION) == ProjectBuildMetadata.PERMISSION_ACTIVITY_RECOGNITION) {
                checkPerm.add("Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.ACTIVITY_RECOGNITION");
            }
            removePermission(true, checkPerm, addPerm);
            onlyCheckGrantableStoragePermissions(true, checkPerm);

            if (!checkPerm.isEmpty() && !addPerm.isEmpty()) {
                permissionCode.append("if (");

                for (int i = 0; i < checkPerm.size(); i++) {
                    if (i != 0) permissionCode.append("\r\n|| ");
                    permissionCode.append(checkPerm.get(i));
                }

                permissionCode.append(") {" + ActivitySourceGenerator.EOL + "ActivityCompat.requestPermissions(this, new String[] {");

                for (int i = 0; i < addPerm.size(); i++) {
                    if (i != 0) permissionCode.append(", ");
                    permissionCode.append(addPerm.get(i));
                }

                permissionCode.append("}, 1000);" + ActivitySourceGenerator.EOL +
                        "} else {" + ActivitySourceGenerator.EOL +
                        "initializeLogic();" + ActivitySourceGenerator.EOL +
                        "}" + ActivitySourceGenerator.EOL);
            }

        } else {
            if ((var1 & ProjectBuildMetadata.PERMISSION_CALL_PHONE) == ProjectBuildMetadata.PERMISSION_CALL_PHONE) {
                checkPerm.add("checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.CALL_PHONE");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_CAMERA) == ProjectBuildMetadata.PERMISSION_CAMERA) {
                checkPerm.add("checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.CAMERA");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_READ_EXTERNAL_STORAGE) == ProjectBuildMetadata.PERMISSION_READ_EXTERNAL_STORAGE) {
                checkPerm.add("checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.READ_EXTERNAL_STORAGE");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_WRITE_EXTERNAL_STORAGE) == ProjectBuildMetadata.PERMISSION_WRITE_EXTERNAL_STORAGE) {
                checkPerm.add("checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.WRITE_EXTERNAL_STORAGE");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_RECORD_AUDIO) == ProjectBuildMetadata.PERMISSION_RECORD_AUDIO) {
                checkPerm.add("checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.RECORD_AUDIO");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_ACCESS_FINE_LOCATION) == ProjectBuildMetadata.PERMISSION_ACCESS_FINE_LOCATION) {
                checkPerm.add("checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.ACCESS_FINE_LOCATION");
            }
            if ((var1 & ProjectBuildMetadata.PERMISSION_ACTIVITY_RECOGNITION) == ProjectBuildMetadata.PERMISSION_ACTIVITY_RECOGNITION) {
                checkPerm.add("Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_DENIED");
                addPerm.add("Manifest.permission.ACTIVITY_RECOGNITION");
            }
            removePermission(false, checkPerm, addPerm);
            onlyCheckGrantableStoragePermissions(false, checkPerm);

            if (!checkPerm.isEmpty() && !addPerm.isEmpty()) {
                permissionCode.append("if (Build.VERSION.SDK_INT >= 23) {" + ActivitySourceGenerator.EOL + "if (");

                for (int i = 0; i < checkPerm.size(); i++) {
                    if (i != 0) permissionCode.append(ActivitySourceGenerator.EOL + "||");
                    permissionCode.append(checkPerm.get(i));
                }

                permissionCode.append(") {" + ActivitySourceGenerator.EOL + "requestPermissions(new String[] {");

                for (int i = 0; i < addPerm.size(); i++) {
                    if (i != 0) permissionCode.append(", ");
                    permissionCode.append(addPerm.get(i));
                }

                permissionCode.append("}, 1000);" + ActivitySourceGenerator.EOL +
                        "} else {" + ActivitySourceGenerator.EOL +
                        "initializeLogic();" + ActivitySourceGenerator.EOL +
                        "}" + ActivitySourceGenerator.EOL +
                        "} else {" + ActivitySourceGenerator.EOL +
                        "initializeLogic();" + ActivitySourceGenerator.EOL +
                        "}" + ActivitySourceGenerator.EOL);
            }
        }

        hasPermission = !checkPerm.isEmpty() || !addPerm.isEmpty();

        if (permissionCode.toString().trim().isEmpty()) {
            return "initializeLogic();" + ActivitySourceGenerator.EOL;
        } else {
            return ActivitySourceGenerator.EOL + permissionCode;
        }
    }
}
