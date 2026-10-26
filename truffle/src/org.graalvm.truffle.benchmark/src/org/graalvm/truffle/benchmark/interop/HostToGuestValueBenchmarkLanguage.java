/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.interop;

import org.graalvm.truffle.benchmark.interop.HostToGuestValueBenchmarkLanguageFactory.AddOneNodeGen;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/** A synthetic language with an add-one function and a function calling its second argument. */
@TruffleLanguage.Registration(id = HostToGuestValueBenchmarkLanguage.ID, contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
public final class HostToGuestValueBenchmarkLanguage extends TruffleLanguage<TruffleLanguage.Env> {

    public static final String ID = "host-to-guest-value-benchmark";

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        String operation = request.getSource().getCharacters().toString();
        RootNode body = switch (operation) {
            case "addOne" -> new AddOneRootNode(this);
            case "call" -> new CallRootNode(this);
            default -> throw new IllegalArgumentException(operation);
        };
        Executable function = new Executable(body.getCallTarget(), operation.equals("addOne") ? 1 : 2);
        return new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                return function;
            }
        }.getCallTarget();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Executable implements TruffleObject {

        final RootCallTarget target;
        final int arity;

        Executable(RootCallTarget target, int arity) {
            this.target = target;
            this.arity = arity;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        static class Execute {

            @Specialization(guards = "receiver.target == cachedTarget", limit = "2")
            static Object doCached(Executable receiver, Object[] arguments,
                            @Cached("receiver.target") RootCallTarget cachedTarget,
                            @Cached("create(cachedTarget)") DirectCallNode call) throws ArityException {
                checkArity(receiver, arguments);
                return call.call(arguments);
            }

            @Specialization(replaces = "doCached")
            static Object doGeneric(Executable receiver, Object[] arguments,
                            @Cached IndirectCallNode call) throws ArityException {
                checkArity(receiver, arguments);
                return call.call(receiver.target, arguments);
            }

            private static void checkArity(Executable receiver, Object[] arguments) throws ArityException {
                if (arguments.length != receiver.arity) {
                    throw ArityException.create(receiver.arity, receiver.arity, arguments.length);
                }
            }
        }
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class AddOneNode extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static int doInt(int argument) {
            return argument + 1;
        }

        @Specialization
        static double doDouble(double argument) {
            return argument + 1.0;
        }
    }

    private static final class AddOneRootNode extends RootNode {

        @Child private AddOneNode add = AddOneNodeGen.create();

        AddOneRootNode(HostToGuestValueBenchmarkLanguage language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return add.execute(frame.getArguments()[0]);
        }

        @Override
        public String getName() {
            return "host-to-guest-add-one";
        }
    }

    private static final class CallRootNode extends RootNode {

        @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(2);

        CallRootNode(HostToGuestValueBenchmarkLanguage language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            try {
                return interop.execute(arguments[1], arguments[0]);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Override
        public String getName() {
            return "host-to-guest-call";
        }
    }
}
