/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.util.swing;

import org.openide.util.ImageUtilities;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Jiri Sedlacek
 */
public class DropdownButton extends JPanel {
    private static final String ICON = "org/graalvm/visualizer/util/resources/popupArrow.png";  // NOI18N

    private static final Icon DROPDOWN_ICON = ImageUtilities.loadImageIcon(ICON, false);
    private static final int DROPDOWN_ICON_WIDTH = DROPDOWN_ICON.getIconWidth();
    private static final int DROPDOWN_ICON_HEIGHT = DROPDOWN_ICON.getIconHeight();

    private static final String NO_ACTION = "none"; // NOI18N
    private static final String POPUP_ACTION = "displayPopup"; // NOI18N

    private static final int POPUP_EXTENT;
    private static final int POPUP_OFFSET;
    private static final int POPUP_XWIDTH;
    private static final int POPUP_MARGIN;

    static {
        if (UIUtils.isWindowsClassicLookAndFeel()) {
            POPUP_EXTENT = 18;
            POPUP_OFFSET = 6;
            POPUP_XWIDTH = -1;
            POPUP_MARGIN = 6;
        } else if (UIUtils.isWindowsLookAndFeel()) {
            POPUP_EXTENT = 15;
            POPUP_OFFSET = 4;
            POPUP_XWIDTH = -1;
            POPUP_MARGIN = 6;
        } else if (UIUtils.isNimbus()) {
            POPUP_EXTENT = 17;
            POPUP_OFFSET = 6;
            POPUP_XWIDTH = -1;
            POPUP_MARGIN = 6;
        } else if (UIUtils.isMetalLookAndFeel()) {
            POPUP_EXTENT = 16;
            POPUP_OFFSET = 5;
            POPUP_XWIDTH = -2;
            POPUP_MARGIN = 6;
        } else if (UIUtils.isAquaLookAndFeel()) {
            POPUP_EXTENT = 19;
            POPUP_OFFSET = 7;
            POPUP_XWIDTH = -8;
            POPUP_MARGIN = 6;
        } else {
            POPUP_EXTENT = 16;
            POPUP_OFFSET = 5;
            POPUP_XWIDTH = -2;
            POPUP_MARGIN = 6;
        }
    }


    private final JComponent container;
    private final Button button;
    private final Popup popup;
    private Consumer<Popup> itemFactory;

    private boolean pushed;
    private final Consumer<JPopupMenu> initializer;
    private final ActionListener actionDelegate;

    public DropdownButton(boolean toolbar) {
        this("", null, toolbar);
    }

    public void setItemFactory(Consumer<Popup> factory) {
        this.itemFactory = factory;
    }

    public DropdownButton(String text, Icon icon, boolean toolbar) {
        this(text, icon, toolbar, null, null);
    }

    public DropdownButton(String text, Icon icon, boolean toolbar, Consumer<JPopupMenu> initializer, ActionListener al) {
        this.initializer = initializer;
        this.actionDelegate = al;

        setOpaque(false);

        if (toolbar) {
            JToolBar tb = new JToolBar() {
                public void doLayout() {
                    for (Component c : getComponents())
                        c.setBounds(0, 0, getWidth(), getHeight());
                }

                public void paint(Graphics g) {
                    paintChildren(g);
                }
            };
            tb.setFloatable(false);
            tb.setFocusable(false);
            container = tb;
            add(container);
        } else {
            container = this;
        }

        button = new Button(text, icon);
        container.add(button);

        popup = new Popup();
        container.add(popup);

        KeyStroke down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
        container.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(down, POPUP_ACTION);
        container.getActionMap().put(POPUP_ACTION, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                displayPopup();
            }
        });
    }

    public boolean requestFocusInWindow() {
        return popup.isFocusable() ? popup.requestFocusInWindow() :
                button.requestFocusInWindow();
    }

    public void setEnabled(boolean enabled) {
        if (button != null) {
            button.setEnabled(enabled);
            if (popup != null) {
                popup.setEnabled(enabled);
            }
            if (enabled) {
                exposeButton();
            } else {
                exposePopup();
            }
        }
    }

    public boolean isEnabled() {
        return button == null ? false : button.isEnabled();
    }

    public void setPopupEnabled(boolean enabled) {
        if (popup != null) popup.setEnabled(enabled);
    }

    public boolean isPopupEnabled() {
        return popup == null ? false : popup.isEnabled();
    }

    public void setPushed(boolean p) {
        pushed = p;
        repaint();
    }

    public boolean isPushed() {
        return pushed;
    }


    public void setToolTipText(String text) {
        button.setToolTipText(text);
    }

    public void setPushedToolTipText(String text) {
        button.putClientProperty("PUSHED_TOOLTIP", text); // NOI18N
    }

    public void setPopupToolTipText(String text) {
        popup.setToolTipText(text);
    }


    public void setText(String text) {
        if (button != null) {
            String _text = button.getText();
            button.setText(text);

            Component parent = getParent();
            if (!Objects.equals(text, _text) && parent != null) {
                parent.invalidate();
                parent.revalidate();
                parent.repaint();
            }
        }
    }

    public String getText() {
        return button == null ? null : button.getText();
    }

    public void setIcon(Icon icon) {
        if (button != null) {
            Icon _icon = button.getIcon();
            button.setIcon(icon);

            Component parent = getParent();
            if (!Objects.equals(icon, _icon) && parent != null) {
                parent.invalidate();
                parent.revalidate();
                parent.repaint();
            }
        }
    }

    public Icon getIcon() {
        return button == null ? null : button.getIcon();
    }


    public void clickPopup() {
        if (popup != null) popup.doClick();
    }

    public void displayPopup() {
        JPopupMenu menu = new JPopupMenu();
        populatePopup(menu);
        if (menu.getComponentCount() > 0) {
            Dimension size = menu.getPreferredSize();
            size.width = Math.max(size.width, getWidth());
            menu.setPreferredSize(size);
            menu.show(this, 0, getHeight());
        }
    }

    protected void populatePopup(JPopupMenu menu) {
        if (initializer != null) {
            initializer.accept(menu);
        }
    }

    protected void performAction(ActionEvent e) {
        if (actionDelegate != null) {
            actionDelegate.actionPerformed(e);
        }
    }


    public void paint(Graphics g) {
        paintChildren(g);
    }

    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Icon icon = DROPDOWN_ICON;
        if (!popup.isEnabled()) {
            icon = ImageUtilities.createDisabledIcon(icon);
        }
        icon.paintIcon(this, g, getWidth() - DROPDOWN_ICON_WIDTH - POPUP_OFFSET,
                (getHeight() - DROPDOWN_ICON_HEIGHT) / 2);

        if (pushed || !button.isEnabled() || container.getComponent(0) == popup ||
                button.getModel().isRollover() || button.isFocusOwner()) {
            g.setColor(Color.GRAY);
            g.drawLine(getWidth() - POPUP_EXTENT, POPUP_MARGIN,
                    getWidth() - POPUP_EXTENT, getHeight() - POPUP_MARGIN);
        }
    }

    private boolean wasIn;

    private void processChildMouseEvent(MouseEvent e) {
        boolean isIn = contains(e.getX(), e.getY());
        boolean isPopupSide = e.getX() >= getWidth() - POPUP_EXTENT;

        switch (e.getID()) {
            case MouseEvent.MOUSE_ENTERED:
                if (!wasIn) {
                    button.processEventImpl(fromEvent((MouseEvent) e, button, MouseEvent.MOUSE_ENTERED));
                    popup.processEventImpl(fromEvent((MouseEvent) e, popup, MouseEvent.MOUSE_ENTERED));
                }
                break;
            case MouseEvent.MOUSE_EXITED:
                if (!isIn) {
                    popup.processEventImpl(fromEvent((MouseEvent) e, popup, MouseEvent.MOUSE_EXITED));
                    button.processEventImpl(fromEvent((MouseEvent) e, button, MouseEvent.MOUSE_EXITED));
                    exposeButton();
                }
                break;
            case MouseEvent.MOUSE_MOVED:
                if (isPopupSide) {
                    exposePopup();
                    MouseEvent ee = fromEvent((MouseEvent) e, popup, MouseEvent.MOUSE_MOVED);
                    popup.processEventImpl(ee);
                    ToolTipManager.sharedInstance().mouseMoved(ee);
                } else {
                    exposeButton();
                    MouseEvent ee = fromEvent((MouseEvent) e, button, MouseEvent.MOUSE_MOVED);
                    button.processEventImpl(ee);
                    ToolTipManager.sharedInstance().mouseMoved(ee);
                }
                break;
            default:
                if (isPopupSide) {
                    popup.processEventImpl(e);
                } else {
                    button.processEventImpl(e);
                }
        }

        wasIn = isIn;
    }

    private static MouseEvent fromEvent(MouseEvent e, Component source, int id) {
        return new MouseEvent(source, id, e.getWhen(), e.getModifiers(), e.getX(),
                e.getY(), e.getClickCount(), e.isPopupTrigger());
    }

    private boolean exposeButton() {
        if (container.getComponent(0) == button) return false;
        container.setComponentZOrder(button, 0);
        repaint();
        return true;
    }

    private boolean exposePopup() {
        if (container.getComponent(0) == popup) return false;
        container.setComponentZOrder(popup, 0);
        repaint();
        return true;
    }


    public Dimension getPreferredSize() {
        Dimension d = button.getPreferredSize();
        d.width += POPUP_EXTENT + POPUP_XWIDTH;
        return d;
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    public void doLayout() {
        for (Component c : getComponents())
            c.setBounds(0, 0, getWidth(), getHeight());
    }


    private class Button extends SmallButton {

        Button(String text, Icon icon) {
            super(text, icon);

            // See GenericToolbar.addImpl()
            putClientProperty("MetalListener", new Object()); // NOI18N

            if (UIUtils.isAquaLookAndFeel())
                putClientProperty("JComponent.sizeVariant", "regular"); // NOI18N

            setModel(new DefaultButtonModel() {
                public boolean isRollover() {
                    return super.isRollover() || (isEnabled() && (popup != null && popup.getModel().isRollover()));
                }

                public boolean isPressed() {
                    return pushed || super.isPressed();
                }

                public boolean isArmed() {
                    return pushed || super.isArmed();
                }
            });

            setHorizontalAlignment(LEADING);
            setDefaultCapable(false);
        }

        public String getToolTipText() {
            if (pushed) {
                Object pushedTT = getClientProperty("PUSHED_TOOLTIP"); // NOI18N
                if (pushedTT != null) return pushedTT.toString();
            }
            return super.getToolTipText();
        }

        protected void fireActionPerformed(ActionEvent e) {
            super.fireActionPerformed(e);
            performAction(e);
        }

        protected void processEvent(AWTEvent e) {
            if (!(e instanceof MouseEvent)) processEventImpl(e);
            else processChildMouseEvent((MouseEvent) e);
        }

        private void processEventImpl(AWTEvent e) {
            super.processEvent(e);
        }

        public boolean hasFocus() {
            return isEnabled() ? super.hasFocus() : popup.hasFocus();
        }

        public void paint(Graphics g) {
            Rectangle c = g.getClipBounds();
            if (pushed || !isEnabled() || container.getComponent(0) != this)
                g.setClip(0, 0, getWidth() - POPUP_EXTENT, getHeight());
            super.paint(g);
            g.setClip(c);
        }

        public void repaint() {
            DropdownButton.this.repaint();
        }

        public Insets getMargin() {
            Insets i = super.getMargin();
            if (UIUtils.isWindowsClassicLookAndFeel()) {
                if (i == null) {
                    i = new Insets(1, 1, 1, 1);
                } else {
                    i.top = 1;
                    i.left = 1;
                    i.bottom = 1;
                    i.right = 1;
                }
            } else if (UIUtils.isNimbusLookAndFeel()) {
                if (i == null) {
                    i = new Insets(0, 2, 0, 2);
                } else {
                    i.left = 2;
                    i.right = 2;
                }
            } else if (UIUtils.isAquaLookAndFeel()) {
                if (i == null) {
                    i = new Insets(0, -6, 0, 0);
                } else {
                    i.left = -6;
                    i.right = 0;
                }
            }
            return i;
        }

    }

    private class Popup extends JButton {

        Popup() {
            super(" "); // NOI18N

            // See GenericToolbar.addImpl()
            putClientProperty("MetalListener", new Object()); // NOI18N

            setModel(new DefaultButtonModel() {
                public boolean isRollover() {
                    return super.isRollover() || pushed;
                }
            });

            setHorizontalAlignment(LEADING);
            setDefaultCapable(false);

            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), NO_ACTION);
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), NO_ACTION);
        }

        protected void fireActionPerformed(ActionEvent e) {
            super.fireActionPerformed(e);
            displayPopup();
        }

        protected void processEvent(AWTEvent e) {
            if (!(e instanceof MouseEvent)) processEventImpl(e);
            else processChildMouseEvent((MouseEvent) e);
        }

        private void processEventImpl(AWTEvent e) {
            super.processEvent(e);
            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                if (isFocusable()) requestFocus();
                else button.requestFocus();
            }
        }

        public boolean hasFocus() {
            return isFocusable() ? super.hasFocus() : button.hasFocus();

        }

        public boolean isFocusable() {
            return !button.isEnabled();
        }

        public void paint(Graphics g) {
            if (isEnabled() && (pushed || !button.isEnabled() || container.getComponent(0) == this)) {
                Rectangle c = g.getClipBounds();
                g.setClip(getWidth() - POPUP_EXTENT, 0, POPUP_EXTENT, getHeight());
                super.paint(g);
                g.setClip(c);
            }
        }

        public void repaint() {
            DropdownButton.this.repaint();
        }

    }

    /**
     * @author Jiri Sedlacek
     */
    static class SmallButton extends JButton {

        protected static final Icon NO_ICON = new Icon() {
            public int getIconWidth() {
                return 0;
            }

            public int getIconHeight() {
                return 16;
            }

            public void paintIcon(Component c, Graphics g, int x, int y) {
            }
        };


        {
            setDefaultCapable(false);
            if (UIUtils.isWindowsLookAndFeel()) setOpaque(false);
        }


        public SmallButton() {
            this(null, null);
        }

        public SmallButton(Icon icon) {
            this(null, icon);
        }

        public SmallButton(String text) {
            this(text, null);
        }

        public SmallButton(Action a) {
            super(a);
        }

        public SmallButton(String text, Icon icon) {
            super(text);
            setIcon(icon);
        }


        public void setIcon(Icon defaultIcon) {
            if (defaultIcon == null) {
                defaultIcon = NO_ICON;
                setIconTextGap(0);
            }
            super.setIcon(defaultIcon);
        }

        public Insets getMargin() {
            Insets margin = super.getMargin();
            if (margin != null) {
                if (getParent() instanceof JToolBar) {
                    if (UIUtils.isNimbus()) {
                        margin.left = margin.top + 3;
                        margin.right = margin.top + 3;
                    }
                } else {
                    if (UIUtils.isNimbus()) {
                        margin.left = margin.top - 6;
                        margin.right = margin.top - 6;
                    } else {
                        margin.left = margin.top + 3;
                        margin.right = margin.top + 3;
                    }
                }
            }
            return margin;
        }

    }
}
