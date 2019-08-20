/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;

/**
 *
 * @since 0.8 or earlier
 * @deprecated message is no longer needed to send interop messages. See {@link InteropLibrary} for
 *             details. For a reflective message representation see
 *             {@link com.oracle.truffle.api.library.Message} instead.
 */
@Deprecated
@SuppressWarnings("deprecation")
public abstract class Message {
    /**
     * @since 0.8 or earlier
     */
    protected Message() {
        registerClass(this);
    }

    /**
     * @since 0.8 or earlier
     * @deprecated use {@link InteropLibrary#readMember(Object, String)} or
     *             {@link InteropLibrary#readArrayElement(Object, long)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message READ = Read.INSTANCE;

    /**
     *
     * @since 0.8 or earlier
     * @deprecated use {@link InteropLibrary#asString(Object)},
     *             {@link InteropLibrary#asBoolean(Object)}, {@link InteropLibrary#asByte(Object)},
     *             {@link InteropLibrary#asShort(Object)}, {@link InteropLibrary#asInt(Object)},
     *             {@link InteropLibrary#asLong(Object)}, {@link InteropLibrary#asFloat(Object)} or
     *             {@link InteropLibrary#asDouble(Object)} instead. See {@link InteropLibrary} for
     *             an overview of the new interop messages.
     */
    @Deprecated public static final Message UNBOX = Unbox.INSTANCE;

    /**
     * @since 0.8 or earlier
     * @deprecated use {@link InteropLibrary#writeMember(Object, String, Object)} or
     *             {@link InteropLibrary#writeArrayElement(Object, long, Object)} instead.
     */
    @Deprecated public static final Message WRITE = Write.INSTANCE;

    /**
     * @since 0.32
     * @deprecated use {@link InteropLibrary#removeMember(Object, String)} or
     *             {@link InteropLibrary#removeArrayElement(Object, long)} instead.
     */
    @Deprecated public static final Message REMOVE = Remove.INSTANCE;

    /**
     * @since 19.0
     * @deprecated use {@link InteropLibrary#execute(Object, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message EXECUTE = Execute.INSTANCE;

    /**
     * Use {@link Message#EXECUTE} instead.
     *
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public static Message createExecute(@SuppressWarnings("unused") int argumentsLength) {
        return EXECUTE;
    }

    /**
     * @since 0.8 or earlier
     * @deprecated use {@link InteropLibrary#isExecutable(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message IS_EXECUTABLE = IsExecutable.INSTANCE;

    /**
     * @since 0.30
     * @deprecated use {@link InteropLibrary#isInstantiable(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message IS_INSTANTIABLE = IsInstantiable.INSTANCE;

    /**
     * @since 19.0
     * @deprecated use {@link InteropLibrary#invokeMember(Object, String, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message INVOKE = Invoke.INSTANCE;

    /**
     * Use {@link Message#INVOKE} instead.
     *
     * @since 0.8 or earlier
     */
    @Deprecated
    public static Message createInvoke(@SuppressWarnings("unused") int argumentsLength) {
        return INVOKE;
    }

    /**
     *
     * @since 19.0
     * @deprecated use {@link InteropLibrary#instantiate(Object, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message NEW = New.INSTANCE;

    /**
     * Use {@link Message#NEW} instead.
     *
     * @since 0.8 or earlier
     */
    @Deprecated
    public static Message createNew(@SuppressWarnings("unused") int argumentsLength) {
        return NEW;
    }

    /**
     * @since 0.8 or earlier
     * @deprecated use {@link InteropLibrary#isNull(Object)} instead.
     */
    @Deprecated public static final Message IS_NULL = IsNull.INSTANCE;

    /**
     * @deprecated use {@link InteropLibrary#hasArrayElements(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     * @since 0.8 or earlier
     */
    @Deprecated public static final Message HAS_SIZE = HasSize.INSTANCE;

    /**
     * @since 0.8 or earlier
     * @deprecated use {@link InteropLibrary#getArraySize(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message GET_SIZE = GetSize.INSTANCE;

    /**
     *
     * @since 0.8 or earlier
     * @deprecated use {@link InteropLibrary#isString(Object)},
     *             {@link InteropLibrary#isBoolean(Object)} or
     *             {@link InteropLibrary#isNumber(Object)} instead. See {@link InteropLibrary} for
     *             an overview of the new interop messages.
     */
    @Deprecated public static final Message IS_BOXED = IsBoxed.INSTANCE;

    /**
     *
     * @since 0.26
     * @deprecated for {@link InteropLibrary#hasMembers(Object) objects} use
     *             {@link InteropLibrary#isMemberReadable(Object, String)},
     *             {@link InteropLibrary#isMemberWritable(Object, String)},
     *             {@link InteropLibrary#isMemberInsertable(Object, String)},
     *             {@link InteropLibrary#isMemberRemovable(Object, String)} or
     *             {@link InteropLibrary#isMemberInternal(Object, String)} instead. For
     *             {@link InteropLibrary#hasArrayElements(Object) arrays} use
     *             {@link InteropLibrary#isArrayElementReadable(Object, long)},
     *             {@link InteropLibrary#isArrayElementWritable(Object, long)},
     *             {@link InteropLibrary#isArrayElementInsertable(Object, long)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated public static final Message KEY_INFO = KeyInfoMsg.INSTANCE;

    /**
     * @since 0.30
     * @deprecated use {@link InteropLibrary#hasMembers(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated public static final Message HAS_KEYS = HasKeys.INSTANCE;

    /**
     * @since 0.18
     * @deprecated use {@link InteropLibrary#getMembers(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated public static final Message KEYS = Keys.INSTANCE;

    /**
     * @since 0.26 or earlier
     * @deprecated use {@link InteropLibrary#isPointer(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated public static final Message IS_POINTER = IsPointer.INSTANCE;

    /**
     *
     * @since 0.26 or earlier
     * @deprecated use {@link InteropLibrary#asPointer(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated public static final Message AS_POINTER = AsPointer.INSTANCE;

    /**
     *
     * @since 0.26 or earlier
     * @deprecated use {@link InteropLibrary#toNative(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated public static final Message TO_NATIVE = ToNative.INSTANCE;

    /**
     * @since 0.8 or earlier
     */
    @Override
    public abstract boolean equals(Object message);

    /**
     * When re-implementing {@link #equals(java.lang.Object)}, it is generally recommended to also
     * implement <code>hashCode()</code>.
     *
     * @return hash code
     * @since 0.8 or earlier
     */
    @Override
    public abstract int hashCode();

    /**
     *
     * @since 0.8 or earlier
     * @deprecated use {@link LibraryFactory#create(Object)} or
     *             {@link LibraryFactory#getUncached(Object)} instead. See {@link InteropLibrary}
     *             for examples.
     */
    @Deprecated
    @SuppressWarnings("all")
    public final Node createNode() {
        CompilerAsserts.neverPartOfCompilation();
        if (ForeignAccess.LEGACY_TO_LIBRARY_BRIDGE && this instanceof KnownMessage) {
            return LegacyToLibraryNode.create(this);
        } else {
            return InteropAccessNode.create(this);
        }
    }

    /**
     * @param message the message to convert
     * @return canonical string representation
     * @since 0.9
     * @deprecated use {@link com.oracle.truffle.api.library.Message#getSimpleName()} instead.
     */
    @Deprecated
    public static String toString(com.oracle.truffle.api.interop.Message message) {
        if (com.oracle.truffle.api.interop.Message.READ == message) {
            return "READ"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.WRITE == message) {
            return "WRITE"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.REMOVE == message) {
            return "REMOVE"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.UNBOX == message) {
            return "UNBOX"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.GET_SIZE == message) {
            return "GET_SIZE"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.HAS_SIZE == message) {
            return "HAS_SIZE"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.IS_NULL == message) {
            return "IS_NULL"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.IS_BOXED == message) {
            return "IS_BOXED"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.IS_EXECUTABLE == message) {
            return "IS_EXECUTABLE"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.IS_INSTANTIABLE == message) {
            return "IS_INSTANTIABLE"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.HAS_KEYS == message) {
            return "HAS_KEYS"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.KEYS == message) {
            return "KEYS"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.KEY_INFO == message) {
            return "KEY_INFO"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.IS_POINTER == message) {
            return "IS_POINTER"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.AS_POINTER == message) {
            return "AS_POINTER"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Message.TO_NATIVE == message) {
            return "TO_NATIVE"; // NOI18N
        }
        if (com.oracle.truffle.api.interop.Execute.INSTANCE == message) {
            return "EXECUTE";
        }
        if (com.oracle.truffle.api.interop.Invoke.INSTANCE == message) {
            return "INVOKE";
        }
        if (com.oracle.truffle.api.interop.New.INSTANCE == message) {
            return "NEW";
        }
        return message.getClass().getName();
    }

    /**
     * @param messageId canonical string representation of a message
     * @return the message
     * @throws IllegalArgumentException if the string does not represent known message
     * @since 0.9
     */
    public static Message valueOf(String messageId) {
        switch (messageId) {
            case "READ":
                return Message.READ;
            case "WRITE":
                return Message.WRITE;
            case "REMOVE":
                return Message.REMOVE;
            case "UNBOX":
                return Message.UNBOX;
            case "GET_SIZE":
                return Message.GET_SIZE;
            case "HAS_SIZE":
                return Message.HAS_SIZE;
            case "IS_NULL":
                return Message.IS_NULL;
            case "IS_BOXED":
                return Message.IS_BOXED;
            case "IS_EXECUTABLE":
                return Message.IS_EXECUTABLE;
            case "IS_INSTANTIABLE":
                return Message.IS_INSTANTIABLE;
            case "HAS_KEYS":
                return Message.HAS_KEYS;
            case "KEYS":
                return Message.KEYS;
            case "KEY_INFO":
                return Message.KEY_INFO;
            case "IS_POINTER":
                return Message.IS_POINTER;
            case "AS_POINTER":
                return Message.AS_POINTER;
            case "TO_NATIVE":
                return Message.TO_NATIVE;
            case "EXECUTE":
                return Message.EXECUTE;
            case "NEW":
                return Message.NEW;
            case "INVOKE":
                return Message.INVOKE;
        }
        if (!TruffleOptions.AOT) {
            initializeMessageClass(messageId);
        }
        Message instance = CLASS_TO_MESSAGE.get(messageId);
        if (instance == null) {
            throw new IllegalArgumentException("Cannot find existing message instance for " + messageId);
        }
        return instance;
    }

    @CompilerDirectives.TruffleBoundary
    private static void initializeMessageClass(String message) throws IllegalArgumentException {
        try {
            ClassLoader l = Message.class.getClassLoader();
            if (l == null) {
                l = ClassLoader.getSystemClassLoader();
            }
            Class.forName(message, false, l).getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot find message for " + message, ex);
        }
    }

    InteropAccessNode uncached;

    private static final Map<String, Message> CLASS_TO_MESSAGE = new ConcurrentHashMap<>();

    /**
     * Resets the state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        CLASS_TO_MESSAGE.clear();
    }

    @CompilerDirectives.TruffleBoundary
    private static void registerClass(Message message) {
        if (message instanceof KnownMessage) {
            return;
        }
        final String key = message.getClass().getName();
        CLASS_TO_MESSAGE.putIfAbsent(key, message);
    }
}
