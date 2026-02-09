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

import at.ssw.visualizer.cfg.editor.CfgEditorTopComponent;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import org.openide.util.actions.Presenter;

/**
 * Common superclass for all actions which set the link router.
 *
 * @author Bernhard Stiftner
 * @author Rumpfhuber Stefan
 */
public abstract class AbstractRouterAction extends AbstractCfgEditorAction implements Presenter.Menu, Presenter.Popup, Presenter.Toolbar {
       
    public void performAction() {
        CfgEditorTopComponent tc = getEditor();
        if (tc != null) {  
            setLinkRouter(tc);
        }
    }

    protected abstract void setLinkRouter(CfgEditorTopComponent editor);
    
    @Override
    public JMenuItem getMenuPresenter() {
        JMenuItem presenter = new MenuPresenter();
        presenter.setToolTipText(getName());
        return presenter;
    }

    @Override
    public JMenuItem getPopupPresenter() {
        return getMenuPresenter();
    }

    @Override
    public JComponent getToolbarPresenter() {
        ToolbarPresenter presenter = new ToolbarPresenter();    
        presenter.setToolTipText(getName());
        return presenter;
    }
      
    class MenuPresenter extends JRadioButtonMenuItem {

        public MenuPresenter() {
            super(AbstractRouterAction.this);
            setIcon(null);
        }      
    }

    class ToolbarPresenter extends JToggleButton {

        public ToolbarPresenter() {
            super(AbstractRouterAction.this);
            setText(null);  
        }
    } 
}
