/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LookupAndBind extends RootNode {
    @Child private RootNode libraryNode;
    @Child Node lookupSymbol = Message.READ.createNode();
    @Child Node bind = Message.createInvoke(1).createNode();
    private final List<String> functions;

    LookupAndBind(RootNode root, List<String> functions) {
        super(null);
        this.libraryNode = root;
        this.functions = functions;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Map<String, TruffleObject> libraryWrapper = new HashMap<>();
        LibFFILibrary library = (LibFFILibrary) libraryNode.execute(frame);
        LibFFISymbol[] symbols = new LibFFISymbol[functions.size()];
        for (int i = 0; i < symbols.length; i++) {
            String nameAndSignature = functions.get(i);
            int at = nameAndSignature.indexOf("(");
            String symbolName = nameAndSignature.substring(0, at).trim();
            String signature = nameAndSignature.substring(at);
            try {
                TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(lookupSymbol, library, symbolName);
                TruffleObject bound = (TruffleObject) ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
                libraryWrapper.put(symbolName, bound);
            } catch (InteropException e) {
                throw UnknownIdentifierException.raise(nameAndSignature);
            }
        }
        return library.register(libraryWrapper);
    }

}
