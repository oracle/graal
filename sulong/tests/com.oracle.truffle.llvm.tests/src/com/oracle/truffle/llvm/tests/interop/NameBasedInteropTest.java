/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.tests.interop.values.ArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.StructObject;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public final class NameBasedInteropTest extends InteropTestBase {

    private static TruffleObject testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("nameBasedInterop.c");
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        tests.add(new Object[]{"B", (byte) 5});
        tests.add(new Object[]{"S", (short) 5});
        tests.add(new Object[]{"I", 5});
        tests.add(new Object[]{"L", 5L});
        tests.add(new Object[]{"F", 5.7f});
        tests.add(new Object[]{"D", 5.7});
        return tests;
    }

    @Parameter(0) public String name;
    @Parameter(1) public Object value;

    public class GetStructNode extends SulongTestNode {

        public GetStructNode() {
            super(testLibrary, "getStruct" + name);
        }
    }

    @Test
    public void getStruct(@Inject(GetStructNode.class) CallTarget get) {
        Map<String, Object> members = makeStruct();
        Object expected = members.get("value" + name);
        Object actual = get.call(new StructObject(members));
        Assert.assertEquals(expected, actual);
    }

    public class SetStructNode extends SulongTestNode {

        public SetStructNode() {
            super(testLibrary, "setStruct" + name);
        }
    }

    @Test
    public void setStruct(@Inject(SetStructNode.class) CallTarget set) {
        Map<String, Object> members = makeStruct();
        set.call(new StructObject(members), value);
        Assert.assertEquals(value, members.get("value" + name));
    }

    public class GetArrayNode extends SulongTestNode {

        public GetArrayNode() {
            super(testLibrary, "getArray" + name);
        }
    }

    @Test
    public void getArray(@Inject(GetArrayNode.class) CallTarget get) {
        Object[] arr = new Object[42];
        arr[3] = value;
        Object actual = get.call(new ArrayObject(arr), 3);
        Assert.assertEquals(value, actual);
    }

    public class SetArrayNode extends SulongTestNode {

        public SetArrayNode() {
            super(testLibrary, "setArray" + name);
        }
    }

    @Test
    public void setArray(@Inject(SetArrayNode.class) CallTarget set) {
        Object[] arr = new Object[42];
        set.call(new ArrayObject(arr), 5, value);
        Assert.assertEquals(value, arr[5]);
    }

    private static Map<String, Object> makeStruct() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("valueBool", true);
        values.put("valueB", (byte) 40);
        values.put("valueS", (short) 41);
        values.put("valueI", 42);
        values.put("valueL", 43L);
        values.put("valueF", 44.5F);
        values.put("valueD", 45.5);
        return values;
    }

}
