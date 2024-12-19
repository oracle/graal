/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import com.oracle.svm.jdwp.bridge.SteppingControlConstants;

import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public record StepInfo(RequestFilter filter, int size, int depth, long threadId, Location startLocation) {

    /**
     * Converts the protocol depth to interpreter's depth.
     */
    public int getInterpreterDepth() {
        return switch (depth) {
            case SteppingConstants.INTO -> SteppingControlConstants.STEP_INTO;
            case SteppingConstants.OVER -> SteppingControlConstants.STEP_OVER;
            case SteppingConstants.OUT -> SteppingControlConstants.STEP_OUT;
            default -> throw new IllegalArgumentException(Integer.toString(depth));
        };
    }

    /**
     * Converts the protocol size to interpreter's size.
     */
    public int getInterpreterSize() {
        return switch (size) {
            case SteppingConstants.MIN -> SteppingControlConstants.STEP_MIN;
            case SteppingConstants.LINE -> SteppingControlConstants.STEP_LINE;
            default -> throw new IllegalArgumentException(Integer.toString(size));
        };
    }

    public static final class Location {

        private final boolean computeLine;
        private long methodId;
        private ResolvedJavaMethod method;
        private int bci;
        private int lineNumber;

        public Location(long methodId, int bci, boolean computeLine) {
            this.methodId = methodId;
            this.bci = bci;
            this.computeLine = computeLine;
            if (computeLine) {
                method = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaMethod(methodId);
                LineNumberTable lineNumberTable = method.getLineNumberTable();
                if (lineNumberTable != null) {
                    this.lineNumber = lineNumberTable.getLineNumber(bci);
                }
            }
        }

        public long getMethodId() {
            return methodId;
        }

        public int getBci() {
            return bci;
        }

        public boolean differsAndUpdate(long newMethodId, int newBci) {
            if (methodId != newMethodId) {
                // Different methods mean different locations
                methodId = newMethodId;
                bci = newBci;
                method = null;
                return true;
            }
            // We're in the same method
            boolean differs = bci != newBci;
            if (!differs) {
                return false;
            }
            if (!computeLine) {
                // BCI differs, we'll update and return true
                bci = newBci;
            } else {
                if (method == null) {
                    method = ServerJDWP.SYMBOLIC_REFS.toResolvedJavaMethod(methodId);
                    lineNumber = getLineAt(method, bci);
                }
                int newLineNumber = getLineAt(method, newBci);
                differs = newLineNumber == -1 || lineNumber != newLineNumber;
                if (differs) {
                    lineNumber = newLineNumber;
                }
            }
            return differs;
        }

        private static int getLineAt(ResolvedJavaMethod method, int bci) {
            LineNumberTable lineNumberTable = method.getLineNumberTable();
            if (lineNumberTable != null) {
                return lineNumberTable.getLineNumber(bci);
            } else {
                return -1;
            }
        }
    }
}
