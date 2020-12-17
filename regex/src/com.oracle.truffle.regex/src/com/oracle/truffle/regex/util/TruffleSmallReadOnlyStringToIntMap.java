/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.util;

import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.AbstractRegexObject;

@ExportLibrary(InteropLibrary.class)
public final class TruffleSmallReadOnlyStringToIntMap extends AbstractRegexObject {

    public static final int MAX_SIZE = 8;

    private final TruffleReadOnlyKeysArray keys;
    @CompilationFinal(dimensions = 1) private final String[] map;

    private TruffleSmallReadOnlyStringToIntMap(String[] keys, String[] map) {
        this.keys = new TruffleReadOnlyKeysArray(keys);
        this.map = map;
    }

    @TruffleBoundary
    public static boolean canCreate(Map<String, Integer> map) {
        return maxValue(map) < MAX_SIZE;
    }

    @TruffleBoundary
    public static TruffleSmallReadOnlyStringToIntMap create(Map<String, Integer> argMap) {
        String[] keys = new String[argMap.size()];
        String[] map = new String[maxValue(argMap) + 1];
        assert map.length <= MAX_SIZE;
        int i = 0;
        for (Map.Entry<String, Integer> entry : argMap.entrySet()) {
            keys[i++] = entry.getKey();
            assert map[entry.getValue()] == null;
            map[entry.getValue()] = entry.getKey();
        }
        return new TruffleSmallReadOnlyStringToIntMap(keys, map);
    }

    @TruffleBoundary
    private static int maxValue(Map<String, Integer> map) {
        int max = 0;
        for (int i : map.values()) {
            max = Math.max(max, i);
        }
        return max;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return keys;
    }

    @ExportMessage
    boolean isMemberReadable(String symbol) {
        return keys.contains(symbol);
    }

    @ExportMessage
    int readMember(String symbol) {
        for (int i = 0; i < map.length; i++) {
            if (map[i] != null && map[i].equals(symbol)) {
                return i;
            }
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public String toString() {
        return "TRegexReadOnlyMap";
    }
}
