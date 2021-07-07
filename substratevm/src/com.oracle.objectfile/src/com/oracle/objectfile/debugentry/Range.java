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

/**
 * Details of a specific address range in a compiled method either a primary range identifying a
 * whole method or a sub-range identifying a sequence of instructions that belong to an inlined
 * method.
 */

public class Range {
    private static final String CLASS_DELIMITER = ".";
    private final MethodEntry methodEntry;
    private final String fullMethodNameWithParams;
    private final int lo;
    private final int hi;
    private final int line;
    /*
     * This is null for a primary range.
     */
    private final Range primary;

    /*
     * Create a primary range.
     */
    public Range(StringTable stringTable, MethodEntry methodEntry, int lo, int hi, int line) {
        this(stringTable, methodEntry, lo, hi, line, null);
    }

    /*
     * Create a primary or secondary range.
     */
    public Range(StringTable stringTable, MethodEntry methodEntry, int lo, int hi, int line, Range primary) {
        assert methodEntry != null;
        if (methodEntry.fileEntry != null) {
            stringTable.uniqueDebugString(methodEntry.fileEntry.getFileName());
            stringTable.uniqueDebugString(methodEntry.fileEntry.getPathName());
        }
        this.methodEntry = methodEntry;
        this.fullMethodNameWithParams = stringTable.uniqueString(constructClassAndMethodNameWithParams());
        this.lo = lo;
        this.hi = hi;
        this.line = line;
        this.primary = primary;
    }

    public boolean contains(Range other) {
        return (lo <= other.lo && hi >= other.hi);
    }

    public boolean isPrimary() {
        return getPrimary() == null;
    }

    public Range getPrimary() {
        return primary;
    }

    public String getClassName() {
        return methodEntry.ownerType.typeName;
    }

    public String getMethodName() {
        return methodEntry.memberName;
    }

    public String getSymbolName() {
        return methodEntry.getSymbolName();
    }

    public int getHi() {
        return hi;
    }

    public int getLo() {
        return lo;
    }

    public int getLine() {
        return line;
    }

    public String getFullMethodName() {
        return constructClassAndMethodName();
    }

    public String getFullMethodNameWithParams() {
        return fullMethodNameWithParams;
    }

    public boolean isDeoptTarget() {
        return methodEntry.isDeoptTarget;
    }

    private String getExtendedMethodName(boolean includeClass, boolean includeParams, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder();
        if (includeReturnType && methodEntry.valueType.typeName.length() > 0) {
            builder.append(methodEntry.valueType.typeName);
            builder.append(' ');
        }
        if (includeClass && getClassName() != null) {
            builder.append(getClassName());
            builder.append(CLASS_DELIMITER);
        }
        builder.append(getMethodName());
        if (includeParams) {
            builder.append('(');
            builder.append(String.join(", ", methodEntry.paramNames));
            builder.append(')');
        }
        if (includeReturnType) {
            builder.append(" ");
            builder.append(methodEntry.valueType.typeName);
        }
        return builder.toString();
    }

    private String constructClassAndMethodName() {
        return getExtendedMethodName(true, false, false);
    }

    private String constructClassAndMethodNameWithParams() {
        return getExtendedMethodName(true, true, false);
    }

    public FileEntry getFileEntry() {
        return methodEntry.fileEntry;
    }

    public int getModifiers() {
        return methodEntry.modifiers;
    }

    @Override
    public String toString() {
        return String.format("Range(lo=0x%05x hi=0x%05x %s %s:%d)", lo, hi, constructClassAndMethodNameWithParams(), methodEntry.getFullFileName(), line);
    }

    public String getFileName() {
        return methodEntry.getFileName();
    }

    public MethodEntry getMethodEntry() {
        return methodEntry;
    }
}
