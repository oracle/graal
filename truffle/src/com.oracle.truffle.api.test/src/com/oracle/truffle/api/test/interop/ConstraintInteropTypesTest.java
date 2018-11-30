/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.fail;

import java.math.BigInteger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("deprecation")
public class ConstraintInteropTypesTest {

    private Context context;
    private Value polyglot;

    @Before
    public void setup() {
        this.context = Context.create();
        this.polyglot = context.getPolyglotBindings();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test(expected = PolyglotException.class)
    public void forbidNonPrimitiveObjectReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(this);

        polyglot.putMember("value", obj);
        Value result = polyglot.getMember("value").execute();
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = PolyglotException.class)
    public void forbidNullReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(null);
        polyglot.putMember("value", obj);
        Value result = polyglot.getMember("value").execute();
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = PolyglotException.class)
    public void forbidStringBuilderReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(new StringBuilder("I am string builder!"));
        polyglot.putMember("value", obj);
        Value result = polyglot.getMember("value").execute();
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = PolyglotException.class)
    public void forbidBigIntegerReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(new BigInteger("30"));
        polyglot.putMember("value", obj);
        Value result = polyglot.getMember("value").execute();
        fail("No result, an exception should be thrown: " + result);
    }

    public void allowStringReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        polyglot.putMember("value", obj);
        Value result = polyglot.getMember("value").execute();
        Assert.assertEquals("30", result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidNonPrimitiveObjectParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = this;
        Object result = com.oracle.truffle.api.interop.ForeignAccess.sendExecute(com.oracle.truffle.api.interop.Message.EXECUTE.createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = NullPointerException.class)
    public void forbidNullParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = null;
        Object result = com.oracle.truffle.api.interop.ForeignAccess.sendExecute(com.oracle.truffle.api.interop.Message.EXECUTE.createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidStringBuilderParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = new StringBuilder("I am string builder!");
        Object result = com.oracle.truffle.api.interop.ForeignAccess.sendExecute(com.oracle.truffle.api.interop.Message.EXECUTE.createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidBigIntegerParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = new BigInteger("30");
        Object result = com.oracle.truffle.api.interop.ForeignAccess.sendExecute(com.oracle.truffle.api.interop.Message.EXECUTE.createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    public void allowStringReturnWithParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = "30";
        Object result = com.oracle.truffle.api.interop.ForeignAccess.sendExecute(com.oracle.truffle.api.interop.Message.EXECUTE.createNode(), obj, param);
        Assert.assertEquals("30", result);
    }

    abstract static class Dummy extends TruffleLanguage<Object> {
    }

    @com.oracle.truffle.api.interop.MessageResolution(receiverType = BrokenTruffleObject.class)
    static final class BrokenTruffleObject implements TruffleObject {

        final Object value;

        BrokenTruffleObject(Object value) {
            this.value = value;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof BrokenTruffleObject;
        }

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return BrokenTruffleObjectForeign.ACCESS;
        }

        @com.oracle.truffle.api.interop.Resolve(message = "EXECUTE")
        abstract static class BrokenExecNode extends Node {
            @SuppressWarnings("unused")
            Object access(BrokenTruffleObject obj, Object... args) {
                return obj.value;
            }
        }
    }
}
