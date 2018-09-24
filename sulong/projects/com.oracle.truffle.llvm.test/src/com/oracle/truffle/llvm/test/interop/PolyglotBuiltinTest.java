/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.test.interop.values.ArrayObject;
import com.oracle.truffle.llvm.test.interop.values.BoxedTestValue;
import com.oracle.truffle.llvm.test.interop.values.NullValue;
import com.oracle.truffle.llvm.test.interop.values.StructObject;
import com.oracle.truffle.llvm.test.interop.values.TestConstructor;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.math.BigInteger;
import java.util.HashMap;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assume;

@RunWith(TruffleRunner.class)
public class PolyglotBuiltinTest extends InteropTestBase {

    private static TruffleObject testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = InteropTestBase.loadTestBitcodeInternal("polyglotBuiltinTest");
    }

    public static class TestNewNode extends SulongTestNode {

        public TestNewNode() {
            super(testLibrary, "test_new");
        }
    }

    @Test
    public void testNew(@Inject(TestNewNode.class) CallTarget testNew) {
        Object ret = testNew.call(new TestConstructor(1, args -> new BoxedTestValue(args[0])));

        Assert.assertThat(ret, is(instanceOf(BoxedTestValue.class)));
        BoxedTestValue value = (BoxedTestValue) ret;
        Assert.assertEquals(42, value.getValue());
    }

    public static class TestRemoveMemberNode extends SulongTestNode {

        public TestRemoveMemberNode() {
            super(testLibrary, "test_remove_member");
        }
    }

    @Test
    public void testRemoveMember(@Inject(TestRemoveMemberNode.class) CallTarget testRemoveMember) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("test", new NullValue());

        Object ret = testRemoveMember.call(new StructObject(map));
        Assert.assertEquals("ret", true, ret);

        Assert.assertFalse("containsKey(test)", map.containsKey("test"));
    }

    public static class TestRemoveArrayElementNode extends SulongTestNode {

        public TestRemoveArrayElementNode() {
            super(testLibrary, "test_remove_array_element");
        }
    }

    @Test
    public void testRemoveIndex(@Inject(TestRemoveArrayElementNode.class) CallTarget testRemoveArrayElement) {
        Object[] arr = new Object[5];

        Object ret = testRemoveArrayElement.call(new ArrayObject(arr));
        Assert.assertEquals("ret", true, ret);

        Assert.assertEquals("arr[3]", "<removed>", arr[3]);
    }

    public static class TestHasMemberNode extends SulongTestNode {

        public TestHasMemberNode() {
            super(testLibrary, "test_has_member");
        }
    }

    @Test
    public void testHasMemberExisting(@Inject(TestHasMemberNode.class) CallTarget testHasMember) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("test", new NullValue());

        Object ret = testHasMember.call(new StructObject(map));
        Assert.assertEquals("ret", true, ret);
    }

    @Test
    public void testHasMemberNotExisting(@Inject(TestHasMemberNode.class) CallTarget testHasMember) {
        HashMap<String, Object> map = new HashMap<>();

        Object ret = testHasMember.call(new StructObject(map));
        Assert.assertEquals("ret", false, ret);
    }

    @Test
    public void testHasMemberNotAnObject(@Inject(TestHasMemberNode.class) CallTarget testHasMember) {
        Object ret = testHasMember.call(new NullValue());
        Assert.assertEquals("ret", false, ret);
    }

    public static class TestHostInteropNode extends SulongTestNode {

        public TestHostInteropNode() {
            super(testLibrary, "test_host_interop");
        }
    }

    @Test
    public void testHostInterop(@Inject(TestHostInteropNode.class) CallTarget testHostInterop) {
        Assume.assumeFalse("skipping host interop test in native mode", TruffleOptions.AOT);

        Object ret = testHostInterop.call();

        Assert.assertTrue("isHostObject", runWithPolyglot.getTruffleTestEnv().isHostObject(ret));
        Assert.assertSame("ret", BigInteger.class, runWithPolyglot.getTruffleTestEnv().asHostObject(ret));
    }
}
