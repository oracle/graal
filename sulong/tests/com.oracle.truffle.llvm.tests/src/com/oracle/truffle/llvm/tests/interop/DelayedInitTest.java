/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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

import java.nio.file.Paths;
import java.util.ArrayList;

import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.llvm.tests.CommonTestUtils;

/**
 * Tests whether parsed libraries can be executed in arbitrary order.
 */
@RunWith(CommonTestUtils.ExcludingTruffleRunner.class)
public class DelayedInitTest extends InteropTestBase {

    @Test
    public void loadWithTruffleAPI() {
        ArrayList<String> buf = new ArrayList<>();
        // register callback function for reporting
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember(
                        "callback", (ProxyExecutable) t -> {
                            Assert.assertEquals("argument count", 1, t.length);
                            if (t[0].isString()) {
                                buf.add(t[0].asString());
                            } else {
                                Assert.fail("unexpected value type");
                            }
                            return 0;
                        });
        // first parse libFoo, then libBar (both depend on libBaz)
        CallTarget targetFoo = getCallTarget("libFoo.so");
        CallTarget targetBar = getCallTarget("libBar.so");
        // execute them the other way round to ensure libBar also initializes libBaz
        targetBar.call();
        targetFoo.call();
        String[] lines = buf.toArray(buf.toArray(new String[0]));
        Assert.assertArrayEquals(new String[]{
                        "ctor baz",
                        "ctor bar",
                        "hello baz",
                        "ctor foo",
                        "hello baz"
        }, lines);
    }

    private static CallTarget getCallTarget(String name) {
        return getTestBitcodeCallTarget(Paths.get(testBase.toString(), "delayedInit", name).toFile());
    }
}
