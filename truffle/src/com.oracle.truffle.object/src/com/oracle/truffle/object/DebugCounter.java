/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** @since 0.17 or earlier */
public abstract class DebugCounter {
    private DebugCounter() {
    }

    /** @since 0.17 or earlier */
    public abstract long get();

    /** @since 0.17 or earlier */
    public abstract void inc();

    /** @since 0.17 or earlier */
    public static DebugCounter create(String name) {
        return ObjectStorageOptions.DebugCounters ? DebugCounterImpl.createImpl(name) : Dummy.INSTANCE;
    }

    /** @since 0.17 or earlier */
    public static void dumpCounters() {
        if (ObjectStorageOptions.DebugCounters) {
            DebugCounterImpl.dumpCounters(System.out);
        }
    }

    private static final class DebugCounterImpl extends DebugCounter {
        private static final ArrayList<DebugCounter> allCounters = new ArrayList<>();

        private final String name;
        private final AtomicLong value;

        private DebugCounterImpl(String name) {
            this.name = name;
            this.value = new AtomicLong();
            allCounters.add(this);
        }

        private static DebugCounter createImpl(String name) {
            return new DebugCounterImpl(name);
        }

        @Override
        public long get() {
            return value.get();
        }

        @Override
        public void inc() {
            value.incrementAndGet();
        }

        @Override
        public String toString() {
            return name + ": " + get();
        }

        private static void dumpCounters(PrintStream out) {
            for (DebugCounter counter : allCounters) {
                out.println(counter);
            }
        }

        static {
            assert ObjectStorageOptions.DebugCounters;
            if (ObjectStorageOptions.DumpDebugCounters) {
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    public void run() {
                        dumpCounters(System.out);
                    }
                }));
            }
        }
    }

    private static final class Dummy extends DebugCounter {
        static final DebugCounter INSTANCE = new Dummy();

        private Dummy() {
        }

        @Override
        public long get() {
            return 0;
        }

        @Override
        public void inc() {
        }
    }
}
