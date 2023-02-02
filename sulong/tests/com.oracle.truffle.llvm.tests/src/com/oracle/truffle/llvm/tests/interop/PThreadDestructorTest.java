/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.tck.TruffleRunner;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(TruffleRunner.class)
public class PThreadDestructorTest {

    Value testLibrary;

    @Rule
    public TestRule getRule() {
        return (base, description) -> new Statement() {
            final Statement next = InteropTestBase.runWithPolyglot.apply(base, description);

            @Override
            public void evaluate() throws Throwable {
                next.evaluate();
                afterContextClosed();
            }
        };
    }

    @BeforeClass
    public static void loadLibrary() {
        TestOptions.assumeBundledLLVM();
    }

    @Before
    public void initTest() {
        keyValues = Collections.synchronizedSet(new HashSet<>());
        testLibrary = InteropTestBase.loadTestBitcodeValue("pthreadDestr.c");
    }

    @ExportLibrary(InteropLibrary.class)
    static class TestDestructor implements TruffleObject {
        private final Set<Integer> keyValues;

        TestDestructor(Set<Integer> keyValues) {
            this.keyValues = keyValues;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object execute(Object... arguments) {
            try {
                Object val = arguments[0];
                Assert.assertTrue(val instanceof Integer);
                Assert.assertTrue(keyValues.remove(val));
                return val;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Set<Integer> keyValues;

    @Test
    public void testDestructor() {
        TestDestructor destr = new TestDestructor(keyValues);
        testLibrary.invokeMember("create_key", destr);

        int[] values = new int[]{10, 20};
        Thread[] threads = new Thread[values.length];
        for (int i = 0; i < values.length; i++) {
            final int ii = i;
            keyValues.add(values[ii]);
            threads[i] = InteropTestBase.runWithPolyglot.getTruffleTestEnv().createThread(() -> {
                testLibrary.invokeMember("set_specific", values[ii]);
                Value ret = testLibrary.invokeMember("get_specific");
                Assert.assertEquals(values[ii], ret.asInt());
            });
        }

        for (int i = 0; i < values.length; i++) {
            threads[i].start();
        }

        for (int i = 0; i < values.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                Assert.fail(e.getMessage());
            }
        }

    }

    protected void afterContextClosed() {
        Assert.assertTrue(keyValues.isEmpty());
    }

}
