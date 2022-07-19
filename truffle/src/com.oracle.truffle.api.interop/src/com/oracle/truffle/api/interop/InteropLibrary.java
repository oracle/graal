/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.api.interop.AssertUtils.assertString;
import static com.oracle.truffle.api.interop.AssertUtils.preCondition;
import static com.oracle.truffle.api.interop.AssertUtils.validArguments;
import static com.oracle.truffle.api.interop.AssertUtils.validInteropArgument;
import static com.oracle.truffle.api.interop.AssertUtils.validInteropReturn;
import static com.oracle.truffle.api.interop.AssertUtils.validNonInteropArgument;
import static com.oracle.truffle.api.interop.AssertUtils.validProtocolArgument;
import static com.oracle.truffle.api.interop.AssertUtils.validProtocolReturn;
import static com.oracle.truffle.api.interop.AssertUtils.validScope;
import static com.oracle.truffle.api.interop.AssertUtils.violationInvariant;
import static com.oracle.truffle.api.interop.AssertUtils.violationPost;

import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRules;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.InteropLibrary.Asserts;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

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
 * <li>{@link TruffleObject}: Any object that implements the {@link TruffleObject} interface is
 * interpreted according to the interop messages it {@link ExportLibrary exports}. Truffle objects
 * are expected but not required to export interop library messages.
 * <li>{@link String} and {@link Character} are interpreted as {@link #isString(Object) string}
 * value.
 * <li>{@link Boolean} is interpreted as {@link #isBoolean(Object) boolean} value.
 * <li>{@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float} and {@link Double}
 * are interpreted as {@link #isNumber(Object) number} values.
 * </ul>
 * Note that {@code null} is <i>never</i> a valid interop value. Instead, use a
 * {@link TruffleObject} which implements {@link #isNull(Object)} message.
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
 * <li>{@link #isException(Object) Exception}
 * <li>{@link #isMetaObject(Object) Meta-Object}
 * <li>{@link #isIterator(Object) Iterator}
 * </ul>
 * All receiver values may have none, one or multiple of the following traits:
 * <ul>
 * <li>{@link #isExecutable(Object) executable}
 * <li>{@link #isInstantiable(Object) instantiable}
 * <li>{@link #isPointer(Object) pointer}
 * <li>{@link #hasMembers(Object) members}
 * <li>{@link #hasHashEntries(Object) hash entries}
 * <li>{@link #hasArrayElements(Object) array elements}
 * <li>{@link #hasBufferElements(Object) buffer elements}
 * <li>{@link #hasLanguage(Object) language}
 * <li>{@link #hasMetaObject(Object) associated metaobject}
 * <li>{@link #hasMetaParents(Object) metaobject parents as array elements}
 * <li>{@link #hasDeclaringMetaObject(Object) declaring meta object}
 * <li>{@link #hasSourceLocation(Object) source location}
 * <li>{@link #hasIdentity(Object) identity}
 * <li>{@link #hasScopeParent(Object) scope parent}
 * <li>{@link #hasExecutableName(Object) executable name}
 * <li>{@link #hasExceptionMessage(Object) exception message}
 * <li>{@link #hasExceptionCause(Object) exception cause}
 * <li>{@link #hasExceptionStackTrace(Object) exception stack trace}
 * <li>{@link #hasIterator(Object) iterator}
 * </ul>
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
@DefaultExport(DefaultTStringExports.class)
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
     *             signature. The exception is thrown on best effort basis, dynamic languages may
     *             throw their own exceptions if the arguments are wrong.
     * @throws ArityException if the number of expected arguments does not match the number of
     *             actual arguments.
     * @throws UnsupportedMessageException if and only if {@link #isExecutable(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isExecutable(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isExecutable")
    public Object execute(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns {@code true} if the receiver has an executable name. Invoking this message does not
     * cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #getExecutableName(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"getExecutableName"})
    public boolean hasExecutableName(Object receiver) {
        return false;
    }

    /**
     * Returns executable name of the receiver. Throws {@code UnsupportedMessageException} when the
     * receiver is has no {@link #hasExecutableName(Object) executable name}. The return value is an
     * interop value that is guaranteed to return <code>true</code> for {@link #isString(Object)}.
     *
     * @see #hasExecutableName(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"hasExecutableName"})
    public Object getExecutableName(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns {@code true} if the receiver has a declaring meta object. The declaring meta object
     * is the meta object of the executable or meta object that declares the receiver value.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see #getDeclaringMetaObject(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"getDeclaringMetaObject"})
    public boolean hasDeclaringMetaObject(Object receiver) {
        return false;
    }

    /**
     * Returns declaring meta object. The declaring meta object is the meta object of declaring
     * executable or meta object. Throws {@code UnsupportedMessageException} when the receiver is
     * has no {@link #hasDeclaringMetaObject(Object) declaring meta object}. The return value is an
     * interop value that is guaranteed to return <code>true</code> for
     * {@link #isMetaObject(Object)}.
     *
     * @see #hasDeclaringMetaObject(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"hasDeclaringMetaObject"})
    public Object getDeclaringMetaObject(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    // Instantiable Messages
    /**
     * Returns <code>true</code> if the receiver represents an <code>instantiable</code> value, else
     * <code>false</code>. Contructors or {@link #isMetaObject(Object) metaobjects} are typical
     * examples of instantiable values. Invoking this message does not cause any observable
     * side-effects. Note that receiver values which are {@link #isExecutable(Object) executable}
     * might also be {@link #isInstantiable(Object) instantiable}.
     *
     * @see #instantiate(Object, Object...)
     * @see #isMetaObject(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "instantiate")
    public boolean isInstantiable(Object receiver) {
        return false;
    }

    /**
     * Instantiates the receiver value with the given arguments. The returned object must be
     * initialized correctly according to the language specification (e.g. by calling the
     * constructor or initialization routine).
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
    @Abstract(ifExported = {"asString", "asTruffleString"})
    public boolean isString(Object receiver) {
        return false;
    }

    /**
     * Returns the Java string value if the receiver represents a {@link #isString(Object) string}
     * like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isString(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isString(Object)
     * @since 19.0
     */
    @Abstract(ifExported = "isString")
    public String asString(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the {@link TruffleString} value if the receiver represents a {@link #isString(Object)
     * string} like value.
     *
     * @throws UnsupportedMessageException if and only if {@link #isString(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isString(Object)
     * @since 21.3
     */
    public TruffleString asTruffleString(Object receiver) throws UnsupportedMessageException {
        return TruffleString.fromJavaStringUncached(asString(receiver), TruffleString.Encoding.UTF_16);
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
        throw UnsupportedMessageException.create();
    }

    // Member Messages
    /**
     * Returns <code>true</code> if the receiver may have members. Therefore, at least one of
     * {@link #readMember(Object, String)}, {@link #writeMember(Object, String, Object)},
     * {@link #removeMember(Object, String)}, {@link #invokeMember(Object, String, Object...)} must
     * not throw {@link UnsupportedMessageException}. Members are structural elements of a class.
     * For example, a method or field is a member of a class. Invoking this message does not cause
     * any observable side-effects. Returns <code>false</code> by default.
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
                    "invokeMember", "isMemberInternal", "hasMemberReadSideEffects", "hasMemberWriteSideEffects", "isScope"})
    public boolean hasMembers(Object receiver) {
        return false;
    }

    /**
     * Returns an array of member name strings. The returned value must return <code>true</code> for
     * {@link #hasArrayElements(Object)} and every array element must be of type
     * {@link #isString(Object) string}. The member elements may also provide additional information
     * like {@link #getSourceLocation(Object) source location} in case of {@link #isScope(Object)
     * scope} variables, etc.
     * <p>
     * The order of member names needs to be:
     * <ul>
     * <li>deterministic, assuming the program execution is deterministic,</li>
     * <li>in the declaration order, when applicable,</li>
     * <li>multiple invocations of this method must return the same members in the same order as
     * long as no side-effecting operations were performed.</li>
     * </ul>
     * If the includeInternal argument is <code>true</code> then internal member names are returned
     * as well. Internal members are implementation specific and should not be exposed to guest
     * language application. An example of internal members are internal slots in ECMAScript.
     *
     * @throws UnsupportedMessageException if and only if the receiver does not have any
     *             {@link #hasMembers(Object) members}.
     * @see #hasMembers(Object)
     * @since 19.0
     */
    @Abstract(ifExported = {"hasMembers", "isScope"})
    public Object getMembers(Object receiver, boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Short-cut for {@link #getMembers(Object, boolean) getMembers(receiver, false)}. Invoking this
     * message does not cause any observable side-effects.
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
     * Reads the value of a given member.
     * <p>
     * In case of a method-like member, we recommend that languages return a bound method (or an
     * artificial receiver-method binding) to improve cross-language portability. In this case, the
     * member should be {@link #isMemberReadable(Object, String) readable} and
     * {@link #isMemberInvocable(Object, String) invocable} and the result of reading the member
     * should be {@link #isExecutable(Object) executable} and bound to the receiver.
     * <p>
     * This message must have not observable side-effects unless
     * {@link #hasMemberReadSideEffects(Object, String)} returns <code>true</code>.
     *
     * @throws UnsupportedMessageException if when the receiver does not support reading at all. An
     *             empty receiver with no readable members supports the read operation (even though
     *             there is nothing to read), therefore it throws {@link UnknownIdentifierException}
     *             for all arguments instead.
     * @throws UnknownIdentifierException if the given member cannot be read, e.g. because it is not
     *             (or no longer) {@link #isMemberReadable(Object, String) readable} such as when
     *             the member with the given name does not exist or has been removed.
     * @see #hasMemberReadSideEffects(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "isMemberReadable")
    public Object readMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException {
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
     * This method must have no observable side-effects other than the changed member unless
     * {@link #hasMemberWriteSideEffects(Object, String) side-effects} are allowed.
     *
     * @throws UnsupportedMessageException when the receiver does not support writing at all, e.g.
     *             when it is immutable.
     * @throws UnknownIdentifierException if the given member is not
     *             {@link #isMemberModifiable(Object, String) modifiable} nor
     *             {@link #isMemberInsertable(Object, String) insertable}.
     * @throws UnsupportedTypeException if the provided value type is not allowed to be written.
     * @see #hasMemberWriteSideEffects(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = {"isMemberModifiable", "isMemberInsertable"})
    public void writeMember(Object receiver, String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
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
     * @throws UnsupportedMessageException when the receiver does not support removing at all, e.g.
     *             when it is immutable.
     * @throws UnknownIdentifierException if the given member is not
     *             {@link #isMemberRemovable(Object, String)} removable}, e.g. the receiver does not
     *             have a member with the given name.
     * @see #isMemberRemovable(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "isMemberRemovable")
    public void removeMember(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException {
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
     * @throws UnknownIdentifierException if the given member does not exist or is not
     *             {@link #isMemberInvocable(Object, String) invocable}.
     * @throws UnsupportedTypeException if one of the arguments is not compatible to the executable
     *             signature. The exception is thrown on best effort basis, dynamic languages may
     *             throw their own exceptions if the arguments are wrong.
     * @throws ArityException if the number of expected arguments does not match the number of
     *             actual arguments.
     * @throws UnsupportedMessageException when the receiver does not support invoking at all, e.g.
     *             when storing executable members is not allowed.
     * @see #isMemberInvocable(Object, String)
     * @since 19.0
     */
    @Abstract(ifExported = "isMemberInvocable")
    public Object invokeMember(Object receiver, String member, Object... arguments)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
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

    // Hashes
    /**
     * Returns {@code true} if the receiver may have hash entries. Therefore, at least one of
     * {@link #readHashValue(Object, Object)}, {@link #writeHashEntry(Object, Object, Object)},
     * {@link #removeHashEntry(Object, Object)} must not throw {@link UnsupportedMessageException}.
     * For example, the contents of a map data structure could be interpreted as hash elements.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see #getHashEntriesIterator(Object)
     * @see #getHashSize(Object)
     * @see #isHashEntryReadable(Object, Object)
     * @see #isHashEntryWritable(Object, Object)
     * @see #isHashEntryInsertable(Object, Object)
     * @see #isHashEntryRemovable(Object, Object)
     * @see #readHashValue(Object, Object)
     * @see #readHashValueOrDefault(Object, Object, Object)
     * @see #writeHashEntry(Object, Object, Object)
     * @see #removeHashEntry(Object, Object)
     * @since 21.1
     */
    @Abstract(ifExported = {"getHashSize", "isHashEntryReadable", "readHashValue", "readHashValueOrDefault",
                    "isHashEntryModifiable", "isHashEntryInsertable", "writeHashEntry", "isHashEntryRemovable",
                    "removeHashEntry", "getHashEntriesIterator", "getHashKeysIterator", "getHashValuesIterator"})
    public boolean hasHashEntries(Object receiver) {
        return false;
    }

    /**
     * Returns the number of receiver entries.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasHashEntries(Object)} returns
     *             {@code false}.
     * @since 21.1
     */
    @Abstract(ifExported = "hasHashEntries")
    public long getHashSize(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns {@code true} if mapping for the specified key exists and is
     * {@link #readHashValue(Object, Object) readable}. This method may only return {@code true} if
     * {@link #hasHashEntries(Object)} returns {@code true} as well and
     * {@link #isHashEntryInsertable(Object, Object)} returns {@code false}. Invoking this message
     * does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #readHashValue(Object, Object)
     * @since 21.1
     */
    @Abstract(ifExported = "readHashValue")
    public boolean isHashEntryReadable(Object receiver, Object key) {
        return false;
    }

    /**
     * Reads the value for the specified key.
     *
     * @throws UnsupportedMessageException if the receiver does not support reading at all. An empty
     *             receiver with no readable hash entries supports the read operation (even though
     *             there is nothing to read), therefore it throws {@link UnknownKeyException} for
     *             all arguments instead.
     * @throws UnknownKeyException if mapping for the specified key is not
     *             {@link #isHashEntryReadable(Object, Object) readable}, e.g. when the hash does
     *             not contain specified key.
     * @see #isHashEntryReadable(Object, Object)
     * @see #readHashValueOrDefault(Object, Object, Object)
     * @since 21.1
     */
    @Abstract(ifExported = "isHashEntryReadable")
    public Object readHashValue(Object receiver, Object key) throws UnsupportedMessageException, UnknownKeyException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Reads the value for the specified key or returns the {@code defaultValue} when the mapping
     * for the specified key does not exist or is not readable.
     *
     * @throws UnsupportedMessageException if the receiver does not support reading at all. An empty
     *             receiver with no readable hash entries supports the read operation (even though
     *             there is nothing to read), therefore it returns the {@code defaultValue} for all
     *             arguments instead.
     * @see #isHashEntryReadable(Object, Object)
     * @see #readHashValue(Object, Object)
     * @since 21.1
     */
    public Object readHashValueOrDefault(Object receiver, Object key, Object defaultValue) throws UnsupportedMessageException {
        try {
            return readHashValue(receiver, key);
        } catch (UnknownKeyException e) {
            return defaultValue;
        }
    }

    /**
     * Returns {@code true} if mapping for the specified key exists and is
     * {@link #writeHashEntry(Object, Object, Object) writable}. This method may only return
     * {@code true} if {@link #hasHashEntries(Object)} returns {@code true} as well and
     * {@link #isHashEntryInsertable(Object, Object)} returns {@code false}. Invoking this message
     * does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #writeHashEntry(Object, Object, Object)
     * @since 21.1
     */
    @Abstract(ifExported = {"writeHashEntry"})
    public boolean isHashEntryModifiable(Object receiver, Object key) {
        return false;
    }

    /**
     * Returns {@code true} if mapping for the specified key does not exist and is
     * {@link #writeHashEntry(Object, Object, Object) writable}. This method may only return
     * {@code true} if {@link #hasHashEntries(Object)} returns {@code true} as well and
     * {@link #isHashEntryExisting(Object, Object)} returns {@code false}. Invoking this message
     * does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #writeHashEntry(Object, Object, Object)
     * @since 21.1
     */
    @Abstract(ifExported = "writeHashEntry")
    public boolean isHashEntryInsertable(Object receiver, Object key) {
        return false;
    }

    /**
     * Returns {@code true} if mapping for the specified key is
     * {@link #isHashEntryModifiable(Object, Object) modifiable} or
     * {@link #isHashEntryInsertable(Object, Object) insertable}.
     *
     * @since 21.1
     */
    public boolean isHashEntryWritable(Object receiver, Object key) {
        return isHashEntryModifiable(receiver, key) || isHashEntryInsertable(receiver, key);
    }

    /**
     * Associates the specified value with the specified key in the receiver. Writing the entry is
     * allowed if is existing and {@link #isHashEntryModifiable(Object, Object) modifiable}, or not
     * existing and {@link #isHashEntryInsertable(Object, Object) insertable}.
     *
     * @throws UnsupportedMessageException when the receiver does not support writing at all, e.g.
     *             when it is immutable.
     * @throws UnknownKeyException if mapping for the specified key is not
     *             {@link #isHashEntryModifiable(Object, Object) modifiable} nor
     *             {@link #isHashEntryInsertable(Object, Object) insertable}.
     * @throws UnsupportedTypeException if the provided key type or value type is not allowed to be
     *             written.
     * @since 21.1
     */
    @Abstract(ifExported = {"isHashEntryModifiable", "isHashEntryInsertable"})
    public void writeHashEntry(Object receiver, Object key, Object value) throws UnsupportedMessageException, UnknownKeyException, UnsupportedTypeException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns {@code true} if mapping for the specified key exists and is removable. This method
     * may only return {@code true} if {@link #hasHashEntries(Object)} returns {@code true} as well
     * and {@link #isHashEntryInsertable(Object, Object)} returns {@code false}. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #removeHashEntry(Object, Object)
     * @since 21.1
     */
    @Abstract(ifExported = "removeHashEntry")
    public boolean isHashEntryRemovable(Object receiver, Object key) {
        return false;
    }

    /**
     * Removes the mapping for a given key from the receiver. Mapping removing is allowed if it is
     * {@link #isHashEntryRemovable(Object, Object) removable}.
     *
     * @throws UnsupportedMessageException when the receiver does not support removing at all, e.g.
     *             when it is immutable.
     * @throws UnknownKeyException if the given mapping is not
     *             {@link #isHashEntryRemovable(Object, Object) removable}, e.g. the receiver does
     *             not have a mapping for given key.
     * @see #isHashEntryRemovable(Object, Object)
     * @since 21.1
     */
    @Abstract(ifExported = "isHashEntryRemovable")
    public void removeHashEntry(Object receiver, Object key) throws UnsupportedMessageException, UnknownKeyException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns {@code true} if mapping for a given key is existing. The mapping is existing if it is
     * {@link #isHashEntryModifiable(Object, Object) modifiable},
     * {@link #isHashEntryReadable(Object, Object) readable} or
     * {@link #isHashEntryRemovable(Object, Object) removable}.
     *
     * @since 21.1
     */
    public boolean isHashEntryExisting(Object receiver, Object key) {
        return isHashEntryReadable(receiver, key) || isHashEntryModifiable(receiver, key) || isHashEntryRemovable(receiver, key);
    }

    /**
     * Returns the hash entries iterator for the receiver. The return value is always an
     * {@link #isIterator(Object) iterator} of {@link #hasArrayElements(Object) array} elements. The
     * first array element is a key, the second array element is an associated value. Array returned
     * by the iterator may be modifiable but detached from the hash, updating the array elements may
     * not update the hash. So even if array elements are
     * {@link #isArrayElementModifiable(Object, long) modifiable} always use
     * {@link #writeHashEntry(Object, Object, Object)} to update the hash mapping.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasHashEntries(Object)} returns
     *             {@code false} for the same receiver.
     * @since 21.1
     */
    @Abstract(ifExported = "hasHashEntries")
    public Object getHashEntriesIterator(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the hash keys iterator for the receiver. The return value is always an
     * {@link #isIterator(Object) iterator}.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasHashEntries(Object)} returns
     *             {@code false} for the same receiver.
     * @since 21.1
     */
    public Object getHashKeysIterator(Object receiver) throws UnsupportedMessageException {
        Object entriesIterator = getHashEntriesIterator(receiver);
        return HashIterator.keys(entriesIterator);
    }

    /**
     * Returns the hash values iterator for the receiver. The return value is always an
     * {@link #isIterator(Object) iterator}.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasHashEntries(Object)} returns
     *             {@code false} for the same receiver.
     * @since 21.1
     */
    public Object getHashValuesIterator(Object receiver) throws UnsupportedMessageException {
        Object entriesIterator = getHashEntriesIterator(receiver);
        return HashIterator.values(entriesIterator);
    }

    // Array Messages

    /**
     * Returns <code>true</code> if the receiver may have array elements. Therefore, At least one of
     * {@link #readArrayElement(Object, long)}, {@link #writeArrayElement(Object, long, Object)},
     * {@link #removeArrayElement(Object, long)} must not throw {#link
     * {@link UnsupportedMessageException}. For example, the contents of an array or list
     * datastructure could be interpreted as array elements. Invoking this message does not cause
     * any observable side-effects. Returns <code>false</code> by default.
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
     * @throws UnsupportedMessageException when the receiver does not support reading at all. An
     *             empty receiver with no readable array elements supports the read operation (even
     *             though there is nothing to read), therefore it throws
     *             {@link UnknownIdentifierException} for all arguments instead.
     * @throws InvalidArrayIndexException if the given index is not
     *             {@link #isArrayElementReadable(Object, long) readable}, e.g. when the index is
     *             invalid or the index is out of bounds.
     * @since 19.0
     */
    @Abstract(ifExported = {"hasArrayElements"})
    public Object readArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
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
     * @throws UnsupportedMessageException when the receiver does not support writing at all, e.g.
     *             when it is immutable.
     * @throws InvalidArrayIndexException if the given index is not
     *             {@link #isArrayElementInsertable(Object, long) insertable} nor
     *             {@link #isArrayElementModifiable(Object, long) modifiable}, e.g. when the index
     *             is invalid or the index is out of bounds and the array does not support growing.
     * @throws UnsupportedTypeException if the provided value type is not allowed to be written.
     * @since 19.0
     */
    @Abstract(ifExported = {"isArrayElementModifiable", "isArrayElementInsertable"})
    public void writeArrayElement(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Remove an array element from the receiver object. Removing member is allowed if the array
     * element is {@link #isArrayElementRemovable(Object, long) removable}. This method may only
     * return <code>true</code> if {@link #hasArrayElements(Object)} returns <code>true</code> as
     * well and {@link #isArrayElementInsertable(Object, long)} returns <code>false</code>.
     *
     * This method does not have observable side-effects other than the removed array element and
     * shift of remaining elements. If shifting is not supported then the array might allow only
     * removal of last element.
     *
     * @throws UnsupportedMessageException when the receiver does not support removing at all, e.g.
     *             when it is immutable.
     * @throws InvalidArrayIndexException if the given index is not
     *             {@link #isArrayElementRemovable(Object, long) removable}, e.g. when the index is
     *             invalid, the index is out of bounds, or the array does not support shifting of
     *             remaining elements.
     * @see #isArrayElementRemovable(Object, long)
     * @since 19.0
     */
    @Abstract(ifExported = "isArrayElementRemovable")
    public void removeArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
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

    // region Buffer Messages

    /**
     * Returns {@code true} if the receiver may have buffer elements.
     * <p>
     * If this message returns {@code true}, then {@link #getBufferSize(Object)},
     * {@link #readBufferByte(Object, long)}, {@link #readBufferShort(Object, ByteOrder, long)},
     * {@link #readBufferInt(Object, ByteOrder, long)},
     * {@link #readBufferLong(Object, ByteOrder, long)},
     * {@link #readBufferFloat(Object, ByteOrder, long)} and
     * {@link #readBufferDouble(Object, ByteOrder, long)} must not throw
     * {@link UnsupportedMessageException}.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     * <p>
     * By default, it returns {@code false}.
     *
     * @since 21.1
     */
    @Abstract(ifExported = {"getBufferSize", "isBufferWritable", "readBufferByte", "readBufferShort", "readBufferInt", "readBufferLong", "readBufferFloat", "readBufferDouble", "writeBufferByte",
                    "writeBufferShort", "writeBufferInt", "writeBufferLong", "writeBufferFloat", "writeBufferDouble"})
    public boolean hasBufferElements(Object receiver) {
        return false;
    }

    /**
     * Returns {@code true} if the receiver is a modifiable buffer.
     * <p>
     * If this message returns {@code true}, then {@link #getBufferSize(Object)},
     * {@link #writeBufferByte(Object, long, byte)},
     * {@link #writeBufferShort(Object, ByteOrder, long, short)},
     * {@link #writeBufferInt(Object, ByteOrder, long, int)},
     * {@link #writeBufferLong(Object, ByteOrder, long, long)},
     * {@link #writeBufferFloat(Object, ByteOrder, long, float)} and
     * {@link #writeBufferDouble(Object, ByteOrder, long, double)} must not throw
     * {@link UnsupportedMessageException}.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     * <p>
     * By default, it returns {@code false} if {@link #hasBufferElements(Object)} return
     * {@code true}, and throws {@link UnsupportedMessageException} otherwise.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasBufferElements(Object)} returns
     *             {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"writeBufferByte", "writeBufferShort", "writeBufferInt", "writeBufferLong", "writeBufferFloat", "writeBufferDouble"})
    public boolean isBufferWritable(Object receiver) throws UnsupportedMessageException {
        if (hasBufferElements(receiver)) {
            return false;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns the buffer size of the receiver, in bytes.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasBufferElements(Object)} returns
     *             {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"hasBufferElements"})
    public long getBufferSize(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Reads the byte from the receiver object at the given byte offset from the start of the
     * buffer.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @return the byte at the given index
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= </code>{@link #getBufferSize(Object)}
     * @throws UnsupportedMessageException if and only if either {@link #hasBufferElements(Object)}
     *             returns {@code false} returns {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"hasBufferElements"})
    public byte readBufferByte(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Writes the given byte from the receiver object at the given byte offset from the start of the
     * buffer.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= </code>{@link #getBufferSize(Object)}
     * @throws UnsupportedMessageException if and only if either {@link #hasBufferElements(Object)}
     *             or {@link #isBufferWritable} returns {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"isBufferWritable"})
    public void writeBufferByte(Object receiver, long byteOffset, byte value) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Reads the short from the receiver object in the given byte order at the given byte offset
     * from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @return the short at the given byte offset from the start of the buffer
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 1</code>
     * @throws UnsupportedMessageException if and only if {@link #hasBufferElements(Object)} returns
     *             {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"hasBufferElements"})
    public short readBufferShort(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Writes the given short from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 1</code>
     * @throws UnsupportedMessageException if and only if either {@link #hasBufferElements(Object)}
     *             or {@link #isBufferWritable} returns {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"isBufferWritable"})
    public void writeBufferShort(Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Reads the int from the receiver object in the given byte order at the given byte offset from
     * the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @return the int at the given byte offset from the start of the buffer
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 3</code>
     * @throws UnsupportedMessageException if and only if {@link #hasBufferElements(Object)} returns
     *             {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"hasBufferElements"})
    public int readBufferInt(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Writes the given int from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 3</code>
     * @throws UnsupportedMessageException if and only if either {@link #hasBufferElements(Object)}
     *             or {@link #isBufferWritable} returns {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"isBufferWritable"})
    public void writeBufferInt(Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Reads the long from the receiver object in the given byte order at the given byte offset from
     * the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @return the int at the given byte offset from the start of the buffer
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 7</code>
     * @throws UnsupportedMessageException if and only if {@link #hasBufferElements(Object)} returns
     *             {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"hasBufferElements"})
    public long readBufferLong(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Writes the given long from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 7</code>
     * @throws UnsupportedMessageException if and only if either {@link #hasBufferElements(Object)}
     *             or {@link #isBufferWritable} returns {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"isBufferWritable"})
    public void writeBufferLong(Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Reads the float from the receiver object in the given byte order at the given byte offset
     * from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @return the float at the given byte offset from the start of the buffer
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 3</code>
     * @throws UnsupportedMessageException if and only if {@link #hasBufferElements(Object)} returns
     *             {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"hasBufferElements"})
    public float readBufferFloat(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Writes the given float from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 3</code>
     * @throws UnsupportedMessageException if and only if either {@link #hasBufferElements(Object)}
     *             or {@link #isBufferWritable} returns {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"isBufferWritable"})
    public void writeBufferFloat(Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Reads the double from the receiver object in the given byte order at the given byte offset
     * from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     * <p>
     * Invoking this message does not cause any observable side-effects.
     *
     * @return the double at the given byte offset from the start of the buffer
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 7</code>
     * @throws UnsupportedMessageException if and only if {@link #hasBufferElements(Object)} returns
     *             {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"hasBufferElements"})
    public double readBufferDouble(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Writes the given double from the receiver object in the given byte order at the given byte
     * offset from the start of the buffer.
     * <p>
     * Unaligned accesses are supported.
     * <p>
     * The access is <em>not</em> guaranteed to be atomic. Therefore, this message is <em>not</em>
     * thread-safe.
     *
     * @throws InvalidBufferOffsetException if and only if
     *             <code>byteOffset < 0 || byteOffset >= {@link #getBufferSize(Object)} - 7</code>
     * @throws UnsupportedMessageException if and only if either {@link #hasBufferElements(Object)}
     *             or {@link #isBufferWritable} returns {@code false}
     * @since 21.1
     */
    @Abstract(ifExported = {"isBufferWritable"})
    public void writeBufferDouble(Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedMessageException, InvalidBufferOffsetException {
        throw UnsupportedMessageException.create();
    }

    // endregion

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
        throw UnsupportedMessageException.create();
    }

    /**
     * Attempts to transform a {@link TruffleObject receiver} to a value that represents a raw
     * native pointer. After a successful transformation, the provided receiver returns true for
     * {@link #isPointer(Object)} and can be unwrapped using the {@link #asPointer(Object)} message.
     * If transformation cannot be done {@link #isPointer(Object)} will keep returning false.
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
     * @throws UnsupportedMessageException if and only if {@link #isInstant(Object)} returns
     *             <code>false</code>.
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
     * Objects with only date information must not have timezone information attached and objects
     * with only time information must have either none, or {@link ZoneRules#isFixedOffset() fixed
     * zone} only. If this rule is violated then an {@link AssertionError} is thrown if assertions
     * are enabled.
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
     * @throws UnsupportedMessageException if and only if {@link #isTimeZone(Object)} returns
     *             <code>false</code> .
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
     * @throws UnsupportedMessageException if and only if {@link #isDate(Object)} returns
     *             <code>false</code>.
     * @see #isDate(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"isDate", "asInstant"})
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
     * @throws UnsupportedMessageException if and only if {@link #isTime(Object)} returns
     *             <code>false</code>.
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
     * @see #asDuration(Object)
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
     * @throws UnsupportedMessageException if and only if {@link #isDuration(Object)} returns
     *             <code>false</code> .
     * @see #isDuration(Object)
     * @since 20.0.0 beta 2
     */
    @Abstract(ifExported = {"isDuration"})
    public Duration asDuration(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the receiver value represents a throwable exception/error}.
     * Invoking this message does not cause any observable side-effects. Returns <code>false</code>
     * by default.
     * <p>
     * Objects must only return <code>true</code> if they support {@link #throwException} as well.
     * If this method is implemented then also {@link #throwException(Object)} must be implemented.
     *
     * The following simplified {@code TryCatchNode} shows how the exceptions should be handled by
     * languages.
     *
     * {@link InteropLibrarySnippets.TryCatchNode}
     *
     * @see #throwException(Object)
     * @see com.oracle.truffle.api.exception.AbstractTruffleException
     * @since 19.3
     */
    @Abstract(ifExported = {"throwException"})
    public boolean isException(Object receiver) {
        // A workaround for missing inheritance feature for default exports.
        return InteropAccessor.EXCEPTION.isException(receiver);
    }

    /**
     * Throws the receiver object as an exception of the source language, as if it was thrown by the
     * source language itself. Allows rethrowing exceptions caught by another language. If this
     * method is implemented then also {@link #isException(Object)} must be implemented.
     * <p>
     * Any interop value can be an exception value and export {@link #throwException(Object)}. The
     * exception thrown by this message must extend
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException}. In future versions this
     * contract will be enforced using an assertion.
     * <p>
     * For a sample {@code TryCatchNode} implementation see {@link #isException(Object)
     * isException}.
     *
     * @throws UnsupportedMessageException if and only if {@link #isException(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isException(Object)
     * @since 19.3
     */
    @Abstract(ifExported = {"isException"})
    public RuntimeException throwException(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            throw InteropAccessor.EXCEPTION.throwException(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns {@link ExceptionType exception type} of the receiver. Throws
     * {@code UnsupportedMessageException} when the receiver is not an {@link #isException(Object)
     * exception}.
     * <p>
     * For a sample {@code TryCatchNode} implementation see {@link #isException(Object)
     * isException}.
     *
     * @see #isException(Object)
     * @see ExceptionType
     * @since 20.3
     */
    @Abstract(ifExported = {"getExceptionExitStatus", "isExceptionIncompleteSource"})
    public ExceptionType getExceptionType(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return (ExceptionType) InteropAccessor.EXCEPTION.getExceptionType(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns {@code true} if receiver value represents an incomplete source exception. Throws
     * {@code UnsupportedMessageException} when the receiver is not an {@link #isException(Object)
     * exception} or the exception is not a {@link ExceptionType#PARSE_ERROR}.
     *
     * @see #isException(Object)
     * @see #getExceptionType(Object)
     * @since 20.3
     */
    public boolean isExceptionIncompleteSource(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.isExceptionIncompleteSource(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns exception exit status of the receiver. Throws {@code UnsupportedMessageException}
     * when the receiver is not an {@link #isException(Object) exception} of the
     * {@link ExceptionType#EXIT exit type}. See
     * <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/Exit.md">Context Exit</a>
     * for further information. A return value zero indicates that the execution of the application
     * was successful, a non-zero value that it failed. The individual interpretation of non-zero
     * values depends on the application.
     *
     * @see #isException(Object)
     * @see #getExceptionType(Object)
     * @see ExceptionType
     * @since 20.3
     */
    public int getExceptionExitStatus(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.getExceptionExitStatus(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception with an attached internal cause.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see #isException(Object)
     * @see #getExceptionCause(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"getExceptionCause"})
    public boolean hasExceptionCause(Object receiver) {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.hasExceptionCause(receiver);
        } else {
            return false;
        }
    }

    /**
     * Returns the internal cause of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link #isException(Object) exception} or has no internal cause. The
     * return value of this message is guaranteed to return <code>true</code> for
     * {@link #isException(Object)}.
     *
     *
     * @see #isException(Object)
     * @see #hasExceptionCause(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"hasExceptionCause"})
    public Object getExceptionCause(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.getExceptionCause(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception that has an exception message. Invoking
     * this message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #isException(Object)
     * @see #getExceptionMessage(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"getExceptionMessage"})
    public boolean hasExceptionMessage(Object receiver) {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.hasExceptionMessage(receiver);
        } else {
            return false;
        }
    }

    /**
     * Returns exception message of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link #isException(Object) exception} or has no exception message.
     * The return value of this message is guaranteed to return <code>true</code> for
     * {@link #isString(Object)}.
     *
     * @see #isException(Object)
     * @see #hasExceptionMessage(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"hasExceptionMessage"})
    public Object getExceptionMessage(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.getExceptionMessage(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception and has a stack trace. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #isException(Object)
     * @see #getExceptionStackTrace(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"getExceptionStackTrace"})
    public boolean hasExceptionStackTrace(Object receiver) {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.hasExceptionStackTrace(receiver);
        } else {
            return false;
        }
    }

    /**
     * Returns the exception stack trace of the receiver that is of type exception. Returns an
     * {@link #hasArrayElements(Object) array} of objects with potentially
     * {@link #hasExecutableName(Object) executable name}, {@link #hasDeclaringMetaObject(Object)
     * declaring meta object} and {@link #hasSourceLocation(Object) source location} of the caller.
     * Throws {@code UnsupportedMessageException} when the receiver is not an
     * {@link #isException(Object) exception} or has no stack trace. Invoking this message or
     * accessing the stack trace elements array must not cause any observable side-effects.
     * <p>
     * The default implementation of {@link #getExceptionStackTrace(Object)} calls
     * {@link TruffleStackTrace#getStackTrace(Throwable)} on the underlying exception object and
     * {@link TruffleStackTraceElement#getGuestObject()} to access an interop capable object of the
     * underlying stack trace element.
     *
     * @see #isException(Object)
     * @see #hasExceptionStackTrace(Object)
     * @since 20.3
     */
    @Abstract(ifExported = {"hasExceptionStackTrace"})
    public Object getExceptionStackTrace(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.getExceptionStackTrace(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Returns {@code true} if the receiver provides an iterator. For example, an array or a list
     * provide an iterator over their content. Invoking this message does not cause any observable
     * side-effects. By default returns {@code true} for receivers that have
     * {@link #hasArrayElements(Object) array elements}.
     *
     * @see #getIterator(Object)
     * @since 21.1
     */
    @Abstract(ifExported = {"getIterator"})
    public boolean hasIterator(Object receiver) {
        return hasArrayElements(receiver);
    }

    /**
     * Returns the iterator for the receiver. The return value is always an
     * {@link #isIterator(Object) iterator}. Invoking this message does not cause any observable
     * side-effects.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasIterator(Object)} returns
     *             {@code false} for the same receiver.
     * @since 21.1
     */
    @Abstract(ifExported = {"hasIterator"})
    public Object getIterator(Object receiver) throws UnsupportedMessageException {
        if (!hasIterator(receiver)) {
            throw UnsupportedMessageException.create();
        }
        return new ArrayIterator(receiver);
    }

    /**
     * Returns {@code true} if the receiver represents an iterator. Invoking this message does not
     * cause any observable side-effects. Returns {@code false} by default.
     *
     * @see #hasIterator(Object)
     * @see #getIterator(Object)
     * @since 21.1
     */
    @Abstract(ifExported = {"hasIteratorNextElement", "getIteratorNextElement"})
    public boolean isIterator(Object receiver) {
        return false;
    }

    /**
     * Returns {@code true} if the receiver is an iterator which has more elements, else
     * {@code false}. Multiple calls to the {@link #hasIteratorNextElement(Object)} might lead to
     * different results if the underlying data structure is modified.
     * <p>
     * The following example shows how the {@link #hasIteratorNextElement(Object)
     * hasIteratorNextElement} message can be emulated in languages where iterators only have a next
     * method and throw an exception if there are no further elements.
     *
     * <pre>
     * &#64;ExportLibrary(InteropLibrary.class)
     * abstract class InteropIterator implements TruffleObject {
     *
     *     &#64;SuppressWarnings("serial")
     *     public static final class Stop extends AbstractTruffleException {
     *     }
     *
     *     private static final Object STOP = new Object();
     *     private Object next;
     *
     *     protected InteropIterator() {
     *     }
     *
     *     protected abstract Object next() throws Stop;
     *
     *     &#64;ExportMessage
     *     &#64;SuppressWarnings("static-method")
     *     boolean isIterator() {
     *         return true;
     *     }
     *
     *     &#64;ExportMessage
     *     boolean hasIteratorNextElement() {
     *         fetchNext();
     *         return next != STOP;
     *     }
     *
     *     &#64;ExportMessage
     *     Object getIteratorNextElement() throws StopIterationException {
     *         fetchNext();
     *         Object res = next;
     *         if (res == STOP) {
     *             throw StopIterationException.create();
     *         } else {
     *             next = null;
     *         }
     *         return res;
     *     }
     *
     *     private void fetchNext() {
     *         if (next == null) {
     *             try {
     *                 next = next();
     *             } catch (Stop stop) {
     *                 next = STOP;
     *             }
     *         }
     *     }
     * }
     * </pre>
     *
     * @throws UnsupportedMessageException if and only if {@link #isIterator(Object)} returns
     *             {@code false} for the same receiver.
     * @see #isIterator(Object)
     * @see #getIteratorNextElement(Object)
     * @since 21.1
     */
    @Abstract(ifExported = {"isIterator", "getIteratorNextElement"})
    public boolean hasIteratorNextElement(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the next element in the iteration. When the underlying data structure is modified the
     * {@link #getIteratorNextElement(Object)} may throw the {@link StopIterationException} despite
     * the {@link #hasIteratorNextElement(Object)} returned {@code true}.
     *
     * @throws UnsupportedMessageException if {@link #isIterator(Object)} returns {@code false} for
     *             the same receiver or when the underlying iterator element exists but is not
     *             readable.
     * @throws StopIterationException if the iteration has no more elements. Even if the
     *             {@link StopIterationException} was thrown it might not be thrown again by a next
     *             {@link #getIteratorNextElement(Object)} invocation on the same receiver due to a
     *             modification of an underlying iterable.
     *
     * @see #isIterator(Object)
     * @see #hasIteratorNextElement(Object)
     * @since 21.1
     */
    @Abstract(ifExported = {"isIterator", "hasIteratorNextElement"})
    public Object getIteratorNextElement(Object receiver) throws UnsupportedMessageException, StopIterationException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the receiver value has a declared source location attached, else
     * <code>false</code>. Returning a source location for a value is optional and typically impacts
     * the capabilities of tools like debuggers to jump to the declaration of a value.
     * <p>
     * Examples for values that may provide a source location:
     * <ul>
     * <li>{@link #isMetaObject(Object) Metaobjects} like classes or types.
     * <li>First class {@link #isExecutable(Object) executables}, like functions, closures or
     * promises.
     * <li>Allocation sites for instances. Note that in most languages it is very expensive to track
     * the allocation site of an instance and it is therefore not recommended to support this
     * feature by default, but ideally behind an optional language option.
     * <ul>
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getSourceLocation(Object)} must be implemented.
     *
     * @see #getSourceLocation(Object)
     * @since 20.1
     */
    @Abstract(ifExported = {"getSourceLocation"})
    @TruffleBoundary
    public boolean hasSourceLocation(Object receiver) {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.hasSourceLocation(receiver);
        }
        return false;
    }

    /**
     * Returns the declared source location of the receiver value. Throws an
     * {@link UnsupportedMessageException} if the value does not have a declared source location.
     * See {@link #hasSourceLocation(Object)} for further details on potential interpretations.
     * Throws {@link UnsupportedMessageException} by default.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasSourceLocation(Object)} must be implemented.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasSourceLocation(Object)} returns
     *             <code>false</code> for the same receiver.
     * @since 20.1
     */
    @Abstract(ifExported = {"hasSourceLocation"})
    @TruffleBoundary
    public SourceSection getSourceLocation(Object receiver) throws UnsupportedMessageException {
        // A workaround for missing inheritance feature for default exports.
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.getSourceLocation(receiver);
        }
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the receiver originates from a language, else <code>false</code>
     * . Primitive values or other shared interop value representations that are not associated with
     * a language may return <code>false</code>. Values that originate from a language should return
     * <code>true</code>. Returns <code>false</code> by default.
     * <p>
     * The associated language allows tools to identify the original language of a value. If an
     * instrument requests a
     * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#getLanguageView(LanguageInfo, Object)
     * language view} then values that are already associated with a language will just return the
     * same value. Otherwise {@link TruffleLanguage#getLanguageView(Object, Object)} will be invoked
     * on the language. The returned language may be also exposed to embedders in the future.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getLanguage(Object)} and {@link #toDisplayString(Object, boolean)} must be
     * implemented.
     *
     * @see #getLanguage(Object)
     * @see #toDisplayString(Object)
     * @since 20.1
     */
    @Abstract(ifExported = {"getLanguage", "isScope"})
    public boolean hasLanguage(Object receiver) {
        return false;
    }

    /**
     * Returns the the original language of the receiver value. The returned language class must be
     * non-null and represent a valid {@link Registration registered} language class. For more
     * details see {@link #hasLanguage(Object)}.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasLanguage(Object)} must be implemented.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasLanguage(Object)} returns
     *             <code>false</code> for the same receiver.
     * @since 20.1
     */
    @SuppressWarnings("unchecked")
    @Abstract(ifExported = {"hasLanguage"})
    public Class<? extends TruffleLanguage<?>> getLanguage(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the receiver value has a metaobject associated. The metaobject
     * represents a description of the object, reveals its kind and its features. Some information
     * that a metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. Should return <code>false</code> when no metaobject is known for this type.
     * Returns <code>false</code> by default.
     * <p>
     * An example, for Java objects the returned metaobject is the {@link Object#getClass() class}
     * instance. In JavaScript this could be the function or class that is associated with the
     * object.
     * <p>
     * Metaobjects for primitive values or values of other languages may be provided using
     * {@link TruffleLanguage#getLanguageView(Object, Object) language views}. While an object is
     * associated with a metaobject in one language, the metaobject might be a different when viewed
     * from another language.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getMetaObject(Object)} must be implemented.
     *
     * @see #getMetaObject(Object)
     * @see #isMetaObject(Object)
     * @since 20.1
     */
    @Abstract(ifExported = {"getMetaObject"})
    public boolean hasMetaObject(Object receiver) {
        return false;
    }

    /**
     * Returns the metaobject that is associated with this value. The metaobject represents a
     * description of the object, reveals its kind and its features. Some information that a
     * metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. When no metaobject is known for this type. Throws
     * {@link UnsupportedMessageException} by default.
     * <p>
     * The returned object must return <code>true</code> for {@link #isMetaObject(Object)} and
     * provide implementations for {@link #getMetaSimpleName(Object)},
     * {@link #getMetaQualifiedName(Object)}, and {@link #isMetaInstance(Object, Object)}. For all
     * values with metaobjects it must at hold that
     * <code>isMetaInstance(getMetaObject(value), value) ==
     * true</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasMetaObject(Object)} must be implemented.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     *
     * @see #hasMetaObject(Object)
     * @since 20.1
     */
    @Abstract(ifExported = {"hasMetaObject"})
    public Object getMetaObject(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Converts the receiver to a human readable {@link #isString(Object) string}. Each language may
     * have special formating conventions - even primitive values may not follow the traditional
     * Java rules. The format of the returned string is intended to be interpreted by humans not
     * machines and should therefore not be relied upon by machines. By default the receiver class
     * name and its {@link System#identityHashCode(Object) identity hash code} is used as string
     * representation.
     * <p>
     * String representations for primitive values or values of other languages may be provided
     * using {@link TruffleLanguage#getLanguageView(Object, Object) language views}. It is common
     * that languages provide different string representations for primitive and foreign values. To
     * convert the result value to a Java string use {@link InteropLibrary#asString(Object)}.
     *
     * @param allowSideEffects whether side-effects are allowed in the production of the string.
     * @see TruffleLanguage#getLanguageView(Object, Object)
     * @since 20.1
     */
    @Abstract(ifExported = {"hasLanguage", "getLanguage", "isScope"})
    @TruffleBoundary
    public Object toDisplayString(Object receiver, boolean allowSideEffects) {
        if (allowSideEffects) {
            return Objects.toString(receiver);
        } else {
            return receiver.getClass().getTypeName() + "@" + Integer.toHexString(System.identityHashCode(receiver));
        }
    }

    /**
     * Converts the receiver to a human readable {@link #isString(Object) string} of the language.
     * Short-cut for
     * <code>{@link #toDisplayString(Object, boolean) toDisplayString(Object, true)}</code>.
     *
     * @see #toDisplayString(Object, boolean)
     * @since 20.1
     */
    public final Object toDisplayString(Object receiver) {
        return toDisplayString(receiver, true);
    }

    /**
     * Returns <code>true</code> if the receiver value represents a metaobject. Metaobjects may be
     * values that naturally occur in a language or they may be returned by
     * {@link #getMetaObject(Object)}. A metaobject represents a description of the object, reveals
     * its kind and its features. If a receiver is a metaobject it is often also
     * {@link #isInstantiable(Object) instantiable}, but this is not a requirement.
     * <p>
     * <b>Sample interpretations:</b> In Java an instance of the type {@link Class} is a metaobject.
     * In JavaScript any function instance is a metaobject. For example, the metaobject of a
     * JavaScript class is the associated constructor function.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getMetaQualifiedName(Object)}, {@link #getMetaSimpleName(Object)} and
     * {@link #isMetaInstance(Object, Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Abstract(ifExported = {"getMetaQualifiedName", "getMetaSimpleName", "isMetaInstance"})
    public boolean isMetaObject(Object receiver) {
        return false;
    }

    /**
     * Returns the qualified name of a metaobject as {@link #isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The qualified name of a Java class includes the package name
     * and its class name. JavaScript does not have the notion of qualified name and therefore
     * returns the {@link #getMetaSimpleName(Object) simple name} instead.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isMetaObject(Object)} must be implemented as well.
     *
     * @throws UnsupportedMessageException if and only if {@link #isMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     *
     * @since 20.1
     */
    @Abstract(ifExported = {"isMetaObject"})
    public Object getMetaQualifiedName(Object metaObject) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns the simple name of a metaobject as {@link #isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The simple name of a Java class is the class name.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isMetaObject(Object)} must be implemented as well.
     *
     * @throws UnsupportedMessageException if and only if {@link #isMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     *
     * @since 20.1
     */
    @Abstract(ifExported = {"isMetaObject"})
    public Object getMetaSimpleName(Object metaObject) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the given instance is of the provided receiver metaobject, else
     * <code>false</code>.
     * <p>
     * <b>Sample interpretations:</b> A Java object is an instance of its returned
     * {@link Object#getClass() class}.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isMetaObject(Object)} must be implemented as well.
     *
     * @param instance the instance object to check.
     * @throws UnsupportedMessageException if and only if {@link #isMetaObject(Object)} returns
     *             <code>false</code> for the same receiver.
     * @since 20.1
     */
    @Abstract(ifExported = {"isMetaObject"})
    public boolean isMetaInstance(Object receiver, Object instance) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the receiver value {@link #isMetaObject(Object) is a metaobject}
     * which has parents (super types).
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getMetaParents(Object)} must be implemented.
     *
     * @param receiver a metaobject
     * @see #getMetaParents(Object)
     * @since 22.2
     */
    @Abstract(ifExported = {"getMetaParents"})
    public boolean hasMetaParents(Object receiver) {
        return false;
    }

    /**
     * Returns an array like {@link #hasArrayElements(Object)} of metaobjects that are direct
     * parents (super types) of this metaobject.
     * <p>
     * The returned object is an {@link #hasArrayElements(Object) array} of objects that return
     * <code>true</code> from {@link #isMetaObject(Object)}.
     *
     * @param receiver a metaobject
     * @throws UnsupportedMessageException if and only if {@link #hasMetaParents(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #hasMetaParents(Object)
     * @since 22.2
     */
    @Abstract(ifExported = {"hasMetaParents"})
    public Object getMetaParents(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns {@link TriState#TRUE TRUE} if the receiver is or {@link TriState#FALSE FALSE} if the
     * receiver is not identical to the <code>other</code> value. Returns {@link TriState#UNDEFINED
     * UNDEFINED} if the operation is not specified.
     * <p>
     * <b>Sample interpretations:</b>
     * <ul>
     * <li>A Java object might be of the identical instance as another Java object. Typically
     * compared using the <code>==</code> operator.
     * </ul>
     * <p>
     * Any implementation, with the exception of an implementation that returns
     * {@link TriState#UNDEFINED UNDEFINED} unconditionally, must guarantee the following
     * properties:
     * <ul>
     * <li>It is <i>reflexive</i>: for any value {@code x}, {@code lib.isIdenticalOrUndefined(x, x)}
     * always returns {@link TriState#TRUE TRUE}. This is necessary to ensure that the
     * {@link #hasIdentity(Object)} contract has reliable results.
     * <li>It is <i>symmetric</i>: for any values {@code x} and {@code y},
     * {@code lib.isIdenticalOrUndefined(x, y)} returns {@link TriState#TRUE TRUE} if and only if
     * {@code lib.isIdenticalOrUndefined(y, x)} returns {@link TriState#TRUE TRUE}.
     * <li>It is <i>transitive</i>: for any values {@code x}, {@code y}, and {@code z}, if
     * {@code lib.isIdenticalOrUndefined(x, y)} returns {@link TriState#TRUE TRUE} and
     * {@code lib.isIdenticalOrUndefined(y, z)} returns {@link TriState#TRUE TRUE}, then
     * {@code lib.isIdentical(x, z, zLib)} returns {@link TriState#TRUE TRUE}.
     * <li>It is <i>consistent</i>: for any values {@code x} and {@code y}, multiple invocations of
     * {@code lib.isIdenticalOrUndefined(x, y)} consistently returns the same value.
     * </ul>
     * <p>
     * Note that the target language identical semantics typically does not map directly to interop
     * identical implementation. Instead target language identity is specified by the language
     * operation, may take multiple other rules into account and may only fallback to interop
     * identical for values without dedicated interop type. For example, in many languages
     * primitives like numbers or strings may be identical, in the target language sense, still
     * identity can only be exposed for objects and non-primitive values. Primitive values like
     * {@link Integer} can never be interop identical to other boxed language integers as this would
     * violate the symmetric property.
     * <p>
     * Example receiver class MyObject which uses an explicit identity field to compute whether two
     * values are identical.
     *
     * <pre>
     * static class MyObject {
     *
     *     final Object identity;
     *
     *     MyObject(Object identity) {
     *         this.identity = identity;
     *     }
     *
     *     &#64;ExportMessage
     *     static final class IsIdenticalOrUndefined {
     *         &#64;Specialization
     *         static TriState doMyObject(MyObject receiver, MyObject other) {
     *             return receiver.identity == other.identity ? TriState.TRUE : TriState.FALSE;
     *         }
     *
     *         &#64;Fallback
     *         static TriState doOther(MyObject receiver, Object other) {
     *             return TriState.UNDEFINED;
     *         }
     *     }
     *     // ...
     * }
     * </pre>
     *
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #identityHashCode(Object)} must be implemented.
     *
     * @param other the other value to compare to
     *
     * @since 20.2
     */
    @Abstract(ifExported = {"isIdentical", "identityHashCode"})
    protected TriState isIdenticalOrUndefined(Object receiver, Object other) {
        return TriState.UNDEFINED;
    }

    /**
     * Returns <code>true</code> if two values represent the the identical value, else
     * <code>false</code>. Two values are identical if and only if they have specified identity
     * semantics in the target language and refer to the identical instance.
     * <p>
     * By default, an interop value does not support identical comparisons, and will return
     * <code>false</code> for any invocation of this method. Use {@link #hasIdentity(Object)} to
     * find out whether a receiver supports identity comparisons.
     * <p>
     * This method has the following properties:
     * <ul>
     * <li>It is <b>not</b> <i>reflexive</i>: for any value {@code x},
     * {@code lib.isIdentical(x, x, lib)} may return {@code false} if the object does not support
     * identity, else <code>true</code>. This method is reflexive if {@code x} supports identity. A
     * value supports identity if {@code lib.isIdentical(x, x, lib)} returns <code>true</code>. The
     * method {@link #hasIdentity(Object)} may be used to document this intent explicitly.
     * <li>It is <i>symmetric</i>: for any values {@code x} and {@code y},
     * {@code lib.isIdentical(x, y, yLib)} returns {@code true} if and only if
     * {@code lib.isIdentical(y, x, xLib)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any values {@code x}, {@code y}, and {@code z}, if
     * {@code lib.isIdentical(x, y, yLib)} returns {@code true} and
     * {@code lib.isIdentical(y, z, zLib)} returns {@code true}, then
     * {@code lib.isIdentical(x, z, zLib)} returns {@code true}.
     * <li>It is <i>consistent</i>: for any values {@code x} and {@code y}, multiple invocations of
     * {@code lib.isIdentical(x, y, yLib)} consistently returns {@code true} or consistently return
     * {@code false}.
     * </ul>
     * <p>
     * Note that the target language identical semantics typically does not map directly to interop
     * identical implementation. Instead target language identity is specified by the language
     * operation, may take multiple other rules into account and may only fallback to interop
     * identical for values without dedicated interop type. For example, in many languages
     * primitives like numbers or strings may be identical, in the target language sense, still
     * identity can only be exposed for objects and non-primitive values. Primitive values like
     * {@link Integer} can never be interop identical to other boxed language integers as this would
     * violate the symmetric property.
     * <p>
     * This method performs double dispatch by forwarding calls to
     * {@link #isIdenticalOrUndefined(Object, Object)} with receiver and other value first and then
     * with reversed parameters if the result was {@link TriState#UNDEFINED undefined}. This allows
     * the receiver and the other value to negotiate identity semantics. This method is supposed to
     * be exported only if the receiver represents a wrapper that forwards messages. In such a case
     * the isIdentical message should be forwarded to the delegate value. Otherwise, the
     * {@link #isIdenticalOrUndefined(Object, Object)} should be exported instead.
     * <p>
     * This method must not cause any observable side-effects.
     * <p>
     * Cached usage example:
     *
     * <pre>
     * abstract class IsIdenticalUsage extends Node {
     *
     *     abstract boolean execute(Object left, Object right);
     *
     *     &#64;Specialization(limit = "3")
     *     public boolean isIdentical(Object left, Object right,
     *                     &#64;CachedLibrary("left") InteropLibrary leftInterop,
     *                     &#64;CachedLibrary("right") InteropLibrary rightInterop) {
     *         return leftInterop.isIdentical(left, right, rightInterop);
     *     }
     * }
     * </pre>
     * <p>
     * Uncached usage example:
     *
     * <pre>
     * &#64;TruffleBoundary
     * public static boolean isIdentical(Object left, Object right) {
     *     return InteropLibrary.getUncached(left).isIdentical(left, right,
     *                     InteropLibrary.getUncached(right));
     * }
     * </pre>
     *
     * For a full example please refer to the SLEqualNode of the SimpleLanguage example
     * implementation.
     *
     * @since 20.2
     */
    public boolean isIdentical(Object receiver, Object other, InteropLibrary otherInterop) {
        TriState result = this.isIdenticalOrUndefined(receiver, other);
        if (result == TriState.UNDEFINED) {
            result = otherInterop.isIdenticalOrUndefined(other, receiver);
        }
        return result == TriState.TRUE;
    }

    /**
     * Returns <code>true</code> if and only if the receiver specifies identity, else
     * <code>false</code>. This method is a short-cut for
     * <code>this.isIdentical(receiver, receiver, this) != TriState.UNDEFINED</code>. This message
     * cannot be exported. To add identity support to the receiver export
     * {@link #isIdenticalOrUndefined(Object, Object)} instead.
     *
     * @see #isIdenticalOrUndefined(Object, Object)
     * @since 20.2
     */
    public final boolean hasIdentity(Object receiver) {
        return this.isIdentical(receiver, receiver, this);
    }

    /**
     * Returns an identity hash code for the receiver if it has {@link #hasIdentity(Object)
     * identity}. If the receiver has no identity then an {@link UnsupportedMessageException} is
     * thrown. The identity hash code may be used by languages to store foreign values with identity
     * in an identity hash map.
     * <p>
     * <ul>
     * <li>Whenever it is invoked on the same object more than once during an execution of a guest
     * context, the identityHashCode method must consistently return the same integer. This integer
     * need not remain consistent from one execution context of a guest application to another
     * execution context of the same application.
     * <li>If two objects are the same according to the
     * {@link #isIdentical(Object, Object, InteropLibrary)} message, then calling the
     * identityHashCode method on each of the two objects must produce the same integer result.
     * <li>As much as is reasonably practical, the identityHashCode message does return distinct
     * integers for objects that are not the same.
     * </ul>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isIdenticalOrUndefined(Object, Object)} must be implemented.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasIdentity(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isIdenticalOrUndefined(Object, Object)
     * @see #isIdentical(Object, Object, InteropLibrary)
     * @since 20.2
     */
    @Abstract(ifExported = "isIdenticalOrUndefined")
    public int identityHashCode(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if the value represents a scope object, else <code>false</code>.
     * The scope object contains variables as {@link #getMembers(Object) members} and has a
     * {@link InteropLibrary#toDisplayString(Object, boolean) scope display name}. It needs to be
     * associated with a {@link #getLanguage(Object) language}. The scope may return a
     * {@link InteropLibrary#getSourceLocation(Object) source location} that indicates the range of
     * the scope in the source code. The scope may have {@link #hasScopeParent(Object) parent
     * scopes}.
     * <p>
     * The {@link #getMembers(Object) members} of a scope represent all visible flattened variables,
     * including all parent scopes, if any. The variables of the current scope must be listed first
     * in {@link #getMembers(Object)}. Variables of the {@link InteropLibrary#getScopeParent(Object)
     * parent scope} must be listed afterwards, even if they contain duplicates. This allows to
     * resolve which variables are redeclared in sub scopes.
     * <p>
     * Every {@link #getMembers(Object) member} may not be just a String literal, but a
     * {@link #isString(Object) string object} that provides also a
     * {@link #getSourceLocation(Object) source location} of its declaration. When different
     * variables of the same name are in different scopes, they will be represented by different
     * member elements providing the same {@link #asString(Object) name}.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasMembers(Object)} and {@link #toDisplayString(Object, boolean)} must be
     * implemented and {@link #hasSourceLocation(Object)} is recommended.
     *
     * @see #getLanguage(Object)
     * @see #getMembers(Object)
     * @see #hasScopeParent(Object)
     * @since 20.3
     */
    @Abstract(ifExported = "hasScopeParent")
    public boolean isScope(Object receiver) {
        return false;
    }

    /**
     * Returns <code>true</code> if this scope has an enclosing parent scope, else
     * <code>false</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isScope(Object)} and {@link #getScopeParent(Object)} must be implemented.
     *
     * @see #isScope(Object)
     * @see #getScopeParent(Object)
     * @since 20.3
     */
    @Abstract(ifExported = "getScopeParent")
    public boolean hasScopeParent(Object receiver) {
        return false;
    }

    /**
     * Returns the parent scope object if it {@link #hasScopeParent(Object) has the parent}. The
     * returned object must be a {@link #isScope(Object) scope} and must provide a reduced list of
     * {@link #getMembers(Object) member} variables, omitting all variables that are local to the
     * current scope.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #isScope(Object)} and {@link #getScopeParent(Object)} must be implemented.
     *
     * @throws UnsupportedMessageException if and only if {@link #hasScopeParent(Object)} returns
     *             <code>false</code> for the same receiver.
     * @see #isScope(Object)
     * @see #hasScopeParent(Object)
     * @since 20.3
     */
    @Abstract(ifExported = "hasScopeParent")
    public Object getScopeParent(Object receiver) throws UnsupportedMessageException {
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
     * Returns the uncached automatically dispatched version of the interop library. This is a
     * short-cut for calling <code>InteropLibrary.getFactory().getUncached()</code>.
     *
     * @see LibraryFactory#getUncached()
     * @since 20.2
     */
    public static InteropLibrary getUncached() {
        return UNCACHED;
    }

    /**
     * Returns the uncached manually dispatched version of the interop library. This is a short-cut
     * for calling <code>InteropLibrary.getFactory().getUncached(v)</code>.
     *
     * @see LibraryFactory#getUncached(Object)
     * @since 20.2
     */
    public static InteropLibrary getUncached(Object v) {
        return FACTORY.getUncached(v);
    }

    /**
     * Utility for libraries to require adoption before cached versions of nodes can be executed.
     * Only fails if assertions (-ea) are enabled.
     *
     * @since 19.0
     */
    protected final boolean assertAdopted() {
        assert this.getRootNode() != null : "Invalid library usage. Cached library must be adopted by a RootNode before it is executed.";
        return true;
    }

    static final LibraryFactory<InteropLibrary> FACTORY = LibraryFactory.resolve(InteropLibrary.class);
    static final InteropLibrary UNCACHED = FACTORY.getUncached();

    /**
     * Utility to check whether a value is a valid interop value. Interop values are all values that
     * can flow through the language implementation freely and are intended to be used as receivers
     * for the {@link InteropLibrary}. This method will be extended with more checked types as
     * interop is extended with further allowed values.
     * <p>
     * It is not recommended to make assumptions about the types of interop values. It is
     * recommended to use instance methods in {@link InteropLibrary} to check for interop types
     * instead. However, it can be useful to make such assumptions for performance reasons. To
     * verify that these assumptions continue to hold this method can be used.
     *
     * @since 21.3
     */
    @TruffleBoundary
    public static boolean isValidValue(Object receiver) {
        return receiver instanceof TruffleObject //
                        || receiver instanceof Boolean //
                        || receiver instanceof Byte  //
                        || receiver instanceof Short //
                        || receiver instanceof Character //
                        || receiver instanceof Integer //
                        || receiver instanceof Long //
                        || receiver instanceof Float //
                        || receiver instanceof Double //
                        || receiver instanceof String //
                        || receiver instanceof TruffleString;
    }

    /**
     * Utility to check whether a value is a valid interop protocol value. An interop protocol value
     * is either an {@link #isValidValue(Object) interop value} or a value that might be returned or
     * passed as parameter by any of the methods in {@link InteropLibrary}. This can be useful to
     * validate all values received or returned by a wrapper that uses {@link ReflectionLibrary} to
     * delegate all interop protocol parameters and return values. This method will be extended with
     * more checked types as interop is extended with further allowed values.
     *
     * @see #isValidValue(Object)
     * @since 21.3
     */
    @TruffleBoundary
    public static boolean isValidProtocolValue(Object value) {
        return isValidValue(value) || value instanceof ByteOrder || value instanceof Instant || value instanceof ZoneId || value instanceof LocalDate ||
                        value instanceof LocalTime || value instanceof Duration || value instanceof ExceptionType || value instanceof SourceSection || value instanceof Class<?> ||
                        value instanceof TriState || value instanceof InteropLibrary || value instanceof Object[];
    }

    static class Asserts extends InteropLibrary {

        @Child private InteropLibrary delegate;

        public enum Type {
            NULL,
            BOOLEAN,
            DATE_TIME_ZONE,
            DURATION,
            STRING,
            NUMBER,
            POINTER,
            META_OBJECT,
            ITERATOR;
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
            assert validProtocolReturn(receiver, result);
            return result;
        }

        private boolean notOtherType(Object receiver, Type type) {
            assert type == Type.NULL || !delegate.isNull(receiver) : violationInvariant(receiver);
            assert type == Type.BOOLEAN || !delegate.isBoolean(receiver) : violationInvariant(receiver);
            assert type == Type.STRING || !delegate.isString(receiver) : violationInvariant(receiver);
            assert type == Type.NUMBER || !delegate.isNumber(receiver) : violationInvariant(receiver);
            assert type == Type.DATE_TIME_ZONE || (!delegate.isDate(receiver) && !delegate.isTime(receiver) && !delegate.isTimeZone(receiver)) : violationInvariant(receiver);
            assert type == Type.DURATION || !delegate.isDuration(receiver) : violationInvariant(receiver);
            assert type == Type.META_OBJECT || !delegate.isMetaObject(receiver) : violationInvariant(receiver);
            assert type == Type.ITERATOR || !delegate.isIterator(receiver) : violationInvariant(receiver);
            return true;
        }

        @Override
        public boolean isBoolean(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.isBoolean(receiver);
            }
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
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean asBoolean(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asBoolean(receiver);
            }
            assert preCondition(receiver);
            boolean wasBoolean = delegate.isBoolean(receiver);
            try {
                boolean result = delegate.asBoolean(receiver);
                assert wasBoolean : violationInvariant(receiver);
                assert notOtherType(receiver, Type.BOOLEAN);
                assert validProtocolReturn(receiver, result);
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
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.execute(receiver, arguments);
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, arguments);
            assert validArguments(receiver, arguments);
            boolean wasExecutable = delegate.isExecutable(receiver);
            try {
                Object result = delegate.execute(receiver, arguments);
                assert wasExecutable : violationInvariant(receiver, arguments);
                assert validInteropReturn(receiver, result);
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
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object instantiate(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.instantiate(receiver, arguments);
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, arguments);
            assert validArguments(receiver, arguments);
            boolean wasInstantiable = delegate.isInstantiable(receiver);
            try {
                Object result = delegate.instantiate(receiver, arguments);
                assert wasInstantiable : violationInvariant(receiver, arguments);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof ArityException || e instanceof UnsupportedTypeException : violationInvariant(receiver, arguments);
                assert !(e instanceof UnsupportedMessageException) || !wasInstantiable : violationInvariant(receiver, arguments);
                throw e;
            }
        }

        @Override
        public boolean isString(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.isString(receiver);
            }
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
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public String asString(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asString(receiver);
            }
            assert preCondition(receiver);
            boolean wasString = delegate.isString(receiver);
            try {
                String result = delegate.asString(receiver);
                assert wasString : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasString : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public TruffleString asTruffleString(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asTruffleString(receiver);
            }
            assert preCondition(receiver);
            boolean wasString = delegate.isString(receiver);
            try {
                TruffleString result = delegate.asTruffleString(receiver);
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
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.fitsInByte(receiver);
            }
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
            assert validProtocolReturn(receiver, fits);
            return fits;
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.fitsInShort(receiver);
            }
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
            assert validProtocolReturn(receiver, fits);
            return fits;
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.fitsInInt(receiver);
            }
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
            assert validProtocolReturn(receiver, fits);
            return fits;
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.fitsInLong(receiver);
            }
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
            assert validProtocolReturn(receiver, fits);
            return fits;
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.fitsInFloat(receiver);
            }
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
            assert validProtocolReturn(receiver, fits);
            return fits;
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.fitsInDouble(receiver);
            }
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
            assert validProtocolReturn(receiver, fits);
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
                assert validProtocolReturn(receiver, result);
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
                assert validProtocolReturn(receiver, result);
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
                assert validProtocolReturn(receiver, result);
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
                assert validProtocolReturn(receiver, result);
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
                assert validProtocolReturn(receiver, result);
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
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasMembers(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasMembers(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object readMember(Object receiver, String identifier) throws UnsupportedMessageException, UnknownIdentifierException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.readMember(receiver, identifier);
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean wasReadable = delegate.isMemberReadable(receiver, identifier);
            try {
                Object result = delegate.readMember(receiver, identifier);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasReadable || isMultiThreaded(receiver) : violationInvariant(receiver, identifier);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownIdentifierException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeMember(Object receiver, String identifier, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            if (CompilerDirectives.inCompiledCode()) {
                delegate.writeMember(receiver, identifier, value);
                return;
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            assert validInteropArgument(receiver, value);
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
            if (CompilerDirectives.inCompiledCode()) {
                delegate.removeMember(receiver, identifier);
                return;
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
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
        public Object invokeMember(Object receiver, String identifier, Object... arguments)
                        throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.invokeMember(receiver, identifier, arguments);
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            assert validProtocolArgument(receiver, arguments);
            assert validArguments(receiver, arguments);
            boolean wasInvocable = delegate.isMemberInvocable(receiver, identifier);
            try {
                Object result = delegate.invokeMember(receiver, identifier, arguments);
                assert delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
                assert wasInvocable || isMultiThreaded(receiver) : violationInvariant(receiver, identifier);
                assert validInteropReturn(receiver, result);
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
                assert validInteropReturn(receiver, result);
                assert validProtocolArgument(receiver, internal);
                assert isMultiThreaded(receiver) || assertMemberKeys(receiver, result, internal);
                assert !delegate.hasScopeParent(receiver) || assertScopeMembers(receiver, result, getUncached().getMembers(delegate.getScopeParent(receiver), internal));
                assert validInteropReturn(receiver, result);
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
            for (long i = 0; i < arraySize; i++) {
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

        private static boolean assertScopeMembers(Object receiver, Object allMembers, Object parentMembers) {
            assert parentMembers != null : violationPost(receiver, parentMembers);
            InteropLibrary allUncached = InteropLibrary.getUncached(allMembers);
            InteropLibrary parentUncached = InteropLibrary.getUncached(parentMembers);
            assert allUncached.hasArrayElements(allMembers) : violationPost(receiver, allMembers);
            assert parentUncached.hasArrayElements(parentMembers) : violationPost(receiver, parentMembers);
            long allSize;
            long parentSize;
            try {
                allSize = allUncached.getArraySize(allMembers);
                parentSize = parentUncached.getArraySize(parentMembers);
            } catch (UnsupportedMessageException e) {
                assert false : violationPost(receiver, e);
                return true;
            }
            assert AssertUtils.validScopeMemberLengths(allSize, parentSize, allMembers, parentMembers);
            long currentSize = allSize - parentSize;
            for (long i = 0; i < parentSize; i++) {
                assert allUncached.isArrayElementReadable(allMembers, i + currentSize) : violationPost(receiver, allMembers);
                assert parentUncached.isArrayElementReadable(parentMembers, i) : violationPost(receiver, parentMembers);
                Object allElement;
                Object parentElement;
                try {
                    allElement = allUncached.readArrayElement(allMembers, i + currentSize);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    assert false : violationPost(receiver, allMembers);
                    return true;
                }
                try {
                    parentElement = parentUncached.readArrayElement(parentMembers, i);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    assert false : violationPost(receiver, parentMembers);
                    return true;
                }
                assert InteropLibrary.getUncached().isString(allElement) : violationPost(receiver, allElement);
                assert InteropLibrary.getUncached().isString(parentElement) : violationPost(receiver, parentElement);
                String allElementName;
                String parentElementName;
                try {
                    allElementName = InteropLibrary.getUncached().asString(allElement);
                } catch (UnsupportedMessageException e) {
                    assert false : violationInvariant(allElement);
                    return true;
                }
                try {
                    parentElementName = InteropLibrary.getUncached().asString(parentElement);
                } catch (UnsupportedMessageException e) {
                    assert false : violationInvariant(parentElement);
                    return true;
                }
                assert AssertUtils.validScopeMemberNames(allElementName, parentElementName, allMembers, parentMembers, i + currentSize, i);
            }
            return true;
        }

        @Override
        public boolean hasMemberReadSideEffects(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean result = delegate.hasMemberReadSideEffects(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            assert !result || (delegate.isMemberReadable(receiver, identifier) || isMultiThreaded(receiver)) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean hasMemberWriteSideEffects(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean result = delegate.hasMemberWriteSideEffects(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            assert !result || (delegate.isMemberWritable(receiver, identifier) || isMultiThreaded(receiver)) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isMemberReadable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean result = delegate.isMemberReadable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isMemberModifiable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, identifier);
            boolean result = delegate.isMemberModifiable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isMemberInsertable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean result = delegate.isMemberInsertable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberExisting(receiver, identifier) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isMemberRemovable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean result = delegate.isMemberRemovable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isMemberInvocable(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean result = delegate.isMemberInvocable(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) && !delegate.isMemberInsertable(receiver, identifier) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isMemberInternal(Object receiver, String identifier) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, identifier);
            boolean result = delegate.isMemberInternal(receiver, identifier);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver, identifier);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean hasHashEntries(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasHashEntries(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public long getHashSize(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                long result = delegate.getHashSize(receiver);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                assert !delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isHashEntryReadable(Object receiver, Object key) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean result = delegate.isHashEntryReadable(receiver, key);
            assert !result || delegate.hasHashEntries(receiver) && !delegate.isHashEntryInsertable(receiver, key) : violationInvariant(receiver, key);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object readHashValue(Object receiver, Object key) throws UnsupportedMessageException, UnknownKeyException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.readHashValue(receiver, key);
            }
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean wasReadable = delegate.isHashEntryReadable(receiver, key);
            try {
                Object result = delegate.readHashValue(receiver, key);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver, key);
                assert wasReadable || isMultiThreaded(receiver) : violationInvariant(receiver, key);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownKeyException : violationPost(receiver, e);
                assert !(e instanceof UnsupportedMessageException) || !wasReadable : violationInvariant(receiver, key);
                throw e;
            }
        }

        @Override
        public Object readHashValueOrDefault(Object receiver, Object key, Object defaultValue) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.readHashValueOrDefault(receiver, key, defaultValue);
            }
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            assert validInteropArgument(receiver, defaultValue);
            try {
                Object result = delegate.readHashValueOrDefault(receiver, key, defaultValue);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver, key);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public boolean isHashEntryModifiable(Object receiver, Object key) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean result = delegate.isHashEntryModifiable(receiver, key);
            assert !result || delegate.hasHashEntries(receiver) && !delegate.isHashEntryInsertable(receiver, key) : violationInvariant(receiver, key);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isHashEntryInsertable(Object receiver, Object key) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean result = delegate.isHashEntryInsertable(receiver, key);
            assert !result || delegate.hasHashEntries(receiver) && !delegate.isHashEntryExisting(receiver, key) : violationInvariant(receiver, key);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isHashEntryWritable(Object receiver, Object key) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean result = delegate.isHashEntryWritable(receiver, key);
            assert result == (delegate.isHashEntryModifiable(receiver, key) || delegate.isHashEntryInsertable(receiver, key)) : violationInvariant(receiver, key);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public void writeHashEntry(Object receiver, Object key, Object value) throws UnsupportedMessageException, UnknownKeyException, UnsupportedTypeException {
            if (CompilerDirectives.inCompiledCode()) {
                delegate.writeHashEntry(receiver, key, value);
                return;
            }
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            assert validInteropArgument(receiver, value);
            boolean wasWritable = delegate.isHashEntryModifiable(receiver, key) || delegate.isHashEntryInsertable(receiver, key);
            try {
                delegate.writeHashEntry(receiver, key, value);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver, key);
                assert wasWritable || isMultiThreaded(receiver) : violationInvariant(receiver, key);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownKeyException || e instanceof UnsupportedTypeException : violationPost(receiver, e);
                assert !(e instanceof UnsupportedMessageException) || !wasWritable : violationInvariant(receiver, key);
                throw e;
            }
        }

        @Override
        public boolean isHashEntryRemovable(Object receiver, Object key) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean result = delegate.isHashEntryRemovable(receiver, key);
            assert !result || delegate.hasHashEntries(receiver) && !delegate.isHashEntryInsertable(receiver, key) : violationInvariant(receiver, key);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public void removeHashEntry(Object receiver, Object key) throws UnsupportedMessageException, UnknownKeyException {
            if (CompilerDirectives.inCompiledCode()) {
                delegate.removeHashEntry(receiver, key);
                return;
            }
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean wasRemovable = delegate.isHashEntryRemovable(receiver, key);
            try {
                delegate.removeHashEntry(receiver, key);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver, key);
                assert wasRemovable || isMultiThreaded(receiver) : violationInvariant(receiver, key);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof UnknownKeyException : violationPost(receiver, e);
                assert !(e instanceof UnsupportedMessageException) || !wasRemovable : violationInvariant(receiver, key);
                throw e;
            }
        }

        @Override
        public boolean isHashEntryExisting(Object receiver, Object key) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, key);
            boolean result = delegate.isHashEntryExisting(receiver, key);
            assert result == (delegate.isHashEntryReadable(receiver, key) || delegate.isHashEntryModifiable(receiver, key) || delegate.isHashEntryRemovable(receiver, key)) : violationInvariant(
                            receiver, key);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object getHashEntriesIterator(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getHashEntriesIterator(receiver);
            }
            assert preCondition(receiver);
            try {
                Object result = delegate.getHashEntriesIterator(receiver);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                assert assertIterator(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                assert !delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public Object getHashKeysIterator(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getHashKeysIterator(receiver);
            }
            assert preCondition(receiver);
            try {
                Object result = delegate.getHashKeysIterator(receiver);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                assert assertIterator(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                assert !delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public Object getHashValuesIterator(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getHashValuesIterator(receiver);
            }
            assert preCondition(receiver);
            try {
                Object result = delegate.getHashValuesIterator(receiver);
                assert delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                assert assertIterator(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                assert !delegate.hasHashEntries(receiver) : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            assert preCondition(receiver);
            return delegate.hasArrayElements(receiver);
        }

        @Override
        public Object readArrayElement(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.readArrayElement(receiver, index);
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, index);
            boolean wasReadable = delegate.isArrayElementReadable(receiver, index);
            try {
                Object result = delegate.readArrayElement(receiver, index);
                assert delegate.hasArrayElements(receiver) : violationInvariant(receiver, index);
                assert wasReadable || isMultiThreaded(receiver) : violationInvariant(receiver, index);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof InvalidArrayIndexException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeArrayElement(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
            if (CompilerDirectives.inCompiledCode()) {
                delegate.writeArrayElement(receiver, index, value);
                return;
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, index);
            assert validInteropArgument(receiver, value);
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
            if (CompilerDirectives.inCompiledCode()) {
                delegate.removeArrayElement(receiver, index);
                return;
            }
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, index);
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
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public boolean isArrayElementReadable(Object receiver, long index) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, index);
            boolean result = delegate.isArrayElementReadable(receiver, index);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementInsertable(receiver, index) : violationInvariant(receiver, index);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isArrayElementModifiable(Object receiver, long index) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, index);
            boolean result = delegate.isArrayElementModifiable(receiver, index);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementInsertable(receiver, index) : violationInvariant(receiver, index);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isArrayElementInsertable(Object receiver, long index) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, index);
            boolean result = delegate.isArrayElementInsertable(receiver, index);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementExisting(receiver, index) : violationInvariant(receiver, index);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isArrayElementRemovable(Object receiver, long index) {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, index);
            boolean result = delegate.isArrayElementRemovable(receiver, index);
            assert !result || delegate.hasArrayElements(receiver) && !delegate.isArrayElementInsertable(receiver, index) : violationInvariant(receiver, index);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        // region Buffer Messages

        @Override
        public boolean hasBufferElements(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasBufferElements(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isBufferWritable(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                final boolean result = delegate.isBufferWritable(receiver);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public long getBufferSize(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            try {
                final long result = delegate.getBufferSize(receiver);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public byte readBufferByte(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, byteOffset);
            try {
                final byte result = delegate.readBufferByte(receiver, byteOffset);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (UnsupportedMessageException e) {
                assert !delegate.hasBufferElements(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeBufferByte(Object receiver, long byteOffset, byte value) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, byteOffset);
            assert validProtocolArgument(receiver, value);
            try {
                delegate.writeBufferByte(receiver, byteOffset, value);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert delegate.isBufferWritable(receiver) : violationInvariant(receiver, byteOffset);
            } catch (UnsupportedMessageException e) {
                assert !delegate.isBufferWritable(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public short readBufferShort(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            try {
                final short result = delegate.readBufferShort(receiver, order, byteOffset);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (UnsupportedMessageException e) {
                assert !delegate.hasBufferElements(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeBufferShort(Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            assert validProtocolArgument(receiver, value);
            try {
                delegate.writeBufferShort(receiver, order, byteOffset, value);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert delegate.isBufferWritable(receiver) : violationInvariant(receiver, byteOffset);
            } catch (UnsupportedMessageException e) {
                assert !delegate.isBufferWritable(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public int readBufferInt(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            try {
                final int result = delegate.readBufferInt(receiver, order, byteOffset);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (UnsupportedMessageException e) {
                assert !delegate.hasBufferElements(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeBufferInt(Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            assert validProtocolArgument(receiver, value);
            try {
                delegate.writeBufferInt(receiver, order, byteOffset, value);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert delegate.isBufferWritable(receiver) : violationInvariant(receiver, byteOffset);
            } catch (UnsupportedMessageException e) {
                assert !delegate.isBufferWritable(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public long readBufferLong(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            try {
                final long result = delegate.readBufferLong(receiver, order, byteOffset);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (UnsupportedMessageException e) {
                assert !delegate.hasBufferElements(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeBufferLong(Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            assert validProtocolArgument(receiver, value);
            try {
                delegate.writeBufferLong(receiver, order, byteOffset, value);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert delegate.isBufferWritable(receiver) : violationInvariant(receiver, byteOffset);
            } catch (UnsupportedMessageException e) {
                assert !delegate.isBufferWritable(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public float readBufferFloat(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            try {
                final float result = delegate.readBufferFloat(receiver, order, byteOffset);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (UnsupportedMessageException e) {
                assert !delegate.hasBufferElements(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeBufferFloat(Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            assert validProtocolArgument(receiver, value);
            try {
                delegate.writeBufferFloat(receiver, order, byteOffset, value);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert delegate.isBufferWritable(receiver) : violationInvariant(receiver, byteOffset);
            } catch (UnsupportedMessageException e) {
                assert !delegate.isBufferWritable(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public double readBufferDouble(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            try {
                final double result = delegate.readBufferDouble(receiver, order, byteOffset);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (UnsupportedMessageException e) {
                assert !delegate.hasBufferElements(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public void writeBufferDouble(Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedMessageException, InvalidBufferOffsetException {
            assert preCondition(receiver);
            assert validProtocolArgument(receiver, order);
            assert validProtocolArgument(receiver, byteOffset);
            assert validProtocolArgument(receiver, value);
            try {
                delegate.writeBufferDouble(receiver, order, byteOffset, value);
                assert delegate.hasBufferElements(receiver) : violationInvariant(receiver, byteOffset);
                assert delegate.isBufferWritable(receiver) : violationInvariant(receiver, byteOffset);
            } catch (UnsupportedMessageException e) {
                assert !delegate.isBufferWritable(receiver) : violationPost(receiver, e);
                throw e;
            } catch (InteropException e) {
                assert e instanceof InvalidBufferOffsetException : violationPost(receiver, e);
                throw e;
            }
        }

        // endregion

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
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asPointer(receiver);
            }

            assert preCondition(receiver);
            boolean wasPointer = delegate.isPointer(receiver);
            try {
                long result = delegate.asPointer(receiver);
                assert wasPointer : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasPointer : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public LocalDate asDate(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asDate(receiver);
            }
            assert preCondition(receiver);
            boolean hasDate = delegate.isDate(receiver);
            try {
                LocalDate result = delegate.asDate(receiver);
                assert hasDate : violationInvariant(receiver);
                assert !delegate.isTimeZone(receiver) || delegate.isTime(receiver) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hasDate : violationInvariant(receiver);
                assert !delegate.isTimeZone(receiver) || !delegate.isTime(receiver) || hasFixedTimeZone(receiver) : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public LocalTime asTime(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asTime(receiver);
            }
            assert preCondition(receiver);
            boolean hasTime = delegate.isTime(receiver);
            try {
                LocalTime result = delegate.asTime(receiver);
                assert hasTime : violationInvariant(receiver);
                assert !delegate.isTimeZone(receiver) || delegate.isDate(receiver) || hasFixedTimeZone(receiver) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                assert validProtocolReturn(receiver, result);
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
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asTimeZone(receiver);
            }
            assert preCondition(receiver);
            boolean hasTimeZone = delegate.isTimeZone(receiver);
            try {
                ZoneId result = delegate.asTimeZone(receiver);
                assert hasTimeZone : violationInvariant(receiver);
                assert ((delegate.isDate(receiver) || result.getRules().isFixedOffset()) && delegate.isTime(receiver)) ||
                                (!delegate.isDate(receiver) && !delegate.isTime(receiver)) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hasTimeZone : violationInvariant(receiver);
                throw e;
            }
        }

        private boolean hasFixedTimeZone(Object receiver) {
            try {
                return delegate.asTimeZone(receiver).getRules().isFixedOffset();
            } catch (InteropException e) {
                throw shouldNotReachHere(violationInvariant(receiver));
            }
        }

        @Override
        public Duration asDuration(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asDuration(receiver);
            }
            assert preCondition(receiver);
            boolean wasDuration = delegate.isDuration(receiver);
            try {
                Duration result = delegate.asDuration(receiver);
                assert wasDuration : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DURATION);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasDuration : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public Instant asInstant(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.asInstant(receiver);
            }
            assert preCondition(receiver);
            boolean hasDateAndTime = delegate.isDate(receiver) && delegate.isTime(receiver) && delegate.isTimeZone(receiver);
            try {
                Instant result = delegate.asInstant(receiver);
                assert hasDateAndTime : violationInvariant(receiver);
                assert ZonedDateTime.of(delegate.asDate(receiver), delegate.asTime(receiver),
                                delegate.asTimeZone(receiver)).//
                                toInstant().equals(result) : violationInvariant(receiver);
                assert notOtherType(receiver, Type.DATE_TIME_ZONE);
                assert validProtocolReturn(receiver, result);
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
            assert !delegate.isTimeZone(receiver) || (delegate.isTime(receiver) && result) || ((!delegate.isTime(receiver) || hasFixedTimeZone(receiver)) && !result) : violationInvariant(receiver);
            assert !result || notOtherType(receiver, Type.DATE_TIME_ZONE);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isTime(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isTime(receiver);
            assert !delegate.isTimeZone(receiver) || ((delegate.isDate(receiver) || hasFixedTimeZone(receiver)) && result) || (!delegate.isDate(receiver) && !result) : violationInvariant(receiver);
            assert !result || notOtherType(receiver, Type.DATE_TIME_ZONE);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isTimeZone(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isTimeZone(receiver);
            assert !result || ((delegate.isDate(receiver) || hasFixedTimeZone(receiver)) && delegate.isTime(receiver)) ||
                            (!delegate.isDate(receiver) && !delegate.isTime(receiver)) : violationInvariant(receiver);
            assert !result || notOtherType(receiver, Type.DATE_TIME_ZONE);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isDuration(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isDuration(receiver);
            assert !result || notOtherType(receiver, Type.DURATION);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isException(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isException(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public ExceptionType getExceptionType(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            ExceptionType result = delegate.getExceptionType(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isExceptionIncompleteSource(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.isExceptionIncompleteSource(receiver);
            }
            assert preCondition(receiver);
            boolean wasParseError;
            try {
                wasParseError = delegate.getExceptionType(receiver) == ExceptionType.PARSE_ERROR;
            } catch (UnsupportedMessageException e) {
                wasParseError = false;
            }
            try {
                boolean result = delegate.isExceptionIncompleteSource(receiver);
                assert !result || wasParseError : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasParseError : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public int getExceptionExitStatus(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getExceptionExitStatus(receiver);
            }
            assert preCondition(receiver);
            boolean wasExit;
            try {
                wasExit = delegate.getExceptionType(receiver) == ExceptionType.EXIT;
            } catch (UnsupportedMessageException e) {
                wasExit = false;
            }
            try {
                int result = delegate.getExceptionExitStatus(receiver);
                assert wasExit : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasExit : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public RuntimeException throwException(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.throwException(receiver);
            }
            assert preCondition(receiver);
            boolean wasException = delegate.isException(receiver);
            boolean wasAbstractTruffleException = false;
            boolean unsupported = false;
            try {
                throw delegate.throwException(receiver);
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasException : violationInvariant(receiver);
                unsupported = true;
                throw e;
            } catch (Throwable e) {
                wasAbstractTruffleException = InteropAccessor.EXCEPTION.isException(e);
                throw e;
            } finally {
                if (!unsupported) {
                    assert wasException : violationInvariant(receiver);
                    assert wasAbstractTruffleException : violationInvariant(receiver);
                }
            }
        }

        @Override
        public boolean hasExceptionCause(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasExceptionCause(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object getExceptionCause(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getExceptionCause(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasExceptionCause = delegate.hasExceptionCause(receiver);
            try {
                Object result = delegate.getExceptionCause(receiver);
                assert wasHasExceptionCause : violationInvariant(receiver);
                assert assertException(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasExceptionCause : violationInvariant(receiver);
                throw e;
            }
        }

        private static boolean assertException(Object receiver, Object exception) {
            InteropLibrary uncached = InteropLibrary.getUncached(exception);
            assert uncached.isException(exception) : violationPost(receiver, exception);
            return true;
        }

        @Override
        public boolean hasExceptionMessage(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasExceptionMessage(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object getExceptionMessage(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getExceptionMessage(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasExceptionMessage = delegate.hasExceptionMessage(receiver);
            try {
                Object result = delegate.getExceptionMessage(receiver);
                assert wasHasExceptionMessage : violationInvariant(receiver);
                assert assertString(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasExceptionMessage : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasExceptionStackTrace(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasExceptionStackTrace(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object getExceptionStackTrace(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getExceptionStackTrace(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasExceptionStackTrace = delegate.hasExceptionStackTrace(receiver);
            try {
                Object result = delegate.getExceptionStackTrace(receiver);
                assert wasHasExceptionStackTrace : violationInvariant(receiver);
                assert verifyStackTrace(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasExceptionStackTrace : violationInvariant(receiver);
                throw e;
            }
        }

        private static boolean verifyStackTrace(Object receiver, Object stackTrace) {
            assert stackTrace != null : violationPost(receiver, stackTrace);
            InteropLibrary stackTraceLib = InteropLibrary.getFactory().getUncached(stackTrace);
            assert stackTraceLib.hasArrayElements(stackTrace) : violationPost(receiver, stackTrace);
            return true;
        }

        @Override
        public boolean hasExecutableName(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasExecutableName(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object getExecutableName(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getExecutableName(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasExecutableName = delegate.hasExecutableName(receiver);
            try {
                Object result = delegate.getExecutableName(receiver);
                assert wasHasExecutableName : violationInvariant(receiver);
                assert assertString(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasExecutableName : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasDeclaringMetaObject(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasDeclaringMetaObject(receiver);
            return result;
        }

        @Override
        public Object getDeclaringMetaObject(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getDeclaringMetaObject(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasDeclaringMetaObject = delegate.hasDeclaringMetaObject(receiver);
            try {
                Object result = delegate.getDeclaringMetaObject(receiver);
                assert wasHasDeclaringMetaObject : violationInvariant(receiver);
                assert verifyDeclaringMetaObject(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasDeclaringMetaObject : violationInvariant(receiver);
                throw e;
            }
        }

        private static boolean verifyDeclaringMetaObject(Object receiver, Object meta) {
            assert meta != null : violationPost(receiver, meta);
            InteropLibrary metaLib = InteropLibrary.getFactory().getUncached(meta);
            assert metaLib.isMetaObject(meta) : violationPost(receiver, meta);
            try {
                assert metaLib.getMetaSimpleName(meta) != null : violationPost(receiver, meta);
                assert metaLib.getMetaQualifiedName(meta) != null : violationPost(receiver, meta);
            } catch (UnsupportedMessageException e) {
                assert false : violationPost(receiver, meta);
            }
            return true;
        }

        @Override
        public Object toDisplayString(Object receiver, boolean allowSideEffects) {
            assert preCondition(receiver);
            assert validNonInteropArgument(receiver, allowSideEffects);
            Object result = delegate.toDisplayString(receiver, allowSideEffects);
            assert assertString(receiver, result);
            assert validInteropReturn(receiver, result);
            return result;
        }

        @Override
        public boolean hasIterator(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasIterator(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object getIterator(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getIterator(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasIterator = delegate.hasIterator(receiver);
            try {
                Object result = delegate.getIterator(receiver);
                assert wasHasIterator : violationInvariant(receiver);
                assert assertIterator(receiver, result);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasIterator : violationInvariant(receiver);
                throw e;
            }
        }

        private static boolean assertIterator(Object receiver, Object iterator) {
            assert iterator != null : violationPost(receiver, iterator);
            InteropLibrary uncached = InteropLibrary.getUncached(iterator);
            assert uncached.isIterator(iterator) : violationPost(receiver, iterator);
            return true;
        }

        @Override
        public boolean isIterator(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isIterator(receiver);
            assert !result || notOtherType(receiver, Type.ITERATOR);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean hasIteratorNextElement(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.hasIteratorNextElement(receiver);
            }
            assert preCondition(receiver);
            boolean wasIterator = delegate.isIterator(receiver);
            try {
                boolean result = delegate.hasIteratorNextElement(receiver);
                assert wasIterator : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasIterator : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public Object getIteratorNextElement(Object receiver) throws UnsupportedMessageException, StopIterationException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getIteratorNextElement(receiver);
            }
            assert preCondition(receiver);
            boolean wasIterator = delegate.isIterator(receiver);
            try {
                Object result = delegate.getIteratorNextElement(receiver);
                assert wasIterator : violationInvariant(receiver);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException || e instanceof StopIterationException : violationPost(receiver, e);
                throw e;
            }
        }

        @Override
        public boolean hasSourceLocation(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.hasSourceLocation(receiver);
            }
            assert preCondition(receiver);
            boolean result = delegate.hasSourceLocation(receiver);
            if (result) {
                try {
                    assert delegate.getSourceLocation(receiver) != null : violationPost(receiver, result);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            } else {
                assert assertHasNoSourceSection(receiver);
            }
            assert validProtocolReturn(receiver, result);
            return result;
        }

        private boolean assertHasNoSourceSection(Object receiver) {
            try {
                delegate.getSourceLocation(receiver);
                assert false : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
            }
            return true;
        }

        @Override
        public SourceSection getSourceLocation(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getSourceLocation(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasSourceLocation = delegate.hasSourceLocation(receiver);
            try {
                SourceSection result = delegate.getSourceLocation(receiver);
                assert wasHasSourceLocation : violationInvariant(receiver);
                assert result != null : violationPost(receiver, result);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasSourceLocation : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasLanguage(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasLanguage(receiver);
            if (result) {
                try {
                    assert delegate.getLanguage(receiver) != null : violationPost(receiver, result);
                } catch (InteropException e) {
                    assert false : violationInvariant(receiver);
                } catch (Exception e) {
                }
            } else {
                assert assertHasNoLanguage(receiver);
            }
            assert validProtocolReturn(receiver, result);
            return result;
        }

        private boolean assertHasNoLanguage(Object receiver) {
            try {
                delegate.getLanguage(receiver);
                assert false : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
            }
            return true;
        }

        @Override
        public Class<? extends TruffleLanguage<?>> getLanguage(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getLanguage(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasLanguage = delegate.hasLanguage(receiver);
            try {
                Class<? extends TruffleLanguage<?>> result = delegate.getLanguage(receiver);
                assert wasHasLanguage : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasLanguage : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasMetaObject(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.hasMetaObject(receiver);
            if (result) {
                assert assertHasMetaObject(receiver, result);
            } else {
                assert assertHasNoMetaObject(receiver);
            }
            assert validProtocolReturn(receiver, result);
            return result;
        }

        private boolean assertHasMetaObject(Object receiver, boolean result) {
            try {
                Object meta = delegate.getMetaObject(receiver);
                assert verifyMetaObject(receiver, meta);
            } catch (InteropException e) {
                assert false : violationInvariant(receiver);
            } catch (Exception e) {
            }
            return true;
        }

        private static boolean verifyMetaObject(Object receiver, Object meta) throws UnsupportedMessageException {
            assert meta != null : violationPost(receiver, meta);
            InteropLibrary metaLib = InteropLibrary.getFactory().getUncached(meta);
            assert metaLib.isMetaObject(meta) : violationPost(receiver, meta);
            assert metaLib.getMetaSimpleName(meta) != null : violationPost(receiver, meta);
            assert metaLib.getMetaQualifiedName(meta) != null : violationPost(receiver, meta);
            return true;
        }

        private boolean assertHasNoMetaObject(Object receiver) {
            try {
                delegate.getMetaObject(receiver);
                assert false : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
            }
            return true;
        }

        @Override
        public Object getMetaObject(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getMetaObject(receiver);
            }
            assert preCondition(receiver);
            boolean wasHasMetaObject = delegate.hasMetaObject(receiver);
            try {
                Object result = delegate.getMetaObject(receiver);
                assert wasHasMetaObject : violationInvariant(receiver);
                assert verifyMetaObject(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasHasMetaObject : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isMetaObject(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isMetaObject(receiver);
            if (result) {
                assert assertMetaObject(receiver);
            } else {
                assert assertNoMetaObject(receiver);
                assert !result || notOtherType(receiver, Type.META_OBJECT);
            }
            assert validProtocolReturn(receiver, result);
            return result;
        }

        private boolean assertNoMetaObject(Object receiver) {
            try {
                delegate.isMetaInstance(receiver, receiver);
                assert false : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
            }
            try {
                delegate.getMetaSimpleName(receiver);
                assert false : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
            }
            try {
                delegate.getMetaQualifiedName(receiver);
                assert false : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
            }
            try {
                delegate.getMetaParents(receiver);
                assert false : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
            }
            return true;
        }

        private boolean assertMetaObject(Object receiver) {
            try {
                delegate.isMetaInstance(receiver, receiver);
            } catch (UnsupportedMessageException e) {
                assert false : violationInvariant(receiver);
            }
            try {
                assert assertString(receiver, delegate.getMetaSimpleName(receiver)) : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
                assert false : violationInvariant(receiver);
            }
            try {
                assert assertString(receiver, delegate.getMetaQualifiedName(receiver)) : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
                assert false : violationInvariant(receiver);
            }
            return true;
        }

        @Override
        public Object getMetaQualifiedName(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getMetaQualifiedName(receiver);
            }
            assert preCondition(receiver);
            boolean wasMetaObject = delegate.isMetaObject(receiver);
            try {
                Object result = delegate.getMetaQualifiedName(receiver);
                assert wasMetaObject : violationInvariant(receiver);
                assert assertString(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasMetaObject : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public Object getMetaSimpleName(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getMetaSimpleName(receiver);
            }
            assert preCondition(receiver);
            boolean wasMetaObject = delegate.isMetaObject(receiver);
            try {
                Object result = delegate.getMetaSimpleName(receiver);
                assert wasMetaObject : violationInvariant(receiver);
                assert assertString(receiver, result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasMetaObject : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean isMetaInstance(Object receiver, Object instance) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.isMetaInstance(receiver, instance);
            }
            assert preCondition(receiver);
            assert validInteropArgument(receiver, instance);
            boolean wasMetaObject = delegate.isMetaObject(receiver);
            try {
                boolean result = delegate.isMetaInstance(receiver, instance);
                assert wasMetaObject : violationInvariant(receiver);
                assert validProtocolReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !wasMetaObject : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        public boolean hasMetaParents(Object receiver) {
            assert preCondition(receiver);
            boolean wasMetaObject = delegate.isMetaObject(receiver);
            boolean result = delegate.hasMetaParents(receiver);
            if (result) {
                assert wasMetaObject : violationInvariant(receiver);
            } else if (!wasMetaObject) {
                assert assertNoMetaObject(receiver);
            }
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public Object getMetaParents(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getMetaParents(receiver);
            }
            boolean wasMetaObject = delegate.isMetaObject(receiver);
            boolean hadMetaParents = delegate.hasMetaParents(receiver);
            try {
                Object result = delegate.getMetaParents(receiver);
                assert wasMetaObject : violationInvariant(receiver);
                assert hadMetaParents : violationInvariant(receiver);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hadMetaParents : violationInvariant(receiver);
                throw e;
            }
        }

        @Override
        protected TriState isIdenticalOrUndefined(Object receiver, Object other) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, other);
            TriState result = delegate.isIdenticalOrUndefined(receiver, other);
            assert verifyIsSameOrUndefined(delegate, result, receiver, other);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        static boolean verifyIsSameOrUndefined(InteropLibrary library, TriState result, Object receiver, Object other) {
            if (result != TriState.UNDEFINED) {
                int hashCode = 0;
                try {
                    hashCode = library.identityHashCode(receiver);
                } catch (Exception t) {
                    throw shouldNotReachHere(t);
                }
            }
            return true;
        }

        @Override
        public int identityHashCode(Object receiver) throws UnsupportedMessageException {
            assert preCondition(receiver);
            int result;
            try {
                result = delegate.identityHashCode(receiver);
                assert delegate.hasIdentity(receiver) : violationInvariant(receiver);
            } catch (UnsupportedMessageException e) {
                assert !delegate.hasIdentity(receiver) : violationInvariant(receiver);
                throw e;
            }
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean isIdentical(Object receiver, Object other, InteropLibrary otherInterop) {
            assert preCondition(receiver);
            assert validInteropArgument(receiver, other);
            assert validProtocolArgument(receiver, otherInterop);
            boolean result = delegate.isIdentical(receiver, other, otherInterop);
            assert verifyIsSame(result, receiver, other, otherInterop);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        boolean verifyIsSame(boolean result, Object receiver, Object other, InteropLibrary otherInterop) {
            try {
                InteropLibrary otherDelegate = otherInterop;
                if (otherInterop instanceof Asserts) {
                    // avoid recursions
                    otherDelegate = ((Asserts) otherInterop).delegate;
                }
                // verify symmetric property
                assert result == otherDelegate.isIdentical(other, receiver, delegate) : violationInvariant(receiver);
                if (result) {
                    // if true identity hash code must be equal
                    assert delegate.identityHashCode(receiver) == otherDelegate.identityHashCode(other) : violationInvariant(receiver);
                }

                // verify reflexivity
                TriState state = delegate.isIdenticalOrUndefined(receiver, other);
                if (state != TriState.UNDEFINED) {
                    assert delegate.isIdentical(receiver, receiver, delegate) : violationInvariant(receiver);
                }

                // also check isIdenticalOrUndefined results as they would be skipped with the
                // isIdentical default implementation.
                verifyIsSameOrUndefined(delegate, state, receiver, other);
                verifyIsSameOrUndefined(otherDelegate, otherDelegate.isIdenticalOrUndefined(other, receiver), other, receiver);
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
            return true;
        }

        @Override
        public boolean isScope(Object receiver) {
            assert preCondition(receiver);
            boolean result = delegate.isScope(receiver);
            assert !result || delegate.hasMembers(receiver) : violationInvariant(receiver);
            assert !result || delegate.hasLanguage(receiver) : violationInvariant(receiver);
            assert validProtocolReturn(receiver, result);
            return result;
        }

        @Override
        public boolean hasScopeParent(Object receiver) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.hasScopeParent(receiver);
            }
            assert preCondition(receiver);
            boolean result = delegate.hasScopeParent(receiver);
            if (result) {
                assert delegate.isScope(receiver) : violationInvariant(receiver);
                try {
                    assert validScope(delegate.getScopeParent(receiver));
                } catch (UnsupportedMessageException e) {
                    assert false : violationInvariant(receiver);
                }
            } else {
                try {
                    delegate.getScopeParent(receiver);
                    assert false : violationInvariant(receiver);
                } catch (UnsupportedMessageException e) {
                }
            }
            return result;
        }

        @Override
        public Object getScopeParent(Object receiver) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getScopeParent(receiver);
            }
            assert preCondition(receiver);
            boolean hadScopeParent = delegate.hasScopeParent(receiver);
            try {
                Object result = delegate.getScopeParent(receiver);
                assert hadScopeParent : violationInvariant(receiver);
                assert delegate.isScope(receiver) : violationInvariant(receiver);
                assert validScope(result);
                assert validInteropReturn(receiver, result);
                return result;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(receiver);
                assert !hadScopeParent : violationInvariant(receiver);
                throw e;
            }
        }
    }
}

class InteropLibrarySnippets {

    abstract static class StatementNode extends Node {
        abstract void executeVoid(VirtualFrame frame);
    }

    static class BlockNode extends StatementNode {

        @Children private StatementNode[] children;

        BlockNode(StatementNode... children) {
            this.children = children;
        }

        @Override
        @ExplodeLoop
        void executeVoid(VirtualFrame frame) {
            for (StatementNode child : children) {
                child.executeVoid(frame);
            }
        }
    }

    @SuppressWarnings("serial")
    private abstract static class AbstractTruffleException extends RuntimeException {
    }

    // BEGIN: InteropLibrarySnippets.TryCatchNode
    static final class TryCatchNode extends StatementNode {

        @Node.Child private BlockNode block;
        @Node.Child private BlockNode catchBlock;
        @Node.Child private BlockNode finallyBlock;
        private final BranchProfile exceptionProfile;

        TryCatchNode(BlockNode block, BlockNode catchBlock,
                        BlockNode finallyBlock) {
            this.block = block;
            this.catchBlock = catchBlock;
            this.finallyBlock = finallyBlock;
            this.exceptionProfile = BranchProfile.create();
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            RuntimeException rethrowException = null;
            try {
                block.executeVoid(frame);
            } catch (AbstractTruffleException ex) {
                exceptionProfile.enter();
                try {
                    if (catchBlock != null) {
                        catchBlock.executeVoid(frame);
                        // do not rethrow if handled
                        rethrowException = null;
                    } else {
                        // rethrow if not handled
                        rethrowException = ex;
                    }
                } catch (AbstractTruffleException e) {
                    rethrowException = e;
                }
            } catch (ControlFlowException cfe) {
                // run finally blocks for control flow
                rethrowException = cfe;
            }
            // Java finally blocks that execute nodes are not allowed for
            // compilation as code in finally blocks is duplicated
            // by the Java bytecode compiler. This can lead to
            // exponential code growth in worst cases.
            if (finallyBlock != null) {
                finallyBlock.executeVoid(frame);
            }
            if (rethrowException != null) {
                throw rethrowException;
            }
        }
    }
    // END: InteropLibrarySnippets.TryCatchNode
}
