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
