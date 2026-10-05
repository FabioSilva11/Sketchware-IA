package com.besome.sketch.editor.manage.font;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AddFontActivityTest {
    @Test
    public void fontNameComesFromTheFileName() {
        // Was "onte_teste." : the first letter was dropped and the dot kept
        assertEquals("fonte_teste", AddFontActivity.fontNameFromFile("fonte_teste.ttf"));
        assertEquals("open_sans_bold", AddFontActivity.fontNameFromFile("Open Sans-Bold.TTF"));
        assertEquals("rattfont", AddFontActivity.fontNameFromFile("rattfont.ttf"));
        assertEquals("font_2024", AddFontActivity.fontNameFromFile("2024.otf"));
    }
}
