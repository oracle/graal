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

import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.function.Supplier;

/**
 * @since 0.8 or earlier
 * @deprecated Use {@link InteropLibrary} instead.
 */
@Deprecated
@SuppressWarnings({"deprecation", "unused"})
public final class ForeignAccess {
    private final Factory factory;
    private final Supplier<? extends RootNode> languageCheckSupplier;

    // still here for GraalVM intrinsics.
    @SuppressWarnings("unused") private final Thread initThread;

    static final boolean LEGACY_TO_LIBRARY_BRIDGE = true;

    private ForeignAccess(Factory faf) {
        this(null, faf);
    }

    private ForeignAccess(Supplier<? extends RootNode> languageCheckSupplier, Factory faf) {
        this.factory = faf;
        this.initThread = null;
        this.languageCheckSupplier = languageCheckSupplier;
        CompilerAsserts.neverPartOfCompilation("do not create a ForeignAccess object from compiled code");
    }

    /**
     * @since 0.30
     * @deprecated use <code>@{@linkplain ExportLibrary}(InteropLibrary.class)</code> on the
     *             receiver type instead to export interop messages.
     */
    @Deprecated
    public static ForeignAccess create(final Class<? extends TruffleObject> baseClass, final StandardFactory factory) {
        if (baseClass == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(new DelegatingFactory(baseClass, factory));
    }

    /**
     * @since 0.30
     * @deprecated use <code>@{@linkplain ExportLibrary}(InteropLibrary.class)</code> on the
     *             receiver type instead to export interop messages.
     */
    @Deprecated
    public static ForeignAccess create(final StandardFactory factory, final RootNode languageCheck) {
        if (languageCheck == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(
                        languageCheck == null ? null : new RootNodeSupplier(languageCheck),
                        new DelegatingFactory(null, factory));
    }

    /**
     * @since 19.0
     */
    public static ForeignAccess createAccess(final StandardFactory factory, final Supplier<? extends RootNode> languageCheckSupplier) {
        if (languageCheckSupplier == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(languageCheckSupplier, new DelegatingFactory(null, factory));
    }

    /**
     * @since 0.8 or earlier
     * @deprecated use <code>@{@linkplain ExportLibrary}(InteropLibrary.class)</code> on the
     *             receiver type instead to export interop messages.
     */
    @Deprecated
    public static ForeignAccess create(Factory factory) {
        return new ForeignAccess(factory);
    }

    /**
     * @since 0.24
     * @deprecated use the specific {@link InteropLibrary} or use library {@link ReflectionLibrary
     *             reflection} instead.
     */
    @Deprecated
    public static Object send(Node foreignNode, TruffleObject receiver, Object... arguments) throws InteropException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE && foreignNode instanceof LegacyToLibraryNode) {
                return ((LegacyToLibraryNode) foreignNode).send(receiver, arguments);
            } else {
                return ((InteropAccessNode) foreignNode).execute(receiver, arguments);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#readMember(Object, String)} or
     *             {@link InteropLibrary#readArrayElement(Object, long)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendRead(Node readNode, TruffleObject receiver, Object identifier) throws UnknownIdentifierException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) readNode).message == Message.READ;
                return ((LegacyToLibraryNode) readNode).sendRead(receiver, identifier);
            } else {
                return ((InteropAccessNode) readNode).execute(receiver, identifier);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#writeMember(Object, String, Object)} or
     *             {@link InteropLibrary#writeArrayElement(Object, long, Object)} instead.
     */
    @Deprecated
    public static Object sendWrite(Node writeNode, TruffleObject receiver, Object identifier, Object value)
                    throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) writeNode).message == Message.WRITE;
                ((LegacyToLibraryNode) writeNode).sendWrite(receiver, identifier, value);
                return value;
            } else {
                return ((InteropAccessNode) writeNode).execute(receiver, identifier, value);
            }
        } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.32
     * @deprecated use {@link InteropLibrary#removeMember(Object, String)} or
     *             {@link InteropLibrary#removeArrayElement(Object, long)} instead.
     */
    @Deprecated
    public static boolean sendRemove(Node removeNode, TruffleObject receiver, Object identifier)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) removeNode).message == Message.REMOVE;
                return ((LegacyToLibraryNode) removeNode).sendRemove(receiver, identifier);
            } else {
                return (boolean) ((InteropAccessNode) removeNode).execute(receiver, identifier);
            }
        } catch (UnsupportedTypeException e) {
            // necessary for legacy support
            throw UnsupportedMessageException.create();
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#asString(Object)},
     *             {@link InteropLibrary#asBoolean(Object)}, {@link InteropLibrary#asByte(Object)},
     *             {@link InteropLibrary#asShort(Object)}, {@link InteropLibrary#asInt(Object)},
     *             {@link InteropLibrary#asLong(Object)}, {@link InteropLibrary#asFloat(Object)} or
     *             {@link InteropLibrary#asDouble(Object)} instead. See {@link InteropLibrary} for
     *             an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendUnbox(Node unboxNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) unboxNode).message == Message.UNBOX;
                return ((LegacyToLibraryNode) unboxNode).sendUnbox(receiver);
            } else {
                return ((InteropAccessNode) unboxNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.26
     * @deprecated use {@link InteropLibrary#isPointer(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsPointer(Node isPointerNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) isPointerNode).message == Message.IS_POINTER;
                return ((LegacyToLibraryNode) isPointerNode).sendIsPointer(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isPointerNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.26
     * @deprecated use {@link InteropLibrary#asPointer(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static long sendAsPointer(Node asPointerNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) asPointerNode).message == Message.AS_POINTER;
                return ((LegacyToLibraryNode) asPointerNode).sendAsPointer(receiver);
            } else {
                return (long) ((InteropAccessNode) asPointerNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.26
     * @deprecated use {@link InteropLibrary#toNative(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendToNative(Node toNativeNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) toNativeNode).message == Message.TO_NATIVE;
                return ((LegacyToLibraryNode) toNativeNode).sendToNative(receiver);
            } else {
                return ((InteropAccessNode) toNativeNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#execute(Object, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendExecute(Node executeNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) executeNode).message == Message.EXECUTE;
                return ((LegacyToLibraryNode) executeNode).sendExecute(receiver, arguments);
            } else {
                return ((InteropAccessNode) executeNode).execute(receiver, arguments);
            }
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#isExecutable(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsExecutable(Node isExecutableNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) isExecutableNode).sendIsExecutable(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isExecutableNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.30
     * @deprecated use {@link InteropLibrary#isInstantiable(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsInstantiable(Node isInstantiableNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) isInstantiableNode).sendIsInstantiable(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isInstantiableNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#invokeMember(Object, String, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendInvoke(Node invokeNode, TruffleObject receiver, String identifier, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnknownIdentifierException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) invokeNode).sendInvoke(receiver, identifier, arguments);
            } else {
                return ((InteropAccessNode) invokeNode).execute(receiver, identifier, arguments);
            }
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#instantiate(Object, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendNew(Node newNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) newNode).message == Message.NEW;
                return ((LegacyToLibraryNode) newNode).sendNew(receiver, arguments);
            } else {
                return ((InteropAccessNode) newNode).execute(receiver, arguments);
            }
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#isNull(Object)} instead.
     */
    @Deprecated
    public static boolean sendIsNull(Node isNullNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) isNullNode).message == Message.IS_NULL;
                return ((LegacyToLibraryNode) isNullNode).sendIsNull(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isNullNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#hasArrayElements(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendHasSize(Node hasSizeNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) hasSizeNode).message == Message.HAS_SIZE;
                return ((LegacyToLibraryNode) hasSizeNode).sendHasSize(receiver);
            } else {
                return (boolean) ((InteropAccessNode) hasSizeNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#getArraySize(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendGetSize(Node getSizeNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) getSizeNode).message == Message.GET_SIZE;
                return ((LegacyToLibraryNode) getSizeNode).sendGetSize(receiver);
            } else {
                return ((InteropAccessNode) getSizeNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#isString(Object)},
     *             {@link InteropLibrary#isBoolean(Object)} or
     *             {@link InteropLibrary#isNumber(Object)} instead. See {@link InteropLibrary} for
     *             an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsBoxed(Node isBoxedNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) isBoxedNode).message == Message.IS_BOXED;
                return ((LegacyToLibraryNode) isBoxedNode).sendIsBoxed(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isBoxedNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.26
     * @deprecated for {@link InteropLibrary#hasMembers(Object) objects} use
     *             {@link InteropLibrary#isMemberReadable(Object, String)},
     *             {@link InteropLibrary#isMemberWritable(Object, String)},
     *             {@link InteropLibrary#isMemberInsertable(Object, String)},
     *             {@link InteropLibrary#isMemberRemovable(Object, String)} or
     *             {@link InteropLibrary#isMemberInternal(Object, String)} instead. For
     *             {@link InteropLibrary#hasArrayElements(Object) arras} use
     *             {@link InteropLibrary#isArrayElementReadable(Object, long)},
     *             {@link InteropLibrary#isArrayElementWritable(Object, long)},
     *             {@link InteropLibrary#isArrayElementInsertable(Object, long)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static int sendKeyInfo(Node keyInfoNode, TruffleObject receiver, Object identifier) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) keyInfoNode).message == Message.KEY_INFO;
                return ((LegacyToLibraryNode) keyInfoNode).sendKeyInfo(receiver, identifier);
            } else {
                return (Integer) send(keyInfoNode, receiver, identifier);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            try {
                TruffleObject keys = sendKeys(Message.KEYS.createNode(), receiver, true);
                Number sizeNumber = (Number) sendGetSize(Message.GET_SIZE.createNode(), keys);
                int size = sizeNumber.intValue();
                Node readNode = Message.READ.createNode();
                for (int i = 0; i < size; i++) {
                    Object key = sendRead(readNode, keys, i);
                    // identifier must not be null
                    if (identifier.equals(key)) {
                        return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
                    }
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException uex) {
            }
            try {
                boolean hasSize = sendHasSize(Message.HAS_SIZE.createNode(), receiver);
                if (hasSize && identifier instanceof Number) {
                    int id = ((Number) identifier).intValue();
                    if (id < 0 || id != ((Number) identifier).doubleValue()) {
                        // identifier is some wild double number
                        return 0;
                    }
                    Number sizeNumber = (Number) sendGetSize(Message.GET_SIZE.createNode(), receiver);
                    int size = sizeNumber.intValue();
                    if (id < size) {
                        return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
                    }
                }
            } catch (UnsupportedMessageException uex) {
            }
            return 0;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.30
     * @deprecated use {@link InteropLibrary#hasMembers(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static boolean sendHasKeys(Node hasKeysNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) hasKeysNode).message == Message.HAS_KEYS;
                return ((LegacyToLibraryNode) hasKeysNode).sendHasKeys(receiver);
            } else {
                return (boolean) ((InteropAccessNode) hasKeysNode).execute(receiver);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            try {
                sendKeys(Message.KEYS.createNode(), receiver, true);
                return true;
            } catch (UnsupportedMessageException uex) {
                return false;
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.24
     * @deprecated use {@link InteropLibrary#getMembers(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static TruffleObject sendKeys(Node keysNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) keysNode).message == Message.KEYS;
                return ((LegacyToLibraryNode) keysNode).sendKeys(receiver);
            } else {
                return (TruffleObject) send(keysNode, receiver);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.26
     * @deprecated use {@link InteropLibrary#getMembers(Object, boolean)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static TruffleObject sendKeys(Node keysNode, TruffleObject receiver, boolean includeInternal) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                assert ((LegacyToLibraryNode) keysNode).message == Message.KEYS;
                return ((LegacyToLibraryNode) keysNode).sendKeys(receiver, includeInternal);
            } else {
                return (TruffleObject) send(keysNode, receiver, includeInternal);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * @since 0.11
     * @deprecated without replacement. There is no longer any frame involved for interop calls.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public static List<Object> getArguments(Frame frame) {
        final Object[] arr = frame.getArguments();
        return com.oracle.truffle.api.interop.impl.ReadOnlyArrayList.asList(arr, 1, arr.length);
    }

    /**
     * @since 0.8 or earlier
     * @deprecated without replacement. There is no longer any frame involved for interop calls.
     */
    @Deprecated
    public static TruffleObject getReceiver(Frame frame) {
        return (TruffleObject) frame.getArguments()[InteropAccessNode.ARG0_RECEIVER];
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        Object f;
        if (factory instanceof DelegatingFactory) {
            f = ((DelegatingFactory) factory).factory;
        } else {
            f = factory;
        }
        return "ForeignAccess[" + f.getClass().getName() + "]";
    }

    @SuppressWarnings("deprecation")
    CallTarget access(Message message) {
        return factory.accessMessage(message);
    }

    CallTarget checkLanguage() {
        if (languageCheckSupplier != null) {
            RootNode languageCheck = languageCheckSupplier.get();
            return Truffle.getRuntime().createCallTarget(languageCheck);
        } else {
            return null;
        }
    }

    boolean canHandle(TruffleObject receiver) {
        return factory.canHandle(receiver);
    }

    /**
     *
     * @since 0.8 or earlier
     * @deprecated use {@link ExportLibrary} instead to export message implementations.
     */
    @Deprecated
    public interface Factory {

        /**
         * @since 0.8 or earlier
         */
        boolean canHandle(TruffleObject obj);

        /**
         * @since 0.8 or earlier
         */
        CallTarget accessMessage(Message tree);
    }

    /**
     * @since 0.30
     * @deprecated use {@link ExportLibrary} instead to export message implementations.
     */
    @Deprecated
    public interface StandardFactory {
        /**
         *
         * @since 0.30
         */
        default CallTarget accessIsNull() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessIsExecutable() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessIsInstantiable() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessIsBoxed() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessHasSize() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessGetSize() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessUnbox() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessRead() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessWrite() {
            return null;
        }

        /**
         *
         * @since 0.32
         */
        default CallTarget accessRemove() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessExecute(int argumentsLength) {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessInvoke(int argumentsLength) {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessNew(int argumentsLength) {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessHasKeys() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessKeys() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessKeyInfo() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessIsPointer() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessAsPointer() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessToNative() {
            return null;
        }

        /**
         *
         * @since 0.30
         */
        default CallTarget accessMessage(Message unknown) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static class DelegatingFactory implements Factory {
        private final Class<?> baseClass;
        private final StandardFactory factory;

        DelegatingFactory(Class<?> baseClass, StandardFactory factory) {
            this.baseClass = baseClass;
            this.factory = factory;
        }

        @Override
        public boolean canHandle(TruffleObject obj) {
            if (baseClass == null) {
                return ((Factory) factory).canHandle(obj);
            }
            return baseClass.isInstance(obj);
        }

        @Override
        public CallTarget accessMessage(Message msg) {
            return accessMessage(factory, msg);
        }

        private static CallTarget accessMessage(StandardFactory factory, Message msg) {
            if (msg instanceof KnownMessage) {
                switch (msg.hashCode()) {
                    case Execute.HASH:
                        return factory.accessExecute(0);
                    case Invoke.HASH:
                        return factory.accessInvoke(0);
                    case New.HASH:
                        return factory.accessNew(0);
                    case GetSize.HASH:
                        return factory.accessGetSize();
                    case HasKeys.HASH:
                        return factory.accessHasKeys();
                    case HasSize.HASH:
                        return factory.accessHasSize();
                    case IsBoxed.HASH:
                        return factory.accessIsBoxed();
                    case IsExecutable.HASH:
                        return factory.accessIsExecutable();
                    case IsInstantiable.HASH:
                        return factory.accessIsInstantiable();
                    case IsNull.HASH:
                        return factory.accessIsNull();
                    case Read.HASH:
                        return factory.accessRead();
                    case Unbox.HASH:
                        return factory.accessUnbox();
                    case Write.HASH:
                        return factory.accessWrite();
                    case Remove.HASH:
                        return factory.accessRemove();
                    case Keys.HASH:
                        return factory.accessKeys();
                    case KeyInfoMsg.HASH:
                        return factory.accessKeyInfo();
                    case IsPointer.HASH:
                        return factory.accessIsPointer();
                    case AsPointer.HASH:
                        return factory.accessAsPointer();
                    case ToNative.HASH:
                        return factory.accessToNative();
                }
            }
            return factory.accessMessage(msg);
        }
    }

    private static final class RootNodeSupplier implements Supplier<RootNode> {
        private final RootNode rootNode;

        RootNodeSupplier(RootNode rootNode) {
            assert rootNode != null : "The rootNode must be non null.";
            this.rootNode = rootNode;
        }

        @Override
        public RootNode get() {
            return (RootNode) rootNode.deepCopy();
        }
    }
}
