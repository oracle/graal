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

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * The actual component for changing the CFG visualizer settings.
 *
 * @author Bernhard Stiftner
 * @author Rumpfhuber Stefan
 */
public class CFGOptionsPanel extends JPanel {

    List<ConfigurationElement> elements = new ArrayList<ConfigurationElement>();

    /** Creates a new instance of CFGOptionsPanel */
    public CFGOptionsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        JPanel cfgPanel = new JPanel(new GridBagLayout());

        // color/font settings
        addColorChooser(cfgPanel, "Background color: ", new ColorChooser(CfgPreferences.PROP_BACKGROUND_COLOR){
            @Override
            public void apply() {
                CfgPreferences.getInstance().setBackgroundColor(getColor());              
            }

            @Override
            public void update() {
               this.originalColor = CfgPreferences.getInstance().getBackgroundColor();
               setColor(this.originalColor);
            }
            
             public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_BACKGROUND_COLOR);
            }
            
            
           
        });
        addColorChooser(cfgPanel, "Back edge color: ", new ColorChooser(CfgPreferences.PROP_BACK_EDGE_COLOR){

            @Override
            public void apply() {               
                CfgPreferences.getInstance().setBackedgeColor(getColor());                 
            }

            @Override
            public void update() {
                this.originalColor = CfgPreferences.getInstance().getBackedgeColor();
                this.setColor(this.originalColor);

            }

            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_BACKEDGE_COLOR);
            }
        });
        addColorChooser(cfgPanel, "Edge color: ", new ColorChooser(CfgPreferences.PROP_EDGE_COLOR){

            @Override
            public void apply() {               
                CfgPreferences.getInstance().setEdgeColor(getColor());  
            }

            @Override
            public void update() {
                this.originalColor = CfgPreferences.getInstance().getEdgeColor(); 
                setColor(this.originalColor);
            }
            
            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_EDGE_COLOR);
            }
            
        });
        addColorChooser(cfgPanel, "Exception edge color: ", new ColorChooser(CfgPreferences.PROP_EXCEPTION_EDGE_COLOR){

            @Override
            public void apply() {             
                CfgPreferences.getInstance().setExceptionEdgeColor(getColor());  
            }

            @Override
            public void update() {
                this.originalColor = CfgPreferences.getInstance().getExceptionEdgeColor();
                setColor(this.originalColor);
                
            }
            
            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_EXCEPTIONEDGE_COLOR);
            }
        });
        addColorChooser(cfgPanel, "Node color: ", new ColorChooser(CfgPreferences.PROP_NODE_COLOR){

            @Override
            public void apply() {
               CfgPreferences.getInstance().setNodeColor(getColor());  
            }

            @Override
            public void update() {
                this.originalColor = CfgPreferences.getInstance().getNodeColor();
                setColor(this.originalColor);
                
            }
            
            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAUT_NODE_COLOR);
            }
        });
        addColorChooser(cfgPanel, "Text color: ", new ColorChooser(CfgPreferences.PROP_TEXT_COLOR){

            @Override
            public void apply() {
               CfgPreferences.getInstance().setTextColor(getColor());  
            }

            @Override
            public void update() {
               this.originalColor = CfgPreferences.getInstance().getTextColor();
               
            }
            
            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_TEXT_COLOR);
            }
        });     
        addColorChooser(cfgPanel, "Border color: ", new ColorChooser(CfgPreferences.PROP_BORDER_COLOR){

            @Override
            public void apply() {
                CfgPreferences.getInstance().setBorderColor(getColor());  
            }

            @Override
            public void update() {
                this.originalColor = CfgPreferences.getInstance().getBorderColor();
                setColor(this.originalColor);
                
            }
            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_BORDER_COLOR);
            }
        });
        addColorChooser(cfgPanel, "Selected Nodes color: ", new ColorChooser(CfgPreferences.PROP_SELECTION_COLOR_FG){

            @Override
            public void apply() {
                CfgPreferences.getInstance().setSelectionColorForeground(getColor());  
            }

            @Override
            public void update() {
                this.originalColor = CfgPreferences.getInstance().getSelectionColorForeground();
                setColor(this.originalColor);
            }
            
            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_SELECTION_COLOR_FOREGROUND);
            }
        });
        addColorChooser(cfgPanel, "Selection Rect color: ", new ColorChooser(CfgPreferences.PROP_SELECTION_COLOR_BG){

            @Override
            public void apply() {
                CfgPreferences.getInstance().setSelectionColorBackground(getColor());
            }

            @Override
            public void update() {
                this.originalColor = CfgPreferences.getInstance().getSelectionColorBackground();
                setColor(this.originalColor);
            }
            
            public void reset() {
                this.setColor(CfgPreferencesDefaults.DEFAULT_SELECTION_COLOR_BACKGROUND);
            }
        });
        addFontChooser(cfgPanel, "Text font: ", new FontChooser(CfgPreferences.PROP_TEXT_FONT){

            @Override
            public void apply() {
                CfgPreferences.getInstance().setTextFont(getSelectedFont());                
            }

            @Override
            public void update() {
                this.originalFont = CfgPreferences.getInstance().getTextFont();
                this.setSelectedFont(originalFont);
                
            }
            
            public void reset() {
                this.setSelectedFont(CfgPreferencesDefaults.DEFAULT_TEXT_FONT);
            }
        });

        // flags editor
        addFlagsEditor(cfgPanel, "Flags: ");
        add(cfgPanel);

        // add update button
        Box hBox = new Box(BoxLayout.X_AXIS);
        hBox.add(new JButton(new ResetAction()));
        hBox.add(Box.createHorizontalGlue());
        add(hBox);

        add(Box.createVerticalGlue());
    }
    
    public void update() {
        for (ConfigurationElement e : elements) {          
            e.update();
        }
    }

    public void cancel() {
        for (ConfigurationElement e : elements) {
            e.update();
        }
    }

    public void applyChanges() {
        for (ConfigurationElement e : elements) {
            if(e.isChanged())
                e.apply();
        }
    }

    public boolean isDataValid() {
        return true;
    }

    public boolean isChanged() {
        for (ConfigurationElement e : elements) {
            if (e.isChanged()) {
                return true;
            }
        }
        return false;
    }
    
    public void loadDefault() {
        for (ConfigurationElement e : elements) {
            e.reset();
        }
    }

    private void addColorChooser(JComponent c, String displayName, ColorChooser chooser) {
        GridBagLayout layout = (GridBagLayout)c.getLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        JLabel label = new JLabel(displayName, JLabel.TRAILING);
        layout.setConstraints(label, constraints);
        c.add(label);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        layout.setConstraints(chooser, constraints);
        c.add(chooser);
        elements.add(chooser);
        label.setLabelFor(chooser);
    }

   

    private void addFontChooser(JComponent c, String displayName, FontChooser chooser) {
        GridBagLayout layout = (GridBagLayout)c.getLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        JLabel label = new JLabel(displayName, JLabel.TRAILING);
        layout.setConstraints(label, constraints);
        c.add(label);
        constraints.insets = new Insets(3, 3, 3, 1);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.CENTER;
        layout.setConstraints(chooser.getPreview(), constraints);
        c.add(chooser.getPreview());
        constraints.insets = new Insets(3, 1, 3, 3);
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        layout.setConstraints(chooser.getButton(), constraints);
        c.add(chooser.getButton());
        elements.add(chooser);
        label.setLabelFor(chooser.getButton());
    }

    private void addFlagsEditor(JComponent c, String displayName) {
        GridBagLayout layout = (GridBagLayout)c.getLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.NORTHEAST;
        FlagsEditor flagsEditor = new FlagsEditor();
        JLabel flagsLabel = new JLabel(displayName, JLabel.TRAILING);
        flagsLabel.setVerticalAlignment(SwingConstants.TOP);
        layout.setConstraints(flagsLabel, constraints);
        c.add(flagsLabel);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        layout.setConstraints(flagsEditor, constraints);
        c.add(flagsEditor);
        elements.add(flagsEditor);
        flagsLabel.setLabelFor(flagsEditor);
    }

    
    

    interface ConfigurationElement {

        public boolean isChanged();
        
        //apply changes to preferences
        public void apply();
        
        //optain current value from preferences
        public void update();
        
        //reset to default value
        public void reset();
        
    }
    
    abstract class ColorChooser extends ColorChooserButton implements ConfigurationElement {

        Color originalColor;//the color before any change
        String propertyName;

        public ColorChooser(String propertyName) {
            this.propertyName = propertyName;
        }
        
        public boolean isChanged() {
            return !originalColor.equals(getColor());
        }
        
        
        public abstract void apply();
        public abstract void update();

    }
    

    abstract class FontChooser implements ConfigurationElement, ActionListener {

        Font originalFont;
        Font selectedFont;
        String propertyName;
        JTextField preview;
        JButton button;

        public FontChooser(String propertyName) {
            this.propertyName = propertyName;
            preview = new JTextField("");
            preview.setEditable(false);
            button = new JButton("...");
            button.setMargin(new Insets(0, 0, 0, 0));
            button.addActionListener(this);
        }

        public boolean isChanged() {
            return !originalFont.equals(selectedFont);
        }
        
        public abstract void apply();
        
        public abstract void update();
        
        public abstract void reset();

        public JTextField getPreview() {
            return preview;
        }

        public JButton getButton() {
            return button;
        };

        public Font getSelectedFont() {
            return selectedFont;
        }

        public void setSelectedFont(Font f) {
            selectedFont = f;
            preview.setText(fontToString(f));
            preview.revalidate();
        }

        public void actionPerformed(ActionEvent e) {
            setSelectedFont(FontChooserDialog.show(selectedFont));
        }

        public String fontToString(Font font) {
            StringBuffer sb = new StringBuffer();
            sb.append(font.getName());
            sb.append(" ");
            sb.append(font.getSize());
            if (font.isBold()) {
                sb.append(" bold");
            }
            if (font.isItalic()) {
                sb.append(" italic");
            }
            return sb.toString();
        }

    }

    class FlagsEditor extends FlagsEditorPanel implements ConfigurationElement {

        FlagsSetting originalFlags;

        public FlagsEditor() {
            super(null);    
        }

        public boolean isChanged() {
            return !originalFlags.getFlagString().equals(getFlagString());
        }

        public void apply() {
            CfgPreferences.getInstance().setFlagsSetting(new FlagsSetting(getFlagString()));
            update();
        }

        public void update() {
            originalFlags = CfgPreferences.getInstance().getFlagsSetting();
            setFlagString(originalFlags.getFlagString());
        }

        public void reset() {
            FlagsSetting defaultFlags = new FlagsSetting(CfgPreferencesDefaults.DEFAULT_FLAGSTRING);
            setFlagString(defaultFlags.getFlagString());
            colorButton.setColor(getParent().getBackground());            
        }

    }

    class ResetAction extends AbstractAction {

        public ResetAction() {
            super("Reset");
        }

        public void actionPerformed(ActionEvent e) {
            CFGOptionsPanel.this.loadDefault();           
        }

    }

}
