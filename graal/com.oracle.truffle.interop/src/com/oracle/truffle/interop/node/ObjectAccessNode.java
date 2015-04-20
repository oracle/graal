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
package com.oracle.truffle.interop.node;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.interop.InteropPredicate;
import com.oracle.truffle.api.interop.messages.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.interop.*;

abstract class ObjectAccessNode extends Node {

    public abstract Object executeWith(VirtualFrame frame, TruffleObject receiver, Object[] arguments);

}

class UnresolvedObjectAccessNode extends ObjectAccessNode {

    private static final int CACHE_SIZE = 8;
    private int cacheLength = 1;

    @Override
    public Object executeWith(VirtualFrame frame, TruffleObject receiver, Object[] arguments) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        ForeignObjectAccessHeadNode nthParent = (ForeignObjectAccessHeadNode) NodeUtil.getNthParent(this, cacheLength);
        ObjectAccessNode first = nthParent.getFirst();
        if (cacheLength < CACHE_SIZE) {
            cacheLength++;
            CachedObjectAccessNode createCachedAccess = createCachedAccess(receiver, nthParent.getAccessTree(), first);
            return first.replace(createCachedAccess).executeWith(frame, receiver, arguments);
        } else {
            return first.replace(createGenericAccess(nthParent.getAccessTree())).executeWith(frame, receiver, arguments);
        }
    }

    private static CachedObjectAccessNode createCachedAccess(TruffleObject receiver, Message accessTree, ObjectAccessNode next) {
        ForeignAccessFactory accessFactory = receiver.getForeignAccessFactory();
        return new CachedObjectAccessNode(Truffle.getRuntime().createDirectCallNode(accessFactory.getAccess(accessTree)), next, accessFactory.getLanguageCheck());
    }

    private static GenericObjectAccessNode createGenericAccess(Message access) {
        return new GenericObjectAccessNode(access);
    }
}

class GenericObjectAccessNode extends ObjectAccessNode {

    private final Message access;
    @Child private IndirectCallNode indirectCallNode;

    public GenericObjectAccessNode(Message access) {
        this.access = access;
        indirectCallNode = Truffle.getRuntime().createIndirectCallNode();
    }

    public GenericObjectAccessNode(GenericObjectAccessNode prev) {
        this(prev.access);
    }

    @Override
    public Object executeWith(VirtualFrame frame, TruffleObject truffleObject, Object[] arguments) {
        return indirectCallNode.call(frame, truffleObject.getForeignAccessFactory().getAccess(access), ForeignAccessArguments.create(truffleObject, arguments));
    }
}

class CachedObjectAccessNode extends ObjectAccessNode {
    @Child private DirectCallNode callTarget;
    @Child private ObjectAccessNode next;

    private final InteropPredicate languageCheck;

    protected CachedObjectAccessNode(DirectCallNode callTarget, ObjectAccessNode next, InteropPredicate languageCheck) {
        this.callTarget = callTarget;
        this.next = next;
        this.languageCheck = languageCheck;
        this.callTarget.forceInlining();
    }

    protected CachedObjectAccessNode(CachedObjectAccessNode prev) {
        this(prev.callTarget, prev.next, prev.languageCheck);
    }

    @Override
    public Object executeWith(VirtualFrame frame, TruffleObject receiver, Object[] arguments) {
        return doAccess(frame, receiver, arguments);
    }

    private Object doAccess(VirtualFrame frame, TruffleObject receiver, Object[] arguments) {
        if (languageCheck.test(receiver)) {
            return callTarget.call(frame, ForeignAccessArguments.create(receiver, arguments));
        } else {
            return doNext(frame, receiver, arguments);
        }
    }

    private Object doNext(VirtualFrame frame, TruffleObject receiver, Object[] arguments) {
        return next.executeWith(frame, receiver, arguments);
    }
}
