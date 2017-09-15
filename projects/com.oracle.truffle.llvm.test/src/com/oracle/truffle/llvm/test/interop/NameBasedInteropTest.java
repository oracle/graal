/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.llvm.test.options.TestOptions;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.graalvm.polyglot.Source;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public final class NameBasedInteropTest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule();

    private static LanguageInfo llvmLanguage;

    @BeforeClass
    public static void loadTestBitcode() {
        File file = new File(TestOptions.TEST_SUITE_PATH, "interop/nameBasedInterop/O0_MEM2REG.bc");
        Source source;
        try {
            source = Source.newBuilder("llvm", file).build();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        runWithPolyglot.getPolyglotContext().eval(source);

        final Map<String, LanguageInfo> languages = runWithPolyglot.getTruffleTestEnv().getLanguages();
        llvmLanguage = languages.get("llvm");
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

    public static class SulongTestNode extends RootNode {

        private final TruffleObject function;
        @Child Node execute;

        protected SulongTestNode(String fnName, int argCount) {
            super(null);
            function = (TruffleObject) runWithPolyglot.getTruffleTestEnv().lookupSymbol(llvmLanguage, fnName);
            execute = Message.createExecute(argCount).createNode();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return ForeignAccess.sendExecute(execute, function, frame.getArguments());
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    public class GetStructNode extends SulongTestNode {

        public GetStructNode() {
            super("getStruct" + name, 1);
        }
    }

    @Test
    public void getStruct(@Inject(GetStructNode.class) CallTarget get) {
        Map<String, Object> obj = makeStruct();
        Object expected = obj.get("value" + name);
        Object actual = get.call(JavaInterop.asTruffleObject(obj));
        Assert.assertEquals(expected, actual);
    }

    public class SetStructNode extends SulongTestNode {

        public SetStructNode() {
            super("setStruct" + name, 2);
        }
    }

    @Test
    public void setStruct(@Inject(SetStructNode.class) CallTarget set) {
        Map<String, Object> obj = makeStruct();
        set.call(JavaInterop.asTruffleObject(obj), value);
        Assert.assertEquals(value, obj.get("value" + name));
    }

    public class GetArrayNode extends SulongTestNode {

        public GetArrayNode() {
            super("getArray" + name, 2);
        }
    }

    @Test
    public void getArray(@Inject(GetArrayNode.class) CallTarget get) {
        Object[] arr = new Object[42];
        arr[3] = value;
        Object actual = get.call(JavaInterop.asTruffleObject(arr), 3);
        Assert.assertEquals(value, actual);
    }

    public class SetArrayNode extends SulongTestNode {

        public SetArrayNode() {
            super("setArray" + name, 3);
        }
    }

    @Test
    public void setArray(@Inject(SetArrayNode.class) CallTarget set) {
        Object[] arr = new Object[42];
        set.call(JavaInterop.asTruffleObject(arr), 5, value);
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
