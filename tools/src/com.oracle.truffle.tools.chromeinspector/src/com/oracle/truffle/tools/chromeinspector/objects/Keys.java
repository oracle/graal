/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.objects;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.utilities.TriState;

final class Keys extends AbstractInspectorArray {

    @CompilationFinal(dimensions = 1) private final String[] names;

    Keys(String[] names) {
        this.names = names;
    }

    @Override
    int getArraySize() {
        return names.length;
    }

    @Override
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (index < 0 || index >= Inspector.NAMES.length) {
            CompilerDirectives.transferToInterpreter();
            throw InvalidArrayIndexException.create(index);
        }
        return names[(int) index];
    }

    @Override
    TriState isIdenticalOrUndefined(Object other) {
        if (other instanceof Keys otherKeys) {
            return TriState.valueOf(names == otherKeys.names);
        } else {
            return TriState.UNDEFINED;
        }
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    int identityHashCode() {
        return Arrays.hashCode(names);
    }

}
