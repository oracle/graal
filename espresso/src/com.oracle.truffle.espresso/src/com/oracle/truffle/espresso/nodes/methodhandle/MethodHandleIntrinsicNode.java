/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.methodhandle;

import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.perf.DebugCounter;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoNode;

/**
 * Top of the method handle intrinsic behavior implementation hierarchy.
 */
public abstract class MethodHandleIntrinsicNode extends EspressoNode {
    protected static final DebugCounter hits = DebugCounter.create("MH cache hits");
    protected static final DebugCounter miss = DebugCounter.create("MH cache miss");

    protected final Method method;

    public MethodHandleIntrinsicNode(Method method) {
        this.method = method;
    }

    /**
     * The dummy polymorphic signature method object.
     */
    public Method getMethod() {
        return method;
    }

    @Idempotent
    public boolean inliningEnabled() {
        return getContext().getEspressoEnv().InlineMethodHandle;
    }

    public abstract Object call(Object[] args);

    public Object processReturnValue(Object obj, JavaKind kind) {
        switch (kind) {
            case Boolean:
                return ((int) obj != 0);
            case Byte:
                return ((byte) (int) obj);
            case Char:
                return ((char) (int) obj);
            case Short:
                return ((short) (int) obj);
            default:
                return obj;
        }
    }
}
