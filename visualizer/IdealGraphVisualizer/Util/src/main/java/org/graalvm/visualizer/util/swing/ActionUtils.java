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

import org.openide.awt.Actions;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author sdedic
 */
public class ActionUtils {
    private ActionUtils() {
    }

    private static final Insets BUTTON_INSETS = new Insets(2, 1, 0, 1);

    /**
     * Shared mouse listener used for setting the border painting property of
     * the toolbar buttons and for invoking the popup menu.
     */
    private static final MouseListener sharedMouseListener
            = new MouseAdapter() {
        public @Override
        void mouseEntered(MouseEvent evt) {
            Object src = evt.getSource();

            if (src instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) evt.getSource();
                if (button.isEnabled()) {
                    button.setContentAreaFilled(true);
                    button.setBorderPainted(true);
                }
            }
        }

        public @Override
        void mouseExited(MouseEvent evt) {
            Object src = evt.getSource();
            if (src instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) evt.getSource();
                removeButtonContentAreaAndBorder(button);
            }
        }
    };

    private static void processButton(AbstractButton button) {
        if (button == null) {
            return;
        }
        removeButtonContentAreaAndBorder(button);
        button.setMargin(BUTTON_INSETS);
        button.addMouseListener(sharedMouseListener);
        //fix of issue #69642. Focus shouldn't stay in toolbar
        button.setFocusable(false);
    }

    private static void removeButtonContentAreaAndBorder(AbstractButton button) {
        boolean canRemove = true;
        if (button instanceof JToggleButton) {
            canRemove = !button.isSelected();
        }
        if (canRemove) {
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
        }
    }

    public static Action findAction(String category, String id, Lookup context) {
        Action a = Actions.forID(category, id);
        if (a == null) {
            return null;
        }
        if (a instanceof ContextAwareAction) {
            a = ((ContextAwareAction) a).createContextAwareInstance(context);
            if (a instanceof PropertyChangeListener) {
                // Bug in action intialization; see NETBEANS-1985. The event will cause the context-bound action
                // to re-evaluate the status.
                ((PropertyChangeListener) a).propertyChange(new PropertyChangeEvent(a, Action.SELECTED_KEY, null, Boolean.TRUE));
            }
            a.isEnabled();
        }
        return a;
    }

    public static ActionMap populatePopupMenu(JPopupMenu menu, ActionMap am, String configFolder, Lookup context) {
        if (am == null) {
            am = new ActionMap();
        }
        for (Action a : Utilities.actionsForPath(configFolder)) {
            if (a == null) {
                menu.addSeparator();
                continue;
            }
            if (a instanceof ContextAwareAction) {
                a = ((ContextAwareAction) a).createContextAwareInstance(context);
                if (a instanceof PropertyChangeListener) {
                    // Bug in action intialization; see NETBEANS-1985. The event will cause the context-bound action
                    // to re-evaluate the status.
                    ((PropertyChangeListener) a).propertyChange(new PropertyChangeEvent(a, Action.SELECTED_KEY, null, Boolean.TRUE));
                }
                a.isEnabled();
            }
            Object item;

            if (a instanceof Presenter.Popup) {
                item = menu.add(((Presenter.Popup) a).getPopupPresenter());
            } else {
                item = menu.add(a);
            }

            // end 
            String n = (String) a.getValue(Action.NAME);
            String desc = (String) a.getValue(Action.SHORT_DESCRIPTION);
            if (desc != null && !desc.equals(n)) {
                am.put(n, a);
            }
        }
        return am;
    }

    public static Action addToolbarAction(JToolBar toolBar, Action a, ActionMap am, Lookup context, boolean processButtons) {
        if (a == null) {
            toolBar.addSeparator();
            return null;
        }
        if (a instanceof ContextAwareAction) {
            a = ((ContextAwareAction) a).createContextAwareInstance(context);
            if (a instanceof PropertyChangeListener) {
                // Bug in action intialization; see NETBEANS-1985. The event will cause the context-bound action
                // to re-evaluate the status.
                ((PropertyChangeListener) a).propertyChange(new PropertyChangeEvent(a, Action.SELECTED_KEY, null, Boolean.TRUE));
            }
            a.isEnabled();
        }
        Object item;

        String n = (String) a.getValue(Action.NAME);
        String desc = (String) a.getValue(Action.SHORT_DESCRIPTION);

        if (a instanceof Presenter.Toolbar) {
            item = toolBar.add(((Presenter.Toolbar) a).getToolbarPresenter());
        } else {
            JComponent b = toolBar.add(a);
            String tooltip = desc;
            if (tooltip == null) {
                tooltip = n;
            }
            b.setToolTipText(tooltip);
            item = b;
        }

        // NbEditorToolbar's button processing
        if (item instanceof AbstractButton && processButtons) {
            AbstractButton button = (AbstractButton) item;
            processButton(button);
        }
        // end 
        if (am != null && desc != null && !desc.equals(n)) {
            am.put(n, a);
        }
        return a;
    }

    public static ActionMap populateToolbar(JToolBar toolBar, ActionMap am, String toolbarConfigFolder, Lookup context, boolean processButtons) {
        if (am == null) {
            am = new ActionMap();
        }
        for (Action a : Utilities.actionsForPath(toolbarConfigFolder)) {
            if (a == null) {
                toolBar.addSeparator();
                continue;
            }
            if (a instanceof ContextAwareAction) {
                a = ((ContextAwareAction) a).createContextAwareInstance(context);
                if (a instanceof PropertyChangeListener) {
                    // Bug in action intialization; see NETBEANS-1985. The event will cause the context-bound action
                    // to re-evaluate the status.
                    ((PropertyChangeListener) a).propertyChange(new PropertyChangeEvent(a, Action.SELECTED_KEY, null, Boolean.TRUE));
                }
                a.isEnabled();
            }
            Object item;

            if (a instanceof Presenter.Toolbar) {
                item = toolBar.add(((Presenter.Toolbar) a).getToolbarPresenter());
            } else {
                item = toolBar.add(a);
            }

            // NbEditorToolbar's button processing
            if (item instanceof AbstractButton && processButtons) {
                AbstractButton button = (AbstractButton) item;
                processButton(button);
            }
            // end 
            String n = (String) a.getValue(Action.NAME);
            String desc = (String) a.getValue(Action.SHORT_DESCRIPTION);
            if (desc != null && !desc.equals(n)) {
                am.put(n, a);
            }
        }
        return am;
    }
}
