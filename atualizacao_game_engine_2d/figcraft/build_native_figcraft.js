const fs = require('fs');
const path = require('path');

// ============================================================================
// Gerador FigCraft (.figcraft) 100% Fiel aos Layouts XML Validados do Sketchware-IA
// Sem reinventar a roda: reutiliza design.xml, DesignDrawer (design_drawer_item.xml),
// view_editor.xml, palette_widget.xml, view_property.xml, property_grid_item.xml,
// event_grid_item.xml, property_popup_hybrid.xml, property_popup_choice_attribute.xml,
// image_picker_item.xml, property.xml, property_input_item.xml, fr_logic_list.xml,
// fr_logic_list_item.xml, logic_editor.xml e dialog_new_project_type.xml.
// ============================================================================

let idCounter = 100;
function nextId(prefix = 'node') {
  return `${prefix}-${++idCounter}`;
}

function solidFill(hex, opacity = 1) {
  return [{ type: 'SOLID', color: hex, opacity, visible: true }];
}

function stroke(hex, weight = 1, align = 'INSIDE') {
  return [{ color: hex, weight, align, cap: 'ROUND', join: 'ROUND', visible: true }];
}

function shadow(color = 'rgba(0,0,0,0.14)', x = 0, y = 4, blur = 12, spread = 0) {
  return [{ type: 'DROP_SHADOW', color, offset: { x, y }, blur, spread, visible: true }];
}

function baseNode(type, name, x, y, width, height, extra = {}) {
  return {
    id: nextId(type.toLowerCase()),
    name,
    type,
    x,
    y,
    width,
    height,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    fills: [],
    strokes: [],
    effects: [],
    cornerRadius: 0,
    children: [],
    ...extra,
  };
}

function rect(name, x, y, w, h, fillHex, radius = 0, extra = {}) {
  return baseNode('RECTANGLE', name, x, y, w, h, {
    fills: fillHex ? solidFill(fillHex, extra.fillOpacity ?? 1) : [],
    cornerRadius: radius,
    ...extra,
  });
}

function frame(name, x, y, w, h, fillHex = '#FAF8FF', radius = 0, extra = {}) {
  return baseNode('FRAME', name, x, y, w, h, {
    fills: fillHex ? solidFill(fillHex, extra.fillOpacity ?? 1) : [],
    cornerRadius: radius,
    clipContent: true,
    ...extra,
  });
}

function ellipse(name, x, y, w, h, fillHex, extra = {}) {
  return baseNode('ELLIPSE', name, x, y, w, h, {
    fills: fillHex ? solidFill(fillHex, extra.fillOpacity ?? 1) : [],
    ...extra,
  });
}

function text(name, str, x, y, size = 12, color = '#1B1B21', weight = '400', extra = {}) {
  const lines = String(str).split('\n');
  const maxLen = Math.max(...lines.map((l) => l.length), 1);
  const w = extra.width || Math.max(24, Math.ceil(maxLen * size * 0.58));
  const h = extra.height || Math.ceil(lines.length * size * 1.35);
  return baseNode('TEXT', name, x, y, w, h, {
    fills: solidFill(color),
    text: String(str),
    fontSize: size,
    fontFamily: 'Roboto, Inter, sans-serif',
    fontWeight: String(weight),
    textAlignHorizontal: extra.align || 'LEFT',
    lineHeight: extra.lineHeight || Math.round(size * 1.3),
    letterSpacing: 0,
    ...extra,
  });
}

// Cores Oficiais de app/src/main/res/values/m3_colors.xml e colors.xml
const M3 = {
  primary: '#575992',
  onPrimary: '#FFFFFF',
  primaryContainer: '#E1E0FF',
  onPrimaryContainer: '#13144B',
  secondary: '#5C5D72',
  secondaryContainer: '#E1E0F9',
  onSecondaryContainer: '#191A2C',
  surface: '#FBF8FF',
  surfaceDim: '#DBD9E0',
  surfaceBright: '#FBF8FF',
  surfaceContainerLowest: '#FFFFFF',
  surfaceContainerLow: '#F5F2FA',
  surfaceContainer: '#EFEDF4',
  surfaceContainerHigh: '#E9E7EF',
  surfaceContainerHighest: '#E4E1E9',
  onSurface: '#1B1B21',
  onSurfaceVariant: '#46464F',
  outline: '#777680',
  outlineVariant: '#C8C5D0',
  error: '#BA1A1A',
  coolGreen: '#4CAF50',
  scrim: '#000000',
};

// Cores Oficiais do Scratch 3.0 para os blocos no LogicEditorActivity
const SCRATCH = {
  motion: '#4C97FF',
  looks: '#9966FF',
  sound: '#CF63CF',
  events: '#FFBF00',
  control: '#FFAB19',
  sensing: '#5CB1D6',
  operators: '#59C059',
  variables: '#FF8C1A',
  myblocks: '#FF6680',
  pen: '#0FBD8C',
};

// ============================================================================
// COMPONENTES REUTILIZÁVEIS FIÉIS AOS XMLs DO SKETCHWARE-IA
// ============================================================================

// 1. Android Status Bar + AppBarLayout de design.xml (Toolbar + TabLayout 4 abas)
function addDesignAppBar(parent, activeTabIndex = 0, projectTitle = 'SuperCatJump', scId = '608') {
  // Status Bar (24dp)
  parent.children.push(rect('status_bar', 0, 0, 412, 24, M3.surface));
  parent.children.push(text('time', '22:45', 14, 5, 11, M3.onSurface, '600'));
  parent.children.push(text('status_icons', '5G  Wi-Fi  100%', 318, 5, 10, M3.onSurfaceVariant, '500'));

  // Toolbar (56dp) - R.id.toolbar em design.xml + design_menu.xml
  parent.children.push(rect('toolbar', 0, 24, 412, 54, M3.surface));
  // Botão Voltar ou Menu Lateral Esquerdo (Drawer toggle)
  parent.children.push(rect('btn_nav_back', 8, 33, 36, 36, M3.surfaceContainerLow, 18));
  parent.children.push(text('ic_back', '←', 19, 41, 16, M3.onSurface, '700'));

  // Título e subtítulo (sc_id) como em DesignActivity
  parent.children.push(text('toolbar_title', projectTitle, 52, 33, 16, M3.onSurface, '700'));
  parent.children.push(text('toolbar_subtitle', scId, 52, 53, 11, M3.onSurfaceVariant, '500'));

  // Ícones do design_menu.xml à direita: Undo/Redo, Save, AI/Code, e Open DesignDrawer (⋮)
  const menuIcons = [
    { label: '↶', x: 256, title: 'undo' },
    { label: '↷', x: 292, title: 'redo' },
    { label: '💾', x: 328, title: 'save' },
    { label: '⋮', x: 368, title: 'open_drawer', highlight: true },
  ];
  menuIcons.forEach((m) => {
    parent.children.push(
      rect(
        `menu_${m.title}`,
        m.x,
        34,
        32,
        32,
        m.highlight ? M3.primaryContainer : M3.surfaceContainerLow,
        16
      )
    );
    parent.children.push(
      text(
        `ic_${m.title}`,
        m.label,
        m.x + 10,
        41,
        14,
        m.highlight ? M3.onPrimaryContainer : M3.onSurface,
        '700'
      )
    );
  });

  // TabLayout (R.id.tab_layout em design.xml) — Exatamente as 4 abas nativas: View, Event, Component, Strings
  parent.children.push(rect('tab_layout', 0, 78, 412, 42, M3.surface));
  const tabs = ['View', 'Event', 'Component', 'Strings'];
  tabs.forEach((t, idx) => {
    const tabW = 103;
    const tx = idx * tabW;
    const isSel = idx === activeTabIndex;
    parent.children.push(
      text(
        `tab_${t}`,
        t,
        tx + 24,
        91,
        13,
        isSel ? M3.primary : M3.onSurfaceVariant,
        isSel ? '700' : '500'
      )
    );
    if (isSel) {
      parent.children.push(rect(`tab_ind_${t}`, tx + 18, 117, 67, 3, M3.primary, 2));
    }
  });
  parent.children.push(rect('appbar_divider', 0, 120, 412, 1, M3.outlineVariant));
}

// 2. Barra Inferior Nativa de design.xml (R.id.bottom_bar: file_name_container + btn_canvas_tools + SplitButton Run)
function addDesignBottomBar(parent, y = 844, xmlFileName = 'main.xml') {
  parent.children.push(rect('bottom_bar_divider', 0, y - 1, 412, 1, M3.outlineVariant));
  parent.children.push(rect('bottom_bar', 0, y, 412, 56, M3.surface));

  // R.id.file_name_container (Spinner de seleção de arquivo XML: main.xml)
  parent.children.push(
    rect('file_name_container', 8, y + 10, 146, 36, M3.surfaceContainerLow, 8, {
      strokes: stroke(M3.outlineVariant, 1),
    })
  );
  parent.children.push(text('xml_orient_icon', '📱', 16, y + 19, 13, M3.primary, '600'));
  parent.children.push(text('file_name', xmlFileName, 38, y + 20, 13, M3.onSurface, '600'));
  parent.children.push(text('file_drop_arrow', '▾', 136, y + 20, 12, M3.onSurfaceVariant, '700'));

  // R.id.btn_canvas_tools (Botão Tonal IconOnly de design.xml)
  parent.children.push(rect('btn_canvas_tools', 238, y + 9, 42, 38, M3.secondaryContainer, 19));
  parent.children.push(text('ic_canvas_tools', '⊞', 252, y + 18, 16, M3.onSecondaryContainer, '700'));

  // MaterialSplitButton (R.id.btn_execute + R.id.btn_compiler_opt)
  parent.children.push(rect('btn_execute', 288, y + 9, 82, 38, M3.primary, 18));
  parent.children.push(text('btn_execute_txt', 'Run', 316, y + 19, 13, '#FFFFFF', '700'));
  parent.children.push(rect('btn_compiler_opt', 372, y + 9, 32, 38, M3.primary, 14));
  parent.children.push(text('btn_compiler_arrow', '▾', 383, y + 20, 12, '#FFFFFF', '700'));
}

// 3. Paleta Esquerda de view_editor.xml + palette_widget.xml (width=120dp) + Stage Central (shape + ViewPane)
function addViewEditorBody(parent, showBottomProperty = true) {
  const bodyY = 121;
  const bodyH = showBottomProperty ? 553 : 723;

  // R.id.layout_palette (width="120dp" em view_editor.xml)
  parent.children.push(rect('layout_palette', 0, bodyY, 124, bodyH, M3.surface));
  parent.children.push(rect('palette_right_div', 124, bodyY, 1, bodyH, M3.outlineVariant));

  // R.id.palette_group (TabLayout Widgets | Favorite)
  parent.children.push(rect('palette_group', 4, bodyY + 4, 116, 26, M3.surfaceContainerLow, 6));
  parent.children.push(text('tab_widgets', 'Widgets', 12, bodyY + 11, 10, M3.primary, '700'));
  parent.children.push(rect('tab_widgets_ind', 10, bodyY + 27, 46, 2, M3.primary, 1));
  parent.children.push(text('tab_favorite', 'Favorite', 68, bodyY + 11, 10, M3.onSurfaceVariant, '500'));

  // R.id.cardView em palette_widget.xml ("+ Create new widget")
  parent.children.push(rect('card_create_widget', 6, bodyY + 34, 112, 26, M3.surfaceContainerHigh, 8));
  parent.children.push(text('ic_add_widget', '+', 14, bodyY + 40, 12, M3.onSurface, '700'));
  parent.children.push(text('txt_add_widget', 'New Widget', 28, bodyY + 41, 10, M3.onSurface, '600'));

  // R.id.tv_layout ("Layouts" -> Containers Game 2D)
  parent.children.push(text('tv_layout', 'Layouts (Cena 2D)', 8, bodyY + 66, 10, M3.onSurface, '700'));

  const layoutWidgets = [
    { name: 'GameView', sub: 'Canvas 2D', color: '#4C97FF', icon: '🎮' },
    { name: 'TileMap', sub: 'Grade 2D', color: '#59C059', icon: '🧱' },
    { name: 'Camera2D', sub: 'Viewport', color: '#9966FF', icon: '🎥' },
  ];

  let wy = bodyY + 82;
  layoutWidgets.forEach((w) => {
    parent.children.push(rect(`pw_${w.name}`, 4, wy, 116, 28, M3.surfaceContainerLowest, 6, {
      strokes: stroke(M3.outlineVariant, 0.5),
    }));
    parent.children.push(rect(`pw_ic_${w.name}`, 8, wy + 4, 20, 20, w.color, 5));
    parent.children.push(text(`pw_ict_${w.name}`, w.icon, 12, wy + 7, 10, '#FFFFFF', '700'));
    parent.children.push(text(`pw_nm_${w.name}`, w.name, 32, wy + 8, 10, M3.onSurface, '600'));
    wy += 31;
  });

  // R.id.divider (MaterialDivider em palette_widget.xml)
  parent.children.push(rect('palette_divider', 10, wy + 2, 104, 1, M3.outlineVariant));
  wy += 8;

  // R.id.tv_widget ("Widgets" -> Objetos & UI Game 2D)
  parent.children.push(text('tv_widget', 'Widgets (Game 2D)', 8, wy, 10, M3.onSurface, '700'));
  wy += 16;

  const gameWidgets = [
    { name: 'Sprite', color: '#FFAB19', icon: '🐱', sel: true },
    { name: 'AnimatedSprite', color: '#FF8C1A', icon: '🎞' },
    { name: 'PhysicsBody2D', color: '#5CB1D6', icon: '⚙' },
    { name: 'CollisionShape', color: '#0FBD8C', icon: '⬡' },
    { name: 'ParticleEmitter', color: '#FF6680', icon: '✨' },
    { name: 'Joystick', color: '#575992', icon: '🕹' },
    { name: 'TouchButton', color: '#E11D48', icon: '🔘' },
    { name: 'Text / HUD', color: '#2563EB', icon: '🔤' },
    { name: 'AudioPlayer', color: '#CF63CF', icon: '🔊' },
    { name: 'Timer', color: '#FFBF00', icon: '⏱' },
  ];

  gameWidgets.forEach((w) => {
    if (wy + 28 <= bodyY + bodyH - 4) {
      parent.children.push(
        rect(
          `gw_${w.name}`,
          4,
          wy,
          116,
          27,
          w.sel ? M3.primaryContainer : M3.surfaceContainerLowest,
          6,
          { strokes: stroke(w.sel ? M3.primary : M3.outlineVariant, w.sel ? 1.5 : 0.5) }
        )
      );
      parent.children.push(rect(`gw_ic_${w.name}`, 8, wy + 3, 20, 20, w.color, 5));
      parent.children.push(text(`gw_ict_${w.name}`, w.icon, 12, wy + 6, 10, '#FFFFFF', '700'));
      parent.children.push(
        text(
          `gw_nm_${w.name}`,
          w.name,
          32,
          wy + 7,
          10,
          w.sel ? M3.onPrimaryContainer : M3.onSurface,
          w.sel ? '700' : '600'
        )
      );
      wy += 30;
    }
  });

  // Área Direita: R.id.editor_layout + R.id.shape (Frame do dispositivo + ViewPane)
  parent.children.push(rect('editor_layout', 125, bodyY, 287, bodyH, M3.surfaceContainerLow));

  // R.id.shape (Moldura do aparelho em view_editor.xml)
  const phoneX = 136;
  const phoneY = bodyY + 12;
  const phoneW = 266;
  const phoneH = Math.min(bodyH - 24, 515);
  parent.children.push(
    rect('phone_shape', phoneX, phoneY, phoneW, phoneH, '#1E293B', 14, {
      strokes: stroke('#475569', 2),
      effects: shadow('rgba(15,23,42,0.16)', 0, 4, 12),
    })
  );

  // Statusbar + Toolbar simuladas do ViewEditor (como em ViewEditor.java)
  parent.children.push(rect('sim_statusbar', phoneX + 6, phoneY + 6, phoneW - 12, 16, '#0F172A', 4));
  parent.children.push(text('sim_status_txt', 'GameSceneActivity (60 FPS)', phoneX + 12, phoneY + 8, 9, '#94A3B8', '500'));

  // ViewPane (Stage 2D dentro de R.id.shape)
  const stageX = phoneX + 6;
  const stageY = phoneY + 24;
  const stageW = phoneW - 12;
  const stageH = phoneH - 30;
  parent.children.push(rect('view_pane_sky', stageX, stageY, stageW, stageH, '#38BDF8', 8));

  // Grade sutil no ViewPane
  for (let gx = stageX + 36; gx < stageX + stageW; gx += 36) {
    parent.children.push(rect(`grid_v_${gx}`, gx, stageY, 1, stageH, '#7DD3FC', 0, { fillOpacity: 0.35 }));
  }
  for (let gy = stageY + 36; gy < stageY + stageH; gy += 36) {
    parent.children.push(rect(`grid_h_${gy}`, stageX, gy, stageW, 1, '#7DD3FC', 0, { fillOpacity: 0.35 }));
  }

  // HUD widget no topo do Stage (textview_hud)
  parent.children.push(
    rect('widget_hud', stageX + 8, stageY + 8, 128, 22, '#0F172A', 6, { fillOpacity: 0.72 })
  );
  parent.children.push(text('widget_hud_txt', '★ SCORE: 0250   ♥ 3', stageX + 14, stageY + 13, 9, '#FDE047', '700'));

  // Nuvens decorativas no Stage 2D
  parent.children.push(rect('cloud_1', stageX + 150, stageY + 26, 68, 18, '#FFFFFF', 9, { fillOpacity: 0.85 }));
  parent.children.push(rect('cloud_2', stageX + 26, stageY + 62, 54, 15, '#FFFFFF', 8, { fillOpacity: 0.8 }));

  // Plataforma flutuante (tilemap_plat)
  parent.children.push(rect('tile_plat', stageX + 140, stageY + stageH - 150, 96, 20, '#16A34A', 4));
  parent.children.push(rect('tile_plat_dirt', stageX + 140, stageY + stageH - 142, 96, 12, '#854D0E', 2));

  // Moedas (animated_coin)
  parent.children.push(ellipse('coin_1', stageX + 156, stageY + stageH - 176, 18, 18, '#FACC15', { strokes: stroke('#CA8A04', 1.5) }));
  parent.children.push(ellipse('coin_2', stageX + 184, stageY + stageH - 176, 18, 18, '#FACC15', { strokes: stroke('#CA8A04', 1.5) }));

  // Sprite selecionado: sprite1 (Gato / Herói com bounding box de seleção nativa do ViewPane)
  const spX = stageX + 54;
  const spY = stageY + stageH - 154;
  parent.children.push(
    rect('sprite1_selection_box', spX - 4, spY - 4, 64, 64, '#575992', 6, {
      fillOpacity: 0.18,
      strokes: stroke('#FFFFFF', 2),
    })
  );
  parent.children.push(rect('sprite1_body', spX + 4, spY + 4, 48, 48, '#F97316', 12, { strokes: stroke('#FFFFFF', 1.5) }));
  parent.children.push(text('sprite1_face', '🐱', spX + 16, spY + 14, 20, '#FFFFFF', '700'));
  // Badge do ViewBean ID acima da seleção
  parent.children.push(rect('sprite1_tag', spX - 4, spY - 22, 92, 16, M3.primary, 4));
  parent.children.push(text('sprite1_tag_txt', 'sprite1 (64x64)', spX + 2, spY - 19, 8, '#FFFFFF', '700'));

  // TileMap do chão (tilemap1)
  parent.children.push(rect('tilemap1_top', stageX, stageY + stageH - 94, stageW, 16, '#22C55E'));
  parent.children.push(rect('tilemap1_earth', stageX, stageY + stageH - 78, stageW, 78, '#78350F'));

  // Controles Touch UI no Stage (joystick1 + touchbutton_jump)
  parent.children.push(
    ellipse('joystick1_outer', stageX + 14, stageY + stageH - 68, 54, 54, '#0F172A', {
      fillOpacity: 0.5,
      strokes: stroke('#E2E8F0', 1.5),
    })
  );
  parent.children.push(ellipse('joystick1_thumb', stageX + 29, stageY + stageH - 53, 24, 24, '#FFFFFF', { fillOpacity: 0.85 }));

  parent.children.push(
    ellipse('touchbutton_jump', stageX + stageW - 64, stageY + stageH - 64, 48, 48, '#E11D48', {
      fillOpacity: 0.85,
      strokes: stroke('#FFFFFF', 1.5),
    })
  );
  parent.children.push(text('touchbutton_txt', 'A', stageX + stageW - 45, stageY + stageH - 49, 14, '#FFFFFF', '700'));
}

// 4. Painel Inferior de Propriedades Validado: view_property.xml + property_grid_item.xml (height="170dp")
function addViewPropertyPanel(parent, activeSubTab = 'Basic') {
  const vpY = 674; // Cobre a área inferior quando um widget está selecionado (170dp de altura: 674..844)
  parent.children.push(rect('view_property_top_div', 0, vpY - 1, 412, 1, M3.outlineVariant));
  parent.children.push(rect('view_property_root', 0, vpY, 412, 170, M3.surface));

  // Linha 1 (height="40dp", paddingHorizontal="8dp"): spn_widget à esquerda, img_delete e img_save à direita
  parent.children.push(
    rect('spn_widget', 8, vpY + 5, 168, 30, M3.surfaceContainerLow, 8, {
      strokes: stroke(M3.outlineVariant, 1),
    })
  );
  parent.children.push(text('spn_widget_ic', '🐱', 16, vpY + 12, 12, M3.onSurface, '700'));
  parent.children.push(text('spn_widget_id', 'sprite1 (Sprite)', 36, vpY + 13, 12, M3.onSurface, '700'));
  parent.children.push(text('spn_widget_arr', '▾', 158, vpY + 13, 12, M3.onSurfaceVariant, '700'));

  // R.id.img_delete (lixeira) e R.id.img_save (salvar na coleção) à direita
  parent.children.push(rect('img_delete', 332, vpY + 4, 34, 32, M3.surfaceContainerLow, 8));
  parent.children.push(text('ic_delete', '🗑', 343, vpY + 12, 13, M3.error, '700'));
  parent.children.push(rect('img_save', 370, vpY + 4, 34, 32, M3.surfaceContainerLow, 8));
  parent.children.push(text('ic_save', '💾', 381, vpY + 12, 13, M3.onSurface, '700'));

  // Linha 2: R.id.layout_property_group (Basic | Recent | Event)
  const groups = ['Basic', 'Recent', 'Event'];
  groups.forEach((g, idx) => {
    const gx = 8 + idx * 78;
    const isSel = g === activeSubTab;
    parent.children.push(
      rect(
        `prop_grp_${g}`,
        gx,
        vpY + 40,
        72,
        24,
        isSel ? M3.secondaryContainer : M3.surfaceContainerLow,
        6
      )
    );
    parent.children.push(
      text(
        `prop_grp_txt_${g}`,
        g,
        gx + 18,
        vpY + 46,
        11,
        isSel ? M3.onSecondaryContainer : M3.onSurfaceVariant,
        isSel ? '700' : '500'
      )
    );
  });
  parent.children.push(rect('prop_grp_div', 0, vpY + 68, 412, 1, M3.outlineVariant));

  // Linha 3 (height="82dp"): R.id.hcv_property com cards 80dp x 80dp (property_grid_item.xml) + Card "See all" fixo à direita!
  if (activeSubTab === 'Basic') {
    const propCards = [
      { id: 'id', icon: '🏷', title: 'id', val: 'sprite1' },
      { id: 'tx', icon: '↔', title: 'translation_x', val: '120.0' },
      { id: 'ty', icon: '↕', title: 'translation_y', val: '240.0' },
      { id: 'rot', icon: '↻', title: 'rotate', val: '90.0°', hi: true },
      { id: 'img', icon: '🖼', title: 'image', val: 'cat_run_1', hi: true },
    ];
    propCards.forEach((c, idx) => {
      const cx = 8 + idx * 64;
      const cardW = 60;
      const cardH = 82;
      parent.children.push(
        rect(
          `pgi_${c.id}`,
          cx,
          vpY + 76,
          cardW,
          cardH,
          c.hi ? M3.secondaryContainer : M3.surfaceContainerHighest,
          10,
          { strokes: c.hi ? stroke(M3.primary, 1.5) : [] }
        )
      );
      parent.children.push(text(`pgi_ic_${c.id}`, c.icon, cx + 22, vpY + 86, 16, M3.onSurface, '700'));
      parent.children.push(text(`pgi_tl_${c.id}`, c.title, cx + 5, vpY + 112, 9, M3.onSurface, '600'));
      parent.children.push(text(`pgi_vl_${c.id}`, c.val, cx + 6, vpY + 128, 9, M3.primary, '700'));
    });

    // Card flutuante R.id.property_menu_item ("See all") fixo na borda direita de view_property.xml!
    parent.children.push(
      rect('property_menu_item_see_all', 332, vpY + 76, 72, 82, M3.primaryContainer, 12, {
        strokes: stroke(M3.primary, 1.5),
        effects: shadow('rgba(87,89,146,0.2)', -2, 2, 8),
      })
    );
    parent.children.push(text('see_all_ic', '☰', 360, vpY + 92, 18, M3.onPrimaryContainer, '700'));
    parent.children.push(text('see_all_txt', 'See all', 348, vpY + 122, 11, M3.onPrimaryContainer, '700'));
  } else {
    // Aba "Event" dentro de ViewProperty usando event_grid_item.xml (96dp x 74dp cards com bolinha verde ic_mtrl_add_circle)
    const evCards = [
      { id: 'onGameStart', icon: '🚩', title: 'onGameStart', used: true },
      { id: 'onSpriteTouch', icon: '👆', title: 'onSpriteTouch', used: true },
      { id: 'onCollision', icon: '💥', title: 'onCollisionWith', used: true },
      { id: 'onCloneStart', icon: '🧬', title: 'onCloneStart', used: false },
    ];
    evCards.forEach((ec, idx) => {
      const cx = 8 + idx * 98;
      parent.children.push(
        rect(`egi_${ec.id}`, cx, vpY + 78, 92, 76, M3.surfaceContainerHighest, 10, {
          strokes: ec.used ? stroke(M3.coolGreen, 1.5) : [],
        })
      );
      parent.children.push(text(`egi_ic_${ec.id}`, ec.icon, cx + 36, vpY + 90, 18, M3.onSurface, '700'));
      if (ec.used) {
        parent.children.push(ellipse(`egi_used_${ec.id}`, cx + 56, vpY + 86, 12, 12, M3.coolGreen));
        parent.children.push(text(`egi_chk_${ec.id}`, '✓', cx + 59, vpY + 87, 8, '#FFFFFF', '700'));
      }
      parent.children.push(text(`egi_tl_${ec.id}`, ec.title, cx + 8, vpY + 124, 10, M3.onSurface, '600'));
    });
  }
}

// ============================================================================
// CONSTRUÇÃO DOS 6 FRAMES PRINCIPAIS NO CANVAS DO FIGCRAFT
// ============================================================================

const frames = [];

// ----------------------------------------------------------------------------
// FRAME 1: dialog_new_project_type.xml (Seleção entre Native, Android Studio e Game 2D)
// ----------------------------------------------------------------------------
function buildFrame01() {
  const f = frame('01 - dialog_new_project_type.xml (Novo Projeto: Native, Android Studio, Game 2D)', 0, 0, 412, 900, M3.surface);

  // Status bar + AppBar da tela inicial (My Projects)
  f.children.push(rect('status_bar', 0, 0, 412, 24, M3.surface));
  f.children.push(text('time', '22:45', 14, 5, 11, M3.onSurface, '600'));
  f.children.push(rect('home_toolbar', 0, 24, 412, 56, M3.surface));
  f.children.push(text('home_title', 'Sketchware-IA', 20, 41, 18, M3.onSurface, '700'));

  // Lista de projetos ao fundo
  const sampleProjects = [
    { name: 'SuperCatJump (Game 2D)', pkg: 'com.game.supercatjump • 608', badge: 'GAME 2D', color: '#4C97FF' },
    { name: 'LojaVirtualApp', pkg: 'com.app.lojavirtual • 607', badge: 'NATIVE', color: M3.primary },
    { name: 'FinanceStudioPro', pkg: 'com.pro.finance • 606', badge: 'ANDROID STUDIO', color: '#0F9D58' },
  ];
  sampleProjects.forEach((p, idx) => {
    const py = 92 + idx * 74;
    f.children.push(rect(`proj_${idx}`, 16, py, 380, 64, M3.surfaceContainerLow, 14, { strokes: stroke(M3.outlineVariant, 1) }));
    f.children.push(rect(`proj_ic_${idx}`, 28, py + 12, 40, 40, p.color, 10));
    f.children.push(text(`proj_nm_${idx}`, p.name, 80, py + 14, 14, M3.onSurface, '700'));
    f.children.push(text(`proj_pk_${idx}`, p.pkg, 80, py + 35, 11, M3.onSurfaceVariant, '400'));
  });

  // Scrim escuro do MaterialAlertDialog
  f.children.push(rect('dialog_scrim', 0, 0, 412, 900, M3.scrim, 0, { fillOpacity: 0.45 }));

  // Card exato de dialog_new_project_type.xml (padding="24dp", MaterialAlertDialog)
  const dx = 18;
  const dy = 210;
  const dw = 376;
  const dh = 468;
  f.children.push(
    rect('dialog_new_project_type', dx, dy, dw, dh, M3.surfaceContainerLowest, 24, {
      strokes: stroke(M3.outlineVariant, 1),
      effects: shadow('rgba(0,0,0,0.28)', 0, 12, 28),
    })
  );

  // Título de dialog_new_project_type.xml (@string/create_project_type_title, 18sp bold, marginBottom=16dp)
  f.children.push(text('dialog_title', 'Select Project Type', dx + 24, dy + 24, 18, M3.onSurface, '700'));
  f.children.push(
    text(
      'dialog_sub',
      'Layout validado: res/layout/dialog_new_project_type.xml',
      dx + 24,
      dy + 48,
      10.5,
      M3.primary,
      '600'
    )
  );

  // GridLayout com os cards usando @drawable/bg_chat_option_row e @drawable/bg_chat_project_icon (44dp)
  // 2 cards na linha superior (Native | Android Studio) + 1 card destacado na linha inferior (Game 2D - Scratch Engine)
  const cardW = 156;
  const cardH = 128;

  // Card 1: R.id.card_project_sketchware ("Native")
  f.children.push(
    rect('card_project_sketchware', dx + 24, dy + 76, cardW, cardH, M3.surfaceContainerLow, 16, {
      strokes: stroke(M3.outlineVariant, 1),
    })
  );
  f.children.push(rect('icon_sketchware', dx + 80, dy + 94, 44, 44, M3.primaryContainer, 12));
  f.children.push(text('ic_sw_txt', '📱', dx + 94, dy + 106, 18, M3.onPrimaryContainer, '700'));
  f.children.push(text('lbl_sketchware', 'Native', dx + 79, dy + 152, 14, M3.onSurface, '700'));
  f.children.push(text('sub_sketchware', 'project_type: sketchware', dx + 38, dy + 174, 10, M3.onSurfaceVariant, '400'));

  // Card 2: R.id.card_project_android_studio ("Android Studio")
  f.children.push(
    rect('card_project_android_studio', dx + 196, dy + 76, cardW, cardH, M3.surfaceContainerLow, 16, {
      strokes: stroke(M3.outlineVariant, 1),
    })
  );
  f.children.push(rect('icon_as', dx + 252, dy + 94, 44, 44, '#DCFCE7', 12));
  f.children.push(text('ic_as_txt', '🤖', dx + 266, dy + 106, 18, '#15803D', '700'));
  f.children.push(text('lbl_as', 'Android Studio', dx + 226, dy + 152, 14, M3.onSurface, '700'));
  f.children.push(text('sub_as', 'project_type: android_studio', dx + 206, dy + 174, 10, M3.onSurfaceVariant, '400'));

  // Card 3 (NOVO): R.id.card_project_game2d ("Game 2D") — Mesmo padrão visual de dialog_new_project_type.xml
  f.children.push(
    rect('card_project_game2d', dx + 24, dy + 218, 328, 136, M3.primaryContainer, 16, {
      strokes: stroke(M3.primary, 2),
    })
  );
  f.children.push(rect('icon_game2d', dx + 44, dy + 244, 44, 44, M3.primary, 12));
  f.children.push(text('ic_g2d_txt', '🎮', dx + 57, dy + 256, 18, '#FFFFFF', '700'));
  f.children.push(text('lbl_game2d', 'Game 2D (Scratch Engine)', dx + 102, dy + 240, 14.5, M3.onPrimaryContainer, '700'));
  f.children.push(
    text(
      'desc_game2d',
      'project_type: "game_2d" em mysc/list/project\nReutiliza DesignActivity, DesignDrawer,\nViewEditor, ViewProperty e LogicEditorActivity.',
      dx + 102,
      dy + 264,
      10,
      M3.onPrimaryContainer,
      '500'
    )
  );

  // Nota técnica de reutilização
  f.children.push(rect('note_reuse', dx + 24, dy + 370, 328, 74, M3.surfaceContainerLow, 12));
  f.children.push(
    text(
      'note_reuse_txt',
      '✓ Sem criar outro app nem outra interface:\nAbre o mesmo DesignActivity.java + MyProjectSettingActivity.java,\nfiltrando a paleta e os blocos pela flag project_type == "game_2d".',
      dx + 34,
      dy + 382,
      10,
      M3.onSurfaceVariant,
      '500'
    )
  );

  return f;
}

// ----------------------------------------------------------------------------
// FRAME 2: design.xml + view_editor.xml + palette_widget.xml + view_property.xml
// ----------------------------------------------------------------------------
function buildFrame02() {
  const f = frame(
    '02 - DesignActivity + ViewEditor + ViewProperty (view_editor.xml & view_property.xml)',
    460,
    0,
    412,
    900,
    M3.surface
  );

  // AppBar exata de design.xml (Aba 0 = View selecionada)
  addDesignAppBar(f, 0, 'SuperCatJump', '608');

  // Corpo exato de view_editor.xml + palette_widget.xml
  addViewEditorBody(f, true);

  // Painel inferior exato de view_property.xml + property_grid_item.xml (80x80dp cards + See all)
  addViewPropertyPanel(f, 'Basic');

  // Barra inferior de design.xml
  addDesignBottomBar(f, 844, 'main.xml');

  return f;
}

// ----------------------------------------------------------------------------
// FRAME 3: Menu Lateral Nativo (DesignDrawer.java + design_drawer_item.xml)
// Mostra como o menu lateral já existente gerencia Imagens (Sprites/Tiles), Sons, Fontes, Views e Assets!
// ----------------------------------------------------------------------------
function buildFrame03() {
  const f = frame(
    '03 - Menu Lateral Nativo (DesignDrawer.java + design_drawer_item.xml — Imagens, Sons, Views)',
    920,
    0,
    412,
    900,
    M3.surface
  );

  // Fundo: DesignActivity + ViewEditor + ViewProperty
  addDesignAppBar(f, 0, 'SuperCatJump', '608');
  addViewEditorBody(f, true);
  addViewPropertyPanel(f, 'Basic');
  addDesignBottomBar(f, 844, 'main.xml');

  // Scrim sobre a tela quando o Drawer está aberto
  f.children.push(rect('drawer_scrim', 0, 24, 412, 876, M3.scrim, 0, { fillOpacity: 0.42 }));

  // Menu Lateral Nativo (DesignDrawer.java + design_drawer_item.xml)
  // Mostrado aberto (310dp de largura) com os itens exatos de DesignDrawer.java!
  const dw = 316;
  f.children.push(
    rect('design_drawer_panel', 0, 24, dw, 876, M3.surfaceContainerLowest, 20, {
      effects: shadow('rgba(0,0,0,0.30)', 6, 0, 24),
    })
  );

  // Cabeçalho do DesignDrawer
  f.children.push(rect('drawer_header', 12, 36, dw - 24, 58, M3.primaryContainer, 14));
  f.children.push(text('drawer_title', 'Configuration & Resource Managers', 22, 46, 12.5, M3.onPrimaryContainer, '700'));
  f.children.push(
    text(
      'drawer_subtitle',
      'DesignDrawer.java + design_drawer_item.xml',
      22,
      66,
      10,
      M3.onPrimaryContainer,
      '500'
    )
  );

  // Lista de itens exatos de DesignDrawer.java usando design_drawer_item.xml:
  // (24dp img_icon, tv_root_title 14sp, tv_sub_title 12sp, e chevron › à direita)
  const drawerItems = [
    {
      icon: '📚',
      title: 'Library',
      sub: 'Manage libraries (AppCompat, Game 2D)',
      highlight: false,
    },
    {
      icon: '📱',
      title: 'View (Scenes)',
      sub: 'ManageViewActivity — Cenas do jogo',
      highlight: true,
      badge: 'Cenas',
    },
    {
      icon: '🖼',
      title: 'Image (Sprites & Costumes)',
      sub: 'ManageImageActivity — Sprites e cenários',
      highlight: true,
      badge: 'Sprites',
    },
    {
      icon: '🔊',
      title: 'Sound (SFX & BGM)',
      sub: 'ManageSoundActivity — Sons .wav e .mp3',
      highlight: true,
      badge: 'Sons',
    },
    {
      icon: '🎞',
      title: 'Lottie',
      sub: 'ManageLottieActivity — Animações JSON',
      highlight: false,
    },
    {
      icon: '🔤',
      title: 'Font (HUD Fonts)',
      sub: 'ManageFontActivity — Fontes do placar',
      highlight: true,
      badge: 'HUD',
    },
    {
      icon: '☕',
      title: 'Java',
      sub: 'ManageJavaActivity — Classes Java extras',
      highlight: false,
    },
    {
      icon: '📦',
      title: 'Resource',
      sub: 'ManageResourceActivity — XMLs e drawables',
      highlight: false,
    },
    {
      icon: '🗂',
      title: 'Assets (TileMaps & JSON)',
      sub: 'ManageAssetsActivity — Mapas e partículas',
      highlight: true,
      badge: 'Mapas',
    },
    {
      icon: '🛡',
      title: 'Permission & Manifest',
      sub: 'ManagePermissionActivity — OpenGL / Vibrate',
      highlight: false,
    },
    {
      icon: '🧩',
      title: 'Custom Blocks',
      sub: 'CustomBlocksManagerActivity — Blocos extras',
      highlight: false,
    },
    {
      icon: '💻',
      title: 'Source Code',
      sub: 'CodeViewerActivity — Ver Java gerado',
      highlight: false,
    },
    {
      icon: '⭐',
      title: 'Collection',
      sub: 'ManageCollectionActivity — Favoritos salvos',
      highlight: false,
    },
  ];

  let iy = 104;
  drawerItems.forEach((item, idx) => {
    const rowH = 54;
    f.children.push(
      rect(
        `dd_item_${idx}`,
        10,
        iy,
        dw - 20,
        rowH,
        item.highlight ? M3.secondaryContainer : M3.surfaceContainerLowest,
        12,
        {
          strokes: item.highlight ? stroke(M3.primary, 1.5) : stroke(M3.outlineVariant, 0.5),
        }
      )
    );

    // R.id.img_icon (24dp)
    f.children.push(
      rect(
        `dd_ic_box_${idx}`,
        20,
        iy + 11,
        32,
        32,
        item.highlight ? M3.primary : M3.surfaceContainerHigh,
        8
      )
    );
    f.children.push(
      text(
        `dd_ic_${idx}`,
        item.icon,
        28,
        iy + 19,
        13,
        item.highlight ? '#FFFFFF' : M3.onSurface,
        '700'
      )
    );

    // R.id.tv_root_title (14sp) + R.id.tv_sub_title (12sp)
    f.children.push(
      text(
        `dd_title_${idx}`,
        item.title,
        60,
        iy + 10,
        12,
        item.highlight ? M3.onSecondaryContainer : M3.onSurface,
        '700'
      )
    );
    f.children.push(
      text(
        `dd_sub_${idx}`,
        item.sub,
        60,
        iy + 29,
        9.5,
        item.highlight ? M3.primary : M3.onSurfaceVariant,
        '500'
      )
    );

    if (item.badge) {
      f.children.push(rect(`dd_badge_${idx}`, dw - 76, iy + 8, 52, 16, M3.primary, 8));
      f.children.push(text(`dd_badge_txt_${idx}`, item.badge, dw - 70, iy + 11, 8, '#FFFFFF', '700'));
    }

    // Chevron › de design_drawer_item.xml
    f.children.push(text(`dd_chev_${idx}`, '›', dw - 24, iy + 18, 16, M3.onSurfaceVariant, '700'));

    iy += 58;
  });

  return f;
}

// ----------------------------------------------------------------------------
// FRAME 4: Diálogos de Propriedades Validados (property_popup_hybrid.xml,
// image_picker_item.xml, property_popup_choice_attribute.xml e PropertyActivity property.xml)
// ----------------------------------------------------------------------------
function buildFrame04() {
  const f = frame(
    '04 - Diálogos de Propriedades Nativos (property_popup_hybrid.xml, Image Picker & property.xml)',
    1380,
    0,
    412,
    900,
    M3.surface
  );

  // Ao fundo: PropertyActivity (property.xml + property_input_item.xml) aberta pelo botão "See all"
  f.children.push(rect('status_bar', 0, 0, 412, 24, M3.surface));
  f.children.push(text('time', '22:45', 14, 5, 11, M3.onSurface, '600'));

  // Toolbar de PropertyActivity (property.xml: título "Properties", subtítulo "sprite1", ícone + ManageImageActivity)
  f.children.push(rect('prop_toolbar', 0, 24, 412, 54, M3.surface));
  f.children.push(text('prop_back', '←', 18, 41, 16, M3.onSurface, '700'));
  f.children.push(text('prop_title', 'Properties', 52, 33, 16, M3.onSurface, '700'));
  f.children.push(text('prop_sub', 'sprite1 (Sprite — property.xml)', 52, 53, 11, M3.onSurfaceVariant, '500'));
  f.children.push(rect('prop_add_img_btn', 364, 34, 34, 34, M3.primaryContainer, 17));
  f.children.push(text('prop_add_img_ic', '🖼+', 371, 43, 11, M3.onPrimaryContainer, '700'));
  f.children.push(rect('prop_div', 0, 78, 412, 1, M3.outlineVariant));

  // Linhas de property_input_item.xml ao fundo (R.id.img_left_icon, R.id.tv_name, R.id.tv_value)
  const bgProps = [
    { icon: '🏷', name: 'id', val: 'sprite1' },
    { icon: '↔', name: 'translation_x', val: '120.0 dp' },
    { icon: '↕', name: 'translation_y', val: '240.0 dp' },
    { icon: '↻', name: 'rotate', val: '90.0' },
  ];
  bgProps.forEach((bp, idx) => {
    const py = 84 + idx * 44;
    f.children.push(rect(`pii_${idx}`, 8, py, 396, 40, M3.surfaceContainerLow, 8));
    f.children.push(text(`pii_ic_${idx}`, bp.icon, 20, py + 12, 13, M3.onSurfaceVariant, '700'));
    f.children.push(text(`pii_nm_${idx}`, bp.name, 48, py + 13, 12, M3.onSurface, '600'));
    f.children.push(text(`pii_vl_${idx}`, bp.val, 310, py + 13, 12, M3.primary, '700'));
  });

  // Scrim suave para destacar os 3 diálogos nativos validados do Sketchware-IA
  f.children.push(rect('dialogs_scrim', 0, 120, 412, 780, M3.scrim, 0, { fillOpacity: 0.35 }));

  // --------------------------------------------------------------------------
  // DIÁLOGO 1: property_popup_hybrid.xml (PropertyInputItem.java — Slider + TextInputEditText)
  // Usado ao clicar em rotate, alpha, translation_x, translation_y, scale_x, scale_y, gravity, speed
  // --------------------------------------------------------------------------
  const d1Y = 132;
  f.children.push(
    rect('dlg_hybrid', 16, d1Y, 380, 224, M3.surfaceContainerLowest, 22, {
      strokes: stroke(M3.primary, 1.5),
      effects: shadow('rgba(0,0,0,0.25)', 0, 8, 20),
    })
  );
  f.children.push(rect('dlg1_ic_box', 32, d1Y + 16, 28, 28, M3.primaryContainer, 8));
  f.children.push(text('dlg1_ic', '↻', 40, d1Y + 22, 14, M3.onPrimaryContainer, '700'));
  f.children.push(text('dlg1_title', 'rotate (property_popup_hybrid.xml)', 68, d1Y + 22, 14, M3.onSurface, '700'));

  // R.id.current_value (18sp centralizado)
  f.children.push(text('dlg1_cur_val', '90.0°', 182, d1Y + 52, 18, M3.primary, '700'));

  // R.id.slider (com.google.android.material.slider.Slider)
  f.children.push(rect('dlg1_slider_track_bg', 36, d1Y + 86, 340, 8, M3.surfaceContainerHighest, 4));
  f.children.push(rect('dlg1_slider_track_act', 36, d1Y + 86, 210, 8, M3.primary, 4));
  f.children.push(ellipse('dlg1_slider_thumb', 238, d1Y + 80, 20, 20, M3.primary, { strokes: stroke('#FFFFFF', 2) }));

  // R.id.ti_input + R.id.ed_input (TextInputLayout OutlinedBox)
  f.children.push(
    rect('dlg1_ti_input', 36, d1Y + 114, 340, 48, M3.surfaceContainerLowest, 8, {
      strokes: stroke(M3.primary, 1.5),
    })
  );
  f.children.push(rect('dlg1_lbl_bg', 48, d1Y + 107, 110, 14, M3.surfaceContainerLowest));
  f.children.push(text('dlg1_lbl', 'Enter value in degrees', 52, d1Y + 108, 9, M3.primary, '600'));
  f.children.push(text('dlg1_ed_input', '90.0', 50, d1Y + 131, 14, M3.onSurface, '600'));

  // Botões Cancel / Save do MaterialAlertDialogBuilder
  f.children.push(text('dlg1_cancel', 'Cancel', 268, d1Y + 184, 13, M3.primary, '700'));
  f.children.push(rect('dlg1_save_btn', 324, d1Y + 174, 56, 32, M3.primary, 16));
  f.children.push(text('dlg1_save_txt', 'Save', 338, d1Y + 183, 13, '#FFFFFF', '700'));

  // --------------------------------------------------------------------------
  // DIÁLOGO 2: PropertyResourceItem.java + image_picker_item.xml
  // Usado ao clicar em "image" (Fantasia do Sprite / Tileset / Background importado no ManageImageActivity)
  // --------------------------------------------------------------------------
  const d2Y = 372;
  f.children.push(
    rect('dlg_resource_picker', 16, d2Y, 380, 266, M3.surfaceContainerLowest, 22, {
      strokes: stroke(M3.primary, 1.5),
      effects: shadow('rgba(0,0,0,0.25)', 0, 8, 20),
    })
  );
  f.children.push(rect('dlg2_ic_box', 32, d2Y + 16, 28, 28, M3.primaryContainer, 8));
  f.children.push(text('dlg2_ic', '🖼', 39, d2Y + 22, 13, M3.onPrimaryContainer, '700'));
  f.children.push(
    text('dlg2_title', 'image (image_picker_item.xml)', 68, d2Y + 22, 13, M3.onSurface, '700')
  );

  // Barra de busca de SearchWithRecyclerViewBinding
  f.children.push(
    rect('dlg2_search', 32, d2Y + 50, 348, 34, M3.surfaceContainerLow, 8, {
      strokes: stroke(M3.outlineVariant, 1),
    })
  );
  f.children.push(text('dlg2_search_txt', '🔍 Search images from ManageImageActivity...', 44, d2Y + 61, 11, M3.onSurfaceVariant, '400'));

  // Cards de image_picker_item.xml (RadioButton + text_view 16sp + miniatura layoutImg)
  const imgItems = [
    { name: 'cat_run_1 (Costume 1)', color: '#F97316', icon: '🐱', sel: true },
    { name: 'cat_jump_2 (Costume 2)', color: '#FB923C', icon: '😺', sel: false },
    { name: 'tileset_grass_2d', color: '#22C55E', icon: '🧱', sel: false },
  ];
  imgItems.forEach((im, idx) => {
    const ry = d2Y + 92 + idx * 42;
    f.children.push(
      rect(
        `ipi_card_${idx}`,
        32,
        ry,
        348,
        36,
        im.sel ? M3.secondaryContainer : M3.surfaceContainerHigh,
        8,
        { strokes: stroke(im.sel ? M3.primary : M3.outlineVariant, im.sel ? 1.5 : 0.5) }
      )
    );
    f.children.push(
      text(`ipi_rad_${idx}`, im.sel ? '◉' : '○', 44, ry + 10, 14, im.sel ? M3.primary : M3.onSurfaceVariant, '700')
    );
    f.children.push(text(`ipi_txt_${idx}`, im.name, 68, ry + 11, 12, M3.onSurface, im.sel ? '700' : '500'));
    f.children.push(rect(`ipi_thumb_${idx}`, 342, ry + 4, 28, 28, im.color, 6));
    f.children.push(text(`ipi_th_ic_${idx}`, im.icon, 349, ry + 10, 12, '#FFFFFF', '700'));
  });

  f.children.push(text('dlg2_cancel', 'Cancel', 260, d2Y + 230, 13, M3.primary, '700'));
  f.children.push(rect('dlg2_select_btn', 316, d2Y + 220, 64, 32, M3.primary, 16));
  f.children.push(text('dlg2_select_txt', 'Select', 330, d2Y + 229, 13, '#FFFFFF', '700'));

  // --------------------------------------------------------------------------
  // DIÁLOGO 3: property_popup_choice_attribute.xml (PropertyAttributeItem.java — RadioGroup)
  // Usado ao clicar em physics_type, rotation_style, body_type, layer
  // --------------------------------------------------------------------------
  const d3Y = 652;
  f.children.push(
    rect('dlg_choice_attr', 16, d3Y, 380, 232, M3.surfaceContainerLowest, 22, {
      strokes: stroke(M3.primary, 1.5),
      effects: shadow('rgba(0,0,0,0.25)', 0, 8, 20),
    })
  );
  f.children.push(rect('dlg3_ic_box', 32, d3Y + 16, 28, 28, M3.primaryContainer, 8));
  f.children.push(text('dlg3_ic', '⚙', 40, d3Y + 22, 13, M3.onPrimaryContainer, '700'));
  f.children.push(
    text('dlg3_title', 'physics_type (property_popup_choice)', 68, d3Y + 22, 13, M3.onSurface, '700')
  );

  // R.id.help + R.id.rg (RadioGroup)
  f.children.push(
    text('dlg3_help', 'Select 2D Physics Body simulation mode for this Sprite:', 34, d3Y + 50, 11, M3.onSurfaceVariant, '500')
  );

  const radioChoices = [
    { label: 'DYNAMIC (Gravity + Collisions + Forces)', sel: true },
    { label: 'KINEMATIC (Moved by blocks, collides)', sel: false },
    { label: 'STATIC (Fixed obstacle / Ground)', sel: false },
    { label: 'NONE (No physics simulation)', sel: false },
  ];
  radioChoices.forEach((rc, idx) => {
    const cy = d3Y + 72 + idx * 28;
    f.children.push(
      text(`dlg3_rad_${idx}`, rc.sel ? '◉' : '○', 38, cy, 13, rc.sel ? M3.primary : M3.onSurfaceVariant, '700')
    );
    f.children.push(
      text(`dlg3_lbl_${idx}`, rc.label, 62, cy + 1, 11.5, rc.sel ? M3.onSurface : M3.onSurfaceVariant, rc.sel ? '700' : '500')
    );
  });

  f.children.push(text('dlg3_cancel', 'Cancel', 268, d3Y + 194, 13, M3.primary, '700'));
  f.children.push(rect('dlg3_save_btn', 324, d3Y + 184, 56, 32, M3.primary, 16));
  f.children.push(text('dlg3_save_txt', 'Save', 338, d3Y + 193, 13, '#FFFFFF', '700'));

  return f;
}

// ----------------------------------------------------------------------------
// FRAME 5: Aba Event Validada (fr_logic_list.xml + fr_logic_list_item.xml + NavigationRailView)
// ----------------------------------------------------------------------------
function buildFrame05() {
  const f = frame(
    '05 - Aba Event Nativa (rs.java + fr_logic_list.xml + fr_logic_list_item.xml)',
    1840,
    0,
    412,
    900,
    M3.surface
  );

  // AppBar de design.xml com a Aba 1 (Event) selecionada!
  addDesignAppBar(f, 1, 'SuperCatJump', '608');

  // Layout exato de fr_logic_list.xml:
  // Esquerda: NavigationRailView (R.id.palette com @menu/logic_palette: Activity, View, Component, Drawer, More block)
  const bodyY = 121;
  const bodyH = 723;
  f.children.push(rect('nav_rail', 0, bodyY, 80, bodyH, M3.surface));
  f.children.push(rect('nav_rail_div', 80, bodyY, 1, bodyH, M3.outlineVariant));

  const railItems = [
    { id: 'activity', icon: '🔄', label: 'Activity', sel: false },
    { id: 'view', icon: '🎮', label: 'View', sel: true },
    { id: 'component', icon: '⚙', label: 'Component', sel: false },
    { id: 'drawer', icon: '☰', label: 'Drawer', sel: false },
    { id: 'moreblock', icon: '🧩', label: 'More block', sel: false },
  ];
  railItems.forEach((ri, idx) => {
    const ry = bodyY + 16 + idx * 72;
    if (ri.sel) {
      f.children.push(rect(`rail_pill_${ri.id}`, 14, ry, 52, 30, M3.secondaryContainer, 15));
    }
    f.children.push(
      text(
        `rail_ic_${ri.id}`,
        ri.icon,
        32,
        ry + 6,
        14,
        ri.sel ? M3.onSecondaryContainer : M3.onSurfaceVariant,
        '700'
      )
    );
    f.children.push(
      text(
        `rail_lbl_${ri.id}`,
        ri.label,
        14,
        ry + 36,
        10,
        ri.sel ? M3.onSurface : M3.onSurfaceVariant,
        ri.sel ? '700' : '500'
      )
    );
  });

  // Direita: R.id.event_list (RecyclerView com itens fr_logic_list_item.xml)
  const eventsList = [
    {
      icon: '🐱',
      color: '#F97316',
      targetType: 'Sprite',
      targetId: 'sprite1',
      eventText: 'whenGameStart (Quando o jogo iniciar 🚩)',
      expanded: false,
    },
    {
      icon: '🐱',
      color: '#F97316',
      targetType: 'Sprite',
      targetId: 'sprite1',
      eventText: 'onCollisionWith (Ao colidir com tilemap1 💥)',
      expanded: true,
    },
    {
      icon: '🐱',
      color: '#F97316',
      targetType: 'Sprite',
      targetId: 'sprite1',
      eventText: 'onBroadcastReceived ("msg_level_up" 📩)',
      expanded: false,
    },
    {
      icon: '🕹',
      color: '#575992',
      targetType: 'Joystick',
      targetId: 'joystick1',
      eventText: 'onJoystickMoved (angle, strength)',
      expanded: false,
    },
    {
      icon: '🔘',
      color: '#E11D48',
      targetType: 'TouchButton',
      targetId: 'touchbutton_jump',
      eventText: 'onButtonPressed (Quando botão tocado)',
      expanded: false,
    },
    {
      icon: '🧬',
      color: '#0FBD8C',
      targetType: 'Sprite Clone',
      targetId: 'coin_prefab',
      eventText: 'whenCloneStarts (Ao iniciar clone)',
      expanded: false,
    },
  ];

  let ey = bodyY + 12;
  eventsList.forEach((ev, idx) => {
    const cardH = ev.expanded ? 114 : 72;
    f.children.push(
      rect(`flli_card_${idx}`, 90, ey, 312, cardH, M3.surfaceContainerLowest, 14, {
        strokes: stroke(ev.expanded ? M3.primary : M3.outlineVariant, ev.expanded ? 1.5 : 1),
        effects: shadow('rgba(0,0,0,0.06)', 0, 2, 6),
      })
    );

    // R.id.img_icon (24dp)
    f.children.push(rect(`flli_ic_bg_${idx}`, 104, ey + 20, 32, 32, ev.color, 8));
    f.children.push(text(`flli_ic_${idx}`, ev.icon, 113, ey + 27, 14, '#FFFFFF', '700'));

    // R.id.tv_target_type (11sp) + R.id.tv_target_id (14sp) + R.id.tv_event_text (12sp)
    f.children.push(text(`flli_tt_${idx}`, ev.targetType, 148, ey + 12, 10, M3.onSurfaceVariant, '500'));
    f.children.push(text(`flli_tid_${idx}`, ev.targetId, 148, ey + 26, 13, M3.onSurface, '700'));
    f.children.push(text(`flli_et_${idx}`, `⚡ ${ev.eventText}`, 148, ey + 45, 10.5, M3.primary, '600'));

    // R.id.img_menu (ic_arrow_drop_down_24px)
    f.children.push(text(`flli_drop_${idx}`, ev.expanded ? '▴' : '▾', 378, ey + 28, 14, M3.onSurfaceVariant, '700'));

    // Se expandido, mostra fr_logic_list_item_buttons.xml (Reset / Delete)
    if (ev.expanded) {
      f.children.push(rect(`flli_div_${idx}`, 102, ey + 70, 288, 1, M3.outlineVariant));
      f.children.push(rect(`flli_btn_reset_${idx}`, 114, ey + 76, 124, 30, M3.surfaceContainerLow, 8));
      f.children.push(text(`flli_txt_reset_${idx}`, '↺ Reset Blocks', 136, ey + 85, 11, M3.onSurface, '600'));
      f.children.push(rect(`flli_btn_del_${idx}`, 254, ey + 76, 124, 30, '#FEE2E2', 8));
      f.children.push(text(`flli_txt_del_${idx}`, '🗑 Delete Event', 274, ey + 85, 11, M3.error, '700'));
    }

    ey += cardH + 10;
  });

  // FloatingActionButton R.id.fab em fr_logic_list.xml (canto inferior direito, abre AddEventActivity)
  f.children.push(
    rect('fab_add_event', 340, 772, 56, 56, M3.primaryContainer, 16, {
      strokes: stroke(M3.primary, 1.5),
      effects: shadow('rgba(87,89,146,0.28)', 0, 6, 14),
    })
  );
  f.children.push(text('fab_add_ic', '+', 361, 787, 24, M3.onPrimaryContainer, '700'));

  // Barra inferior de design.xml
  addDesignBottomBar(f, 844, 'main.xml');

  return f;
}

// ----------------------------------------------------------------------------
// FRAME 6: LogicEditorActivity Validado (logic_editor.xml + PaletteSelector Scratch 3)
// ----------------------------------------------------------------------------
function buildFrame06() {
  const f = frame(
    '06 - LogicEditorActivity Nativo (logic_editor.xml + PaletteSelector com 10 Categorias Scratch 3)',
    2300,
    0,
    412,
    900,
    M3.surface
  );

  // Status Bar
  f.children.push(rect('status_bar', 0, 0, 412, 24, M3.surface));
  f.children.push(text('time', '22:45', 14, 5, 11, M3.onSurface, '600'));

  // Toolbar exata de logic_editor.xml (R.id.toolbar + R.id.syntax_check_container)
  f.children.push(rect('logic_toolbar', 0, 24, 412, 54, M3.surface));
  f.children.push(rect('logic_back', 8, 33, 36, 36, M3.surfaceContainerLow, 18));
  f.children.push(text('logic_back_ic', '←', 19, 41, 16, M3.onSurface, '700'));
  f.children.push(text('logic_title', 'sprite1 : whenGameStart', 52, 33, 15, M3.onSurface, '700'));
  f.children.push(text('logic_sub', 'layout/logic_editor.xml • BlockPane + PaletteSelector', 52, 53, 10, M3.onSurfaceVariant, '500'));

  // R.id.syntax_check_container em logic_editor.xml
  f.children.push(rect('syntax_check_container', 330, 35, 74, 30, '#DCFCE7', 15));
  f.children.push(text('syntax_txt', '✓ Syntax', 342, 44, 11, '#15803D', '700'));
  f.children.push(rect('logic_tb_div', 0, 78, 412, 1, M3.outlineVariant));

  // R.id.editor (ViewLogicEditor / BlockPane) na parte superior (79..515)
  f.children.push(rect('block_pane', 0, 79, 412, 436, '#F5F2FA'));

  // Pilha de blocos Scratch 3 renderizados pelo BlockCopyInterface / Rs.java no BlockPane
  const bx = 18;
  let by = 92;

  // Hat Block (Eventos - #FFBF00)
  f.children.push(rect('hat_top', bx, by, 140, 10, SCRATCH.events, 6));
  f.children.push(rect('hat_body', bx, by + 6, 268, 32, SCRATCH.events, 6, { strokes: stroke('#D97706', 1) }));
  f.children.push(text('hat_txt', '🚩 quando o jogo iniciar (whenGameStart)', bx + 10, by + 15, 11, '#1E293B', '700'));
  by += 39;

  // Movimento: ir para x: [120] y: [240] (#4C97FF)
  f.children.push(rect('blk_goto', bx, by, 238, 30, SCRATCH.motion, 5, { strokes: stroke('#2563EB', 1) }));
  f.children.push(text('blk_goto_t1', 'ir para x:', bx + 10, by + 8, 11, '#FFFFFF', '700'));
  f.children.push(rect('blk_goto_p1', bx + 70, by + 5, 42, 20, '#FFFFFF', 10));
  f.children.push(text('blk_goto_v1', '120', bx + 80, by + 9, 10, '#1E293B', '700'));
  f.children.push(text('blk_goto_t2', 'y:', bx + 120, by + 8, 11, '#FFFFFF', '700'));
  f.children.push(rect('blk_goto_p2', bx + 136, by + 5, 42, 20, '#FFFFFF', 10));
  f.children.push(text('blk_goto_v2', '240', bx + 146, by + 9, 10, '#1E293B', '700'));
  by += 31;

  // Aparência: mudar fantasia para [cat_run_1 ▾] (#9966FF)
  f.children.push(rect('blk_costume', bx, by, 264, 30, SCRATCH.looks, 5, { strokes: stroke('#7C3AED', 1) }));
  f.children.push(text('blk_cost_t', 'mudar fantasia para', bx + 10, by + 8, 11, '#FFFFFF', '700'));
  f.children.push(rect('blk_cost_p', bx + 136, by + 5, 116, 20, '#7C3AED', 6));
  f.children.push(text('blk_cost_v', 'cat_run_1 ▾', bx + 146, by + 9, 10, '#FFFFFF', '700'));
  by += 31;

  // Controle C-Block: sempre (forever - #FFAB19)
  f.children.push(rect('blk_forever_top', bx, by, 290, 28, SCRATCH.control, 5));
  f.children.push(text('blk_forever_t', '🔁 sempre (GameLoop 60 FPS)', bx + 10, by + 7, 11, '#FFFFFF', '700'));
  f.children.push(rect('blk_forever_arm', bx, by + 26, 16, 206, SCRATCH.control));

  let cy = by + 29;
  const cx = bx + 16;

  // C-Block interno: se <tocando em [tilemap_coin ▾]?> então
  f.children.push(rect('blk_if_top', cx, cy, 316, 30, SCRATCH.control, 5));
  f.children.push(text('blk_if_t1', 'se', cx + 8, cy + 8, 11, '#FFFFFF', '700'));
  // Hexágono Sensores (#5CB1D6)
  f.children.push(rect('blk_sens_hex', cx + 30, cy + 4, 214, 22, SCRATCH.sensing, 11));
  f.children.push(text('blk_sens_txt', '⬡ tocando em [coin_prefab ▾] ?', cx + 40, cy + 8, 10, '#FFFFFF', '700'));
  f.children.push(text('blk_if_t2', 'então', cx + 252, cy + 8, 11, '#FFFFFF', '700'));
  f.children.push(rect('blk_if_arm', cx, cy + 28, 14, 96, SCRATCH.control));

  let icy = cy + 31;
  const icx = cx + 14;

  // Som: tocar som [coin_pickup ▾] (#CF63CF)
  f.children.push(rect('blk_snd', icx, icy, 246, 28, SCRATCH.sound, 5));
  f.children.push(text('blk_snd_t', '🔊 tocar som [coin_pickup ▾]', icx + 10, icy + 7, 10.5, '#FFFFFF', '700'));
  icy += 29;

  // Variáveis: adicione [10] a [score ▾] (#FF8C1A)
  f.children.push(rect('blk_var', icx, icy, 246, 28, SCRATCH.variables, 5));
  f.children.push(text('blk_var_t', '📊 adicione ( 10 ) a [score ▾]', icx + 10, icy + 7, 10.5, '#FFFFFF', '700'));
  icy += 29;

  // Partículas / Pen (#0FBD8C)
  f.children.push(rect('blk_fx', icx, icy, 260, 28, SCRATCH.pen, 5));
  f.children.push(text('blk_fx_t', '✨ emitir [15] partículas em [sprite1 ▾]', icx + 10, icy + 7, 10.5, '#FFFFFF', '700'));

  // Fechamento do se
  f.children.push(rect('blk_if_bot', cx, cy + 122, 120, 14, SCRATCH.control, 4));

  // Movimento dentro do sempre: mova (joystick1.strength * 5) passos
  const mvy = cy + 138;
  f.children.push(rect('blk_move_op', cx, mvy, 304, 30, SCRATCH.motion, 5));
  f.children.push(text('blk_move_t', 'mova', cx + 10, mvy + 8, 11, '#FFFFFF', '700'));
  f.children.push(rect('blk_op_mul', cx + 50, mvy + 4, 186, 22, SCRATCH.operators, 11));
  f.children.push(text('blk_op_txt', '( joystickX ) * ( 5.0 )', cx + 66, mvy + 8, 10, '#FFFFFF', '700'));
  f.children.push(text('blk_move_t2', 'passos', cx + 244, mvy + 8, 11, '#FFFFFF', '700'));

  // se tocar na borda, volte
  f.children.push(rect('blk_edge', cx, mvy + 31, 210, 26, SCRATCH.motion, 5));
  f.children.push(text('blk_edge_t', 'se tocar na borda, volte', cx + 10, mvy + 37, 10.5, '#FFFFFF', '700'));

  // Fechamento do sempre
  f.children.push(rect('blk_forever_bot', bx, by + 230, 140, 16, SCRATCH.control, 4));

  // --------------------------------------------------------------------------
  // Parte Inferior de logic_editor.xml: R.id.area_palette (PaletteBlock) +
  // R.id.right_drawer (LogicEditorDrawer / PaletteSelector com as 10 Categorias Scratch 3)
  // + R.id.fab_toggle_palette (FloatingActionButton no canto inferior direito)
  // --------------------------------------------------------------------------
  const palY = 515;
  const palH = 385;
  f.children.push(rect('palette_top_div', 0, palY - 1, 412, 1, M3.outlineVariant));
  f.children.push(rect('area_palette', 0, palY, 276, palH, M3.surfaceContainerLowest));

  f.children.push(
    text('pal_header', 'Blocos da Categoria: Movimento', 12, palY + 10, 11, SCRATCH.motion, '700')
  );

  const samplePaletteBlocks = [
    { label: 'mova ( 10 ) passos', spec: 'scratchMoveSteps' },
    { label: 'gire ↻ ( 15 ) graus', spec: 'scratchTurnRight' },
    { label: 'gire ↺ ( 15 ) graus', spec: 'scratchTurnLeft' },
    { label: 'vá para x: ( 0 ) y: ( 0 )', spec: 'scratchGoToXY' },
    { label: 'deslize por ( 1 ) seg. até x: ( 0 ) y: ( 0 )', spec: 'scratchGlideToXY' },
    { label: 'aponte para a direção ( 90 )', spec: 'scratchPointInDirection' },
    { label: 'adicione ( 10 ) a x', spec: 'scratchChangeXBy' },
    { label: 'adicione ( 10 ) a y', spec: 'scratchChangeYBy' },
    { label: 'aplique impulso x: ( 0 ) y: ( -12 )', spec: 'gameApplyImpulse' },
    { label: 'se tocar na borda, volte', spec: 'scratchIfOnEdgeBounce' },
  ];

  samplePaletteBlocks.forEach((b, idx) => {
    const py = palY + 32 + idx * 33;
    f.children.push(rect(`pblk_${idx}`, 10, py, 254, 28, SCRATCH.motion, 5));
    f.children.push(text(`pblk_t_${idx}`, b.label, 18, py + 7, 10, '#FFFFFF', '700'));
  });

  // R.id.right_drawer (LogicEditorDrawer com PaletteSelector.java mostrando as 10 categorias do Scratch 3!)
  f.children.push(rect('right_drawer_div', 276, palY, 1, palH, M3.outlineVariant));
  f.children.push(rect('right_drawer_cats', 277, palY, 135, palH, M3.surfaceContainerLow));

  const scratchCategories = [
    { name: 'Movimento', color: SCRATCH.motion, sel: true },
    { name: 'Aparência', color: SCRATCH.looks, sel: false },
    { name: 'Som', color: SCRATCH.sound, sel: false },
    { name: 'Eventos', color: SCRATCH.events, sel: false },
    { name: 'Controle', color: SCRATCH.control, sel: false },
    { name: 'Sensores', color: SCRATCH.sensing, sel: false },
    { name: 'Operadores', color: SCRATCH.operators, sel: false },
    { name: 'Variáveis', color: SCRATCH.variables, sel: false },
    { name: 'Meus Blocos', color: SCRATCH.myblocks, sel: false },
    { name: 'Caneta / Física', color: SCRATCH.pen, sel: false },
  ];

  scratchCategories.forEach((cat, idx) => {
    const cy = palY + 8 + idx * 32;
    f.children.push(
      rect(
        `cat_${idx}`,
        282,
        cy,
        124,
        28,
        cat.sel ? M3.primaryContainer : M3.surfaceContainerLowest,
        6,
        { strokes: cat.sel ? stroke(cat.color, 1.5) : [] }
      )
    );
    f.children.push(rect(`cat_dot_${idx}`, 288, cy + 6, 12, 16, cat.color, 3));
    f.children.push(
      text(
        `cat_nm_${idx}`,
        cat.name,
        306,
        cy + 7,
        10.5,
        cat.sel ? M3.onPrimaryContainer : M3.onSurface,
        cat.sel ? '700' : '600'
      )
    );
  });

  // R.id.fab_toggle_palette em logic_editor.xml (canto inferior direito)
  f.children.push(
    rect('fab_toggle_palette', 344, 832, 52, 52, M3.primary, 16, {
      effects: shadow('rgba(87,89,146,0.3)', 0, 4, 12),
    })
  );
  f.children.push(text('fab_pal_ic', '🎨', 361, 848, 16, '#FFFFFF', '700'));

  return f;
}

frames.push(buildFrame01());
frames.push(buildFrame02());
frames.push(buildFrame03());
frames.push(buildFrame04());
frames.push(buildFrame05());
frames.push(buildFrame06());

// Converte a árvore de frames em comandos nativos do figcraft-cli run
const commands = [
  {
    command: 'file.rename',
    params: { name: 'Sketchware-IA - Motor Game 2D (Scratch 3) - Layouts Validados' },
  },
  {
    command: 'page.rename',
    params: { name: 'Layouts Validados do Sketchware-IA (Game 2D + Scratch 3)' },
  },
  {
    command: 'page.setBackground',
    params: { color: '#E4E1E9' },
  },
];

// No FigCraft, um documento novo começa com a página de id=1; o primeiro nó criado recebe id=2, e assim por diante.
let figNodeId = 1;

for (const f of frames) {
  const frameId = ++figNodeId;
  commands.push({
    command: 'frame.create',
    params: {
      name: f.name,
      x: f.x,
      y: f.y,
      width: f.width,
      height: f.height,
      props: {
        fill: f.fills?.[0]?.color || '#FAF8FF',
        cornerRadius: 18,
        clip: true,
        stroke: '#C8C5D0',
        strokeWeight: 1.5,
      },
      select: false,
    },
  });

  for (const child of f.children) {
    ++figNodeId;
    const fillHex = child.fills?.[0]?.color || '#FFFFFF';
    const opacity = child.fills?.[0]?.opacity ?? child.opacity ?? 1;
    const strokeObj = child.strokes?.[0];

    if (child.type === 'RECTANGLE') {
      const props = {};
      if (child.fills && child.fills.length > 0) {
        props.fill = fillHex;
      }
      if (opacity < 1) {
        props.opacity = opacity;
      }
      if (child.cornerRadius) {
        props.cornerRadius = child.cornerRadius;
      }
      if (strokeObj) {
        props.stroke = strokeObj.color;
        props.strokeWeight = strokeObj.weight || 1;
      }
      commands.push({
        command: 'shape.rectangle',
        params: {
          parent: frameId,
          name: child.name,
          x: child.x,
          y: child.y,
          width: child.width,
          height: child.height,
          props,
          select: false,
        },
      });
    } else if (child.type === 'ELLIPSE') {
      const props = { fill: fillHex };
      if (opacity < 1) {
        props.opacity = opacity;
      }
      if (strokeObj) {
        props.stroke = strokeObj.color;
        props.strokeWeight = strokeObj.weight || 1;
      }
      commands.push({
        command: 'shape.ellipse',
        params: {
          parent: frameId,
          name: child.name,
          x: child.x,
          y: child.y,
          width: child.width,
          height: child.height,
          props,
          select: false,
        },
      });
    } else if (child.type === 'TEXT') {
      const weightNum = parseInt(child.fontWeight || '500', 10);
      const fontStyle = weightNum >= 700 ? 'Bold' : weightNum >= 600 ? 'SemiBold' : 'Regular';
      commands.push({
        command: 'text.create',
        params: {
          parent: frameId,
          name: child.name,
          text: child.text,
          x: child.x,
          y: child.y,
          props: {
            fontSize: child.fontSize || 12,
            fill: fillHex,
            fontFamily: 'Inter',
            fontStyle,
            textAlign: (child.textAlignHorizontal || 'left').toLowerCase(),
          },
          select: false,
        },
      });
    }
  }
}

const cmdPath = path.join(__dirname, 'native_game2d_commands.json');
fs.writeFileSync(cmdPath, JSON.stringify(commands, null, 2), 'utf8');
console.log('Comandos FigCraft gerados:', cmdPath, 'Total comandos:', commands.length);

