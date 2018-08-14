/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;
import com.oracle.truffle.api.nodes.Node;

/**
 * Inter-operability is based on sending messages. Standard messages are defined as as constants
 * like {@link #IS_NULL} or factory methods in this class, but one can always define their own,
 * specialized messages.
 *
 * @since 0.8 or earlier
 */
public abstract class Message {
    /**
     * One can define their own extended message by subclassing. The expectation is that the
     * subclass will have public constructor and its {@link #equals(java.lang.Object)} and
     * {@link #hashCode()} methods will operate on the class equivalence. Only then the subclass
     * will work properly with {@link #valueOf(java.lang.String)} and
     * {@link #toString(com.oracle.truffle.api.interop.Message)} methods.
     *
     * @since 0.8 or earlier
     */
    protected Message() {
        registerClass(this);
    }

    /**
     * Message to read an object field. The
     * {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) target} created for this
     * message accepts (in addition to a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver}) a single
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) argument} identifying a
     * field to read - e.g. either {@link String} or a {@link Number} - if access to an array at
     * particular index is requested.
     * <p>
     * If the object does not support the {@link #READ} message, an
     * {@link UnsupportedMessageException} has to be thrown.
     *
     * If the object does not allow reading a property for a given identifier, an
     * {@link UnknownIdentifierException} has to be thrown.
     * <p>
     * The code that wants to send this message should use:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendRead(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object) sendRead}(
     *   {@link Message#READ}.{@link Message#createNode() createNode()}, receiver, nameOfTheField
     * );
     * </pre>
     *
     * Where <code>receiver</code> is the {@link TruffleObject foreign object} to access and
     * <code>nameOfTheField</code> is the name (or index) of its field.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.8 or earlier
     */
    public static final Message READ = Read.INSTANCE;

    /**
     * Converts {@link TruffleObject truffle value} to Java primitive type. Primitive types are
     * subclasses of {@link Number}, {@link Boolean}, {@link Character} and {@link String}. Before
     * sending the {@link #UNBOX} message, it is desirable to send the {@link #IS_BOXED} one and
     * verify that the object can really be unboxed.
     * <p>
     * If the object does not support the {@link #UNBOX} message, an
     * {@link UnsupportedMessageException} has to be thrown.
     * <p>
     * To unbox an object, use:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendUnbox(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject) sendUnbox}(
     *   {@link Message#UNBOX}.{@link Message#createNode() createNode()}, objectToUnbox
     * );
     * </pre>
     *
     * The returned value should be subclass of {@link Number}, {@link Boolean}, {@link Character}
     * or {@link String}.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.8 or earlier
     */
    public static final Message UNBOX = Unbox.INSTANCE;

    /**
     * Message to write a field. The
     * {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) target} created for this
     * message accepts the object to modify as a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver} and two
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) arguments}. The first
     * one identifies a field to read - e.g. either {@link String} or an {@link Integer} - if access
     * to an array at particular index is requested. The second one is the value to assign to such
     * field.
     * <p>
     * If the object does not support the {@link #WRITE} message, an
     * {@link UnsupportedMessageException} has to be thrown.
     *
     * If the object does not allow writing a property for a given identifier, an
     * {@link UnknownIdentifierException} has to be thrown.
     *
     * If the provided value has an unsupported type and cannot be written, an
     * {@link UnsupportedTypeException} has to be thrown.
     * <p>
     * Use following style to construct field modification message:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendWrite(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object, java.lang.Object) sendWrite}(
     *   {@link Message#WRITE}.{@link Message#createNode() createNode()}, receiver, nameOfTheField, newValue
     * );
     * </pre>
     *
     * Where <code>receiver</code> is the {@link TruffleObject foreign object} to access,
     * <code>nameOfTheField</code> is the name (or index) of its field and <code>newValue</code> is
     * the value to assign to the receiver's field.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.8 or earlier
     */
    public static final Message WRITE = Write.INSTANCE;

    /**
     * Message to remove a field. The
     * {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) target} created for this
     * message accepts the object to modify as a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver} and one
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) argument} identifying a
     * field to remove - e.g. either {@link String} or a {@link Number} - if removal of an array
     * element at particular index is requested.
     * <p>
     * If the object does not support the {@link #REMOVE} message, an
     * {@link UnsupportedMessageException} has to be thrown.
     *
     * If the object does not contain a property for a given identifier, an
     * {@link UnknownIdentifierException} has to be thrown.
     * <p>
     * Use following style to construct field removal message:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendRemove(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object) sendRemove}(
     *   {@link Message#WRITE}.{@link Message#createNode() createNode()}, receiver, nameOfTheField);
     * </pre>
     *
     * Where <code>receiver</code> is the {@link TruffleObject foreign object} to access and
     * <code>nameOfTheField</code> is the name (or index) of its field.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.32
     */
    public static final Message REMOVE = Remove.INSTANCE;

    /**
     * The non-object oriented execution message. In contrast to the {@link #INVOKE} message, which
     * is more suitable for dealing with object oriented style of programming, the {@link #EXECUTE}
     * message is more suitable for execution where one can explicitly control all passed in
     * arguments.
     * <p>
     * To inter-operate with a non-OOP language like <em>C</em> - for example to execute its
     * function:
     *
     * <pre>
     * <b>double</b> add(<b>double</b> a, <b>double</b> b) {
     *   <b>return</b> a + b;
     * }
     * </pre>
     *
     * One can obtain reference to the <em>add</em> function (for example by
     * {@link Env#importSymbol(java.lang.String) importing it as a global symbol}) and store it into
     * variable <code>addFunction</code>. Then it's time to check the object is executable by
     * sending it the {@link #IS_EXECUTABLE} message.
     * <p>
     * If the object does not support the <code>EXECUTE</code> message, an
     * {@link UnsupportedMessageException} has to be thrown.
     *
     * If the caller provides a wrong number of arguments, an {@link ArityException} has to be
     * thrown.
     *
     * If one of the provided argument values has an unsupported type, an
     * {@link UnsupportedTypeException} has to be thrown.
     * <p>
     * Use following style to construct execution message:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendExecute(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) sendExecute}(
     *   {@link Message#EXECUTE Message.EXECUTE}.{@link Message#createNode() createNode()}, addFunction, valueOfA, valueOfB
     * );
     * </pre>
     *
     * The <code>valueOfA</code> and <code>valueOfB</code> should be <code>double</code> or
     * {@link Double} or at least be {@link #UNBOX unboxable} to such type.
     * <p>
     * One can use this method to talk to object oriented language as well, however one needs to pay
     * attention to provide all necessary arguments manually - usually an OOP language requires the
     * first argument to represent <code>this</code> or <code>self</code> and only then pass in the
     * additional arguments. It may be easier to use the {@link #INVOKE} message which is more
     * suitable for object oriented languages and handles (if supported) the arguments manipulation
     * automatically.
     * <p>
     *
     * @since 1.0
     */
    public static final Message EXECUTE = Execute.INSTANCE;

    /**
     * Use {@link Message#EXECUTE} instead.
     *
     * @since 0.8 or earlier
     */
    @Deprecated
    public static Message createExecute(@SuppressWarnings("unused") int argumentsLength) {
        return EXECUTE;
    }

    /**
     * Message to check executability of a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) foreign object}.
     * <p>
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message accepts
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) no arguments} and a
     * single non-null {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame)
     * receiver}. The call should yield value of {@link Boolean}. Either {@link Boolean#TRUE} if the
     * receiver can be executed (i.e. accepts the {@link #EXECUTE} message, or {@link Boolean#FALSE}
     * otherwise. This is the way to send the <code>IS_EXECUTABLE</code> message:
     *
     * <pre>
     * {@link Boolean} canBeExecuted = ({@link Boolean}) {@link ForeignAccess}.sendIsExecutable(
     *   {@link Message#IS_EXECUTABLE}.{@link Message#createNode() createNode()}, receiver
     * );
     * </pre>
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.8 or earlier
     */
    public static final Message IS_EXECUTABLE = IsExecutable.INSTANCE;

    /**
     * Message to check the ability to create new instances of a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) foreign object}.
     * <p>
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message accepts
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) no arguments} and a
     * single non-null {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame)
     * receiver}. The call should yield value of {@link Boolean}. Either {@link Boolean#TRUE} if the
     * receiver can be instantiated (i.e. accepts the {@link #NEW} message, or {@link Boolean#FALSE}
     * otherwise. This is the way to send the <code>IS_INSTANTIABLE</code> message:
     *
     * <pre>
     * {@link Boolean} canBeinstantiated = ({@link Boolean}) {@link ForeignAccess}.sendIsInstantiable(
     *   {@link Message#IS_INSTANTIABLE}.{@link Message#createNode() createNode()}, receiver
     * );
     * </pre>
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.30
     */
    public static final Message IS_INSTANTIABLE = IsInstantiable.INSTANCE;

    /**
     * The object oriented execute message. Unlike {@link #EXECUTE} the receiver of the message
     * isn't the actual function to invoke, but an object. The object has the function as a field,
     * or as a field of its class, or whatever is appropriate for an object oriented language.
     * <p>
     * Languages that don't support object oriented semantics do not and should not implement this
     * message. When the invoke message isn't supported, the caller is expected to fall back into
     * following basic operations:
     * <ul>
     * <li>sending {@link #READ} message to access the field</li>
     * <li>verify the result {@link #IS_EXECUTABLE}, if so continue by</li>
     * <li>sending {@link #EXECUTE execute message}</li>
     * </ul>
     * <p>
     * The last step is problematic, as it is not clear whether to pass just the execution
     * arguments, or prefix them with the original receiver (aka <code>this</code> or
     * <code>self</code>). Object oriented languages would in general welcome obtaining the
     * receiving object as first argument, non-object languages like <em>C</em> would get confused
     * by doing so. However it is not possible for the caller to find out what language one is
     * sending message to - only the set of supported messages is known. As a result it is
     * recommended for object oriented languages to support the {@link #INVOKE} message and handle
     * the semantics the way it is natural to them. Languages like <em>C</em> shouldn't implement
     * {@link #INVOKE} and just support primitive operations like {@link #EXECUTE} and {@link #READ}
     * .
     * <p>
     * When accessing a method of an object in an object oriented manner, one is supposed to send
     * the {@link #INVOKE} message first. Only when that fails, fallback to non-object oriented
     * workflow with {@link #EXECUTE}. Imagine there is a <em>Java</em> class with <code>add</code>
     * method and its instance:
     *
     * <pre>
     * <b>public class</b> Arith {
     *    <b>public double</b> add(double a, double b) {
     *      <b>return</b> a + b;
     *    }
     * }
     * Arith obj = <b>new</b> Arith();
     * </pre>
     * <p>
     * If the object does not support the <code>INVOKE</code> message, an
     * {@link UnsupportedMessageException} has to be thrown.
     *
     * If the object does not allow invoking a member with the given identifier, an
     * {@link UnknownIdentifierException} has to be thrown.
     *
     * If the caller provides a wrong number of arguments, an {@link ArityException} has to be
     * thrown.
     *
     * If one of the provided argument values has an unsupported type, an
     * {@link UnsupportedTypeException} has to be thrown.
     * <p>
     *
     * To access <code>obj</code>'s <code>add</code> method one should use:
     *
     * <pre>
     * <b>try</b> {
     *   {@link ForeignAccess}.{@link ForeignAccess#sendInvoke(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.String, java.lang.Object...) sendInvoke}(
     *     {@link Message#INVOKE Message.INVOKE}.{@link Message#createNode() createNode()}, obj, "add", valueOfA, valueOfB
     *   );
     * } <b>catch</b> ({@link IllegalArgumentException} ex) {
     *   // access the language via {@link #EXECUTE}
     * }
     * </pre>
     *
     * The <code>valueOfA</code> and <code>valueOfB</code> should be <code>double</code> or
     * {@link Double} or at least be {@link #UNBOX unboxable} to such type.
     * <p>
     * The expected behavior of this message is to perform {@link #READ} first and then
     * {@link #EXECUTE} the result.
     *
     * @since 1.0
     */
    public static final Message INVOKE = Invoke.INSTANCE;

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
     * The allocation message. The expected behavior of this message is to allocate a new instance
     * of the {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver} and
     * then perform its constructor with appropriate number of arguments. To check if an object
     * supports allocation of new instances, use the {@link #IS_INSTANTIABLE} message.
     * <p>
     * If the object does not support the <code>NEW</code> message, an
     * {@link UnsupportedMessageException} has to be thrown.
     *
     * If the caller provides a wrong number of arguments, an {@link ArityException} has to be
     * thrown.
     *
     * If one of the provided argument values has an unsupported type, an
     * {@link UnsupportedTypeException} has to be thrown.
     * <p>
     *
     * @since 1.0
     */
    public static final Message NEW = New.INSTANCE;

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
     * Check for <code>null</code> message. The Truffle languages are suggested to have their own
     * object representing <code>null</code> like values in their languages. For purposes of
     * inter-operability it is essential to canonicalize such values from time to time - sending
     * this message is a way to recognize such <code>null</code> representing values:
     *
     * <pre>
     * {@link Boolean} isNull = ({@link Boolean}) {@link ForeignAccess}.sendIsNull(
     *   {@link Message#IS_NULL}.{@link Message#createNode() createNode()}, objectToCheckForNull
     * );
     * </pre>
     *
     * <p>
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of {@link Boolean}.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.8 or earlier
     */
    public static final Message IS_NULL = IsNull.INSTANCE;

    /**
     * Message to check for having a size. If a {@link TruffleObject} indicates it <em>has a
     * size</em>, it is expected it represents array-like structure and it also properly responds to
     * {@link #GET_SIZE} message. When <code>HAS_SIZE</code> returns <code>false</code>, it
     * indicates that {@link #GET_SIZE} message is not supported.
     * <p>
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of {@link Boolean}.
     *
     * @since 0.8 or earlier
     * @see ForeignAccess#sendHasSize(com.oracle.truffle.api.nodes.Node,
     *      com.oracle.truffle.api.interop.TruffleObject)
     */
    public static final Message HAS_SIZE = HasSize.INSTANCE;

    /**
     * Getter of the size. If {@link #HAS_SIZE supported}, this message has to return size of
     * receiver's array like structure as an {@link Integer}. If the {@link #HAS_SIZE} message
     * returns <code>true</code> implementations for {@link #READ} and {@link #WRITE} messages with
     * {@link Integer} parameters from range <code>0</code> to <code>GET_SIZE - 1</code> are
     * required.
     * <p>
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of {@link Integer}.
     * <p>
     * If the object does not support the {@link #GET_SIZE} message, an
     * {@link UnsupportedMessageException} has to be thrown.
     * <p>
     *
     * @since 0.8 or earlier
     * @see ForeignAccess#sendGetSize(com.oracle.truffle.api.nodes.Node,
     *      com.oracle.truffle.api.interop.TruffleObject)
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
     * {@link Boolean} isBoxed = ({@link Boolean}) {@link ForeignAccess}.sendIsBoxed(
     *   {@link Message#IS_BOXED}.{@link Message#createNode() createNode()}, objectToCheck
     * );
     * </pre>
     *
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of {@link Boolean}. If the object responds with
     * {@link Boolean#TRUE}, it is safe to continue by sending it {@link #UNBOX} message.
     *
     * @since 0.8 or earlier
     */
    public static final Message IS_BOXED = IsBoxed.INSTANCE;

    /**
     * Message to retrieve flags about a particular key (a property name). The returned value is an
     * integer containing bit flags. See {@link KeyInfo} for possible flags. This message also
     * allows a fast check of existence of a property among {@link #KEYS}, the returned value is
     * <code>0</code> iff the key does not exist. The
     * {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) target} created for this
     * message accepts (in addition to a
     * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver}) a single
     * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) argument} identifying a
     * property to get the info of - e.g. either {@link String} or a {@link Number} - if test of an
     * array at a particular index is requested.
     * <p>
     * The default implementation requests {@link #KEYS} and test if they contain the requested key.
     * If they do, a default bit mask <code>0b111</code> is returned.
     * <p>
     * The code that wants to send this message should use:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendKeyInfo(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object) sendKeyInfo}(
     *   {@link Message#KEY_INFO}.{@link Message#createNode() createNode()}, receiver, nameOfTheField
     * );
     * </pre>
     *
     * Where <code>receiver</code> is the {@link TruffleObject foreign object} to access and
     * <code>nameOfTheField</code> is the name (or index) of its field.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.26
     */
    public static final Message KEY_INFO = KeyInfoMsg.INSTANCE;

    /**
     * Message to check for having properties. If a {@link TruffleObject} indicates it <em>has
     * keys</em>, it is expected it represents an object structure with properties and it also
     * properly responds to {@link #KEYS} message. When <code>HAS_KEYS</code> returns
     * <code>false</code>, it indicates that {@link #KEYS} message is not supported.
     * <p>
     * The default implementation requests {@link #KEYS} and returns <code>true</code> if the
     * request was successful and <code>false</code> otherwise.
     * <p>
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of type {@link Boolean}.
     *
     * @since 0.30
     * @see ForeignAccess#sendHasKeys(com.oracle.truffle.api.nodes.Node,
     *      com.oracle.truffle.api.interop.TruffleObject)
     */
    public static final Message HAS_KEYS = HasKeys.INSTANCE;

    /**
     * Obtains list of property names. Checks the properties of a {@link TruffleObject foreign
     * objects} and obtains list of its property names. Those names can then be used in
     * {@link #READ} and {@link #WRITE} messages to obtain/assign real values. To check if an object
     * supports properties, use the {@link #HAS_KEYS} message.
     * <p>
     * Since version 0.26 the {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message)
     * target} created for this message accepts a boolean argument specifying whether internal keys
     * should be included. Internal keys are extra property keys that are a part of the object, but
     * are not provided among ordinary keys. They may even not correspond to anything what is an
     * explicit part of the guest language representation. An example of such internal values are
     * internal slots in ECMAScript.
     * <p>
     * The return value from using this message is another {@link TruffleObject} that responds to
     * {@link #HAS_SIZE} message and its indexes 0 to {@link #GET_SIZE} - 1 contain {@link String}
     * names of individual properties. The properties should be provided in deterministic order.
     *
     * @since 0.18
     */
    public static final Message KEYS = Keys.INSTANCE;

    /**
     * Check for a value being a native pointer. Can the {@link TruffleObject foreign object} be
     * converted to a 64bit pointer value? The way to check whether an object is a pointer is:
     *
     * <pre>
     * {@link Boolean} isPointer = ({@link Boolean}) {@link ForeignAccess}.sendIsPointer(
     *   {@link Message#IS_POINTER}.{@link Message#createNode() createNode()}, objectToCheck
     * );
     * </pre>
     *
     * Calling {@link Factory#accessMessage(com.oracle.truffle.api.interop.Message) the target}
     * created for this message should yield value of {@link Boolean}. If the object responds with
     * {@link Boolean#TRUE}, the object can be accessed by {@link #AS_POINTER} message.
     *
     * It is expected that objects should only return {@link Boolean#TRUE} here if the native
     * pointer value corresponding to this object already exists, and obtaining it is a cheap
     * operation. If an object can be transformed to a pointer representation, but this hasn't
     * happened yet, the object is expected to return {@link Boolean#FALSE} to {@link #IS_POINTER},
     * and wait for the {@link #TO_NATIVE} message to trigger the transformation.
     *
     * @since 0.26 or earlier
     */
    public static final Message IS_POINTER = IsPointer.INSTANCE;

    /**
     * Converts {@link TruffleObject truffle value} to a raw 64bit pointer value. Before sending the
     * {@link #AS_POINTER} message, it is desirable to send the {@link #IS_POINTER} one and verify
     * that the object can really be unwrapped to a raw pointer value.
     * <p>
     * If the object does not support the {@link #AS_POINTER} message, an
     * {@link UnsupportedMessageException} has to be thrown.
     * <p>
     * To unwrap a pointer value, use:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendAsPointer(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject) sendAsPointer}(
     *   {@link Message#AS_POINTER}.{@link Message#createNode() createNode()}, objectAsPointer
     * );
     * </pre>
     *
     * The returned value is a {@link Long} value.
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.26 or earlier
     */
    public static final Message AS_POINTER = AsPointer.INSTANCE;

    /**
     * Transforms a {@link TruffleObject truffle value} a new {@link TruffleObject truffle native
     * value} that represents a raw native pointer. This resulting {@link TruffleObject truffle
     * native value} returns true for {@link #IS_POINTER} and can be unwrapped using the
     * {@link #AS_POINTER} message.
     * <p>
     * If an object returns true for {@link #IS_POINTER}, it is still expected that this object
     * supports the {@link #TO_NATIVE} message. It can just return a reference to itself in that
     * case.
     * <p>
     * If the object does not support the {@link #TO_NATIVE} message, an
     * {@link UnsupportedMessageException} has to be thrown.
     * <p>
     * To transform an object to a native value, use:
     *
     * <pre>
     * {@link ForeignAccess}.{@link ForeignAccess#sendToNative(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject) sendToNative}(
     *   {@link Message#TO_NATIVE}.{@link Message#createNode() createNode()}, objectToNative
     * );
     * </pre>
     *
     * <p>
     * To achieve good performance it is essential to cache/keep reference to the
     * {@link Message#createNode() created node}.
     *
     * @since 0.26 or earlier
     */
    public static final Message TO_NATIVE = ToNative.INSTANCE;

    /**
     * Compares types of two messages. Messages are encouraged to implement this method. All
     * standard ones ({@link #IS_NULL}, {@link #READ}, etc.) do so. Messages obtained by different
     * methods or fields are not equal.
     *
     * @param message the object to compare to
     * @return true, if the structure of the message is that same as of <code>this</code> one.
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
     * Creates an AST node for this message. The node can be inserted into AST of your language and
     * will handle communication with the foreign language.
     *
     * @return node to be inserted into your AST and passed back to
     *         {@link ForeignAccess#send(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...)}
     *         method.
     * @since 0.8 or earlier
     */
    public final Node createNode() {
        CompilerAsserts.neverPartOfCompilation();
        return InteropAccessNode.create(this);
    }

    /**
     * Converts the message into canonical string representation. The converted string can be
     * stored, persisted, transfered and later passed to {@link #valueOf(java.lang.String)} to
     * construct the message again.
     *
     * @param message the message to convert
     * @return canonical string representation
     * @since 0.9
     */
    public static String toString(Message message) {
        if (Message.READ == message) {
            return "READ"; // NOI18N
        }
        if (Message.WRITE == message) {
            return "WRITE"; // NOI18N
        }
        if (Message.REMOVE == message) {
            return "REMOVE"; // NOI18N
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
        if (Message.IS_INSTANTIABLE == message) {
            return "IS_INSTANTIABLE"; // NOI18N
        }
        if (Message.HAS_KEYS == message) {
            return "HAS_KEYS"; // NOI18N
        }
        if (Message.KEYS == message) {
            return "KEYS"; // NOI18N
        }
        if (Message.KEY_INFO == message) {
            return "KEY_INFO"; // NOI18N
        }
        if (Message.IS_POINTER == message) {
            return "IS_POINTER"; // NOI18N
        }
        if (Message.AS_POINTER == message) {
            return "AS_POINTER"; // NOI18N
        }
        if (Message.TO_NATIVE == message) {
            return "TO_NATIVE"; // NOI18N
        }
        if (Execute.INSTANCE == message) {
            return "EXECUTE";
        }
        if (Invoke.INSTANCE == message) {
            return "INVOKE";
        }
        if (New.INSTANCE == message) {
            return "NEW";
        }
        return message.getClass().getName();
    }

    /**
     * Converts string representation into real message. If the string was obtained by
     * {@link #toString(com.oracle.truffle.api.interop.Message)} method, it is guaranteed to be
     * successfully recognized (if the classpath of the system remains the same).
     *
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
