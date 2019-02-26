/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;

public interface MethodHandleConstant extends PoolConstant {
    default Tag tag() {
        return Tag.METHODHANDLE;
    }

    enum RefKind {
        GETFIELD(1),
        GETSTATIC(2),
        PUTFIELD(3),
        PUTSTATIC(4),
        INVOKEVIRTUAL(5),
        INVOKESTATIC(6),
        INVOKESPECIAL(7),
        NEWINVOKESPECIAL(8),
        INVOKEINTERFACE(9);

        public final int value;

        RefKind(int value) {
            this.value = value;
        }

        public static RefKind forValue(int value) {
            // @formatter:off
            switch(value) {
                case 1: return GETFIELD;
                case 2: return GETSTATIC;
                case 3: return PUTFIELD;
                case 4: return PUTSTATIC;
                case 5: return INVOKEVIRTUAL;
                case 6: return INVOKESTATIC;
                case 7: return INVOKESPECIAL;
                case 8: return NEWINVOKESPECIAL;
                case 9: return INVOKEINTERFACE;
                default: return null;
            }
            // @formatter:on
        }
    }

    RefKind getRefKind();

    char getRefIndex();

    @Override
    default String toString(ConstantPool pool) {
        return getRefKind() + " " + pool.at(getRefIndex()).toString(pool);
    }

    final class Index implements MethodHandleConstant {

        private final byte refKind;
        private final char refIndex;

        Index(int refKind, int refIndex) {
            this.refKind = PoolConstant.u1(refKind);
            this.refIndex = PoolConstant.u2(refIndex);
        }

        public RefKind getRefKind() {
            RefKind kind = RefKind.forValue(refKind);
            assert kind != null;
            return kind;
        }

        public char getRefIndex() {
            return refIndex;
        }
    }
}
