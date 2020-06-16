/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates.
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

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.tck.TruffleRunner;

@RunWith(TruffleRunner.class)
public class SulongBuiltinsTest extends InteropTestBase {
    private static Value getFunction(String name) {
        Value func = runWithPolyglot.getPolyglotContext().getBindings("llvm").getMember(name);
        Assert.assertNotNull(name + " was not found", func);
        Assert.assertTrue(name + " was found but canExecute() returned false", func.canExecute());
        return func;
    }

    @Test
    public void testPolyglotFitsInI32() {
        String name = "polyglot_fits_in_i32";
        Value func = getFunction(name);
        String msg = "Incorrect implementation of " + name;

        Assert.assertTrue(msg, func.execute(Integer.MAX_VALUE).asBoolean());
        long v = Integer.MAX_VALUE + 1L;
        Assert.assertFalse(msg, func.execute(v).asBoolean());
    }

    @Test
    public void testPolyglotGetArraySize() {
        String name = "polyglot_get_array_size";
        Value func = getFunction(name);
        String msg = "Incorrect implementation of " + name;

        int[] v = {1, 2, 3};
        Assert.assertEquals(msg, func.execute(v).asLong(), 3L);
    }

    @Test
    public void testTruffleHandle() {
        String nameHandle = "truffle_handle_for_managed";
        Value funcHandle = getFunction(nameHandle);
        String msgHandle = "Incorrect implementation of " + nameHandle;

        String nameManaged = "truffle_managed_from_handle";
        Value funcManaged = getFunction(nameManaged);
        String msgManaged = "Incorrect implementation of " + nameManaged;

        Object v = new Object();
        Value handle = funcHandle.execute(v);
        Assert.assertNotNull(msgHandle, handle);
        Value v2 = funcManaged.execute(handle);
        Assert.assertSame(msgManaged, v, v2.asHostObject());
    }
}
