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
package at.ssw.visualizer.cfg.action;

import at.ssw.visualizer.cfg.graph.CfgEventListener;
import at.ssw.visualizer.cfg.editor.CfgEditorTopComponent;
import at.ssw.visualizer.cfg.graph.CfgScene;
import at.ssw.visualizer.cfg.model.CfgNode;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicArrowButton;
import org.openide.util.HelpCtx;
import org.openide.util.actions.Presenter;

/**
 * Changes the background color of a node.
 *
 * @author Bernhard Stiftner
 * @author Rumpfhuber Stefan
 */
public class ColorAction extends AbstractCfgEditorAction implements Presenter.Menu, Presenter.Popup, Presenter.Toolbar {

    public static final int IMAGE_WIDTH = 16;
    public static final int IMAGE_HEIGHT = 16;
    
    
    /** Names of colors shown in color select lists. */
    private static final String[] COLOR_NAMES = {"White", "Light Gray", "Dark Gray", "Light Yellow", "Dark Yellow", "Light Green", "Dark Green", "Light Cyan", "Dark Cyan", "Light Blue", "Dark Blue", "Light Magenta", "Dark Magenta", "Light Red", "Dark Red"};

    /** Values of colors shown in color select lists. */
    private static final Color[] COLORS = {
        new Color(0xFFFFFF), new Color(0xD4D0C8), new Color(0xA4A098), new Color(0xF0F0B0), new Color(0xE0E040), 
        new Color(0xB0F0B0), new Color(0x40E040), new Color(0xB0F0F0), new Color(0x40E0E0), new Color(0xB0B0F0), 
        new Color(0x4040E0), new Color(0xF0B0F0), new Color(0xE040E0), new Color(0xF0B0B0), new Color(0xE04040)
    };

    public void performAction() {
        // nothing to do here, the presenters are supposed to call
        // performAction(Color)
    }

    protected void performAction(Color color) {
        CfgEditorTopComponent tc = getEditor();
        if (tc != null) {
            tc.getCfgScene().setSelectedNodesColor(color);
        }
    }

    public String getName() {
        return "Change NodeColor";
    }

    @Override
    protected String iconResource() {
        return "at/ssw/visualizer/cfg/icons/color.gif";
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public JMenuItem getMenuPresenter() {
        return new MenuPresenter();
    }

    @Override
    public JMenuItem getPopupPresenter() {
        return new MenuPresenter();
    }

    @Override
    public JComponent getToolbarPresenter() {
        return new ToolbarPresenter();
    }

    class MenuPresenter extends JMenu {
       
        public MenuPresenter() {
            super(ColorAction.this);
            initGUI();
        }
        
        protected void initGUI() {  
            CfgEditorTopComponent tc = getEditor();
            if( tc != null && tc.getCfgScene().getSelectedNodes().size()==0) {
                //no node selected
                setEnabled(false);
            } else {
                add(new SetColorAction(null, "Automatic"));
                for (int i = 0; i < COLORS.length; i++) {
                    add(new SetColorAction(COLORS[i], COLOR_NAMES[i]));
                }         
            }          
        }       
    }

    class ToolbarPresenter extends JButton implements CfgEventListener, MouseListener {

        final int arrowSize = 5;
        final int arrowMargin = 3;
        JPopupMenu popup;

        public ToolbarPresenter() {
            setIcon(createIcon());
            setToolTipText(ColorAction.this.getName());

            popup = new JPopupMenu();
            popup.add(new SetColorAction(null, "Automatic"));
            for (int i = 0; i < COLORS.length; i++) {
                popup.add(new SetColorAction(COLORS[i], COLOR_NAMES[i]));
            }
            addMouseListener(this);
        }

        public Icon createIcon() {
            BasicArrowButton arrow = new BasicArrowButton(SwingConstants.SOUTH);
            BufferedImage img = new BufferedImage(IMAGE_WIDTH + arrowSize + 2 * arrowMargin, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics g = img.getGraphics();
            ColorAction.this.getIcon().paintIcon(this, g, 0, 0);
            arrow.paintTriangle(g, IMAGE_WIDTH + arrowMargin + arrowSize / 2, IMAGE_HEIGHT / 2 - arrowSize / 2, arrowSize, SwingConstants.SOUTH, true);
            return new ImageIcon(img);
        }
       
        
        public void selectionChanged(CfgScene scene) {
            Set<CfgNode> nodes = scene.getSelectedNodes();
            setEnabled(nodes.size() > 0);
        }
        
        public void mouseClicked(MouseEvent e) {
            if (e.getX() < getInsets().left + IMAGE_WIDTH + arrowMargin) {
                performAction(null);
            } else {
                popup.show(this, 0, getSize().height);
            }
        }

        public void mouseEntered(MouseEvent e) {
        }

        public void mouseExited(MouseEvent e) {
        }

        public void mousePressed(MouseEvent e) {
        }

        public void mouseReleased(MouseEvent e) {
        }     
    }

    protected Icon createIcon(Color color) {
        if (color == null) {
            return ColorAction.this.getIcon();
        } else {
            return new ColorIcon(color);
        }
    }

    class ColorIcon implements Icon {

        Color color;

        public ColorIcon(Color color) {
            this.color = color;
        }

        public int getIconWidth() {
            return IMAGE_WIDTH;
        }

        public int getIconHeight() {
            return IMAGE_HEIGHT;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color oldColor = g.getColor();
            g.setColor(color);
            g.fillRect(x, y, IMAGE_WIDTH, IMAGE_HEIGHT);
            g.setColor(oldColor);
        }
    }

    class SetColorAction extends AbstractAction {

        Color color;
        String name;
        Icon icon;

        public SetColorAction(Color color, String name) {
            super(name, createIcon(color));
            this.color = color;
            this.name = name;
            icon = (Icon) getValue(AbstractAction.SMALL_ICON);
        }

        public Color getColor() {
            return color;
        }

        public String getName() {
            return name;
        }

        public Icon getIcon() {
            return icon;
        }

        public void actionPerformed(ActionEvent e) {
            performAction(color);
        }
    }
}
