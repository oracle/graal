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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.ReflectionLibraryDefault.Send;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;

/**
 * The reflection library allows to send to and proxy messages of receivers. The reflection library
 * may be used for any receiver object. If a sent message is not implemented by the target receiver
 * then the default library export will be used, otherwise it is forwarded to the resolved exported
 * message. If the reflection library is exported by a receiver directly then the reflection
 * behavior can be customized. This allows, for example, to proxy all messages to a delegate
 * instance.
 *
 * <h3>Sending Messages</h3>
 *
 * Messages can be sent to receivers by first {@link Message#resolve(Class, String) resolving} a
 * target message. Then the message can be sent to a receiver that may implement that message. The
 * message name and class may be resolved dynamically.
 *
 * <h4>Usage example</h4>
 *
 * <pre>
 * String messageName = "isArray";
 * Message message = Message.resolve(ArrayLibrary.class, messageName);
 * Object receiver = 42;
 * try {
 *     ReflectionLibrary.getFactory().getUncached().send(receiver, message);
 * } catch (Exception e) {
 *     // handle error
 * }
 * </pre>
 *
 * <h3>Proxies</h3>
 *
 * It is possible to proxy library messages to a delegate object. To achieve that the reflection
 * library can be exported by a receiver type.
 *
 * <h4>Usage example</h4>
 *
 * <pre>
 * &#64;ExportLibrary(ReflectionLibrary.class)
 * static class AgnosticWrapper {
 *
 *     final Object delegate;
 *
 *     AgnosticWrapper(Object delegate) {
 *         this.delegate = delegate;
 *     }
 *
 *     &#64;ExportMessage
 *     final Object send(Message message, Object[] args,
 *                     &#64;CachedLibrary("this.delegate") ReflectionLibrary reflection) throws Exception {
 *         // do before
 *         Object result = reflection.send(delegate, message, args);
 *         // do after
 *         return result;
 *     }
 * }
 *
 * </pre>
 *
 * @since 19.0
 */
@GenerateLibrary
@DefaultExport(ReflectionLibraryDefault.class)
public abstract class ReflectionLibrary extends Library {

    /**
     * Constructor for generated subclasses. Subclasses of this class are generated, do not extend
     * this class directly.
     *
     * @since 19.0
     */
    protected ReflectionLibrary() {
    }

    /**
     * Sends a given message to the target receiver with the provided arguments. The provided
     * receiver and message must not be null. If the argument types don't match the expected message
     * signature then an {@link IllegalArgumentException} will be thrown.
     *
     * @since 19.0
     */
    @Abstract
    public abstract Object send(Object receiver, Message message, Object... args) throws Exception;

    private static final LibraryFactory<ReflectionLibrary> FACTORY = LibraryFactory.resolve(ReflectionLibrary.class);

    /**
     * Returns the library factory for {@link ReflectionLibrary}.
     *
     * @since 19.0
     */
    public static LibraryFactory<ReflectionLibrary> getFactory() {
        return FACTORY;
    }

}

@ExportLibrary(value = ReflectionLibrary.class, receiverType = Object.class)
final class ReflectionLibraryDefault {

    static final int LIMIT = 8;

    @ExportMessage
    static class Send {

        @Specialization(guards = {"message == cachedMessage", "cachedLibrary.accepts(receiver)"}, limit = "LIMIT")
        static Object doSendCached(Object receiver, Message message, Object[] args,
                        @Cached("message") Message cachedMessage,
                        @Cached("message.getFactory().create(receiver)") Library cachedLibrary) throws Exception {
            return message.getFactory().genericDispatch(cachedLibrary, receiver, cachedMessage, args, 0);
        }

        @Specialization(replaces = "doSendCached")
        @TruffleBoundary
        static Object doSendGeneric(Object receiver, Message message, Object[] args) throws Exception {
            LibraryFactory<?> lib = message.getFactory();
            return lib.genericDispatch(lib.getUncached(receiver), receiver, message, args, 0);
        }
    }

}

// CheckStyle: start generated
@GeneratedBy(ReflectionLibraryDefault.class)
final class ReflectionLibraryDefaultGen {

    private static final LibraryFactory<DynamicDispatchLibrary> DYNAMIC_DISPATCH_LIBRARY_ = LibraryFactory.resolve(DynamicDispatchLibrary.class);

    static {
        LibraryExport.register(ReflectionLibraryDefault.class, new ReflectionLibraryExports());
    }

    private ReflectionLibraryDefaultGen() {
    }

    @GeneratedBy(ReflectionLibraryDefault.class)
    private static final class ReflectionLibraryExports extends LibraryExport<ReflectionLibrary> {

        private ReflectionLibraryExports() {
            super(ReflectionLibrary.class, Object.class, true);
        }

        @Override
        protected ReflectionLibrary createUncached(Object receiver) {
            return new Uncached(receiver);
        }

        @Override
        protected ReflectionLibrary createCached(Object receiver) {
            return new Cached(receiver);
        }

        @GeneratedBy(ReflectionLibraryDefault.class)
        private static final class Cached extends ReflectionLibrary {

            @Child private DynamicDispatchLibrary dynamicDispatch_;
            private final Class<?> dynamicDispatchTarget_;
            @CompilationFinal private int state_;
            @CompilationFinal private int exclude_;
            @Child private SendCachedData sendCached_cache;

            Cached(Object receiver) {
                this.dynamicDispatch_ = DYNAMIC_DISPATCH_LIBRARY_.create(receiver);
                this.dynamicDispatchTarget_ = DYNAMIC_DISPATCH_LIBRARY_.getUncached(receiver).dispatch(receiver);
            }

            @Override
            public boolean accepts(Object receiver) {
                return dynamicDispatch_.accepts(receiver) && dynamicDispatch_.dispatch(receiver) == dynamicDispatchTarget_;
            }

            @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
            @Override
            public Object send(Object arg0Value, Message arg1Value, Object... arg2Value) throws Exception {
                assert getRootNode() != null : "Invalid libray usage. Cached library must be adopted by a RootNode before it is executed.";
                int state = state_;
                if (state != 0 /*
                                * is-active doSendCached(Object, Message, Object[], Message,
                                * Library) || doSendGeneric(Object, Message, Object[])
                                */) {
                    if ((state & 0b1) != 0 /*
                                            * is-active doSendCached(Object, Message, Object[],
                                            * Message, Library)
                                            */) {
                        SendCachedData s1_ = this.sendCached_cache;
                        while (s1_ != null) {
                            if ((arg1Value == s1_.cachedMessage_) && (s1_.cachedLibrary_.accepts(arg0Value))) {
                                return Send.doSendCached(arg0Value, arg1Value, arg2Value, s1_.cachedMessage_, s1_.cachedLibrary_);
                            }
                            s1_ = s1_.next_;
                        }
                    }
                    if ((state & 0b10) != 0 /* is-active doSendGeneric(Object, Message, Object[]) */) {
                        return Send.doSendGeneric(arg0Value, arg1Value, arg2Value);
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return executeAndSpecialize(arg0Value, arg1Value, arg2Value);
            }

            private Object executeAndSpecialize(Object arg0Value, Message arg1Value, Object[] arg2Value) throws Exception {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                int state = state_;
                int exclude = exclude_;
                try {
                    if ((exclude) == 0 /*
                                        * is-not-excluded doSendCached(Object, Message, Object[],
                                        * Message, Library)
                                        */) {
                        int count1_ = 0;
                        SendCachedData s1_ = this.sendCached_cache;
                        if ((state & 0b1) != 0 /*
                                                * is-active doSendCached(Object, Message, Object[],
                                                * Message, Library)
                                                */) {
                            while (s1_ != null) {
                                if ((arg1Value == s1_.cachedMessage_) && (s1_.cachedLibrary_.accepts(arg0Value))) {
                                    break;
                                }
                                s1_ = s1_.next_;
                                count1_++;
                            }
                        }
                        if (s1_ == null) {
                            Library cachedLibrary__ = super.insert((arg1Value.getFactory().create(arg0Value)));
                            // assert (arg1Value == s1_.cachedMessage_);
                            if ((cachedLibrary__.accepts(arg0Value)) && count1_ < (ReflectionLibraryDefault.LIMIT)) {
                                s1_ = super.insert(new SendCachedData(sendCached_cache));
                                s1_.cachedMessage_ = (arg1Value);
                                s1_.cachedLibrary_ = s1_.insertAccessor(cachedLibrary__);
                                this.sendCached_cache = s1_;
                                this.state_ = state = state | 0b1 /*
                                                                   * add-active doSendCached(Object,
                                                                   * Message, Object[], Message,
                                                                   * Library)
                                                                   */;
                            }
                        }
                        if (s1_ != null) {
                            lock.unlock();
                            hasLock = false;
                            return Send.doSendCached(arg0Value, arg1Value, arg2Value, s1_.cachedMessage_, s1_.cachedLibrary_);
                        }
                    }
                    this.exclude_ = exclude = exclude | 0b1 /*
                                                             * add-excluded doSendCached(Object,
                                                             * Message, Object[], Message, Library)
                                                             */;
                    this.sendCached_cache = null;
                    state = state & 0xfffffffe /*
                                                * remove-active doSendCached(Object, Message,
                                                * Object[], Message, Library)
                                                */;
                    this.state_ = state = state | 0b10 /*
                                                        * add-active doSendGeneric(Object, Message,
                                                        * Object[])
                                                        */;
                    lock.unlock();
                    hasLock = false;
                    return Send.doSendGeneric(arg0Value, arg1Value, arg2Value);
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            @Override
            public NodeCost getCost() {
                int state = state_;
                if (state == 0b0) {
                    return NodeCost.UNINITIALIZED;
                } else if ((state & (state - 1)) == 0 /* is-single-active */) {
                    SendCachedData s1_ = this.sendCached_cache;
                    if ((s1_ == null || s1_.next_ == null)) {
                        return NodeCost.MONOMORPHIC;
                    }
                }
                return NodeCost.POLYMORPHIC;
            }

            @GeneratedBy(ReflectionLibraryDefault.class)
            private static final class SendCachedData extends Node {

                @Child SendCachedData next_;
                @CompilationFinal Message cachedMessage_;
                @Child Library cachedLibrary_;

                SendCachedData(SendCachedData next_) {
                    this.next_ = next_;
                }

                @Override
                public NodeCost getCost() {
                    return NodeCost.NONE;
                }

                <T extends Node> T insertAccessor(T node) {
                    return super.insert(node);
                }

            }
        }

        @GeneratedBy(ReflectionLibraryDefault.class)
        private static final class Uncached extends ReflectionLibrary {

            @Child private DynamicDispatchLibrary dynamicDispatch_;
            private final Class<?> dynamicDispatchTarget_;

            Uncached(Object receiver) {
                this.dynamicDispatch_ = DYNAMIC_DISPATCH_LIBRARY_.getUncached(receiver);
                this.dynamicDispatchTarget_ = dynamicDispatch_.dispatch(receiver);
            }

            @Override
            public boolean accepts(Object receiver) {
                return dynamicDispatch_.accepts(receiver) && dynamicDispatch_.dispatch(receiver) == dynamicDispatchTarget_;
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
            public Object send(Object arg0Value, Message arg1Value, Object... arg2Value) throws Exception {
                return Send.doSendGeneric(arg0Value, arg1Value, arg2Value);
            }

        }
    }
}

@GeneratedBy(ReflectionLibrary.class)
final class ReflectionLibraryGen extends LibraryFactory<ReflectionLibrary> {

    private static final Class<ReflectionLibrary> LIBRARY_CLASS = ReflectionLibraryGen.lazyLibraryClass();
    private static final Message SEND = new MessageImpl("send", 0, Object.class, Object.class, Message.class, Object[].class);
    private static final ReflectionLibraryGen INSTANCE = new ReflectionLibraryGen();

    static {
        LibraryFactory.register(ReflectionLibraryGen.LIBRARY_CLASS, INSTANCE);
    }

    private ReflectionLibraryGen() {
        super(ReflectionLibraryGen.LIBRARY_CLASS, Collections.unmodifiableList(Arrays.asList(ReflectionLibraryGen.SEND)));
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
        if (messageImpl.getParameterCount() - 1 != args.length - offset) {
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
    protected ReflectionLibrary createDispatchImpl(int limit) {
        return new CachedDispatchFirst(null, null, limit);
    }

    @Override
    protected ReflectionLibrary createUncachedDispatch() {
        return new UncachedDispatch();
    }

    @SuppressWarnings("unchecked")
    private static Class<ReflectionLibrary> lazyLibraryClass() {
        try {
            return (Class<ReflectionLibrary>) Class.forName("com.oracle.truffle.api.library.ReflectionLibrary", false,
                            ReflectionLibraryGen.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @GeneratedBy(ReflectionLibrary.class)
    private static class MessageImpl extends Message {

        final int index;

        MessageImpl(String name, int index, Class<?> returnType, Class<?>... parameters) {
            super(ReflectionLibraryGen.LIBRARY_CLASS, name, returnType, parameters);
            this.index = index;
        }

    }

    @GeneratedBy(ReflectionLibrary.class)
    private static final class Proxy extends ReflectionLibrary {

        @Child private ReflectionLibrary lib;

        Proxy(ReflectionLibrary lib) {
            this.lib = lib;
        }

        @Override
        public Object send(Object receiver_, Message message, Object... args) throws Exception {
            try {
                return lib.send(receiver_, ReflectionLibraryGen.SEND, message, args);
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

        @TruffleBoundary
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

        @TruffleBoundary
        @Override
        public Object send(Object receiver_, Message message, Object... args) throws Exception {
            return INSTANCE.getUncached(receiver_).send(receiver_, message, args);
        }

        @TruffleBoundary
        @Override
        public boolean accepts(Object receiver_) {
            return true;
        }

        @Override
        public boolean isAdoptable() {
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

        @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
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
                this.library = insert(INSTANCE.create(receiver_));
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
                        this.next = insert(new CachedDispatchNext(INSTANCE.create(receiver_), next));
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

    }
}
