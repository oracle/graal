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
package com.oracle.truffle.nfi.types;

import java.util.Collections;
import java.util.List;

public final class NativeSignature {

    private static final int NOT_VARARGS = -1;

    private final NativeTypeMirror retType;
    private final List<NativeTypeMirror> argTypes;

    private final int fixedArgCount;

    private NativeSignature(NativeTypeMirror retType, int fixedArgCount, List<NativeTypeMirror> argTypes) {
        this.retType = retType;
        this.argTypes = argTypes;
        this.fixedArgCount = fixedArgCount;
    }

    static NativeSignature prepare(NativeTypeMirror retType, List<NativeTypeMirror> argTypes) {
        return new NativeSignature(retType, NOT_VARARGS, argTypes);
    }

    static NativeSignature prepareVarargs(NativeTypeMirror retType, int fixedArgCount, List<NativeTypeMirror> argTypes) {
        assert 0 <= fixedArgCount && fixedArgCount <= argTypes.size();
        return new NativeSignature(retType, fixedArgCount, argTypes);
    }

    public NativeTypeMirror getRetType() {
        return retType;
    }

    public List<NativeTypeMirror> getArgTypes() {
        return Collections.unmodifiableList(argTypes);
    }

    public boolean isVarargs() {
        return fixedArgCount != NOT_VARARGS;
    }

    public int getFixedArgCount() {
        return isVarargs() ? fixedArgCount : argTypes.size();
    }
}
