package pro.sketchware.core;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.util.HashMap;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import mod.agus.jcoderz.editor.event.ManageEvent;

import pro.sketchware.filemanagers.FileCryptoUtils;
import pro.sketchware.workspace.WorkspacePathManager;

public class TranslationManager {
   public static TranslationManager a;
   public HashMap b;
   public HashMap c;
   public String d = WorkspacePathManager.l();
   public boolean e;
   public final String f = "block";
   public final String g = "root_spec";

   public TranslationManager() {
      if (this.b == null) {
         this.b = new HashMap();
      }

      if (this.c == null) {
         this.c = new HashMap();
      }

   }

   public static TranslationManager b() {
      if (a == null) {
         synchronized (TranslationManager.class) {
            if (a == null) {
               a = new TranslationManager();
            }
         }
      }
      return a;
   }

   public String a(Context var1, int var2) {
      return this.a(var1.getResources(), var2);
   }

   public String a(Context var1, int var2, Object... var3) {
      return this.a(var1.getResources(), var2, var3);
   }

   public String a(Context var1, String var2) {
      if (this.b == null) {
         this.b = new HashMap();
      }

      if (this.b.isEmpty()) {
         this.e = false;
         this.b = this.b(this.d);
      }

      if (this.c == null) {
         this.c = new HashMap();
      }

      if (this.c.isEmpty()) {
         this.a(var1);
      }

      var2 = (String)this.c.get(var2);
      String var3 = var2;
      if (var2 == null) {
         var3 = "";
      }

      return var3;
   }

   public String a(Context var1, String var2, String var3) {
      if (this.b == null) {
         this.b = new HashMap();
      }

      boolean var4 = this.b.isEmpty();
      byte var5 = 0;
      if (var4) {
         this.e = false;
         this.b = this.b(this.d);
      }

      if (this.c == null) {
         this.c = new HashMap();
      }

      if (this.c.isEmpty()) {
         this.a(var1);
      }

      label159: {
         switch (var3) {
            case "onStartTrackingTouch":
               var5 = 9;
               break label159;
            case "onSpeechError":
               var5 = 33;
               break label159;
            case "onResponse":
               var5 = 30;
               break label159;
            case "onDataReceived":
               var5 = 35;
               break label159;
            case "onProgressChanged":
               var5 = 8;
               break label159;
            case "onMapReady":
               var5 = 39;
               break label159;
            case "onClick":
            case "onDeleteSuccess":
               var5 = 24;
               break label159;
            case "onAnimationEnd":
               var5 = 12;
               break label159;
            case "onDateChange":
               var5 = 15;
               break label159;
            case "onChildChanged":
               var5 = 17;
               break label159;
            case "onLocationChanged":
               var5 = 41;
               break label159;
            case "onUploadProgress":
               var5 = 20;
               break label159;
            case "onConnectionStopped":
               var5 = 38;
               break label159;
            case "onConnectionError":
               var5 = 37;
               break label159;
            case "onAnimationStart":
               var5 = 11;
               break label159;
            case "onConnected":
               var5 = 34;
               break label159;
            case "onCheckedChange":
               var5 = 1;
               break label159;
            case "onItemSelected":
               var5 = 2;
               break label159;
            case "onPageFinished":
               var5 = 7;
               break label159;
            case "onChildRemoved":
               var5 = 18;
               break label159;
            case "onErrorResponse":
               var5 = 31;
               break label159;
            case "onUploadSuccess":
               var5 = 22;
               break label159;
            case "onAnimationCancel":
               var5 = 13;
               break label159;
            case "onBindCustomView":
               var5 = 14;
               break label159;
            case "onFailure":
               var5 = 25;
               break label159;
            case "onDataSent":
               var5 = 36;
               break label159;
            case "onChildAdded":
               var5 = 16;
               break label159;
            case "onTextChanged":
               var5 = 5;
               break label159;
            case "onCancelled":
               var5 = 19;
               break label159;
            case "onSpeechResult":
               var5 = 32;
               break label159;
            case "onItemClicked":
               var5 = 3;
               break label159;
            case "onPictureTaken":
               var5 = 26;
               break label159;
            case "onDownloadProgress":
               var5 = 21;
               break label159;
            case "onPictureTakenCancel":
               var5 = 27;
               break label159;
            case "onDownloadSuccess":
               var5 = 23;
               break label159;
            case "onStopTrackingTouch":
               var5 = 10;
               break label159;
            case "onPageStarted":
               var5 = 6;
               break label159;
            case "onFilesPickedCancel":
               var5 = 29;
               break label159;
            case "onMarkerClicked":
               var5 = 40;
               break label159;
            case "onItemLongClicked":
               var5 = 4;
               break label159;
            case "onFilesPicked":
               var5 = 28;
               break label159;
         }

         var5 = -1;
      }

      String var7;
      switch (var5) {
         case 0:
         case 1:
         case 2:
         case 3:
         case 4:
         case 5:
         case 6:
         case 7:
         case 8:
         case 9:
         case 10:
         case 11:
         case 12:
         case 13:
         case 14:
         case 15:
         case 16:
         case 17:
         case 18:
         case 19:
         case 20:
         case 21:
         case 22:
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
         case 28:
         case 29:
         case 30:
         case 31:
         case 32:
         case 33:
         case 34:
         case 35:
         case 36:
         case 37:
         case 38:
         case 39:
         case 40:
         case 41:
            String var6 = (String)this.c.get(var3);
            var3 = var6;
            if (var6 == null) {
               var3 = "";
            }

            StringBuilder var9 = new StringBuilder();
            var9.append(this.a(var1, 2131626026));
            var9.append(" ");
            var9.append(var2);
            var9.append(" ");
            var9.append(var3);
            var7 = var9.toString();
            break;
         default:
            String var10 = (String)this.c.get(var3);
            var7 = var10;
            if (var10 == null) {
               var7 = ManageEvent.i(var2, var3);
            }
      }

      return var7;
   }

   public String a(Context var1, String var2, ArrayList var3) {
      int var4 = var3.size();
      boolean var5 = true;
      byte var6 = 0;
      if (var4 > 1) {
         var4 = var3.size() - 1;
      } else {
         var4 = 0;
      }

      if (this.b == null) {
         this.b = new HashMap();
      }

      if (this.b.isEmpty()) {
         this.e = false;
         this.b = this.b(this.d);
      }

      StringBuilder var7;
      String var10;
      boolean var11;
      String var19;
      label99: {
         var7 = new StringBuilder(1024);
         StringBuilder var8 = new StringBuilder();
         var8.append(var2);
         var8.append("_head");
         var19 = var8.toString();
         StringBuilder var9 = new StringBuilder();
         var9.append(var2);
         var9.append("_tail");
         var10 = var9.toString();
         HashMap var24 = this.b;
         if (var24 != null && var24.containsKey(var19) && this.b.containsKey(var10)) {
            int var12 = 0;
            String var26;
            do {
               var11 = var5;
               if (var12 >= var4) {
                  break label99;
               }

               StringBuilder var25 = new StringBuilder();
               var25.append(var2);
               var25.append("_body_");
               ++var12;
               var25.append(var12);
               var26 = var25.toString();
            } while(this.b.containsKey(var26));
         }

         var11 = false;
      }

      if (var11) {
         var19 = (String)this.b.get(var19);
      } else {
         try {
            var19 = var1.getResources().getString(var1.getResources().getIdentifier(var19, "string", var1.getPackageName()));
         } catch (Exception var15) {
            var19 = "";
         }
      }

      String var27 = var19;
      if (var19 == null) {
         var27 = "";
      }

      var7.append(var27);
      int var29 = var6;
      if (var3.size() > 0) {
         if (var7.length() > 0) {
            var7.append(" ");
         }

         var7.append((String)var3.get(0));
         var29 = var6;
      }

      for(; var29 < var4; var7.append((String)var3.get(var29))) {
         StringBuilder var21 = new StringBuilder();
         var21.append(var2);
         var21.append("_body_");
         ++var29;
         var21.append(var29);
         String var22 = var21.toString();
         if (var11) {
            var22 = (String)this.b.get(var22);
         } else {
            try {
               var22 = var1.getResources().getString(var1.getResources().getIdentifier(var22, "string", var1.getPackageName()));
            } catch (Exception var14) {
               var22 = "";
            }
         }

         var27 = var22;
         if (var22 == null) {
            var27 = "";
         }

         if (var27.length() > 0) {
            var7.append(" ");
         }

         var7.append(var27);
         if (var7.length() > 0) {
            var7.append(" ");
         }
      }

      String var16;
      if (var11) {
         var16 = (String)this.b.get(var10);
      } else {
         try {
            var16 = var1.getResources().getString(var1.getResources().getIdentifier(var10, "string", var1.getPackageName()));
         } catch (Exception var13) {
            var16 = "";
         }
      }

      var2 = var16;
      if (var16 == null) {
         var2 = "";
      }

      if (var7.length() > 0 && var2.length() > 0) {
         var7.append(" ");
      }

      var7.append(var2);
      return var7.toString();
   }

   public String a(Resources var1, int var2) {
      String var3 = var1.getResourceEntryName(var2);
      if (this.b == null) {
         this.b = new HashMap();
      }

      try {
         if (this.b.isEmpty()) {
            this.e = false;
            this.b = this.b(this.d);
         }

         if (this.b.containsKey(var3) && this.b.get(var3) != null && ((String)this.b.get(var3)).length() > 0) {
            var3 = ((String)this.b.get(var3)).replaceAll("\\\\\\'", "'").replaceAll("\\\\\\\"", "\"").replaceAll("\\\\n", "\\\n");
            return var3;
         }
      } catch (Exception var4) {
         return var1.getString(var2);
      }

      return var1.getString(var2);
   }

   public String a(Resources var1, int var2, Object... var3) {
      String var4 = var1.getResourceEntryName(var2);
      if (this.b == null) {
         this.b = new HashMap();
      }

      boolean var5 = this.b.isEmpty();
      int var6 = 0;
      if (var5) {
         this.e = false;
         this.b = this.b(this.d);
      }

      String var7;
      label98: {
         Exception var10000;
         label86: {
            int var8;
            try {
               if (!this.b.containsKey(var4) || this.b.get(var4) == null || ((String)this.b.get(var4)).length() <= 0) {
                  return var1.getString(var2, var3);
               }

               var7 = ((String)this.b.get(var4)).replaceAll("\\\\\\'", "'").replaceAll("\\\\\\\"", "\"").replaceAll("\\\\n", "\\\n");
               var8 = var3.length;
            } catch (Exception var17) {
               var10000 = var17;
               boolean var10001 = false;
               break label86;
            }

            int var9;
            String var12;
            for(var9 = 0; var6 < var8; var7 = var12) {
               Object var10 = var3[var6];
               int var11 = var9;
               var12 = var7;

               label89: {
                  String var20;
                  try {
                     if (!var7.contains("%")) {
                        break label89;
                     }

                     var20 = var10.toString();
                  } catch (Exception var16) {
                     var10000 = var16;
                     boolean var23 = false;
                     break label86;
                  }

                  var12 = var20;

                  label63: {
                     try {
                        if (!var20.equals("\\n")) {
                           break label63;
                        }
                     } catch (Exception var15) {
                        var10000 = var15;
                        boolean var24 = false;
                        break label86;
                     }

                     var12 = "\\\\n";
                  }

                  try {
                     var12 = var7.replaceFirst("%s", var12);
                  } catch (Exception var14) {
                     var10000 = var14;
                     boolean var25 = false;
                     break label86;
                  }

                  var11 = var9 + 1;
               }

               ++var6;
               var9 = var11;
            }

            try {
               if (var9 != var3.length) {
                  return var1.getString(var2, var3);
               }

               var5 = var7.contains("%");
               break label98;
            } catch (Exception var13) {
               var10000 = var13;
               boolean var26 = false;
            }
         }

         Exception var19 = var10000;
         StringBuilder var22 = new StringBuilder();
         var22.append("Faild to load (");
         var22.append(var4);
         var22.append(")");
         Log.e("ERROR", var22.toString(), var19);
         return var1.getString(var2, var3);
      }

      if (!var5) {
         return var7;
      } else {
         return var1.getString(var2, var3);
      }
   }

   public final HashMap a(String param1) {
      HashMap<String, String> var2 = new HashMap();
      if (!new File(param1).exists()) {
         return var2;
      }

      FileInputStream var4 = null;
      InputStreamReader var3 = null;
      try {
         XmlPullParser var5 = XmlPullParserFactory.newInstance().newPullParser();
         var4 = new FileInputStream(param1);
         var3 = new InputStreamReader(var4, "UTF-8");
         var5.setInput(var3);
         int var6 = var5.getEventType();
         while (var6 != 1) {
            if (var6 == 2 && var5.getName().equals("string")) {
               var2.put(var5.getAttributeValue(null, "name"), var5.nextText());
            }

            var6 = var5.next();
         }

         this.e = true;
      } catch (Exception var7) {
         this.e = false;
      } finally {
         if (var4 != null) {
            try {
               var4.close();
            } catch (Exception ignored) {
            }
         }

         if (var3 != null) {
            try {
               var3.close();
            } catch (Exception ignored) {
            }
         }
      }

      return var2;
   }

   public HashMap a(byte[] param1) {
      HashMap<String, String> var2 = new HashMap();
      ByteArrayInputStream var5 = null;
      InputStreamReader var3 = null;
      try {
         XmlPullParser var4 = XmlPullParserFactory.newInstance().newPullParser();
         var5 = new ByteArrayInputStream(param1);
         var3 = new InputStreamReader(var5, "UTF-8");
         var4.setInput(var3);
         int var6 = var4.getEventType();
         while (var6 != 1) {
            if (var6 == 2 && var4.getName().equals("string")) {
               var2.put(var4.getAttributeValue(null, "name"), var4.nextText());
            }

            var6 = var4.next();
         }

         this.e = true;
      } catch (Exception var7) {
         this.e = false;
      } finally {
         if (var5 != null) {
            try {
               var5.close();
            } catch (Exception ignored) {
            }
         }

         if (var3 != null) {
            try {
               var3.close();
            } catch (Exception ignored) {
            }
         }
      }

      return var2;
   }

   public void a() {
      HashMap var1 = this.b;
      if (var1 != null) {
         var1.clear();
         this.b = null;
      }

      var1 = this.c;
      if (var1 != null) {
         var1.clear();
         this.c = null;
      }

   }

   public void a(Context var1) {
      this.c = new HashMap();
      this.c(var1, "initializeLogic");
      this.c(var1, "onBackPressed");
      this.c(var1, "onPostCreate");
      this.c(var1, "onStart");
      this.c(var1, "onStop");
      this.c(var1, "onDestroy");
      this.c(var1, "onResume");
      this.c(var1, "onPause");
      this.c(var1, "moreBlock");
      this.c(var1, "onClick");
      this.c(var1, "onCheckedChange");
      this.c(var1, "onItemSelected");
      this.c(var1, "onItemClicked");
      this.c(var1, "onItemLongClicked");
      this.c(var1, "onTextChanged");
      this.c(var1, "onPageStarted");
      this.c(var1, "onPageFinished");
      this.c(var1, "onProgressChanged");
      this.c(var1, "onStartTrackingTouch");
      this.c(var1, "onStopTrackingTouch");
      this.c(var1, "onAnimationStart");
      this.c(var1, "onAnimationEnd");
      this.c(var1, "onAnimationCancel");
      this.c(var1, "onBindCustomView");
      this.c(var1, "onDateChange");
      this.c(var1, "onChildAdded");
      this.c(var1, "onChildChanged");
      this.c(var1, "onChildRemoved");
      this.c(var1, "onCancelled");
      this.c(var1, "onSensorChanged");
      this.c(var1, "onCreateUserComplete");
      this.c(var1, "onSignInUserComplete");
      this.c(var1, "onResetPasswordEmailSent");
      this.c(var1, "onUploadProgress");
      this.c(var1, "onDownloadProgress");
      this.c(var1, "onUploadSuccess");
      this.c(var1, "onDownloadSuccess");
      this.c(var1, "onDeleteSuccess");
      this.c(var1, "onFailure");
      this.c(var1, "onPictureTaken");
      this.c(var1, "onPictureTakenCancel");
      this.c(var1, "onFilesPicked");
      this.c(var1, "onFilesPickedCancel");
      this.c(var1, "onAdLoaded");
      this.c(var1, "onAdFailedToLoad");
      this.c(var1, "onAdOpened");
      this.c(var1, "onAdClosed");
      this.c(var1, "onResponse");
      this.c(var1, "onErrorResponse");
      this.c(var1, "onSpeechResult");
      this.c(var1, "onSpeechError");
      this.c(var1, "onConnected");
      this.c(var1, "onDataReceived");
      this.c(var1, "onDataSent");
      this.c(var1, "onConnectionError");
      this.c(var1, "onConnectionStopped");
      this.c(var1, "onMapReady");
      this.c(var1, "onMarkerClicked");
      this.c(var1, "onLocationChanged");
      this.b(var1, "viewOnClick");
      this.b(var1, "setVarBoolean");
      this.b(var1, "setVarInt");
      this.b(var1, "increaseInt");
      this.b(var1, "decreaseInt");
      this.b(var1, "setVarString");
      this.b(var1, "mapCreateNew");
      this.b(var1, "mapPut");
      this.b(var1, "mapGet");
      this.b(var1, "mapContainKey");
      this.b(var1, "mapRemoveKey");
      this.b(var1, "mapSize");
      this.b(var1, "mapClear");
      this.b(var1, "mapIsEmpty");
      this.b(var1, "mapGetAllKeys");
      this.b(var1, "addListInt");
      this.b(var1, "insertListInt");
      this.b(var1, "deleteList");
      this.b(var1, "getAtListInt");
      this.b(var1, "indexListInt");
      this.b(var1, "lengthList");
      this.b(var1, "containListInt");
      this.b(var1, "clearList");
      this.b(var1, "addListStr");
      this.b(var1, "insertListStr");
      this.b(var1, "getAtListStr");
      this.b(var1, "indexListStr");
      this.b(var1, "containListStr");
      this.b(var1, "addListMap");
      this.b(var1, "insertListMap");
      this.b(var1, "getAtListMap");
      this.b(var1, "setListMap");
      this.b(var1, "containListMap");
      this.b(var1, "addMapToList");
      this.b(var1, "insertMapToList");
      this.b(var1, "getMapInList");
      this.b(var1, "repeat");
      this.b(var1, "forever");
      this.b(var1, "break");
      this.b(var1, "if");
      this.b(var1, "ifElse");
      this.b(var1, "else");
      this.b(var1, "true");
      this.b(var1, "false");
      this.b(var1, "<");
      this.b(var1, "=");
      this.b(var1, ">");
      this.b(var1, "&&");
      this.b(var1, "||");
      this.b(var1, "not");
      this.b(var1, "+");
      this.b(var1, "-");
      this.b(var1, "*");
      this.b(var1, "/");
      this.b(var1, "%");
      this.b(var1, "random");
      this.b(var1, "stringLength");
      this.b(var1, "stringJoin");
      this.b(var1, "stringIndex");
      this.b(var1, "stringLastIndex");
      this.b(var1, "stringSub");
      this.b(var1, "stringEquals");
      this.b(var1, "stringContains");
      this.b(var1, "stringReplace");
      this.b(var1, "stringReplaceFirst");
      this.b(var1, "stringReplaceAll");
      this.b(var1, "toNumber");
      this.b(var1, "trim");
      this.b(var1, "toUpperCase");
      this.b(var1, "toLowerCase");
      this.b(var1, "toString");
      this.b(var1, "toStringWithDecimal");
      this.b(var1, "toStringFormat");
      this.b(var1, "addSourceDirectly");
      this.b(var1, "mapToStr");
      this.b(var1, "strToMap");
      this.b(var1, "listMapToStr");
      this.b(var1, "strToListMap");
      this.b(var1, "mathGetDip");
      this.b(var1, "mathGetDisplayWidth");
      this.b(var1, "mathGetDisplayHeight");
      this.b(var1, "mathPi");
      this.b(var1, "mathE");
      this.b(var1, "mathPow");
      this.b(var1, "mathMin");
      this.b(var1, "mathMax");
      this.b(var1, "mathSqrt");
      this.b(var1, "mathAbs");
      this.b(var1, "mathRound");
      this.b(var1, "mathCeil");
      this.b(var1, "mathFloor");
      this.b(var1, "mathSin");
      this.b(var1, "mathCos");
      this.b(var1, "mathTan");
      this.b(var1, "mathAsin");
      this.b(var1, "mathAcos");
      this.b(var1, "mathAtan");
      this.b(var1, "mathExp");
      this.b(var1, "mathLog");
      this.b(var1, "mathLog10");
      this.b(var1, "mathToRadian");
      this.b(var1, "mathToDegree");
      this.b(var1, "isDrawerOpen");
      this.b(var1, "openDrawer");
      this.b(var1, "closeDrawer");
      this.b(var1, "viewOnClick");
      this.b(var1, "setEnable");
      this.b(var1, "getEnable");
      this.b(var1, "setVisible");
      this.b(var1, "setClickable");
      this.b(var1, "setText");
      this.b(var1, "setTypeface");
      this.b(var1, "getText");
      this.b(var1, "setBgColor");
      this.b(var1, "setBgResource");
      this.b(var1, "setTextColor");
      this.b(var1, "setHint");
      this.b(var1, "setHintTextColor");
      this.b(var1, "setImage");
      this.b(var1, "setColorFilter");
      this.b(var1, "requestFocus");
      this.b(var1, "setRotate");
      this.b(var1, "getRotate");
      this.b(var1, "setAlpha");
      this.b(var1, "getAlpha");
      this.b(var1, "setTranslationX");
      this.b(var1, "getTranslationX");
      this.b(var1, "setTranslationY");
      this.b(var1, "getTranslationY");
      this.b(var1, "setScaleX");
      this.b(var1, "getScaleX");
      this.b(var1, "setScaleY");
      this.b(var1, "getScaleY");
      this.b(var1, "getLocationX");
      this.b(var1, "getLocationY");
      this.b(var1, "setChecked");
      this.b(var1, "getChecked");
      this.b(var1, "setThumbResource");
      this.b(var1, "setTrackResource");
      this.b(var1, "listSetData");
      this.b(var1, "listSetCustomViewData");
      this.b(var1, "listRefresh");
      this.b(var1, "listSetItemChecked");
      this.b(var1, "listGetCheckedPosition");
      this.b(var1, "listGetCheckedPositions");
      this.b(var1, "listGetCheckedCount");
      this.b(var1, "listSmoothScrollTo");
      this.b(var1, "spnSetData");
      this.b(var1, "spnRefresh");
      this.b(var1, "spnSetSelection");
      this.b(var1, "spnGetSelection");
      this.b(var1, "webViewLoadUrl");
      this.b(var1, "webViewGetUrl");
      this.b(var1, "webViewSetCacheMode");
      this.b(var1, "webViewCanGoBack");
      this.b(var1, "webViewCanGoForward");
      this.b(var1, "webViewGoBack");
      this.b(var1, "webViewGoForward");
      this.b(var1, "webViewClearCache");
      this.b(var1, "webViewClearHistory");
      this.b(var1, "webViewStopLoading");
      this.b(var1, "webViewZoomIn");
      this.b(var1, "webViewZoomOut");
      this.b(var1, "calendarViewGetDate");
      this.b(var1, "calendarViewSetDate");
      this.b(var1, "calendarViewSetMinDate");
      this.b(var1, "calnedarViewSetMaxDate");
      this.b(var1, "adViewLoadAd");
      this.b(var1, "mapViewSetMapType");
      this.b(var1, "mapViewMoveCamera");
      this.b(var1, "mapViewZoomTo");
      this.b(var1, "mapViewZoomIn");
      this.b(var1, "mapViewZoomOut");
      this.b(var1, "mapViewAddMarker");
      this.b(var1, "mapViewSetMarkerInfo");
      this.b(var1, "mapViewSetMarkerPosition");
      this.b(var1, "mapViewSetMarkerColor");
      this.b(var1, "mapViewSetMarkerIcon");
      this.b(var1, "mapViewSetMarkerVisible");
      this.b(var1, "intentSetAction");
      this.b(var1, "intentSetData");
      this.b(var1, "intentSetScreen");
      this.b(var1, "intentPutExtra");
      this.b(var1, "intentSetFlags");
      this.b(var1, "startActivity");
      this.b(var1, "intentGetString");
      this.b(var1, "finishActivity");
      this.b(var1, "fileGetData");
      this.b(var1, "fileSetData");
      this.b(var1, "fileRemoveData");
      this.b(var1, "calendarGetNow");
      this.b(var1, "calendarAdd");
      this.b(var1, "calendarSet");
      this.b(var1, "calendarFormat");
      this.b(var1, "calendarDiff");
      this.b(var1, "calendarGetTime");
      this.b(var1, "calendarSetTime");
      this.b(var1, "vibratorAction");
      this.b(var1, "timerAfter");
      this.b(var1, "timerEvery");
      this.b(var1, "timerCancel");
      this.b(var1, "dialogSetTitle");
      this.b(var1, "dialogSetMessage");
      this.b(var1, "dialogOkButton");
      this.b(var1, "dialogCancelButton");
      this.b(var1, "dialogNeutralButton");
      this.b(var1, "dialogShow");
      this.b(var1, "dialogDismiss");
      this.b(var1, "mediaplayerCreate");
      this.b(var1, "mediaplayerStart");
      this.b(var1, "mediaplayerPause");
      this.b(var1, "mediaplayerSeek");
      this.b(var1, "mediaplayerGetCurrent");
      this.b(var1, "mediaplayerGetDuration");
      this.b(var1, "mediaplayerIsPlaying");
      this.b(var1, "mediaplayerSetLooping");
      this.b(var1, "mediaplayerIsLooping");
      this.b(var1, "mediaplayerReset");
      this.b(var1, "mediaplayerRelease");
      this.b(var1, "soundpoolCreate");
      this.b(var1, "soundpoolLoad");
      this.b(var1, "soundpoolStreamPlay");
      this.b(var1, "soundpoolStreamStop");
      this.b(var1, "objectanimatorSetTarget");
      this.b(var1, "objectanimatorSetProperty");
      this.b(var1, "objectanimatorSetValue");
      this.b(var1, "objectanimatorSetFromTo");
      this.b(var1, "objectanimatorSetDuration");
      this.b(var1, "objectanimatorSetRepeatMode");
      this.b(var1, "objectanimatorSetRepeatCount");
      this.b(var1, "objectanimatorSetInterpolator");
      this.b(var1, "objectanimatorStart");
      this.b(var1, "objectanimatorCancel");
      this.b(var1, "objectanimatorIsRunning");
      this.b(var1, "firebaseAdd");
      this.b(var1, "firebasePush");
      this.b(var1, "firebaseGetPushKey");
      this.b(var1, "firebaseDelete");
      this.b(var1, "firebaseGetChildren");
      this.b(var1, "firebaseauthCreateUser");
      this.b(var1, "firebaseauthSignInUser");
      this.b(var1, "firebaseauthSignInAnonymously");
      this.b(var1, "firebaseauthIsLoggedIn");
      this.b(var1, "firebaseauthGetCurrentUser");
      this.b(var1, "firebaseauthGetUid");
      this.b(var1, "firebaseauthResetPassword");
      this.b(var1, "firebaseauthSignOutUser");
      this.b(var1, "firebaseStartListen");
      this.b(var1, "firebaseStopListen");
      this.b(var1, "gyroscopeStartListen");
      this.b(var1, "gyroscopeStopListen");
      this.b(var1, "interstitialadCreate");
      this.b(var1, "interstitialadLoadAd");
      this.b(var1, "interstitialadShow");
      this.b(var1, "firebasestorageUploadFile");
      this.b(var1, "firebasestorageDownloadFile");
      this.b(var1, "firebasestorageDelete");
      this.b(var1, "camerastarttakepicture");
      this.b(var1, "filepickerstartpickfiles");
      this.b(var1, "requestnetworkSetParams");
      this.b(var1, "requestnetworkSetHeaders");
      this.b(var1, "requestnetworkStartRequestNetwork");
      this.b(var1, "fileutildelete");
      this.b(var1, "fileutilcopy");
      this.b(var1, "fileutilwrite");
      this.b(var1, "fileutilread");
      this.b(var1, "fileutilmove");
      this.b(var1, "fileutilisexist");
      this.b(var1, "fileutilmakedir");
      this.b(var1, "fileutillistdir");
      this.b(var1, "fileutilisdir");
      this.b(var1, "fileutilisfile");
      this.b(var1, "fileutillength");
      this.b(var1, "fileutilStartsWith");
      this.b(var1, "fileutilEndsWith");
      this.b(var1, "fileutilGetLastSegmentPath");
      this.b(var1, "doToast");
      this.b(var1, "copyToClipboard");
      this.b(var1, "setTitle");
      this.b(var1, "seekBarGetMax");
      this.b(var1, "seekBarGetProgress");
      this.b(var1, "seekBarSetMax");
      this.b(var1, "seekBarSetProgress");
      this.b(var1, "getExternalStorageDir");
      this.b(var1, "getPackageDataDir");
      this.b(var1, "getPublicDir");
      this.b(var1, "resizeBitmapFileRetainRatio");
      this.b(var1, "resizeBitmapFileToSquare");
      this.b(var1, "resizeBitmapFileToCircle");
      this.b(var1, "resizeBitmapFileWithRoundedBorder");
      this.b(var1, "cropBitmapFileFromCenter");
      this.b(var1, "rotateBitmapFile");
      this.b(var1, "scaleBitmapFile");
      this.b(var1, "skewBitmapFile");
      this.b(var1, "setBitmapFileColorFilter");
      this.b(var1, "setBitmapFileBrightness");
      this.b(var1, "setBitmapFileContrast");
      this.b(var1, "setImageFilePath");
      this.b(var1, "setImageUrl");
      this.b(var1, "getJpegRotate");
      this.b(var1, "progressBarSetIndeterminate");
      this.b(var1, "textToSpeechSetPitch");
      this.b(var1, "textToSpeechSetSpeechRate");
      this.b(var1, "textToSpeechSpeak");
      this.b(var1, "textToSpeechIsSpeaking");
      this.b(var1, "textToSpeechStop");
      this.b(var1, "textToSpeechShutdown");
      this.b(var1, "speechToTextStartListening");
      this.b(var1, "speechToTextStopListening");
      this.b(var1, "speechToTextShutdown");
      this.b(var1, "bluetoothConnectReadyConnection");
      this.b(var1, "bluetoothConnectReadyConnectionToUuid");
      this.b(var1, "bluetoothConnectStartConnection");
      this.b(var1, "bluetoothConnectStartConnectionToUuid");
      this.b(var1, "bluetoothConnectStopConnection");
      this.b(var1, "bluetoothConnectSendData");
      this.b(var1, "bluetoothConnectIsBluetoothEnabled");
      this.b(var1, "bluetoothConnectIsBluetoothActivated");
      this.b(var1, "bluetoothConnectActivateBluetooth");
      this.b(var1, "bluetoothConnectGetPairedDevices");
      this.b(var1, "bluetoothConnectGetRandomUuid");
      this.b(var1, "locationManagerRequestLocationUpdates");
      this.b(var1, "locationManagerRemoveUpdates");
   }

   public HashMap b(String var1) {
      HashMap var2 = new HashMap();
      FileCryptoUtils var3 = new FileCryptoUtils();
      JsonCompressionUtils var4 = new JsonCompressionUtils();
      if (!(new File(var1)).exists()) {
         return var2;
      } else {
         HashMap var8;
         HashMap var9;
         try {
            var9 = this.a(var4.a(var3.h(var1)));
         } catch (IllegalBlockSizeException var5) {
            var8 = this.a(var1);
            return var8;
         } catch (BadPaddingException var6) {
            var8 = this.a(var1);
            return var8;
         } catch (Exception var7) {
            var8 = var2;
            return var8;
         }

         var8 = var9;
         return var8;
      }
   }

   public void b(Context var1, String var2) {
      StringBuilder var3 = new StringBuilder();
      var3.append("block_");
      var3.append(TextFormatParser.d(var2));
      String var5 = var3.toString();
      ArrayList var4 = TextFormatParser.a(var2);
      this.c.put(var2, this.a(var1, var5, var4));
   }

   public boolean b(Context var1) {
      if (this.b == null) {
         this.b = new HashMap();
      }

      HashMap var2 = this.b;
      if (var2 != null) {
         var2.clear();
      }

      this.b = this.b(this.d);
      this.a(var1);
      boolean var3;
      if (!this.b.isEmpty() && this.e) {
         var3 = true;
      } else {
         var3 = false;
      }

      return var3;
   }

   public void c(Context var1, String var2) {
      StringBuilder var3 = new StringBuilder();
      var3.append("root_spec_");
      var3.append(TextFormatParser.c(var2));
      String var4 = var3.toString();
      ArrayList var5 = TextFormatParser.b(var2);
      this.c.put(var2, this.a(var1, var4, var5));
   }
}
