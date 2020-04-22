/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

/**
 * Representation of context information.
 */
public class BytecodeLocation {

    public static final int EMPTY_BCI = -1;
    public static final int UNKNOWN_BCI = -2;
    public static final int DYNAMIC_ALLOCATION_BCI = -3;

    public static final BytecodeLocation EMPTY_BYTECODE_LOCATION = BytecodeLocation.create(EMPTY_BCI, null);
    public static final BytecodeLocation UNKNOWN_BYTECODE_LOCATION = BytecodeLocation.create(UNKNOWN_BCI, null);

    /**
     * Transform the Object key into a BCI. The BCI might be duplicated due to Graal method
     * substitutions and inlining. Then we use a unique object key.
     */
    public static int keyToBci(Object key) {
        int bci;
        if (key instanceof Integer) {
            bci = (int) key;
            assert bci >= 0 : "Negative BCI.";
        } else {
            bci = BytecodeLocation.UNKNOWN_BCI;
        }
        return bci;
    }

    public static boolean isValidBci(Object key) {
        if (key instanceof Integer) {
            int bci = (int) key;
            return bci != EMPTY_BCI && bci != UNKNOWN_BCI;
        }
        return false;
    }

    public static boolean hasValidBci(BytecodeLocation location) {
        return location.bci != EMPTY_BCI && location.bci != UNKNOWN_BCI;
    }

    /**
     * Byte code index.
     */
    private final int bci;

    /**
     * Method containing the BCI.
     */
    private final AnalysisMethod method;

    public static BytecodeLocation create(Object key, AnalysisMethod method) {
        return create(keyToBci(key), method);
    }

    public static BytecodeLocation create(AnalysisMethod method, Object key) {
        return create(keyToBci(key), method);
    }

    public static BytecodeLocation create(int bci, AnalysisMethod method) {
        return new BytecodeLocation(bci, method);
    }

    public static BytecodeLocation create(AnalysisMethod method, int bci) {
        return new BytecodeLocation(bci, method);
    }

    protected BytecodeLocation(int bci, AnalysisMethod method) {
        this.bci = bci;
        this.method = method;
    }

    public int getBci() {
        return bci;
    }

    public AnalysisMethod getMethod() {
        return method;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("");

        String methodName = "_";
        String sourceLocation = "_";

        if (method != null) {
            methodName = method.format("%h.%n(%p)");

            StackTraceElement traceElement = method.asStackTraceElement(bci);
            sourceLocation = traceElement.getFileName() + ":" + traceElement.getLineNumber();
        }

        str.append("@(").append(methodName).append(":").append(bci).append(")");
        str.append(" = (").append(sourceLocation).append(")");
        return str.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BytecodeLocation) {
            BytecodeLocation that = (BytecodeLocation) obj;
            return this.bci == that.bci && this.method != null ? this.method.equals(that.method) : that.method == null;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 42 ^ (method != null ? method.hashCode() : 1) ^ bci;
    }

    public static String formatLocation(BytecodeLocation bcl) {
        return formatLocation(bcl.getMethod(), bcl.getBci());
    }

    public static String formatLocation(AnalysisMethod method, int bci) {
        return method.format("%h.%n(%p)") + ":" + bci;
    }

}
