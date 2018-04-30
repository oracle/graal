/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.result;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.regex.RegexObject;

public final class TraceFinderResult extends RegexResult {

    private final int fromIndex;
    private final int end;
    private final int[] indices;
    private final CallTarget traceFinderCallTarget;
    @CompilationFinal(dimensions = 1) private final PreCalculatedResultFactory[] preCalculatedResults;
    private boolean resultCalculated = false;

    public TraceFinderResult(RegexObject regex, Object input, int fromIndex, int end, CallTarget traceFinderCallTarget, PreCalculatedResultFactory[] preCalculatedResults) {
        super(regex, input, preCalculatedResults[0].getNumberOfGroups());
        this.fromIndex = fromIndex;
        this.end = end;
        this.indices = new int[preCalculatedResults[0].getNumberOfGroups() * 2];
        this.traceFinderCallTarget = traceFinderCallTarget;
        this.preCalculatedResults = preCalculatedResults;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getEnd() {
        return end;
    }

    public int[] getIndices() {
        return indices;
    }

    public CallTarget getTraceFinderCallTarget() {
        return traceFinderCallTarget;
    }

    public PreCalculatedResultFactory[] getPreCalculatedResults() {
        return preCalculatedResults;
    }

    public boolean isResultCalculated() {
        return resultCalculated;
    }

    public void setResultCalculated() {
        this.resultCalculated = true;
    }

}
