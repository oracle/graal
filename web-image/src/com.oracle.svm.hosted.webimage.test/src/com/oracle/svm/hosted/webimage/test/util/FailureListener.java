/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.test.util;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

/**
 * JUnit {@link RunListener} that stops JUnit once the given threshold of failing tests is reached.
 */
public class FailureListener extends RunListener {

    private final RunNotifier runNotifier;
    private final int maxFailures;

    /**
     * How many tests failed so far.
     */
    private int numFailures = 0;

    /**
     * @param maxFailures After how many failures should the runner stop. No limit if 0 is passed.
     */
    public FailureListener(RunNotifier runNotifier, int maxFailures) {
        this.runNotifier = runNotifier;
        this.maxFailures = maxFailures;
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        numFailures++;

        if (maxFailures > 0 && numFailures >= maxFailures) {
            runNotifier.pleaseStop();
        }
    }
}
