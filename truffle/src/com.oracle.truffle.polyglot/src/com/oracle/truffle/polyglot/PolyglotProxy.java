/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.GuestToHostRootNode.createGuestToHost;
import static com.oracle.truffle.polyglot.GuestToHostRootNode.guestToHostCall;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressWarnings("deprecation")
@ExportLibrary(InteropLibrary.class)
final class PolyglotProxy implements TruffleObject {

    static final int LIMIT = 5;

    private static final ProxyArray EMPTY = new ProxyArray() {

        public void set(long index, Value value) {
            throw new ArrayIndexOutOfBoundsException();
        }

        public long getSize() {
            return 0;
        }

        public Object get(long index) {
            throw new ArrayIndexOutOfBoundsException();
        }
    };
    final PolyglotLanguageContext languageContext;
    final Proxy proxy;

    PolyglotProxy(PolyglotLanguageContext context, Proxy proxy) {
        this.languageContext = context;
        this.proxy = proxy;
    }

    @ExportMessage
    boolean isInstantiable() {
        return proxy instanceof ProxyInstantiable;
    }

    private static final CallTarget INSTANTIATE = createGuestToHost(new GuestToHostRootNode(ProxyInstantiable.class, "newInstance") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyInstantiable) proxy).newInstance((Value[]) arguments[ARGUMENT_OFFSET]);
        }
    });

    @ExportMessage
    @TruffleBoundary
    Object instantiate(Object[] arguments, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyInstantiable) {
            Value[] convertedArguments = languageContext.toHostValues(arguments, 0);
            Object result = guestToHostCall(library, INSTANTIATE, languageContext, proxy, convertedArguments);
            return languageContext.toGuestValue(result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isExecutable() {
        return proxy instanceof ProxyExecutable;
    }

    private static final CallTarget EXECUTE = createGuestToHost(new GuestToHostRootNode(ProxyExecutable.class, "execute") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyExecutable) proxy).execute((Value[]) arguments[ARGUMENT_OFFSET]);
        }
    });

    @ExportMessage
    @TruffleBoundary
    Object execute(Object[] arguments, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyExecutable) {
            Value[] convertedArguments = languageContext.toHostValues(arguments, 0);
            Object result = guestToHostCall(library, EXECUTE, languageContext, proxy, convertedArguments);
            return languageContext.toGuestValue(result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isPointer() {
        return proxy instanceof ProxyNativeObject;
    }

    private static final CallTarget AS_POINTER = createGuestToHost(new GuestToHostRootNode(ProxyNativeObject.class, "asPointer") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyNativeObject) proxy).asPointer();
        }
    });

    @ExportMessage
    @TruffleBoundary
    long asPointer(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyNativeObject) {
            return (long) guestToHostCall(library, AS_POINTER, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean hasArrayElements() {
        return proxy instanceof ProxyArray;
    }

    private static final CallTarget ARRAY_GET = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "get") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                return ((ProxyArray) proxy).get(index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    });

    @ExportMessage
    @TruffleBoundary
    Object readElement(long index, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            Object result = guestToHostCall(library, ARRAY_GET, languageContext, proxy, index);
            return languageContext.toGuestValue(result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    private static final CallTarget ARRAY_SET = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "set") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                ((ProxyArray) proxy).set(index, (Value) arguments[ARGUMENT_OFFSET + 1]);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            return null;
        }
    });

    @ExportMessage
    @TruffleBoundary
    void writeElement(long index, Object value, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            Value castValue = languageContext.asValue(value);
            guestToHostCall(library, ARRAY_SET, languageContext, proxy, index, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    private static final CallTarget ARRAY_REMOVE = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "remove") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InteropException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                return ((ProxyArray) proxy).remove(index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    });

    @ExportMessage
    @TruffleBoundary
    void removeElement(long index, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (proxy instanceof ProxyArray) {
            boolean result = (boolean) guestToHostCall(library, ARRAY_REMOVE, languageContext, proxy, index);
            if (!result) {
                throw InvalidArrayIndexException.create(index);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    private static final CallTarget ARRAY_SIZE = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "getSize") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyArray) proxy).getSize();
        }
    });

    @ExportMessage
    @TruffleBoundary
    long getArraySize(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            return (long) guestToHostCall(library, ARRAY_SIZE, languageContext, proxy);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isElementReadable")
    @ExportMessage(name = "isElementModifiable")
    @ExportMessage(name = "isElementRemovable")
    @TruffleBoundary
    boolean isElementExisting(long index, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyArray) {
            long size = (long) guestToHostCall(library, ARRAY_SIZE, languageContext, proxy);
            return index >= 0 && index < size;
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isElementInsertable(long index, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyArray) {
            long size = (long) guestToHostCall(library, ARRAY_SIZE, languageContext, proxy);
            return index < 0 || index >= size;
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return proxy instanceof ProxyObject;
    }

    private static final CallTarget MEMBER_KEYS = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "getMemberKeys") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyObject) proxy).getMemberKeys();
        }
    });

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            Object result = guestToHostCall(library, MEMBER_KEYS, languageContext, proxy);
            if (result == null) {
                result = EMPTY;
            }
            Object guestValue = languageContext.toGuestValue(result);
            if (!InteropLibrary.resolve().getUncachedDispatch().hasArrayElements(guestValue)) {
                throw PolyglotImpl.wrapHostException(languageContext, new IllegalStateException(
                                String.format("getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                                                languageContext.asValue(guestValue).toString())));
            }
            return guestValue;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    private static final CallTarget GET_MEMBER = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "getMember") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return ((ProxyObject) proxy).getMember((String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    });

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library)) {
                throw UnknownIdentifierException.create(member);
            }
            Object result = guestToHostCall(library, GET_MEMBER, languageContext, proxy, member);
            return languageContext.toGuestValue(result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    private static final CallTarget PUT_MEMBER = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "putMember") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                ((ProxyObject) proxy).putMember((String) arguments[ARGUMENT_OFFSET], (Value) arguments[ARGUMENT_OFFSET + 1]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            return null;
        }
    });

    @ExportMessage
    @TruffleBoundary
    void writeMember(String member, Object value, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            Value castValue = languageContext.asValue(value);
            guestToHostCall(library, PUT_MEMBER, languageContext, proxy, member, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object invokeMember(String member, Object[] arguments, @CachedLibrary("this") InteropLibrary library,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library)) {
                throw UnknownIdentifierException.create(member);
            }
            Object memberObject;
            try {
                memberObject = readMember(member, library);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            memberObject = languageContext.toGuestValue(memberObject);
            if (executables.isExecutable(memberObject)) {
                return executables.execute(memberObject, arguments);
            } else {
                throw UnsupportedMessageException.create();
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInvokable(String member, @CachedLibrary("this") InteropLibrary library,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables) {
        if (proxy instanceof ProxyObject) {
            if (isMemberExisting(member, library)) {
                try {
                    return executables.isExecutable(readMember(member, library));
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    return false;
                }
            }
        }
        return false;
    }

    private static final CallTarget REMOVE_MEMBER = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "removeMember") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return ((ProxyObject) proxy).removeMember((String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    });

    @ExportMessage
    @TruffleBoundary
    void removeMember(String member, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library)) {
                throw UnknownIdentifierException.create(member);
            }
            boolean result = (boolean) guestToHostCall(library, REMOVE_MEMBER, languageContext, proxy, member);
            if (!result) {
                throw UnknownIdentifierException.create(member);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    private static final CallTarget HAS_MEMBER = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "hasMember") {
        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyObject) proxy).hasMember((String) arguments[ARGUMENT_OFFSET]);
        }
    });

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    @TruffleBoundary
    boolean isMemberExisting(String member, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyObject) {
            return (boolean) guestToHostCall(library, HAS_MEMBER, languageContext, proxy, member);
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(String member, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyObject) {
            return !isMemberExisting(member, library);
        } else {
            return false;
        }
    }

    public static boolean isProxyGuestObject(TruffleObject value) {
        return value instanceof PolyglotProxy;
    }

    public static boolean isProxyGuestObject(Object value) {
        return value instanceof PolyglotProxy;
    }

    public static Object withContext(PolyglotLanguageContext context, Object valueReceiver) {
        return new PolyglotProxy(context, ((PolyglotProxy) valueReceiver).proxy);
    }

    public static Proxy toProxyHostObject(TruffleObject value) {
        return ((PolyglotProxy) value).proxy;
    }

    public static TruffleObject toProxyGuestObject(PolyglotLanguageContext context, Proxy receiver) {
        return new PolyglotProxy(context, receiver);
    }

}
