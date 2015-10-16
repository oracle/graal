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
package com.oracle.graal.lir.stackslotalloc;

import jdk.vm.ci.meta.Value;

import com.oracle.graal.lir.VirtualStackSlot;
import com.oracle.graal.lir.debug.IntervalDumper;

class StackIntervalDumper implements IntervalDumper {
    private final StackInterval[] intervals;

    public StackIntervalDumper(StackInterval[] intervals) {
        this.intervals = intervals;
    }

    public void visitIntervals(IntervalVisitor visitor) {
        for (StackInterval interval : intervals) {
            if (interval != null) {
                printInterval(interval, visitor);
            }
        }
    }

    private static void printInterval(StackInterval interval, IntervalVisitor visitor) {
        Value hint = interval.locationHint() != null ? interval.locationHint().getOperand() : null;
        VirtualStackSlot operand = interval.getOperand();
        String type = operand.getLIRKind().getPlatformKind().toString();
        char typeChar = operand.getPlatformKind().getTypeChar();
        visitor.visitIntervalStart(operand, operand, interval.location(), hint, type, typeChar);

        // print ranges
        visitor.visitRange(interval.from(), interval.to());

        // no use positions

        visitor.visitIntervalEnd("NOT_SUPPORTED");
    }

}
