/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.test;

import org.junit.runner.*;

/**
 * Timing support for JUnit test runs.
 */
public class TimingDecorator extends GraalJUnitRunListenerDecorator {

    private long startTime;
    private long classStartTime;

    public TimingDecorator(GraalJUnitRunListener l) {
        super(l);
    }

    @Override
    public void testClassStarted(Class<?> clazz) {
        classStartTime = System.nanoTime();
        super.testClassStarted(clazz);
    }

    @Override
    public void testClassFinished(Class<?> clazz) {
        long totalTime = System.nanoTime() - classStartTime;
        super.testClassFinished(clazz);
        getWriter().print(' ' + valueToString(totalTime));
    }

    @Override
    public void testStarted(Description description) {
        startTime = System.nanoTime();
        super.testStarted(description);
    }

    @Override
    public void testFinished(Description description) {
        long totalTime = System.nanoTime() - startTime;
        super.testFinished(description);
        getWriter().print(" " + valueToString(totalTime));
    }

    private static String valueToString(long value) {
        return String.format("%d.%d ms", value / 1000000, (value / 100000) % 10);
    }

}
