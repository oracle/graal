/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.SynchronizationTestFactory.NotifyNodeFactory;
import com.oracle.truffle.api.dsl.test.SynchronizationTestFactory.WaitNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;

public class SynchronizationTest {

    @TypeSystemReference(ExampleTypes.class)
    @NodeChildren({@NodeChild("monitor"), @NodeChild("latch")})
    abstract static class WaitNode extends ValueNode {

        public abstract Object executeEvaluated(Object monitor, CountDownLatch latch);

        @SuppressWarnings("deprecation")
        @Specialization
        Object doWait(Object monitor, CountDownLatch latch) {
            Assert.assertEquals(false, Thread.holdsLock(getAtomicLock()));
            final boolean holdsLock = ((ReentrantLock) getLock()).isHeldByCurrentThread();
            Assert.assertEquals(false, holdsLock);

            synchronized (monitor) {
                latch.countDown();
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    throw new Error(e);
                }
            }
            return monitor;
        }

    }

    @TypeSystemReference(ExampleTypes.class)
    @NodeChildren({@NodeChild("monitor")})
    abstract static class NotifyNode extends ValueNode {

        public abstract Object executeEvaluated(Object monitor);

        @SuppressWarnings("deprecation")
        @Specialization
        Object doNotify(Object monitor) {
            Assert.assertEquals(false, Thread.holdsLock(getAtomicLock()));
            final boolean holdsLock = ((ReentrantLock) getLock()).isHeldByCurrentThread();
            Assert.assertEquals(false, holdsLock);

            synchronized (monitor) {
                monitor.notify();
            }
            return monitor;
        }

    }

    static class IfNode extends ValueNode {

        @Child WaitNode waitNode = WaitNodeFactory.create(null, null);
        @Child NotifyNode notifyNode = NotifyNodeFactory.create(null);

    }

    @Test
    public void testFirstExecutionDoesNotHoldLock() throws InterruptedException {
        final Object monitor = new Object();
        final CountDownLatch latch = new CountDownLatch(1);

        final IfNode ifNode = new IfNode();

        // We need a root node to get its lock
        @SuppressWarnings("unused")
        final CallTarget callTarget = TestHelper.createCallTarget(ifNode);

        Thread waitThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ifNode.waitNode.executeEvaluated(monitor, latch);
            }
        });
        waitThread.start();

        Thread notifyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ifNode.notifyNode.executeEvaluated(monitor);
            }
        });

        latch.await();
        notifyThread.start();

        waitThread.join();
        notifyThread.join();
    }
}
