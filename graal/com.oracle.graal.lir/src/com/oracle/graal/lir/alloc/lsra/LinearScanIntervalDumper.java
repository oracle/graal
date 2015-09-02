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
package com.oracle.graal.lir.alloc.lsra;

import static jdk.internal.jvmci.code.ValueUtil.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.lir.alloc.lsra.Interval.UsePosList;
import com.oracle.graal.lir.debug.*;

class LinearScanIntervalDumper implements IntervalDumper {
    private final Interval[] intervals;

    public LinearScanIntervalDumper(Interval[] intervals) {
        this.intervals = intervals;
    }

    public void visitIntervals(IntervalVisitor visitor) {
        for (Interval interval : intervals) {
            if (interval != null) {
                printInterval(interval, visitor);
            }
        }
    }

    private static void printInterval(Interval interval, IntervalVisitor visitor) {
        Value hint = interval.locationHint(false) != null ? interval.locationHint(false).operand : null;
        AllocatableValue operand = interval.operand;
        String type = isRegister(operand) ? "fixed" : operand.getLIRKind().getPlatformKind().toString();
        char typeChar = operand.getKind().getTypeChar();
        visitor.visitIntervalStart(interval.splitParent().operand, operand, interval.location(), hint, type, typeChar);

        // print ranges
        Range cur = interval.first();
        while (cur != Range.EndMarker) {
            visitor.visitRange(cur.from, cur.to);
            cur = cur.next;
            assert cur != null : "range list not closed with range sentinel";
        }

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
