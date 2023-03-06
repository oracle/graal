/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Timer implements AutoCloseable {

    private static final Timer DISABLED = new Timer(null, null, null);

    private final String category;
    private final Object key;
    private final long startTime;
    private long endTime;
    private final Timer parent;
    private List<Timer> children;

    private Timer(Timer parent, String category, Object key) {
        this.key = key;
        this.category = category;
        this.parent = parent;
        this.startTime = key != null ? getTimeNS() : 0L;
    }

    long elapsedNS() {
        return endTime - startTime;
    }

    @Override
    public void close() {
        if (key == null) {
            return;
        }
        endTime = getTimeNS();
        if (parent != null) {
            if (parent.children == null) {
                parent.children = new ArrayList<>();
            }
            parent.children.add(this);
            ProcessorContext.getInstance().setCurrentTimer(parent);
        }
    }

    public void printSummary(PrintStream out, String indent) {
        if (key == null) {
            // disabled
            return;
        }
        printTime(out, indent, category, 1, elapsedNS());
        printCategories(out, Arrays.asList(this), indent + "  ");
    }

    private static void printCategories(PrintStream out, List<Timer> all, String indent) {
        Map<String, List<Timer>> categories = new HashMap<>();
        for (Timer current : all) {
            if (current.children != null) {
                for (Timer timer : current.children) {
                    categories.computeIfAbsent(timer.category, (e) -> new ArrayList<>()).add(timer);
                }
            }
        }
        for (var entry : categories.entrySet()) {
            String key = entry.getKey();
            List<Timer> group = entry.getValue();
            Set<Object> element = new HashSet<>();
            long elapsedNS = 0;
            for (Timer timer : group) {
                elapsedNS += timer.elapsedNS();
                element.add(timer.key);
            }
            printTime(out, indent, key, element.size(), elapsedNS);
            printCategories(out, group, indent + "  ");
        }

    }

    private static void printTime(PrintStream out, String indent, String key, int count, long elapsedNS) {
        out.printf(String.format("%s %-15s %10.2fms (count %s)%n", indent, key, elapsedNS / 1_000_000d, count));
    }

    public static Timer create(String category, Object key) {
        ProcessorContext context = ProcessorContext.getInstance();
        if (!context.timingsEnabled()) {
            return DISABLED;
        }
        Timer timer = new Timer(context.getCurrentTimer(), category, key);
        context.setCurrentTimer(timer);
        return timer;
    }

    private static final java.lang.management.ThreadMXBean threadMXBean;
    static {
        java.lang.management.ThreadMXBean bean = null;
        try {
            bean = ManagementFactory.getThreadMXBean();
        } catch (NoClassDefFoundError e) {
        }
        threadMXBean = bean;
    }

    private static long getTimeNS() {
        return threadMXBean != null && threadMXBean.isThreadCpuTimeSupported() ? threadMXBean.getCurrentThreadCpuTime() : System.nanoTime();
    }

}
