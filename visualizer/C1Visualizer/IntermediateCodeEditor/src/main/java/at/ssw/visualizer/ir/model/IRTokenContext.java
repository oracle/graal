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

import org.netbeans.editor.BaseTokenID;
import org.netbeans.editor.TokenContext;
import org.netbeans.editor.TokenContextPath;
import org.netbeans.editor.TokenID;

/**
 * The one and only IR token context containing all possible tokens.
 *
 * @author Bernhard Stiftner
 */
public class IRTokenContext extends TokenContext {

    public static final int BLOCK_TOKEN_ID = 1;
    public static final int HIR_TOKEN_ID = 2;
    public static final int LIR_TOKEN_ID = 3;
    public static final int OTHER_TOKEN_ID = -1;
    public static final int WHITESPACE_TOKEN_ID = -2;
    public static final int EOF_TOKEN_ID = -3;

    public static final TokenID BLOCK_TOKEN = new BaseTokenID("block", BLOCK_TOKEN_ID);
    public static final TokenID HIR_TOKEN = new BaseTokenID("hir", HIR_TOKEN_ID);
    public static final TokenID LIR_TOKEN = new BaseTokenID("lir", LIR_TOKEN_ID);
    public static final TokenID OTHER_TOKEN = new BaseTokenID("other", OTHER_TOKEN_ID);
    public static final TokenID WHITESPACE_TOKEN = new BaseTokenID("whitespace", WHITESPACE_TOKEN_ID);
    public static final TokenID EOF_TOKEN = new BaseTokenID("eof", EOF_TOKEN_ID);

    public static final IRTokenContext context = new IRTokenContext();
    public static final TokenContextPath contextPath = context.getContextPath();

    private IRTokenContext() {
        super("ir-");
        addTokenID(BLOCK_TOKEN);
        addTokenID(HIR_TOKEN);
        addTokenID(LIR_TOKEN);
        addTokenID(OTHER_TOKEN);
        addTokenID(WHITESPACE_TOKEN);
        addTokenID(EOF_TOKEN);
    }
}
