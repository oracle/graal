/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.result;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.regex.runtime.nodes.DispatchNode;
import com.oracle.truffle.regex.runtime.nodes.LazyCaptureGroupGetResultNode;
import com.oracle.truffle.regex.runtime.nodes.TraceFinderGetResultNode;

@ReportPolymorphism
@GenerateUncached
abstract class RegexResultGetStartNode extends Node {

    private static final int INVALID_RESULT = -1;

    abstract int execute(Object receiver, int groupNumber);

    @Specialization
    static int doNoMatch(@SuppressWarnings("unused") NoMatchResult receiver, @SuppressWarnings("unused") int groupNumber) {
        return INVALID_RESULT;
    }

    @Specialization
    static int doSingleResult(SingleResult receiver, int groupNumber,
                    @Cached ConditionProfile boundsProfile) {
        if (boundsProfile.profile(groupNumber == 0)) {
            return receiver.getStart();
        } else {
            return INVALID_RESULT;
        }
    }

    @Specialization
    static int doSingleResultLazyStart(SingleResultLazyStart receiver, int groupNumber,
                    @Cached DispatchNode calcResult,
                    @Cached ConditionProfile boundsProfile,
                    @Exclusive @Cached ConditionProfile calcLazyProfile) {
        if (boundsProfile.profile(groupNumber == 0)) {
            if (calcLazyProfile.profile(!receiver.isStartCalculated())) {
                receiver.setStart((int) calcResult.execute(receiver.getFindStartCallTarget(), receiver.createArgsFindStart()));
            }
            return receiver.getStart();
        } else {
            return INVALID_RESULT;
        }
    }

    @Specialization
    static int doSingleIndexArray(SingleIndexArrayResult receiver, int groupNumber) {
        return fromSingleArray(receiver.getIndices(), groupNumber);
    }

    @Specialization
    static int doTraceFinder(TraceFinderResult receiver, int groupNumber,
                    @Cached TraceFinderGetResultNode getResultNode) {
        return fromSingleArray(getResultNode.execute(receiver), groupNumber);
    }

    @Specialization
    static int doLazyCaptureGroups(LazyCaptureGroupsResult receiver, int groupNumber,
                    @Cached LazyCaptureGroupGetResultNode getResultNode) {
        return fromSingleArray(getResultNode.execute(receiver), groupNumber);
    }

    private static int fromSingleArray(int[] array, int groupNumber) {
        try {
            return array[groupNumber * 2];
        } catch (ArrayIndexOutOfBoundsException e) {
            return INVALID_RESULT;
        }
    }

}
