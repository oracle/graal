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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class RegisterPackageNFITest extends NFITest {

    static class FunctionRegistry implements TruffleObject {

        private final Map<String, TruffleObject> functions;

        @TruffleBoundary
        FunctionRegistry() {
            functions = new HashMap<>();
        }

        @TruffleBoundary
        void add(String name, TruffleObject function) {
            functions.put(name, function);
        }

        @TruffleBoundary
        TruffleObject get(String name) {
            return functions.get(name);
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return FunctionRegistryMessageResolutionForeign.ACCESS;
        }
    }

    @MessageResolution(receiverType = FunctionRegistry.class)
    static class FunctionRegistryMessageResolution {

        @Resolve(message = "EXECUTE")
        abstract static class ExecuteFunctionRegistry extends Node {

            @Child Node bind = Message.INVOKE.createNode();

            private void register(FunctionRegistry receiver, String name, String signature, TruffleObject symbol) {
                try {
                    TruffleObject boundSymbol = (TruffleObject) ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
                    receiver.add(name, boundSymbol);
                } catch (InteropException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(ex);
                }
            }

            Object access(FunctionRegistry receiver, Object[] args) {
                register(receiver, (String) args[0], (String) args[1], (TruffleObject) args[2]);
                return "";
            }
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecutable extends Node {

            @SuppressWarnings("unused")
            boolean access(FunctionRegistry receiver) {
                return true;
            }
        }

        @CanResolve
        abstract static class CanResolveFunctionRegistry extends Node {

            boolean test(TruffleObject obj) {
                return obj instanceof FunctionRegistry;
            }
        }
    }

    static class LoadPackageNode extends Node {

        private final TruffleObject initializePackage = lookupAndBind("initialize_package", "((string,string,pointer):void):void");
        @Child Node execute = Message.EXECUTE.createNode();

        FunctionRegistry loadPackage() {
            FunctionRegistry registry = new FunctionRegistry();
            try {
                ForeignAccess.sendExecute(execute, initializePackage, registry);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(ex);
            }
            return registry;
        }
    }

    public static class RegisterPackageTestNode extends NFITestRootNode {

        @Child LoadPackageNode loadPackage = new LoadPackageNode();

        @Child Node unary = Message.EXECUTE.createNode();
        @Child Node binary = Message.EXECUTE.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            FunctionRegistry registry = loadPackage.loadPackage();

            TruffleObject add = registry.get("add");
            TruffleObject square = registry.get("square");
            TruffleObject sqrt = registry.get("sqrt");

            double a = (Double) frame.getArguments()[0];
            double b = (Double) frame.getArguments()[1];

            double aSq = (Double) ForeignAccess.sendExecute(unary, square, a);
            double bSq = (Double) ForeignAccess.sendExecute(unary, square, b);

            double cSq = (Double) ForeignAccess.sendExecute(binary, add, aSq, bSq);
            return ForeignAccess.sendExecute(unary, sqrt, cSq);
        }
    }

    @Test
    public void testPythagoras(@Inject(RegisterPackageTestNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(3.0, 4.0);
        Assert.assertThat("return value", ret, is(instanceOf(Double.class)));
        Assert.assertEquals("return value", 5.0, ret);
    }
}
