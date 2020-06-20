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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Predefined lists of capture group start and end indices. Used for regular expressions like
 * /(\w)(\d)/
 */
public final class PreCalculatedResultFactory implements JsonConvertible {

    @CompilationFinal(dimensions = 1) private final int[] indices;
    @CompilationFinal private int length;

    public PreCalculatedResultFactory(int nGroups) {
        this.indices = new int[nGroups * 2];
        Arrays.fill(this.indices, -1);
    }

    private PreCalculatedResultFactory(int[] indices, int length) {
        this.indices = indices;
        this.length = length;
    }

    public PreCalculatedResultFactory copy() {
        return new PreCalculatedResultFactory(Arrays.copyOf(indices, indices.length), length);
    }

    public int getStart(int groupNr) {
        return indices[groupNr * 2];
    }

    public void setStart(int groupNr, int value) {
        indices[groupNr * 2] = value;
    }

    public int getEnd(int groupNr) {
        return indices[(groupNr * 2) + 1];
    }

    public void setEnd(int groupNr, int value) {
        indices[(groupNr * 2) + 1] = value;
    }

    /**
     * Outermost bounds of the result, necessary for expressions where lookaround matches may exceed
     * the bounds of capture group 0.
     */
    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void updateIndices(CompilationFinalBitSet updateIndices, int index) {
        for (int i : updateIndices) {
            indices[i] = index;
        }
    }

    public RegexResult createFromStart(int start) {
        return createFromOffset(start);
    }

    public RegexResult createFromEnd(int end) {
        return createFromOffset(end - length);
    }

    public int getNumberOfGroups() {
        return indices.length / 2;
    }

    private RegexResult createFromOffset(int offset) {
        if (indices.length == 2) {
            return new SingleResult(indices[0] + offset, indices[1] + offset);
        }
        final int[] realIndices = new int[indices.length];
        applyOffset(realIndices, offset);
        return new SingleIndexArrayResult(realIndices);
    }

    public void applyRelativeToEnd(int[] target, int end) {
        applyOffset(target, end - length);
    }

    private void applyOffset(int[] target, int offset) {
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] == -1) {
                target[i] = -1;
            } else {
                target[i] = indices[i] + offset;
            }
        }
    }

    @Override
    public int hashCode() {
        return length * 31 + Arrays.hashCode(indices);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PreCalculatedResultFactory)) {
            return false;
        }
        PreCalculatedResultFactory o = (PreCalculatedResultFactory) obj;
        return length == o.length && Arrays.equals(indices, o.indices);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("indices", Json.array(indices)),
                        Json.prop("length", length));
    }
}
