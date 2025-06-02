/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import static jdk.graal.compiler.debug.DebugCloseable.VOID_CLOSEABLE;

import java.util.concurrent.TimeUnit;

import org.graalvm.collections.Pair;

abstract class BaseTimerKey extends AccumulatedKey implements TimerKey {
    static final class FlatTimer extends AbstractKey implements TimerKey {
        private BaseTimerKey accm;

        FlatTimer(String nameFormat, Object nameArg1, Object nameArg2) {
            super(nameFormat, nameArg1, nameArg2);
        }

        @Override
        protected String createName(String format, Object arg1, Object arg2) {
            return super.createName(format, arg1, arg2) + FLAT_KEY_SUFFIX;
        }

        @Override
        public TimeUnit getTimeUnit() {
            return accm.getTimeUnit();
        }

        @Override
        public void add(DebugContext debug, long value, TimeUnit units) {
            accm.add(debug, value, units);
        }

        @Override
        public boolean isEnabled(DebugContext debug) {
            return accm.isEnabled(debug);
        }

        @Override
        public DebugCloseable start(DebugContext debug) {
            return accm.start(debug);
        }

        @Override
        public TimerKey doc(String doc) {
            throw new IllegalArgumentException("Cannot set documentation for derived key " + getName());
        }

        @Override
        public String toHumanReadableFormat(long value) {
            return accm.toHumanReadableFormat(value);
        }

        @Override
        public Pair<String, String> toCSVFormat(long value) {
            return accm.toCSVFormat(value);
        }

        @Override
        public String getDocName() {
            return null;
        }
    }

    BaseTimerKey(String nameFormat, Object nameArg1, Object nameArg2) {
        super(new FlatTimer(nameFormat, nameArg1, nameArg2), nameFormat, nameArg1, nameArg2);
        ((FlatTimer) flat).accm = this;
    }

    @Override
    public DebugCloseable start(DebugContext debug) {
        if (debug.isTimerEnabled(this)) {
            Timer result = createTimerInstance(debug);
            debug.currentTimer = result;
            return result;
        } else {
            return VOID_CLOSEABLE;
        }
    }

    @Override
    public void add(DebugContext debug, long value, TimeUnit units) {
        if (debug.isTimerEnabled(this)) {
            addToCurrentValue(debug, getTimeUnit().convert(value, units));
        }
    }

    @Override
    public boolean isEnabled(DebugContext debug) {
        return debug.isTimerEnabled(this);
    }

    @Override
    public TimerKey getFlat() {
        return (FlatTimer) flat;
    }

    abstract static class Timer extends CloseableCounter implements DebugCloseable {
        final DebugContext debug;

        Timer(AccumulatedKey counter, DebugContext debug) {
            super(debug, debug.currentTimer, counter);
            this.debug = debug;
        }

        @Override
        public void close() {
            super.close();
            debug.currentTimer = parent;
        }
    }

    protected abstract Timer createTimerInstance(DebugContext debug);

    @Override
    public TimerKey doc(String doc) {
        setDoc(doc);
        return this;
    }
}
