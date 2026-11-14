package pro.sketchware.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.view.View.MeasureSpec;
import android.widget.RelativeLayout;

import pro.sketchware.core.ViewInflationUtils;

public class BlockCanvasView extends RelativeLayout {
   public int A;
   public int B;
   public int C;
   public float D;
   public BlockView E;
   public int F;
   public int G;
   public int H;
   public int I;
   public int J;
   public boolean K;
   public boolean L;
   public Paint M;
   public Paint N;
   public Paint O;
   public int P;
   public int Q;
   public int R;
   public ComponentClassInfo S;
   public Context a;
   public String b;
   public String c;
   public int d;
   public int e;
   public Paint f;
   public boolean g;
   public int h;
   public int i;
   public int j;
   public int k;
   public int l;
   public int m;
   public int n;
   public int o;
   public int p;
   public int q;
   public int r;
   public int s;
   public int t;
   public int u;
   public int v;
   public int w;
   public int x;
   public int y;
   public int z;

   public BlockCanvasView(Context var1, String var2, String var3, boolean var4) {
      super(var1);
      this.h = 3;
      this.i = 12;
      this.j = 15;
      this.k = 3;
      this.l = 2;
      this.m = 15;
      this.n = 15;
      this.o = 15;
      int var5 = this.o;
      int var6 = this.h;
      this.p = var5 + var6;
      this.q = this.p + 10;
      this.r = this.q + var6;
      this.s = 6;
      this.t = 60;
      this.u = 2;
      this.v = 2;
      this.w = 3;
      this.x = 0;
      this.y = 2;
      var6 = this.i;
      this.B = var6;
      this.C = var6;
      this.E = null;
      this.F = 100;
      this.G = 14;
      this.H = 15;
      this.I = 6;
      this.J = 4;
      this.K = false;
      this.L = false;
      this.P = 1;
      this.Q = 1;
      this.R = 805306368;
      this.S = null;
      this.a = var1;
      this.b = var2;
      if (var3 != null && var3.indexOf(".") > 0) {
         this.c = var3.substring(0, var3.indexOf("."));
      } else {
         this.c = var3;
      }

      label86: {
         this.a();
         var2 = this.b;
         var6 = var2.hashCode();
         if (var6 != 32) {
            if (var6 != 104) {
               if (var6 != 112) {
                  if (var6 != 115) {
                     if (var6 != 118) {
                        switch (var6) {
                           case 97:
                              if (var2.equals("a")) {
                                 var6 = 13;
                                 break label86;
                              }
                              break;
                           case 98:
                              if (var2.equals("b")) {
                                 var6 = 1;
                                 break label86;
                              }
                              break;
                           case 99:
                              if (var2.equals("c")) {
                                 var6 = 4;
                                 break label86;
                              }
                              break;
                           case 100:
                              if (var2.equals("d")) {
                                 var6 = 2;
                                 break label86;
                              }
                              break;
                           case 101:
                              if (var2.equals("e")) {
                                 var6 = 5;
                                 break label86;
                              }
                              break;
                           case 102:
                              if (var2.equals("f")) {
                                 var6 = 6;
                                 break label86;
                              }
                              break;
                           default:
                              switch (var6) {
                                 case 108:
                                    if (var2.equals("l")) {
                                       var6 = 12;
                                       break label86;
                                    }
                                    break;
                                 case 109:
                                    if (var2.equals("m")) {
                                       var6 = 8;
                                       break label86;
                                    }
                                    break;
                                 case 110:
                                    if (var2.equals("n")) {
                                       var6 = 3;
                                       break label86;
                                    }
                              }
                        }
                     } else if (var2.equals("v")) {
                        var6 = 10;
                        break label86;
                     }
                  } else if (var2.equals("s")) {
                     var6 = 9;
                     break label86;
                  }
               } else if (var2.equals("p")) {
                  var6 = 11;
                  break label86;
               }
            } else if (var2.equals("h")) {
               var6 = 7;
               break label86;
            }
         } else if (var2.equals(" ")) {
            var6 = 0;
            break label86;
         }

         var6 = -1;
      }

      switch (var6) {
         case 0:
            this.u = 4;
            this.d = 4;
            break;
         case 1:
            this.w = 8;
            this.x = 5;
            this.d = 2;
            break;
         case 2:
            this.d = 3;
            this.w = 4;
            break;
         case 3:
            this.d = 3;
            break;
         case 4:
            this.u = 4;
            this.d = 10;
            break;
         case 5:
            this.u = 4;
            this.d = 12;
            break;
         case 6:
            this.u = 4;
            this.d = 5;
            break;
         case 7:
            this.u = 8;
            this.d = 7;
            break;
         case 8:
            this.d = 9;
            break;
         case 9:
         case 10:
         case 11:
         case 12:
         case 13:
            this.d = 1;
      }

      this.e = this.R;
      this.g = var4;
      ((RelativeLayout)this).setWillNotDraw(false);
      this.a(var1);
   }

   public BlockCanvasView(Context var1, String var2, boolean var3) {
      this(var1, var2, "", var3);
   }

   private float[] getBooleanReflections() {
      int var1 = this.A / 2;
      int var2 = this.P;
      float var3 = (float)(var2 / 2 + 0);
      float var4 = (float)var1;
      return new float[]{var3, var4, var4, (float)(var2 / 2 + 0), var4, (float)(var2 / 2 + 0), (float)(this.z - var1), (float)(var2 / 2 + 0)};
   }

   private float[] getBooleanShadows() {
      int var1 = this.A;
      int var2 = var1 / 2;
      int var3 = this.z;
      int var4 = this.P;
      float var5 = (float)(var3 - var4 / 2);
      float var6 = (float)var2;
      return new float[]{var5, var6, (float)(var3 - var2), (float)(var1 - var4 / 2), (float)(var3 - var2), (float)(var1 - var4 / 2), var6, (float)(var1 - var4 / 2)};
   }

   private float[] getNumberBottomShadows() {
      int var1 = this.A;
      int var2 = var1 / 2;
      float var3 = (float)(this.z - var2);
      int var4 = this.P;
      return new float[]{var3, (float)(var1 - var4 / 2), (float)var2, (float)(var1 - var4 / 2)};
   }

   private float[] getNumberTopReflections() {
      int var1 = this.A / 2;
      float var2 = (float)var1;
      int var3 = this.P;
      return new float[]{var2, (float)(var3 / 2 + 0), (float)(this.z - var1), (float)(var3 / 2 + 0)};
   }

   private float[] getRectReflections() {
      int var1 = this.P;
      return new float[]{0.0F, (float)(var1 / 2 + 0), (float)(this.z - var1 / 2), (float)(var1 / 2 + 0), (float)(var1 / 2 + 0), 0.0F, (float)(var1 / 2 + 0), (float)(this.A - var1 / 2)};
   }

   private float[] getRectShadows() {
      int var1 = this.z;
      int var2 = this.P;
      float var3 = (float)(var1 - var2 / 2);
      float var4 = (float)(var1 - var2 / 2);
      int var5 = this.A;
      return new float[]{var3, 0.0F, var4, (float)(var5 - var2 / 2), (float)(var1 - var2 / 2), (float)(var5 - var2 / 2), 0.0F, (float)(var5 - var2 / 2)};
   }

   public void a() {
      this.S = ComponentLibraryRegistry.a(this.b, this.c);
   }

   public void a(float var1, float var2, boolean var3) {
      if (this.d == 9) {
         this.z = (int)var1 + this.H;
      } else {
         this.z = (int)var1;
      }

      this.A = (int)var2;
      if (var3) {
         this.e();
      }

   }

   public void a(BlockCanvasView var1, boolean var2, boolean var3, int var4) {
      this.e = -16777216;
      this.d = var1.d;
      this.z = var1.z;
      this.A = var1.A;
      this.B = var1.B;
      this.C = var1.C;
      if (!var2) {
         if (var3) {
            this.d = 4;
            this.A = (int)(this.D * 6.0F);
         } else if (var4 > 0) {
            this.B = var4 - this.h;
         }
      }

      this.e();
   }

   public final void a(Context var1) {
      this.D = ViewInflationUtils.a(var1, 1.0F);
      float var2 = (float)this.h;
      float var3 = this.D;
      this.h = (int)(var2 * var3);
      this.i = (int)((float)this.i * var3);
      this.j = (int)((float)this.j * var3);
      this.m = (int)((float)this.m * var3);
      this.n = (int)((float)this.n * var3);
      this.k = (int)((float)this.k * var3);
      this.l = (int)((float)this.l * var3);
      this.o = (int)((float)this.o * var3);
      this.p = (int)((float)this.p * var3);
      this.q = (int)((float)this.q * var3);
      this.r = (int)((float)this.r * var3);
      this.s = (int)((float)this.s * var3);
      this.t = (int)((float)this.t * var3);
      this.B = (int)((float)this.B * var3);
      this.C = (int)((float)this.C * var3);
      this.w = (int)((float)this.w * var3);
      this.u = (int)((float)this.u * var3);
      this.x = (int)((float)this.x * var3);
      this.v = (int)((float)this.v * var3);
      this.y = (int)((float)this.y * var3);
      this.F = (int)((float)this.F * var3);
      this.G = (int)((float)this.G * var3);
      this.I = (int)((float)this.I * var3);
      this.J = (int)((float)this.J * var3);
      this.H = (int)((float)this.H * var3);
      this.P = (int)((float)this.P * var3);
      this.Q = (int)((float)this.Q * var3);
      if (this.P < 2) {
         this.P = 2;
      }

      if (this.Q < 2) {
         this.Q = 2;
      }

      this.f = new Paint();
      if (!this.g) {
         this.K = true;
         this.L = true;
      }

      this.M = new Paint();
      this.M.setColor(-536870912);
      this.M.setStrokeWidth((float)this.P);
      this.N = new Paint();
      this.N.setColor(-1610612736);
      this.N.setStyle(Style.STROKE);
      this.N.setStrokeWidth((float)this.P);
      this.O = new Paint();
      this.O.setColor(-1593835521);
      this.O.setStyle(Style.STROKE);
      this.O.setStrokeWidth((float)this.Q);
      ((RelativeLayout)this).setLayerType(1, (Paint)null);
      this.a((float)this.F, (float)(this.G + this.u + this.v), false);
   }

   public final void a(Canvas var1) {
      Path var2 = new Path();
      int var3 = this.A;
      int var4 = var3 / 2;
      float var5 = (float)var4;
      var2.moveTo(var5, (float)var3);
      var2.lineTo(0.0F, var5);
      var2.lineTo(var5, 0.0F);
      var2.lineTo((float)(this.z - var4), 0.0F);
      var2.lineTo((float)this.z, var5);
      var2.lineTo((float)(this.z - var4), (float)this.A);
      var1.drawPath(var2, this.f);
      if (this.K) {
         var1.drawLines(this.getBooleanShadows(), this.N);
      }

      if (this.L) {
         var1.drawLines(this.getBooleanReflections(), this.O);
      }

   }

   public final void a(Path var1) {
      var1.moveTo(0.0F, (float)this.k);
      var1.lineTo((float)this.k, 0.0F);
      var1.lineTo((float)this.o, 0.0F);
      var1.lineTo((float)this.p, (float)this.h);
      var1.lineTo((float)this.q, (float)this.h);
      var1.lineTo((float)this.r, 0.0F);
      var1.lineTo((float)(this.z - this.k), 0.0F);
      var1.lineTo((float)this.z, (float)this.k);
   }

   public final void a(Path var1, int var2) {
      var1.lineTo((float)this.j, (float)(var2 - this.l));
      float var3 = (float)(this.j + this.l);
      float var4 = (float)var2;
      var1.lineTo(var3, var4);
      var1.lineTo((float)(this.z - this.k), var4);
      var1.lineTo((float)this.z, (float)(var2 + this.k));
   }

   public final void a(Path var1, int var2, boolean var3, int var4) {
      var1.lineTo((float)this.z, (float)(var2 - this.k));
      float var5 = (float)(this.z - this.k);
      float var6 = (float)var2;
      var1.lineTo(var5, var6);
      if (var3) {
         var1.lineTo((float)(this.r + var4), var6);
         var1.lineTo((float)(this.q + var4), (float)(this.h + var2));
         var1.lineTo((float)(this.p + var4), (float)(this.h + var2));
         var1.lineTo((float)(this.o + var4), var6);
      }

      if (var4 > 0) {
         var1.lineTo((float)(this.l + var4), var6);
         var1.lineTo((float)var4, (float)(var2 + this.l));
      } else {
         var1.lineTo((float)(var4 + this.k), var6);
         var1.lineTo(0.0F, (float)(var2 - this.k));
      }

   }

   public final float[] a(int var1) {
      int var2 = this.P;
      float var3 = (float)(var2 / 2 + 0);
      int var4 = this.k;
      float var5 = (float)(var1 - var4);
      float var6 = (float)(var2 / 2 + 0);
      float var7 = (float)var4;
      float var8 = (float)(var2 / 2 + 0);
      float var9 = (float)var4;
      float var10 = (float)var4;
      float var11 = (float)(var2 / 2 + 0);
      float var12 = (float)var4;
      float var13 = (float)(var2 / 2 + 0);
      float var14 = (float)this.o;
      float var15 = (float)(var2 / 2 + 0);
      float var16 = (float)this.p;
      var1 = this.h;
      float var17 = (float)(var2 / 2 + var1);
      int var18 = this.q;
      float var19 = (float)var18;
      float var20 = (float)(var2 / 2 + var1);
      float var21 = (float)var18;
      float var22 = (float)(var1 + var2 / 2);
      var1 = this.r;
      return new float[]{var3, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var19, var20, var21, var22, (float)var1, (float)(var2 / 2 + 0), (float)var1, (float)(var2 / 2 + 0), (float)(this.z - var4), (float)(var2 / 2 + 0)};
   }

   public final float[] a(int var1, int var2) {
      int var3 = this.j;
      int var4 = this.l;
      float var5 = (float)(var3 + var4);
      int var6 = this.P;
      return new float[]{var5, (float)(var1 - var6 / 2), (float)(var3 - var6 / 2), (float)(var1 + var4), (float)(var3 - var6 / 2), (float)(var1 + var4), (float)(var3 - var6 / 2), (float)(var2 - var4)};
   }

   public final float[] a(int var1, boolean var2, int var3) {
      float[] var4;
      if (var2) {
         var4 = new float[24];
      } else {
         var4 = new float[8];
      }

      int var5 = this.z;
      var4[0] = (float)var5;
      int var6 = this.k;
      int var7 = this.P;
      var4[1] = (float)(var1 - var6 - var7 / 2);
      var4[2] = (float)(var5 - var6);
      var4[3] = (float)(var1 - var7 / 2);
      if (var2) {
         var4[4] = (float)(var5 - var6);
         var4[5] = (float)(var1 - var7 / 2);
         var5 = this.r;
         var4[6] = (float)(var3 + var5);
         var4[7] = (float)(var1 - var7 / 2);
         var4[8] = (float)(var5 + var3);
         var4[9] = (float)(var1 - var7 / 2);
         int var8 = this.q;
         var4[10] = (float)(var3 + var8);
         var5 = this.h;
         var4[11] = (float)(var1 + var5 - var7 / 2);
         var4[12] = (float)(var8 + var3);
         var4[13] = (float)(var1 + var5 - var7 / 2);
         var8 = this.p;
         var4[14] = (float)(var3 + var8);
         var4[15] = (float)(var1 + var5 - var7 / 2);
         var4[16] = (float)(var8 + var3);
         var4[17] = (float)(var5 + var1 - var7 / 2);
         var5 = this.o;
         var4[18] = (float)(var3 + var5);
         var4[19] = (float)(var1 - var7 / 2);
         if (var3 > 0) {
            var4[20] = (float)(var5 + var3);
            var4[21] = (float)(var1 - var7 / 2);
            var4[22] = (float)(var3 + this.l);
            var4[23] = (float)(var1 - var7 / 2);
         } else {
            var4[20] = (float)(var5 + var3);
            var4[21] = (float)(var1 - var7 / 2);
            var4[22] = (float)(var3 + var6);
            var4[23] = (float)(var1 - var7 / 2);
         }
      } else if (var3 > 0) {
         var4[4] = (float)(var5 - var6);
         var4[5] = (float)(var1 - var7 / 2);
         var4[6] = (float)(var3 + this.l);
         var4[7] = (float)(var1 - var7 / 2);
      } else {
         var4[4] = (float)(var5 - var6);
         var4[5] = (float)(var1 - var7 / 2);
         var4[6] = (float)(var3 + var6);
         var4[7] = (float)(var1 - var7 / 2);
      }

      return var4;
   }

   public final void b(Canvas var1) {
      Path var2 = new Path();
      this.a(var2);
      int var3 = this.A;
      int var4 = this.d;
      boolean var5 = true;
      boolean var6;
      if (var4 != 5) {
         var6 = true;
      } else {
         var6 = false;
      }

      this.a(var2, var3, var6, 0);
      var1.drawPath(var2, this.f);
      if (this.K) {
         var1.drawLines(this.b(0, this.A), this.N);
         var3 = this.A;
         if (this.d != 5) {
            var6 = var5;
         } else {
            var6 = false;
         }

         var1.drawLines(this.a(var3, var6, 0), this.N);
      }

      if (this.L) {
         var1.drawLines(this.a(this.A), this.O);
      }

   }

   public boolean b() {
      boolean var1;
      if (this.d >= 10) {
         var1 = true;
      } else {
         var1 = false;
      }

      return var1;
   }

   public final float[] b(int var1, int var2) {
      int var3 = this.z;
      int var4 = this.P;
      float var5 = (float)(var3 - var4 / 2);
      int var6 = this.k;
      return new float[]{var5, (float)(var1 + var6), (float)(var3 - var4 / 2), (float)(var2 - var6)};
   }

   public final void c(Canvas var1) {
      var1.drawRect(new Rect(0, 0, this.z, this.A), this.f);
      Path var2 = new Path();
      int var3 = this.z;
      int var4 = this.J;
      var2.moveTo((float)(var3 - var4), (float)var4);
      var4 = this.z;
      var3 = this.J;
      int var5 = this.I;
      var2.lineTo((float)(var4 - var3 - var5 / 2), (float)(var3 + var5));
      var4 = this.z;
      var3 = this.J;
      var2.lineTo((float)(var4 - var3 - this.I), (float)var3);
      var1.drawPath(var2, this.M);
   }

   public boolean c() {
      boolean var1;
      if (this.d == 12) {
         var1 = true;
      } else {
         var1 = false;
      }

      return var1;
   }

   public final float[] c(int var1, int var2) {
      float var3 = (float)(var2 + this.l);
      var2 = this.P;
      return new float[]{var3, (float)(var2 / 2 + var1), (float)(this.z - this.k), (float)(var1 + var2 / 2)};
   }

   public int d() {
      return this.getTotalHeight() - this.h;
   }

   public final void d(Canvas var1) {
      Path var2 = new Path();
      var2.moveTo(0.0F, (float)this.s);
      var2.arcTo(new RectF(0.0F, 0.0F, (float)this.t, (float)(this.s * 2)), 180.0F, 180.0F);
      var2.lineTo((float)(this.z - this.k), (float)this.s);
      var2.lineTo((float)this.z, (float)(this.s + this.k));
      this.a(var2, this.A, true, 0);
      var1.drawPath(var2, this.f);
      if (this.K) {
         var1.drawLines(this.b(this.s, this.A), this.N);
         var1.drawLines(this.a(this.A, true, 0), this.N);
      }

   }

   public void e() {
      ((RelativeLayout)this).requestLayout();
   }

   public final void e(Canvas var1) {
      Path var2 = new Path();
      int var3 = this.A;
      int var4 = this.B;
      int var5 = this.h;
      var3 = var3 + var4 - var5;
      var5 = this.n + var3 + this.C - var5;
      this.a(var2);
      this.a(var2, this.A, true, this.j);
      this.a(var2, var3);
      this.a(var2, this.n + var3, true, this.j);
      this.a(var2, var5);
      this.a(var2, this.m + var5, true, 0);
      var1.drawPath(var2, this.f);
      if (this.K) {
         var1.drawLines(this.b(0, this.A), this.N);
         var1.drawLines(this.a(this.A, true, this.j), this.N);
         var1.drawLines(this.a(this.A, var3), this.N);
         var1.drawLines(this.b(var3, this.n + var3), this.N);
         var1.drawLines(this.a(this.n + var3, true, this.j), this.N);
         var1.drawLines(this.a(this.n + var3, var5), this.N);
         var1.drawLines(this.b(var5, this.m + var5), this.N);
         var1.drawLines(this.a(this.m + var5, true, 0), this.N);
      }

      if (this.L) {
         var1.drawLines(this.a(this.m + var5), this.O);
         var1.drawLines(this.c(var3, this.j), this.O);
         var1.drawLines(this.c(var5, this.j), this.O);
      }

   }

   public int f() {
      return this.A;
   }

   public final void f(Canvas var1) {
      Path var2 = new Path();
      int var3 = this.A + this.B - this.h;
      this.a(var2);
      int var4 = this.A;
      int var5 = this.j;
      boolean var6 = true;
      this.a(var2, var4, true, var5);
      this.a(var2, var3);
      var4 = this.m;
      boolean var7;
      if (this.d == 10) {
         var7 = true;
      } else {
         var7 = false;
      }

      this.a(var2, var4 + var3, var7, 0);
      var1.drawPath(var2, this.f);
      if (this.K) {
         var1.drawLines(this.b(0, this.A), this.N);
         var1.drawLines(this.a(this.A, true, this.j), this.N);
         var1.drawLines(this.a(this.A, var3), this.N);
         var1.drawLines(this.b(var3, this.m + var3), this.N);
         var4 = this.m;
         if (this.d == 10) {
            var7 = var6;
         } else {
            var7 = false;
         }

         var1.drawLines(this.a(var4 + var3, var7, 0), this.N);
      }

      if (this.L) {
         var1.drawLines(this.a(this.m + var3), this.O);
         var1.drawLines(this.c(var3, this.j), this.O);
      }

   }

   public int g() {
      return this.A + this.B + this.n - this.h;
   }

   public final void g(Canvas var1) {
      Path var2 = new Path();
      int var3 = this.A;
      int var4 = var3 / 2;
      var2.moveTo((float)var4, (float)var3);
      var3 = this.A;
      var2.arcTo(new RectF(0.0F, 0.0F, (float)var3, (float)var3), 90.0F, 180.0F);
      var2.lineTo((float)(this.z - var4), 0.0F);
      var4 = this.z;
      var3 = this.A;
      var2.arcTo(new RectF((float)(var4 - var3), 0.0F, (float)var4, (float)var3), 270.0F, 180.0F);
      var1.drawPath(var2, this.f);
      if (this.K) {
         var3 = this.z;
         var4 = this.A;
         float var5 = (float)(var3 - var4);
         int var6 = this.P;
         var1.drawArc(new RectF(var5, 0.0F, (float)(var3 - var6 / 2), (float)(var4 - var6 / 2)), 330.0F, 120.0F, false, this.N);
         var1.drawLines(this.getNumberBottomShadows(), this.N);
         var3 = this.P;
         var5 = (float)(var3 / 2 + 0);
         var4 = this.A;
         var1.drawArc(new RectF(var5, 0.0F, (float)var4, (float)(var4 - var3 / 2)), 90.0F, 30.0F, false, this.N);
      }

      if (this.L) {
         var4 = this.P;
         float var20 = (float)(var4 / 2 + 0);
         float var7 = (float)(var4 / 2 + 0);
         var4 = this.A;
         var1.drawArc(new RectF(var20, var7, (float)var4, (float)var4), 150.0F, 120.0F, false, this.O);
         var1.drawLines(this.getNumberTopReflections(), this.O);
         var3 = this.z;
         var4 = this.A;
         var20 = (float)(var3 - var4);
         int var22 = this.P;
         var1.drawArc(new RectF(var20, (float)(var22 / 2 + 0), (float)(var3 - var22 / 2), (float)var4), 270.0F, 30.0F, false, this.O);
      }

   }

   public ComponentClassInfo getClassInfo() {
      if (this.S == null) {
         this.a();
      }

      return this.S;
   }

   public int getTopH() {
      return this.A;
   }

   public int getTotalHeight() {
      int var1 = this.A;
      int var2 = var1;
      if (this.b()) {
         var2 = var1 + (this.n + this.B - this.h);
      }

      var1 = var2;
      if (this.c()) {
         var1 = var2 + (this.m + this.C - this.h);
      }

      int var3 = this.d;
      if (var3 != 4 && var3 != 7 && var3 != 10) {
         var2 = var1;
         if (var3 != 12) {
            return var2;
         }
      }

      var2 = var1 + this.h;
      return var2;
   }

   public int getTotalWidth() {
      return this.z;
   }

   public int getW() {
      return this.z;
   }

   public final void h(Canvas var1) {
      var1.drawRect(new Rect(0, 0, this.z, this.A), this.f);
      if (this.K) {
         var1.drawLines(this.getRectShadows(), this.N);
      }

      if (this.L) {
         var1.drawLines(this.getRectReflections(), this.O);
      }

   }

   public void onDraw(Canvas var1) {
      this.f.setColor(this.e);
      switch (this.d) {
         case 1:
            this.h(var1);
            break;
         case 2:
            this.a(var1);
            break;
         case 3:
            this.g(var1);
            break;
         case 4:
         case 5:
            this.b(var1);
         case 6:
         case 8:
         default:
            break;
         case 7:
            this.d(var1);
            break;
         case 9:
            this.c(var1);
            break;
         case 10:
         case 11:
            this.f(var1);
            break;
         case 12:
            this.e(var1);
      }

      super.onDraw(var1);
   }

   public void onMeasure(int var1, int var2) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(this.getTotalWidth(), 1073741824), MeasureSpec.makeMeasureSpec(this.getTotalHeight(), 1073741824));
   }

   public void setSubstack1Height(int var1) {
      var1 = Math.max(var1, this.i);
      if (var1 != this.B) {
         this.B = var1;
      }

   }

   public void setSubstack2Height(int var1) {
      var1 = Math.max(var1, this.i);
      if (var1 != this.C) {
         this.C = var1;
      }

   }
}
