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
package at.ssw.visualizer.texteditor.fold;

import at.ssw.visualizer.texteditor.model.FoldingRegion;
import at.ssw.visualizer.texteditor.model.Text;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.editor.CodeFoldingSideBar;
import org.netbeans.spi.editor.fold.FoldHierarchyTransaction;
import org.netbeans.spi.editor.fold.FoldOperation;

/**
 *
 * @author Alexander Reder
 */
public class FoldManager implements org.netbeans.spi.editor.fold.FoldManager {

    protected FoldOperation operation;
    
    public void init(FoldOperation operation) {
        this.operation = operation;
    }

    public void initFolds(FoldHierarchyTransaction transaction) {
        Document document = operation.getHierarchy().getComponent().getDocument();
        Text text = (Text) document.getProperty(Text.class);
        if (document.getLength() == 0 || text == null) {
            return;
        }

        try {
            for (FoldingRegion fr : text.getFoldings()) {
                operation.addToHierarchy(fr.getKind(), fr.getKind().toString(), fr.isInitiallyCollapsed(), fr.getStart(), fr.getEnd(), 0, 0, null, transaction);
            }
        } catch (BadLocationException ex) {
            Logger logger = Logger.getLogger(FoldManager.class.getName());
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    public void insertUpdate(DocumentEvent arg0, FoldHierarchyTransaction arg1) {
    }

    public void removeUpdate(DocumentEvent arg0, FoldHierarchyTransaction arg1) {
    }

    public void changedUpdate(DocumentEvent arg0, FoldHierarchyTransaction arg1) {
    }

    public void removeEmptyNotify(Fold arg0) {
    }

    public void removeDamagedNotify(Fold arg0) {
    }

    public void expandNotify(Fold arg0) {
    }

    public void release() {
    }

    public static class FoldManagerFactory implements org.netbeans.spi.editor.fold.FoldManagerFactory {

        public FoldManager createFoldManager() {
            return new FoldManager();
        }
    }

    public static class SideBarFactory implements org.netbeans.editor.SideBarFactory {

        public JComponent createSideBar(JTextComponent target) {
            return new CodeFoldingSideBar(target);
        }
    }
    
}
