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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;

/**
 * Inter-operability is based on sending messages. Standard messages are defined as as constants
 * like {@link #IS_NULL} or factory methods in this class, but one can always define their own,
 * specialized messages.
 */
public abstract class Message {
    /**
     * Message to read an object field. The
     * {@link Factory#access(com.oracle.truffle.api.interop.Message) target} created for this
     * message accepts (in addition to a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver}) a single
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) argument} identifying a
     * field to read - e.g. either {@link String} or an {@link Integer} - if access to an array at
     * particular index is requested. The code that wants to send this message should use:
     * 
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#execute(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.VirtualFrame, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) execute}(
     *   {@link Message#READ}.{@link Message#createNode()}, {@link VirtualFrame currentFrame}, receiver, nameOfTheField
     * );
     * </pre>
     * 
     * Where <code>receiver</code> is the {@link TruffleObject foreign object} to access and
     * <code>nameOfTheField</code> is the name (or index) of its field.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     */
    public static final Message READ = Read.INSTANCE;

    /**
     * Converts {@link TruffleObject truffle value} to Java primitive type. Primitive types are
     * subclasses of {@link Number}, {@link Boolean}, {@link Character} and {@link String}. Before
     * sending the {@link #UNBOX} message, it is desirable to send the {@link #IS_BOXED} one and
     * verify that the object can really be unboxed. To unbox an object, use:
     * 
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#execute(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.VirtualFrame, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) execute}(
     *   {@link Message#UNBOX}.{@link Message#createNode()}, {@link VirtualFrame currentFrame}, objectToUnbox
     * );
     * </pre>
     * 
     * The returned value should be subclass of {@link Number}, {@link Boolean}, {@link Character}
     * or {@link String}.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     */
    public static final Message UNBOX = Unbox.INSTANCE;

    /**
     * Message to write a field. The {@link Factory#access(com.oracle.truffle.api.interop.Message)
     * target} created for this message accepts the object to modify as a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver} and two
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) arguments}. The first
     * one identifies a field to read - e.g. either {@link String} or an {@link Integer} - if access
     * to an array at particular index is requested. The second one is the value to assign to such
     * field. Use following style to construct field modification message:
     * 
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#execute(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.VirtualFrame, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) execute}(
     *   {@link Message#WRITE}.{@link Message#createNode()}, {@link VirtualFrame currentFrame}, receiver, nameOfTheField, newValue
     * );
     * </pre>
     * 
     * Where <code>receiver</code> is the {@link TruffleObject foreign object} to access,
     * <code>nameOfTheField</code> is the name (or index) of its field and <code>newValue</code> is
     * the value to assign to the receiver's field.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     */
    public static Message WRITE = Write.INSTANCE;

    /**
     * Creates an execute message. All messages created by this method are
     * {@link Object#equals(java.lang.Object) equal} to each other regardless of the value of
     * <code>argumentsLength</code>.
     *
     * @param argumentsLength number of parameters to pass to the target
     * @return execute message
     */
    public static Message createExecute(int argumentsLength) {
        return Execute.create(Execute.EXECUTE, argumentsLength);
    }

    /**
     * Message to check executability of a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) foreign object}.
     * <p>
     * Calling {@link Factory#access(com.oracle.truffle.api.interop.Message) the target} created for
     * this message accepts {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) no
     * arguments} and a single non-null
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver}. The call
     * should yield value of {@link Boolean}. Either {@link Boolean#TRUE} if the receiver can be
     * executed (e.g. accepts {@link #createExecute(int)} message, or {@link Boolean#FALSE}
     * otherwise. This is the way to send the <code>IS_EXECUTABLE</code> message:
     * 
     * <pre>
     * {@link Boolean} canBeExecuted = ({@link Boolean}) {@link ForeignAccess}.execute(
     *   {@link Message#IS_EXECUTABLE}.{@link Message#createNode()}, {@link VirtualFrame currentFrame}, receiver
     * );
     * </pre>
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     */
    public static final Message IS_EXECUTABLE = IsExecutable.INSTANCE;

    /**
     * Creates an execute message. All messages created by this method are
     * {@link Object#equals(java.lang.Object) equal} to each other regardless of the value of
     * <code>argumentsLength</code>. The expected behavior of this message is to perform
     * {@link #READ} first and on the result invoke {@link #createExecute(int)}.
     *
     * @param argumentsLength number of parameters to pass to the target
     * @return read & execute message
     */
    public static Message createInvoke(int argumentsLength) {
        return Execute.create(Execute.INVOKE, argumentsLength);
    }

    /**
     * Creates an allocation message. All messages created by this method are
     * {@link Object#equals(java.lang.Object) equal} to each other regardless of the value of
     * <code>argumentsLength</code>. The expected behavior of this message is to allocate a new
     * instance of the {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame)
     * receiver} and then perform its constructor with appropriate number of arguments.
     *
     * @param argumentsLength number of parameters to pass to the target
     * @return read & execute message
     */
    public static Message createNew(int argumentsLength) {
        return Execute.create(Execute.NEW, argumentsLength);
    }

    /**
     * Check for <code>null</code> message. The Truffle languages are suggested to have their own
     * object representing <code>null</code> like values in their languages. For purposes of
     * inter-operability it is essential to canonicalize such values from time to time - sending
     * this message is a way to recognize such <code>null</code> representing values:
     * 
     * <pre>
     * {@link Boolean} isNull = ({@link Boolean}) {@link ForeignAccess}.execute(
     *   {@link Message#IS_NULL}.{@link Message#createNode()}, {@link VirtualFrame currentFrame}, objectToCheckForNull
     * );
     * </pre>
     *
     * <p>
     * Calling {@link Factory#access(com.oracle.truffle.api.interop.Message) the target} created for
     * this message should yield value of {@link Boolean}.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     */
    public static final Message IS_NULL = IsNull.INSTANCE;

    /**
     * Message to check for having a size.
     * <p>
     * Calling {@link Factory#access(com.oracle.truffle.api.interop.Message) the target} created for
     * this message should yield value of {@link Boolean}.
     */
    public static final Message HAS_SIZE = HasSize.INSTANCE;

    /**
     * Getter of the size. If {@link #HAS_SIZE supported}, this message allows to obtain a size (of
     * an array).
     * <p>
     * Calling {@link Factory#access(com.oracle.truffle.api.interop.Message) the target} created for
     * this message should yield value of {@link Integer}.
     */
    public static final Message GET_SIZE = GetSize.INSTANCE;

    /**
     * Check for value being boxed. Can the {@link TruffleObject foreign object} be converted to one
     * of the basic Java types? Many languages have a special representation for types like number,
     * string, etc. To ensure inter-operability, these types should support unboxing - if they do,
     * they should handle this message and return {@link Boolean#TRUE}. The way to check whether an
     * object is boxed is:
     * 
     * <pre>
     * {@link Boolean} isBoxed = ({@link Boolean}) {@link ForeignAccess}.execute(
     *   {@link Message#IS_BOXED}.{@link Message#createNode()}, {@link VirtualFrame currentFrame}, objectToCheck
     * );
     * </pre>
     * 
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of {@link Boolean}. If the object responds with
     * {@link Boolean#TRUE}, it is safe to continue by sending it {@link #UNBOX} message.
     */
    public static final Message IS_BOXED = IsBoxed.INSTANCE;

    /**
     * Compares types of two messages. Messages are encouraged to implement this method. All
     * standard ones ({@link #IS_NULL}, {@link #READ}, etc.) do so. Messages obtained via the same
     * {@link #createExecute(int) method} are equal, messages obtained by different methods or
     * fields are not.
     *
     * @param message the object to compare to
     * @return true, if the structure of the message is that same as of <code>this</code> one.
     */
    @Override
    public abstract boolean equals(Object message);

    /**
     * When re-implementing {@link #equals(java.lang.Object)}, it is generally recommended to also
     * implement <code>hashCode()</code>.
     * 
     * @return hash code
     */
    @Override
    public abstract int hashCode();

    /**
     * Creates an AST node for this message. The node can be inserted into AST of your language and
     * will handle communication with the foreign language.
     *
     * @return node to be inserted into your AST and passed back to
     *         {@link ForeignAccess#execute(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.VirtualFrame, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object[])}
     *         method.
     */
    public final Node createNode() {
        return new ForeignObjectAccessHeadNode(this);
    }

    /**
     * Converts the message into canonical string representation. The converted string can be
     * stored, persisted, transfered and later passed to {@link #valueOf(java.lang.String)} to
     * construct the message again.
     * 
     * @param message the message to convert
     * @return canonical string representation
     */
    public static String toString(Message message) {
        if (Message.READ == message) {
            return "READ"; // NOI18N
        }
        if (Message.WRITE == message) {
            return "WRITE"; // NOI18N
        }
        if (Message.UNBOX == message) {
            return "UNBOX"; // NOI18N
        }
        if (Message.GET_SIZE == message) {
            return "GET_SIZE"; // NOI18N
        }
        if (Message.HAS_SIZE == message) {
            return "HAS_SIZE"; // NOI18N
        }
        if (Message.IS_NULL == message) {
            return "IS_NULL"; // NOI18N
        }
        if (Message.IS_BOXED == message) {
            return "IS_BOXED"; // NOI18N
        }
        if (Message.IS_EXECUTABLE == message) {
            return "IS_EXECUTABLE"; // NOI18N
        }
        if (message instanceof Execute) {
            return ((Execute) message).name();
        }
        return message.getClass().getName();
    }

    /**
     * Converts string representation into real message. If the string was obtained by
     * {@link #toString(com.oracle.truffle.api.interop.Message)} method, it is guaranteed to be
     * successfully recognized (if the classpath of the system remains the same).
     * 
     * @param message canonical string representation of a message
     * @return the message
     * @throws IllegalArgumentException if the string does not represent known message
     */
    public static Message valueOf(String message) {
        try {
            return (Message) Message.class.getField(message).get(null);
        } catch (Exception ex) {
            try {
                String factory = "create" + message.charAt(0) + message.substring(1).toLowerCase();
                return (Message) Message.class.getMethod(factory, int.class).invoke(null, 0);
            } catch (Exception ex2) {
                try {
                    return (Message) Class.forName(message).newInstance();
                } catch (Exception ex1) {
                    throw new IllegalArgumentException("Cannot find message for " + message, ex);
                }
            }
        }
    }
}
