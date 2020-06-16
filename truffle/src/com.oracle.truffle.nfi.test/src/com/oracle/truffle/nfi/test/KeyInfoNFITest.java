/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.KeyInfoNFITestFactory.VerifyKeyInfoNodeGen;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class KeyInfoNFITest extends NFITest {

    private static void addTest(List<Object[]> ret, String symbol, Supplier<Object> object, String description, boolean read, boolean invoke, boolean optional) {
        ret.add(new Object[]{symbol, object, description, read, invoke, optional});
    }

    @Parameters(name = "{2}[{0}]")
    public static Collection<Object[]> data() {
        List<Object[]> ret = new ArrayList<>();
        addTest(ret, "increment_SINT32", () -> testLibrary, "testLibrary", true, false, false);
        addTest(ret, "__NOT_EXISTING__", () -> testLibrary, "testLibrary", false, false, true);

        Supplier<Object> symbol = () -> {
            try {
                return UNCACHED_INTEROP.readMember(testLibrary, "increment_SINT32");
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        };
        addTest(ret, "bind", symbol, "symbol", false, true, false);
        addTest(ret, "__NOT_EXISTING__", symbol, "symbol", false, false, false);

        Supplier<Object> boundSymbol = () -> lookupAndBind("increment_SINT32", "(sint32):sint32");
        addTest(ret, "bind", boundSymbol, "boundSymbol", false, true, false);
        addTest(ret, "__NOT_EXISTING__", boundSymbol, "boundSymbol", false, false, false);

        return ret;
    }

    @Parameter(0) public String symbol;
    @Parameter(1) public Supplier<Object> object;
    @Parameter(2) public String description;

    @Parameter(3) public boolean read;
    @Parameter(4) public boolean invoke;
    @Parameter(5) public boolean optional;

    public static class KeyInfoNode extends NFITestRootNode {

        @Child VerifyKeyInfoNode verify = VerifyKeyInfoNodeGen.create();

        @Override
        public Object executeTest(VirtualFrame frame) {
            verify.execute(frame.getArguments()[0], (String) frame.getArguments()[1], (boolean) frame.getArguments()[2], (boolean) frame.getArguments()[3], (boolean) frame.getArguments()[4]);
            return null;
        }
    }

    @TruffleBoundary
    private static void assertBoolean(String message, boolean expected, boolean actual) {
        if (expected) {
            Assert.assertTrue(message, actual);
        } else {
            Assert.assertFalse(message, actual);
        }
    }

    @TruffleBoundary
    private static void assertBooleanOptional(String message, boolean expected, boolean actual) {
        if (expected) {
            Assert.assertTrue(message, actual);
        }
    }

    abstract static class VerifyKeyInfoNode extends Node {

        abstract void execute(Object object, String symbol, boolean read, boolean invoke, boolean optional);

        @Specialization(limit = "3")
        void verify(Object object, String symbol, boolean read, boolean invoke, boolean optional,
                        @CachedLibrary("object") InteropLibrary interop) {
            if (optional) {
                /*
                 * As a performance optimization, the NFI is allowed to report non-existing symbols
                 * in libraries as existing.
                 */
                assertBooleanOptional("isMemberExisting", read || invoke, interop.isMemberExisting(object, symbol));
                assertBooleanOptional("isMemberReadable", read, interop.isMemberReadable(object, symbol));
            } else {
                assertBoolean("isMemberExisting", read || invoke, interop.isMemberExisting(object, symbol));
                assertBoolean("isMemberReadable", read, interop.isMemberReadable(object, symbol));
            }

            /*
             * Even in the optional case, the actual read should fail.
             */
            boolean success = tryRead(interop, object, symbol);
            assertBoolean("trying to read member", read, success);

            assertBoolean("isMemberInvocable", invoke, interop.isMemberInvocable(object, symbol));

            assertBoolean("isMemberInsertable", false, interop.isMemberInsertable(object, symbol));
            assertBoolean("isMemberModifiable", false, interop.isMemberModifiable(object, symbol));
            assertBoolean("isMemberWritable", false, interop.isMemberWritable(object, symbol));
            assertBoolean("isMemberRemovable", false, interop.isMemberRemovable(object, symbol));
            assertBoolean("isMemberInternal", false, interop.isMemberInternal(object, symbol));
        }

        @TruffleBoundary
        static boolean tryRead(InteropLibrary interop, Object object, String symbol) {
            try {
                interop.readMember(object, symbol);
                return true;
            } catch (InteropException ex) {
                return false;
            }
        }
    }

    @Test
    public void testKeyInfo(@Inject(KeyInfoNode.class) CallTarget keyInfo) {
        keyInfo.call(object.get(), symbol, read, invoke, optional);
    }
}
