/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Red Hat, Inc. All rights reserved.
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

import java.nio.file.Path;
import java.nio.file.Paths;
/**
 * Details of a specific address range in a compiled method
 * either a primary range identifying a whole method
 * or a sub-range identifying a sequence of
 * instructions that belong to an inlined method.
 */

public class Range {

    /* TODO this should be '.' for PECOFF files */
    private static final String CLASS_DELIMITER = "::";

    private String fileName;
    private Path filePath;
    private String className;
    private String methodName;
    private String paramNames;
    private String returnTypeName;
    private String fullMethodName;
    private int lo;
    private int hi;
    private int line;
    /*
     * this is null for a primary range
     */
    private Range primary;

    /*
     * create a primary range
     */
    public Range(String fileName, Path filePath, String className, String methodName, String paramNames, String returnTypeName, StringTable stringTable, int lo, int hi, int line) {
        this(fileName, filePath, className, methodName, paramNames, returnTypeName, stringTable, lo, hi, line, null);
    }

    /*
     * create a primary or secondary range
     */
    public Range(String fileName, Path filePath, String className, String methodName, String paramNames, String returnTypeName, StringTable stringTable, int lo, int hi, int line, Range primary) {
        /*
         * currently file name and full method name need to go into the debug_str section
         * other strings just need to be deduplicated to save space
         */
        this.fileName = stringTable.uniqueDebugString(fileName);
        this.filePath =  filePath;
        this.className = stringTable.uniqueString(className);
        this.methodName = stringTable.uniqueString(methodName);
        this.paramNames = stringTable.uniqueString(paramNames);
        this.returnTypeName = stringTable.uniqueString(returnTypeName);
        this.fullMethodName = stringTable.uniqueDebugString(constructClassAndMethodNameWithParams());
        this.lo = lo;
        this.hi = hi;
        this.line = line;
        this.primary = primary;
    }

    /*
     * Create a slightly different copy of a previously constructed range.
     * Because the previous range was constructed by one of the other constructors,
     * a valid assumption is that all the strings have previously been inserted int he stringTable,
     * and we can avoid doing that again.
     */
    public Range(Range other, int lo, int hi) {
        this.fileName = other.fileName;
        this.filePath =  other.filePath;
        this.className = other.className;
        this.methodName = other.methodName;
        this.paramNames = other.paramNames;
        this.returnTypeName = other.returnTypeName;
        this.fullMethodName = other.fullMethodName;
        this.lo = lo;
        this.hi = hi;
        this.line = other.line;
        this.primary = other.primary;
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

    public String getFileName() {
        return fileName;
    }

    public Path getFilePath() {
        return filePath;
    }

    public Path getFileAsPath() {
        if (filePath != null) {
            return filePath.resolve(fileName);
        } else {
            return Paths.get(fileName);
        }
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
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

    public String getParamNames() {
        return paramNames;
    }

    public String getClassAndMethodName() {
        StringBuilder builder = new StringBuilder();
        if (className != null) {
            builder.append(className).append(CLASS_DELIMITER);
        }
        builder.append(methodName);
        return builder.toString();
    }

    public String getExtendedMethodName(boolean includeParams, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder();
        if (includeReturnType && returnTypeName.length() > 0) {
            builder.append(returnTypeName);
            builder.append(' ');
        }
        if (className != null) {
            builder.append(className);
            builder.append(CLASS_DELIMITER);
        }
        builder.append(methodName);
        if (includeParams && !paramNames.isEmpty()) {
            builder.append('(');
            builder.append(paramNames);
            builder.append(')');
        }
        return builder.toString();
    }

    private String constructClassAndMethodNameWithParams() {
        return getExtendedMethodName(true, false);
    }
}
