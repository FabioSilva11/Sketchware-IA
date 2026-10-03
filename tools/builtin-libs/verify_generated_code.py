#!/usr/bin/env python3
"""Checks that code Sketchware generates still compiles against the bundled libraries.

Compiles, with the same ECJ version the app runs on the device:
  * the helper classes Lx.java generates (SketchwareUtil, RequestNetworkController/OkHttp,
    GoogleMapController/Maps, BluetoothConnect, FileUtil, ...), extracted from its source;
  * the debug classes in app/src/main/assets/debug;
  * smoke/GeneratedCodeSmoke.java, which mirrors what the generators emit per component;
  * every built-in custom block a palette offers (ExtraBlocks.java, then BlocksHandler.java, in
    BlockLoader's order), filled in the way Fx fills it: numbers as doubles, the two substack
    arguments appended, and each value of a fixed menu (visibility, gravity...) in turn.
The result is then dexed with D8, like the on-device D8 path does, and the block code is checked
for platform APIs newer than the minSdk generated apps default to.

Usage: python tools/builtin-libs/verify_generated_code.py [--java-version 1.8] [--min-sdk 23]
                                                          [--include-unreachable]
Run build_builtin_libs.py first (it resolves the ECJ jar through Gradle).
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import textwrap
import zipfile
from pathlib import Path

from build_builtin_libs import ASSETS_LIBS, BUILD_TOOLS_VERSION, PLATFORM, ROOT, TOOL_DIR, android_home

PACKAGE = "com.example.smoke"
LX = ROOT / "app/src/main/java/a/a/a/Lx.java"
EXTRA_BLOCKS = ROOT / "app/src/main/java/pro/sketchware/blocks/ExtraBlocks.java"
BLOCKS_HANDLER = ROOT / "app/src/main/java/mod/hilal/saif/blocks/BlocksHandler.java"
FX = ROOT / "app/src/main/java/a/a/a/Fx.java"
UQ = ROOT / "app/src/main/java/a/a/a/uq.java"
MENU_SOURCES = [ROOT / "app/src/main/java/pro/sketchware/menu" / name
                for name in ("ExtraMenuBean.java", "DefaultExtraMenuBean.java")]
# Lx methods that return a whole generated class built by plain string concatenation.
HELPER_GENERATORS = ["b", "c", "e", "f", "g", "h"]
JAVA_STRING = re.compile(r'"((?:\\.|[^"\\])*)"|\bpackageName\b')
STRING = r'"((?:\\.|[^"\\])*)"'
ADD_BLOCK = re.compile(r"addBlock\(" + r", ".join([STRING] * 6))

# Imports every generated activity gets (Jx), plus those of the components below (mq).
IMPORTS = """
android.app.* android.os.* android.view.* android.view.View.* android.widget.* android.content.*
android.content.res.* android.graphics.* android.graphics.drawable.* android.media.* android.net.*
android.text.* android.text.style.* android.util.* android.webkit.* android.animation.*
android.view.animation.* java.io.* java.util.* java.util.regex.* java.text.* org.json.*
android.Manifest android.content.pm.PackageManager androidx.annotation.* androidx.appcompat.app.AppCompatActivity
androidx.core.content.ContextCompat androidx.core.app.ActivityCompat android.hardware.Sensor
android.hardware.SensorEvent android.hardware.SensorEventListener android.hardware.SensorManager
android.location.Location java.util.concurrent.Executor java.util.concurrent.TimeUnit
androidx.biometric.BiometricManager androidx.biometric.BiometricPrompt androidx.work.ListenableWorker
androidx.work.OneTimeWorkRequest androidx.work.PeriodicWorkRequest androidx.work.WorkManager
androidx.recyclerview.widget.RecyclerView androidx.recyclerview.widget.LinearLayoutManager
androidx.recyclerview.widget.GridLayoutManager androidx.recyclerview.widget.StaggeredGridLayoutManager
androidx.recyclerview.widget.DividerItemDecoration
com.google.android.gms.location.FusedLocationProviderClient com.google.android.gms.location.LocationCallback
com.google.android.gms.location.LocationRequest com.google.android.gms.location.LocationResult
com.google.android.gms.location.LocationServices com.google.android.gms.location.Priority
com.google.android.gms.ads.AdRequest com.google.android.gms.ads.AdSize com.google.android.gms.ads.AdView
com.google.android.gms.ads.MobileAds com.google.android.gms.ads.OnUserEarnedRewardListener
com.google.android.gms.ads.rewarded.RewardItem com.google.android.gms.ads.rewarded.RewardedAd
com.google.android.gms.ads.rewarded.RewardedAdLoadCallback com.google.android.gms.common.SignInButton
affan.ahmad.otp.OTPListener affan.ahmad.otp.OTPView com.google.android.gms.tasks.OnCompleteListener
com.google.android.gms.tasks.Task com.google.firebase.FirebaseException com.google.firebase.auth.AuthResult
com.google.firebase.auth.FirebaseAuth com.google.firebase.auth.FirebaseUser com.google.firebase.auth.GoogleAuthProvider
com.google.firebase.auth.PhoneAuthCredential com.google.firebase.auth.PhoneAuthProvider
com.google.android.gms.auth.api.signin.GoogleSignIn com.google.android.gms.auth.api.signin.GoogleSignInAccount
com.google.android.gms.auth.api.signin.GoogleSignInClient com.google.android.gms.auth.api.signin.GoogleSignInOptions
com.google.firebase.messaging.FirebaseMessaging androidx.fragment.app.Fragment androidx.fragment.app.FragmentManager
androidx.fragment.app.DialogFragment com.google.gson.Gson com.google.gson.reflect.TypeToken
com.google.android.material.textfield.* androidx.viewpager.widget.ViewPager androidx.viewpager.widget.PagerAdapter
com.google.android.gms.ads.interstitial.InterstitialAd com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer com.sayuti.lib.WaveSideBar
com.andrognito.patternlockview.PatternLockView com.andrognito.patternlockview.utils.*
androidx.swiperefreshlayout.widget.SwipeRefreshLayout com.google.android.material.floatingactionbutton.FloatingActionButton
br.tiagohm.codeview.CodeView br.tiagohm.codeview.Theme br.tiagohm.codeview.Language
""".split()

# %m.<selector> -> (field declarations, argument the block receives)
SELECTORS = {
    # Lists every widget; blocks that need a ViewGroup are meant to get a layout.
    "view": ("LinearLayout view;", "view"),
    "textinputlayout": ("TextInputLayout textinputlayout;", "textinputlayout"),
    "youtubeview": ("YouTubePlayerView youtubeview;", "youtubeview"),
    "viewpager": ("ViewPager viewpager;", "viewpager"),
    "interstitialad": ("InterstitialAd interstitialad; InterstitialAdLoadCallback _interstitialad_interstitial_ad_load_callback; "
                       "String _ad_unit_id;", "interstitialad"),
    "varMap": ("HashMap<String, Object> varMap = new HashMap<>();", "varMap"),
    "listMap": ("ArrayList<HashMap<String, Object>> listMap = new ArrayList<>();", "listMap"),
    "listInt": ("ArrayList<Double> listInt = new ArrayList<>();", "listInt"),
    "sidebar": ("WaveSideBar sidebar;", "sidebar"),
    "patternview": ("PatternLockView patternview;", "patternview"),
    "textview": ("TextView textview;", "textview"),
    "checkbox": ("CheckBox checkbox;", "checkbox"),
    "listview": ("ListView listview;", "listview"),
    "gridview": ("GridView gridview;", "gridview"),
    "videoview": ("VideoView videoview;", "videoview"),
    "ratingbar": ("RatingBar ratingbar;", "ratingbar"),
    "timepicker": ("TimePicker timepicker;", "timepicker"),
    "actv": ("AutoCompleteTextView actv;", "actv"),
    "mactv": ("MultiAutoCompleteTextView mactv;", "mactv"),
    "recyclerview": ("RecyclerView recyclerview;", "recyclerview"),
    "cardview": ("androidx.cardview.widget.CardView cardview;", "cardview"),
    "swiperefreshlayout": ("androidx.swiperefreshlayout.widget.SwipeRefreshLayout swiperefreshlayout;", "swiperefreshlayout"),
    "tablayout": ("com.google.android.material.tabs.TabLayout tablayout;", "tablayout"),
    "bottomnavigation": ("com.google.android.material.bottomnavigation.BottomNavigationView bottomnavigation;",
                         "bottomnavigation"),
    "lottie": ("com.airbnb.lottie.LottieAnimationView lottie;", "lottie"),
    "codeview": ("br.tiagohm.codeview.CodeView codeview;", "codeview"),
    "progressdialog": ("ProgressDialog progressdialog;", "progressdialog"),
    "timepickerdialog": ("TimePickerDialog timepickerdialog;", "timepickerdialog"),
    "menuitem": ("", "menuitem"),
    "submenu": ("SubMenu submenu;", "submenu"),
    "inputstream": ("java.io.InputStream inputstream;", "inputstream"),
    # Compiler-generated classes, stubbed in CONTEXT_FIELDS
    "asynctask": ("", "asynctask"),
    "fragmentAdapter": ("PagerAdapterFragment pagerAdapter;", "pagerAdapter"),
    # Resources the project has, named after files in its resource folders
    "resource": ("", "sample"),
    "image": ("", "sample"),
    "drawable": ("", "sample"),
    "menu": ("", "sample"),
    "color": ("", "0xFF2196F3"),
    "ResString": ("", "android.R.string.ok"),
    "list": ("ArrayList list = new ArrayList();", "list"),
    "listStr": ("ArrayList<String> listStr = new ArrayList<>();", "listStr"),
    "edittext": ("EditText edittext;", "edittext"),
    "recycler": ("RecyclerView recycler;", "recycler"),
    # %m.file lists SharedPreferences ("File") components; %m.File lists java.io.File custom variables.
    "File": ("java.io.File customFile;", "customFile"),
    "file": ("SharedPreferences file;", "file"),
    "imageview": ("ImageView imageview;", "imageview"),
    "intent": ("Intent intent = new Intent();", "intent"),
    "MenuItem": ("MenuItem menuitem;", "menuitem"),
    "webview": ("WebView webview;", "webview"),
    "workmanager": ("WorkManager workmanager;", "workmanager"),
    "alarmmanager": ("AlarmManager alarmmanager;", "alarmmanager"),
    "otpview": ("OTPView otpview;", "otpview"),
    "adview": ("AdView adview;", "adview"),
    "signinbutton": ("SignInButton signinbutton;", "signinbutton"),
    "videoad": ("RewardedAd videoad; RewardedAdLoadCallback _videoad_rewarded_ad_load_callback; "
                "OnUserEarnedRewardListener _videoad_on_user_earned_reward_listener; String _reward_ad_unit_id;", "videoad"),
    "biometricmanager": ("BiometricPrompt biometricmanager; BiometricManager _biometricmanager_manager;", "biometricmanager"),
    "fusedlocationmanager": ("FusedLocationProviderClient fusedlocationmanager; LocationRequest _fusedlocationmanager_location_request; "
                             "LocationCallback _fusedlocationmanager_location_callback; "
                             "void _fusedlocationmanager_start_location_updates() {} void _fusedlocationmanager_stop_location_updates() {}",
                             "fusedlocationmanager"),
    "firebaseauth": ("FirebaseAuth firebaseauth; " + " ".join(
        f"OnCompleteListener<{kind}> firebaseauth_{name}Listener;" for kind, name in [
            ("Void", "updateEmail"), ("Void", "updatePassword"), ("Void", "emailVerificationSent"),
            ("Void", "deleteUser"), ("Void", "updateProfile"), ("AuthResult", "phoneAuth"), ("AuthResult", "googleSignIn")]),
        "firebaseauth"),
    "googlelogin": ("GoogleSignInClient googlelogin;", "googlelogin"),
    "phoneauth": ("PhoneAuthProvider.OnVerificationStateChangedCallbacks phoneauth; "
                  "PhoneAuthProvider.ForceResendingToken phoneauth_resendToken;", "phoneauth"),
    "cloudmessage": ("OnCompleteListener<String> cloudmessage_onCompleteListener;", "cloudmessage"),
    # Selectors that pick a value rather than a component
    "activity": ("", "AllBlocksSmoke"),
    "SignButtonColor": ("", "COLOR_DARK"),
    "SignButtonSize": ("", "SIZE_STANDARD"),
    "Permission": ("", "android.Manifest.permission.CAMERA"),
}
for sensor in ("compass", "lightsensor", "proximitysensor", "barometer", "stepcounter"):
    SELECTORS[sensor] = (f"SensorManager {sensor}; SensorEventListener _{sensor}_sensor_listener;", sensor)
# What Fx passes for each input kind. Numbers are doubles in Sketchware (variables, and literals
# with a decimal point become "1.5d"), so templates must cast them where an int/long/float is needed.
SAMPLE_VALUES = {"s": '"sample"', "d": "1.5d", "b": "true"}
# %s.<kind> inputs whose text is inserted as code (mq: "Input"), and what to type into them
RAW_INPUTS = {"inputOnly", "inputCode", "import"}
RAW_SAMPLES = {"instanceOfOperator": "String", "repeatKnownNum": "_i", "RepeatKnownNumDescending": "_i",
               "asdBoolean": "true", "asdString": '""', "asdNumber": "0", "setCustomLetter": '{"A", "B"}'}
# Blocks only offered where the code around them provides what they use
CONTEXT_ONLY = {
    "AsyncTaskPublishProgress": "AsyncTask doInBackground",
    "notifyDataSetChanged": "custom adapter events",
    "checkboxIsChecked": "onCheckedChanged events",
    "customImport": "moved into the import list by the compiler",
    "customImport2": "moved into the import list by the compiler",
}
# Blocks palettes can show (with "show every single block") for widgets the view editor no longer offers
NO_WIDGET = {name: "BubbleLayout" for name in (
    "setBubbleColor", "setBubbleStrokeColor", "setBubbleStrokeWidth", "setBubbleCornerRadius",
    "setBubbleArrowHeight", "setBubbleArrowWidth", "setBubbleArrowPosition")}
NO_WIDGET.update((name, "BadgeView") for name in (
    "getBadgeCount", "setBadgeNumber", "setBadgeString", "setBadgeBackground", "setBadgeTextColor", "setBadgeTextSize"))
# Reporters whose value is meant for setText() rather than a String variable
CHAR_SEQUENCE_REPORTERS = {"html"}
LIST_TYPES = {"List Map": "ArrayList<HashMap<String, Object>>", "List String": "ArrayList<String>",
              "List Number": "ArrayList<Double>"}
# Variables blocks may use because of where Sketchware lets them be placed, and classes the
# compiler generates for components
CONTEXT_FIELDS = """Menu menu;
View _view;
com.google.android.material.floatingactionbutton.FloatingActionButton _fab;
java.util.List<PatternLockView.Dot> _pattern;
public static class DatePickerFragment extends DialogFragment {}
public class asynctask extends AsyncTask<String, Integer, String> {
protected String doInBackground(String... params) { return null; }
}
public class PagerAdapterFragment extends androidx.fragment.app.FragmentStatePagerAdapter {
public PagerAdapterFragment(Context context, FragmentManager manager) { super(manager); }
public void setTabCount(int tabCount) {}
@Override public int getCount() { return 0; }
@Override public Fragment getItem(int position) { return null; }
}"""
# Arguments for inputs whose value only exists where the block is offered, by input position
ARGUMENT_OVERRIDES = {name: {1: "_pattern"} for name in ("patternToString", "patternToMD5", "patternToSha1")}
JAVA_SOURCES = ROOT / "app/src/main/java"
PALETTE_ENTRY = re.compile(r'\.a\("([a-z ]?)", "(\w+)"\)')


def unescape(literal):
    return re.sub(r"\\u([0-9a-fA-F]{4})|\\(.)",
                  lambda m: chr(int(m.group(1), 16)) if m.group(1) else
                  {"n": "\n", "r": "\r", "t": "\t", "b": "\b", "f": "\f", "0": "\0"}.get(m.group(2), m.group(2)),
                  literal)


def java_format(template, arguments):
    """String.format(template, arguments...) with string arguments, as Fx calls it."""
    position = iter(range(len(arguments)))

    def substitute(match):
        if match.group(0) == "%%":
            return "%"
        if match.group(0) == "%n":
            return "\n"
        if match.group(2) != "s":
            raise ValueError(f"format specifier {match.group(0)} can't take a String argument")
        index = int(match.group(1)) - 1 if match.group(1) else next(position)
        return arguments[index]

    # %[index$][flags][width]conversion; a width alone (%1s) still takes the next ordinary argument.
    return re.sub(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*([a-zA-Z%])", substitute, template)


def palette_kinds():
    """Block name -> the shape palettes give it (which, not ExtraBlocks' own type, is what users get)."""
    kinds = {}
    for source in JAVA_SOURCES.rglob("*.java"):
        for kind, name in PALETTE_ENTRY.findall(source.read_text(encoding="utf-8", errors="ignore")):
            kinds.setdefault(name, kind)
    return kinds


def spec_tokens(spec):
    """A spec split as FB.c splits it: at spaces, and before each %."""
    return [token for token in re.split(r"\s+|(?=%)", spec) if token]


def spec_inputs(spec):
    """Inputs a block shows, as Rs creates them: only %b, %d, %m.<menu> and %s[.<kind>] are inputs."""
    return [token for token in spec_tokens(spec) if len(token) >= 2 and token[0] == "%" and token[1] in "bdms"]


def java_literal(expression):
    """The value of a Java expression made only of concatenated string literals."""
    return "".join(unescape(m.group(1)) for m in re.finditer(STRING, expression))


def custom_blocks():
    """Built-in custom blocks in BlockLoader's order: ExtraBlocks, then BlocksHandler's own.

    BlockLoader.getBlockInfo() returns the first block with a name, so a later one with the same
    name is never used.
    """
    blocks = [dict(name=name, type=kind, typeName=type_name, code=unescape(code), spec=unescape(spec),
                   source="ExtraBlocks")
              for name, kind, type_name, code, _, spec in ADD_BLOCK.findall(EXTRA_BLOCKS.read_text(encoding="utf-8"))]
    handler = BLOCKS_HANDLER.read_text(encoding="utf-8")
    start = handler.index("public static void builtInBlocks")
    for definition in handler[start:handler.index("\n    }\n", start)].split("arrayList.add(hashMap);")[:-1]:
        block = {key: java_literal(value) for key, value in
                 re.findall(r'hashMap\.put\(\s*"(\w+)",\s*((?:"(?:\\.|[^"\\])*"\s*\+?\s*)+)\)', definition)}
        block["source"] = "BlocksHandler"
        blocks.append(block)
    return blocks


def value_menus():
    """Selector name -> the values its menu offers, for menus built from fixed lists."""
    arrays = {name: re.findall(STRING, values) for name, values in re.findall(
        r"public static final String\[\] (\w+) = \{(.*?)\};", UQ.read_text(encoding="utf-8"), flags=re.S)}
    menus = {}
    for source in MENU_SOURCES:
        text = source.read_text(encoding="utf-8")
        cases = list(re.finditer(r'case ((?:"\w+"(?:,\s*)?)+)\s*(?:->|:)', text))
        for case, following in zip(cases, cases[1:] + [None]):
            body = text[case.end():following.start() if following else len(text)]
            array = re.search(r"\buq\.(\w+)\b", body)
            literal = re.search(r"createStringList\((.*?)\)\)", body, flags=re.S)
            values = arrays.get(array.group(1)) if array else re.findall(STRING, literal.group(1)) if literal else None
            if values:
                for name in re.findall(STRING, case.group(1)):
                    menus.setdefault(name, values)
    return menus


def block_statements(include_unreachable):
    """One statement per built-in custom block, from its template filled with sample arguments."""
    fields, statements, problems, unreachable = set(), [], [], []
    kinds = palette_kinds()
    menus = value_menus()
    # Opcodes Fx generates itself; their templates here are never used.
    generated_by_fx = set(re.findall(r'case "(\w+)"', FX.read_text(encoding="utf-8")))
    seen = {}
    for block in custom_blocks():
        name, own_kind, type_name, code, spec = (block.get(key, "") for key in ("name", "type", "typeName", "code", "spec"))
        if name in generated_by_fx:
            continue
        if name in seen:
            problems.append(f"{name}: the {block['source']} block is shadowed by the {seen[name]} one with the same name")
            continue
        seen[name] = block["source"]
        if name in CONTEXT_ONLY or name in NO_WIDGET:
            continue
        if name not in kinds:
            unreachable.append(name)
            if not include_unreachable:
                continue
        kind = kinds.get(name, own_kind)
        code = code.replace("\r\n", "\n").replace("\r", "\n")
        if not code:
            continue  # handled by the compiler, e.g. permission command blocks
        unsupported = [token for token in spec_tokens(spec) if token.startswith("%") and token not in spec_inputs(spec)]
        if unsupported:
            problems.append(f"{name}: {' '.join(unsupported)} in its spec is not an input, so its value is never passed")
        missing = [p for p in spec_inputs(spec) if p.startswith("%m.")
                   and p[3:] not in SELECTORS and p[3:] not in menus]
        if missing:
            problems.append(f"{name}: the verifier has no sample for {' '.join(missing)}")
            continue
        # Each input gets its sample; a menu of fixed values gets each of its values in turn.
        choices = []
        for placeholder in spec_inputs(spec):
            overrides = ARGUMENT_OVERRIDES.get(name, {})
            if len(choices) in overrides:
                choices.append([overrides[len(choices)]])
            elif placeholder.startswith("%m.") and placeholder[3:] in SELECTORS:
                declaration, argument = SELECTORS[placeholder[3:]]
                fields.update(part.strip() + (";" if not part.strip().endswith("}") else "")
                              for part in re.split(r";\s*(?=[A-Z])", declaration) if part.strip())
                choices.append([argument])
            elif placeholder.startswith("%m."):
                choices.append(menus[placeholder[3:]])
            elif placeholder[3:] in RAW_INPUTS:
                choices.append([RAW_SAMPLES.get(name, "0")])  # typed in as code, not quoted
            else:
                choices.append([SAMPLE_VALUES[placeholder[1]]])
        variants = [[values[0] for values in choices]]
        for position, values in enumerate(choices):
            variants += [variants[0][:position] + [value] + variants[0][position + 1:] for value in values[1:]]
        for variant in variants:
            try:
                statements.append((name,) + block_statement(len(statements), name, kind, type_name, code, variant))
            except (ValueError, IndexError, StopIteration) as e:
                problems.append(f"{name}: template needs more arguments than its spec has inputs ({e!r})")
                break
    return sorted(fields), statements, problems, unreachable


def block_statement(index, name, kind, type_name, code, arguments):
    """The return type of the method a block needs, and the statement it becomes with these arguments."""
    # Fx always appends the two substacks, or " " where there is none.
    code = java_format(code, arguments + [" ", " "])
    returns = "void"
    if re.match(r"\s*(case\b|default\s*:)", code):
        code = f"switch ({SAMPLE_VALUES['s'] if 'Str' in name else '1'}) {{\n{code}\n}}"
    elif re.match(r"\s*return\b", code):
        returns = "Object"  # return blocks go in more blocks and events that return a value
    elif kind in ("b", "z"):
        code = f"boolean _block{index} = {code};"
    elif kind == "d":
        code = f"double _block{index} = {code};"
    elif kind == "s":
        code = f"{'CharSequence' if name in CHAR_SEQUENCE_REPORTERS else 'String'} _block{index} = {code};"
    elif kind == "a":
        code = f"HashMap<String, Object> _block{index} = {code};"
    elif kind == "l":
        code = f"{LIST_TYPES.get(type_name, 'Object')} _block{index} = {code};"
    elif kind == "v":
        code = f"{type_name or 'Object'} _block{index} = {code};"
    elif kind == "f":
        # Cap blocks (finish, return, continue...) end a statement list.
        code = f"for (int _loop{index} = 0; _loop{index} < 1; _loop{index}++) {{ {code} }}"
    return returns, code


def all_blocks_activity(fields, statements):
    """The activity source, and which block each of its lines belongs to."""
    lines = [f"package {PACKAGE};", ""] + [f"import {i};" for i in IMPORTS] + [
        "", "public class AllBlocksSmoke extends AppCompatActivity {"] + fields + CONTEXT_FIELDS.split("\n") + [""]
    owners = {}
    for index, (name, returns, code) in enumerate(statements):
        # One method per block, so a broken template doesn't hide the ones after it.
        start = len(lines)
        lines += [f"{returns} block{index}_{name}() {{"] + code.split("\n") + ["}"]
        owners.update((line, name) for line in range(start + 1, len(lines) + 1))
    return "\n".join(lines + ["}", ""]), owners


def summarize_errors(output, owners):
    """ECJ problems as one line each, naming the block a problem in AllBlocksSmoke comes from."""
    summary = []
    for match in re.finditer(r"ERROR in (\S+) \(at line (\d+)\)\n.*?\n[ \t]*\^+\n(.+)", output):
        path, line, message = match.groups()
        where = (f"block {owners.get(int(line), '?')}" if path.endswith("AllBlocksSmoke.java")
                 else f"{Path(path).name}:{line}")
        summary.append(f"{where}: {message.strip()}")
    return list(dict.fromkeys(summary))


def generated_helper(source, method):
    start = source.index(f"public static String {method}(String packageName) {{")
    body = source[source.index("return", start):source.index(";\n    }", start)]
    return "".join(PACKAGE if m.group(0) == "packageName" else unescape(m.group(1))
                   for m in JAVA_STRING.finditer(body))


def generated_sketchware_util(source):
    """SketchwareUtil as Lx.i(packageName, false) builds it from text blocks."""
    start = source.index("public static String i(String packageName, boolean isMaterial3Enabled) {")
    end = source.index("return sketchwareUtilSource.toString();", start)
    body = re.sub(r"if \(isMaterial3Enabled\) \{\s*sketchwareUtilSource\.append\(\"\"\".*?\"\"\"\);\s*\}", "",
                  source[start:end], flags=re.S)
    parts = [f"package {PACKAGE};"]
    for block in re.findall(r'\.append\("""\n(.*?)"""\)', body, flags=re.S):
        parts.append(unescape(textwrap.dedent(block).replace("\\\n", "")))
    return "".join(parts)


def api_levels(sdk):
    """Class -> (since, {member: since}, superclasses) from the platform's api-versions.xml."""
    import xml.etree.ElementTree as ElementTree
    classes = {}
    for element in ElementTree.parse(sdk / "platforms" / PLATFORM / "data/api-versions.xml").getroot():
        since = int(float(element.get("since", "1")))
        members = {child.get("name"): int(float(child.get("since", since))) for child in element
                   if child.tag in ("method", "field")}
        parents = [child.get("name") for child in element if child.tag in ("extends", "implements")]
        classes[element.get("name")] = (since, members, parents)
    return classes


def api_level_of(classes, owner, member):
    """The API level a class, or a member looked up through its superclasses, was added in."""
    pending, visited = [owner], set()
    while pending:
        current = pending.pop(0)
        if current in visited or current not in classes:
            continue
        visited.add(current)
        since, members, parents = classes[current]
        if member is None:
            return since
        if member in members:
            return max(members[member], classes[owner][0] if owner in classes else 1)
        pending += parents
    return None


def newer_api_uses(classes_dir, sdk, min_sdk):
    """Platform APIs above min_sdk that block code uses outside an SDK_INT check, by block."""
    classes = api_levels(sdk)
    found = {}
    activity = PACKAGE.replace(".", "/") + "/AllBlocksSmoke"
    for class_file in sorted(Path(classes_dir).rglob("AllBlocksSmoke*.class")):
        disassembly = subprocess.run(["javap", "-c", "-p", "-v", str(class_file)],
                                     capture_output=True, text=True).stdout
        enclosing = re.search(r"EnclosingMethod: .*// (?:\w+)\.(block\d+_\w+)", disassembly)
        for method in re.split(r"\n  (?=\S)", disassembly):
            header = re.match(r"(?:[\w<>\[\],. ]+ )?(block\d+_\w+)\(", method.strip())
            block = header.group(1) if header else enclosing.group(1) if enclosing else None
            if not block or "android/os/Build$VERSION.SDK_INT" in method:
                continue
            references = [(owner, None) for owner in re.findall(r"// class ([\w/$]+)", method)]
            for kind, owner, name, descriptor in re.findall(
                    r"// (Method|InterfaceMethod|Field) (?:([\w/$]+)\.)?\"?([\w<>$]+)\"?:(\S+)", method):
                # javap leaves out the owner when it's the class itself
                owner = owner or class_file.stem.replace(".", "/")
                if owner.endswith("AllBlocksSmoke"):
                    owner = "android/app/Activity"
                references.append((owner, name if kind == "Field" else name + descriptor))
            for owner, member in references:
                if owner == activity:
                    owner = "android/app/Activity"
                level = api_level_of(classes, owner, member)
                if level and level > min_sdk:
                    found.setdefault(block.split("_", 1)[1], set()).add(
                        f"{owner.replace('/', '.')}{'.' + member.split('(')[0] if member else ''} (API {level})")
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-version", default="1.8")
    parser.add_argument("--min-sdk", type=int, default=23, help="minSdk generated apps default to")
    parser.add_argument("--include-unreachable", action="store_true",
                        help="also check ExtraBlocks blocks that no palette offers")
    arguments = parser.parse_args()

    graph = json.loads((TOOL_DIR / "build/builtin-libs.json").read_text(encoding="utf-8"))
    ecj = graph["onDeviceJavaCompiler"][0]
    sdk = android_home()

    work = Path(tempfile.mkdtemp(prefix="verify-code-"))
    with zipfile.ZipFile(ASSETS_LIBS / "android.jar.zip") as archive:
        archive.extractall(work)
    with zipfile.ZipFile(ASSETS_LIBS / "libs.zip") as archive:
        archive.extractall(work / "libs")

    sources = work / "src" / PACKAGE.replace(".", "/")
    sources.mkdir(parents=True)
    lx = LX.read_text(encoding="utf-8").replace("\r\n", "\n")
    for method in HELPER_GENERATORS:
        code = generated_helper(lx, method)
        name = re.search(r"public (?:final )?class (\w+)", code).group(1)
        (sources / f"{name}.java").write_text(code, encoding="utf-8")
    (sources / "SketchwareUtil.java").write_text(generated_sketchware_util(lx), encoding="utf-8")
    for debug_class in (ROOT / "app/src/main/assets/debug").glob("*.java"):
        code = debug_class.read_text(encoding="utf-8").replace("<?package_name?>", PACKAGE)
        code = code.replace("<?class_name_package?>", PACKAGE).replace("<?class_name?>", "SketchApplication")
        (sources / debug_class.name).write_text(code, encoding="utf-8")
    (sources / "GeneratedCodeSmoke.java").write_text(
        (TOOL_DIR / "smoke/GeneratedCodeSmoke.java").read_text(encoding="utf-8"), encoding="utf-8")
    fields, statements, problems, unreachable = block_statements(arguments.include_unreachable)
    activity, owners = all_blocks_activity(fields, statements)
    (sources / "AllBlocksSmoke.java").write_text(activity, encoding="utf-8")
    # The project's own R class
    (sources / "R.java").write_text(
        f"package {PACKAGE};\npublic final class R {{\n"
        + "".join(f"public static final class {kind} {{ public static final int sample = 0; }}\n"
                  for kind in ("drawable", "menu", "layout", "anim"))
        + "}\n", encoding="utf-8")
    for problem in problems:
        print(f"TEMPLATE PROBLEM {problem}")
    if unreachable:
        print(f"{len(unreachable)} custom blocks are in no palette"
              + ("" if arguments.include_unreachable else " (not checked; pass --include-unreachable)"))

    jars = sorted(str(p) for p in (work / "libs").glob("*/classes.jar"))
    classpath = os.pathsep.join([str(ASSETS_LIBS / "core-lambda-stubs.jar")] + jars)
    classes = work / "classes"
    print(f"Compiling {len(list(sources.glob('*.java')))} files ({len(statements)} block templates) "
          f"with ECJ ({Path(ecj).name}, -{arguments.java_version})")
    # On the device ECJ has no JDK, so java.* comes from android.jar. On the PC that needs
    # -bootclasspath, which ECJ only accepts below Java 9; the API surface checked is the same.
    result = subprocess.run(["java", "-Xmx768m", "-jar", ecj, f"-{arguments.java_version}", "-nowarn", "-proc:none",
                             "-maxProblems", "5000", "-bootclasspath", str(work / "android.jar"),
                             "-d", str(classes), "-cp", classpath, str(work / "src")],
                            capture_output=True, text=True)
    output = (result.stdout + result.stderr).strip()
    if result.returncode != 0 or "ERROR in" in output or problems:
        errors = summarize_errors(output, owners)
        print("\n".join(errors) if errors else output)
        print(f"COMPILE FAILED ({len(errors)} distinct errors); work directory: {work}")
        sys.exit(1)
    print("COMPILE OK")

    d8 = sdk / "build-tools" / BUILD_TOOLS_VERSION / "lib/d8.jar"
    class_files = [str(p) for p in classes.rglob("*.class")]
    dex = work / "dex"
    dex.mkdir()
    command = ["java", "-Xmx768m", "-cp", str(d8), "com.android.tools.r8.D8", "--release", "--min-api", "23",
               "--lib", str(sdk / "platforms" / PLATFORM / "android.jar"), "--output", str(dex)]
    for jar in jars:
        command += ["--classpath", jar]
    result = subprocess.run(command + class_files, capture_output=True, text=True)
    if result.returncode != 0:
        print((result.stdout + result.stderr).strip())
        print("DEX FAILED")
        sys.exit(1)
    print(f"DEX OK ({', '.join(p.name for p in dex.glob('*.dex'))}); work directory: {work}")

    # Generated apps default to minSdk 23; D8 doesn't backport framework APIs, so these crash there.
    newer = newer_api_uses(classes, sdk, arguments.min_sdk)
    for block, uses in sorted(newer.items()):
        print(f"block {block} needs a newer Android than minSdk {arguments.min_sdk}: {', '.join(sorted(uses))}")
    if newer:
        print(f"API LEVEL CHECK FAILED ({len(newer)} blocks)")
        sys.exit(1)
    print(f"API LEVEL OK (minSdk {arguments.min_sdk})")


if __name__ == "__main__":
    main()
