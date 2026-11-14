package pro.sketchware.core;

import java.util.ArrayList;

public class StringListStore extends RuntimeException {
   public ArrayList<String> a;

   public StringListStore(String var1) {
      super(var1);
   }

   public ArrayList<String> a() {
      return this.a;
   }

   public void a(ArrayList<String> var1) {
      this.a = var1;
   }
}
