/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.settings.ui;

import org.graalvm.visualizer.settings.Settings;
import org.graalvm.visualizer.settings.ui.SettingsPanel.SettingsOptionsPanelController;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.NumberFormatter;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author odouda
 */
public abstract class SettingsPanel<P extends SettingsPanel<P, C>, C extends SettingsOptionsPanelController<P, C>> extends JPanel {

    protected static final Connector<Boolean, AbstractButton> BOOL = new Connector<>(Boolean.class,
            (c) -> c.isSelected(), (c, v) -> c.setSelected(v), (c, l) -> c.addActionListener(l));
    protected static final Connector<Boolean, AbstractButton> NOT = new Connector<>(Boolean.class,
            (c) -> !c.isSelected(), (c, v) -> c.setSelected(!v), (c, l) -> c.addActionListener(l));
    protected static final Connector<String, JTextField> STRING = new Connector<>(String.class,
            (c) -> c.getText(), (c, v) -> c.setText(v), (c, l) -> c.addActionListener(l));
    protected static final Connector<Float, JFormattedTextField> FLOAT = new Connector<>(Float.class,
            (c) -> (Float) c.getValue(), (c, v) -> c.setValue(v), (c, l) -> c.addActionListener(l));
    protected static final Connector<Integer, JFormattedTextField> INT_FORM = new Connector<>(Integer.class,
            (c) -> (Integer) c.getValue(), (c, v) -> c.setValue(v), (c, l) -> c.addActionListener(l));
    protected static final Connector<Integer, JComboBox> INT_COMB = new Connector<>(Integer.class,
            (c) -> Integer.parseInt(c.getSelectedItem().toString()), (c, v) -> c.setSelectedItem(v), (c, l) -> c.addActionListener(l));

    private final List<Runnable> loads = new ArrayList<>();
    private final Map<AbstractButton, Runnable> enables = new HashMap<>();
    protected final C controller;
    private boolean reseting = false;

    protected SettingsPanel(C controller) {
        this.controller = Objects.requireNonNull(controller);
    }

    protected abstract Settings getSettings();

    protected void fireChanged() {
        getSettings().fireChanged();
    }

    protected boolean isFireChanged() {
        return true;
    }

    protected void settingsChanged() {
        controller.changed();
        if (!reseting && isFireChanged()) {
            fireChanged();
        }
    }

    protected <T, C extends JComponent> void tie(Connector<T, C> type, C comp, String name) {
        type.attach(comp, (e) -> {
            type.save(comp, getSettings(), name);
            settingsChanged();
        });
        addLoad(() -> type.load(comp, getSettings(), name));
    }

    protected void addLoad(Runnable run) {
        loads.add(run);
    }

    protected void load() {
        reseting = true;
        loads.forEach(action -> action.run());
        enables.values().forEach(action -> action.run());
        reseting = false;
    }

    protected boolean valid() {
        return true;
    }

    protected void store() {
        getSettings().store();
        if (controller.isChanged()) {
            fireChanged();
        }
    }

    protected void enables(javax.swing.AbstractButton button, javax.swing.JComponent component) {
        button.addActionListener(e -> setEnabledRecursive(component, button.isSelected()));
        enables.put(button, () -> setEnabledRecursive(component, button.isSelected()));
    }

    protected void disables(javax.swing.AbstractButton button, javax.swing.JComponent component) {
        button.addActionListener(e -> setEnabledRecursive(component, !button.isSelected()));
        enables.put(button, () -> setEnabledRecursive(component, !button.isSelected()));
    }

    protected void setEnabledRecursive(Container container, boolean def) {
        ArrayList<Runnable> mistakes = new ArrayList<>();
        for (Component c : container.getComponents()) {
            if (c.isEnabled() != def) {
                if (c instanceof Container) {
                    setEnabledRecursive((Container) c, def);
                } else {
                    c.setEnabled(def);
                }
            }
            if (def && c instanceof AbstractButton && enables.containsKey(c)) {
                mistakes.add(enables.get(c));
            }
        }
        mistakes.forEach(action -> action.run());
        if (container.isEnabled() != def) {
            container.setEnabled(def);
        }
    }

    protected static void setPreferredSizeRecursive(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof Container) {
                setPreferredSizeRecursive((Container) c);
            }
        }
        if (container instanceof JPanel || container instanceof JTabbedPane || container instanceof JScrollPane) {
            container.setPreferredSize(calcDims(container));
        }
    }

    private static Dimension calcDims(Container container) {
        Rectangle r = new Rectangle();
        for (Component c : container.getComponents()) {
            r.add(c.getBounds());
        }
        return r.getSize();
    }

    protected static void group(ButtonGroup group, AbstractButton... buttons) {
        for (AbstractButton button : buttons) {
            group.add(button);
        }
    }

    protected static void setMin(JFormattedTextField ftf, Comparable<?> min) {
        JFormattedTextField.AbstractFormatter formatter = ftf.getFormatter();
        if (formatter != null && formatter instanceof NumberFormatter) {
            ((NumberFormatter) formatter).setMinimum(min);
        }
    }

    protected static void setMax(JFormattedTextField ftf, Comparable<?> max) {
        JFormattedTextField.AbstractFormatter formatter = ftf.getFormatter();
        if (formatter != null && formatter instanceof NumberFormatter) {
            ((NumberFormatter) formatter).setMaximum(max);
        }
    }

    protected static class Connector<T, C extends JComponent> {

        private final Class<T> type;
        private final Function<C, T> getter;
        private final BiConsumer<C, T> setter;
        private final BiConsumer<C, ActionListener> attacher;

        public Connector(Class<T> type, Function<C, T> getter, BiConsumer<C, T> setter, BiConsumer<C, ActionListener> attacher) {
            this.type = type;
            this.getter = getter;
            this.setter = setter;
            this.attacher = attacher;
        }

        public void load(C comp, Settings settings, String name) {
            setter.accept(comp, settings.get(type, name));
        }

        public void save(C comp, Settings settings, String name) {
            settings.set(name, getter.apply(comp));
        }

        public void attach(C comp, ActionListener a) {
            attacher.accept(comp, a);
        }
    }

    public static abstract class SettingsOptionsPanelController<P extends SettingsPanel<P, C>, C extends SettingsOptionsPanelController<P, C>> extends OptionsPanelController {

        private P panel;
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private boolean changed;

        @Override
        public void update() {
            getPanel().load();
            changed = false;
        }

        @Override
        public void applyChanges() {
            SwingUtilities.invokeLater(() -> {
                getPanel().store();
                changed = false;
            });
        }

        @Override
        public void cancel() {
            if (isChanged()) {
                getPanel().fireChanged();
            }
        }

        @Override
        public boolean isValid() {
            return getPanel().valid();
        }

        @Override
        public boolean isChanged() {
            return changed;
        }

        @Override
        public HelpCtx getHelpCtx() {
            return null; // new HelpCtx("...ID") if you have a help set
        }

        @Override
        public JComponent getComponent(Lookup masterLookup) {
            return getPanel();
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
            pcs.addPropertyChangeListener(l);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
            pcs.removePropertyChangeListener(l);
        }

        protected abstract P makePanel(C controller);

        private P getPanel() {
            if (panel == null) {
                panel = makePanel((C) this);
            }
            return panel;
        }

        void changed() {
            if (!changed) {
                changed = true;
                pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
            }
            pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
        }

    }
}
