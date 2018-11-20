/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
// CheckStyle: start generated
package com.oracle.truffle.api.library;

import java.util.Arrays;
import java.util.Collections;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GeneratedBy;

@GeneratedBy(ReflectionLibrary.class)
final class ReflectionLibraryGen extends ResolvedLibrary<ReflectionLibrary> {

    private static final ReflectionLibraryGen INSTANCE = new ReflectionLibraryGen();

    static {
        ResolvedLibrary.register(ReflectionLibrary.class, INSTANCE);
        ResolvedExports.register(Default.class, new Default());
    }

    private ReflectionLibraryGen() {
        super(ReflectionLibrary.class, Collections.unmodifiableList(Arrays.asList(Proxy.SEND)), new UncachedDispatch());
    }

    @Override
    protected Class<?> getDefaultClass(Object receiver) {
        return Default.class;
    }

    @Override
    protected ReflectionLibrary createProxy(ReflectionLibrary library) {
        return new Proxy(library);
    }

    @Override
    protected Object genericDispatch(Library originalLib, Object receiver, Message message, Object[] args, int offset) throws Exception {
        ReflectionLibrary lib = (ReflectionLibrary) originalLib;
        return lib.send(receiver, message, args);
    }

    @Override
    public ReflectionLibrary createCachedDispatchImpl(int limit) {
        return new CachedDispatchFirst(null, limit);
    }

    @GeneratedBy(ReflectionLibrary.class)
    private static class Default extends ResolvedExports<ReflectionLibrary> {

        private static final Uncached UNCACHED = new Uncached();

        private Default() {
            super(ReflectionLibrary.class);
        }

        @Override
        protected ReflectionLibrary createUncached(Object receiver) {
            return UNCACHED;
        }

        @Override
        protected ReflectionLibrary createCached(Object receiver) {
            return new Cached(receiver.getClass());
        }

        private static final class Uncached extends ReflectionLibrary {

            @Override
            public boolean accepts(Object receiver) {
                return true;
            }

            @Override
            @TruffleBoundary
            public Object send(Object receiver, Message message, Object... args) throws Exception {
                ResolvedLibrary<?> lib = message.library;
                return lib.genericDispatch(lib.getUncached(receiver), receiver, message, args, 0);
            }
        }

        private static final class Cached extends ReflectionLibrary {

            private final Class<?> receiverClass;
            @Child ReflectiveDispatchNode dispatch = ReflectiveDispatchNodeGen.create();
            private static final Message UNINITIALIZED = new Message(ReflectionLibrary.class, "dummy", Void.class) {
            };
            @CompilationFinal private Message cachedMessage = UNINITIALIZED;

            Cached(Class<?> receiverClass) {
                this.receiverClass = receiverClass;
            }

            private Message profileMessage(Message message) {
                Message localMessage = this.cachedMessage;
                if (localMessage != null) {
                    if (localMessage != message) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        cachedMessage = localMessage = message;
                    }
                } else {
                    localMessage = message;
                }
                return localMessage;
            }

            @Override
            public boolean accepts(Object receiver) {
                return receiver.getClass() == receiverClass;
            }

            @Override
            public Object send(Object receiver, Message message, Object... args) throws Exception {
                return dispatch.executeSend(receiver, profileMessage(message), args, 0);
            }

        }
    }

    @GeneratedBy(ReflectionLibrary.class)
    private static class MessageImpl extends Message {

        MessageImpl(String name, Class<?> returnType, Class<?>... parameters) {
            super(ReflectionLibrary.class, name, returnType, parameters);
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class Proxy extends ReflectionLibrary {

        private static final Message SEND = new MessageImpl("send", Object.class, Object.class, Message.class, Object[].class);

        @Child private ReflectionLibrary lib;

        Proxy(ReflectionLibrary lib) {
            this.lib = lib;
        }

        @Override
        public Object send(Object receiver_, Message message, Object... args) throws Exception {
            try {
                return lib.send(receiver_, Proxy.SEND, message, args);
            } catch (Exception e_) {
                throw e_;
            }
        }

        @Override
        public boolean accepts(Object receiver_) {
            return lib.accepts(receiver_);
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class UncachedDispatch extends ReflectionLibrary {

        @Override
        public Object send(Object receiver_, Message message, Object... args) throws Exception {
            return INSTANCE.getUncached(receiver_).send(receiver_, message, args);
        }

        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class CachedDispatchNext extends CachedDispatch {

        @Child private CachedDispatch next;

        CachedDispatchNext(ReflectionLibrary library, CachedDispatch next) {
            super(library);
            this.next = next;
        }

        @Override
        CachedDispatch getNext() {
            return this.next;
        }

        @Override
        int getLimit() {
            return this.next.getLimit();
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class CachedDispatchFirst extends CachedDispatch {

        private final int limit_;

        CachedDispatchFirst(ReflectionLibrary library, int limit_) {
            super(library);
            this.limit_ = limit_;
        }

        @Override
        CachedDispatch getNext() {
            return null;
        }

        @Override
        int getLimit() {
            return this.limit_;
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private abstract static class CachedDispatch extends ReflectionLibrary {

        @Child private ReflectionLibrary library;

        CachedDispatch(ReflectionLibrary library) {
            this.library = library;
        }

        abstract CachedDispatch getNext();

        abstract int getLimit();

        @Override
        public Object send(Object receiver_, Message message, Object... args) throws Exception {
            do {
                CachedDispatch current = this;
                do {
                    ReflectionLibrary thisLibrary = current.library;
                    if (thisLibrary != null && thisLibrary.accepts(receiver_)) {
                        return thisLibrary.send(receiver_, message, args);
                    }
                    current = current.getNext();
                } while (current != null);
                CompilerDirectives.transferToInterpreterAndInvalidate();
                specialize(receiver_);
            } while (true);
        }

        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

        private void specialize(Object receiver_) {
            CachedDispatch current = this;
            ReflectionLibrary specialized;
            ReflectionLibrary thisLibrary = current.library;
            if (thisLibrary == null) {
                specialized = INSTANCE.createCached(receiver_);
            } else {
                int count = 0;
                do {
                    ReflectionLibrary currentLibrary = current.library;
                    if (currentLibrary != null && currentLibrary.accepts(receiver_)) {
                        return;
                    }
                    count++;
                    current = current.getNext();
                } while (current != null);
                if (count >= getLimit()) {
                    specialized = INSTANCE.getUncachedDispatch();
                } else {
                    specialized = new CachedDispatchNext(INSTANCE.createCached(receiver_), this);
                }
            }
            this.library = insert(specialized);
        }

    }
}
