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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;

/**
 * A library that allows to dynamically dispatch to export library classes. Sometimes the target
 * exports for a receiver type cannot be statically determined by the receiver class with the
 * {@link ExportLibrary} annotation. To allow such types to dynamically dispatch to exports the
 * dynamic dispatch library can be exported instead. By exporting dynamic dispatch the export target
 * can be chosen dynamically.
 * <p>
 * The dynamic dispatch library requires to implement the dispatch method. The dispatch method
 * returns the target exports class that this receiver should dispatch to. If it returns
 * <code>null</code> then the type will dispatch to the library default exports. The implementation
 * of the dispatch must be stable for a single receiver value. For example it is not allowed to
 * change the dispatch target for a receiver instance. If the dispatch target was changed while the
 * receiver was used by a library then an {@link AssertionError} will be thrown.
 * <p>
 * <h4>Full usage example</h4>
 *
 * <pre>
 * &#64;ExportLibrary(DynamicDispatchLibrary.class)
 * static final class DispatchObject {
 *
 *     final Object data;
 *     final Class<?> dispatchTarget;
 *
 *     DispatchObject(Object data, Class<?> dispatchTarget) {
 *         this.data = data;
 *         this.dispatchTarget = dispatchTarget;
 *     }
 *
 *     &#64;ExportMessage
 *     Class<?> dispatch() {
 *         return dispatchTarget;
 *     }
 * }
 *
 * &#64;GenerateLibrary
 * public abstract static class ArrayLibrary extends Library {
 *
 *     public boolean isArray(Object receiver) {
 *         return false;
 *     }
 *
 *     public abstract int read(Object receiver, int index);
 * }
 *
 * &#64;ExportLibrary(value = ArrayLibrary.class, receiverType = DispatchObject.class)
 * static final class DispatchedBufferArray {
 *
 *     &#64;ExportMessage
 *     static boolean isArray(DispatchObject o) {
 *         return true;
 *     }
 *
 *     &#64;ExportMessage
 *     static int read(DispatchObject o, int index) {
 *         return ((int[]) o.data)[index];
 *     }
 * }
 *
 * public static void main(String[] args) {
 *     ArrayLibrary arrays = LibraryFactory.resolve(ArrayLibrary.class).getUncached();
 *     assert 42 == arrays.read(new DispatchObject(new int[]{42}, DispatchedBufferArray.class), 0);
 * }
 * </pre>
 *
 * @since 19.0
 */
@GenerateLibrary
public abstract class DynamicDispatchLibrary extends Library {

    /**
     * Constructor for generated subclasses. Subclasses of this class are generated, do not extend
     * this class directly.
     *
     * @since 19.0
     */
    protected DynamicDispatchLibrary() {
    }

    /**
     * Returns a class that {@link ExportLibrary exports} at least one library with an explicit
     * receiver. Returns <code>null</code> to indicate that the default dispatch of the library
     * should be used.
     *
     * @since 19.0
     */
    @Abstract
    public Class<?> dispatch(@SuppressWarnings("unused") Object receiver) {
        return null;
    }

    /**
     * Cast the object receiver type to the dispatched type. This is not supposed to be implemented
     * by dynamic dispatch implementer but is automatically implemented when implementing dynamic
     * dispatch.
     *
     * @since 19.0
     */
    /*
     * Implementation Note: This message is known by the annotation processor directly. No need to
     * export it as a library message. It is also not allowed to be implemented directly by the
     * dynamic dispatch implementer.
     */
    public abstract Object cast(Object receiver);

    static final LibraryFactory<DynamicDispatchLibrary> FACTORY = LibraryFactory.resolve(DynamicDispatchLibrary.class);

    /**
     * Returns the library factory for {@link DynamicDispatchLibrary}.
     *
     * @since 19.0
     */
    public static LibraryFactory<DynamicDispatchLibrary> getFactory() {
        return FACTORY;
    }

}

// CheckStyle: start generated
@GeneratedBy(DynamicDispatchLibrary.class)
final class DynamicDispatchLibraryGen extends LibraryFactory<DynamicDispatchLibrary> {

    private static final Class<DynamicDispatchLibrary> LIBRARY_CLASS = DynamicDispatchLibraryGen.lazyLibraryClass();
    private static final Message DISPATCH = new MessageImpl("dispatch", 0, Class.class, Object.class);
    private static final DynamicDispatchLibraryGen INSTANCE = new DynamicDispatchLibraryGen();

    static {
        LibraryExport.register(DynamicDispatchLibraryGen.LIBRARY_CLASS, new Default());
        LibraryFactory.register(DynamicDispatchLibraryGen.LIBRARY_CLASS, INSTANCE);
    }

    private DynamicDispatchLibraryGen() {
        super(DynamicDispatchLibraryGen.LIBRARY_CLASS, Collections.unmodifiableList(Arrays.asList(DynamicDispatchLibraryGen.DISPATCH)));
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
        if (messageImpl.getParameterCount() - 1 != args.length - offset) {
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

    @Override
    protected DynamicDispatchLibrary createUncachedDispatch() {
        return new UncachedDispatch();
    }

    @SuppressWarnings("unchecked")
    private static Class<DynamicDispatchLibrary> lazyLibraryClass() {
        try {
            return (Class<DynamicDispatchLibrary>) Class.forName("com.oracle.truffle.api.library.DynamicDispatchLibrary", false, DynamicDispatchLibraryGen.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
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
            public boolean isAdoptable() {
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
            super(DynamicDispatchLibraryGen.LIBRARY_CLASS, name, returnType, parameters);
            this.index = index;
        }

    }

    @GeneratedBy(DynamicDispatchLibrary.class)
    private static final class Proxy extends DynamicDispatchLibrary {

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
                return (Class<?>) lib.send(receiver_, DynamicDispatchLibraryGen.DISPATCH);
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

        @TruffleBoundary
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

        @TruffleBoundary
        @Override
        public Class<?> dispatch(Object receiver_) {
            return INSTANCE.getUncached(receiver_).dispatch(receiver_);
        }

        @TruffleBoundary
        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

        @Override
        public Object cast(Object receiver) {
            return receiver;
        }

        @Override
        public boolean isAdoptable() {
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

        @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
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
