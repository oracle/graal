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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.tregex.nodes.TRegexLazyCaptureGroupsRootNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexLazyFindStartRootNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;

public final class LazyCaptureGroupsResult extends RegexResult implements JsonConvertible {

    private final int fromIndex;
    private final int end;
    private int[] result = null;
    private final CallTarget findStartCallTarget;
    private final CallTarget captureGroupCallTarget;

    public LazyCaptureGroupsResult(RegexObject regex,
                    Object input,
                    int fromIndex,
                    int end,
                    int numberOfCaptureGroups,
                    CallTarget findStartCallTarget,
                    CallTarget captureGroupCallTarget) {
        super(regex, input, numberOfCaptureGroups);
        this.fromIndex = fromIndex;
        this.end = end;
        this.findStartCallTarget = findStartCallTarget;
        this.captureGroupCallTarget = captureGroupCallTarget;
    }

    public LazyCaptureGroupsResult(RegexObject regex, Object input, int[] result) {
        this(regex, input, -1, -1, result.length / 2, null, null);
        this.result = result;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getEnd() {
        return end;
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
        return new Object[]{getInput(), getEnd() - 1, getFromIndex()};
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
        return new Object[]{this, start + 1, getEnd()};
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
    public void debugForceEvaluation() {
        if (getFindStartCallTarget() == null) {
            getCaptureGroupCallTarget().call(createArgsCGNoFindStart());
        } else {
            getCaptureGroupCallTarget().call(createArgsCG((int) getFindStartCallTarget().call(createArgsFindStart())));
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (result == null) {
            debugForceEvaluation();
        }
        return Arrays.toString(result);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("input", getInput().toString()),
                        Json.prop("fromIndex", fromIndex),
                        Json.prop("end", end),
                        Json.prop("result", Json.array(result)));
    }
}
