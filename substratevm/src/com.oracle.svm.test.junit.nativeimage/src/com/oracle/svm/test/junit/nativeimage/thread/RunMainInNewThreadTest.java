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
package com.oracle.svm.test.thread;

import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

/**
 * Verifies Java main thread semantics when application main runs on a launcher-created thread.
 */
@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+RunMainInNewThread",
                "-H:-UnlockExperimentalVMOptions"
})
public class RunMainInNewThreadTest {
    /**
     * Verifies that the unmanaged launcher thread does not become an extra Java thread when main is
     * started on a fresh native thread.
     */
    @Test
    public void testMainThreadIdentityAndVisibility() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());

        Thread mainThread = Thread.currentThread();
        assertMainThread(mainThread);

        ThreadGroup mainGroup = mainThread.getThreadGroup();
        Assert.assertEquals("Unexpected visible non-daemon threads.",
                        List.of(mainThread), visibleNonDaemonThreads());
        Assert.assertEquals("Unexpected direct non-daemon threads in the main group.",
                        List.of(mainThread), directNonDaemonThreadsIn(mainGroup));
    }

    /**
     * Checks the Java-level properties used to recognize the preallocated main thread.
     */
    private static void assertMainThread(Thread thread) {
        Assert.assertEquals("Unexpected main thread name.", "main", thread.getName());
        Assert.assertEquals("Unexpected main thread state.", Thread.State.RUNNABLE, thread.getState());
        Assert.assertFalse("Unexpected main thread daemon status.", thread.isDaemon());
        Assert.assertEquals("Unexpected main thread group.", "main", thread.getThreadGroup().getName());
        Assert.assertEquals("Unexpected parent thread group.", "system", thread.getThreadGroup().getParent().getName());
    }

    /**
     * Lists non-daemon Java threads exposed by stack walking.
     */
    private static List<Thread> visibleNonDaemonThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                        .filter(thread -> !thread.isDaemon())
                        .toList();
    }

    /**
     * Enumerates direct non-daemon Java thread entries in {@code group} without recursing into
     * child groups.
     */
    private static List<Thread> directNonDaemonThreadsIn(ThreadGroup group) {
        Thread[] threads = new Thread[Math.max(4, group.activeCount() + 1)];
        int count = group.enumerate(threads, false);

        /*
         * The active count is only an estimate. Retry with a larger array if the first result may
         * have been truncated.
         */
        while (count == threads.length) {
            threads = new Thread[threads.length * 2];
            count = group.enumerate(threads, false);
        }

        return Arrays.stream(threads, 0, count)
                        .filter(thread -> !thread.isDaemon())
                        .toList();
    }
}
