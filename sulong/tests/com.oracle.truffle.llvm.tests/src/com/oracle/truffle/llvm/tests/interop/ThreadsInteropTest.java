/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import com.oracle.truffle.llvm.tests.interop.values.StructObject;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class ThreadsInteropTest extends InteropTestBase {

    static final int THREAD_COUNT = 3;

    private static void assertDifferent(Value[] threadIds) {
        for (int i = 1; i < threadIds.length; i++) {
            Value a = threadIds[i];
            for (int j = 0; j < i; j++) {
                Value b = threadIds[j];
                Assert.assertFalse("all thread ids must be different", a.equals(b));
            }
        }
    }

    static Value cpart;

    @BeforeClass
    public static void parseTestFile() throws IOException {
        Source cpartSource = Source.newBuilder("llvm", InteropTestBase.getTestBitcodeFile("pthread_test.c")).build();
        cpart = runWithPolyglot.getPolyglotContext().eval(cpartSource);
    }

    @Test
    public void testExternalThreads() {
        Value[] ret = new Value[THREAD_COUNT];
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIdx = i;
            threads[threadIdx] = new Thread(() -> {
                ret[threadIdx] = cpart.invokeMember("get_self", threadIdx);
            });
            threads[threadIdx].start();
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ex) {
            }
        }
        assertDifferent(ret);

        Value different = cpart.invokeMember("check_different");
        Assert.assertEquals("different", 1, different.asInt());
    }

    @Test
    public void testThreadLocalGlobalContainer() {
        Value[] ret = new Value[THREAD_COUNT];
        Thread[] threads = new Thread[THREAD_COUNT];
        StructObject[] structs = new StructObject[THREAD_COUNT];
        CountDownLatch signal = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            StructObject object = new StructObject(new HashMap<>());
            structs[i] = object;
            int finalI = i;
            threads[i] = new Thread(() -> {
                try {
                    cpart.invokeMember("writeGlobal", object);
                    signal.countDown();
                    signal.await();
                    ret[finalI] = cpart.invokeMember("readGlobal");
                } catch (InterruptedException ignored) {
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ignored) {
            }
        }
        assertSameGlobal(ret, structs);
    }

    private static void assertSameGlobal(Value[] threadIds, StructObject[] structs) {
        for (int i = 0; i < threadIds.length; i++) {
            Assert.assertEquals(runWithPolyglot.getPolyglotContext().asValue(structs[i]), threadIds[i]);
        }
    }

    @Test
    public void testFileIOLock() {
        Value buffer = cpart.invokeMember("open_buffer");
        Thread[] threads = new Thread[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIdx = i;
            threads[threadIdx] = new Thread(() -> {
                cpart.invokeMember("concurrent_put", buffer, threadIdx);
            });
            threads[threadIdx].start();
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ex) {
            }
        }

        Value result = cpart.invokeMember("finalize_buffer", buffer);

        // Expected result: THREAD_COUNT * 20 lines, each containing "thread <idx> <lineno>"
        // Where <idx> is the thread index (0 to THREAD_COUNT-1), and <lineno> is from 0 to 19.
        int[] lineno = new int[THREAD_COUNT];
        result.toString().lines().forEachOrdered(line -> {
            String[] words = line.split(" ");

            String expected = "thread <idx> <lineno>";
            if (words.length > 1) {
                try {
                    int idx = Integer.parseInt(words[1]);
                    int nextLineno = lineno[idx]++;
                    expected = String.format("thread %d %d", idx, nextLineno);
                } catch (NumberFormatException ex) {
                    // ignore, the assert below will give a better error message
                }
            }

            Assert.assertEquals("line", expected, line);
        });

        for (int i = 0; i < THREAD_COUNT; i++) {
            Assert.assertEquals("line count for thread " + i, 20, lineno[i]);
        }
    }
}
