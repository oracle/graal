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

import java.io.*;
import java.util.*;

/**
 * The {@code TestCase} class represents a basic test case in the testing framework.
 * @author "Ben L. Titzer"
 */
public abstract class TestCase {

    public final File file;
    public final Properties props;
    public final TestHarness harness;
    public int testNumber;
    public long startTime;
    public long endTime;
    public Throwable thrown;
    public TestResult result;

    protected TestCase(TestHarness harness, File file, Properties props) {
        this.harness = harness;
        this.file = file;
        this.props = props;
    }

    public void test() {
        try {
            startTime = System.currentTimeMillis();
            run();
        } catch (Throwable t) {
            thrown = t;
        } finally {
            endTime = System.currentTimeMillis();
        }
    }

    public abstract void run() throws Throwable;
}
