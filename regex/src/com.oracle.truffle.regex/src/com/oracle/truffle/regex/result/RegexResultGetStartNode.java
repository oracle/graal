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
package com.oracle.truffle.regex.result;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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
                    @Cached("createBinaryProfile()") ConditionProfile boundsProfile) {
        if (boundsProfile.profile(groupNumber == 0)) {
            return receiver.getStart();
        } else {
            return INVALID_RESULT;
        }
    }

    @Specialization
    static int doSingleResultLazyStart(SingleResultLazyStart receiver, int groupNumber,
                    @Cached DispatchNode calcResult,
                    @Cached("createBinaryProfile()") ConditionProfile boundsProfile,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile calcLazyProfile) {
        if (boundsProfile.profile(groupNumber == 0)) {
            if (calcLazyProfile.profile(receiver.getStart() == -1)) {
                receiver.setStart((int) calcResult.execute(receiver.getFindStartCallTarget(),
                                new Object[]{receiver.getInput(), receiver.getEnd() - 1, receiver.getFromIndex()}) + 1);
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
        return fromSingleArray(getResultNode.execute(receiver), groupNumber) - 1;
    }

    private static int fromSingleArray(int[] array, int groupNumber) {
        try {
            return array[groupNumber * 2];
        } catch (ArrayIndexOutOfBoundsException e) {
            return INVALID_RESULT;
        }
    }

}
