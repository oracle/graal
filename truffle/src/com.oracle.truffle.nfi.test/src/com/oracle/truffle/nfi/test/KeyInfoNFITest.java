/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class KeyInfoNFITest extends NFITest {

    private static void addTest(List<Object[]> ret, Object symbol, Supplier<TruffleObject> object, String description, boolean read, boolean invoke) {
        ret.add(new Object[]{symbol, object, description, read, invoke});
        ret.add(new Object[]{new BoxedPrimitive(symbol), object, description, read, invoke});
    }

    @Parameters(name = "{2}[{0}]")
    public static Collection<Object[]> data() {
        List<Object[]> ret = new ArrayList<>();
        addTest(ret, "increment_SINT32", () -> testLibrary, "testLibrary", true, false);
        addTest(ret, "__NOT_EXISTING__", () -> testLibrary, "testLibrary", false, false);
        addTest(ret, 42, () -> testLibrary, "testLibrary", false, false);

        Supplier<TruffleObject> symbol = () -> {
            try {
                return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), testLibrary, "increment_SINT32");
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        };
        addTest(ret, "bind", symbol, "symbol", false, true);
        addTest(ret, "__NOT_EXISTING__", symbol, "symbol", false, false);
        addTest(ret, 42, symbol, "symbol", false, false);

        Supplier<TruffleObject> boundSymbol = () -> lookupAndBind("increment_SINT32", "(sint32):sint32");
        addTest(ret, "bind", boundSymbol, "boundSymbol", false, true);
        addTest(ret, "__NOT_EXISTING__", boundSymbol, "boundSymbol", false, false);
        addTest(ret, 42, boundSymbol, "boundSymbol", false, false);

        return ret;
    }

    @Parameter(0) public Object symbol;
    @Parameter(1) public Supplier<TruffleObject> object;
    @Parameter(2) public String description;

    @Parameter(3) public boolean read;
    @Parameter(4) public boolean invoke;

    public static class KeyInfoNode extends NFITestRootNode {

        @Child Node keyInfo = Message.KEY_INFO.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            return ForeignAccess.sendKeyInfo(keyInfo, (TruffleObject) frame.getArguments()[0], frame.getArguments()[1]);
        }
    }

    private static void assertBoolean(String message, boolean expected, boolean actual) {
        if (expected) {
            Assert.assertTrue(message, actual);
        } else {
            Assert.assertFalse(message, actual);
        }
    }

    private void verifyKeyInfo(Object keyInfo) {
        Assert.assertThat(keyInfo, is(instanceOf(Integer.class)));
        int flags = (Integer) keyInfo;

        assertBoolean("isExisting", read || invoke, KeyInfo.isExisting(flags));

        assertBoolean("isReadable", read, KeyInfo.isReadable(flags));
        assertBoolean("isInvocable", invoke, KeyInfo.isInvocable(flags));

        Assert.assertFalse(KeyInfo.isWritable(flags));
        Assert.assertFalse(KeyInfo.isInternal(flags));
    }

    @Test
    public void testKeyInfo(@Inject(KeyInfoNode.class) CallTarget keyInfo) {
        Object result = keyInfo.call(object.get(), symbol);
        verifyKeyInfo(result);
    }
}
