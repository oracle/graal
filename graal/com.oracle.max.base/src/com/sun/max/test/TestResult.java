/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
/**
 *
 */
package com.sun.max.test;

/**
 * The {@code TestResult} class represents the result of running a test.
 * The result is computed by the harness associated with the test.
 */
public abstract class TestResult {

    public static final Success SUCCESS = new Success();
    public static final Failure FAILURE = new Failure();

    public abstract boolean isSuccess();

    public static class Success extends TestResult {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    public static class Failure extends TestResult {
        @Override
        public boolean isSuccess() {
            return false;
        }
        @Override
        public String failureMessage(TestCase testCase) {
            return testCase.file + " failed";
        }
    }

    public String failureMessage(TestCase tc) {
        return "";
    }

    public static class UnexpectedException extends Failure {
        public final String message;
        public final Throwable thrown;
        public UnexpectedException(Throwable thrown) {
            this.message = "Unexpected exception";
            this.thrown = thrown;
        }
        public UnexpectedException(String message, Throwable thrown) {
            this.message = message;
            this.thrown = thrown;
        }

        @Override
        public String failureMessage(TestCase testCase) {
            return "unexpected exception: " + thrown.toString();
        }
    }
}
