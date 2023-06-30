/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * See https://github.com/oracle/graal/blob/master/truffle/docs/TraversingCompilationQueue.md .
 */
final class DynamicThresholdsQueue extends TraversingBlockingQueue {

    private final int threads;
    private final double minScale;
    private final int minNormalLoad;
    private final int maxNormalLoad;
    private final double slope;

    DynamicThresholdsQueue(int threads, double minScale, int minNormalLoad, int maxNormalLoad, BlockingQueue<Runnable> entries) {
        super(entries);
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
        OptimizedTruffleRuntime.getRuntime().setCompilationThresholdScale(FixedPointMath.toFixedPoint(scale()));
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
