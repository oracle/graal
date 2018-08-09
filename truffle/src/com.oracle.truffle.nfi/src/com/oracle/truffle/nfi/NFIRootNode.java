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
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.types.NativeSource;

class NFIRootNode extends RootNode {

    static class LookupAndBindNode extends Node {

        private final String name;
        private final String signature;

        @Child Node read;
        @Child Node bind;

        LookupAndBindNode(String name, String signature) {
            this.name = name;
            this.signature = signature;
            this.read = Message.READ.createNode();
            this.bind = Message.INVOKE.createNode();
        }

        TruffleObject execute(TruffleObject library) {
            try {
                TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(read, library, name);
                return (TruffleObject) ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }
    }

    @Child DirectCallNode loadLibrary;
    @Children LookupAndBindNode[] lookupAndBind;

    NFIRootNode(NFILanguage language, DirectCallNode loadLibrary, NativeSource source) {
        super(language);
        this.loadLibrary = loadLibrary;
        this.lookupAndBind = new LookupAndBindNode[source.preBoundSymbolsLength()];

        for (int i = 0; i < lookupAndBind.length; i++) {
            lookupAndBind[i] = new LookupAndBindNode(source.getPreBoundSymbol(i), source.getPreBoundSignature(i));
        }
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        TruffleObject library = (TruffleObject) loadLibrary.call(new Object[0]);
        if (lookupAndBind.length == 0) {
            return library;
        } else {
            NFILibrary ret = new NFILibrary(library);
            for (int i = 0; i < lookupAndBind.length; i++) {
                ret.preBindSymbol(lookupAndBind[i].name, lookupAndBind[i].execute(library));
            }
            return ret;
        }
    }
}
