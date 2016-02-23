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
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

class TruffleJUnitRunListenerDecorator implements TruffleJUnitRunListener {

    private final TruffleJUnitRunListener l;

    TruffleJUnitRunListenerDecorator(TruffleJUnitRunListener l) {
        this.l = l;
    }

    @Override
    public void testRunStarted(Description description) {
        l.testRunStarted(description);
    }

    @Override
    public void testRunFinished(Result result) {
        l.testRunFinished(result);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        l.testAssumptionFailure(failure);
    }

    @Override
    public void testIgnored(Description description) {
        l.testIgnored(description);
    }

    @Override
    public void testClassStarted(Class<?> clazz) {
        l.testClassStarted(clazz);
    }

    @Override
    public void testClassFinished(Class<?> clazz) {
        l.testClassFinished(clazz);
    }

    @Override
    public void testStarted(Description description) {
        l.testStarted(description);
    }

    @Override
    public void testFinished(Description description) {
        l.testFinished(description);
    }

    @Override
    public void testFailed(Failure failure) {
        l.testFailed(failure);
    }

    @Override
    public void testSucceeded(Description description) {
        l.testSucceeded(description);
    }

    @Override
    public PrintStream getWriter() {
        return l.getWriter();
    }

    public void testClassFinishedDelimiter() {
        l.testClassFinishedDelimiter();
    }

    public void testClassStartedDelimiter() {
        l.testClassStartedDelimiter();
    }

    public void testStartedDelimiter() {
        l.testStartedDelimiter();
    }

    public void testFinishedDelimiter() {
        l.testFinishedDelimiter();
    }

}
