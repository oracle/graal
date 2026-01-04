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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.TopComponent;

/**
 * The common superclass of all concrete actions related to the CFG visualizer.
 *
 * @author Bernhard Stiftner
 * @author Rumpfhuber Stefan
 */
public abstract class AbstractCfgEditorAction extends CallableSystemAction implements CfgEventListener , PropertyChangeListener {

    CfgEditorTopComponent topComponent = null;
    
    public AbstractCfgEditorAction() {        
        TopComponent.getRegistry().addPropertyChangeListener(this);
        setEnabled(false);

    }
       
    protected CfgEditorTopComponent getEditor() {
        return topComponent;
    }
    
    protected void setEditor(CfgEditorTopComponent newTopComponent) {    
        CfgEditorTopComponent oldTopComponent = getEditor();
        if(newTopComponent != oldTopComponent){
            if(oldTopComponent != null) {
                oldTopComponent.getCfgScene().removeCfgEventListener(this);
            }
            this.topComponent = newTopComponent;         
            if (newTopComponent != null) {
                newTopComponent.getCfgScene().addCfgEventListener(this);
                selectionChanged(newTopComponent.getCfgScene());
            }
            this.setEnabled(newTopComponent!=null);
        }
    }

    
    @Override
    public JMenuItem getMenuPresenter() {
        return new JMenuItem(this);
    }

   
    @Override
    public JComponent getToolbarPresenter() {
        JButton b = new JButton(this);
        if (getIcon() != null) {
            b.setText(null);
            b.setToolTipText(getName());
        }
        return b;
    }

   
    @Override
    public JMenuItem getPopupPresenter() {       
        return new JMenuItem(this);
    }
    
   
    @Override
    protected boolean asynchronous() {
        return false;
    }
     
    
    public void propertyChange(PropertyChangeEvent e) {  
        if ( e.getPropertyName().equals(TopComponent.Registry.PROP_ACTIVATED)) {
            if(e.getNewValue() instanceof CfgEditorTopComponent){  
                CfgEditorTopComponent tc = (CfgEditorTopComponent)e.getNewValue();
                setEditor(tc);
                selectionChanged(tc.getCfgScene());
            } 
        }
    }
    
    
    public void selectionChanged(CfgScene scene) {   
    }

}
