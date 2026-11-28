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
package com.oracle.svm.test.continuations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.internal.vm.Continuation;

/**
 * A {@link StackWalker} of a yielded continuation walks a snapshot of its frames. Native Image
 * does not mount the continuation during the walk, so it can resume and yield again in the
 * meantime, with different frames. The walk must then continue on its original snapshot.
 */
public class ContinuationStackWalkerTest {
    static final int DEPTH = 32;

    static volatile int phase;
    static volatile boolean release;

    @NeverInline("Recursion frames are counted by the test.")
    static void deep(int n) {
        if (n > 0) {
            deep(n - 1);
        } else {
            parkUntil(1);
        }
    }

    static void parkUntil(int p) {
        phase = p;
        while (phase == p && !release) {
            LockSupport.park();
        }
    }

    @Test
    public void walkContinuesOnItsSnapshotWhenTheContinuationYieldsAgain() throws Exception {
        phase = 0;
        release = false;
        Thread vthread = Thread.ofVirtual().start(() -> {
            deep(DEPTH); // parks at the bottom of the recursion
            parkUntil(2); // parks again, with a much shallower stack
        });
        awaitParked(vthread, 1);
        Continuation cont = SubstrateUtil.cast(vthread, Target_java_lang_VirtualThread_ContinuationStackWalkerTest.class).cont;

        List<String> walked = new ArrayList<>();
        cont.stackWalker().forEach(frame -> {
            if (walked.isEmpty()) {
                phase = 0;
                LockSupport.unpark(vthread);
                try {
                    awaitParked(vthread, 2); // resumed and yielded again: a new StoredContinuation
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
            walked.add(frame.getMethodName());
        });

        release = true;
        LockSupport.unpark(vthread);
        assertTrue(vthread.join(Duration.ofSeconds(60)));
        assertEquals(walked.toString(), DEPTH + 1, walked.stream().filter("deep"::equals).count());
    }

    static void awaitParked(Thread t, int p) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (phase != p || t.getState() != Thread.State.WAITING) {
            assertTrue("virtual thread did not park: " + t.getState(), System.nanoTime() < deadline);
            Thread.sleep(1);
        }
    }
}

@TargetClass(className = "java.lang.VirtualThread")
final class Target_java_lang_VirtualThread_ContinuationStackWalkerTest {
    @Alias Continuation cont;
}
