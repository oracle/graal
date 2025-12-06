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
package at.ssw.visualizer.bc.options;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * Controlls the communication.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
final class BCOptionsPanelController extends OptionsPanelController {

    private BCOptionPanel panel;
    private boolean changed;
    private PropertyChangeSupport prop;

    public BCOptionsPanelController() {
        prop = new PropertyChangeSupport(this);
        panel = new BCOptionPanel(this);
        panel.update();
    }

    public void changed() {
        if (!changed) {
            changed = true;
            prop.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
    }

    public void update() {
        panel.update();
        changed = false;
    }

    public void applyChanges() {
        panel.applyChanges();
        changed = false;
    }

    public void cancel() {
        // Nothing to do.
    }

    public boolean isValid() {
        return true;
    }

    public boolean isChanged() {
        return changed;
    }

    public HelpCtx getHelpCtx() {
        return null;
    }

    public JComponent getComponent(Lookup masterLookup) {
        return panel;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        prop.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        prop.removePropertyChangeListener(l);
    }
}
