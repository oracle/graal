/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.alloc.trace;

import static jdk.internal.jvmci.code.ValueUtil.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.lir.debug.*;

final class TraceIntervalDumper implements IntervalDumper {
    private final FixedInterval[] fixedIntervals;
    private final TraceInterval[] intervals;

    public TraceIntervalDumper(FixedInterval[] fixedIntervals, TraceInterval[] intervals) {
        this.fixedIntervals = fixedIntervals;
        this.intervals = intervals;
    }

    public void visitIntervals(IntervalVisitor visitor) {
        for (FixedInterval interval : fixedIntervals) {
            if (interval != null) {
                printFixedInterval(interval, visitor);
            }
        }
        for (TraceInterval interval : intervals) {
            if (interval != null) {
                printInterval(interval, visitor);
            }
        }
    }

    private static void printFixedInterval(FixedInterval interval, IntervalVisitor visitor) {
        Value hint = null;
        AllocatableValue operand = interval.operand;
        String type = "fixed";
        char typeChar = operand.getPlatformKind().getTypeChar();
        visitor.visitIntervalStart(operand, operand, operand, hint, type, typeChar);

        // print ranges
        for (FixedRange range = interval.first(); range != FixedRange.EndMarker; range = range.next) {
            visitor.visitRange(range.from, range.to);
        }

        // no use positions

        visitor.visitIntervalEnd("NOT_SUPPORTED");

    }

    private static void printInterval(TraceInterval interval, IntervalVisitor visitor) {
        Value hint = interval.locationHint(false) != null ? interval.locationHint(false).location() : null;
        AllocatableValue operand = interval.operand;
        String type = isRegister(operand) ? "fixed" : operand.getLIRKind().getPlatformKind().toString();
        char typeChar = operand.getPlatformKind().getTypeChar();
        visitor.visitIntervalStart(interval.splitParent().operand, operand, interval.location(), hint, type, typeChar);

        // print ranges
        visitor.visitRange(interval.from(), interval.to());

        // print use positions
        int prev = -1;
        UsePosList usePosList = interval.usePosList();
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            assert prev < usePosList.usePos(i) : "use positions not sorted";
            visitor.visitUsePos(usePosList.usePos(i), usePosList.registerPriority(i));
            prev = usePosList.usePos(i);
        }

        visitor.visitIntervalEnd(interval.spillState());
    }

}
