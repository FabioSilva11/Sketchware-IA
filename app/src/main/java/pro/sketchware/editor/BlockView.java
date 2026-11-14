package pro.sketchware.editor;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.editor.logic.BlockPane;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Iterator;
import mod.agus.jcoderz.editor.event.ManageEvent;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.project.ProjectTracker;

import pro.sketchware.core.TranslationManager;
import pro.sketchware.filemanagers.FileTextUtils;

public class BlockView extends BlockCanvasView {
   public String T;
   public String U;
   public ArrayList<View> V;
   public int W = 30;
   public int aa = 50;
   public int ba = 90;
   public int ca = 90;
   public int da = 4;
   public boolean ea = false;
   public boolean fa = false;
   public boolean ga = false;
   public int ha = -1;
   public int ia = -1;
   public int ja = -1;
   public ArrayList<View> ka = new ArrayList();
   public ArrayList<String> la = new ArrayList();
   public TextView ma = null;
   public int na = 0;
   public int oa = 0;
   public BlockPane pa = null;
   public String qa;
   public String ra;
   private String spec2 = "";
   public boolean isGameBlock = false;
   public int gameColor = 0;

   public BlockView(Context var1, int var2, String var3, String var4, String var5) {
      super(var1, var4, false);
      ((RelativeLayout)this).setTag(var2);
      this.T = var3;
      this.U = var5;
      this.l();
   }

   public BlockView(Context var1, int var2, String var3, String var4, String var5, String var6) {
      super(var1, var4, var5, false);
      ((RelativeLayout)this).setTag(var2);
      this.T = var3;
      this.U = var6;
      this.l();
   }

   public BlockView(Context var1, int var2, String var3, String var4, String var5, String var6, int var7) {
      super(var1, var4, var5, false);
      ((RelativeLayout)this).setTag(var2);
      this.T = var3;
      this.U = var6;
      this.isGameBlock = true;
      this.gameColor = var7;
      this.l();
   }

   public final int a(TextView var1) {
      Rect var2 = new Rect();
      var1.getPaint().getTextBounds(var1.getText().toString(), 0, var1.getText().length(), var2);
      return var2.width();
   }

   public final TextView a(String var1) {
      String var2;
      TextView var3;
      label15: {
         var3 = new TextView(super.a);
         if (!this.U.equals("getVar")) {
            var2 = var1;
            if (!this.U.equals("getArg")) {
               break label15;
            }
         }

         String var4 = super.c;
         var2 = var1;
         if (var4 != null) {
            var2 = var1;
            if (var4.length() > 0) {
               StringBuilder var6 = new StringBuilder();
               var6.append(super.c);
               var6.append(" : ");
               var6.append(var1);
               var2 = var6.toString();
            }
         }
      }

      var3.setText(var2);
      var3.setTextSize(10.0F);
      var3.setPadding(0, 0, 0, 0);
      var3.setGravity(16);
      var3.setTextColor(-1);
      var3.setTypeface((Typeface)null, 1);
      RelativeLayout.LayoutParams var5 = new RelativeLayout.LayoutParams(-2, super.G);
      var5.setMargins(0, 0, 0, 0);
      var3.setLayoutParams(var5);
      return var3;
   }

   public final void a(BlockView var1) {
      if (((BlockCanvasView)this).b() && -1 == this.ia) {
         this.e(var1);
      } else {
         BlockView var2 = this.h();
         var2.ha = (Integer)((RelativeLayout)var1).getTag();
         var1.E = var2;
      }

   }

   public void a(BlockCanvasView var1, BlockView var2) {
      int var3 = this.ka.indexOf(var1);
      if (var3 >= 0) {
         boolean var4;
         label25: {
            var4 = var1 instanceof BlockView;
            String var6;
            if (var4) {
               BlockView var5 = (BlockView)var1;
               var2.qa = var5.qa;
               var6 = var5.ra;
            } else {
               if (!(var1 instanceof BlockMenuItem)) {
                  break label25;
               }

               var2.qa = var1.b;
               var6 = var1.c;
            }

            var2.ra = var6;
         }

         if (!var4) {
            ((RelativeLayout)this).removeView(var1);
         }

         this.ka.set(var3, var2);
         var2.E = this;
         this.i();
         this.o();
         if (var1 != var2 && var4) {
            var1.E = null;
            ((RelativeLayout)var1).setX(((RelativeLayout)this).getX() + (float)this.getWidthSum() + 10.0F);
            ((RelativeLayout)var1).setY(((RelativeLayout)this).getY() + 5.0F);
            ((BlockView)var1).k();
         }
      }

   }

   public final void a(String var1, int var2) {
      ArrayList var4 = FileTextUtils.c(var1);
      this.ka = new ArrayList();
      this.la = new ArrayList();

      for(int var3 = 0; var3 < var4.size(); ++var3) {
         View var5 = this.b((String)var4.get(var3), var2);
         if (var5 instanceof BlockCanvasView) {
            ((BlockCanvasView)var5).E = this;
         }

         this.ka.add(var5);
         if (var5 instanceof BlockMenuItem) {
            var1 = (String)var4.get(var3);
         } else {
            var1 = "icon";
         }

         if (var5 instanceof TextView) {
            var1 = "label";
         }

         this.la.add(var1);
      }

   }

   public final View b(String var1, int var2) {
      Object var5;
      if (var1.length() >= 2 && var1.charAt(0) == '%') {
         var2 = var1.charAt(1);
         String var3 = "";
         if (var2 == 98) {
            var5 = new BlockMenuItem(super.a, "b", "");
            return (View)var5;
         }

         if (var2 == 100) {
            var5 = new BlockMenuItem(super.a, "d", "");
            return (View)var5;
         }

         if (var2 == 109) {
            var5 = new BlockMenuItem(super.a, "m", var1.substring(3));
            return (View)var5;
         }

         if (var2 == 115) {
            Context var4 = super.a;
            if (var1.length() > 2) {
               var3 = var1.substring(3);
            }

            var5 = new BlockMenuItem(var4, "s", var3);
            return (View)var5;
         }
      }

      var5 = this.a(FileTextUtils.d(var1));
      return (View)var5;
   }

   public void b(BlockView var1) {
      View var2 = this.pa.findViewWithTag(this.ha);
      if (var2 != null) {
         ((BlockView)var2).E = null;
      }

      var1.E = this;
      this.ha = (Integer)((RelativeLayout)var1).getTag();
      if (var2 != null) {
         var1.a((BlockView)var2);
      }

   }

   public void c(BlockView var1) {
      ((RelativeLayout)var1).setX(((RelativeLayout)this).getX());
      ((RelativeLayout)var1).setY(((RelativeLayout)this).getY() - (float)var1.getHeightSum() + (float)super.h);
      var1.h().b(this);
   }

   public void d(BlockView var1) {
      ((RelativeLayout)var1).setX(((RelativeLayout)this).getX() - (float)super.j);
      ((RelativeLayout)var1).setY(((RelativeLayout)this).getY() - (float)((BlockCanvasView)this).f());
      super.E = var1;
      var1.ia = (Integer)((RelativeLayout)this).getTag();
   }

   public void e(BlockView var1) {
      View var2 = this.pa.findViewWithTag(this.ia);
      if (var2 != null) {
         ((BlockView)var2).E = null;
      }

      var1.E = this;
      this.ia = (Integer)((RelativeLayout)var1).getTag();
      if (var2 != null) {
         var1.a((BlockView)var2);
      }

   }

   public void f(BlockView var1) {
      View var2 = this.pa.findViewWithTag(this.ja);
      if (var2 != null) {
         ((BlockView)var2).E = null;
      }

      var1.E = this;
      this.ja = (Integer)((RelativeLayout)var1).getTag();
      if (var2 != null) {
         var1.a((BlockView)var2);
      }

   }

   public void g(BlockView var1) {
      if (this.ha == (Integer)((RelativeLayout)var1).getTag()) {
         this.ha = -1;
      }

      if (this.ia == (Integer)((RelativeLayout)var1).getTag()) {
         this.ia = -1;
      }

      if (this.ja == (Integer)((RelativeLayout)var1).getTag()) {
         this.ja = -1;
      }

      if (var1.fa) {
         int var2 = this.ka.indexOf(var1);
         if (var2 < 0) {
            return;
         }

         var1.qa = "";
         var1.ra = "";
         View var3 = this.b((String)this.la.get(var2), super.e);
         if (var3 instanceof BlockCanvasView) {
            ((BlockCanvasView)var3).E = this;
         }

         this.ka.set(var2, var3);
         ((RelativeLayout)this).addView(var3);
         this.i();
         this.o();
      }

      this.p().k();
   }

   public ArrayList<BlockView> getAllChildren() {
      ArrayList<BlockView> var3 = new ArrayList<BlockView>();
      BlockView var2 = this;

      while(true) {
         var3.add(var2);

         for(View var5 : var2.ka) {
            if (var5 instanceof BlockView) {
               var3.addAll(((BlockView)var5).getAllChildren());
            }
         }

         if (((BlockCanvasView)var2).b()) {
            int var1 = var2.ia;
            if (var1 != -1) {
               var3.addAll(((BlockView)this.pa.findViewWithTag(var1)).getAllChildren());
            }
         }

         if (((BlockCanvasView)var2).c()) {
            int var6 = var2.ja;
            if (var6 != -1) {
               var3.addAll(((BlockView)this.pa.findViewWithTag(var6)).getAllChildren());
            }
         }

         int var7 = var2.ha;
         if (var7 == -1) {
            return var3;
         }

         var2 = (BlockView)this.pa.findViewWithTag(var7);
      }
   }

   public BlockBean getBean() {
      BlockBean var3 = new BlockBean(((RelativeLayout)this).getTag().toString(), this.T, super.b, super.c, this.U);
      var3.color = super.e;

      for(View var1 : this.V) {
         ArrayList var2;
         String var6;
         if (var1 instanceof BlockMenuItem) {
            var2 = var3.parameters;
            var6 = ((BlockMenuItem)var1).getArgValue().toString();
         } else {
            if (!(var1 instanceof BlockView)) {
               continue;
            }

            var2 = var3.parameters;
            StringBuilder var5 = new StringBuilder();
            var5.append("@");
            var5.append(var1.getTag().toString());
            var6 = var5.toString();
         }

         var2.add(var6);
      }

      var3.subStack1 = this.ia;
      var3.subStack2 = this.ja;
      var3.nextBlock = this.ha;
      return var3;
   }

   public int getBlockType() {
      return this.oa;
   }

   public int getDepth() {
      int var1 = 0;
      BlockView var2 = this;

      while(true) {
         var2 = var2.E;
         if (var2 == null) {
            return var1;
         }

         ++var1;
      }
   }

   public int getHeightSum() {
      int var1 = 0;
      BlockView var3 = this;

      while(true) {
         int var2 = var1;
         if (var1 > 0) {
            var2 = var1 - super.h;
         }

         var1 = var2 + ((BlockCanvasView)var3).getTotalHeight();
         var2 = var3.ha;
         if (var2 == -1) {
            return var1;
         }

         var3 = (BlockView)this.pa.findViewWithTag(var2);
      }
   }

   public int getWidthSum() {
      int var2 = 0;
      BlockView var4 = this;

      while(true) {
         var2 = Math.max(var2, ((BlockCanvasView)var4).getW());
         int var1 = var2;
         if (((BlockCanvasView)var4).b()) {
            int var3 = var4.ia;
            var1 = var2;
            if (var3 != -1) {
               var1 = super.j;
               var1 = Math.max(var2, ((BlockView)this.pa.findViewWithTag(var3)).getWidthSum() + var1);
            }
         }

         var2 = var1;
         if (((BlockCanvasView)var4).c()) {
            int var9 = var4.ja;
            var2 = var1;
            if (var9 != -1) {
               var2 = super.j;
               var2 = Math.max(var1, ((BlockView)this.pa.findViewWithTag(var9)).getWidthSum() + var2);
            }
         }

         var1 = var4.ha;
         if (var1 == -1) {
            return var2;
         }

         var4 = (BlockView)this.pa.findViewWithTag(var1);
      }
   }

   public BlockView h() {
      BlockView var2 = this;

      while(true) {
         int var1 = var2.ha;
         if (var1 == -1) {
            return var2;
         }

         var2 = (BlockView)this.pa.findViewWithTag(var1);
      }
   }

   public final void i() {
      this.V = new ArrayList();

      for(int var1 = 0; var1 < this.ka.size(); ++var1) {
         View var2 = (View)this.ka.get(var1);
         if (var2 instanceof BlockView || var2 instanceof BlockMenuItem) {
            this.V.add(var2);
         }
      }

   }

   public final void j() {
      TextView var1 = this.ma;
      if (var1 != null) {
         var1.bringToFront();
         this.ma.setX((float)super.w);
         this.ma.setY((float)(((BlockCanvasView)this).g() - super.n));
      }

   }

   public void k() {
      ((RelativeLayout)this).bringToFront();
      int var3 = super.w;

      for(int var5 = 0; var5 < this.ka.size(); ++var5) {
         View var7 = (View)this.ka.get(var5);
         var7.bringToFront();
         boolean var6 = var7 instanceof BlockView;
         float var1;
         if (var6) {
            var1 = ((RelativeLayout)this).getX() + (float)var3;
         } else {
            var1 = (float)var3;
         }

         var7.setX(var1);
         int var4;
         if (((String)this.la.get(var5)).equals("label")) {
            var4 = this.a((TextView)var7);
         } else {
            var4 = 0;
         }

         if (var7 instanceof BlockMenuItem) {
            var4 = ((BlockMenuItem)var7).getW();
         }

         if (var6) {
            var4 = ((BlockView)var7).getWidthSum();
         }

         var3 += var4 + this.da;
         if (var6) {
            var1 = ((RelativeLayout)this).getY();
            float var2 = (float)super.u;
            var4 = this.na;
            BlockView var8 = (BlockView)var7;
            var7.setY(var1 + var2 + (float)((var4 - var8.na - 1) * super.y));
            var8.k();
         } else {
            var7.setY((float)(super.u + this.na * super.y));
         }
      }

      int var15;
      label81: {
         if (!super.b.equals("b") && !super.b.equals("d") && !super.b.equals("s")) {
            var15 = var3;
            if (!super.b.equals("a")) {
               break label81;
            }
         }

         var15 = Math.max(var3, this.W);
      }

      int var19;
      label74: {
         if (!super.b.equals(" ") && !super.b.equals("")) {
            var19 = var15;
            if (!super.b.equals("f")) {
               break label74;
            }
         }

         var19 = Math.max(var15, this.aa);
      }

      label68: {
         if (!super.b.equals("c")) {
            var3 = var19;
            if (!super.b.equals("e")) {
               break label68;
            }
         }

         var3 = Math.max(var19, this.ca);
      }

      var15 = var3;
      if (super.b.equals("h")) {
         var15 = Math.max(var3, this.ba);
      }

      ((BlockCanvasView)this).a((float)(super.x + var15), (float)(super.u + super.G + this.na * super.y * 2 + super.v), true);
      if (((BlockCanvasView)this).b()) {
         var3 = super.i;
         var15 = this.ia;
         if (var15 > -1) {
            BlockView var20 = (BlockView)this.pa.findViewWithTag(var15);
            ((RelativeLayout)var20).setX(((RelativeLayout)this).getX() + (float)super.j);
            ((RelativeLayout)var20).setY(((RelativeLayout)this).getY() + (float)((BlockCanvasView)this).f());
            ((RelativeLayout)var20).bringToFront();
            var20.k();
            var3 = var20.getHeightSum();
         }

         ((BlockCanvasView)this).setSubstack1Height(var3);
         var3 = super.i;
         var15 = this.ja;
         if (var15 > -1) {
            BlockView var21 = (BlockView)this.pa.findViewWithTag(var15);
            ((RelativeLayout)var21).setX(((RelativeLayout)this).getX() + (float)super.j);
            ((RelativeLayout)var21).setY(((RelativeLayout)this).getY() + (float)((BlockCanvasView)this).g());
            ((RelativeLayout)var21).bringToFront();
            var21.k();
            var3 = var21.getHeightSum();
            if (var21.h().ga) {
               var3 += super.h;
            }
         }

         ((BlockCanvasView)this).setSubstack2Height(var3);
         this.j();
      }

      var3 = this.ha;
      if (var3 > -1) {
         BlockView var22 = (BlockView)this.pa.findViewWithTag(var3);
         ((RelativeLayout)var22).setX(((RelativeLayout)this).getX());
         ((RelativeLayout)var22).setY(((RelativeLayout)this).getY() + (float)((BlockCanvasView)this).d());
         ((RelativeLayout)var22).bringToFront();
         var22.k();
      }

   }

   public void l() {
      byte var3;
      label128: {
         label127: {
            var3 = (byte)0;
            ((View)this).setDrawingCacheEnabled(false);
            float var1 = (float)this.W;
            float var2 = super.D;
            this.W = (int)(var1 * var2);
            this.aa = (int)((float)this.aa * var2);
            this.ba = (int)((float)this.ba * var2);
            this.ca = (int)((float)this.ca * var2);
            this.da = (int)((float)this.da * var2);
            String var6 = super.b;
            int var4 = var6.hashCode();
            if (var4 != 32) {
               if (var4 != 104) {
                  if (var4 != 108) {
                     if (var4 != 112) {
                        if (var4 != 115) {
                           if (var4 != 118) {
                              switch (var4) {
                                 case 97:
                                    if (var6.equals("a")) {
                                       var3 = (byte)7;
                                       break label127;
                                    }
                                    break;
                                 case 98:
                                    if (var6.equals("b")) {
                                       var3 = (byte)1;
                                       break label128;
                                    }
                                    break;
                                 case 99:
                                    if (var6.equals("c")) {
                                       var3 = (byte)8;
                                       break label127;
                                    }
                                    break;
                                 case 100:
                                    if (var6.equals("d")) {
                                       var3 = (byte)3;
                                       break label127;
                                    }
                                    break;
                                 case 101:
                                    if (var6.equals("e")) {
                                       var3 = (byte)9;
                                       break label127;
                                    }
                                    break;
                                 case 102:
                                    if (var6.equals("f")) {
                                       var3 = (byte)10;
                                       break label127;
                                    }
                              }
                           } else if (var6.equals("v")) {
                              var3 = (byte)4;
                              break label127;
                           }
                        } else if (var6.equals("s")) {
                           var3 = (byte)2;
                           break label127;
                        }
                     } else if (var6.equals("p")) {
                        var3 = (byte)5;
                        break label127;
                     }
                  } else if (var6.equals("l")) {
                     var3 = (byte)6;
                     break label127;
                  }
               } else if (var6.equals("h")) {
                  var3 = (byte)11;
                  break label127;
               }
            } else if (var6.equals(" ")) {
               break label128;
            }

            var3 = (byte)-1;
         }

         var3 = (byte)var3;
      }

      switch (var3) {
         case 1:
         case 2:
         case 3:
         case 4:
         case 5:
         case 6:
         case 7:
            this.fa = true;
         case 8:
         case 9:
         default:
            break;
         case 10:
            this.ga = true;
            break;
         case 11:
            this.ea = true;
      }

      int var5 = BlockOpcodeUtils.a(this.U, super.b);
      if (this.isGameBlock) {
         if (this.T == null) {
            this.T = "";
         }

         this.setSpec(this.T);
         super.e = this.gameColor != 0 ? this.gameColor : var5;
         return;
      }

      if (!this.ea && !this.U.equals("definedFunc") && !this.U.equals("getVar") && !this.U.equals("getResStr") && !this.U.equals("getArg") && var5 != -7711273) {
         this.T = TranslationManager.b().a(((View)this).getContext(), this.U);
      }

      if (var5 == -7711273) {
         ExtraBlockInfo var7 = BlockLoader.getBlockInfo(this.U);
         ExtraBlockInfo var11 = var7;
         if (ProjectTracker.SC_ID != null) {
            var11 = var7;
            if (var7.isMissing) {
               var11 = var7;
               if (!ProjectTracker.SC_ID.equals("")) {
                  var11 = BlockLoader.getBlockFromProject(ProjectTracker.SC_ID, this.U);
                  BlockLoader.log("BlockView:returned block: ".concat((new Gson()).toJson(var11)));
               }
            }
         }

         this.spec2 = var11.getSpec2();
         if (this.T.equals("")) {
            this.T = var11.getSpec();
         }

         if (this.T.equals("")) {
            this.T = ManageEvent.h(this.U);
         }

         this.setSpec(this.T);
         var3 = (byte)var11.getColor();
         int var10 = var11.getPaletteColor();
         if (!this.U.equals("definedFunc") && !this.U.equals("getVar") && !this.U.equals("getResStr") && !this.U.equals("getArg")) {
            if (var3 != 0) {
               super.e = var3;
               return;
            }

            if (var10 != 0) {
               super.e = var10;
               return;
            }
         }
      } else {
         if (this.T.equals("")) {
            this.T = ManageEvent.h(this.U);
         }

         this.setSpec(this.T);
      }

      super.e = var5;
   }

   public void m() {
      BlockView var1 = this;

      BlockView var2;
      do {
         var1.n();
         var2 = var1.E;
         var1 = var2;
      } while(var2 != null);

   }

   public void n() {
      int var1 = super.w;

      int var2;
      int var4;
      for(int var3 = 0; var3 < this.ka.size(); var1 += var2 + var4) {
         View var5 = (View)this.ka.get(var3);
         if (((String)this.la.get(var3)).equals("label")) {
            var2 = this.a((TextView)var5);
         } else {
            var2 = 0;
         }

         if (var5 instanceof BlockMenuItem) {
            var2 = ((BlockMenuItem)var5).getW();
         }

         if (var5 instanceof BlockView) {
            var2 = ((BlockView)var5).getWidthSum();
         }

         var4 = this.da;
         ++var3;
      }

      label57: {
         if (!super.b.equals("b") && !super.b.equals("d") && !super.b.equals("s")) {
            var2 = var1;
            if (!super.b.equals("a")) {
               break label57;
            }
         }

         var2 = Math.max(var1, this.W);
      }

      label50: {
         if (!super.b.equals(" ") && !super.b.equals("")) {
            var1 = var2;
            if (!super.b.equals("o")) {
               break label50;
            }
         }

         var1 = Math.max(var2, this.aa);
      }

      label44: {
         if (!super.b.equals("c")) {
            var2 = var1;
            if (!super.b.equals("e")) {
               break label44;
            }
         }

         var2 = Math.max(var1, this.ca);
      }

      var1 = var2;
      if (super.b.equals("h")) {
         var1 = Math.max(var2, this.ba);
      }

      TextView var12 = this.ma;
      var2 = var1;
      if (var12 != null) {
         var2 = super.w;
         var2 = Math.max(var1, var12.getWidth() + var2 + 2);
      }

      ((BlockCanvasView)this).a((float)(super.x + var2), (float)(super.u + super.G + this.na * super.y * 2 + super.v), false);
   }

   public void o() {
      for(BlockView var2 = this; var2 != null; var2 = var2.E) {
         Iterator var3 = var2.V.iterator();
         int var1 = 0;

         while(var3.hasNext()) {
            View var4 = (View)var3.next();
            if (var4 instanceof BlockView) {
               var1 = Math.max(var1, ((BlockView)var4).na + 1);
            }
         }

         var2.na = var1;
         var2.n();
         if (!var2.fa) {
            break;
         }
      }

   }

   public BlockView p() {
      BlockView var1 = this;

      while(true) {
         BlockView var2 = var1.E;
         if (var2 == null) {
            return var1;
         }

         var1 = var2;
      }
   }

   public void setBlockType(int var1) {
      this.oa = var1;
   }

   public void setSpec(String var1) {
      this.T = var1;
      this.removeAllViews();
      this.a(this.T, super.e);
      Iterator var2 = this.ka.iterator();

      while(var2.hasNext()) {
         this.addView((View)var2.next());
      }

      this.i();
      if (super.b.equals("e") && this.U.equals("ifElse")) {
         this.ma = this.a(TranslationManager.b().a(this.getContext(), "else"));
         this.addView(this.ma);
      }

      if (super.b.equals("e") && !this.spec2.equals("")) {
         this.ma = this.a(this.spec2);
         this.addView(this.ma);
      }

      this.k();
   }
}
