/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.concurrent.TimeUnit;

public class DynamicThresholdsQueue extends TraversingBlockingQueue {

    private final GraalTruffleRuntime runtime;
    private final int threads;
    private final double minScale;
    private final int normalLoad;

    public DynamicThresholdsQueue(GraalTruffleRuntime runtime, int threads, double minScale, int normalLoad) {
        this.runtime = runtime;
        this.threads = threads;
        this.minScale = minScale;
        this.normalLoad = normalLoad;
    }

    private double load() {
        return (double) entries.size() / threads;
    }

    @Override
    public boolean add(Runnable e) {
        scaleThresholds();
        return super.add(e);
    }

    @Override
    public boolean offer(Runnable e) {
        scaleThresholds();
        return super.offer(e);
    }

    @Override
    public boolean offer(Runnable e, long timeout, TimeUnit unit) throws InterruptedException {
        scaleThresholds();
        return super.offer(e, timeout, unit);
    }

    @Override
    public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
        scaleThresholds();
        return super.poll(timeout, unit);
    }

    @Override
    public Runnable poll() {
        scaleThresholds();
        return super.poll();
    }

    private void scaleThresholds() {
        double slope = (1 - minScale) / normalLoad;
        runtime.setCompilationThresholdScale(slope * load() + minScale);
    }
}
