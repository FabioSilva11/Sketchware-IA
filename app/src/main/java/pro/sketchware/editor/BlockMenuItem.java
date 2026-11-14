package pro.sketchware.editor;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class BlockMenuItem extends BlockCanvasView {
   public Context T;
   public Object U = "";
   public TextView V;
   public TextView W;
   public int aa = 20;
   public int ba = 4;
   public int ca = 2;
   public int da = 0;
   public int ea = 0;

   public BlockMenuItem(Context var1, String var2, String var3) {
      super(var1, var2, var3, true);
      this.T = var1;
      this.initMenu(var1);
   }

   private void initMenu(Context var1) {
      int var6;
      label68: {
         String var5 = super.b;
         var6 = var5.hashCode();
         if (var6 != 98) {
            if (var6 != 100) {
               if (var6 != 115) {
                  if (var6 != 109) {
                     if (var6 == 110 && var5.equals("n")) {
                        var6 = 2;
                        break label68;
                     }
                  } else if (var5.equals("m")) {
                     var6 = 4;
                     break label68;
                  }
               } else if (var5.equals("s")) {
                  var6 = 3;
                  break label68;
               }
            } else if (var5.equals("d")) {
               var6 = 1;
               break label68;
            }
         } else if (var5.equals("b")) {
            var6 = 0;
            break label68;
         }

         var6 = -1;
      }

      if (var6 != 0) {
         if (var6 != 1) {
            if (var6 != 2) {
               if (var6 != 3) {
                  if (var6 == 4) {
                     super.e = 805306368;
                  }
               } else {
                  super.e = -1;
               }
            } else {
               super.e = -3155748;
            }
         } else {
            super.e = -657931;
         }
      } else {
         super.e = 1342177280;
         this.aa = 25;
      }

      float var3 = (float)this.aa;
      float var4 = super.D;
      this.aa = (int)(var3 * var4);
      this.ba = (int)((float)this.ba * var4);
      this.ca = (int)((float)this.ca * var4);
      this.da = this.ca;
      if (super.b.equals("m") && this.a(super.c).length() >= 0) {
         this.W = this.b(super.c);
         ((RelativeLayout)this).addView(this.W);
         this.da = this.getDropdownTypeWidth();
      }

      if (super.b.equals("m") || super.b.equals("d") || super.b.equals("n") || super.b.equals("s")) {
         this.V = this.c("");
         ((RelativeLayout)this).addView(this.V);
      }

      ((BlockCanvasView)this).a((float)(this.aa + this.da), (float)super.G, false);
   }

   private int getDropdownTypeWidth() {
      Rect var1 = new Rect();
      TextPaint var2 = this.W.getPaint();
      String var3 = this.a(super.c);
      ((Paint)var2).getTextBounds(var3, 0, var3.length(), var1);
      return var1.width() + this.ca * 2;
   }

   private int getLabelWidth() {
      Rect var1 = new Rect();
      this.V.getPaint().getTextBounds(this.V.getText().toString(), 0, this.V.getText().length(), var1);
      return var1.width() + this.ba;
   }

   public final String a(String var1) {
      String var2 = BlockOpcodeUtils.b(var1);
      var1 = var2;
      if (var2.length() > 0) {
         StringBuilder var4 = new StringBuilder();
         var4.append(var2);
         var4.append(" : ");
         var1 = var4.toString();
      }

      return var1;
   }

   public final TextView b(String var1) {
      TextView var2 = new TextView(this.T);
      var2.setText(this.a(var1));
      var2.setTextSize(8.0F);
      var2.setTypeface((Typeface)null, 1);
      RelativeLayout.LayoutParams var4 = new RelativeLayout.LayoutParams(-2, super.G);
      int var3 = this.ca;
      var4.setMargins(var3, 0, var3, 0);
      var2.setPadding(0, 0, 0, 0);
      var2.setLayoutParams(var4);
      var2.setBackgroundColor(0);
      var2.setSingleLine();
      var2.setGravity(17);
      var2.setTextColor(-1);
      return var2;
   }

   public final TextView c(String var1) {
      TextView var2 = new TextView(this.T);
      var2.setText(var1);
      var2.setTextSize(9.0F);
      RelativeLayout.LayoutParams var3 = new RelativeLayout.LayoutParams(this.aa, super.G);
      var3.setMargins(this.da, 0, this.ea, 0);
      var2.setPadding(0, 0, 0, 0);
      var2.setLayoutParams(var3);
      var2.setBackgroundColor(0);
      var2.setSingleLine();
      var2.setGravity(17);
      if (!super.b.equals("m")) {
         var2.setTextColor(-268435456);
      } else {
         var2.setTextColor(-251658241);
      }

      return var2;
   }

   public Object getArgValue() {
      return !super.b.equals("d") && !super.b.equals("m") && !super.b.equals("s") ? this.U : this.V.getText();
   }

   public String getMenuName() {
      return super.c;
   }

   public void setArgValue(Object var1) {
      this.U = var1;
      if (super.b.equals("d") || super.b.equals("m") || super.b.equals("s")) {
         this.V.setText(var1.toString());
         int var2 = Math.max(this.aa, this.getLabelWidth());
         this.V.getLayoutParams().width = var2;
         ((BlockCanvasView)this).a((float)(var2 + this.da), (float)super.G, true);
      }

   }
}
