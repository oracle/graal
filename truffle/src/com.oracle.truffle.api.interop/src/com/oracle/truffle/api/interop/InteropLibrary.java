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
package com.oracle.truffle.api.interop;

import static com.oracle.truffle.api.interop.AssertUtils.preCondition;
import static com.oracle.truffle.api.interop.AssertUtils.validArgument;
import static com.oracle.truffle.api.interop.AssertUtils.validArguments;
import static com.oracle.truffle.api.interop.AssertUtils.validReturn;
import static com.oracle.truffle.api.interop.AssertUtils.violationInvariant;
import static com.oracle.truffle.api.interop.AssertUtils.violationPost;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.InteropLibrary.Asserts;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Represents the library that specifies the interoperability message protocol between Truffle
 * languages, tools and embedders. Every method represents one message specified by the protocol. In
 * the following text we will abbreviate interoperability with interop.
 * <p>
 * The interop API differentiates between the source and the target language. The source language is
 * the language that implements/exports the message implementations. The implementations map types
 * of the source language to the interop protocol as it is specified by the protocol. For example,
 * language values that represent arrays or array like structures should implement the messages for
 * {@link #hasArrayElements(Object) array based access}. This allows the target language to call the
 * protocol without knowledge of the concrete source language. The target language embeds the
 * interop protocol semantics as part of their existing language semantics. For example, language
 * operations that access array elements in the target language should call
 * {@link #hasArrayElements(Object) array access} messages for interop values.
 * <p>
 * The interop protocol only allows <i>interop values</i> to be used as receivers, return values or
 * parameters of messages. Allowed Java types of interop values are:
 * <ul>
 * <li>{@link TruffleObject}: Any subclass of {@link TruffleObject} is interpreted depending on the
 * interop messages it {@link ExportLibrary exports}. Truffle objects are expected but not required
 * to export interop library messages.
 * <li>{@link String} and {@link Character} are interpreted as {@link #isString(Object) string}
 * value.
 * <li>{@link Boolean} is interpreted as {@link #isBoolean(Object) boolean} value.
 * <li>{@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float} and {@link Double}
 * are interpreted as {@link #isNumber(Object) number} values.
 * </ul>
 * <p>
 * The following type combinations are mutually exclusive and cannot return <code>true</code> for
 * the type check message of the same receiver value:
 * <ul>
 * <li>{@link #isNull(Object) Null}
 * <li>{@link #isBoolean(Object) Boolean}
 * <li>{@link #isString(Object) String}
 * <li>{@link #isNumber(Object) Number}
 * <li>{@link #isDate(Object) Date}, {@link #isTime(Object) Time} or {@link #isTimeZone(Object)
 * TimeZone}
 * <li>{@link #isDuration(Object) Duration}
 * </ul>
 * All receiver values may be {@link #isExecutable(Object) executable},
 * {@link #isInstantiable(Object) instantiable}, {@link #isPointer(Object) pointers}, have
 * {@link #hasMembers(Object) members} or {@link #hasArrayElements(Object) array elements} at the
 * same time.
 * <p>
 * <h3>Naive and aware dates and times</h3>
 * <p>
 * If a date or time value has a {@link #isTimeZone(Object) timezone} then it is called <i>aware</i>
 * , otherwise <i>naive</i>
 * <p>
 * An aware time and date has sufficient knowledge of applicable algorithmic and political time
 * adjustments, such as time zone and daylight saving time information, to locate itself relative to
 * other aware objects. An aware object is used to represent a specific moment in time that is not
 * open to interpretation.
 * <p>
 * A naive time and date does not contain enough information to unambiguously locate itself relative
 * to other date/time objects. Whether a naive object represents Coordinated Universal Time (UTC),
 * local time, or time in some other timezone is purely up to the program, just like it is up to the
 * program whether a particular number represents metres, miles, or mass. Naive objects are easy to
 * understand and to work with, at the cost of ignoring some aspects of reality.
 * <p>
 * Interop messages throw {@link InteropException checked exceptions} to indicate error states. The
 * target language is supposed to always catch those exceptions and translate them into guest
 * language errors of the target language. Interop message contracts are verified only if assertions
 * (-ea) are enabled.
 *
 * @see com.oracle.truffle.api.library Reference documentation of Truffle Libraries.
 * @since 19.0
 */
@GenerateLibrary(assertions = Asserts.class, receiverType = TruffleObject.class)
@DefaultExport(DefaultBooleanExports.class)
@DefaultExport(DefaultIntegerExports.class)
@DefaultExport(DefaultByteExports.class)
@DefaultExport(DefaultShortExports.class)
@DefaultExport(DefaultLongExports.class)
@DefaultExport(DefaultFloatExports.class)
@DefaultExport(DefaultDoubleExports.class)
@DefaultExport(DefaultCharacterExports.class)
@DefaultExport(DefaultStringExports.class)
@DefaultExport(DefaultTruffleObjectExports.class)
@SuppressWarnings("unused")
public abstract class InteropLibrary extends Library {

    /**
     * @since 19.0
     */
    protected InteropLibrary() {
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. Invoking this message does not cause any observable side-effects.
     *
     * @since 19.0
     */
    public boolean isNull(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>boolean</code> like value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #asBoolean(Object)
     * @since 19.0
     */
    // Boolean Messages
    @Abstract(ifExported = "asBoolean")
    public boolean isBoolean(Object receiver) {
        return false;
    }

    /**
     * Returns the Java boolean value if the receiver represents a {@link #isBoolean(Object)
     * boolean} like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isBoolean(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isBoolean(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isBoolean")
    public boolean asBoolean(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the receiver represents an <code>executable</code> value, else
     * <code>false</code>. Functions, methods or closures are common examples of executable values.
     * Invoking this message does not cause any observable side-effects. Note that receiver values
     * which are {@link #isExecutable(Object) executable} might also be
     * {@link #isInstantiable(Object) instantiable}.
     *
     * @see #execute(Object, Object...)
     * @since 19.0
     */
    @Abstract(ifExported = "execute")
    public boolean isExecutable(Object receiver) {
        return false;
    }

    /**
     * Executes an executable value with the given arguments.
     *
     * @throws UnsupportedTypeException if one of the arguments is not compatible to the executable
     *             signature
     * @throws ArityException if the number of expected arguments does not match the number of
     *             actual arguments.
     * @throws UnsupportedMessageException if and only if {@link #isExecutable(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isExecutable(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isExecutable")
    public Object execute(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    // Instantiable Messages
    /**
     * Returns <code>true</code> if the receiver represents an <code>instantiable</code> value, else
     * <code>false</code>. Contructors or meta-objects are typical examples of instantiable values.
     * Invoking this message does not cause any observable side-effects. Note that receiver values
     * which are {@link #isExecutable(Object) executable} might also be
     * {@link #isInstantiable(Object) instantiable}.
     *
     * @see #instantiate(Object, Object...)
     * @since 19.0
     */
    @Abstract(ifExported = "instantiate")
    public boolean isInstantiable(Object receiver) {
        return false;
    }

    /**
     * Instantiates the receiver value with the given arguments.
     *
     * @throws UnsupportedTypeException if one of the arguments is not compatible to the executable
     *             signature
     * @throws ArityException if the number of expected arguments does not match the number of
     *             actual arguments.
     * @throws UnsupportedMessageException if and only if {@link #isInstantiable(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isExecutable(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isInstantiable")
    public Object instantiate(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    // String Messages
    /**
     * Returns <code>true</code> if the receiver represents a <code>string</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #asString(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "asString")
    public boolean isString(Object receiver) {
        return false;
    }

    /**
     * Returns the Java boolean value if the receiver represents a {@link #isBoolean(Object)
     * boolean} like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isString(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isBoolean(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isString")
    public String asString(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    // Number Messages
    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see #fitsInByte(Object)
     * @see #fitsInShort(Object)
     * @see #fitsInInt(Object)
     * @see #fitsInLong(Object)
     * @see #fitsInFloat(Object)
     * @see #fitsInDouble(Object)
     * @see #asByte(Object)
     * @see #asShort(Object)
     * @see #asInt(Object)
     * @see #asLong(Object)
     * @see #asFloat(Object)
     * @see #asDouble(Object)
     * @since 19.0
     */
    @Abstract(ifExported = {"fitsInByte", "fitsInShort", "fitsInInt", "fitsInLong", "fitsInFloat", "fitsInDouble", "asByte", "asShort", "asInt", "asLong", "asFloat", "asDouble"})
    public boolean isNumber(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java byte primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asByte(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public boolean fitsInByte(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java short primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asShort(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public boolean fitsInShort(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java int primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asInt(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public boolean fitsInInt(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java long primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asLong(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public boolean fitsInLong(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java float primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asFloat(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public boolean fitsInFloat(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java double primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see #isNumber(Object)
     * @see #asDouble(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public boolean fitsInDouble(Object receiver) {
        return false;
    }

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInByte(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public byte asByte(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the receiver value as Java short primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInShort(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public short asShort(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the receiver value as Java int primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInInt(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public int asInt(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the receiver value as Java long primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInLong(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public long asLong(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the receiver value as Java float primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInFloat(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public float asFloat(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the receiver value as Java double primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver is not a
     *             {@link #isNumber(Object)} or it does not fit without less of precision.
     * @see #isNumber(Object)
     * @see #fitsInDouble(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isNumber")
    public double asDouble(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    // Member Messages
    /**
     * Returns <code>true</code> if the receiver may have members. Members are structural elements
     * of a class. For example, a method or field is a member of a class. Invoking this message does
     * not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #getMembers(Object, boolean)
     * @see #isMemberReadable(Object, String)
     * @see #isMemberModifiable(Object, String)
     * @see #isMemberInvocable(Object, String)
     * @see #isMemberInsertable(Object, String)
     * @see #isMemberRemovable(Object, String)
     * @see #readMember(Object, String)
     * @see #writeMember(Object, String, Object)
     * @see #removeMember(Object, String)
     * @see #invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Abstract(ifExported = {"getMembers", "isMemberReadable", "readMember", "isMemberModifiable", "isMemberInsertable", "writeMember", "isMemberRemovable", "removeMember", "isMemberInvocable",
                    "invokeMember", "isMemberInternal", "hasMemberReadSideEffects", "hasMemberWriteSideEffects"})
    public boolean hasMembers(Object receiver) {
        return false;
    }

    /**
     * Returns an array of member name strings. The returned value must return <code>true</code> for
     * {@link #hasArrayElements(Object)} and every array element must be of type
     * {@link #isString(Object) string}.
     * <p>
     * If the includeInternal argument is <code>true</code> then internal member names are returned
     * as well. Internal members are implementation specific and should not be exposed to guest
     * language application. An example of internal members are internal slots in ECMAScript.
     *
     * @throws UnsupportedMessageException if and only if the receiver does not have any
     *             {@link #hasMembers(Object) members}.
     * @see #hasMembers(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "hasMembers")
    public Object getMembers(Object receiver, boolean includeInternal) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Short-cut for {@link #getMembers(Object) getMembers(receiver, false)}. Invoking this message
     * does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver has no
     *             {@link #hasMembers(Object) members}.
     * @see #getMembers(Object, boolean)
     * @since 19.0
     */
    public final Object getMembers(Object receiver) throws UnsupportedMessageException {
        return getMembers(receiver, false);
    }

    /**
     * Returns <code>true</code> if a given member is {@link #readMember(Object, String) readable}.
     * This method may only return <code>true</code> if {@link #hasMembers(Object)} returns
     * <code>true</code> as well and {@link #isMemberInsertable(Object, String)} returns
     * <code>false</code>. Invoking this message does not cause any observable side-effects. Returns
     * <code>false</code> by default.
     *
     * @see #readMember(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "readMember")
    public boolean isMemberReadable(Object receiver, String member) {
        return false;
    }

    /**
     * Reads the value of a given member. If the member is {@link #isMemberReadable(Object, String)
     * readable} and {@link #isMemberInvocable(Object, String) invocable} then the result of reading
     * the member is {@link #isExecutable(Object) executable} and is bound to this receiver. This
     * method must have not observable side-effects unless
     * {@link #hasMemberReadSideEffects(Object, String)} returns <code>true</code>.
     *
     * @throws UnsupportedMessageException if the member is not readable
     * @throws UnknownIdentifierException if the given member does not exist.
     * @see #hasMemberReadSideEffects(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "isMemberReadable")
    public Object readMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if a given member is existing and
     * {@link #writeMember(Object, String, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    @Abstract(ifExported = "writeMember")
    public boolean isMemberModifiable(Object receiver, String member) {
        return false;
    }

    /**
     * Returns <code>true</code> if a given member is not existing and
     * {@link #writeMember(Object, String, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberExisting(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    @Abstract(ifExported = "writeMember")
    public boolean isMemberInsertable(Object receiver, String member) {
        return false;
    }

    /**
     * Writes the value of a given member. Writing a member is allowed if is existing and
     * {@link #isMemberModifiable(Object, String) modifiable}, or not existing and
     * {@link #isMemberInsertable(Object, String) insertable}.
     *
     * This method must have not observable side-effects other than the changed member unless
     * {@link #hasMemberWriteSideEffects(Object, String) side-effects} are allowed.
     *
     * @throws UnsupportedMessageException if the member is not writable
     * @throws UnknownIdentifierException if the given member is not insertable and does not exist.
     * @throws UnsupportedTypeException if the provided value type is not allowed to be written
     * @see #hasMemberWriteSideEffects(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = {"isMemberModifiable", "isMemberInsertable"})
    public void writeMember(Object receiver, String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if a given member is existing and removable. This method may only
     * return <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #removeMember(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "removeMember")
    public boolean isMemberRemovable(Object receiver, String member) {
        return false;
    }

    /**
     * Removes a member from the receiver object. Removing member is allowed if is
     * {@link #isMemberRemovable(Object, String) removable}.
     *
     * This method does not have not observable side-effects other than the removed member.
     *
     * @throws UnsupportedMessageException if the member is not removable
     * @throws UnknownIdentifierException if the given member is not existing but removing would be
     *             allowed
     * @see #isMemberRemovable(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "isMemberRemovable")
    public void removeMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if a given member is invocable. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Abstract(ifExported = "invokeMember")
    public boolean isMemberInvocable(Object receiver, String member) {
        return false;
    }

    /**
     * Invokes a member for a given receiver and arguments.
     *
     * @throws UnknownIdentifierException if the given member does not exist.
     * @throws UnsupportedTypeException if one of the arguments is not compatible to the executable
     *             signature
     * @throws ArityException if the number of expected arguments does not match the number of
     *             actual arguments.
     * @throws UnsupportedMessageException if the member is not invocable
     * @see #isMemberInvocable(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "isMemberInvocable")
    public Object invokeMember(Object receiver, String member, Object... arguments)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns true if a member is internal. Internal members are not enumerated by
     * {@link #getMembers(Object, boolean)} by default. Internal members are only relevant to guest
     * language implementations and tools, but not to guest applications or embedders. An example of
     * internal members are internal slots in ECMAScript. Invoking this message does not cause any
     * observable side-effects. Returns <code>false</code> by default.
     *
     * @see #getMembers(Object, boolean)
     * @since 19.0
     */
    public boolean isMemberInternal(Object receiver, String member) {
        return false;
    }

    /**
     * Returns true if the member is {@link #isMemberModifiable(Object, String) modifiable} or
     * {@link #isMemberInsertable(Object, String) insertable}.
     *
     * @since 19.0
     */
    public final boolean isMemberWritable(Object receiver, String member) {
        return isMemberModifiable(receiver, member) || isMemberInsertable(receiver, member);
    }

    /**
     * Returns true if the member is existing. A member is existing if it is
     * {@link #isMemberModifiable(Object, String) modifiable},
     * {@link #isMemberReadable(Object, String) readable}, {@link #isMemberRemovable(Object, String)
     * removable} or {@link #isMemberInvocable(Object, String) invocable}.
     *
     * @since 19.0
     */
    public final boolean isMemberExisting(Object receiver, String member) {
        return isMemberReadable(receiver, member) || isMemberModifiable(receiver, member) || isMemberRemovable(receiver, member) || isMemberInvocable(receiver, member);
    }

    /**
     * Returns <code>true</code> if reading a member may cause a side-effect. Invoking this message
     * does not cause any observable side-effects. A member read does not cause any side-effects by
     * default.
     * <p>
     * For instance in JavaScript a property read may have side-effects if the property has a getter
     * function.
     *
     * @see #readMember(Object, String)
     * @since 19.0
     */
    public boolean hasMemberReadSideEffects(Object receiver, String member) {
        return false;
    }

    /**
     * Returns <code>true</code> if writing a member may cause a side-effect, besides the write
     * operation of the member. Invoking this message does not cause any observable side-effects. A
     * member write does not cause any side-effects by default.
     * <p>
     * For instance in JavaScript a property write may have side-effects if the property has a
     * setter function.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    public boolean hasMemberWriteSideEffects(Object receiver, String member) {
        return false;
    }

    // Array Messages

    /**
     * Returns <code>true</code> if the receiver may have array elements. For example, the contents
     * of an array or list datastructure could be interpreted as array elements. Invoking this
     * message does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #getArraySize(Object)
     * @since 19.0
     */
    @Abstract(ifExported = {"readArrayElement", "writeArrayElement", "removeArrayElement", "isArrayElementModifiable", "isArrayElementRemovable", "isArrayElementReadable", "getArraySize"})
    public boolean hasArrayElements(Object receiver) {
        return false;
    }

    /**
     * Reads the value of an array element by index. This method must have not observable
     * side-effect.
     *
     * @throws UnsupportedMessageException if the array element is not readable.
     * @throws InvalidArrayIndexException if the array index is out of bounds or invalid.
     * @since 19.0
     */
    @Abstract(ifExported = {"hasArrayElements"})
    public Object readArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the array size of the receiver.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasArrayElements(Object)} returns
     *             <code>false</code>.
     * @since 19.0
     */
    @Abstract(ifExported = {"hasArrayElements"})
    public long getArraySize(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if a given array element is {@link #readArrayElement(Object, long)
     * readable}. This method may only return <code>true</code> if {@link #hasArrayElements(Object)}
     * returns <code>true</code> as well. Invoking this message does not cause any observable
     * side-effects. Returns <code>false</code> by default.
     *
     * @see #readArrayElement(Object, long)
     * @since 19.0
     */
    @Abstract(ifExported = {"hasArrayElements"})
    public boolean isArrayElementReadable(Object receiver, long index) {
        return false;
    }

    /**
     * Writes the value of an array element by index. Writing an array element is allowed if is
     * existing and {@link #isArrayElementModifiable(Object, long) modifiable}, or not existing and
     * {@link #isArrayElementInsertable(Object, long) insertable}.
     *
     * This method must have not observable side-effects other than the changed array element.
     *
     * @throws UnsupportedMessageException if the array element is not writable
     * @throws InvalidArrayIndexException if the array element is not insertable and does not exist.
     * @throws UnsupportedTypeException if the provided value type is not allowed to be written
     * @since 19.0
     */
    @Abstract(ifExported = {"isArrayElementModifiable", "isArrayElementInsertable"})
    public void writeArrayElement(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Remove an array element from the receiver object. Removing member is allowed if the array
     * element is {@link #isArrayElementRemovable(Object, long) removable}. This method may only
     * return <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as
     * well and {@link #isArrayElementInsertable(Object, long)} returns <code>false</code>.
     *
     * This method does not have not observable side-effects other than the removed array element.
     *
     * @throws UnsupportedMessageException if the array element is not removable
     * @throws InvalidArrayIndexException if the given array element index is not existing but
     *             removing would be allowed
     * @see #isArrayElementRemovable(Object, long)
     * @since 19.0
     */
    @Abstract(ifExported = "isArrayElementRemovable")
    public void removeArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link #writeArrayElement(Object, long, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as well and
     * {@link #isArrayElementInsertable(Object, long)} returns <code>false</code>. Invoking this
     * message does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeArrayElement(Object, long, Object)
     * @see #isArrayElementInsertable(Object, long)
     * @since 19.0
     */
    @Abstract(ifExported = "writeArrayElement")
    public boolean isArrayElementModifiable(Object receiver, long index) {
        return false;
    }

    /**
     * Returns <code>true</code> if a given array element index is not existing and
     * {@link #writeArrayElement(Object, long, Object) insertable}. This method may only return
     * <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as well and
     * {@link #isArrayElementExisting(Object, long)}} returns <code>false</code>. Invoking this
     * message does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeArrayElement(Object, long, Object)
     * @see #isArrayElementModifiable(Object, long)
     * @since 19.0
     */
    @Abstract(ifExported = "writeArrayElement")
    public boolean isArrayElementInsertable(Object receiver, long index) {
        return false;
    }

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link #removeArrayElement(Object, long) removable}. This method may only return
     * <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as well and
     * {@link #isArrayElementInsertable(Object, long)}} returns <code>false</code>. Invoking this
     * message does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #removeArrayElement(Object, long)
     * @since 19.0
     */
    @Abstract(ifExported = "removeArrayElement")
    public boolean isArrayElementRemovable(Object receiver, long index) {
        return false;
    }

    /**
     * Returns true if the array element is {@link #isArrayElementModifiable(Object, long)
     * modifiable} or {@link #isArrayElementInsertable(Object, long) insertable}.
     *
     * @since 19.0
     */
    public final boolean isArrayElementWritable(Object receiver, long index) {
        return isArrayElementModifiable(receiver, index) || isArrayElementInsertable(receiver, index);
    }

    /**
     * Returns true if the array element is existing. An array element is existing if it is,
     * {@link #isArrayElementModifiable(Object, long) modifiable},
     * {@link #isArrayElementReadable(Object, long) readable} or
     * {@link #isArrayElementRemovable(Object, long) removable}.
     *
     * @since 19.0
     */
    public final boolean isArrayElementExisting(Object receiver, long index) {
        return isArrayElementModifiable(receiver, index) || isArrayElementReadable(receiver, index) || isArrayElementRemovable(receiver, index);
    }

    /**
     * Returns <code>true</code> if the receiver value represents a native pointer. Native pointers
     * are represented as 64 bit pointers. Invoking this message does not cause any observable
     * side-effects. Returns <code>false</code> by default.
     * <p>
     * It is expected that objects should only return <code>true</code> if the native pointer value
     * corresponding to this object already exists, and obtaining it is a cheap operation. If an
     * object can be transformed to a pointer representation, but this hasn't happened yet, the
     * object is expected to return <code>false</code> with {@link #isPointer(Object)}, and wait for
     * the {@link #toNative(Object)} message to trigger the transformation.
     *
     * @see #asPointer(Object)
     * @see #toNative(Object)
     * @since 19.0
     */
    @Abstract(ifExported = {"asPointer"})
    public boolean isPointer(Object receiver) {
        return false;
    }

    /**
     * Returns the pointer value as long value if the receiver represents a pointer like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isPointer(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isPointer(Object)
     * @since 19.0
     */
    @Abstract(ifExported = {"isPointer"})
    public long asPointer(Object receiver) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Transforms a {@link TruffleObject receiver} to a value that represents a raw native pointer.
     * After the transformation, the provided receiver returns true for {@link #isPointer(Object)}
     * and can be unwrapped using the {@link #asPointer(Object)} message.
     *
     * @see #isPointer(Object)
     * @see #asPointer(Object)
     * @since 19.0
     */
    public void toNative(Object receiver) {
    }

    /**
     * Returns the receiver as instant if this object represents an {@link #isInstant(Object)
     * instant}. If a value is an instant then it is also a {@link #isDate(Object) date},
     * {@link #isTime(Object) time} and {@link #isTimeZone(Object) timezone}. Using this method may
     * be more efficient than reconstructing the timestamp from the date, time and timezone data.
     * <p>
     * Implementers should implement this method if they can provide a more efficient conversion to
     * Instant than reconstructing it from date, time and timezone date. Implementers must ensure
     * that the following Java code snippet always holds:
     *
     * <pre>
     * ZoneId zone = getTimeZone(receiver);
     * LocalDate date = getDate(receiver);
     * LocalTime time = getTime(receiver);
     * assert ZonedDateTime.of(date, time, zone).toInstant().equals(getInstant(receiver));
     * </pre>
     *
     * @see #isDate(Object)
     * @see #isTime(Object)
     * @see #isTimeZone(Object)
     * @throws UnsupportedMessageException if {@link #isInstant(Object)} returns <code>false</code>.
     * @since 20.0.0 beta 2
     */
    public Instant asInstant(Object receiver) throws UnsupportedMessageException {
        if (isDate(receiver) && isTime(receiver) && isTimeZone(receiver)) {
            LocalDate date = asDate(receiver);
            LocalTime time = asTime(receiver);
            ZoneId zone = asTimeZone(receiver);
            return toInstant(date, time, zone);
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    private static Instant toInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    /**
     * Returns <code>true</code> if the receiver represents an instant. If a value is an instant
     * then it is also a {@link #isDate(Object) date}, {@link #isTime(Object) time} and
     * {@link #isTimeZone(Object) timezone}.
     *
     * This method is short-hand for:
     *
     * <pre>
     * {@linkplain #isDate(Object) isDate}(v) && {@link #isTime(Object) isTime}(v) && {@link #isTimeZone(Object) isTimeZone}(v)
     * </pre>
     *
     * @see #isDate(Object)
     * @see #isTime(Object)
     * @see #isInstant(Object)
     * @see #asInstant(Object)
     * @since 20.0.0 beta 2
     */
    public final boolean isInstant(Object receiver) {
        return isDate(receiver) && isTime(receiver) && isTimeZone(receiver);
    }

    /**
     * Returns <code>true</code> if this object represents a timezone, else <code>false</code>. The
     * interpretation of timezone objects may vary:
     * <ul>
     * <li>If {@link #isDate(Object)} and {@link #isTime(Object)} return <code>true</code>, then the
     * returned date or time information is aware of this timezone.
     * <li>If {@link #isDate(Object)} and {@link #isTime(Object)} returns <code>false</code>, then
     * it represents just timezone information.
     * </ul>
     * Objects with only time or only date information must not have timezone information attached,
     * as aware date or time information always consist of both date and time. If this rule is
     * violated then an {@link AssertionError} is thrown if assertions are enabled.
     * <p>
     * If this method is implemented then also {@link #asTimeZone(Object)} must be implemented.
     *
     * @see #asTimeZone(Object)
     * @see #asInstant(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"asTimeZone", "asInstant"})
    public boolean isTimeZone(Object receiver) {
        return false;
    }

    /**
     * Returns the receiver as timestamp if this object represents a {@link #isTimeZone(Object)
     * timezone}.
     *
     * @throws UnsupportedMessageException if {@link #isTimeZone(Object)} returns <code>false</code>
     *             .
     * @see #isTimeZone(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"isTimeZone", "asInstant"})
    public ZoneId asTimeZone(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if this object represents a date, else <code>false</code>. If the
     * receiver is also a {@link #isTimeZone(Object) timezone} then the date is aware, otherwise it
     * is naive.
     *
     * @see #asDate(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"asDate", "asInstant"})
    public boolean isDate(Object receiver) {
        return false;
    }

    /**
     * Returns the receiver as date if this object represents a {@link #isDate(Object) date}. The
     * returned date is either aware if the receiver has a {@link #isTimeZone(Object) timezone}
     * otherwise it is naive.
     *
     * @throws UnsupportedMessageException if {@link #isDate(Object)} returns <code>false</code>.
     * @see #isDate(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"isTime", "asInstant"})
    public LocalDate asDate(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if this object represents a time, else <code>false</code>. If the
     * receiver is also a {@link #isTimeZone(Object) timezone} then the time is aware, otherwise it
     * is naive.
     *
     * @see #asTime(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"asTime", "asInstant"})
    public boolean isTime(Object receiver) {
        return false;
    }

    /**
     * Returns the receiver as time if this object represents a {@link #isTime(Object) time}. The
     * returned time is either aware if the receiver has a {@link #isTimeZone(Object) timezone}
     * otherwise it is naive.
     *
     * @throws UnsupportedMessageException if {@link #isTime(Object)} returns <code>false</code>.
     * @see #isTime(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"isTime", "asInstant"})
    public LocalTime asTime(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if this object represents a duration, else <code>false</code>.
     *
     * @see Duration
     * @see #asDate(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"asDuration"})
    public boolean isDuration(Object receiver) {
        return false;
    }

    /**
     * Returns the receiver as duration if this object represents a {@link #isDuration(Object)
     * duration}.
     *
     * @throws UnsupportedMessageException if {@link #isDuration(Object)} returns <code>false</code>
     *             .
     * @see #isDuration(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"isDuration"})
    public Duration asDuration(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the library factory for the interop library. Short-cut for
     * {@link LibraryFactory#resolve(Class) ResolvedLibrary.resolve(InteropLibrary.class)}.
     *
     * @see LibraryFactory#resolve(Class)
     * @since 19.0
     */
    public static LibraryFactory<InteropLibrary> getFactory() {
        return FACTORY;
    }

    /**
     * Utility for libraries to require adoption before cached versions of nodes can be executed.
     * Only failes if assertions (-ea) are enabled.
     *
     * @since 19.0
     */
    protected final boolean assertAdopted() {
        assert assertAdoptedImpl();
        return true;
    }

    private boolean assertAdoptedImpl() {
        Node node = this;
        do {
            if (node instanceof RootNode || node instanceof LegacyToLibraryNode || node instanceof InteropAccessNode) {
                // we all nodes with root nodes or
                // unadopted that use the compatibility bridge
                return true;
            }
            node = node.getParent();
        } while (node != null);

        assert false : "Invalid library usage. Cached library must be adopted by a RootNode before it is executed.";
        return true;
    }

    static final LibraryFactory<InteropLibrary> FACTORY = LibraryFactory.resolve(InteropLibrary.class);

    static class Asserts extends InteropLibrary {

        @Child private InteropLibrary delegate;

        public enum Type {
            NULL,
            BOOLEAN,
            DATE_TIME_ZONE,
            DURATION,
            STRING,
            NUMBER,
            POINTER;
        }

        Asserts(InteropLibrary delegate) {
            this.delegate = delegate;
        }

        private static boolean isMultiThreaded(Object receiver) {
            EngineSupport engine = InteropAccessor.ACCESSOR.engineSupport();
            if (engine == null) {
                return false;
            }
            return engine.isMultiThreaded(receiver);
        }

        @Override
        public boolean accepts(Object receiver) {
            assert preCondition(receiver);
            return delegate.accepts(receiver);
        }

        @Override
        public boolean isNull(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isNull(receiver);
            assert !result || notOtherType(receiver, Type.NULL);
            return result;
        }

        private boolean notOtherType(Object receiver, Type type) {
            assert type == Type.NULL || !delegate.isNull(receiver) : violationInvariant(receiver);
            assert type == Type.BOOLEAN || !delegate.isBoolean(receiver) : violationInvariant(receiver);
            assert type == Type.STRING || !delegate.isString(receiver) : violationInvariant(receiver);
            assert type == Type.NUMBER || !delegate.isNumber(receiver) : violationInvariant(receiver);
            assert type == Type.DATE_TIME_ZONE || (!delegate.isDate(receiver) && !delegate.isTime(receiver) && !delegate.isTimeZone(receiver)) : violationInvariant(receiver);
            assert type == Type.DURATION || !delegate.isDuration(receiver) : violationInvariant(receiver);
            return true;
        }

        @Override
        public boolean isBoolean(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isBoolean(receiver);
            if (result) {
                try {
                    delegate.asBoolean(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !result || notOtherType(receiver, Type.BOOLEAN);
            return result;
        }

        @Override
        public boolean asBoolean(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean wasBoolean = delegate.isBoolean(receiver);
            try {
                boolean result = delegate.asBoolean(receiver);
                assert wasBoolean : violationInvariant(receiver);
                assert notOtherType(receiver, Type.BOOLEAN);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasBoolean : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isExecutable(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isExecutable(receiver);
            return result;
        }

        @Override
        public Object execute(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            assert preCondition(receiver);
            assert validArguments(receiver, arguments);
            boolean wasExecutable = delegate.isExecutable(receiver);
            try {
                Object result = delegate.execute(receiver, arguments);
                assert wasExecutable : violationInvariant(receiver, arguments);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof ArityException || e instanceof UnsupportedTypeException : violationInvariant(receiver, arguments);
                assert !(e instanceof UnsupportedMessageException) || !wasExecutable : violationInvariant(receiver, arguments);
                throw e;
            }
        }

        @Override
        public boolean isInstantiable(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isInstantiable(receiver);
            return result;
        }

        @Override
        public Object instantiate(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            assert preCondition(receiver);
            assert validArguments(receiver, arguments);
            boolean wasInstantiable = delegate.isInstantiable(receiver);
            try {
                Object result = delegate.instantiate(receiver, arguments);
                assert wasInstantiable : violationInvariant(receiver, arguments);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof ArityException || e instanceof UnsupportedTypeException : violationInvariant(receiver, arguments);
                assert !(e instanceof UnsupportedMessageException) || !wasInstantiable : violationInvariant(receiver, arguments);
                throw e;
            }
        }

        @Override
        public boolean isString(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isString(receiver);
            if (result) {
                try {
                    delegate.asString(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !result || notOtherType(receiver, Type.STRING);
            return result;
        }

        @Override
        public String asString(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean wasString = delegate.isString(receiver);
            try {
                String result = delegate.asString(receiver);
                assert wasString : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasString : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isNumber(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isNumber(receiver);
            assert !result || notOtherType(receiver, Type.NUMBER);
            return result;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            assert preCondition(receiver);
            boolean fits = delegate.fitsInByte(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInShort(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInInt(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInLong(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInFloat(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInDouble(receiver) : violationInvariant(receiver);
            if (fits) {
                try {
                    delegate.asByte(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !fits || notOtherType(receiver, Type.NUMBER);
            return fits;
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            assert preCondition(receiver);

            boolean fits = delegate.fitsInShort(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInInt(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInLong(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInFloat(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInDouble(receiver) : violationInvariant(receiver);
            if (fits) {
                try {
                    delegate.asShort(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !fits || notOtherType(receiver, Type.NUMBER);
            return fits;
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            assert preCondition(receiver);

            boolean fits = delegate.fitsInInt(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInLong(receiver) : violationInvariant(receiver);
            assert !fits || delegate.fitsInDouble(receiver) : violationInvariant(receiver);
            if (fits) {
                try {
                    delegate.asInt(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !fits || notOtherType(receiver, Type.NUMBER);
            return fits;
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            assert preCondition(receiver);

            boolean fits = delegate.fitsInLong(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            if (fits) {
                try {
                    delegate.asLong(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !fits || notOtherType(receiver, Type.NUMBER);
            return fits;
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            assert preCondition(receiver);
            boolean fits = delegate.fitsInFloat(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            if (fits) {
                try {
                    delegate.asFloat(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !fits || notOtherType(receiver, Type.NUMBER);
            return fits;
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            assert preCondition(receiver);
            boolean fits = delegate.fitsInDouble(receiver);
            assert !fits || delegate.isNumber(receiver) : violationInvariant(receiver);
            if (fits) {
                try {
                    delegate.asDouble(receiver);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            }
            assert !fits || notOtherType(receiver, Type.NUMBER);
            return fits;
        }

        @Override
        public byte asByte(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);

            try {
                byte result = delegate.asByte(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInByte(receiver) : violationInvariant(receiver);
                assert result == delegate.asShort(receiver) : violationInvariant(receiver);
                assert result == delegate.asInt(receiver) : violationInvariant(receiver);
                assert result == delegate.asLong(receiver) : violationInvariant(receiver);
                assert result == delegate.asFloat(receiver) : violationInvariant(receiver);
                assert result == delegate.asDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public short asShort(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                short result = delegate.asShort(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInShort(receiver) : violationInvariant(receiver);
                assert result == delegate.asInt(receiver) : violationInvariant(receiver);
                assert result == delegate.asLong(receiver) : violationInvariant(receiver);
                assert result == delegate.asFloat(receiver) : violationInvariant(receiver);
                assert result == delegate.asDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public int asInt(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                int result = delegate.asInt(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInInt(receiver) : violationInvariant(receiver);
                assert result == delegate.asLong(receiver) : violationInvariant(receiver);
                assert result == delegate.asDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public long asLong(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                long result = delegate.asLong(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInLong(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public float asFloat(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                float result = delegate.asFloat(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInFloat(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public double asDouble(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                double result = delegate.asDouble(receiver);
                assert delegate.isNumber(receiver) : violationInvariant(receiver);
                assert delegate.fitsInDouble(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasMembers(Object receiver) {
            assert preCondition(receiver);
            return delegate.hasMembers(receiver);
        }

        @Override
        public Object readMember(Object receiver, String identifier) throws UnsupportedMessageException, UnknownIdentifierException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean wasReadable = delegate.isMemberReadable(receiver, identifier);
            try {
                Object result = delegate.readMember(receiver, identifier);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasReadable || isMultiThreaded(receiver) : violationInvariant(receiver, identifier);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownIdentifierException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeMember(Object receiver, String identifier, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            assert validArgument(receiver, value);
            boolean wasWritable = (delegate.isMemberModifiable(receiver, identifier) || delegate.isMemberInsertable(receiver, identifier));
            try {
                delegate.writeMember(receiver, identifier, value);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasWritable || isMultiThreaded(receiver) : violationInvariant(receiver, identifier);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownIdentifierException || e instanceof UnsupportedTypeException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void removeMember(Object receiver, String identifier) throws UnsupportedMessageException, UnknownIdentifierException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean wasRemovable = delegate.isMemberRemovable(receiver, identifier);
            try {
                delegate.removeMember(receiver, identifier);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasRemovable || isMultiThreaded(receiver) : violationInvariant(receiver, identifier);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownIdentifierException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public Object invokeMember(Object receiver, String identifier, Object... arguments) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            assert validArguments(receiver, arguments);
            boolean wasInvocable = delegate.isMemberInvocable(receiver, identifier);
            try {
                Object result = delegate.invokeMember(receiver, identifier, arguments);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasInvocable || isMultiThreaded(receiver) : violationInvariant(receiver, identifier);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof ArityException || e instanceof UnknownIdentifierException ||
                                e instanceof UnsupportedTypeException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public Object getMembers(Object receiver, boolean internal) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                Object result = delegate.getMembers(receiver, internal);
                assert validReturn(receiver, result);
                assert isMultiThreaded(receiver) || assertMemberKeys(receiver, result, internal);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        private static boolean assertMemberKeys(Object receiver, Object result, boolean internal) {
            assert result != null : violationPost(receiver, result);
            InteropLibrary uncached = InteropLibrary.getFactory().getUncached(result);
            assert uncached.hasArrayElements(result) : violationPost(receiver, result);
            long arraySize;
            try {
                arraySize = uncached.getArraySize(result);
            } catch (UnsupportedMessageException e) {
                assert false : violationPost(receiver, e);
                return true;
            }
            for (int i = 0; i < arraySize; i++) {
                assert uncached.isArrayElementReadable(result, i) : violationPost(receiver, result);
                Object element;
                try {
                    element = uncached.readArrayElement(result, i);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    assert false : violationPost(receiver, result);
                    return true;
                }
                assert InteropLibrary.getFactory().getUncached().isString(element) : violationPost(receiver, element);
                try {
                    InteropLibrary.getFactory().getUncached().asString(element);
                } catch (UnsupportedMessageException e) {
                    assert false : violationInvariant(result, i);
                }
            }
            return true;
        }

        @Override
        public boolean hasMemberReadSideEffects(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.hasMemberReadSideEffects(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            assert !result || (delegate.isMemberReadable(receiver, identifier) || isMultiThreaded(receiver)) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean hasMemberWriteSideEffects(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.hasMemberWriteSideEffects(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            assert !result || (delegate.isMemberWritable(receiver, identifier) || isMultiThreaded(receiver)) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberReadable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberReadable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberModifiable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberModifiable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberInsertable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberInsertable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberExisting(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberRemovable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberRemovable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberInvocable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberInvocable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isMemberInternal(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validArgument(receiver, identifier);
            boolean result = delegate.isMemberInternal(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            assert preCondition(receiver);
            return delegate.hasArrayElements(receiver);
        }

        @Override
        public Object readArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            assert preCondition(receiver);
            boolean wasReadable = delegate.isArrayElementReadable(receiver, index);
            try {
                Object result = delegate.readArrayElement(receiver, index);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver, index);
                assert wasReadable || isMultiThreaded(receiver) : violationInvariant(receiver, index);
                assert validReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof InvalidArrayIndexException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeArrayElement(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
            assert preCondition(receiver);
            assert validArgument(receiver, value);
            boolean wasWritable = delegate.isArrayElementModifiable(receiver, index) || delegate.isArrayElementInsertable(receiver, index);
            try {
                delegate.writeArrayElement(receiver, index, value);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver, index);
                assert wasWritable || isMultiThreaded(receiver) : violationInvariant(receiver, index);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnsupportedTypeException || e instanceof InvalidArrayIndexException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void removeArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            assert preCondition(receiver);
            boolean wasRemovable = delegate.isArrayElementRemovable(receiver, index);
            try {
                delegate.removeArrayElement(receiver, index);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver, index);
                assert wasRemovable || isMultiThreaded(receiver) : violationInvariant(receiver, index);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof InvalidArrayIndexException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public long getArraySize(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                long result = delegate.getArraySize(receiver);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public boolean isArrayElementReadable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementReadable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isArrayElementModifiable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementModifiable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isArrayElementInsertable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementInsertable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementExisting(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isArrayElementRemovable(Object receiver, long identifier) {
            assert preCondition(receiver);
            boolean result = delegate.isArrayElementRemovable(receiver, identifier);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            return result;
        }

        @Override
        public boolean isPointer(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isPointer(receiver);
            return result;
        }

        @Override
        public void toNative(Object receiver) {
            assert preCondition(receiver);
            boolean wasPointer = delegate.isPointer(receiver);
            delegate.toNative(receiver);
            assert !wasPointer || delegate.isPointer(receiver) : violationInvariant(receiver);
        }

        @Override
        public long asPointer(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean wasPointer = delegate.isPointer(receiver);
            try {
                long result = delegate.asPointer(receiver);
                assert wasPointer : violationInvariant(receiver);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasPointer : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public LocalDate asDate(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean hasDate = delegate.isDate(receiver);
            try {
                LocalDate result = delegate.asDate(receiver);
                assert hasDate : violationInvariant(receiver);
                assert !delegate.isTimeZone(receiver) || delegate.isTime(receiver) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hasDate : violationInvariant(receiver);
                assert !delegate.isTimeZone(receiver) || !delegate.isTime(receiver) : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public LocalTime asTime(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean hasTime = delegate.isTime(receiver);
            try {
                LocalTime result = delegate.asTime(receiver);
                assert hasTime : violationInvariant(receiver);
                assert !delegate.isTimeZone(receiver) || delegate.isDate(receiver) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hasTime : violationInvariant(receiver);
                assert !delegate.isTimeZone(receiver) || !delegate.isDate(receiver) : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public ZoneId asTimeZone(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean hasTimeZone = delegate.isTimeZone(receiver);
            try {
                ZoneId result = delegate.asTimeZone(receiver);
                assert hasTimeZone : violationInvariant(receiver);
                assert (delegate.isDate(receiver) && delegate.isTime(receiver)) || (!delegate.isDate(receiver) && !delegate.isTime(receiver)) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hasTimeZone : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public Duration asDuration(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean wasDuration = delegate.isDuration(receiver);
            try {
                Duration result = delegate.asDuration(receiver);
                assert wasDuration : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DURATION);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasDuration : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public Instant asInstant(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            boolean hasDateAndTime = delegate.isDate(receiver) && delegate.isTime(receiver) && delegate.isTimeZone(receiver);
            try {
                Instant result = delegate.asInstant(receiver);
                assert hasDateAndTime : violationInvariant(receiver);
                assert ZonedDateTime.of(delegate.asDate(receiver), delegate.asTime(receiver),
                                delegate.asTimeZone(receiver)).//
                                toInstant().equals(result) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hasDateAndTime : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isDate(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isDate(receiver);
            assert !delegate.isTimeZone(receiver) || (delegate.isTime(receiver) && result) || (!delegate.isTime(receiver) && !result) : violationInvariant(receiver);
            assert !result || notOtherType(receiver, Type.DATE_TIME_ZONE);
            return result;
        }

        @Override
        public boolean isTime(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isTime(receiver);
            assert !delegate.isTimeZone(receiver) || (delegate.isDate(receiver) && result) || (!delegate.isDate(receiver) && !result) : violationInvariant(receiver);
            assert !result || notOtherType(receiver, Type.DATE_TIME_ZONE);
            return result;
        }

        @Override
        public boolean isTimeZone(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isTimeZone(receiver);
            assert !result || (delegate.isDate(receiver) && delegate.isTime(receiver)) || (!delegate.isDate(receiver) && !delegate.isTime(receiver)) : violationInvariant(receiver);
            assert !result || notOtherType(receiver, Type.DATE_TIME_ZONE);
            return result;
        }

        @Override
        public boolean isDuration(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isDuration(receiver);
            assert !result || notOtherType(receiver, Type.DURATION);
            return result;
        }

    }
}
