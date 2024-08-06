/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.truffle.nodes.frame;

public enum VirtualFrameAccessFlags {
    // Does not access the frame storage (getTag / isX)
    BENIGN(Constants.BENIGN),

    // Accesses that do not modify the frame (getters)
    NON_STATIC(Constants.NON_STATIC),
    STATIC_PRIMITIVE(Constants.STATIC_PRIMITIVE),
    STATIC_OBJECT(Constants.STATIC_OBJECT),
    STATIC_BOTH(Constants.STATIC),

    // Accesses that modifies the frame (setters, copy, swap)
    NON_STATIC_UPDATE(Constants.NON_STATIC_UPDATE),
    STATIC_PRIMITIVE_UPDATE(Constants.STATIC_PRIMITIVE_UPDATE),
    STATIC_OBJECT_UPDATE(Constants.STATIC_OBJECT_UPDATE),
    STATIC_BOTH_UPDATE(Constants.STATIC_UPDATE),

    // Special flag.
    NON_STATIC_NO_SET_TAG_UPDATE(Constants.NON_STATIC_UPDATE_NO_SET_TAG),
    STATIC_NO_SET_TAG_UPDATE(Constants.STATIC_UPDATE_NO_SET_TAG);

    private final byte flags;

    VirtualFrameAccessFlags(byte flags) {
        this.flags = flags;
    }

    public final boolean isStatic() {
        return (flags & Constants.STATIC_FLAG) != 0;
    }

    public final boolean isPrimitive() {
        return (flags & Constants.PRIMITIVE_FLAG) != 0;
    }

    public final boolean isObject() {
        return (flags & Constants.OBJECT_FLAG) != 0;
    }

    public final boolean setsTag() {
        return (flags & Constants.SET_TAG_FLAG) != 0;
    }

    public final boolean updatesFrame() {
        return (flags & Constants.UPDATES_FRAME) != 0;
    }

    private static final class Constants {
        static final byte STATIC_FLAG = 1 << 0;
        static final byte PRIMITIVE_FLAG = 1 << 1;
        static final byte OBJECT_FLAG = 1 << 2;
        static final byte SET_TAG_FLAG = 1 << 3;
        static final byte UPDATES_FRAME = 1 << 4;

        public static final byte BENIGN = 0;
        public static final byte NON_STATIC = PRIMITIVE_FLAG | OBJECT_FLAG | SET_TAG_FLAG;

        public static final byte NON_STATIC_UPDATE = PRIMITIVE_FLAG | OBJECT_FLAG | SET_TAG_FLAG | UPDATES_FRAME;
        public static final byte NON_STATIC_UPDATE_NO_SET_TAG = PRIMITIVE_FLAG | OBJECT_FLAG | UPDATES_FRAME;
        public static final byte STATIC_UPDATE_NO_SET_TAG = STATIC_FLAG | PRIMITIVE_FLAG | OBJECT_FLAG | UPDATES_FRAME;

        public static final byte STATIC = STATIC_FLAG | PRIMITIVE_FLAG | OBJECT_FLAG | SET_TAG_FLAG;
        public static final byte STATIC_PRIMITIVE = STATIC_FLAG | PRIMITIVE_FLAG | SET_TAG_FLAG;
        public static final byte STATIC_OBJECT = STATIC_FLAG | OBJECT_FLAG | SET_TAG_FLAG;

        public static final byte STATIC_UPDATE = STATIC_FLAG | PRIMITIVE_FLAG | OBJECT_FLAG | SET_TAG_FLAG | UPDATES_FRAME;
        public static final byte STATIC_PRIMITIVE_UPDATE = STATIC_FLAG | PRIMITIVE_FLAG | SET_TAG_FLAG | UPDATES_FRAME;
        public static final byte STATIC_OBJECT_UPDATE = STATIC_FLAG | OBJECT_FLAG | SET_TAG_FLAG | UPDATES_FRAME;
    }
}
