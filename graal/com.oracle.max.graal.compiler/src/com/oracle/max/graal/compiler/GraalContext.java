/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.observer.*;

/**
 * This class is intended for non-essential stuff like statistics, observing, etc. It should not be used for anything
 * that has a direct influence on the result of a compilation!
 */
public class GraalContext {

    public static final GraalContext EMPTY_CONTEXT = new GraalContext("silent context");

    public final ObservableContext observable = new ObservableContext();
    public final GraalTimers timers = new GraalTimers();
    public final GraalMetrics metrics = new GraalMetrics();

    private final String name;

    public GraalContext(String name) {
        this.name = name;
    }

    public boolean isObserved() {
        return observable.isObserved();
    }

    public void addCompilationObserver(CompilationObserver observer) {
        observable.addCompilationObserver(observer);
    }

    public void print() {
        if (GraalOptions.Meter || GraalOptions.Time) {
            for (int i = 0; i < 22 + name.length(); i++) {
                TTY.print('=');
            }
            TTY.println("\n========== " + name + " ==========");
            if (GraalOptions.Meter) {
                metrics.print();
            }
            if (GraalOptions.Time) {
                timers.print();
            }
        }
    }
}
