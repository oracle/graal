/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.interop;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings({"static-method", "deprecation"})
public final class FaultyMRTest {

    public static class FaultyMRTestObject implements TruffleObject {

        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return FaultyMRTestObjectMRForeign.ACCESS;
        }

        public static boolean isInstance(@SuppressWarnings("unused") TruffleObject obj) {
            // this simulates a faulty check, usually for the wrong class
            return false;
        }
    }

    @com.oracle.truffle.api.interop.MessageResolution(receiverType = FaultyMRTestObject.class)
    public static class FaultyMRTestObjectMR {

        @com.oracle.truffle.api.interop.Resolve(message = "IS_NULL")
        public abstract static class IsNullNode extends Node {
            @SuppressWarnings("unused")
            public Object access(VirtualFrame frame, FaultyMRTestObject object) {
                return true;
            }
        }
    }

    @Test(expected = AssertionError.class)
    public void test() {
        com.oracle.truffle.api.interop.ForeignAccess.sendIsNull(com.oracle.truffle.api.interop.Message.IS_NULL.createNode(), new FaultyMRTestObject());
    }

    public static class FaultyMRTestObject2 implements TruffleObject {

        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return FaultyMRTestObject2Factory.INSTANCE;
        }
    }

    private static final class FaultyMRTestObject2Factory implements com.oracle.truffle.api.interop.ForeignAccess.StandardFactory {

        // this provides a faulty class to the ForeignAccess
        private static final com.oracle.truffle.api.interop.ForeignAccess INSTANCE = com.oracle.truffle.api.interop.ForeignAccess.create(FaultyMRTestObject.class, new FaultyMRTestObject2Factory());

        @Override
        public CallTarget accessWrite() {
            return null;
        }

        @Override
        public CallTarget accessIsBoxed() {
            return null;
        }

        @Override
        public CallTarget accessUnbox() {
            return null;
        }

        @Override
        public CallTarget accessRead() {
            return null;
        }

        @Override
        public CallTarget accessRemove() {
            return null;
        }

        @Override
        public CallTarget accessIsInstantiable() {
            return null;
        }

        @Override
        public CallTarget accessNew(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessHasKeys() {
            return null;
        }

        @Override
        public CallTarget accessKeys() {
            return null;
        }

        @Override
        public CallTarget accessKeyInfo() {
            return null;
        }

        @Override
        public CallTarget accessIsNull() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
        }

        @Override
        public CallTarget accessIsExecutable() {
            return null;
        }

        @Override
        public CallTarget accessInvoke(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessHasSize() {
            return null;
        }

        @Override
        public CallTarget accessGetSize() {
            return null;
        }

        @Override
        public CallTarget accessExecute(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessIsPointer() {
            return null;
        }

        @Override
        public CallTarget accessAsPointer() {
            return null;
        }

        @Override
        public CallTarget accessToNative() {
            return null;
        }

        @Override
        public CallTarget accessMessage(com.oracle.truffle.api.interop.Message unknown) {
            return null;
        }
    }

    @Test(expected = AssertionError.class)
    public void test2() {
        com.oracle.truffle.api.interop.ForeignAccess.sendIsNull(com.oracle.truffle.api.interop.Message.IS_NULL.createNode(), new FaultyMRTestObject2());
    }
}
