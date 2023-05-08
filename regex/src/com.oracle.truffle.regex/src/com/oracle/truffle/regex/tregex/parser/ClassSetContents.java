/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import org.graalvm.collections.EconomicSet;

public final class ClassSetContents implements JsonConvertible {

    enum Kind {
        Character,
        NestedClass,
        Strings,
        Range
    }

    private final Kind kind;
    private final CodePointSet codePointSet;
    private final EconomicSet<String> strings;

    private ClassSetContents(Kind kind, CodePointSet codePointSet, EconomicSet<String> strings) {
        this.kind = kind;
        this.codePointSet = codePointSet;
        this.strings = strings;
    }

    public static ClassSetContents createCharacter(int codePoint) {
        return new ClassSetContents(Kind.Character, CodePointSet.create(codePoint), EconomicSet.create());
    }

    public static ClassSetContents createNestedClass(CodePointSet codePointSet, EconomicSet<String> strings) {
        return new ClassSetContents(Kind.NestedClass, codePointSet, strings);
    }

    public static ClassSetContents createStrings(CodePointSet singleCodePoints, EconomicSet<String> strings) {
        return new ClassSetContents(Kind.Strings, singleCodePoints, strings);
    }

    public static ClassSetContents createRange(int lo, int hi) {
        return new ClassSetContents(Kind.Range, CodePointSet.create(lo, hi), EconomicSet.create());
    }

    public EconomicSet<String> getStrings() {
        return strings;
    }

    public CodePointSet getCodePointSet() {
        return codePointSet;
    }

    public boolean isCharacter() {
        return kind == Kind.Character;
    }

    public boolean isRange() {
        return kind == Kind.Range;
    }

    public int getCodePoint() {
        assert isCharacter();
        return codePointSet.getLo(0);
    }

    public boolean isCodePointSetOnly() {
        return strings.isEmpty();
    }

    @Override
    public JsonValue toJson() {
        return null;
    }
}
