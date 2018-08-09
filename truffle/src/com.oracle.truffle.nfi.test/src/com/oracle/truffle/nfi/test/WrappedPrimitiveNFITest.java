/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class WrappedPrimitiveNFITest extends NFITest {

    private static class TestObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            Assert.fail("unexpected interop access to TestObject");
            return null;
        }
    }

    private static final Object[] ARGUMENTS = {
                    false, (byte) 42, (short) 42, (char) 42, 42, (long) 42, (float) 42, (double) 42, "Hello, World!", new TestObject(), new NullObject()
    };

    @Parameters(name = "{1}")
    public static List<Object[]> getParameters() {
        ArrayList<Object[]> ret = new ArrayList<>(ARGUMENTS.length);
        for (Object arg : ARGUMENTS) {
            ret.add(new Object[]{arg, arg.getClass().getSimpleName()});
        }
        return ret;
    }

    @Parameter(0) public Object argument;
    @Parameter(1) public String argumentType;

    public static class PassObjectNode extends SendExecuteNode {

        public PassObjectNode() {
            super("pass_object", "(object, ():object, (object, object):object) : object");
        }
    }

    @Test
    public void passObjectTest(@Inject(PassObjectNode.class) CallTarget target) {
        TestCallback getObject = new TestCallback(0, (args) -> {
            return argument;
        });

        TestCallback verifyObject = new TestCallback(2, (args) -> {
            Assert.assertSame("arg 1", argument, args[0]);
            Assert.assertSame("arg 2", argument, args[1]);
            return argument;
        });

        Object ret = target.call(argument, getObject, verifyObject);
        Assert.assertSame("return value", argument, ret);
    }
}
