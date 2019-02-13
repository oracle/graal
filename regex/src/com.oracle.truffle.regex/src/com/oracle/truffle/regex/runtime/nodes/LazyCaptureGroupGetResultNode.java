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
package com.oracle.truffle.regex.runtime.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.result.LazyCaptureGroupsResult;

public abstract class LazyCaptureGroupGetResultNode extends Node {

    public abstract int[] execute(LazyCaptureGroupsResult receiver);

    @Specialization(guards = {"receiver.getResult() == null", "receiver.getFindStartCallTarget() == null"})
    int[] doLazyCaptureGroupsCalc(LazyCaptureGroupsResult receiver,
                    @Cached("create()") CalcResultNode calcResult) {
        calcResult.execute(receiver.getCaptureGroupCallTarget(), receiver.createArgsCGNoFindStart());
        return receiver.getResult();
    }

    @Specialization(guards = {"receiver.getResult() == null", "receiver.getFindStartCallTarget() != null"})
    int[] doLazyCaptureGroupsCalcWithFindStart(LazyCaptureGroupsResult receiver,
                    @Cached("create()") CalcResultNode calcStart,
                    @Cached("create()") CalcResultNode calcResult) {
        final int start = (int) calcStart.execute(receiver.getFindStartCallTarget(), receiver.createArgsFindStart());
        calcResult.execute(receiver.getCaptureGroupCallTarget(), receiver.createArgsCG(start));
        return receiver.getResult();
    }

    @Specialization(guards = {"receiver.getResult() != null"})
    int[] doLazyCaptureGroups(LazyCaptureGroupsResult receiver) {
        return receiver.getResult();
    }

    public static LazyCaptureGroupGetResultNode create() {
        return LazyCaptureGroupGetResultNodeGen.create();
    }
}
