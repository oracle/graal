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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import static com.oracle.truffle.nfi.test.NFITest.NFITestRootNode.getInterop;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class RegisterPackageNFITest extends NFITest {

    private static final FunctionRegistry REGISTRY = new FunctionRegistry();

    @ExportLibrary(InteropLibrary.class)
    static class FunctionRegistry implements TruffleObject {

        private final Map<String, Object> functions;

        @TruffleBoundary
        FunctionRegistry() {
            functions = new HashMap<>();
        }

        @TruffleBoundary
        void clear() {
            functions.clear();
        }

        @TruffleBoundary
        void add(String name, Object function) {
            functions.put(name, function);
        }

        @TruffleBoundary
        Object get(String name) {
            return functions.get(name);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] args,
                        @Cached RegisterFunctionNode register) {
            register.execute(this, (String) args[0], (String) args[1], args[2]);
            return "";
        }

        @GenerateUncached
        abstract static class RegisterFunctionNode extends Node {

            protected abstract void execute(FunctionRegistry receiver, String name, String signature, Object symbol);

            @Specialization(limit = "3")
            static void register(FunctionRegistry receiver, String name, String signature, Object symbol,
                            @CachedLibrary("symbol") InteropLibrary interop) {
                try {
                    Object boundSymbol = interop.invokeMember(symbol, "bind", signature);
                    receiver.add(name, boundSymbol);
                } catch (InteropException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(ex);
                }
            }
        }
    }

    static class LoadPackageNode extends Node {

        private final TruffleObject initializePackage = lookupAndBind("initialize_package", "((string,string,pointer):void):void");
        @Child InteropLibrary interop = getInterop(initializePackage);

        FunctionRegistry loadPackage() {
            REGISTRY.clear();
            try {
                interop.execute(initializePackage, REGISTRY);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(ex);
            }
            return REGISTRY;
        }
    }

    public static class RegisterPackageTestNode extends NFITestRootNode {

        @Child LoadPackageNode loadPackage = new LoadPackageNode();
        @Child InteropLibrary interop = getInterop();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            FunctionRegistry registry = loadPackage.loadPackage();

            Object add = registry.get("add");
            Object square = registry.get("square");
            Object sqrt = registry.get("sqrt");

            double a = (Double) frame.getArguments()[0];
            double b = (Double) frame.getArguments()[1];

            double aSq = (Double) interop.execute(square, a);
            double bSq = (Double) interop.execute(square, b);

            double cSq = (Double) interop.execute(add, aSq, bSq);
            return interop.execute(sqrt, cSq);
        }
    }

    @Test
    public void testPythagoras(@Inject(RegisterPackageTestNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(3.0, 4.0);
        Assert.assertThat("return value", ret, is(instanceOf(Double.class)));
        Assert.assertEquals("return value", 5.0, ret);
    }
}
