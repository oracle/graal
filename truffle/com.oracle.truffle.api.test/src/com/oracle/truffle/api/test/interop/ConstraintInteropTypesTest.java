/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test.interop;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.vm.PolyglotEngine;
import java.math.BigInteger;
import org.junit.Assert;
import static org.junit.Assert.fail;
import org.junit.Test;

public class ConstraintInteropTypesTest {
    @Test(expected = ClassCastException.class)
    public void forbidNonPrimitiveObjectReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(this);
        PolyglotEngine engine = PolyglotEngine.newBuilder().globalSymbol("value", obj).build();

        Object result = engine.findGlobalSymbol("value").execute().get();
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = NullPointerException.class)
    public void forbidNullReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(null);
        PolyglotEngine engine = PolyglotEngine.newBuilder().globalSymbol("value", obj).build();

        Object result = engine.findGlobalSymbol("value").execute().get();
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidStringBuilderReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(new StringBuilder("I am string builder!"));
        PolyglotEngine engine = PolyglotEngine.newBuilder().globalSymbol("value", obj).build();

        Object result = engine.findGlobalSymbol("value").execute().get();
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidBigIntegerReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject(new BigInteger("30"));
        PolyglotEngine engine = PolyglotEngine.newBuilder().globalSymbol("value", obj).build();

        Object result = engine.findGlobalSymbol("value").execute().get();
        fail("No result, an exception should be thrown: " + result);
    }

    public void allowStringReturn() {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        PolyglotEngine engine = PolyglotEngine.newBuilder().globalSymbol("value", obj).build();

        Object result = engine.findGlobalSymbol("value").execute().get();
        Assert.assertEquals("30", result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidNonPrimitiveObjectParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = this;
        Object result = ForeignAccess.sendExecute(Message.createExecute(1).createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = NullPointerException.class)
    public void forbidNullParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = null;
        Object result = ForeignAccess.sendExecute(Message.createExecute(1).createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidStringBuilderParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = new StringBuilder("I am string builder!");
        Object result = ForeignAccess.sendExecute(Message.createExecute(1).createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    @Test(expected = ClassCastException.class)
    public void forbidBigIntegerParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = new BigInteger("30");
        Object result = ForeignAccess.sendExecute(Message.createExecute(1).createNode(), obj, param);
        fail("No result, an exception should be thrown: " + result);
    }

    public void allowStringReturnWithParam() throws Exception {
        BrokenTruffleObject obj = new BrokenTruffleObject("30");
        Object param = "30";
        Object result = ForeignAccess.sendExecute(Message.createExecute(1).createNode(), obj, param);
        Assert.assertEquals("30", result);
    }

    abstract static class Dummy extends TruffleLanguage<Object> {
    }

    @MessageResolution(language = Dummy.class, receiverType = BrokenTruffleObject.class)
    static final class BrokenTruffleObject implements TruffleObject {

        final Object value;

        BrokenTruffleObject(Object value) {
            this.value = value;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof BrokenTruffleObject;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return BrokenTruffleObjectForeign.createAccess();
        }

        @Resolve(message = "EXECUTE")
        abstract static class BrokenExecNode extends Node {
            @SuppressWarnings("unused")
            Object access(BrokenTruffleObject obj, Object... args) {
                return obj.value;
            }
        }
    }
}
