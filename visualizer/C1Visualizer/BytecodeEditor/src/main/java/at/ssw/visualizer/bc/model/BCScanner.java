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
package at.ssw.visualizer.bc.model;

import at.ssw.visualizer.texteditor.model.Scanner;
import org.netbeans.editor.TokenID;

/**
 * This class is used for parsing the bytecode text.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public class BCScanner extends Scanner {
    public BCScanner() {
        super("\n\r\t ,:", BCTokenContext.contextPath);
    }

    private boolean isBlock() {
        return expectChar('B') && expectChar(DIGIT) && expectChars(DIGIT) && expectEnd();
    }

    private boolean isBciDef() {
        return expectChar(DIGIT) && expectChars(DIGIT) && expectEnd(':');
    }

    private boolean isBciRef() {
        return expectChar('#') && expectChar(DIGIT) && expectChars(DIGIT) && expectEnd();
    }

    private boolean isVarRef() {
        return expectChar('%') && expectChar(DIGIT) && expectChars(DIGIT) && expectEnd();
    }

    private boolean isBcDescription() {
        return beforeChar(':') && expectChar(LETTER) && expectChars(LETTER_DIGIT) && expectEnd();
    }

    @Override
    protected TokenID parseToken() {
        findTokenBegin();
        if (ch == EOF) {
            return BCTokenContext.EOF_TOKEN;
        } else if (isWhitespace()) {
            return BCTokenContext.WHITESPACE_TOKEN;
        } else if (isBlock()) {
            return BCTokenContext.BLOCK_TOKEN;
        } else if (isBciDef() || isBciRef()) {
            return BCTokenContext.BCI_TOKEN;
        } else if (isVarRef()) {
            return BCTokenContext.VAR_REFERENCE_TOKEN;
        } else if (isBcDescription()) {
            return BCTokenContext.BC_DESCRIPTION_TOKEN;
        } else {
            readToWhitespace();
            return BCTokenContext.OTHER_TOKEN;
        }
    }

}
