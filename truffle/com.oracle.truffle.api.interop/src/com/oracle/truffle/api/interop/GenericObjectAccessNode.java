/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.IndirectCallNode;

final class GenericObjectAccessNode extends ObjectAccessNode {
    private final Message access;
    @Child private IndirectCallNode indirectCallNode;

    @Child private ForeignAccessArguments accessArguments = new ForeignAccessArguments();

    GenericObjectAccessNode(Message access) {
        this.access = access;
        indirectCallNode = Truffle.getRuntime().createIndirectCallNode();
    }

    GenericObjectAccessNode(GenericObjectAccessNode prev) {
        this(prev.access);
    }

    @Override
    public Object executeWith(TruffleObject truffleObject, Object[] arguments) {
        final CallTarget ct = findCallTarget(truffleObject);
        return indirectCallNode.call(ct, accessArguments.executeCreate(truffleObject, arguments));
    }

    @TruffleBoundary
    protected CallTarget findCallTarget(TruffleObject truffleObject) {
        final ForeignAccess fa = truffleObject.getForeignAccess();
        final CallTarget ct = fa.access(access);
        if (ct == null) {
            throw messageNotRecognizedException();
        }
        return ct;
    }

    @CompilerDirectives.TruffleBoundary
    private RuntimeException messageNotRecognizedException() {
        throw UnsupportedMessageException.raise(access);
    }

}
