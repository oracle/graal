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

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.tests.interop.values.NativeValue;
import com.oracle.truffle.llvm.tests.interop.values.StructObject;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback;
import com.oracle.truffle.llvm.tests.Platform;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class DerefHandleTest extends InteropTestBase {

    private static TruffleObject testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("derefHandleTest.c");
    }

    public class TestAllocateDerefHandleNode extends SulongTestNode {

        public TestAllocateDerefHandleNode() {
            super(testLibrary, "test_allocate_deref_handle");
        }
    }

    public class TestAddHandleMembers extends SulongTestNode {

        public TestAddHandleMembers() {
            super(testLibrary, "test_add_handle_members");
        }
    }

    public class TestReadFromDerefHandleNode extends SulongTestNode {

        public TestReadFromDerefHandleNode() {
            super(testLibrary, "test_read_from_deref_handle");
        }
    }

    public class TestWriteToDerefHandleNode extends SulongTestNode {

        public TestWriteToDerefHandleNode() {
            super(testLibrary, "test_write_to_deref_handle");
        }
    }

    public class TestCallDerefHandleNode extends SulongTestNode {

        public TestCallDerefHandleNode() {
            super(testLibrary, "test_call_deref_handle");
        }
    }

    public class TestDerefHandlePointerArithNode extends SulongTestNode {

        public TestDerefHandlePointerArithNode() {
            super(testLibrary, "test_deref_handle_pointer_arith");
        }
    }

    public class TestCallDerefHandlemMemberNode extends SulongTestNode {

        public TestCallDerefHandlemMemberNode() {
            super(testLibrary, "test_call_deref_handle_member");
        }
    }

    @Test
    public void testWrappedDerefHandle(@Inject(TestAllocateDerefHandleNode.class) CallTarget allocateDerefHandle,
                    @Inject(TestAddHandleMembers.class) CallTarget addHandleMembers) throws UnsupportedMessageException {
        Map<String, Object> members = makePointObject();
        int x = (int) members.get("x");
        int y = (int) members.get("y");
        Object handle = allocateDerefHandle.call(new StructObject(members));
        NativeValue handleNative = new NativeValue(InteropLibrary.getFactory().getUncached().asPointer(handle));
        Object sumObj = addHandleMembers.call(handleNative);
        Assert.assertEquals(x + y, sumObj);
    }

    @Test
    public void testRawDerefHandle(@Inject(TestAllocateDerefHandleNode.class) CallTarget allocateDerefHandle,
                    @Inject(TestAddHandleMembers.class) CallTarget addHandleMembers) {
        Map<String, Object> members = makePointObject();
        int x = (int) members.get("x");
        int y = (int) members.get("y");
        Object handle = allocateDerefHandle.call(new StructObject(members));
        Object sumObj = addHandleMembers.call(handle);
        Assert.assertEquals(x + y, sumObj);
    }

    @Test
    public void testReadFromDerefHandle(@Inject(TestReadFromDerefHandleNode.class) CallTarget accessDerefHandle) {
        Map<String, Object> members = makePointObject();
        int x = (int) members.get("x");
        int y = (int) members.get("y");
        Object actual = accessDerefHandle.call(new StructObject(members));
        Assert.assertEquals(x * x + y * y, actual);
    }

    @Test
    public void testWriteToDerefHandle(@Inject(TestWriteToDerefHandleNode.class) CallTarget writeToDerefHandle) {
        Map<String, Object> members = makePointObject();
        writeToDerefHandle.call(new StructObject(members), 22, 33);
        int x = (int) members.get("x");
        int y = (int) members.get("y");
        Assert.assertEquals(22, x);
        Assert.assertEquals(33, y);
    }

    @Test
    public void testCallDerefHandle(@Inject(TestCallDerefHandleNode.class) CallTarget callDerefHandle) {
        TestCallback callback = new TestCallback(2, DerefHandleTest::add);
        long actual = (long) callDerefHandle.call(callback, 13L, 29L);
        Assert.assertEquals(42L, actual);
    }

    @Test
    public void testDerefHandlePointerArith(@Inject(TestDerefHandlePointerArithNode.class) CallTarget derefHandlePointerArith) {
        Map<String, Object> members = makePointObject();
        int y = (int) members.get("y");
        Object actual = derefHandlePointerArith.call(new StructObject(members));
        Assert.assertEquals(y, actual);
    }

    @Test
    public void testCallDerefHandleMember(@Inject(TestCallDerefHandlemMemberNode.class) CallTarget callDerefHandleMember) {
        Assume.assumeFalse("Skipping AArch64 failing test", Platform.isAArch64());
        Object actual = callDerefHandleMember.call(new StructObject(makePointObject()), 3L, 7L);
        Assert.assertEquals(10L, actual);
    }

    private static Map<String, Object> makePointObject() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("x", 3);
        values.put("y", 4);
        values.put("identity", new TestCallback(2, DerefHandleTest::add));
        return values;
    }

    private static Object add(Object... args) {
        if (args.length == 2 && args[0] instanceof Number && args[1] instanceof Number) {
            return ((long) args[0]) + ((long) args[1]);
        }
        throw new IllegalArgumentException("expected long arguments");
    }

}
