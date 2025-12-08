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
package at.ssw.visualizer.ir.model;

import at.ssw.visualizer.texteditor.model.Scanner;
import org.netbeans.editor.TokenID;

/**
 * Splits the textual intermediate representation into tokens for HIR and LIR operands.
 *
 * @author Christian Wimmer
 */
public class IRScanner extends Scanner {
    public IRScanner() {
        super("\n\r\t .,;()[]", IRTokenContext.contextPath);
    }

    private boolean isBlock() {
        return expectChar('B') && expectChar(DIGIT) && expectChars(DIGIT) && expectEnd();
    }

    private boolean isHir() {
        return expectChar(LETTER) && expectChar(DIGIT) && expectChars(DIGIT) && expectEnd();
    }

    private boolean isLir() {
        return expectChar(LETTER) && skipUntil('|') && expectChars(LETTER)
                && isReferenceOrEmpty() && expectEnd();
    }

    private boolean isReferenceOrEmpty() {
        if (ch != '[')
            return true;
        readNext();
        return expectChars(REFERENCE_CHARS) && expectChar(']');
    }

    @Override
    protected TokenID parseToken() {
        findTokenBegin();
        if (ch == EOF && offset + 1 >= stopOffset) {
            // only except EOF if we are at the end of the buffer
            return IRTokenContext.EOF_TOKEN;
        } else if (isWhitespace()) {
            return IRTokenContext.WHITESPACE_TOKEN;
        } else if (isBlock()) {
            return IRTokenContext.BLOCK_TOKEN;
        } else if (isHir()) {
            return IRTokenContext.HIR_TOKEN;
        } else if (isLir()) {
            return IRTokenContext.LIR_TOKEN;
        } else {
            readToWhitespace();
            return IRTokenContext.OTHER_TOKEN;
        }
    }

}
