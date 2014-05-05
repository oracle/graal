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

import java.io.*;

import org.junit.internal.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;

public class GraalTextListener implements GraalJUnitRunListener {

    private final PrintStream fWriter;

    public GraalTextListener(JUnitSystem system) {
        this(system.out());
    }

    public GraalTextListener(PrintStream writer) {
        fWriter = writer;
    }

    @Override
    public PrintStream getWriter() {
        return fWriter;
    }

    @Override
    public void testRunStarted(Description description) {
    }

    @Override
    public void testRunFinished(Result result) {
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
    }

    @Override
    public void testClassStarted(Class<?> clazz) {
    }

    @Override
    public void testClassFinished(Class<?> clazz) {
    }

    @Override
    public void testStarted(Description description) {
        getWriter().print('.');
    }

    @Override
    public void testFinished(Description description) {
    }

    @Override
    public void testFailed(Failure failure) {
        getWriter().print('E');
    }

    @Override
    public void testSucceeded(Description description) {
    }

    @Override
    public void testIgnored(Description description) {
        getWriter().print('I');
    }

    @Override
    public void testClassFinishedDelimiter() {
    }

    @Override
    public void testClassStartedDelimiter() {
    }

    @Override
    public void testStartedDelimiter() {
    }

    @Override
    public void testFinishedDelimiter() {
    }

    public static RunListener createRunListener(GraalJUnitRunListener l) {
        return new TextListener(l.getWriter()) {
            private Class<?> lastClass;
            private boolean failed;

            @Override
            public final void testStarted(Description description) {
                Class<?> currentClass = description.getTestClass();
                if (currentClass != lastClass) {
                    if (lastClass != null) {
                        l.testClassFinished(lastClass);
                        l.testClassFinishedDelimiter();
                    }
                    lastClass = currentClass;
                    l.testClassStarted(currentClass);
                    l.testClassStartedDelimiter();
                }
                failed = false;
                l.testStarted(description);
                l.testStartedDelimiter();
            }

            @Override
            public final void testFailure(Failure failure) {
                failed = true;
                l.testFailed(failure);
            }

            @Override
            public final void testFinished(Description description) {
                // we have to do this because there is no callback for successful tests
                if (!failed) {
                    l.testSucceeded(description);
                }
                l.testFinished(description);
                l.testFinishedDelimiter();
            }

            @Override
            public void testIgnored(Description description) {
                l.testStarted(description);
                l.testStartedDelimiter();
                l.testIgnored(description);
                l.testFinished(description);
                l.testFinishedDelimiter();
            }

            @Override
            public void testRunStarted(Description description) {
                l.testRunStarted(description);
            }

            @Override
            public void testRunFinished(Result result) {
                if (lastClass != null) {
                    l.testClassFinished(lastClass);
                }
                l.testRunFinished(result);
                super.testRunFinished(result);
            }

            @Override
            public void testAssumptionFailure(Failure failure) {
                l.testAssumptionFailure(failure);
            }

        };
    }

}
