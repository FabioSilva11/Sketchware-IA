package pro.sketchware.core;

import com.google.gson.Gson;
import java.util.HashMap;

public class MapJsonCodec {
   public static String a(HashMap var0) {
      return (new Gson()).toJson(var0);
   }

   public static HashMap a(String var0) {
      return (HashMap)(new Gson()).fromJson(var0, (new MapStringObjectTypeToken()).getType());
   }
}
