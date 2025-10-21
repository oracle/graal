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
package at.ssw.visualizer.nc.model;

import org.netbeans.editor.BaseTokenID;
import org.netbeans.editor.TokenContext;
import org.netbeans.editor.TokenContextPath;
import org.netbeans.editor.TokenID;

/**
 *
 * @author Alexander Reder
 */
public class NCTokenContext extends TokenContext {
    
    public static final int EOF_TOKEN_ID = -1;
    public static final int WHITESPACE_TOKEN_ID = -2;
    public static final int OTHER_TOKEN_ID = -3;
    public static final int ADDRESS_TOKEN_ID = 4;
    public static final int INSTRUCTION_TOKEN_ID = 5;
    public static final int REGISTER_TOKEN_ID = 6;
    public static final int BLOCK_TOKEN_ID = 7;
    public static final int COMMENT_TOKEN_ID = 8;
    
    public static final TokenID EOF_TOKEN = new BaseTokenID("eof", EOF_TOKEN_ID);
    public static final TokenID WHITESPACE_TOKEN = new BaseTokenID("whitespace", WHITESPACE_TOKEN_ID);
    public static final TokenID OTHER_TOKEN = new BaseTokenID("other", OTHER_TOKEN_ID);
    public static final TokenID ADDRESS_TOKEN = new BaseTokenID("address", ADDRESS_TOKEN_ID);
    public static final TokenID INSTRUCTION_TOKEN = new BaseTokenID("instruction", INSTRUCTION_TOKEN_ID);
    public static final TokenID REGISTER_TOKEN = new BaseTokenID("register", REGISTER_TOKEN_ID);
    public static final TokenID BLOCK_TOKEN = new BaseTokenID("block", BLOCK_TOKEN_ID);
    public static final TokenID COMMENT_TOKEN = new BaseTokenID("comment", COMMENT_TOKEN_ID);
    
    public static final NCTokenContext context = new NCTokenContext();
    public static final TokenContextPath contextPath = context.getContextPath();
    
    private NCTokenContext() {
        super("nc-");
        addTokenID(WHITESPACE_TOKEN);
        addTokenID(OTHER_TOKEN);
        addTokenID(ADDRESS_TOKEN);
        addTokenID(INSTRUCTION_TOKEN);
        addTokenID(REGISTER_TOKEN);
        addTokenID(BLOCK_TOKEN);
        addTokenID(COMMENT_TOKEN);
    }

}
