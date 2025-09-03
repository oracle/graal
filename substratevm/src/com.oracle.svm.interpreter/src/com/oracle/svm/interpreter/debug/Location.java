/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.debug;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import static com.oracle.svm.core.code.FrameSourceInfo.LINENUMBER_NATIVE;
import static com.oracle.svm.core.code.FrameSourceInfo.LINENUMBER_UNKNOWN;

public interface Location {
    int LINENUMBER_MARKER = LINENUMBER_NATIVE;

    JavaMethod method();

    int bci();

    int lineNumber();

    static Location create(ResolvedJavaMethod method, int bci) {
        return create(method, bci, LINENUMBER_MARKER);
    }

    static Location create(ResolvedJavaMethod method, int bci, int lineNo) {
        return new Location() {
            private int lineNumber = lineNo;

            @Override
            public JavaMethod method() {
                return method;
            }

            @Override
            public int bci() {
                return bci;
            }

            @Override
            public int lineNumber() {
                if (lineNumber != LINENUMBER_MARKER) {
                    return lineNumber;
                }
                LineNumberTable lineNumberTable = method.getLineNumberTable();
                if (lineNumberTable != null) {
                    lineNumber = lineNumberTable.getLineNumber(bci);
                } else {
                    lineNumber = LINENUMBER_UNKNOWN;
                }
                return lineNumber;
            }
        };
    }

    /**
     * Returns the line number associated with a BCI, or -1 (LINENUMBER_UNKNOWN) if the method is
     * native or no line information is available.
     */
    static int getLineNumber(ResolvedJavaMethod method, int bci) {
        if (method.isNative()) {
            return LINENUMBER_UNKNOWN;
        }
        LineNumberTable lineNumberTable = method.getLineNumberTable();
        if (lineNumberTable != null) {
            return lineNumberTable.getLineNumber(bci);
        }
        return LINENUMBER_UNKNOWN;
    }
}
