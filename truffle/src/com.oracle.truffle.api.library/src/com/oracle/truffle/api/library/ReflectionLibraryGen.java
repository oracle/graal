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
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;

@GeneratedBy(ReflectionLibrary.class)
final class ReflectionLibraryGen extends LibraryFactory<ReflectionLibrary> {

    private static final ReflectionLibraryGen INSTANCE = new ReflectionLibraryGen();

    static {
        LibraryFactory.register(ReflectionLibrary.class, INSTANCE);
    }

    private ReflectionLibraryGen() {
        super(ReflectionLibrary.class, Collections.unmodifiableList(Arrays.asList(Proxy.SEND)), new UncachedDispatch());
    }

    @Override
    protected Class<?> getDefaultClass(Object receiver) {
        return ReflectionLibraryDefault.class;
    }

    @Override
    protected ReflectionLibrary createProxy(ReflectionLibrary library) {
        return new Proxy(library);
    }

    @Override
    protected Object genericDispatch(Library originalLib, Object receiver, Message message, Object[] args, int offset) throws Exception {
        ReflectionLibrary lib = (ReflectionLibrary) originalLib;
        MessageImpl messageImpl = (MessageImpl) message;
        if (messageImpl.getParameterTypes().size() - 1 != args.length - offset) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Invalid number of arguments.");
        }
        switch (messageImpl.index) {
            case 0:
                return lib.send(receiver, (Message) args[offset], (Object[]) args[offset + 1]);
        }
        CompilerDirectives.transferToInterpreter();
        throw new AbstractMethodError(message.toString());
    }

    @Override
    protected ReflectionLibrary createCachedDispatchImpl(int limit) {
        return new CachedDispatchFirst(null, null, limit);
    }

    @GeneratedBy(ReflectionLibrary.class)
    private static class MessageImpl extends Message {

        final int index;

        MessageImpl(String name, int index, Class<?> returnType, Class<?>... parameters) {
            super(ReflectionLibrary.class, name, returnType, parameters);
            this.index = index;
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class Proxy extends ReflectionLibrary {

        private static final Message SEND = new MessageImpl("send", 0, Object.class, Object.class, Message.class, Object[].class);

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
    private static final class CachedToUncachedDispatch extends ReflectionLibrary {

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public Object send(Object receiver_, Message message, Object... args) throws Exception {
            assert getRootNode() != null : "Invalid libray usage. Cached library must be adopted by a RootNode before it is executed.";
            Node prev_ = NodeUtil.pushEncapsulatingNode(getParent());
            try {
                return INSTANCE.getUncached(receiver_).send(receiver_, message, args);
            } finally {
                NodeUtil.popEncapsulatingNode(prev_);
            }
        }

        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class UncachedDispatch extends ReflectionLibrary {

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public Object send(Object receiver_, Message message, Object... args) throws Exception {
            return INSTANCE.getUncached(receiver_).send(receiver_, message, args);
        }

        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

        @Override
        protected boolean isAdoptable() {
            return false;
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class CachedDispatchNext extends CachedDispatch {

        CachedDispatchNext(ReflectionLibrary library, CachedDispatch next) {
            super(library, next);
        }

        @Override
        int getLimit() {
            throw new AssertionError();
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class CachedDispatchFirst extends CachedDispatch {

        private final int limit_;

        CachedDispatchFirst(ReflectionLibrary library, CachedDispatch next, int limit_) {
            super(library, next);
            this.limit_ = limit_;
        }

        @Override
        int getLimit() {
            return this.limit_;
        }

        @Override
        public NodeCost getCost() {
            if (this.library instanceof CachedToUncachedDispatch) {
                return NodeCost.MEGAMORPHIC;
            }
            CachedDispatch current = this;
            int count = 0;
            do {
                if (current.library != null) {
                    count++;
                }
                current = current.next;
            } while (current != null);
            return NodeCost.fromCount(count);
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private abstract static class CachedDispatch extends ReflectionLibrary {

        @Child ReflectionLibrary library;
        @Child CachedDispatch next;

        CachedDispatch(ReflectionLibrary library, CachedDispatch next) {
            this.library = library;
            this.next = next;
        }

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
                    current = current.next;
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
            ReflectionLibrary thisLibrary = current.library;
            if (thisLibrary == null) {
                this.library = insert(INSTANCE.createCached(receiver_));
            } else {
                Lock lock = getLock();
                lock.lock();
                try {
                    int count = 0;
                    do {
                        ReflectionLibrary currentLibrary = current.library;
                        if (currentLibrary != null && currentLibrary.accepts(receiver_)) {
                            return;
                        }
                        count++;
                        current = current.next;
                    } while (current != null);
                    if (count >= getLimit()) {
                        this.library = insert(new CachedToUncachedDispatch());
                        this.next = null;
                    } else {
                        this.next = insert(new CachedDispatchNext(INSTANCE.createCached(receiver_), next));
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

    }
}
