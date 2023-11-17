/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.debug;

import jdk.vm.ci.meta.Value;

/**
 * Provides abstract access to intervals for dumping.
 */
public interface IntervalDumper {

    interface IntervalVisitor {
        void visitIntervalStart(Value parentOperand, Value splitOperand, Value location, Value hint, String typeName);

        void visitRange(int from, int to);

        void visitUsePos(int pos, Object registerPrioObject);

        void visitIntervalEnd(Object spillState);

    }

    /**
     * Visits the {@link IntervalVisitor} for every interval.
     *
     * The order is as follows:
     * <ul>
     * <li>Call {@link IntervalVisitor#visitIntervalStart}</li>
     * <li>For every range:
     * <ul>
     * <li>Call {@link IntervalVisitor#visitRange}</li>
     * </ul>
     * <li>For every use position:
     * <ul>
     * <li>Call {@link IntervalVisitor#visitUsePos}</li>
     * </ul>
     * </li>
     * <li>call {@link IntervalVisitor#visitIntervalEnd}</li>
     * </ul>
     */
    void visitIntervals(IntervalVisitor visitor);

}
