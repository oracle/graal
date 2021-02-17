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
    private FileEntry fileEntry;
    private String className;
    private String methodName;
    private String symbolName;
    private String paramSignature;
    private String returnTypeName;
    private String fullMethodName;
    private String fullMethodNameWithParams;
    private int lo;
    private int hi;
    private int line;
    private boolean isDeoptTarget;
    private int modifiers;
    /*
     * This is null for a primary range.
     */
    private Range primary;

    /*
     * Create a primary range.
     */
    public Range(String className, String methodName, String symbolName, String paramSignature, String returnTypeName, StringTable stringTable, FileEntry fileEntry, int lo, int hi, int line,
                    int modifiers, boolean isDeoptTarget) {
        this(className, methodName, symbolName, paramSignature, returnTypeName, stringTable, fileEntry, lo, hi, line, modifiers, isDeoptTarget, null);
    }

    /*
     * Create a secondary range.
     */
    public Range(String className, String methodName, String symbolName, StringTable stringTable, FileEntry fileEntry, int lo, int hi, int line,
                    Range primary) {
        this(className, methodName, symbolName, "", "", stringTable, fileEntry, lo, hi, line, 0, false, primary);
    }

    /*
     * Create a primary or secondary range.
     */
    private Range(String className, String methodName, String symbolName, String paramSignature, String returnTypeName, StringTable stringTable, FileEntry fileEntry, int lo, int hi, int line,
                    int modifiers, boolean isDeoptTarget, Range primary) {
        this.fileEntry = fileEntry;
        if (fileEntry != null) {
            stringTable.uniqueDebugString(fileEntry.getFileName());
            stringTable.uniqueDebugString(fileEntry.getPathName());
        }
        this.className = stringTable.uniqueString(className);
        this.methodName = stringTable.uniqueString(methodName);
        this.symbolName = stringTable.uniqueString(symbolName);
        this.paramSignature = stringTable.uniqueString(paramSignature);
        this.returnTypeName = stringTable.uniqueString(returnTypeName);
        this.fullMethodName = stringTable.uniqueString(constructClassAndMethodName());
        this.fullMethodNameWithParams = stringTable.uniqueString(constructClassAndMethodNameWithParams());
        this.lo = lo;
        this.hi = hi;
        this.line = line;
        this.isDeoptTarget = isDeoptTarget;
        this.modifiers = modifiers;
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
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getSymbolName() {
        return symbolName;
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
        return fullMethodName;
    }

    public String getFullMethodNameWithParams() {
        return fullMethodNameWithParams;
    }

    public boolean isDeoptTarget() {
        return isDeoptTarget;
    }

    private String getExtendedMethodName(boolean includeClass, boolean includeParams, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder();
        if (includeReturnType && returnTypeName.length() > 0) {
            builder.append(returnTypeName);
            builder.append(' ');
        }
        if (includeClass && className != null) {
            builder.append(className);
            builder.append(CLASS_DELIMITER);
        }
        builder.append(methodName);
        if (includeParams) {
            builder.append('(');
            builder.append(paramSignature);
            builder.append(')');
        }
        if (includeReturnType) {
            builder.append(" ");
            builder.append(returnTypeName);
        }
        return builder.toString();
    }

    private String constructClassAndMethodName() {
        return getExtendedMethodName(true, false, false);
    }

    private String constructClassAndMethodNameWithParams() {
        return getExtendedMethodName(true, true, false);
    }

    public String getMethodReturnTypeName() {
        return returnTypeName;
    }

    public String getParamSignature() {
        return paramSignature;
    }

    public FileEntry getFileEntry() {
        return fileEntry;
    }

    public void setFileEntry(FileEntry fileEntry) {
        this.fileEntry = fileEntry;
    }

    public int getModifiers() {
        return modifiers;
    }

    @Override
    public String toString() {
        return String.format("Range(lo=0x%05x hi=0x%05x %s %s:%d)", lo, hi, constructClassAndMethodNameWithParams(), fileEntry.getFullName(), line);
    }

    public String getFileName() {
        return fileEntry.getFileName();
    }
}
