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

import org.netbeans.editor.BaseTokenID;
import org.netbeans.editor.TokenContext;
import org.netbeans.editor.TokenContextPath;
import org.netbeans.editor.TokenID;

/**
 * The token context for the scanner.
 *
 * @author Alexander Reder
 */
public class BCTokenContext extends TokenContext {

    public static final int EOF_TOKEN_ID = -1;
    public static final int WHITESPACE_TOKEN_ID = -2;
    public static final int OTHER_TOKEN_ID = -3;
    public static final int BCI_TOKEN_ID = 4;
    public static final int BC_DESCRIPTION_TOKEN_ID = 5;
    public static final int VAR_REFERENCE_TOKEN_ID = 6;
    public static final int BLOCK_TOKEN_ID = 7;

    public static final TokenID EOF_TOKEN = new BaseTokenID("eof", EOF_TOKEN_ID);
    public static final TokenID WHITESPACE_TOKEN = new BaseTokenID("whitespace", WHITESPACE_TOKEN_ID);
    public static final TokenID OTHER_TOKEN = new BaseTokenID("other", OTHER_TOKEN_ID);
    public static final TokenID BCI_TOKEN = new BaseTokenID("bci", BCI_TOKEN_ID);
    public static final TokenID BC_DESCRIPTION_TOKEN = new BaseTokenID("bytecode_description", BC_DESCRIPTION_TOKEN_ID);
    public static final TokenID VAR_REFERENCE_TOKEN = new BaseTokenID("var_reference", VAR_REFERENCE_TOKEN_ID);
    public static final TokenID BLOCK_TOKEN = new BaseTokenID("block", BLOCK_TOKEN_ID);

    public static final BCTokenContext context = new BCTokenContext();
    public static final TokenContextPath contextPath = context.getContextPath();

    /**
     * Initializes the token context with the prefix "bc-" and all available
     * tokens.
     */
    private BCTokenContext() {
        super("bc-");
        addTokenID(WHITESPACE_TOKEN);
        addTokenID(OTHER_TOKEN);
        addTokenID(BCI_TOKEN);
        addTokenID(BC_DESCRIPTION_TOKEN);
        addTokenID(VAR_REFERENCE_TOKEN);
        addTokenID(BLOCK_TOKEN);
    }
}
