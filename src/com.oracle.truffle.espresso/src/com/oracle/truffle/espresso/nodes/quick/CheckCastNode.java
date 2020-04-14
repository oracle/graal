/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class CheckCastNode extends QuickNode {

    final Klass typeToCheck;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract boolean executeCheckCast(Klass instanceKlass);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "instanceKlass == cachedKlass")
    boolean checkCastCached(Klass instanceKlass,
                    @Cached("instanceKlass") Klass cachedKlass,
                    @Cached("checkCast(typeToCheck, cachedKlass)") boolean cachedAnswer) {
        return cachedAnswer;
    }

    @Specialization(replaces = "checkCastCached")
    boolean checkCastSlow(Klass instanceKlass) {
        // Brute checkcast, walk the whole klass hierarchy.
        return checkCast(typeToCheck, instanceKlass);
    }

    CheckCastNode(Klass typeToCheck, int top, int callerBCI) {
        super(top, callerBCI);
        assert !typeToCheck.isPrimitive();
        this.typeToCheck = typeToCheck;
    }

    @TruffleBoundary
    static boolean checkCast(Klass typeToCheck, Klass instanceKlass) {
        return typeToCheck.isAssignableFrom(instanceKlass);
    }

    @Override
    public final int execute(final VirtualFrame frame) {
        BytecodeNode root = getBytecodesNode();
        StaticObject receiver = root.peekObject(frame, top - 1);
        if (StaticObject.isNull(receiver) || executeCheckCast(receiver.getKlass())) {
            return 0;
        }
        Meta meta = typeToCheck.getMeta();
        throw Meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                getExceptionMessage(root, receiver));
    }

    @TruffleBoundary
    private final String getExceptionMessage(BytecodeNode root, StaticObject receiver) {
        return receiver.getKlass().getType() + " cannot be cast to: " + typeToCheck.getType() + " in context " + root.getMethod().toString();
    }
}
