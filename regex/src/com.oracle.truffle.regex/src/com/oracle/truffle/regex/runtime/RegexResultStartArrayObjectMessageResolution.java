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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.result.LazyCaptureGroupsResult;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.result.SingleIndexArrayResult;
import com.oracle.truffle.regex.result.SingleResult;
import com.oracle.truffle.regex.result.SingleResultLazyStart;
import com.oracle.truffle.regex.result.StartsEndsIndexArrayResult;
import com.oracle.truffle.regex.result.TraceFinderResult;
import com.oracle.truffle.regex.runtime.RegexResultStartArrayObjectMessageResolutionFactory.RegexResultGetStartNodeGen;
import com.oracle.truffle.regex.runtime.nodes.CalcResultNode;
import com.oracle.truffle.regex.runtime.nodes.LazyCaptureGroupGetResultNode;
import com.oracle.truffle.regex.runtime.nodes.TraceFinderGetResultNode;

@MessageResolution(receiverType = RegexResultStartArrayObject.class)
public class RegexResultStartArrayObjectMessageResolution {

    abstract static class RegexResultGetStartNode extends Node {

        abstract int execute(RegexResult receiver, int groupNumber);

        @Specialization(guards = {"isNoMatch(receiver)"})
        int doNoMatch(@SuppressWarnings("unused") RegexResult receiver, int groupNumber) {
            throw unknownIdentifierException(groupNumber);
        }

        static boolean isNoMatch(RegexResult receiver) {
            return receiver == RegexResult.NO_MATCH;
        }

        @Specialization(guards = {"groupNumber == 0"})
        int doSingleResult(SingleResult receiver, @SuppressWarnings("unused") int groupNumber) {
            return receiver.getStart();
        }

        @Specialization(guards = {"groupNumber != 0"})
        int doSingleResultOutOfBounds(@SuppressWarnings("unused") SingleResult receiver, int groupNumber) {
            throw unknownIdentifierException(groupNumber);
        }

        @Specialization(guards = {"groupNumber == 0", "isMinusOne(receiver.getStart())"})
        int doSingleResultLazyStartCalc(SingleResultLazyStart receiver, @SuppressWarnings("unused") int groupNumber,
                        @Cached("create()") CalcResultNode calcResult) {
            receiver.setStart((int) calcResult.execute(receiver.getFindStartCallTarget(),
                            new Object[]{receiver.getInput(), receiver.getEnd() - 1, receiver.getFromIndex()}) + 1);
            return receiver.getStart();
        }

        @Specialization(guards = {"groupNumber == 0", "!isMinusOne(receiver.getStart())"})
        int doSingleResultLazyStart(SingleResultLazyStart receiver, @SuppressWarnings("unused") int groupNumber) {
            return receiver.getStart();
        }

        static boolean isMinusOne(int i) {
            return i == -1;
        }

        @Specialization(guards = {"groupNumber != 0"})
        int doSingleResultLazyStartOutOfBounds(@SuppressWarnings("unused") SingleResultLazyStart receiver, int groupNumber) {
            throw unknownIdentifierException(groupNumber);
        }

        @Specialization
        int doStartsEndsIndexArray(StartsEndsIndexArrayResult receiver, int groupNumber) {
            try {
                return receiver.getStarts()[groupNumber];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw unknownIdentifierException(groupNumber);
            }
        }

        @Specialization
        int doSingleIndexArray(SingleIndexArrayResult receiver, int groupNumber) {
            return fromSingleArray(receiver.getIndices(), groupNumber);
        }

        @Specialization
        int doTraceFinder(TraceFinderResult receiver, int groupNumber,
                        @Cached("create()") TraceFinderGetResultNode getResultNode) {
            return fromSingleArray(getResultNode.execute(receiver), groupNumber);
        }

        @Specialization
        int doLazyCaptureGroups(LazyCaptureGroupsResult receiver, int groupNumber,
                        @Cached("create()") LazyCaptureGroupGetResultNode getResultNode) {
            return fromSingleArray(getResultNode.execute(receiver), groupNumber) - 1;
        }

        private static int fromSingleArray(int[] array, int groupNumber) {
            try {
                return array[groupNumber * 2];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw unknownIdentifierException(groupNumber);
            }
        }

        public static RegexResultGetStartNode create() {
            return RegexResultGetStartNodeGen.create();
        }
    }

    private static RuntimeException unknownIdentifierException(int groupNumber) {
        CompilerDirectives.transferToInterpreter();
        return UnknownIdentifierException.raise(Integer.toString(groupNumber));
    }

    @Resolve(message = "READ")
    abstract static class RegexResultStartReadNode extends Node {

        @Child RegexResultGetStartNode getStartNode = RegexResultGetStartNode.create();

        public Object access(RegexResultStartArrayObject receiver, int index) {
            return getStartNode.execute(receiver.getResult(), index);
        }
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class RegexResultStartHasSizeNode extends Node {

        @SuppressWarnings("unused")
        public boolean access(RegexResultStartArrayObject receiver) {
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class RegexResultStartGetSizeNode extends Node {

        public int access(RegexResultStartArrayObject receiver) {
            return receiver.getResult().getGroupCount();
        }
    }
}
