/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import org.junit.Assert;
import org.junit.Test;

public class DeadlockWatchdogTest {

    @Test
    public void recordActivityCoalescesUntilConsumed() {
        DeadlockWatchdog watchdog = new DeadlockWatchdog(1, false, false);
        try {
            watchdog.recordActivity();
            watchdog.recordActivity();

            Assert.assertTrue(watchdog.consumeActivity());
            Assert.assertFalse(watchdog.consumeActivity());
        } finally {
            watchdog.close();
        }
    }

    @Test
    public void disabledWatchdogIgnoresActivity() {
        DeadlockWatchdog watchdog = new DeadlockWatchdog(0, false);

        watchdog.recordActivity();

        Assert.assertFalse(watchdog.consumeActivity());
    }

}
