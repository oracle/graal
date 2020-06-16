/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.tests.interop.values.BoxedIntValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class TypeCheckTest extends InteropTestBase {

    private static Value checkTypes;

    @BeforeClass
    public static void loadTestBitcode() {
        Value testLibrary = loadTestBitcodeValue("typeCheck.c");
        checkTypes = testLibrary.getMember("check_types");
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        tests.add(new Object[]{"boolean", true});
        tests.add(new Object[]{"byte", (byte) 5});
        tests.add(new Object[]{"short", (short) 5});
        tests.add(new Object[]{"int", 5});
        tests.add(new Object[]{"boxedint", new BoxedIntValue(42)});
        tests.add(new Object[]{"long", 5L});
        tests.add(new Object[]{"big_long", Long.MAX_VALUE});
        tests.add(new Object[]{"float", 5.7f});
        tests.add(new Object[]{"small_float", Float.MIN_VALUE});
        tests.add(new Object[]{"big_float", Float.MAX_VALUE});
        tests.add(new Object[]{"double", 5.7});
        tests.add(new Object[]{"small_double", Double.MIN_VALUE});
        tests.add(new Object[]{"big_double", Double.MAX_VALUE});
        tests.add(new Object[]{"string", "Hello, World!"});
        tests.add(new Object[]{"null", null});
        tests.add(new Object[]{"object", ProxyObject.fromMap(new HashMap<>())});
        tests.add(new Object[]{"array", ProxyArray.fromArray()});
        tests.add(new Object[]{"executable", new ProxyExecutable() {
            @Override
            public Object execute(Value... arguments) {
                return null;
            }
        }});
        tests.add(new Object[]{"class", BigInteger.class});
        return tests;
    }

    @Parameter(0) public String name;
    @Parameter(1) public Object value;

    @Test
    public void checkTypes() {
        Value v = runWithPolyglot.getPolyglotContext().asValue(value);
        int ret = checkTypes.execute(v).asInt();

        Assert.assertEquals("is_value", true, (ret & 1) != 0);
        Assert.assertEquals("is_null", v.isNull(), (ret & 2) != 0);
        Assert.assertEquals("is_number", v.isNumber(), (ret & 4) != 0);
        Assert.assertEquals("is_boolean", v.isBoolean(), (ret & 8) != 0);
        Assert.assertEquals("is_string", v.isString(), (ret & 16) != 0);
        Assert.assertEquals("can_execute", v.canExecute(), (ret & 32) != 0);
        Assert.assertEquals("has_array_elements", v.hasArrayElements(), (ret & 64) != 0);
        Assert.assertEquals("has_members", v.hasMembers(), (ret & 128) != 0);
        Assert.assertEquals("can_instantiate", v.canInstantiate(), (ret & 256) != 0);
    }
}
