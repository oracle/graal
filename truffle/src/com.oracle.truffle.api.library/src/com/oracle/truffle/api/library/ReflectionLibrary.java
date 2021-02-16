/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;

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
    @SuppressWarnings("unused")
    @TruffleBoundary
    public Object send(Object receiver, Message message, Object... args) throws Exception {
        throw new AbstractMethodError();
    }

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
        static Object doSendCached(Object receiver, @SuppressWarnings("unused") Message message, Object[] args,
                        @Cached("message") Message cachedMessage,
                        @Cached("createLibrary(message, receiver)") Library cachedLibrary) throws Exception {
            return cachedMessage.getFactory().genericDispatch(cachedLibrary, receiver, cachedMessage, args, 0);
        }

        static Library createLibrary(Message message, Object receiver) {
            return message.getFactory().create(receiver);
        }

        @Specialization(replaces = "doSendCached")
        @TruffleBoundary
        static Object doSendGeneric(Object receiver, Message message, Object[] args) throws Exception {
            LibraryFactory<?> lib = message.getFactory();
            return lib.genericDispatch(lib.getUncached(receiver), receiver, message, args, 0);
        }
    }

}
