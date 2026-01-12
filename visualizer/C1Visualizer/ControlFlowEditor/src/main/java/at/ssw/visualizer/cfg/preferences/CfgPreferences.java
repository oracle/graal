/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package at.ssw.visualizer.cfg.preferences;

import at.ssw.visualizer.cfg.editor.CfgEditorTopComponent;
import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.prefs.Preferences;
import javax.swing.event.EventListenerList;
import org.openide.util.NbPreferences;

/**
 * Replacement for old CFGSettings to remove dependency on deprecated SystemOption package
 * 
 * @author Rumpfhuber Stefan
 */
public class CfgPreferences  {
    public static final String PROP_FLAGS = "flagsPreference";
    public static final String PROP_TEXT_FONT = "textFontPreference";
    public static final String PROP_TEXT_COLOR = "textColorPreference";
    public static final String PROP_NODE_COLOR = "nodeColorPreference";
    public static final String PROP_EDGE_COLOR = "edgeColorPreference";
    public static final String PROP_BORDER_COLOR = "borderColorPreference";
    public static final String PROP_BACK_EDGE_COLOR = "backEdgeColorPreference";
    public static final String PROP_BACKGROUND_COLOR = "backgroundColorPreference";
    public static final String PROP_SELECTION_COLOR_FG = "selectionColorFgPreference";
    public static final String PROP_SELECTION_COLOR_BG = "selectionColorBgPreference";
    public static final String PROP_EXCEPTION_EDGE_COLOR = "exceptionEdgeColorPreference";
    
    private static final String PROP_FONTNAME = "_FontFamily";
    private static final String PROP_FONTSIZE = "_FontSize";
    private static final String PROP_FONTSTYLE = "_FontStyle";
    
    protected static final String nodeName = "CfgPreferences";
      
    private static CfgPreferences instance = new CfgPreferences();
    private EventListenerList listenerList;
       
    private FlagsSetting flagsSetting;
    private Color node_color; 
    private Color background_color; 
    private Color backedge_color; 
    private Color edge_color;
    private Color border_color;
    private Color exceptionEdgeColor;
    private Color text_color;
    private Font  text_font;
    private Color selection_color_fg;
    private Color selection_color_bg;
    
    
    private  CfgPreferences(){        
        listenerList = new EventListenerList();       
        init();           
    }
    
    public static CfgPreferences getInstance(){
        return instance;
    }

    public void addPropertyChangeListener(CfgEditorTopComponent listener) {
        listenerList.add(PropertyChangeListener.class, listener);   
    }

    public void removePropertyChangeListener(CfgEditorTopComponent listener) {
        listenerList.remove(PropertyChangeListener.class, listener);
    }
 
    protected final Preferences getPreferences() {
        return NbPreferences.forModule(this.getClass()).node("options").node(nodeName);
    }
    
    
    protected void init(){
        Preferences prefs = this.getPreferences();
        String flagString = prefs.get(PROP_FLAGS, CfgPreferencesDefaults.DEFAULT_FLAGSTRING);
        flagsSetting = new FlagsSetting(flagString);
        node_color = this.getColorProperty(PROP_NODE_COLOR, CfgPreferencesDefaults.DEFAUT_NODE_COLOR);
        background_color = this.getColorProperty(PROP_BACKGROUND_COLOR, CfgPreferencesDefaults.DEFAULT_BACKGROUND_COLOR); 
        backedge_color = this.getColorProperty(PROP_BACK_EDGE_COLOR, CfgPreferencesDefaults.DEFAULT_BACKEDGE_COLOR); 
        edge_color = this.getColorProperty(PROP_EDGE_COLOR, CfgPreferencesDefaults.DEFAULT_EDGE_COLOR);
        selection_color_fg= this.getColorProperty(PROP_SELECTION_COLOR_FG, CfgPreferencesDefaults.DEFAULT_SELECTION_COLOR_FOREGROUND);
        border_color = this.getColorProperty(PROP_BORDER_COLOR, CfgPreferencesDefaults.DEFAULT_BORDER_COLOR);
        exceptionEdgeColor = this.getColorProperty(PROP_EXCEPTION_EDGE_COLOR, CfgPreferencesDefaults.DEFAULT_EXCEPTIONEDGE_COLOR);
        text_color= this.getColorProperty(PROP_TEXT_COLOR, CfgPreferencesDefaults.DEFAULT_TEXT_COLOR);
        selection_color_bg = this.getColorProperty(PROP_SELECTION_COLOR_BG, CfgPreferencesDefaults.DEFAULT_SELECTION_COLOR_BACKGROUND);
        selection_color_fg = this.getColorProperty(PROP_SELECTION_COLOR_FG, CfgPreferencesDefaults.DEFAULT_SELECTION_COLOR_FOREGROUND);
        text_font = this.getFontProperty(PROP_TEXT_FONT, CfgPreferencesDefaults.DEFAULT_TEXT_FONT);     
    }
         
    private void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        Object[] listeners = listenerList.getListenerList();   
       
        PropertyChangeEvent event = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == PropertyChangeListener.class) {
                ((PropertyChangeListener) listeners[i+1]).propertyChange(event);
            }
        }
    }
    
    private Font getFontProperty(String propName, Font defaultFont){
        Preferences prefs = this.getPreferences();
        String fontName = prefs.get(propName+PROP_FONTNAME, defaultFont.getFamily());
        int fontSize = prefs.getInt(propName+PROP_FONTSIZE, defaultFont.getSize());
        int fontStyle = prefs.getInt(propName+PROP_FONTSTYLE, defaultFont.getStyle());                
        return new Font(fontName, fontStyle, fontSize);   
    }
       
    private Color getColorProperty(String propName, Color defaultColor){
        Preferences prefs = this.getPreferences();
        int srgb = prefs.getInt(propName, defaultColor.getRGB());
        if(srgb == defaultColor.getRGB())
            return defaultColor;
        return new Color(srgb);
    }
     
    public Color getBackedgeColor() {
        return backedge_color;
    }
  
    public Color getBackgroundColor() {
        return background_color;
    }

    public Color getBorderColor() {
        return border_color;
    }
 
    public Color getEdgeColor() {
        return edge_color;
    }

    public Color getExceptionEdgeColor() {
        return exceptionEdgeColor;
    }
   
    public Color getNodeColor() {
        return node_color;
    }

    public Color getSelectionColorForeground() {
        return selection_color_fg;
    }
    
    public Color getSelectionColorBackground() {
        return selection_color_bg;
    }

    public Color getTextColor() {
        return text_color;
    }

    public Font getTextFont() {
        return text_font;
    }

    public FlagsSetting getFlagsSetting() {
        return flagsSetting;
    }
    
    
    public void setFlagsSetting(FlagsSetting flagsSetting) {
        FlagsSetting old = this.getFlagsSetting();
        this.flagsSetting = flagsSetting;
        Preferences prefs = getPreferences();
        firePropertyChange(PROP_FLAGS, old, flagsSetting);
        prefs.put(PROP_FLAGS, flagsSetting.getFlagString());
    }
    

    public void setTextFont(Font text_font) {
        Font old = this.getTextFont();
        Preferences prefs = getPreferences();
        this.text_font = text_font;
        firePropertyChange(PROP_TEXT_FONT, old, text_font);
        prefs.put(PROP_TEXT_FONT + PROP_FONTNAME , text_font.getFamily());
        prefs.putInt(PROP_TEXT_FONT + PROP_FONTSIZE, text_font.getSize());
        prefs.putInt(PROP_TEXT_FONT + PROP_FONTSTYLE, text_font.getStyle());   
    }
    
   public void setBackedgeColor(Color backedge_color) {
        Color old = this.getBackedgeColor();        
        this.backedge_color = backedge_color;
        firePropertyChange(PROP_BACK_EDGE_COLOR, old, backedge_color);
        getPreferences().putInt(PROP_BACK_EDGE_COLOR, backedge_color.getRGB());        
    }

    public void setBackgroundColor(Color bg_color) {
        Color old = this.getBackgroundColor();
        background_color = bg_color;
        firePropertyChange(PROP_BACKGROUND_COLOR, old, bg_color );
        getPreferences().putInt(PROP_BACKGROUND_COLOR, bg_color.getRGB());      
    }

    public void setBorderColor(Color border_color) {
        Color old = getBorderColor();      
        this.border_color = border_color;
        firePropertyChange(PROP_BORDER_COLOR, old, border_color );
        getPreferences().putInt(PROP_BORDER_COLOR, border_color.getRGB());       
    }

    public void setEdgeColor(Color edge_color) {
        Color old = getEdgeColor();     
        this.edge_color = edge_color;
        firePropertyChange(PROP_EDGE_COLOR, old, edge_color);
        getPreferences().putInt(PROP_EDGE_COLOR, edge_color.getRGB());      
    }
    
    public void setNodeColor(Color node_color) {
        Color old = getNodeColor();      
        this.node_color = node_color;
        firePropertyChange(PROP_NODE_COLOR, old, node_color);
        getPreferences().putInt(PROP_NODE_COLOR, node_color.getRGB());
        
    }
    
    public void setSelectionColorForeground(Color selection_color) {
         Color old = this.getSelectionColorForeground();   
         this.selection_color_fg = selection_color;
         firePropertyChange(PROP_SELECTION_COLOR_FG, old, selection_color);
         getPreferences().putInt(PROP_SELECTION_COLOR_FG, selection_color.getRGB());       
    }
    
    public void setSelectionColorBackground(Color selection_color) {
         Color old = this.getSelectionColorBackground();   
         this.selection_color_bg = selection_color;
         firePropertyChange(PROP_SELECTION_COLOR_BG, old, selection_color);
         getPreferences().putInt(PROP_SELECTION_COLOR_BG, selection_color.getRGB());       
    }
    
    public void setTextColor(Color text_color) {
         Color old = this.getTextColor();       
         this.text_color = text_color;
         firePropertyChange(PROP_TEXT_COLOR, old, text_color);
         getPreferences().putInt(PROP_TEXT_COLOR, text_color.getRGB());     
    }
    
    public void setExceptionEdgeColor(Color exceptionEdgeColor) {
        Color old = this.getExceptionEdgeColor();  
        this.exceptionEdgeColor = exceptionEdgeColor;
        firePropertyChange(PROP_EXCEPTION_EDGE_COLOR, old, exceptionEdgeColor);
        getPreferences().putInt(PROP_EXCEPTION_EDGE_COLOR, exceptionEdgeColor.getRGB());       
    }

   

}
