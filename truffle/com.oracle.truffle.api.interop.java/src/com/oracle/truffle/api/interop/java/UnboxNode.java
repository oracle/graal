/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@NodeChildren(value = {@NodeChild(value = "valueNode", type = ReadArgNode.class)})
abstract class UnboxNode extends Node {
    @Node.Child private Node unbox;
    @Node.Child private Node isBoxed;

    public abstract Object executeUnbox(VirtualFrame frame);

    @Specialization
    public int executeUnbox(int value) {
        return value;
    }

    @Specialization
    public long executeUnbox(long value) {
        return value;
    }

    @Specialization
    public String executeUnbox(String value) {
        return value;
    }

    @Specialization(guards = "isBoxedPrimitive(frame, foreignValue)")
    public Object executeUnbox(VirtualFrame frame, TruffleObject foreignValue) {
        if (unbox == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            unbox = insert(Message.UNBOX.createNode());
        }
        return ForeignAccess.execute(unbox, frame, foreignValue);
    }

    protected final boolean isBoxedPrimitive(VirtualFrame frame, TruffleObject object) {
        if (isBoxed == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isBoxed = insert(Message.IS_BOXED.createNode());
        }
        return (boolean) ForeignAccess.execute(isBoxed, frame, object);
    }

}
