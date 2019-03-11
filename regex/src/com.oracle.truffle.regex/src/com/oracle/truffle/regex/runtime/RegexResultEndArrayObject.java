/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.regex.RegexLanguageObject;
import com.oracle.truffle.regex.result.LazyCaptureGroupsResult;
import com.oracle.truffle.regex.result.NoMatchResult;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.result.SingleIndexArrayResult;
import com.oracle.truffle.regex.result.SingleResult;
import com.oracle.truffle.regex.result.SingleResultLazyStart;
import com.oracle.truffle.regex.result.StartsEndsIndexArrayResult;
import com.oracle.truffle.regex.result.TraceFinderResult;
import com.oracle.truffle.regex.runtime.nodes.LazyCaptureGroupGetResultNode;
import com.oracle.truffle.regex.runtime.nodes.TraceFinderGetResultNode;

@ExportLibrary(InteropLibrary.class)
public final class RegexResultEndArrayObject implements RegexLanguageObject {

    private final RegexResult result;

    public RegexResultEndArrayObject(RegexResult result) {
        this.result = result;
    }

    public RegexResult getResult() {
        return result;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return index >= 0 && index < result.getGroupCount();
    }

    @ExportMessage
    long getArraySize() {
        return result.getGroupCount();
    }

    @ExportMessage
    int readArrayElement(long index, @Cached RegexResultGetEndNode getEndNode) throws InvalidArrayIndexException {
        return getEndNode.execute(getResult(), (int) index);
    }

    @ReportPolymorphism
    @GenerateUncached
    abstract static class RegexResultGetEndNode extends Node {

        abstract int execute(RegexResult receiver, int groupNumber) throws InvalidArrayIndexException;

        @Specialization
        static int doNoMatch(@SuppressWarnings("unused") NoMatchResult receiver, int groupNumber) throws InvalidArrayIndexException {
            CompilerDirectives.transferToInterpreter();
            throw invalidIndexException(groupNumber);
        }

        @Specialization
        static int doSingleResult(SingleResult receiver, int groupNumber,
                        @Shared("boundsProfile") @Cached("createBinaryProfile()") ConditionProfile boundsProfile) throws InvalidArrayIndexException {
            if (boundsProfile.profile(groupNumber == 0)) {
                return receiver.getEnd();
            } else {
                CompilerDirectives.transferToInterpreter();
                throw invalidIndexException(groupNumber);
            }
        }

        @Specialization
        static int doSingleResultLazyStart(SingleResultLazyStart receiver, int groupNumber,
                        @Shared("boundsProfile") @Cached("createBinaryProfile()") ConditionProfile boundsProfile) throws InvalidArrayIndexException {
            if (boundsProfile.profile(groupNumber == 0)) {
                return receiver.getEnd();
            } else {
                CompilerDirectives.transferToInterpreter();
                throw invalidIndexException(groupNumber);
            }
        }

        @Specialization
        static int doStartsEndsIndexArray(StartsEndsIndexArrayResult receiver, int groupNumber) throws InvalidArrayIndexException {
            try {
                return receiver.getEnds()[groupNumber];
            } catch (ArrayIndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw invalidIndexException(groupNumber);
            }
        }

        @Specialization
        static int doSingleIndexArray(SingleIndexArrayResult receiver, int groupNumber) throws InvalidArrayIndexException {
            return fromSingleArray(receiver.getIndices(), groupNumber);
        }

        @Specialization
        static int doTraceFinder(TraceFinderResult receiver, int groupNumber,
                        @Cached TraceFinderGetResultNode getResultNode) throws InvalidArrayIndexException {
            return fromSingleArray(getResultNode.execute(receiver), groupNumber);
        }

        @Specialization
        static int doLazyCaptureGroups(LazyCaptureGroupsResult receiver, int groupNumber,
                        @Cached LazyCaptureGroupGetResultNode getResultNode) throws InvalidArrayIndexException {
            return fromSingleArray(getResultNode.execute(receiver), groupNumber) - 1;
        }

        private static int fromSingleArray(int[] array, int groupNumber) throws InvalidArrayIndexException {
            try {
                return array[groupNumber * 2 + 1];
            } catch (ArrayIndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw invalidIndexException(groupNumber);
            }
        }

        private static RuntimeException invalidIndexException(int groupNumber) throws InvalidArrayIndexException {
            CompilerDirectives.transferToInterpreter();
            throw InvalidArrayIndexException.create(groupNumber);
        }
    }
}
