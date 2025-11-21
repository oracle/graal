/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import java.lang.reflect.Field;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public final class EspressoResolvedJavaField extends AbstractEspressoResolvedJavaField {
    private Field mirrorCache;

    EspressoResolvedJavaField(EspressoResolvedInstanceType holder) {
        super(holder);
    }

    @Override
    public native int getOffset();

    @Override
    protected native int getFlags();

    @Override
    protected native String getName0();

    @Override
    protected native JavaType getType0(UnresolvedJavaType unresolved);

    @Override
    protected native int getConstantValueIndex();

    @Override
    protected boolean equals0(AbstractEspressoResolvedJavaField that) {
        if (that instanceof EspressoResolvedJavaField espressoResolvedJavaField) {
            return equals0(espressoResolvedJavaField);
        }
        return false;
    }

    private native boolean equals0(EspressoResolvedJavaField that);

    @Override
    public native int hashCode();

    public Field getMirror() {
        if (mirrorCache == null) {
            mirrorCache = getMirror0();
        }
        return mirrorCache;
    }

    private native Field getMirror0();

    @Override
    protected native byte[] getRawAnnotationBytes(int category);
}
