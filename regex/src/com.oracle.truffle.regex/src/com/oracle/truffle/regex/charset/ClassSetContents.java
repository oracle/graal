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
package com.oracle.truffle.regex.charset;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class ClassSetContents implements JsonConvertible {

    enum Kind {
        Character,
        Class,
        Strings,
        Range,
        POSIXCollationElement,
        POSIXCollationEquivalenceClass
    }

    private final Kind kind;
    private final CodePointSet codePointSet;
    private final EconomicSet<String> strings;
    private final boolean mayContainStrings;

    private ClassSetContents(Kind kind, CodePointSet codePointSet, EconomicSet<String> strings, boolean mayContainStrings) {
        this.kind = kind;
        this.codePointSet = codePointSet;
        this.strings = strings;
        this.mayContainStrings = mayContainStrings;
    }

    public static ClassSetContents createCharacter(int codePoint) {
        return new ClassSetContents(Kind.Character, CodePointSet.create(codePoint), EconomicSet.create(), false);
    }

    public static ClassSetContents createUnicodePropertyOfStrings(CodePointSet codePointSet, EconomicSet<String> strings) {
        return new ClassSetContents(Kind.Class, codePointSet, strings, true);
    }

    public static ClassSetContents createCharacterClass(CodePointSet codePointSet) {
        return new ClassSetContents(Kind.Class, codePointSet, EconomicSet.create(), false);
    }

    public static ClassSetContents createClass(CodePointSet codePointSet, EconomicSet<String> strings, boolean mayContainStrings) {
        return new ClassSetContents(Kind.Class, codePointSet, strings, mayContainStrings);
    }

    public static ClassSetContents createStrings(CodePointSet singleCodePoints, EconomicSet<String> strings) {
        return new ClassSetContents(Kind.Strings, singleCodePoints, strings, !strings.isEmpty());
    }

    public static ClassSetContents createRange(int lo, int hi) {
        return new ClassSetContents(Kind.Range, CodePointSet.create(lo, hi), EconomicSet.create(), false);
    }

    public static ClassSetContents createPOSIXCollationElement(int codePoint) {
        return new ClassSetContents(Kind.POSIXCollationElement, CodePointSet.create(codePoint), EconomicSet.create(), false);
    }

    public static ClassSetContents createPOSIXCollationElement(String string) {
        EconomicSet<String> strings = EconomicSet.create();
        strings.add(string);
        return new ClassSetContents(Kind.POSIXCollationElement, CodePointSet.getEmpty(), strings, true);
    }

    public static ClassSetContents createPOSIXCollationEquivalenceClass(int codePoint) {
        return new ClassSetContents(Kind.POSIXCollationEquivalenceClass, CodePointSet.create(codePoint), EconomicSet.create(), false);
    }

    public static ClassSetContents createPOSIXCollationEquivalenceClass(String string) {
        EconomicSet<String> strings = EconomicSet.create();
        strings.add(string);
        return new ClassSetContents(Kind.POSIXCollationEquivalenceClass, CodePointSet.getEmpty(), strings, true);
    }

    public ClassSetContents unionUnicodePropertyOfStrings(ClassSetContents other) {
        EconomicSet<String> unionStrings = EconomicSet.create();
        unionStrings.addAll(strings);
        unionStrings.addAll(other.strings);
        return new ClassSetContents(Kind.Class, codePointSet.union(other.codePointSet), unionStrings, mayContainStrings || other.mayContainStrings);
    }

    public ClassSetContents caseFold(CodePointSetAccumulator tmp) {
        EconomicSet<String> foldedStrings = EconomicSet.create(strings.size());
        for (String string : strings) {
            foldedStrings.add(CaseFoldData.icuSimpleCaseFold(string));
        }
        return new ClassSetContents(kind, CaseFoldData.simpleCaseFold(codePointSet, tmp), foldedStrings, mayContainStrings);
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

    public boolean isCharacterClass() {
        return kind == Kind.Class;
    }

    public boolean isRange() {
        return kind == Kind.Range;
    }

    public boolean isPosixCollationElement() {
        return kind == Kind.POSIXCollationElement;
    }

    public boolean isPosixCollationEquivalenceClass() {
        return kind == Kind.POSIXCollationEquivalenceClass;
    }

    public boolean isAllowedInRange() {
        return kind == Kind.Character || ((kind == Kind.POSIXCollationElement || kind == Kind.POSIXCollationEquivalenceClass) && isCodePointSetOnly());
    }

    public int getCodePoint() {
        assert isAllowedInRange();
        return codePointSet.getLo(0);
    }

    public boolean isCodePointSetOnly() {
        return strings.isEmpty();
    }

    public boolean mayContainStrings() {
        return mayContainStrings;
    }

    @Override
    public JsonValue toJson() {
        return null;
    }
}
