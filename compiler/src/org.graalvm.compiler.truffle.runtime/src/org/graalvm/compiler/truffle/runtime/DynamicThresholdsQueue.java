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

final class DynamicThresholdsQueue extends TraversingBlockingQueue {

    private final int threads;
    private final double minScale;
    private final int minNormalLoad;
    private final int maxNormalLoad;
    private final double slope;

    DynamicThresholdsQueue(int threads, double minScale, int minNormalLoad, int maxNormalLoad) {
        this.threads = threads;
        this.minScale = minScale;
        this.minNormalLoad = minNormalLoad;
        this.maxNormalLoad = maxNormalLoad;
        this.slope = (1 - minScale) / minNormalLoad;
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
        GraalTruffleRuntime.getRuntime().setCompilationThresholdScale(FixedPointMath.toFixedPoint(scale()));
    }

    /**
     * @return f(x) where x is the load of the queue and f is a function that
     *
     *         - Grows linearly between coordinates (0, minScale) and (minNormalLoad, 1)
     *
     *         - Equals 1 for all x between minNormalLoad and maxNormalLoad (inclusively)
     *
     *         - For all x > maxNormalLoad - grows at the same rate as for x < minNormalLoad, but
     *         starting at coordinate (maxNormalLoad, 1)
     */
    private double scale() {
        double x = load();
        if (minNormalLoad <= x && x <= maxNormalLoad) {
            return 1;
        }
        if (x < minNormalLoad) {
            return slope * x + minScale;
        }
        return slope * x + (1 - slope * maxNormalLoad);
    }
}
