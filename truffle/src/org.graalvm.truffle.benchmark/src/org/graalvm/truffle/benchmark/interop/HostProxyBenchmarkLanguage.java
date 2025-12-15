/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.HeapIsolationException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@TruffleLanguage.Registration(id = HostProxyBenchmarkLanguage.ID)
public class HostProxyBenchmarkLanguage extends TruffleLanguage<Env> {

    public static final String ID = "org-graalvm-benchmark-interop-HostProxyLanguage";

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
        return RootNode.createConstantNode(new BindInteropExecutable()).getCallTarget();
    }

    @SuppressWarnings("serial")
    static class InteropBenchmarkException extends AbstractTruffleException {

        InteropBenchmarkException(String message) {
            super(message);
        }

        InteropBenchmarkException(Throwable cause) {
            super(cause.getMessage());
            initCause(cause);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class BindInteropExecutable implements TruffleObject {

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] args, @Bind Node node) throws ArityException, UnsupportedMessageException {
            if (args.length != 3) {
                throw ArityException.create(3, 3, args.length);
            }
            Env env = HostProxyBenchmarkLanguage.REFERENCE.get(node);
            Object receiver = args[0];
            String messageName = InteropLibrary.getUncached().asString(args[1]);
            Object arg1 = args[2];
            InteropLibrary hostObjects = InteropLibrary.getUncached(arg1);
            if (!hostObjects.isHostObject(arg1)) {
                throw new InteropBenchmarkException("Invalid arguments. Interop message name and arguments with receiver as host Object[].");
            }
            try {
                Object[] hostArguments = (Object[]) hostObjects.asHostObject(arg1);
                return new ExecuteInteropExecutable(receiver, messageName, hostArguments);
            } catch (HeapIsolationException e) {
                throw new InteropBenchmarkException("Invalid arguments. Host object cannot be unboxed because it was allocated in an isolated heap.");
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class ExecuteInteropExecutable implements TruffleObject {

        @NeverDefault final Object receiver;
        @NeverDefault final Message message;
        @NeverDefault final Object[] arguments;

        ExecuteInteropExecutable(Object receiver, String message, Object[] arguments) {
            this.receiver = receiver;
            this.message = InteropLibrary.getFactory().getMessages().stream().filter((m) -> m.getSimpleName().equals(message)).findFirst().orElseGet(() -> {
                throw new InteropBenchmarkException("Invalid message " + message);
            });
            this.arguments = arguments;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object[] args,
                        @CachedLibrary("this.receiver") ReflectionLibrary library,
                        @Cached("this.message") Message cachedMessage) {
            try {
                // we assume the message always stays the same for this node
                assert this.message == cachedMessage;

                // receiver and arguments deliberately stay dynamic
                Object r = this.receiver;
                Object[] a = this.arguments;

                Object result = null;
                for (int i = 0; i < HostProxyBenchmark.INNER_LOOP; i++) {
                    result = library.send(r, cachedMessage, a);
                }
                return result;
            } catch (Exception e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new InteropBenchmarkException(e);
            }
        }

    }

    public static final ContextReference<Env> REFERENCE = ContextReference.create(HostProxyBenchmarkLanguage.class);

}
