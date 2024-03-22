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

package org.graalvm.visualizer.source.impl.actions;

import org.graalvm.visualizer.source.NodeLocationContext;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.ProxyLookup;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * @author sdedic
 */
@NbBundle.Messages({
        "ACTION_PreferGuestLanguage=Prefer guest language"
})
@ActionID(category = "CallStack", id = "org.graalvm.visualizer.source.impl.actions.PreferGuestLanguageAction")
@ActionRegistration(displayName = "#ACTION_PreferGuestLanguage",
        lazy = false)
public class PreferGuestLanguageAction extends AbstractAction
        implements ActionListener, ContextAwareAction, Presenter.Toolbar, PropertyChangeListener {
    public static final String ACTION_ID = "org.graalvm.visualizer.source.impl.actions.PreferGuestLanguageAction";
    public static final String CATEGORY = "CallStack";

    private final NodeLocationContext context;
    private Reference<JToggleButton> buttonRef = new WeakReference<>(null);

    public PreferGuestLanguageAction() {
        this.context = null;
    }

    private PreferGuestLanguageAction(Lookup lkp) {
        context = lkp.lookup(NodeLocationContext.class);
        boolean en = context != null && context.isPreferGuestLanguage();
        if (context != null) {
            context.addPropertyChangeListener(WeakListeners.propertyChange(this, context));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (context == null) {
            return;
        }
        context.setPreferGuestLanguage(!context.isPreferGuestLanguage());
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new PreferGuestLanguageAction(new ProxyLookup(lkp, Lookup.getDefault()));
    }

    @Override
    public Component getToolbarPresenter() {
        JToggleButton toggleButton = new JToggleButton();
        buttonRef = new WeakReference(toggleButton);
        toggleButton.putClientProperty("hideActionText", Boolean.TRUE); //NOI18N
        toggleButton.setIcon((Icon) getValue(SMALL_ICON));
        toggleButton.setAction(this); // this will make hard ref to button => check GC
        updateState();
        return toggleButton;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!NodeLocationContext.PROP_PREFER_GUEST_LANGUAGE.equals(evt.getPropertyName())) {
            return;
        }
        updateState();
    }

    private void updateState() {
        JToggleButton b = buttonRef.get();
        if (b != null) {
            b.setSelected(context.isPreferGuestLanguage());
        }
    }

    private Icon smallIcon;

    private Icon getIcon() {
        if (smallIcon != null) {
            return smallIcon;
        }
        String r = iconResource();
        if (r == null) {
            return null;
        }
        smallIcon = ImageUtilities.loadImageIcon(r, true);
        return smallIcon;
    }


    protected String iconResource() {
        return "org/graalvm/visualizer/source/resources/preferGuest.png";
    }

    public String getName() {
        return Bundle.ACTION_PreferGuestLanguage();
    }

    @Override
    public Object getValue(String key) {
        switch (key) {
            case SMALL_ICON:
                return getIcon();
            case SHORT_DESCRIPTION:
                return getName();
            case NAME:
                return getName();
        }
        return super.getValue(key);
    }
}
