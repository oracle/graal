/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import java.io.PrintStream;
import org.junit.internal.JUnitSystem;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;

class TruffleVerboseTextListener extends TruffleTextListener {

    TruffleVerboseTextListener(JUnitSystem system) {
        this(system.out());
    }

    TruffleVerboseTextListener(PrintStream writer) {
        super(writer);
    }

    @Override
    public void testClassStarted(Class<?> clazz) {
        getWriter().print(clazz.getName() + " started");
    }

    @Override
    public void testClassFinished(Class<?> clazz) {
        getWriter().print(clazz.getName() + " finished");
    }

    @Override
    public void testStarted(Description description) {
        getWriter().print("  " + description.getMethodName() + ": ");
    }

    @Override
    public void testIgnored(Description description) {
        getWriter().print("Ignored");
    }

    @Override
    public void testSucceeded(Description description) {
        getWriter().print("Passed");
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        getWriter().printf("(%s) ", failure.getMessage());
    }

    @Override
    public void testFailed(Failure failure) {
        getWriter().print("FAILED");
        lastFailure = failure;
    }

    @Override
    public void testClassFinishedDelimiter() {
        getWriter().println();
    }

    @Override
    public void testClassStartedDelimiter() {
        getWriter().println();
    }

    @Override
    public void testFinishedDelimiter() {
        getWriter().println();
    }

}
