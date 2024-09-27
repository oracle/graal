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
package org.graalvm.visualizer.shell.actions;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;

import org.graalvm.visualizer.data.SuppressFBWarnings;
import org.netbeans.spi.editor.AbstractEditorAction;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.actions.ActionPresenterProvider;
import org.openide.util.actions.Presenter;

/**
 * Basis for Editor actions, which need a context, for example Lookup from the
 * editor parent's TopComponent). Standard editor mechanism creates a wrapper
 * around the action, which is always enabled and only instantiates/delegates on
 * the real implementation after first {@link #actionPerformed}. This causes
 * the action to be enabled at first, then disable. Moreover, the wrapper will
 * not transfer the context to the target action, so even though the target is
 * context-aware, it gets just null / global context.
 *
 * @author sdedic
 */
public abstract class EditorContextActionBase extends AbstractAction implements ContextAwareAction,
        Presenter.Toolbar, Presenter.Menu, Presenter.Popup {
    protected static final int LARGE_ICON_SIZE = 24;
    protected static final String LARGE_ICON_SIZE_STRING = "24"; // NOI18N

    protected final Map<String, ?> attributes;
    private final Map<String, Object> properties = new HashMap<>();


    private static final Object MASK_NULL_VALUE = new Object();

    EditorContextActionBase(Map<String, ?> attrs) {
        if (attrs == null) {
            attrs = new HashMap<>();
        }
        this.attributes = attrs;
    }

    public static Icon createSmallIcon(Action a) {
        String iconBase = (String) a.getValue(AbstractEditorAction.ICON_RESOURCE_KEY);
        if (iconBase != null) {
            return ImageUtilities.loadImageIcon(iconBase, true);
        }
        return null;
    }

    public static Icon createLargeIcon(Action a) {
        String iconBase = (String) a.getValue(AbstractEditorAction.ICON_RESOURCE_KEY);
        if (iconBase != null) {
            iconBase += LARGE_ICON_SIZE_STRING;
            return ImageUtilities.loadImageIcon(iconBase, true);
        }
        return null;
    }

    protected Object createValue(String key) {
        Object value;
        if (Action.SMALL_ICON.equals(key)) {
            value = createSmallIcon(this);
        } else if (Action.LARGE_ICON_KEY.equals(key)) {
            value = createLargeIcon(this);
        } else if (attributes != null) {
            value = attributes.get(key);
        } else {
            value = null;
        }
        return value;
    }

    @Override
    @SuppressFBWarnings(value = "ES_COMPARING_PARAMETER_STRING_WITH_EQ", justification = "Swing library defines the constant and also uses identity comparison")
    public final void putValue(String key, Object value) {
        if (value == null && properties == null) { // Prevent NPE from super(null) in constructor
            return;
        }
        Object oldValue;
        if ("enabled" == key) { // Same == in AbstractAction // NOI18N
            oldValue = enabled;
            enabled = Boolean.TRUE.equals(value);
        } else {
            synchronized (properties) {
                oldValue = properties.put(key, (value != null) ? value : MASK_NULL_VALUE);
            }
        }
        firePropertyChange(key, oldValue, value); // Checks whether oldValue.equals(value)
    }

    @Override
    public Object getValue(String key) {
        if ("enabled" == key) { // Same == in AbstractAction // NOI18N
            return enabled;
        }
        synchronized (properties) {
            Object value = properties.get(key);
            if (value == null) {
                if ("instanceCreate".equals(key)) { // NOI18N
                    return null;
                }
                if (value == null) {
                    value = createValue(key);
                    if (value == null) { // Do not query next time
                        value = MASK_NULL_VALUE;
                    }
                    // Do not fire a change since property was not queried yet
                    properties.put(key, value);
                }
            }
            if (value == MASK_NULL_VALUE) {
                value = null;
            }
            return value;
        }
    }


    @Override
    public Component getToolbarPresenter() {
        return ActionPresenterProvider.getDefault().createToolbarPresenter(this);
    }

    @Override
    public JMenuItem getMenuPresenter() {
        return ActionPresenterProvider.getDefault().createMenuPresenter(this);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        return ActionPresenterProvider.getDefault().createPopupPresenter(this);
    }
}
