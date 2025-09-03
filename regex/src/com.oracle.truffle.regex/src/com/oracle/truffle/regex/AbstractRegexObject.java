/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.tregex.util.TruffleReadOnlyIntArray;
import com.oracle.truffle.regex.util.TruffleNull;
import com.oracle.truffle.regex.util.TruffleReadOnlyMap;
import com.oracle.truffle.regex.util.TruffleSmallReadOnlyStringToIntMap;

@ExportLibrary(InteropLibrary.class)
public abstract class AbstractRegexObject implements TruffleObject {

    @ExportMessage
    @SuppressWarnings("static-method")
    public final boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public final Class<? extends TruffleLanguage<?>> getLanguage() {
        return RegexLanguage.class;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    public Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return toString();
    }

    @TruffleBoundary
    public static AbstractRegexObject createNamedCaptureGroupMapInt(Map<String, List<Integer>> namedCaptureGroups) {
        if (namedCaptureGroups == null) {
            return TruffleNull.INSTANCE;
        }
        if (TruffleSmallReadOnlyStringToIntMap.canCreate(namedCaptureGroups)) {
            return TruffleSmallReadOnlyStringToIntMap.create(namedCaptureGroups);
        } else {
            Map<String, Integer> simpleNamedCaptureGroups = new LinkedHashMap<>(namedCaptureGroups.size());
            for (Map.Entry<String, List<Integer>> entry : namedCaptureGroups.entrySet()) {
                assert entry.getValue().size() == 1;
                simpleNamedCaptureGroups.put(entry.getKey(), entry.getValue().get(0));
            }
            return new TruffleReadOnlyMap(simpleNamedCaptureGroups);
        }
    }

    @TruffleBoundary
    public static AbstractRegexObject createNamedCaptureGroupMapListInt(Map<String, List<Integer>> namedCaptureGroups) {
        if (namedCaptureGroups == null) {
            return TruffleNull.INSTANCE;
        } else {
            Map<String, TruffleReadOnlyIntArray> map = new LinkedHashMap<>(namedCaptureGroups.size());
            for (Map.Entry<String, List<Integer>> entry : namedCaptureGroups.entrySet()) {
                int[] array = new int[entry.getValue().size()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = entry.getValue().get(i);
                }
                map.put(entry.getKey(), new TruffleReadOnlyIntArray(array));
            }
            return new TruffleReadOnlyMap(map);
        }
    }
}
