/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.result.RegexResult;

/**
 * {@link CompiledRegexObject}s represent the {@link CallTarget}s produced by regular expression
 * compilers as executable {@link TruffleObject}s. They accept the following arguments:
 * <ol>
 * <li>{@link RegexObject} {@code regexObject}: the {@link RegexObject} to which this
 * {@link CompiledRegexObject} belongs</li>
 * <li>{@link Object} {@code input}: the character sequence to search in. This may either be a
 * {@link String} or a {@link TruffleObject} that responds to
 * {@link InteropLibrary#hasArrayElements(Object)} and returns {@link Character}s on indexed
 * {@link InteropLibrary#readArrayElement(Object, long)} requests.</li>
 * <li>{@link Number} {@code fromIndex}: the position to start searching from. This argument will be
 * cast to {@code int}, since a {@link String} can not be longer than {@link Integer#MAX_VALUE}. If
 * {@code fromIndex} is greater than {@link Integer#MAX_VALUE}, this method will immediately return
 * NO_MATCH.</li>
 * </ol>
 * The return value is a {@link RegexResult} or a compatible {@link TruffleObject}.
 * <p>
 * A {@link CompiledRegexObject} can be obtained by executing a {@link RegexCompiler}. The purpose
 * of this class is to move from {@link RegexLanguage}-specific {@link CallTarget}s to executable
 * {@link TruffleObject}s that can be passed around via interop and can come from external RegExp
 * compilers (e.g. see {@link ForeignRegexCompiler}).
 */
public class CompiledRegexObject implements RegexLanguageObject {

    private final CallTarget callTarget;

    public CompiledRegexObject(RegexLanguage language, RegexExecRootNode compiledRegex) {
        callTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language, compiledRegex.getFrameDescriptor(), compiledRegex));
    }

    public CallTarget getCallTarget() {
        return callTarget;
    }
}
