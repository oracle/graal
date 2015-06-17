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
package com.oracle.truffle.api.object;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class DebugCounter {
    private static final ArrayList<DebugCounter> allCounters = new ArrayList<>();

    private final String name;
    private final AtomicLong value;

    private DebugCounter(String name) {
        this.name = name;
        this.value = new AtomicLong();
        allCounters.add(this);
    }

    public static DebugCounter create(String name) {
        return new DebugCounter(name);
    }

    public long get() {
        return value.get();
    }

    public void inc() {
        value.incrementAndGet();
    }

    @Override
    public String toString() {
        return name + ": " + value;
    }

    public static void dumpCounters() {
        dumpCounters(System.out);
    }

    public static void dumpCounters(PrintStream out) {
        for (DebugCounter counter : allCounters) {
            out.println(counter);
        }
    }
}
