/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.object;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** @since 0.17 or earlier */
@SuppressWarnings("deprecation")
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
