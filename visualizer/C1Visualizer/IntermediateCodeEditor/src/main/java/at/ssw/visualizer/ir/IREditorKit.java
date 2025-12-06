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
package at.ssw.visualizer.ir;

import at.ssw.visualizer.ir.model.IRScanner;
import at.ssw.visualizer.texteditor.EditorKit;
import java.util.Collection;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.TextAction;
import org.netbeans.editor.Syntax;
import org.netbeans.modules.editor.NbEditorKit;
import org.openide.util.Lookup;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.lookup.Lookups;

/**
 * The IR Editor Kit, providing the syntax support.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public class IREditorKit extends EditorKit {

    @Override
    public String getContentType() {
        return IREditorSupport.MIME_TYPE;
    }

    @Override
    public Syntax createSyntax(Document doc) {
        return new IRScanner();
    }

    @Override
    protected Action[] getCustomActions() {
        return TextAction.augmentList(super.getCustomActions(), new Action[]{
                    new GenerateFoldPopupAction()
                });
    }

    public static class GenerateFoldPopupAction extends NbEditorKit.GenerateFoldPopupAction {

        @Override
        public JMenuItem getPopupMenuItem(JTextComponent target) {
            JMenuItem menu = super.getPopupMenuItem(target);
            menu.add(new JPopupMenu.Separator());
            Lookup lookup = Lookups.forPath("IREditorFolding");
            Collection<? extends CallableSystemAction> foldingActions = lookup.lookupAll(CallableSystemAction.class);
            for (CallableSystemAction csa : foldingActions) {
                menu.add(csa.getMenuPresenter());
            }
            return menu;
        }
    }
}
