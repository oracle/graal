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

import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * Controller for the settings page displayed in the options dialog.
 *
 * @author Bernhard Stiftner
 */
public class CFGOptionsPanelController extends OptionsPanelController {

    CFGOptionsPanel optionsPanel;

    
    public void update() {
        getOptionsPanel().update();       
    }

    public void applyChanges() {
        getOptionsPanel().applyChanges();
    }

    public void cancel() {
        getOptionsPanel().cancel();
    }

    public boolean isValid() {
        return getOptionsPanel().isDataValid();
    }

    public boolean isChanged() {
        return getOptionsPanel().isChanged();
    }

    public JComponent getComponent(Lookup masterLookup) {
        return getOptionsPanel();
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
    public void addPropertyChangeListener(PropertyChangeListener l) {
        getOptionsPanel().addPropertyChangeListener(l);
    }

    //todo: investigate - who removes the changelistener ?
    public void removePropertyChangeListener(PropertyChangeListener l) {
        getOptionsPanel().removePropertyChangeListener(l);
    }

    private CFGOptionsPanel getOptionsPanel() {
        if (optionsPanel == null) {
            optionsPanel = new CFGOptionsPanel();
        }
        return optionsPanel;
    }

}
