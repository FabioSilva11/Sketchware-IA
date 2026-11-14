package com.besome.sketch.editor.logic;

import pro.sketchware.editor.ComponentClassInfo;
import pro.sketchware.editor.BlockView;
import pro.sketchware.editor.BlockMenuItem;
import pro.sketchware.editor.BlockCanvasView;
import pro.sketchware.editor.ComponentLibraryRegistry;
import pro.sketchware.core.ViewInflationUtils;
import android.content.Context;
import android.graphics.Point;
import android.view.View;
import android.widget.RelativeLayout;
import com.besome.sketch.beans.BlockBean;
import java.util.ArrayList;
import java.util.Iterator;

public class BlockPane extends RelativeLayout {
   public Context a;
   public int[] b = new int[2];
   public BlockCanvasView c;
   public BlockView d;
   public ArrayList<Object[]> e = new ArrayList();
   public Object[] f = null;
   public int g = 10;
   public float h = ViewInflationUtils.a(((RelativeLayout)this).getContext(), 1.0F);
   public boolean isGameMode = false;

   public BlockPane(Context var1) {
      super(var1);
      this.a(var1);
   }

   public BlockView a(int var1) {
      return (BlockView)((RelativeLayout)this).findViewWithTag(var1);
   }

   public BlockView a(BlockView var1, int var2, int var3) {
      ((RelativeLayout)this).getLocationOnScreen(this.b);
      BlockView var4 = var1;
      if (var1.getBlockType() == 1) {
         Context var6 = ((RelativeLayout)this).getContext();
         int var5 = this.g++;
         if (var1.isGameBlock) {
            var4 = new BlockView(var6, var5, var1.T, var1.b, var1.c, var1.U, var1.e);
         } else {
            var4 = new BlockView(var6, var5, var1.T, var1.b, var1.c, var1.U);
         }
      }

      var4.pa = this;
      ((RelativeLayout)this).addView(var4);
      ((RelativeLayout)var4).setX((float)(var2 - this.b[0] - ((RelativeLayout)this).getPaddingLeft()));
      ((RelativeLayout)var4).setY((float)(var3 - this.b[1] - ((RelativeLayout)this).getPaddingTop()));
      return var4;
   }

   public BlockView a(BlockView var1, int var2, int var3, boolean var4) {
      BlockView var5;
      if (!var4) {
         var5 = this.a(var1, var2, var3);
      } else {
         ((RelativeLayout)var1).setX((float)(var2 - this.b[0] - ((RelativeLayout)this).getPaddingLeft()));
         ((RelativeLayout)var1).setY((float)(var3 - this.b[1] - ((RelativeLayout)this).getPaddingTop()));
         var5 = var1;
      }

      Object[] var6 = this.f;
      if (var6 == null) {
         var5.p().k();
         this.b();
         return var5;
      } else {
         if (var1.fa) {
            ((BlockCanvasView)var6[1]).E.a((BlockCanvasView)var6[1], var5);
         } else {
            var1 = (BlockView)var6[1];
            var2 = (Integer)var6[2];
            if (var2 != 0) {
               if (var2 != 1) {
                  if (var2 != 2) {
                     if (var2 != 3) {
                        if (var2 == 4) {
                           var1.d(var5);
                        }
                     } else {
                        var1.f(var5);
                     }
                  } else {
                     var1.e(var5);
                  }
               } else {
                  var1.c(var5);
               }
            } else {
               var1.b(var5);
            }
         }

         var5.p().k();
         this.b();
         return var5;
      }
   }

   public BlockView a(String var1) {
      return (BlockView)((RelativeLayout)this).findViewWithTag(Integer.valueOf(var1));
   }

   public final void a() {
      if (this.c == null) {
         this.c = new BlockCanvasView(this.a, " ", true);
      }

      this.c.a(10.0F, 10.0F, false);
      ((RelativeLayout)this).addView(this.c);
      this.d();
   }

   public void a(BlockView var1) {
      boolean var2 = var1.h().ga;
      boolean var3;
      if (((BlockCanvasView)var1).b() && -1 == var1.ia) {
         var3 = true;
      } else {
         var3 = false;
      }

      boolean var4 = var1.fa;
      this.a(((RelativeLayout)var1).getTag().toString(), var2, var3, var4, ((RelativeLayout)var1).getHeight(), ((BlockCanvasView)var1).f());
      this.f = null;
   }

   public void a(BlockView var1, int var2) {
      while(true) {
         if (var1 != null) {
            ((RelativeLayout)var1).setVisibility(var2);

            for(View var4 : var1.V) {
               if (var4 instanceof BlockView) {
                  this.a((BlockView)var4, var2);
               }
            }

            if (((BlockCanvasView)var1).b()) {
               int var5 = var1.ia;
               if (var5 != -1) {
                  this.a((BlockView)((RelativeLayout)this).findViewWithTag(var5), var2);
               }
            }

            if (((BlockCanvasView)var1).c()) {
               int var6 = var1.ja;
               if (var6 != -1) {
                  this.a((BlockView)((RelativeLayout)this).findViewWithTag(var6), var2);
               }
            }

            int var7 = var1.ha;
            if (var7 != -1) {
               var1 = (BlockView)((RelativeLayout)this).findViewWithTag(var7);
               continue;
            }
         }

         return;
      }
   }

   public final void a(BlockView var1, String var2) {
      while(true) {
         if (var1 != null) {
            if (!var1.ea) {
               for(int var3 = 0; var3 < var1.V.size(); ++var3) {
                  View var4 = (View)var1.V.get(var3);
                  boolean var5 = var4 instanceof BlockView;
                  if ((var5 || var4 instanceof BlockMenuItem) && (!var5 || !var4.getTag().toString().equals(var2))) {
                     int[] var6 = new int[2];
                     var4.getLocationOnScreen(var6);
                     this.a(var6, var4, 0);
                     if (var5) {
                        this.a((BlockView)var4, var2);
                     }
                  }
               }
            }

            int var7 = var1.ia;
            if (var7 != -1) {
               this.a((BlockView)((RelativeLayout)this).findViewWithTag(var7), var2);
            }

            var7 = var1.ja;
            if (var7 != -1) {
               this.a((BlockView)((RelativeLayout)this).findViewWithTag(var7), var2);
            }

            var7 = var1.ha;
            if (var7 != -1) {
               var1 = (BlockView)((RelativeLayout)this).findViewWithTag(var7);
               continue;
            }
         }

         return;
      }
   }

   public void a(BlockView var1, ArrayList var2) {
      if (var2.size() <= 0) {
         this.a(var1);
      } else {
         BlockBean var3;
         BlockBean var4;
         int var10;
         label64: {
            var3 = (BlockBean)var2.get(0);
            var4 = (BlockBean)var2.get(var2.size() - 1);
            String var9 = var3.type;
            var10 = var9.hashCode();
            if (var10 != 97) {
               if (var10 != 98) {
                  if (var10 != 100) {
                     if (var10 != 108) {
                        if (var10 != 112) {
                           if (var10 != 115) {
                              if (var10 == 118 && var9.equals("v")) {
                                 var10 = 3;
                                 break label64;
                              }
                           } else if (var9.equals("s")) {
                              var10 = 1;
                              break label64;
                           }
                        } else if (var9.equals("p")) {
                           var10 = 4;
                           break label64;
                        }
                     } else if (var9.equals("l")) {
                        var10 = 5;
                        break label64;
                     }
                  } else if (var9.equals("d")) {
                     var10 = 2;
                     break label64;
                  }
               } else if (var9.equals("b")) {
                  var10 = 0;
                  break label64;
               }
            } else if (var9.equals("a")) {
               var10 = 6;
               break label64;
            }

            var10 = -1;
         }

         boolean var6;
         switch (var10) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
               var6 = true;
               break;
            default:
               var6 = false;
         }

         boolean var7 = var4.type.equals("f");
         boolean var8;
         if ((var3.type.equals("c") || var3.type.equals("e")) && var3.subStack1 <= 0) {
            var8 = true;
         } else {
            var8 = false;
         }

         this.a(var3.id, var7, var8, var6, ((RelativeLayout)var1).getHeight(), ((BlockCanvasView)var1).f());
         this.f = null;
      }
   }

   public final void a(BlockView var1, boolean var2) {
      while(true) {
         if (((RelativeLayout)var1).getVisibility() != 8) {
            if (!var1.ga && (!var2 || -1 == var1.ha)) {
               int[] var3 = new int[2];
               ((RelativeLayout)var1).getLocationOnScreen(var3);
               var3[1] += ((BlockCanvasView)var1).d();
               this.a(var3, var1, 0);
            }

            if (((BlockCanvasView)var1).b() && (!var2 || var1.ia == -1)) {
               int[] var5 = new int[2];
               ((RelativeLayout)var1).getLocationOnScreen(var5);
               var5[0] += var1.j;
               var5[1] += ((BlockCanvasView)var1).f();
               this.a(var5, var1, 2);
            }

            if (((BlockCanvasView)var1).c() && (!var2 || var1.ja == -1)) {
               int[] var6 = new int[2];
               ((RelativeLayout)var1).getLocationOnScreen(var6);
               var6[0] += var1.j;
               var6[1] += ((BlockCanvasView)var1).g();
               this.a(var6, var1, 3);
            }

            int var4 = var1.ia;
            if (var4 != -1) {
               this.a((BlockView)((RelativeLayout)this).findViewWithTag(var4), var2);
            }

            var4 = var1.ja;
            if (var4 != -1) {
               this.a((BlockView)((RelativeLayout)this).findViewWithTag(var4), var2);
            }

            var4 = var1.ha;
            if (var4 != -1) {
               var1 = (BlockView)((RelativeLayout)this).findViewWithTag(var4);
               continue;
            }
         }

         return;
      }
   }

   public final void a(Context var1) {
      this.a = var1;
      this.a();
   }

   public void a(BlockBean var1, boolean var2) {
      String var3 = var1.id;
      if (var3 != null && !var3.equals("") && !var1.id.equals("0")) {
         BlockView var4 = (BlockView)((RelativeLayout)this).findViewWithTag(Integer.valueOf(var1.id));
         if (var4 == null) {
            return;
         }

         BlockView var5 = var4.E;
         if (var4 != var5) {
            this.c(var4);
            ((RelativeLayout)this).removeView(var4);
         } else {
            ((RelativeLayout)this).removeView(var4);
         }

         if (var2 && var5 != null) {
            var5.p().k();
         }
      }

   }

   public void a(String var1, String var2) {
      this.d = new BlockView(((RelativeLayout)this).getContext(), 0, var1, "h", var2);
      BlockView var4 = this.d;
      var4.pa = this;
      ((RelativeLayout)this).addView(var4);
      float var3 = ViewInflationUtils.a(((RelativeLayout)this).getContext(), 1.0F);
      var4 = this.d;
      var3 *= 8.0F;
      ((RelativeLayout)var4).setX(var3);
      this.d.setY(var3);
   }

   public void a(String var1, boolean var2, boolean var3, boolean var4, int var5, int var6) {
      this.e = new ArrayList();
      int var7 = (int)(this.h * 3.0F);

      for(int var8 = 0; var8 < ((RelativeLayout)this).getChildCount(); ++var8) {
         View var9 = ((RelativeLayout)this).getChildAt(var8);
         if (var9 instanceof BlockView) {
            BlockView var12 = (BlockView)var9;
            if (((RelativeLayout)var12).getVisibility() != 8 && var12.E == null) {
               if (var4) {
                  this.a(var12, var1);
               } else if (!var12.fa) {
                  boolean var10 = true;
                  if (!var2 && !var12.ea) {
                     int[] var11 = new int[2];
                     ((RelativeLayout)var12).getLocationOnScreen(var11);
                     var11[1] -= var5 - var7;
                     this.a(var11, var12, 1);
                  }

                  if (var3 && !var12.ea) {
                     int[] var13 = new int[2];
                     ((RelativeLayout)var12).getLocationOnScreen(var13);
                     var13[0] -= var12.j;
                     var13[1] -= var6 - var7;
                     this.a(var13, var12, 4);
                  }

                  if (!var2 || var3) {
                     var10 = false;
                  }

                  this.a(var12, var10);
               }
            }
         }
      }

   }

   public final void a(int[] var1, View var2, int var3) {
      this.e.add(new Object[]{var1, var2, var3});
   }

   public final boolean a(BlockView var1, View var2) {
      if (this.isGameMode) {
         if (!var1.fa) {
            return true;
         }

         if (!(var2 instanceof BlockCanvasView)) {
            return false;
         }

         String draggedType = var1.b != null ? var1.b : "";
         String targetType;
         if (var2 instanceof BlockView) {
            targetType = ((BlockView)var2).qa != null ? ((BlockView)var2).qa : ((BlockCanvasView)var2).b;
         } else {
            targetType = ((BlockCanvasView)var2).b;
         }

         if ("b".equals(targetType)) {
            return "b".equals(draggedType);
         }

         if ("d".equals(targetType) || "n".equals(targetType)) {
            return "d".equals(draggedType) || "n".equals(draggedType) || "s".equals(draggedType);
         }

         if ("s".equals(targetType)) {
            return "s".equals(draggedType) || "d".equals(draggedType) || "n".equals(draggedType) || "b".equals(draggedType);
         }

         return false;
      }

      boolean var3;
      if (!var1.fa) {
         var3 = true;
      } else {
         if (var2 instanceof BlockCanvasView) {
            if (((BlockCanvasView)var2).c.equals("!")) {
               var3 = true;
               return var3;
            }

            ComponentClassInfo var5 = ((BlockCanvasView)var1).getClassInfo();
            if (var5 == null) {
               var3 = false;
               return var3;
            }

            ComponentClassInfo var4 = ((BlockCanvasView)var2).getClassInfo();
            if (var4 == null) {
               var3 = false;
               return var3;
            }

            if (var5.a(var4)) {
               var3 = true;
               return var3;
            }

            if (var2 instanceof BlockView && var5.a(ComponentLibraryRegistry.b(((BlockView)var2).ra))) {
               var3 = true;
               return var3;
            }
         }

         var3 = false;
      }

      return var3;
   }

   public void b() {
      int var1 = ((RelativeLayout)this).getChildCount();
      int var2 = ((RelativeLayout)this).getLayoutParams().width;
      int var3 = ((RelativeLayout)this).getLayoutParams().width;

      int var7;
      for(int var4 = 0; var4 < var1; var3 = var7) {
         View var5 = ((RelativeLayout)this).getChildAt(var4);
         int var6 = var2;
         var7 = var3;
         if (var5 instanceof BlockView) {
            float var8 = var5.getX();
            BlockView var9 = (BlockView)var5;
            var6 = Math.max((int)(var8 + (float)var9.getWidthSum()) + 150, var2);
            var7 = Math.max((int)(var5.getY() + (float)var9.getHeightSum()) + 150, var3);
         }

         ++var4;
         var2 = var6;
      }

      ((RelativeLayout)this).getLayoutParams().width = var2;
      ((RelativeLayout)this).getLayoutParams().height = var3;
   }

   public void b(BlockView var1) {
      this.c(var1);
      Iterator var2 = var1.getAllChildren().iterator();

      while(var2.hasNext()) {
         ((RelativeLayout)this).removeView((BlockView)var2.next());
      }

   }

   public boolean b(String var1) {
      int var2 = ((RelativeLayout)this).getChildCount();

      for(int var3 = 0; var3 < var2; ++var3) {
         View var4 = ((RelativeLayout)this).getChildAt(var3);
         if (var4 instanceof BlockView) {
            BlockBean var5;
            byte var6;
            label110: {
               var5 = ((BlockView)var4).getBean();
               switch (var5.opCode) {
                  case "listSetData":
                     var6 = 15;
                     break label110;
                  case "getAtListInt":
                     var6 = 9;
                     break label110;
                  case "getAtListMap":
                     var6 = 21;
                     break label110;
                  case "getAtListStr":
                     var6 = 10;
                     break label110;
                  case "clearList":
                     var6 = 5;
                     break label110;
                  case "getVar":
                     var6 = 0;
                     break label110;
                  case "setListMap":
                     var6 = 23;
                     break label110;
                  case "strToListMap":
                     var6 = 17;
                     break label110;
                  case "insertListInt":
                     var6 = 18;
                     break label110;
                  case "insertListMap":
                     var6 = 22;
                     break label110;
                  case "insertListStr":
                     var6 = 19;
                     break label110;
                  case "containListInt":
                     var6 = 2;
                     break label110;
                  case "containListMap":
                     var6 = 4;
                     break label110;
                  case "containListStr":
                     var6 = 3;
                     break label110;
                  case "listSetCustomViewData":
                     var6 = 16;
                     break label110;
                  case "spnSetData":
                     var6 = 14;
                     break label110;
                  case "indexListInt":
                     var6 = 11;
                     break label110;
                  case "indexListStr":
                     var6 = 12;
                     break label110;
                  case "lengthList":
                     var6 = 1;
                     break label110;
                  case "listMapToStr":
                     var6 = 6;
                     break label110;
                  case "deleteList":
                     var6 = 13;
                     break label110;
                  case "addListInt":
                     var6 = 7;
                     break label110;
                  case "addListMap":
                     var6 = 20;
                     break label110;
                  case "addListStr":
                     var6 = 8;
                     break label110;
               }

               var6 = -1;
            }

            switch (var6) {
               case 0:
                  if (var5.spec.equals(var1)) {
                     return true;
                  }
                  break;
               case 1:
               case 2:
               case 3:
               case 4:
               case 5:
               case 6:
                  if (((String)var5.parameters.get(0)).equals(var1)) {
                     return true;
                  }
                  break;
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
                  if (((String)var5.parameters.get(1)).equals(var1)) {
                     return true;
                  }
                  break;
               case 18:
               case 19:
               case 20:
               case 21:
                  if (((String)var5.parameters.get(2)).equals(var1)) {
                     return true;
                  }
                  break;
               case 22:
               case 23:
                  if (((String)var5.parameters.get(3)).equals(var1)) {
                     return true;
                  }
            }
         }
      }

      return false;
   }

    public Object[] b(BlockView var1, int var2, int var3) {
       byte threshold;
       if (var1.fa) {
          threshold = 40;
       } else {
          threshold = 60;
       }

       int bestScore = 100000;
       Object[] bestEntry = null;
       Point origin = new Point(var2, var3);

       for(int idx = 0; idx < this.e.size(); ++idx) {
          Object[] entry = this.e.get(idx);
          int[] pos = (int[])entry[0];
          Point rel = new Point(origin.x - pos[0], origin.y - pos[1]);
          int dist = Math.abs(rel.x / 2) + Math.abs(rel.y);
          if (dist < bestScore && dist < threshold && this.a(var1, (View)entry[1])) {
             bestEntry = entry;
             bestScore = dist;
          }
       }

       return bestEntry;
    }

   public void c() {
      this.d();
      this.e = new ArrayList();
      this.f = null;
   }

   public void c(BlockView var1) {
      BlockView var2 = var1.E;
      if (var2 != null) {
         if (var2 != null) {
            var2.g(var1);
            var1.E = null;
         }

      }
   }

   public void c(BlockView var1, int var2, int var3) {
      ((RelativeLayout)this).getLocationOnScreen(this.b);
      this.f = this.b(var1, var2, var3);
      boolean var4 = ((BlockCanvasView)var1).b();
      boolean var5 = true;
      if (var4 && -1 == var1.ia) {
         Object[] var6 = this.f;
         if (var6 != null) {
            BlockView var7 = (BlockView)var6[1];
            var2 = (Integer)var6[2];
            if (var2 != 0) {
               if (var2 != 2) {
                  if (var2 == 3) {
                     var7 = (BlockView)((RelativeLayout)this).findViewWithTag(var7.ja);
                  }
               } else {
                  var7 = (BlockView)((RelativeLayout)this).findViewWithTag(var7.ia);
               }
            } else {
               var7 = (BlockView)((RelativeLayout)this).findViewWithTag(var7.ha);
            }
         }
      }

      Object[] var11 = this.f;
      if (var11 != null) {
         int[] var16 = (int[])var11[0];
         View var12 = (View)var11[1];
         this.c.setX((float)(var16[0] - this.b[0]));
         this.c.setY((float)(var16[1] - this.b[1]));
         this.c.bringToFront();
         this.c.setVisibility(0);
         if (var1.fa) {
            if (var12 instanceof BlockView) {
               this.c.a((BlockView)var12, true, false, 0);
            }

            if (var12 instanceof BlockMenuItem) {
               this.c.a((BlockMenuItem)var12, true, false, 0);
            }
         } else {
            var3 = (Integer)this.f[2];
            if (var3 == 4) {
               var2 = ((BlockView)var12).getHeightSum();
            } else {
               var2 = 0;
            }

            if (var3 == 1 || var3 == 4) {
               var5 = false;
            }

            this.c.a(var1, false, var5, var2);
         }
      } else {
         this.d();
      }

   }

   public boolean c(String var1) {
      int var2 = ((RelativeLayout)this).getChildCount();

      for(int var3 = 0; var3 < var2; ++var3) {
         View var4 = ((RelativeLayout)this).getChildAt(var3);
         if (var4 instanceof BlockView) {
            byte var6;
             BlockBean var7;
            label95: {
               var7 = ((BlockView)var4).getBean();
               switch (var7.opCode) {
                  case "mapIsEmpty":
                     var6 = 13;
                     break label95;
                  case "setVarBoolean":
                     var6 = 1;
                     break label95;
                  case "getAtListMap":
                     var6 = 19;
                     break label95;
                  case "decreaseInt":
                     var6 = 5;
                     break label95;
                  case "getVar":
                     var6 = 0;
                     break label95;
                  case "mapGet":
                     var6 = 8;
                     break label95;
                  case "mapPut":
                     var6 = 7;
                     break label95;
                  case "insertListMap":
                     var6 = 16;
                     break label95;
                  case "mapClear":
                     var6 = 12;
                     break label95;
                  case "mapToStr":
                     var6 = 17;
                     break label95;
                  case "mapGetAllKeys":
                     var6 = 14;
                     break label95;
                  case "mapContainKey":
                     var6 = 9;
                     break label95;
                  case "setVarInt":
                     var6 = 2;
                     break label95;
                  case "mapCreateNew":
                     var6 = 6;
                     break label95;
                  case "increaseInt":
                     var6 = 4;
                     break label95;
                  case "mapSize":
                     var6 = 11;
                     break label95;
                  case "setVarString":
                     var6 = 3;
                     break label95;
                  case "mapRemoveKey":
                     var6 = 10;
                     break label95;
                  case "strToMap":
                     var6 = 18;
                     break label95;
                  case "addListMap":
                     var6 = 15;
                     break label95;
               }

               var6 = -1;
            }

            switch (var6) {
               case 0:
                  if (var7.spec.equals(var1)) {
                     return true;
                  }
                  break;
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
                  if (((String)var7.parameters.get(0)).equals(var1)) {
                     return true;
                  }
                  break;
               case 18:
                  if (((String)var7.parameters.get(1)).equals(var1)) {
                     return true;
                  }
                  break;
               case 19:
                  if (((String)var7.parameters.get(2)).equals(var1)) {
                     return true;
                  }
            }
         }
      }

      return false;
   }

   public void d() {
      this.c.setVisibility(8);
   }

   public int getAddTargetId() {
      Object[] var1 = this.getNearestTarget();
      byte var2 = -1;
      int var3 = var2;
      if (var1 != null) {
         var3 = var2;
         if (var1[2] != null) {
            int var4 = (Integer)var1[2];
            if (var4 != 0 && var4 != 2 && var4 != 3) {
               var3 = var2;
               if (var4 != 5) {
                  return var3;
               }
            }

            var3 = var2;
            if (var1[1] != null) {
               View var5 = (View)var1[1];
               var3 = var2;
               if (var5 instanceof BlockView) {
                  BlockView var6 = (BlockView)var5;
                  if (var6.fa) {
                     var3 = (Integer)var6.E.getTag();
                  } else {
                     var3 = (Integer)((BlockView)var1[1]).getTag();
                  }
               }

               if (var5 instanceof BlockMenuItem) {
                  var3 = (Integer)((BlockMenuItem)var5).E.getTag();
               }
            }
         }
      }

      return var3;
   }

   public ArrayList getBlocks() {
      ArrayList var1 = new ArrayList();
      BlockView var2 = (BlockView)((RelativeLayout)this).findViewWithTag(this.d.ha);
      if (var2 != null) {
         Iterator var3 = var2.getAllChildren().iterator();

         while(var3.hasNext()) {
            var1.add(((BlockView)var3.next()).getBean());
         }
      }

      return var1;
   }

   public Object[] getNearestTarget() {
      return this.f;
   }

   public BlockView getRoot() {
      return this.d;
   }
}
