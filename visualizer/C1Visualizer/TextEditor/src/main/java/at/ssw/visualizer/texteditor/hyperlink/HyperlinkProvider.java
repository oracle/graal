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
package at.ssw.visualizer.texteditor.hyperlink;

import at.ssw.visualizer.texteditor.model.Scanner;
import at.ssw.visualizer.texteditor.model.Text;
import at.ssw.visualizer.texteditor.model.TextRegion;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.TokenID;
import org.netbeans.editor.Utilities;

/**
 * 
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 * @author Alexander Reder
 */
public class HyperlinkProvider implements org.netbeans.lib.editor.hyperlink.spi.HyperlinkProvider {
    
    protected Scanner scanner = null;
    
    protected TextRegion findTarget(Document doc, int offset) {
        Text text = (Text) doc.getProperty(Text.class);
        if (text == null) {
            return null;
        }
        
        scanner = text.getScanner();
        scanner.setText(doc);
        scanner.findTokenBegin(offset);
        TokenID token = scanner.nextToken();
        if (token.getNumericID() < 0) {
            return null;
        }
        
        return text.getHyperlinkTarget(scanner.getTokenString());
    }

    public boolean isHyperlinkPoint(Document doc, int offset) {
        return findTarget(doc, offset) != null;
    }

    public int[] getHyperlinkSpan(Document doc, int offset) {
        if (findTarget(doc, offset) != null) {
            return new int[]{scanner.getTokenOffset(), scanner.getTokenOffset() + scanner.getTokenLength()};
        }
        return null;
    }

    public void performClickAction(Document doc, int offset) {
        TextRegion target = findTarget(doc, offset);
        if (target != null) {
            JTextComponent editor = Utilities.getFocusedComponent();
            editor.select(target.getStart(), target.getEnd());
        }
    }

}
