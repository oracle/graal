/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.core.common.spi.ForeignCallSignature;

public class ArrayIndexOf {

    public static final ForeignCallSignature STUB_INDEX_OF_1_BYTE = new ForeignCallSignature(
                    "indexOf1Byte", int.class, byte[].class, int.class, int.class, byte.class);

    public static final ForeignCallSignature STUB_INDEX_OF_1_CHAR_COMPACT = new ForeignCallSignature(
                    "indexOf1CharCompact", int.class, byte[].class, int.class, int.class, char.class);

    public static int indexOf1Byte(byte[] array, int arrayLength, int fromIndex, byte b) {
        return ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_1_BYTE, array, arrayLength, fromIndex, b);
    }

    public static int indexOf1CharCompact(byte[] array, int arrayLength, int fromIndex, char c) {
        return ArrayIndexOfDispatchNode.indexOf(STUB_INDEX_OF_1_CHAR_COMPACT, array, arrayLength, fromIndex, c);
    }
}
