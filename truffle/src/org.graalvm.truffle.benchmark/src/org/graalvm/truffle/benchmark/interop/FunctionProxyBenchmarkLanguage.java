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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;

@TruffleLanguage.Registration(id = FunctionProxyBenchmarkLanguage.ID)
public class FunctionProxyBenchmarkLanguage extends TruffleLanguage<TruffleLanguage.Env> {

    static final String ID = "org-graalvm-benchmark-interop-FunctionProxyLanguage";

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        String source = request.getSource().getCharacters().toString();
        if (source.equals("members")) {
            return RootNode.createConstantNode(new SumObject()).getCallTarget();
        }
        if (source.equals("host")) {
            return RootNode.createConstantNode(new HostSumFunction()).getCallTarget();
        }
        if (source.equals("string")) {
            return RootNode.createConstantNode(new StringSumFunction()).getCallTarget();
        }
        int arity = Integer.parseInt(source);
        return RootNode.createConstantNode(new SumFunction(arity)).getCallTarget();
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class SumObject implements TruffleObject {

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return MemberNames.INSTANCE;
        }

        @ExportMessage
        boolean isMemberInvocable(String member) {
            return member.equals("regular") || member.equals("supported");
        }

        @ExportMessage
        double invokeMember(String member, Object[] arguments) throws UnknownIdentifierException, ArityException {
            if (!isMemberInvocable(member)) {
                throw UnknownIdentifierException.create(member);
            }
            if (arguments.length != 2) {
                throw ArityException.create(2, 2, arguments.length);
            }
            return (double) arguments[0] + (double) arguments[1];
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class MemberNames implements TruffleObject {

        static final MemberNames INSTANCE = new MemberNames();

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return 2;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < 2;
        }

        @ExportMessage
        String readArrayElement(long index) throws InvalidArrayIndexException {
            if (index == 0) {
                return "regular";
            } else if (index == 1) {
                return "supported";
            }
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class HostSumFunction implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        double execute(Object[] arguments,
                        @CachedLibrary(limit = "1") InteropLibrary objects) throws UnsupportedMessageException {
            assert arguments.length == 2;
            try {
                return (int) objects.readMember(arguments[0], "number") + (double) (int) arguments[1];
            } catch (UnknownIdentifierException e) {
                throw new AssertionError(e);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class StringSumFunction implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        double execute(Object[] arguments,
                        @CachedLibrary(limit = "1") InteropLibrary strings) throws UnsupportedMessageException {
            assert arguments.length == 2;
            return strings.asString(arguments[0]).length() + (double) (int) arguments[1];
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SumFunction implements TruffleObject {

        final int arity;

        SumFunction(int arity) {
            this.arity = arity;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        static class Execute {

            @Specialization(guards = "receiver.arity == arity", limit = "5")
            @ExplodeLoop
            static double sum(SumFunction receiver, Object[] arguments,
                            @Cached("receiver.arity") int arity,
                            @CachedLibrary(limit = "1") InteropLibrary characters) throws UnsupportedMessageException {
                assert arguments.length == arity;
                double sum = 0;
                for (int i = 0; i < arity; i++) {
                    Object argument = arguments[i];
                    if (argument instanceof Boolean) {
                        sum += (boolean) argument ? 1 : 0;
                    } else if (argument instanceof Double) {
                        sum += (double) argument;
                    } else if (argument instanceof Float) {
                        sum += (float) argument;
                    } else if (argument instanceof Long) {
                        sum += (long) argument;
                    } else if (argument instanceof Integer) {
                        sum += (int) argument;
                    } else if (argument instanceof Short) {
                        sum += (short) argument;
                    } else if (argument instanceof Byte) {
                        sum += (byte) argument;
                    } else {
                        sum += characters.asString(argument).charAt(0);
                    }
                }
                return sum;
            }
        }
    }
}
