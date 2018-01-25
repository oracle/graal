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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import java.nio.ByteBuffer;

abstract class ClosureArgumentNode extends Node {

    public abstract Object execute(Object arg);

    abstract static class BufferClosureArgumentNode extends ClosureArgumentNode {

        private final LibFFIType type;

        BufferClosureArgumentNode(LibFFIType type) {
            this.type = type;
        }

        @Specialization
        public Object deserialize(ByteBuffer arg) {
            NativeArgumentBuffer buffer = new NativeArgumentBuffer.Direct(arg, 0);
            return type.deserialize(buffer);
        }
    }

    static class ObjectClosureArgumentNode extends ClosureArgumentNode {

        @Override
        public Object execute(Object arg) {
            if (arg == null) {
                return new NativePointer(0);
            } else {
                return arg;
            }
        }
    }

    static class StringClosureArgumentNode extends ClosureArgumentNode {

        @Override
        public Object execute(Object arg) {
            if (arg == null) {
                return new NativeString(0);
            } else {
                return arg;
            }
        }
    }
}
