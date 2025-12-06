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
package at.ssw.visualizer.texteditor.tooltip;

import at.ssw.visualizer.texteditor.model.Scanner;
import at.ssw.visualizer.texteditor.model.Text;
import java.awt.event.ActionEvent;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.PopupManager;
import org.netbeans.editor.TokenID;
import org.netbeans.editor.Utilities;
import org.netbeans.editor.ext.ExtKit;
import org.netbeans.editor.ext.ToolTipSupport;
import org.netbeans.modules.editor.NbEditorKit;

/**
 *
 * @author Christian Wimmer
 * @author Alexander Reder
 */
public class ToolTipAction extends NbEditorKit.NbBuildToolTipAction {
    public ToolTipAction() {
        putValue(NAME, ExtKit.buildToolTipAction);
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (!showTooltip(target)) {
            super.actionPerformed(evt, target);
        }
    }
    
    private boolean showTooltip(JTextComponent target) {
        BaseDocument document = Utilities.getDocument(target);
        Text text = (Text) document.getProperty(Text.class);
        if (text == null) {
            return false;
        }

        EditorUI ui = Utilities.getEditorUI(target);  
        ToolTipSupport tts = ui.getToolTipSupport();
        int offset = target.viewToModel(tts.getLastMouseEvent().getPoint());

        String toolTipText = text.getRegionHover(offset);

        if (toolTipText == null) {
            Scanner scanner = text.getScanner();
            scanner.setText(document);
            scanner.findTokenBegin(offset);
            TokenID token = scanner.nextToken();
            if (token.getNumericID() < 0) {
                return false;
            }

            toolTipText = text.getStringHover(scanner.getTokenString());
            if (toolTipText == null) {
                return false;
            }
        }

        StyledToolTip tooltip = new StyledToolTip(toolTipText, target.getUI().getEditorKit(target));

        tts.setToolTip(tooltip, PopupManager.ViewPortBounds, PopupManager.Largest, 0, 0);
        return true;
    }
}
