/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug.internal;

import com.oracle.graal.debug.*;

/**
 * A helper class for DebugValues that can nest and need to split out accumulated and flat values
 * for some kind of counter-like measurement.
 */
abstract class CloseableCounterImpl implements DebugCloseable {

    protected final CloseableCounterImpl parent;
    protected final AccumulatedDebugValue counter;
    protected final long start;
    protected long nestedAmountToSubtract;

    CloseableCounterImpl(CloseableCounterImpl parent, AccumulatedDebugValue counter) {
        this.parent = parent;
        this.start = getCounterValue();
        this.counter = counter;
    }

    @Override
    public void close() {
        long end = getCounterValue();
        long difference = end - start;
        if (parent != null) {
            if (!counter.getName().equals(parent.counter.getName())) {
                parent.nestedAmountToSubtract += difference;

                // Look for our counter in an outer scope and fix up
                // the adjustment to the flat count
                CloseableCounterImpl ancestor = parent.parent;
                while (ancestor != null) {
                    if (ancestor.counter.getName().equals(counter.getName())) {
                        ancestor.nestedAmountToSubtract -= difference;
                        break;
                    }
                    ancestor = ancestor.parent;
                }
            }
        }
        long flatAmount = difference - nestedAmountToSubtract;
        counter.addToCurrentValue(difference);
        counter.flat.addToCurrentValue(flatAmount);
    }

    abstract long getCounterValue();
}
