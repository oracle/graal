/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.test.GCUtils;

public abstract class EnginesGCedTest {

    private GCCheck gcCheck;

    @Before
    public void setUp() {
        gcCheck = new GCCheck();
    }

    @After
    public void tearDown() {
        gcCheck.checkCollected();
    }

    protected final void addEngineReference(Engine engine) {
        gcCheck.addEngineReference(engine);
    }

    static final class GCCheck {

        private final Set<WeakReference<Engine>> engineRefs = new HashSet<>();
        private final Set<Long> threadIDs = new HashSet<>();

        GCCheck() {
            Thread[] threads = findAllThreads();
            for (Thread t : threads) {
                if (t != null) {
                    threadIDs.add(t.getId());
                }
            }
        }

        void checkCollected() {
            for (WeakReference<Engine> engineRef : engineRefs) {
                GCUtils.assertGc("Engine not collected", engineRef);
            }
            Thread[] threads = findAllThreads();
            for (Thread t : threads) {
                if (t != null) {
                    if (!threadIDs.contains(t.getId())) {
                        if (t.getClass().getPackage().getName().startsWith("org.graalvm.compiler")) {
                            // A compiler thread
                            continue;
                        }
                        Assert.fail("An extra thread " + t + " is found after test finished.");
                    }
                }
            }
            threadIDs.clear();
        }

        void addEngineReference(Engine engine) {
            engineRefs.add(new WeakReference<>(engine));
        }

        private static Thread[] findAllThreads() {
            ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
            // Use just the current thread group, there might be new threads in the system group.
            int activeThreadCount = threadGroup.activeCount();
            Thread[] threads = new Thread[activeThreadCount + 10];
            threadGroup.enumerate(threads);
            return threads;
        }

    }
}
