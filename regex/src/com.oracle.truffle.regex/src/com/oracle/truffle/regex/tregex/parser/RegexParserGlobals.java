/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;

import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.tregex.parser.ast.Group;

public final class RegexParserGlobals {

    final Group wordBoundarySubstituion;
    final Group nonWordBoundarySubstitution;
    final Group unicodeIgnoreCaseWordBoundarySubstitution;
    final Group unicodeIgnoreCaseNonWordBoundarySubsitution;
    final Group multiLineCaretSubstitution;
    final Group multiLineDollarSubsitution;
    final Group noLeadSurrogateBehind;
    final Group noTrailSurrogateAhead;

    public RegexParserGlobals(RegexLanguage language) {
        final String wordBoundarySrc = "(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$)";
        final String nonWordBoundarySrc = "(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w)";
        wordBoundarySubstituion = RegexParser.parseRootLess(language, wordBoundarySrc);
        nonWordBoundarySubstitution = RegexParser.parseRootLess(language, nonWordBoundarySrc);
        // The definitions of \w and \W depend on whether or not we are using the 'u' and 'i'
        // regexp flags. This means that we cannot substitute \b and \B by the same regular
        // expression all the time; we need an alternative for when both the Unicode and
        // IgnoreCase flags are enabled. The straightforward way to do so would be to parse the
        // expressions `wordBoundarySrc` and `nonWordBoundarySrc` with the 'u' and 'i' flags.
        // However, the resulting expressions would be needlessly complicated (the unicode
        // expansion for \W matches complete surrogate pairs, which we do not care about in
        // these look-around assertions). More importantly, the engine currently does not
        // support complex lookbehind and so \W, which can match anywhere between one or two code
        // units in Unicode mode, would break the engine. Therefore, we make use of the fact
        // that the difference between /\w/ and /\w/ui is only in the two characters \u017F and
        // \u212A and we just slightly adjust the expressions `wordBoundarySrc` and
        // `nonWordBoundarySrc` and parse them in non-Unicode mode.
        final Function<String, String> includeExtraCases = s -> s.replace("\\w", "[\\w\\u017F\\u212A]").replace("\\W", "[^\\w\\u017F\\u212A]");
        unicodeIgnoreCaseWordBoundarySubstitution = RegexParser.parseRootLess(language, includeExtraCases.apply(wordBoundarySrc));
        unicodeIgnoreCaseNonWordBoundarySubsitution = RegexParser.parseRootLess(language, includeExtraCases.apply(nonWordBoundarySrc));
        multiLineCaretSubstitution = RegexParser.parseRootLess(language, "(?:^|(?<=[\\r\\n\\u2028\\u2029]))");
        multiLineDollarSubsitution = RegexParser.parseRootLess(language, "(?:$|(?=[\\r\\n\\u2028\\u2029]))");
        noLeadSurrogateBehind = RegexParser.parseRootLess(language, "(?:^|(?<=[^\\uD800-\\uDBFF]))");
        noTrailSurrogateAhead = RegexParser.parseRootLess(language, "(?:$|(?=[^\\uDC00-\\uDFFF]))");
    }

}
