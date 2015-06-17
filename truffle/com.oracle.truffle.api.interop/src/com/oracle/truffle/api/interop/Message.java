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

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;

/**
 * Inter-operability is based on sending messages. Standard messages are defined as as constants
 * like {@link #IS_NULL} or factory methods in this class, but one can always define their own,
 * specialized messages.
 */
public abstract class Message {
    /**
     * Message to read a field.
     */
    public static final Message READ = Read.INSTANCE;

    /**
     * Converts {@link TruffleObject truffle value} to Java primitive type. Primitive types are
     * subclasses of {@link Number}, {@link Boolean}, {@link Character} and {@link String}. Related
     * to {@link #IS_BOXED} message.
     */
    public static final Message UNBOX = Unbox.INSTANCE;

    /**
     * Message to write a field.
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
        return Execute.create(false, argumentsLength);
    }

    /**
     * Message to check for executability.
     * <p>
     * Calling {@link Factory#access(com.oracle.truffle.api.interop.Message) the target} created for
     * this message should yield value of {@link Boolean}.
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
        return Execute.create(true, argumentsLength);
    }

    /**
     * Check for <code>null</code> message. The Truffle languages are suggested to have their own
     * object representing <code>null</code> like values in their languages. For purposes of
     * inter-operability it is essential to canonicalize such values from time to time - sending
     * this message is a way to recognize such <code>null</code> representing values.
     * <p>
     * Calling {@link Factory#access(com.oracle.truffle.api.interop.Message) the target} created for
     * this message should yield value of {@link Boolean}.
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
     * Check for value being boxed. Can you value be converted to one of the basic Java types? Many
     * languages have a special representation for types like number, string, etc. To ensure
     * inter-operability, these types should support unboxing - if they do, they should handle this
     * message.
     * <p>
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of {@link Boolean}.
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

}
