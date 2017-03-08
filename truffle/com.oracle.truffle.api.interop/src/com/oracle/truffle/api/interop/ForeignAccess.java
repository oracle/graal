/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.interop;

import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.impl.ReadOnlyArrayList;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Encapsulates types of access to {@link TruffleObject}. If you want to expose your own objects to
 * foreign language implementations, you need to implement {@link TruffleObject} and its
 * {@link TruffleObject#getForeignAccess()} method. To create instance of <code>ForeignAccess</code>
 * , use one of the factory methods available in this class.
 *
 * @since 0.8 or earlier
 */
public final class ForeignAccess {
    private final Factory factory;
    private final Thread initThread;
    private final RootNode languageCheck;

    private ForeignAccess(Factory faf) {
        this(null, faf);
    }

    private ForeignAccess(RootNode languageCheck, Factory faf) {
        this.factory = faf;
        this.initThread = Thread.currentThread();
        this.languageCheck = languageCheck;
        CompilerAsserts.neverPartOfCompilation("do not create a ForeignAccess object from compiled code");
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param baseClass the super class of all {@link TruffleObject}s handled by this factory (if
     *            <code>null</code> than the second interface also needs to implement
     *            {@link Factory})
     * @param factory the factory that handles access requests to {@link Message}s known as of
     *            version 0.10
     * @return new instance wrapping <code>factory</code>
     * @since 0.8 or earlier
     * @deprecated Use {@link Factory18} and
     *             {@link #create(java.lang.Class, com.oracle.truffle.api.interop.ForeignAccess.Factory18)}
     */
    @Deprecated
    public static ForeignAccess create(final Class<? extends TruffleObject> baseClass, final Factory10 factory) {
        if (baseClass == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(new DelegatingFactory10(baseClass, factory));
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param baseClass the super class of all {@link TruffleObject}s handled by this factory (if
     *            <code>null</code> than the second interface also needs to implement
     *            {@link Factory})
     * @param factory the factory that handles access requests to {@link Message}s known as of
     *            version 0.18
     * @return new instance wrapping <code>factory</code>
     * @since 0.18
     */
    public static ForeignAccess create(final Class<? extends TruffleObject> baseClass, final Factory18 factory) {
        if (baseClass == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(new DelegatingFactory18(baseClass, factory));
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param factory the factory that handles access requests to {@link Message}s known as of
     *            version 0.10
     * @param languageCheck a {@link RootNode} that performs the language check on receiver objects
     * @return new instance wrapping <code>factory</code>
     * @since 0.13
     * @deprecated Use {@link Factory18} and
     *             {@link #create(com.oracle.truffle.api.interop.ForeignAccess.Factory18, com.oracle.truffle.api.nodes.RootNode)
     *             its associated factory} method
     */
    @Deprecated
    public static ForeignAccess create(final Factory10 factory, final RootNode languageCheck) {
        return new ForeignAccess(languageCheck, new DelegatingFactory10(null, factory));
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param factory the factory that handles access requests to {@link Message}s known as of
     *            version 0.18
     * @param languageCheck a {@link RootNode} that performs the language check on receiver objects,
     *            can be <code>null</code>, but then the factory has to also implement
     *            {@link Factory} interface
     * @return new instance wrapping <code>factory</code>
     * @since 0.18
     */
    public static ForeignAccess create(final Factory18 factory, final RootNode languageCheck) {
        if (languageCheck == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(languageCheck, new DelegatingFactory18(null, factory));
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param factory the factory that handles various access requests {@link Message}s.
     * @return new instance wrapping <code>factory</code>
     * @since 0.8 or earlier
     */
    public static ForeignAccess create(Factory factory) {
        return new ForeignAccess(factory);
    }

    /**
     * Executes {@link Message#createNode() foreign node}.
     *
     * @deprecated replaced by specialized methods for sending individual messages (e.g.
     *             {@link #sendRead(Node, VirtualFrame, TruffleObject, Object)}). For sending any
     *             message use the rare {@link #send(Node, VirtualFrame, TruffleObject, Object...)}
     *             method.
     *
     * @param foreignNode the createNode created by {@link Message#createNode()}
     * @param frame the call frame
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param arguments parameters for the receiver
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws IllegalStateException if any error occurred while accessing the <code>receiver</code>
     *             object
     * @since 0.8 or earlier
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public static Object execute(Node foreignNode, VirtualFrame frame, TruffleObject receiver, Object... arguments) {
        return ((InteropAccessNode) foreignNode).executeOld(receiver, arguments);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #send(Node, TruffleObject, Object...)} instead
     */
    @Deprecated
    public static Object send(Node foreignNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver, Object... arguments) throws InteropException {
        return send(foreignNode, receiver, arguments);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendRead(Node, TruffleObject, Object)} instead
     */
    @Deprecated
    public static Object sendRead(Node readNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver, Object identifier)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        return sendRead(readNode, receiver, identifier);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendWrite(Node, TruffleObject, Object, Object)} instead
     */
    @Deprecated
    public static Object sendWrite(Node writeNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver, Object identifier, Object value)
                    throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        return sendWrite(writeNode, receiver, identifier, value);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendUnbox(Node, TruffleObject)} instead
     */
    @Deprecated
    public static Object sendUnbox(Node unboxNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver) throws UnsupportedMessageException {
        return sendUnbox(unboxNode, receiver);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendExecute(Node, TruffleObject, Object...)} instead
     */
    @Deprecated
    public static Object sendExecute(Node executeNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return sendExecute(executeNode, receiver, arguments);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendIsExecutable(Node, TruffleObject)} instead
     */
    @Deprecated
    public static boolean sendIsExecutable(Node isExecutableNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver) {
        return sendIsExecutable(isExecutableNode, receiver);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendInvoke(Node, TruffleObject, String, Object...)} instead
     */
    @Deprecated
    public static Object sendInvoke(Node invokeNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver, String identifier, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnknownIdentifierException, UnsupportedMessageException {
        return sendInvoke(invokeNode, receiver, identifier, arguments);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendNew(Node, TruffleObject, Object...)} instead
     */
    @Deprecated
    public static Object sendNew(Node newNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return sendNew(newNode, receiver, arguments);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendIsNull(Node, TruffleObject)} instead
     */
    @Deprecated
    public static boolean sendIsNull(Node isNullNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver) {
        return sendIsNull(isNullNode, receiver);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendHasSize(Node, TruffleObject)} instead
     */
    @Deprecated
    public static boolean sendHasSize(Node hasSizeNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver) {
        return sendHasSize(hasSizeNode, receiver);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendGetSize(Node, TruffleObject)} instead
     */
    @Deprecated
    public static Object sendGetSize(Node getSizeNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver) throws UnsupportedMessageException {
        return sendGetSize(getSizeNode, receiver);
    }

    /**
     * @since 0.11
     * @deprecated use {@link #sendIsBoxed(Node, TruffleObject)} instead
     */
    @Deprecated
    public static boolean sendIsBoxed(Node isBoxedNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver) {
        return sendIsBoxed(isBoxedNode, receiver);
    }

    /**
     * @since 0.18
     * @deprecated use {@link #sendKeys(Node, TruffleObject)} instead
     */
    @Deprecated
    public static TruffleObject sendKeys(Node keysNode, @SuppressWarnings("unused") VirtualFrame frame, TruffleObject receiver) throws UnsupportedMessageException {
        return sendKeys(keysNode, receiver);
    }

    /**
     * Sends a {@link Message} to the foreign receiver object by executing the
     * {@link Message#createNode() foreign node}.
     *
     * @param foreignNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param arguments parameters for the receiver
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws InteropException if any error occurred while accessing the <code>receiver</code>
     *             object
     * @since 0.24
     */
    public static Object send(Node foreignNode, TruffleObject receiver, Object... arguments) throws InteropException {
        try {
            return ((InteropAccessNode) foreignNode).execute(receiver, arguments);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    /**
     * Sends a {@link Message#READ READ message} to the foreign receiver object by executing the
     * <code> readNode </code>.
     *
     * @param readNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param identifier name of the property to be read
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>readNode</code>
     * @throws UnknownIdentifierException if the <code>receiver</code> does not allow reading a
     *             property for the given <code>identifier</code>
     * @since 0.24
     */
    public static Object sendRead(Node readNode, TruffleObject receiver, Object identifier) throws UnknownIdentifierException, UnsupportedMessageException {
        try {
            return ((InteropAccessNode) readNode).execute(receiver, identifier);
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
     * Sends a {@link Message#WRITE WRITE message} to the foreign receiver object by executing the
     * <code> writeNode </code>.
     *
     * @param writeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param identifier name of the property to be written
     * @param value value to be written
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>writeNode</code>
     * @throws UnknownIdentifierException if the <code>receiver</code> does not allow writing a
     *             property for the given <code>identifier</code>
     * @throws UnsupportedTypeException if <code>value</code> has an unsupported type
     * @since 0.24
     */
    public static Object sendWrite(Node writeNode, TruffleObject receiver, Object identifier, Object value)
                    throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        try {
            return ((InteropAccessNode) writeNode).execute(receiver, identifier, value);
        } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#UNBOX UNBOX message} to the foreign receiver object by executing the
     * <code> unboxNode </code>.
     *
     * @param unboxNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>unboxNode</code>
     * @since 0.24
     */
    public static Object sendUnbox(Node unboxNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            return ((InteropAccessNode) unboxNode).execute(receiver);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an EXECUTE {@link Message} to the foreign receiver object by executing the
     * <code> executeNode </code>.
     *
     * @param executeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign function object to receive the message passed to
     *            {@link Message#createNode()} method
     * @param arguments arguments passed to the foreign function
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedTypeException if one of element of the <code>arguments</code> has an
     *             unsupported type
     * @throws ArityException if the <code>arguments</code> array does not contain the right number
     *             of arguments for the foreign function
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>executeNode</code>
     * @since 0.24
     */
    public static Object sendExecute(Node executeNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            return ((InteropAccessNode) executeNode).execute(receiver, arguments);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_EXECUTABLE IS_EXECUTABLE message} to the foreign receiver object
     * by executing the <code> isExecutableNode </code>.
     *
     * @param isExecutableNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     */
    public static boolean sendIsExecutable(Node isExecutableNode, TruffleObject receiver) {
        try {
            return (boolean) send(isExecutableNode, receiver);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an INVOKE {@link Message} to the foreign receiver object by executing the
     * <code> invokeNode </code>.
     *
     * @param invokeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign function object to receive the message passed to
     *            {@link Message#createNode()} method
     * @param arguments arguments passed to the foreign function
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedTypeException if one of element of the <code>arguments</code> has an
     *             unsupported type
     * @throws UnknownIdentifierException if the <code>receiver</code> does not have a property for
     *             the given <code>identifier</code> that can be invoked
     * @throws ArityException if the <code>arguments</code> array does not contain the right number
     *             of arguments for the foreign function
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>invokeNode</code>
     * @since 0.24
     */
    public static Object sendInvoke(Node invokeNode, TruffleObject receiver, String identifier, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnknownIdentifierException, UnsupportedMessageException {
        try {
            return ((InteropAccessNode) invokeNode).execute(receiver, identifier, arguments);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an NEW {@link Message} to the foreign receiver object by executing the
     * <code> newNode </code>.
     *
     * @param newNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign function object to receive the message passed to
     *            {@link Message#createNode()} method
     * @param arguments arguments passed to the foreign function
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedTypeException if one of element of the <code>arguments</code> has an
     *             unsupported type
     * @throws ArityException if the <code>arguments</code> array does not contain the right number
     *             of arguments for the foreign function
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>newNode</code>
     * @since 0.24
     */
    public static Object sendNew(Node newNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            return ((InteropAccessNode) newNode).execute(receiver, arguments);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_NULL IS_NULL message} to the foreign receiver object by executing
     * the <code> isNullNode </code>.
     *
     * @param isNullNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     */
    public static boolean sendIsNull(Node isNullNode, TruffleObject receiver) {
        try {
            return (boolean) send(isNullNode, receiver);
        } catch (UnsupportedMessageException ex) {
            return false;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#HAS_SIZE HAS_SIZE message} to the foreign receiver object by
     * executing the <code> hasSizeNode </code>.
     *
     * @param hasSizeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     */
    public static boolean sendHasSize(Node hasSizeNode, TruffleObject receiver) {
        try {
            return (boolean) send(hasSizeNode, receiver);
        } catch (UnsupportedMessageException ex) {
            return false;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#GET_SIZE GET_SIZE message} to the foreign receiver object by executing
     * the <code> getSizeNode </code>.
     *
     * @param getSizeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>getSizeNode</code>
     * @since 0.24
     */
    public static Object sendGetSize(Node getSizeNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            return ((InteropAccessNode) getSizeNode).execute(receiver);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_BOXED IS_BOXED message} to the foreign receiver object by
     * executing the <code> isNullNode </code>.
     *
     * @param isBoxedNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     */
    public static boolean sendIsBoxed(Node isBoxedNode, TruffleObject receiver) {
        try {
            return (boolean) send(isBoxedNode, receiver);
        } catch (UnsupportedMessageException ex) {
            return false;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#KEYS} message to the foreign receiver object.
     *
     * @param keysNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return an instance of {@link TruffleObject} that responds to {@link Message#HAS_SIZE}
     *         and {@link Message#GET_SIZE} and its 0 to {@link Message#GET_SIZE size - 1} indexes
     *         contain {@link String} names of the properties of the <code>receiver</code> object
     * @throws UnsupportedMessageException if the message isn't handled
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     */
    public static TruffleObject sendKeys(Node keysNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            return (TruffleObject) send(keysNode, receiver);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Read only access to foreign call arguments inside of a frame.
     *
     * @param frame the frame that was called via
     *            {@link #send(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) }
     * @return read-only list of parameters passed to the frame
     * @since 0.11
     */
    public static List<Object> getArguments(Frame frame) {
        final Object[] arr = frame.getArguments();
        return ReadOnlyArrayList.asList(arr, 1, arr.length);
    }

    /**
     * The foreign receiver in the frame.
     *
     * @param frame the frame that was called via
     *            {@link #send(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) }
     * @return the receiver used when invoking the frame
     * @since 0.8 or earlier
     */
    public static TruffleObject getReceiver(Frame frame) {
        return (TruffleObject) frame.getArguments()[InteropAccessNode.ARG0_RECEIVER];
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        Object f;
        if (factory instanceof DelegatingFactory18) {
            f = ((DelegatingFactory18) factory).factory;
        } else if (factory instanceof DelegatingFactory10) {
            f = ((DelegatingFactory10) factory).factory;
        } else {
            f = factory;
        }
        return "ForeignAccess[" + f.getClass().getName() + "]";
    }

    private void checkThread() {
        assert initThread == Thread.currentThread();
    }

    CallTarget access(Message message) {
        checkThread();
        return factory.accessMessage(message);
    }

    CallTarget checkLanguage() {
        if (languageCheck != null) {
            return Truffle.getRuntime().createCallTarget((RootNode) languageCheck.deepCopy());
        } else {
            return null;
        }
    }

    boolean canHandle(TruffleObject receiver) {
        checkThread();
        return factory.canHandle(receiver);
    }

    /**
     * Interface of a factory that produces AST snippets that can access a foreign
     * {@code TruffleObject}. A Truffle language implementation accesses a {@code TruffleObject} via
     * a {@code Message}. The {@code TruffleObject} instance provides a {@link ForeignAccess}
     * instance (built via {@link #create(com.oracle.truffle.api.interop.ForeignAccess.Factory)})
     * that provides an AST snippet for a given {@link Message}. Rather than using this generic
     * {@code Factory}, consider implementing {@link Factory18} interface that captures the set of
     * messages each language should implement as of Truffle version 0.18.
     *
     * @since 0.8 or earlier
     */
    public interface Factory {

        /**
         * * Checks whether provided {@link TruffleObject} can be accessed using AST snippets
         * produced by this {@link Factory}.
         *
         * @param obj the object to check
         * @return true, if the object can be processed
         * @since 0.8 or earlier
         */
        boolean canHandle(TruffleObject obj);

        /**
         * Provides an AST snippet to access a {@code TruffleObject}.
         *
         * @param tree the {@code Message} that represents the access to a {@code TruffleObject}.
         * @return the AST snippet for accessing the {@code TruffleObject}, wrapped as a
         *         {@code CallTarget}.
         * @since 0.8 or earlier
         */
        CallTarget accessMessage(Message tree);
    }

    /**
     * Specialized {@link Factory factory} that handles {@link Message messages} known as of release
     * 0.18 of the Truffle API.
     *
     * @since 0.18
     */
    public interface Factory18 {
        /**
         * Handles {@link Message#IS_NULL} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessIsNull();

        /**
         * Handles {@link Message#IS_EXECUTABLE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessIsExecutable();

        /**
         * Handles {@link Message#IS_BOXED} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessIsBoxed();

        /**
         * Handles {@link Message#HAS_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessHasSize();

        /**
         * Handles {@link Message#GET_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessGetSize();

        /**
         * Handles {@link Message#UNBOX} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessUnbox();

        /**
         * Handles {@link Message#READ} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessRead();

        /**
         * Handles {@link Message#WRITE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessWrite();

        /**
         * Handles {@link Message#createExecute(int)} messages.
         *
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessExecute(int argumentsLength);

        /**
         * Handles {@link Message#createInvoke(int)} messages.
         *
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessInvoke(int argumentsLength);

        /**
         * Handles {@link Message#createNew(int)} messages.
         *
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessNew(int argumentsLength);

        /**
         * Handles request for access to a message not known in version 0.10. The parameter to the
         * returned {@link CallTarget} is going to be the object/receiver. The return value is
         * supposed to be a {@link TruffleObject} that represents an array (responds to
         * {@link Message#HAS_SIZE} and {@link Message#GET_SIZE} and its element represent
         * {@link String} names of properties of the receiver.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessKeys();

        /**
         * Handles request for access to a message not known in version 0.18.
         *
         * @param unknown the message
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.18
         */
        CallTarget accessMessage(Message unknown);
    }

    /**
     * Specialized {@link Factory factory} that handles {@link Message messages} known as of release
     * 0.10 of the Truffle API.
     *
     * @since 0.8 or earlier
     * @deprecated extended set of messages is now supported, consider implementing
     *             {@link Factory18}
     */
    @Deprecated
    public interface Factory10 {
        /**
         * Handles {@link Message#IS_NULL} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessIsNull();

        /**
         * Handles {@link Message#IS_EXECUTABLE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessIsExecutable();

        /**
         * Handles {@link Message#IS_BOXED} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessIsBoxed();

        /**
         * Handles {@link Message#HAS_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessHasSize();

        /**
         * Handles {@link Message#GET_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessGetSize();

        /**
         * Handles {@link Message#UNBOX} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessUnbox();

        /**
         * Handles {@link Message#READ} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessRead();

        /**
         * Handles {@link Message#WRITE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessWrite();

        /**
         * Handles {@link Message#createExecute(int)} messages.
         *
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessExecute(int argumentsLength);

        /**
         * Handles {@link Message#createInvoke(int)} messages.
         *
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessInvoke(int argumentsLength);

        /**
         * Handles {@link Message#createNew(int)} messages.
         *
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.9
         */
        CallTarget accessNew(int argumentsLength);

        /**
         * Handles request for access to a message not known in version 1.0.
         *
         * @param unknown the message
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.8 or earlier
         */
        CallTarget accessMessage(Message unknown);
    }

    private static class DelegatingFactory10 implements Factory {
        private final Class<?> baseClass;
        private final Factory10 factory;

        DelegatingFactory10(Class<?> baseClass, Factory10 factory) {
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

        private static CallTarget accessMessage(Factory10 factory, Message msg) {
            if (msg instanceof KnownMessage) {
                switch (msg.hashCode()) {
                    case Execute.EXECUTE:
                        return factory.accessExecute(((Execute) msg).getArity());
                    case Execute.INVOKE:
                        return factory.accessInvoke(((Execute) msg).getArity());
                    case Execute.NEW:
                        return factory.accessNew(((Execute) msg).getArity());
                    case GetSize.HASH:
                        return factory.accessGetSize();
                    case HasSize.HASH:
                        return factory.accessHasSize();
                    case IsBoxed.HASH:
                        return factory.accessIsBoxed();
                    case IsExecutable.HASH:
                        return factory.accessIsExecutable();
                    case IsNull.HASH:
                        return factory.accessIsNull();
                    case Read.HASH:
                        return factory.accessRead();
                    case Unbox.HASH:
                        return factory.accessUnbox();
                    case Write.HASH:
                        return factory.accessWrite();
                }
            }
            return factory.accessMessage(msg);
        }
    }

    private static class DelegatingFactory18 implements Factory {
        private final Class<?> baseClass;
        private final Factory18 factory;

        DelegatingFactory18(Class<?> baseClass, Factory18 factory) {
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

        private static CallTarget accessMessage(Factory18 factory, Message msg) {
            if (msg instanceof KnownMessage) {
                switch (msg.hashCode()) {
                    case Execute.EXECUTE:
                        return factory.accessExecute(((Execute) msg).getArity());
                    case Execute.INVOKE:
                        return factory.accessInvoke(((Execute) msg).getArity());
                    case Execute.NEW:
                        return factory.accessNew(((Execute) msg).getArity());
                    case GetSize.HASH:
                        return factory.accessGetSize();
                    case HasSize.HASH:
                        return factory.accessHasSize();
                    case IsBoxed.HASH:
                        return factory.accessIsBoxed();
                    case IsExecutable.HASH:
                        return factory.accessIsExecutable();
                    case IsNull.HASH:
                        return factory.accessIsNull();
                    case Read.HASH:
                        return factory.accessRead();
                    case Unbox.HASH:
                        return factory.accessUnbox();
                    case Write.HASH:
                        return factory.accessWrite();
                    case Keys.HASH:
                        return factory.accessKeys();
                }
            }
            return factory.accessMessage(msg);
        }
    }

    @SuppressWarnings("unused") private static final InteropAccessor ACCESSOR = new InteropAccessor();
}
