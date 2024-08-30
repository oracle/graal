/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo.DebugTypeKind;
import org.graalvm.compiler.debug.DebugContext;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_INTEGRAL;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_NUMERIC;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_SIGNED;

public class PrimitiveTypeEntry extends TypeEntry {
    private char typeChar;
    private int flags;
    private int bitCount;

    public PrimitiveTypeEntry(String typeName, int size) {
        super(typeName, size);
        typeChar = '#';
        flags = 0;
        bitCount = 0;
    }

    @Override
    public DebugTypeKind typeKind() {
        return DebugTypeKind.PRIMITIVE;
    }

    @Override
    public void addDebugInfo(DebugInfoBase debugInfoBase, DebugTypeInfo debugTypeInfo, DebugContext debugContext) {
        super.addDebugInfo(debugInfoBase, debugTypeInfo, debugContext);
        DebugPrimitiveTypeInfo debugPrimitiveTypeInfo = (DebugPrimitiveTypeInfo) debugTypeInfo;
        flags = debugPrimitiveTypeInfo.flags();
        typeChar = debugPrimitiveTypeInfo.typeChar();
        bitCount = debugPrimitiveTypeInfo.bitCount();
        if (debugContext.isLogEnabled()) {
            debugContext.log("typename %s %s (%d bits)%n", typeName, decodeFlags(), bitCount);
        }
    }

    private String decodeFlags() {
        StringBuilder builder = new StringBuilder();
        if ((flags & FLAG_NUMERIC) != 0) {
            if ((flags & FLAG_INTEGRAL) != 0) {
                if ((flags & FLAG_SIGNED) != 0) {
                    builder.append("SIGNED ");
                } else {
                    builder.append("UNSIGNED ");
                }
                builder.append("INTEGRAL");
            } else {
                builder.append("FLOATING");
            }
        } else {
            if (bitCount > 0) {
                builder.append("LOGICAL");
            } else {
                builder.append("VOID");
            }
        }
        return builder.toString();
    }

    public char getTypeChar() {
        return typeChar;
    }

    public int getBitCount() {
        return bitCount;
    }

    public int getFlags() {
        return flags;
    }
}
