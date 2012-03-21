/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

public final class TimerImpl extends DebugValue implements DebugTimer {

    public static final TimerCloseable VOID_CLOSEABLE = new TimerCloseable() {
        @Override
        public void close() {
        }
    };

    private ThreadLocal<Long> valueToSubstract = new ThreadLocal<>();

    public TimerImpl(String name) {
        super(name);
    }

    @Override
    public TimerCloseable start() {
        if (Debug.isTimeEnabled()) {
            final long startTime = System.nanoTime();
            if (valueToSubstract.get() == null) {
                valueToSubstract.set(0L);
            }
            final long previousValueToSubstract = valueToSubstract.get();
            TimerCloseable result = new TimerCloseable() {
                @Override
                public void close() {
                    long timeSpan = System.nanoTime() - startTime;
                    long oldValueToSubstract = valueToSubstract.get();
                    valueToSubstract.set(timeSpan + previousValueToSubstract);
                    TimerImpl.this.addToCurrentValue(timeSpan - oldValueToSubstract);
                }
            };
            valueToSubstract.set(0L);
            return result;
        } else {
            return VOID_CLOSEABLE;
        }
    }

    @Override
    public String toString(long value) {
        return String.format("%d.%d ms", value / 1000000, (value / 100000) % 10);
    }
}
