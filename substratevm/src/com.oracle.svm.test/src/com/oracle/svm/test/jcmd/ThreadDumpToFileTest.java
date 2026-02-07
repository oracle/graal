/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jcmd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import jdk.internal.vm.ThreadDumper;

/**
 * This test doesn't actually go through JCMD. Instead, it is meant to verify that
 * Target_jdk_internal_vm_ThreadSnapshot is working correctly.
 */
public class ThreadDumpToFileTest {
    private static final class BlockerHelper {
    }

    @Test
    public void testParkBlockerInfoAppearsInDump() throws Exception {
        BlockerHelper blocker = new BlockerHelper();
        CountDownLatch parked = new CountDownLatch(1);

        Thread parkedThread = new Thread(() -> {
            parked.countDown();
            LockSupport.park(blocker);
        }, "test-park-blocker-thread");

        parkedThread.start();

        // Wait until parkedThread is parked
        parked.await();
        while (!parkedThread.getState().equals(Thread.State.WAITING)) {
            Thread.sleep(10);
        }

        String dump = getThreadDump();
        LockSupport.unpark(parkedThread);
        parkedThread.join();

        assertTrue("Dump should contain the blocked thread's name", dump.contains("test-park-blocker-thread"));
        assertTrue("Dump should contain the thread's state", dump.contains("WAITING"));
        assertTrue("Dump should contain text 'parking to wait for'", dump.contains("parking to wait for"));
        assertTrue("Dump should report the blocker object", dump.contains(blocker.getClass().getName()));
    }

    @Test
    public void testDumpBasic() {
        String dump = getThreadDump();

        assertFalse("Dump is empty", dump.isEmpty());
        assertTrue("Dump should report the process ID", dump.contains(String.valueOf(ProcessHandle.current().pid())));
        assertTrue("Dump should report at least some stacktrace lines", dump.contains("    at "));
    }

    private static String getThreadDump() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ThreadDumper.dumpThreads(baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
