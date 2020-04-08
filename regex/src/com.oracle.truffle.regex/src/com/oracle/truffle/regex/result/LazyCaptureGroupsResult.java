/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.result;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyCaptureGroupsRootNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyFindStartRootNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

public final class LazyCaptureGroupsResult extends LazyResult implements JsonConvertible {

    private int[] result = null;
    private final CallTarget findStartCallTarget;
    private final CallTarget captureGroupCallTarget;

    public LazyCaptureGroupsResult(Object input,
                    int fromIndex,
                    int end,
                    CallTarget findStartCallTarget,
                    CallTarget captureGroupCallTarget) {
        super(input, fromIndex, end);
        this.findStartCallTarget = findStartCallTarget;
        this.captureGroupCallTarget = captureGroupCallTarget;
    }

    public LazyCaptureGroupsResult(Object input, int[] result) {
        this(input, -1, -1, null, null);
        this.result = result;
    }

    @Override
    public int getStart(int groupNumber) {
        return result[groupNumber * 2];
    }

    @Override
    public int getEnd(int groupNumber) {
        return result[groupNumber * 2 + 1];
    }

    public void setResult(int[] result) {
        this.result = result;
    }

    public int[] getResult() {
        return result;
    }

    public CallTarget getFindStartCallTarget() {
        return findStartCallTarget;
    }

    public CallTarget getCaptureGroupCallTarget() {
        return captureGroupCallTarget;
    }

    /**
     * Creates an arguments array suitable for the lazy calculation of this result's starting index.
     *
     * @return an arguments array suitable for calling the {@link TRegexLazyFindStartRootNode}
     *         contained in {@link #getFindStartCallTarget()}.
     */
    public Object[] createArgsFindStart() {
        return new Object[]{getInput(), getFromIndex(), getEnd()};
    }

    /**
     * Creates an arguments array suitable for the lazy calculation of this result's capture group
     * boundaries.
     *
     * @param start The value returned by the call to the {@link TRegexLazyFindStartRootNode}
     *            contained in {@link #getFindStartCallTarget()}.
     * @return an arguments array suitable for calling the {@link TRegexLazyCaptureGroupsRootNode}
     *         contained in {@link #getCaptureGroupCallTarget()}.
     */
    public Object[] createArgsCG(int start) {
        return new Object[]{this, start, getEnd()};
    }

    /**
     * Creates an arguments array suitable for the lazy calculation of this result's capture group
     * boundaries if there is no find-start call target (this is the case when the expression is
     * sticky or starts with "^").
     *
     * @return an arguments array suitable for calling the {@link TRegexLazyCaptureGroupsRootNode}
     *         contained in {@link #getCaptureGroupCallTarget()}.
     */
    public Object[] createArgsCGNoFindStart() {
        assert findStartCallTarget == null;
        return new Object[]{this, getFromIndex(), getEnd()};
    }

    /**
     * Forces evaluation of this lazy regex result. Do not use this method on any fast paths, use
     * {@link com.oracle.truffle.regex.runtime.nodes.LazyCaptureGroupGetResultNode} instead!
     */
    @TruffleBoundary
    @Override
    public void debugForceEvaluation() {
        if (result == null) {
            if (getFindStartCallTarget() == null) {
                getCaptureGroupCallTarget().call(createArgsCGNoFindStart());
            } else {
                getCaptureGroupCallTarget().call(createArgsCG((int) getFindStartCallTarget().call(createArgsFindStart())));
            }
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (result == null) {
            debugForceEvaluation();
        }
        StringBuilder sb = new StringBuilder("[").append(result[0]);
        for (int i = 1; i < result.length; i++) {
            sb.append(", ").append(result[i]);
        }
        return sb.append("]").toString();
    }

    @TruffleBoundary
    @Override
    public JsonObject toJson() {
        return super.toJson().append(Json.prop("result", Json.array(result)));
    }
}
