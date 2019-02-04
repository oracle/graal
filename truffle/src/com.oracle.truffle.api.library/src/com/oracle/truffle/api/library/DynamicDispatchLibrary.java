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
package com.oracle.truffle.api.library;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;

/**
 * Base library if the receiver export needs to be dispatched.
 */
@GenerateLibrary
public abstract class DynamicDispatchLibrary extends Library {

    /**
     * Returns a class that {@link ExportLibrary exports} at least one library with an explicit
     * receiver. Returns <code>null</code> to indicate that the default dispatch of the library
     * should be used.
     */
    @Abstract
    public Class<?> dispatch(@SuppressWarnings("unused") Object receiver) {
        return null;
    }

    public static LibraryFactory<DynamicDispatchLibrary> getFactory() {
        return Lazy.FACTORY;
    }

    /**
     * Cast the object receiver type to the dispatched type. This is not supposed to be implemented
     * by dynamic dispatch implementer but is automatically implemented when implementing dynamic
     * dispatch.
     *
     * @param receiver
     * @return
     */
    /*
     * This message is known by the annotation processor directly.
     */
    public abstract Object cast(Object receiver);

    /*
     * This indirection is needed to avoid cyclic class initialization. The enclosing class needs to
     * be loaded before Dispatch.resolve can be used.
     */
    static final class Lazy {

        private Lazy() {
            /* No instances */
        }

        static final LibraryFactory<DynamicDispatchLibrary> FACTORY = LibraryFactory.resolve(DynamicDispatchLibrary.class);

    }
}

// CheckStyle: start generated
@GeneratedBy(DynamicDispatchLibrary.class)
final class DynamicDispatchLibraryGen extends LibraryFactory<DynamicDispatchLibrary> {

    private static final DynamicDispatchLibraryGen INSTANCE = new DynamicDispatchLibraryGen();

    static {
        LibraryFactory.register(DynamicDispatchLibrary.class, INSTANCE);
        LibraryExport.register(DynamicDispatchLibrary.class, new Default());
    }

    private DynamicDispatchLibraryGen() {
        super(DynamicDispatchLibrary.class, Collections.unmodifiableList(Arrays.asList(Proxy.DISPATCH)), new UncachedDispatch());
    }

    @Override
    protected Class<?> getDefaultClass(Object receiver) {
        return DynamicDispatchLibrary.class;
    }

    @Override
    protected DynamicDispatchLibrary createProxy(ReflectionLibrary library) {
        return new Proxy(library);
    }

    @Override
    protected Object genericDispatch(Library originalLib, Object receiver, Message message, Object[] args, int offset) throws Exception {
        DynamicDispatchLibrary lib = (DynamicDispatchLibrary) originalLib;
        MessageImpl messageImpl = (MessageImpl) message;
        if (messageImpl.getParameterTypes().size() - 1 != args.length - offset) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Invalid number of arguments.");
        }
        switch (messageImpl.index) {
            case 0:
                return lib.dispatch(receiver);
        }
        CompilerDirectives.transferToInterpreter();
        throw new AbstractMethodError(message.toString());
    }

    @Override
    protected DynamicDispatchLibrary createDispatchImpl(int limit) {
        return new CachedDispatchFirst(null, null, limit);
    }

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static final class Default extends LibraryExport<DynamicDispatchLibrary> {

        private Default() {
            super(DynamicDispatchLibrary.class, Object.class, false);
        }

        @Override
        protected DynamicDispatchLibrary createUncached(Object receiver) {
            return new Uncached(receiver);
        }

        @Override
        protected DynamicDispatchLibrary createCached(Object receiver) {
            return new Cached(receiver);
        }

        @GeneratedBy(DynamicDispatchLibrary.class)
        private static final class Cached extends DynamicDispatchLibrary {

            private final Class<? extends Object> receiverClass_;

            Cached(Object receiver) {
                this.receiverClass_ = receiver.getClass();
            }

            @Override
            public Object cast(Object receiver) {
                return CompilerDirectives.castExact(receiver, receiverClass_);
            }

            @Override
            public boolean accepts(Object receiver) {
                return receiver.getClass() == this.receiverClass_;
            }

            @Override
            protected boolean isAdoptable() {
                return false;
            }

            @Override
            public Class<?> dispatch(Object receiver) {
                assert this.accepts(receiver) : "Invalid library usage. Library does not accept given receiver.";
                return super.dispatch(receiver);
            }

        }

        @GeneratedBy(DynamicDispatchLibrary.class)
        private static final class Uncached extends DynamicDispatchLibrary {

            private final Class<? extends Object> receiverClass_;

            Uncached(Object receiver) {
                this.receiverClass_ = receiver.getClass();
            }

            @Override
            public boolean accepts(Object receiver) {
                return receiver.getClass() == this.receiverClass_;
            }

            @Override
            public Object cast(Object receiver) {
                return receiver;
            }

            @Override
            protected boolean isAdoptable() {
                return false;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.MEGAMORPHIC;
            }

            @TruffleBoundary
            @Override
            public Class<?> dispatch(Object receiver) {
                assert this.accepts(receiver) : "Invalid library usage. Library does not accept given receiver.";
                return super.dispatch(receiver);
            }

        }
    }

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static class MessageImpl extends Message {

        final int index;

        MessageImpl(String name, int index, Class<?> returnType, Class<?>... parameters) {
            super(DynamicDispatchLibrary.class, name, returnType, parameters);
            this.index = index;
        }

    }

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static final class Proxy extends DynamicDispatchLibrary {

        private static final Message DISPATCH = new MessageImpl("dispatch", 0, Class.class, Object.class);

        @Child private ReflectionLibrary lib;

        Proxy(ReflectionLibrary lib) {
            this.lib = lib;
        }

        @Override
        public Object cast(Object receiver) {
            return receiver;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<?> dispatch(Object receiver_) {
            try {
                return (Class<?>) lib.send(receiver_, Proxy.DISPATCH);
            } catch (RuntimeException e_) {
                throw e_;
            } catch (Exception e_) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e_);
            }
        }

        @Override
        public boolean accepts(Object receiver_) {
            return lib.accepts(receiver_);
        }

    }

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static final class CachedToUncachedDispatch extends DynamicDispatchLibrary {

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public Class<?> dispatch(Object receiver_) {
            assert getRootNode() != null : "Invalid libray usage. Cached library must be adopted by a RootNode before it is executed.";
            Node prev_ = NodeUtil.pushEncapsulatingNode(getParent());
            try {
                return INSTANCE.getUncached(receiver_).dispatch(receiver_);
            } finally {
                NodeUtil.popEncapsulatingNode(prev_);
            }
        }

        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

        @Override
        public Object cast(Object receiver) {
            return receiver;
        }

    }

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static final class UncachedDispatch extends DynamicDispatchLibrary {

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public Class<?> dispatch(Object receiver_) {
            return INSTANCE.getUncached(receiver_).dispatch(receiver_);
        }

        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

        @Override
        public Object cast(Object receiver) {
            return receiver;
        }

        @Override
        protected boolean isAdoptable() {
            return false;
        }

    }

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static final class CachedDispatchNext extends CachedDispatch {

        CachedDispatchNext(DynamicDispatchLibrary library, CachedDispatch next) {
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

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static final class CachedDispatchFirst extends CachedDispatch {

        private final int limit_;

        CachedDispatchFirst(DynamicDispatchLibrary library, CachedDispatch next, int limit_) {
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

    @GeneratedBy(DynamicDispatchLibrary.class)
    private abstract static class CachedDispatch extends DynamicDispatchLibrary {

        @Child DynamicDispatchLibrary library;
        @Child CachedDispatch next;

        CachedDispatch(DynamicDispatchLibrary library, CachedDispatch next) {
            this.library = library;
            this.next = next;
        }

        abstract int getLimit();

        @Override
        public Class<?> dispatch(Object receiver_) {
            do {
                CachedDispatch current = this;
                do {
                    DynamicDispatchLibrary thisLibrary = current.library;
                    if (thisLibrary != null && thisLibrary.accepts(receiver_)) {
                        return thisLibrary.dispatch(receiver_);
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

        @Override
        public Object cast(Object receiver) {
            return receiver;
        }

        private void specialize(Object receiver_) {
            CachedDispatch current = this;
            DynamicDispatchLibrary thisLibrary = current.library;
            if (thisLibrary == null) {
                this.library = insert(INSTANCE.create(receiver_));
            } else {
                Lock lock = getLock();
                lock.lock();
                try {
                    int count = 0;
                    do {
                        DynamicDispatchLibrary currentLibrary = current.library;
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
                        this.next = insert(new CachedDispatchNext(INSTANCE.create(receiver_), next));
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

    }
}
