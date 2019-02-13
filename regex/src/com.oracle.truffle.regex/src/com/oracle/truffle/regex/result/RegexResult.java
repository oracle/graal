/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.RegexLanguageObject;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.runtime.RegexResultEndArrayObject;
import com.oracle.truffle.regex.runtime.RegexResultMessageResolutionForeign;
import com.oracle.truffle.regex.runtime.RegexResultStartArrayObject;

/**
 * {@link RegexResult} is a {@link TruffleObject} that represents the result of matching a regular
 * expression against a string. It can be obtained as the result of a {@link RegexObject}'s
 * {@code exec} method and has the following properties:
 * <ol>
 * <li>{@link Object} {@code input}: The input sequence this result was calculated from. If the
 * result is no match, this property is {@code null}.</li>
 * <li>{@code boolean isMatch}: {@code true} if a match was found, {@code false} otherwise.</li>
 * <li>{@code int groupCount}: number of capture groups present in the regular expression, including
 * group 0. If the result is no match, this property is {@code 0}.</li>
 * <li>{@link TruffleObject} {@code start}: array of positions where the beginning of the capture
 * group with the given number was found. If the result is no match, this property is an empty
 * array. Capture group number {@code 0} denotes the boundaries of the entire expression. If no
 * match was found for a particular capture group, the returned value at its respective index is
 * {@code -1}.</li>
 * <li>{@link TruffleObject} {@code end}: array of positions where the end of the capture group with
 * the given number was found. If the result is no match, this property is an empty array. Capture
 * group number {@code 0} denotes the boundaries of the entire expression. If no match was found for
 * a particular capture group, the returned value at its respective index is {@code -1}.</li>
 * </ol>
 * </li>
 */
public abstract class RegexResult implements RegexLanguageObject {

    public static final RegexResult NO_MATCH = new RegexResult(null, "NULL", 0) {

        @Override
        public String toString() {
            return "NO_MATCH";
        }
    };

    private final RegexObject regex;
    private final Object input;
    private final int groupCount;

    public RegexResult(RegexObject regex, Object input, int groupCount) {
        this.regex = regex;
        this.input = input;
        this.groupCount = groupCount;
    }

    public final RegexObject getCompiledRegex() {
        return regex;
    }

    public final Object getInput() {
        return input;
    }

    public final int getGroupCount() {
        return groupCount;
    }

    public final RegexResultStartArrayObject getStartArrayObject() {
        // this allocation should get virtualized and optimized away by graal
        return new RegexResultStartArrayObject(this);
    }

    public final RegexResultEndArrayObject getEndArrayObject() {
        // this allocation should get virtualized and optimized away by graal
        return new RegexResultEndArrayObject(this);
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof RegexResult;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return RegexResultMessageResolutionForeign.ACCESS;
    }
}
