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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.impl.ReadOnlyArrayList;
import com.oracle.truffle.api.nodes.Node;
import java.util.List;

/**
 * Encapsulates types of access to {@link TruffleObject}. If you want to expose your own objects to
 * foreign language implementations, you need to implement {@link TruffleObject} and its
 * {@link TruffleObject#getForeignAccess()} method. To create instance of <code>ForeignAccess</code>
 * , use one of the factory methods available in this class.
 */
public final class ForeignAccess {
    private final Factory factory;

    private ForeignAccess(Factory faf) {
        this.factory = faf;
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param baseClass the super class of all {@link TruffleObject}s handled by this factory (if
     *            <code>null</code> than the second interface also needs to implement
     *            {@link Factory})
     * @param factory the factory that handles access requests to {@link Message}s known as of
     *            version 1.0
     * @return new instance wrapping <code>factory</code>
     */
    public static ForeignAccess create(final Class<? extends TruffleObject> baseClass, final Factory10 factory) {
        if (baseClass == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        class DelegatingFactory implements Factory {
            @Override
            public boolean canHandle(TruffleObject obj) {
                if (baseClass == null) {
                    return ((Factory) factory).canHandle(obj);
                }
                return baseClass.isInstance(obj);
            }

            @Override
            public CallTarget accessMessage(Message msg) {
                if (msg instanceof KnownMessage) {
                    switch (msg.hashCode()) {
                        case Execute.HASH1:
                            return factory.accessInvoke(((Execute) msg).getArity());
                        case Execute.HASH2:
                            return factory.accessExecute(((Execute) msg).getArity());
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
        return new ForeignAccess(new DelegatingFactory());
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param factory the factory that handles various access requests {@link Message}s.
     * @return new instance wrapping <code>factory</code>
     */
    public static ForeignAccess create(Factory factory) {
        return new ForeignAccess(factory);
    }

    /**
     * Executes {@link Message#createNode() foreign node}.
     * 
     * @param foreignNode the createNode created by {@link Message#createNode()}
     * @param frame the call frame
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param arguments parameters for the receiver
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     */
    public static Object execute(Node foreignNode, VirtualFrame frame, TruffleObject receiver, Object... arguments) {
        ForeignObjectAccessHeadNode fn = (ForeignObjectAccessHeadNode) foreignNode;
        return fn.executeForeign(frame, receiver, arguments);
    }

    /**
     * Read only access to foreign call arguments inside of a frame.
     *
     * @param frame the frame that was called via
     *            {@link #execute(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.VirtualFrame, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) }
     * @return read-only list of parameters passed to the frame
     */
    public static List<Object> getArguments(Frame frame) {
        final Object[] arr = frame.getArguments();
        return ReadOnlyArrayList.asList(arr, 1, arr.length);
    }

    /**
     * The foreign receiver in the frame.
     *
     * @param frame the frame that was called via
     *            {@link #execute(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.VirtualFrame, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) }
     * @return the receiver used when invoking the frame
     */
    public static TruffleObject getReceiver(Frame frame) {
        return (TruffleObject) frame.getArguments()[ForeignAccessArguments.RECEIVER_INDEX];
    }

    CallTarget access(Message message) {
        return factory.accessMessage(message);
    }

    boolean canHandle(TruffleObject receiver) {
        return factory.canHandle(receiver);
    }

    /**
     * Interface of a factory that produces AST snippets that can access a foreign
     * {@code TruffleObject}. A Truffle language implementation accesses a {@code TruffleObject} via
     * a {@code Message}. The {@code TruffleObject} instance provides a {@link ForeignAccess}
     * instance (built via {@link #create(com.oracle.truffle.api.interop.ForeignAccess.Factory)})
     * that provides an AST snippet for a given {@link Message}. Rather than using this generic
     * {@code Factory}, consider implementing {@link Factory10} interface that captures the set of
     * messages each language should implement as of Truffle version 1.0.
     */
    public interface Factory {

        /**
         * * Checks whether provided {@link TruffleObject} can be accessed using AST snippets
         * produced by this {@link Factory}.
         *
         * @param obj the object to check
         * @return true, if the object can be processed
         */
        boolean canHandle(TruffleObject obj);

        /**
         * Provides an AST snippet to access a {@code TruffleObject}.
         *
         * @param tree the {@code Message} that represents the access to a {@code TruffleObject}.
         * @return the AST snippet for accessing the {@code TruffleObject}, wrapped as a
         *         {@code CallTarget}.
         */
        CallTarget accessMessage(Message tree);
    }

    /**
     * Specialized {@link Factory factory} that handles {@link Message messages} known as of release
     * 1.0 of Truffle API.
     *
     */
    public interface Factory10 {
        /**
         * Handles {@link Message#IS_NULL} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessIsNull();

        /**
         * Handles {@link Message#IS_EXECUTABLE} message.
         * 
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessIsExecutable();

        /**
         * Handles {@link Message#IS_BOXED} message.
         * 
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessIsBoxed();

        /**
         * Handles {@link Message#HAS_SIZE} message.
         * 
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessHasSize();

        /**
         * Handles {@link Message#GET_SIZE} message.
         * 
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessGetSize();

        /**
         * Handles {@link Message#UNBOX} message.
         * 
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessUnbox();

        /**
         * Handles {@link Message#READ} message.
         * 
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessRead();

        /**
         * Handles {@link Message#WRITE} message.
         * 
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessWrite();

        /**
         * Handles {@link Message#createExecute(int)} messages.
         * 
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessExecute(int argumentsLength);

        /**
         * Handles {@link Message#createInvoke(int)} messages.
         * 
         * @param argumentsLength number of parameters the messages has been created for
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessInvoke(int argumentsLength);

        /**
         * Handles request for access to a message not known in version 1.0.
         *
         * @param unknown the message
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         */
        CallTarget accessMessage(Message unknown);
    }
}
