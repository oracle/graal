/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.collections.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentSkipListSet;

import org.graalvm.collections.LockFreePool;
import org.junit.Assert;
import org.junit.Test;

public class LockFreePoolTest {

    @Test
    public void addGet() {
        LockFreePool<Integer> pool = new LockFreePool<>();
        Assert.assertNull(pool.get());
        pool.add(17);
        Assert.assertEquals(Integer.valueOf(17), pool.get());

        int total = 256;
        HashSet<Integer> inserted = new HashSet<>();
        for (int i = 0; i < total; i++) {
            inserted.add(i);
            pool.add(i);
        }
        for (int i = 0; i < total; i++) {
            Integer element = pool.get();
            Assert.assertNotNull(element);
            Assert.assertTrue(inserted.contains(element));
            inserted.remove(element);
        }
        Assert.assertTrue(inserted.isEmpty());
    }

    @Test
    public void concurrentAddGet() {
        int threadCount = 4;
        int totalPerThread = 16000;
        LockFreePool<Integer> pool = new LockFreePool<>();
        ConcurrentSkipListSet<Integer> seen = new ConcurrentSkipListSet<>();

        ArrayList<Thread> adderThreads = new ArrayList<>();
        for (int k = 0; k < threadCount; k++) {
            int threadIndex = k;
            adderThreads.add(new Thread() {
                @Override
                public void run() {
                    for (int i = 0; i < totalPerThread; i++) {
                        int element = threadIndex * totalPerThread + i;
                        pool.add(element);
                    }

                    for (int i = 0; i < totalPerThread; i++) {
                        int element = pool.get();
                        seen.add(element);
                    }
                }
            });
        }

        for (int k = 0; k < threadCount; k++) {
            adderThreads.get(k).start();
        }
        for (int k = 0; k < threadCount; k++) {
            try {
                adderThreads.get(k).join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Assert.assertEquals(threadCount * totalPerThread, seen.size());
        for (int i = 0; i < threadCount * totalPerThread; i++) {
            Assert.assertTrue(seen.contains(i));
        }
    }

}
