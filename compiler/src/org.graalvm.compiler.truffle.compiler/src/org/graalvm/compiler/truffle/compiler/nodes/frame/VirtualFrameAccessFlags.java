/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.compiler.nodes.frame;

public final class VirtualFrameAccessFlags {
    static final byte STATIC_FLAG = 1 << 0;
    static final byte PRIMITIVE_FLAG = 1 << 1;
    static final byte OBJECT_FLAG = 1 << 2;
    static final byte SET_TAG_FLAG = 1 << 3;

    public static final byte NON_STATIC = PRIMITIVE_FLAG | OBJECT_FLAG | SET_TAG_FLAG;

    public static final byte NON_STATIC_NO_SET_TAG = PRIMITIVE_FLAG | OBJECT_FLAG;

    public static final byte STATIC = STATIC_FLAG | PRIMITIVE_FLAG | OBJECT_FLAG | SET_TAG_FLAG;
    public static final byte STATIC_PRIMITIVE = STATIC_FLAG | PRIMITIVE_FLAG | SET_TAG_FLAG;
    public static final byte STATIC_OBJECT = STATIC_FLAG | OBJECT_FLAG | SET_TAG_FLAG;
}
