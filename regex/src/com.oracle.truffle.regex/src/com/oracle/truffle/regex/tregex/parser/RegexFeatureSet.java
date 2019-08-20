/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;

import java.util.Optional;

/**
 * A predicate that describes the set of regular expressions supported by the regex compilers being
 * used.
 */
@FunctionalInterface
public interface RegexFeatureSet {

    /**
     * Tests whether or not a regular expression is supported. Returns a descriptive error message
     * if it is not.
     * 
     * @param source the regular expression whose features are in question
     * @param features a record of features detected in the expression by the parser that validated
     *            its well-formedness
     * @return
     *         <ul>
     *         <li>{@code Optional.empty()} if the regex is supported</li>
     *         <li>{@code Optional.of(err)} if the regex is not supported; {@code err} is the error
     *         message</li>
     *         </ul>
     */
    Optional<String> testSupport(RegexSource source, RegexFeatures features);

    @TruffleBoundary
    default void checkSupport(RegexSource source, RegexFeatures features) throws UnsupportedRegexException {
        Optional<String> maybeError = testSupport(source, features);
        if (maybeError.isPresent()) {
            throw new UnsupportedRegexException(maybeError.get(), source);
        }
    }

    RegexFeatureSet DEFAULT = (RegexSource source, RegexFeatures features) -> Optional.empty();

    RegexFeatureSet TREGEX = (RegexSource source, RegexFeatures features) -> {
        if (features.hasBackReferences()) {
            return Optional.of("backreferences not supported");
        }
        if (features.hasLargeCountedRepetitions()) {
            return Optional.of("bounds of range quantifier too high");
        }
        if (features.hasNegativeLookAheadAssertions()) {
            return Optional.of("negative lookahead assertions not supported");
        }
        if (features.hasNonLiteralLookBehindAssertions()) {
            return Optional.of("body of lookbehind assertion too complex");
        }
        if (features.hasNegativeLookBehindAssertions()) {
            return Optional.of("negative lookbehind assertions not supported");
        }
        return Optional.empty();
    };

    RegexFeatureSet JONI = (RegexSource source, RegexFeatures features) -> {
        if (RegexFlags.parseFlags(source.getFlags()).isUnicode()) {
            return Optional.of("unicode mode not supported");
        }
        if (features.hasBackReferencesInLookBehind()) {
            return Optional.of("backreferences inside lookbehind assertions not supported");
        }
        if (features.hasNonTrivialQuantifiersInLookBehind()) {
            return Optional.of("quantifiers inside lookbehind assertions not supported");
        }
        if (features.hasWordBoundaryAssertionsInLookBehind()) {
            return Optional.of("word boundary assertions inside lookbehind assertions not supported");
        }
        if (features.hasEndOfStringAssertionsInLookBehind()) {
            return Optional.of("end of string assertions inside lookbehind assertions not supported");
        }
        if (features.hasNegativeLookBehindAssertionsInLookBehind()) {
            return Optional.of("negative lookbehind assertions inside lookbehind assertions not supported");
        }
        if (features.hasLookAheadAssertionsInLookBehind()) {
            return Optional.of("lookahead assertions inside lookbehind assertions not supported");
        }
        return Optional.empty();
    };

    RegexFeatureSet TREGEX_JONI = (RegexSource source, RegexFeatures features) -> {
        Optional<String> tregexBailout = TREGEX.testSupport(source, features);
        if (tregexBailout.isPresent()) {
            Optional<String> joniBailout = JONI.testSupport(source, features);
            if (joniBailout.isPresent()) {
                String bailoutReasons = String.format("TRegex: %s; Joni: %s", tregexBailout.get(), joniBailout.get());
                throw new UnsupportedRegexException(bailoutReasons, source);
            }
        }
        return Optional.empty();
    };
}
