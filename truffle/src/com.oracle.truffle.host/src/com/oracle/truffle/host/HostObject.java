/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.sql.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.host.HostContext.ToGuestValueNode;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("unused")
final class HostObject implements TruffleObject {

    static final int LIMIT = 5;

    // PE-friendly ByteBuffer's implementations:
    private static final Class<? extends ByteBuffer> HEAP_BYTE_BUFFER_CLASS = ByteBuffer.allocate(0).getClass();
    private static final Class<? extends ByteBuffer> HEAP_BYTE_BUFFER_R_CLASS = ByteBuffer.allocate(0).asReadOnlyBuffer().getClass();
    private static final Class<? extends ByteBuffer> DIRECT_BYTE_BUFFER_CLASS = ByteBuffer.allocateDirect(0).getClass();
    private static final Class<? extends ByteBuffer> DIRECT_BYTE_BUFFER_R_CLASS = ByteBuffer.allocateDirect(0).asReadOnlyBuffer().getClass();

    private static final ZoneId UTC = ZoneId.of("UTC");

    static final HostObject NULL = new HostObject(null, null, null);

    final Object obj;
    final HostContext context;
    /**
     * Either null (default), the Class if this is a static class, or an optional HostException if
     * the object is an instance of Throwable.
     */
    private final Object extraInfo;

    private HostObject(Object obj, HostContext context, Object extraInfo) {
        this.obj = obj;
        this.context = context;
        this.extraInfo = extraInfo;
    }

    static HostObject forClass(Class<?> clazz, HostContext context) {
        assert clazz != null;
        return new HostObject(clazz, context, null);
    }

    static HostObject forStaticClass(Class<?> clazz, HostContext context) {
        assert clazz != null;
        return new HostObject(clazz, context, clazz);
    }

    static HostObject forObject(Object object, HostContext context) {
        assert object != null && !(object instanceof Class<?>);
        return new HostObject(object, context, null);
    }

    static HostObject forException(Throwable object, HostContext context, HostException hostException) {
        Objects.requireNonNull(object);
        return new HostObject(object, context, hostException);
    }

    static boolean isInstance(HostLanguage language, Object v) {
        Object obj = HostLanguage.unwrapIfScoped(language, v);
        return obj instanceof HostObject || obj instanceof HostException;
    }

    static Object withContext(HostLanguage language, Object originalValue, HostContext context) {
        assert context != null;
        Object obj = HostLanguage.unwrapIfScoped(language, originalValue);
        if (obj instanceof HostObject) {
            HostObject hostObject = (HostObject) obj;
            return new HostObject(hostObject.obj, context, hostObject.extraInfo);
        } else if (obj instanceof HostException) {
            return new HostException(((HostException) obj).getOriginal(), context);
        } else {
            throw CompilerDirectives.shouldNotReachHere("Parameter must be HostObject or HostException.");
        }
    }

    static boolean isJavaInstance(HostLanguage language, Class<?> targetType, Object javaObject) {
        Object unboxed = unboxHostObject(language, javaObject);
        if (unboxed != null) {
            return targetType.isInstance(unboxed);
        }
        return false;
    }

    static Object unboxHostObject(HostLanguage language, Object value) {
        Object v = HostLanguage.unwrapIfScoped(language, value);
        if (v instanceof HostObject) {
            return ((HostObject) v).obj;
        } else if (v instanceof HostException) {
            return ((HostException) v).delegate.obj;
        }
        return null;
    }

    static Object valueOf(HostLanguage language, Object value) {
        Object v = HostLanguage.unwrapIfScoped(language, value);
        if (v instanceof HostObject) {
            return ((HostObject) v).obj;
        } else if (v instanceof HostException) {
            return ((HostException) v).delegate.obj;
        }
        return v;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(obj);
    }

    boolean isClass() {
        return obj instanceof Class<?>;
    }

    boolean isArrayClass() {
        return isClass() && asClass().isArray();
    }

    boolean isDefaultClass() {
        return isClass() && !asClass().isArray();
    }

    private static RuntimeException unboxEngineException(HostObject receiver, RuntimeException e) {
        AbstractHostAccess access = receiver.context.language.access;
        if (access.isEngineException(e)) {
            return access.unboxEngineException(e);
        }
        return null;
    }

    @ExportMessage
    boolean hasMembers() {
        return !isNull();
    }

    @ExportMessage
    static class IsMemberReadable {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedReadable) {
            assert cachedReadable == doUncached(receiver, name);
            return cachedReadable;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isReadable(receiver, receiver.getLookupClass(), name, receiver.isStaticClass(), receiver.isClass());
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class KeysArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final String[] keys;

        KeysArray(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < keys.length;
        }

        @ExportMessage
        String readArrayElement(long idx,
                        @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                error.enter();
                throw InvalidArrayIndexException.create(idx);
            }
            return keys[(int) idx];
        }
    }

    @ExportMessage
    Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        if (isNull()) {
            throw UnsupportedMessageException.create();
        }
        String[] fields = HostInteropReflect.findUniquePublicMemberNames(context, getLookupClass(), isStaticClass(), isClass(), includeInternal);
        return new KeysArray(fields);
    }

    @ExportMessage
    Object readMember(String name,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Shared("readField") @Cached ReadFieldNode readField,
                    @Shared("lookupMethod") @Cached LookupMethodNode lookupMethod,
                    @Cached LookupInnerClassNode lookupInnerClass,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, UnknownIdentifierException {
        if (isNull()) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        boolean isStatic = isStaticClass();
        Class<?> lookupClass = getLookupClass();
        HostFieldDesc foundField = lookupField.execute(this, lookupClass, name, isStatic);
        if (foundField != null) {
            return readField.execute(foundField, this);
        }
        HostMethodDesc foundMethod = lookupMethod.execute(this, lookupClass, name, isStatic);
        if (foundMethod != null) {
            return new HostFunction(foundMethod, this.obj, this.context);
        }

        if (isStatic) {
            LookupInnerClassNode lookupInnerClassNode = lookupInnerClass;
            if (HostInteropReflect.STATIC_TO_CLASS.equals(name)) {
                return HostObject.forClass(lookupClass, context);
            }
            Class<?> innerclass = lookupInnerClassNode.execute(lookupClass, name);
            if (innerclass != null) {
                return HostObject.forStaticClass(innerclass, context);
            }
        } else if (isClass() && HostInteropReflect.CLASS_TO_STATIC.equals(name)) {
            return HostObject.forStaticClass(asClass(), context);
        } else if (HostInteropReflect.ADAPTER_SUPER_MEMBER.equals(name) && HostAdapterFactory.isAdapterInstance(this.obj)) {
            return HostAdapterFactory.getSuperAdapter(this);
        }
        error.enter();
        throw UnknownIdentifierException.create(name);
    }

    @ExportMessage
    static class IsMemberModifiable {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedModifiable) {
            assert cachedModifiable == doUncached(receiver, name);
            return cachedModifiable;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isModifiable(receiver, receiver.getLookupClass(), name, receiver.isStaticClass());
        }

    }

    @ExportMessage
    static class IsMemberInternal {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedInternal) {
            assert cachedInternal == doUncached(receiver, name);
            return cachedInternal;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isInternal(receiver, receiver.getLookupClass(), name, receiver.isStaticClass());
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInsertable(String member) {
        return false;
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Cached WriteFieldNode writeField,
                    @Shared("error") @Cached BranchProfile error)
                    throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        if (isNull()) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        HostFieldDesc f = lookupField.execute(this, getLookupClass(), member, isStaticClass());
        if (f == null) {
            error.enter();
            throw UnknownIdentifierException.create(member);
        }
        try {
            writeField.execute(f, this, value);
        } catch (ClassCastException | NullPointerException e) {
            // conversion failed by ToJavaNode
            error.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, getMessage(e));
        }
    }

    @TruffleBoundary
    private static String getMessage(RuntimeException e) {
        return e.getMessage();
    }

    @ExportMessage
    static class IsMemberInvocable {

        @Specialization(guards = {"receiver.isStaticClass()", "receiver.isStaticClass() == cachedStatic", "receiver.getLookupClass() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        static boolean doCached(HostObject receiver, String name,
                        @Cached("receiver.isStaticClass()") boolean cachedStatic,
                        @Cached("receiver.getLookupClass()") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, name)") boolean cachedInvokable) {
            assert cachedInvokable == doUncached(receiver, name);
            return cachedInvokable;
        }

        @Specialization(replaces = "doCached")
        static boolean doUncached(HostObject receiver, String name) {
            if (receiver.isNull()) {
                return false;
            }
            return HostInteropReflect.isInvokable(receiver, receiver.getLookupClass(), name, receiver.isStaticClass());
        }
    }

    @ExportMessage
    Object invokeMember(String name, Object[] args,
                    @Shared("lookupMethod") @Cached LookupMethodNode lookupMethod,
                    @Shared("hostExecute") @Cached HostExecuteNode executeMethod,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Shared("readField") @Cached ReadFieldNode readField,
                    @CachedLibrary(limit = "5") InteropLibrary fieldValues,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedTypeException, ArityException, UnsupportedMessageException, UnknownIdentifierException {
        if (isNull()) {
            error.enter();
            throw UnsupportedMessageException.create();
        }

        boolean isStatic = isStaticClass();
        Class<?> lookupClass = getLookupClass();

        // (1) look for a method; if found, invoke it on obj.
        HostMethodDesc foundMethod = lookupMethod.execute(this, lookupClass, name, isStatic);
        if (foundMethod != null) {
            return executeMethod.execute(foundMethod, obj, args, context);
        }

        // (2) look for a field; if found, read its value and if that IsExecutable, Execute it.
        HostFieldDesc foundField = lookupField.execute(this, lookupClass, name, isStatic);
        if (foundField != null) {
            Object fieldValue = readField.execute(foundField, this);
            if (fieldValues.isExecutable(fieldValue)) {
                return fieldValues.execute(fieldValue, args);
            }
        }
        error.enter();
        throw UnknownIdentifierException.create(name);
    }

    @ExportMessage
    static class IsArrayElementReadable {

        @Specialization(guards = "isArray.execute(receiver)", limit = "1")
        static boolean doArray(HostObject receiver, long index,
                        @Shared("isArray") @Cached IsArrayNode isArray) {
            long size = Array.getLength(receiver.obj);
            return index >= 0 && index < size;
        }

        @Specialization(guards = "isList.execute(receiver)", limit = "1")
        static boolean doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("error") @Cached BranchProfile error) {
            try {
                long size = GuestToHostCalls.getListSize(receiver);
                return index >= 0 && index < size;
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = "isMapEntry.execute(receiver)", limit = "1")
        static boolean doMapEntry(HostObject receiver, long index,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) {
            return index >= 0 && index < 2;
        }

        @Specialization(guards = {"!isList.execute(receiver)", "!isArray.execute(receiver)", "!isMapEntry.execute(receiver)"}, limit = "1")
        static boolean doNotArrayOrList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) {
            return false;
        }
    }

    @ExportMessage
    static class IsArrayElementModifiable {

        @Specialization(guards = "isArray.execute(receiver)", limit = "1")
        static boolean doArray(HostObject receiver, long index,
                        @Shared("isArray") @Cached IsArrayNode isArray) {
            long size = Array.getLength(receiver.obj);
            return index >= 0 && index < size;
        }

        @Specialization(guards = "isList.execute(receiver)", limit = "1")
        static boolean doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("error") @Cached BranchProfile error) {
            try {
                long size = GuestToHostCalls.getListSize(receiver);
                return index >= 0 && index < size;
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = "isMapEntry.execute(receiver)", limit = "1")
        static boolean doMapEntry(HostObject receiver, long index,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) {
            return index == 1;
        }

        @Specialization(guards = {"!isList.execute(receiver)", "!isArray.execute(receiver)", "!isMapEntry.execute(receiver)"}, limit = "1")
        static boolean doNotArrayOrList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) {
            return false;
        }
    }

    @ExportMessage
    boolean isArrayElementInsertable(long index, @Shared("isList") @Cached IsListNode isList,
                    @Shared("error") @Cached BranchProfile error) {
        try {
            return isList.execute(this) && GuestToHostCalls.getListSize(this) == index;
        } catch (Throwable t) {
            error.enter();
            throw context.hostToGuestException(t);
        }
    }

    @ExportMessage
    static class WriteArrayElement {

        @Specialization(guards = {"isArray.execute(receiver)"}, limit = "1")
        @SuppressWarnings("unchecked")
        static void doArray(HostObject receiver, long index, Object value,
                        @Shared("toHost") @Cached HostToTypeNode toHostNode,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Cached ArraySet arraySet,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            Object obj = receiver.obj;
            Object javaValue;
            try {
                javaValue = toHostNode.execute(receiver.context, value, obj.getClass().getComponentType(), null, true);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                }
                throw e;
            }
            try {
                arraySet.execute(obj, (int) index, javaValue);
            } catch (ArrayIndexOutOfBoundsException e) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"isList.execute(receiver)"}, limit = "1")
        @SuppressWarnings("unchecked")
        static void doList(HostObject receiver, long index, Object value,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("toHost") @Cached HostToTypeNode toHostNode,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            Object javaValue;
            try {
                javaValue = toHostNode.execute(receiver.context, value, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                }
                throw e;
            }
            try {
                GuestToHostCalls.setListElement(receiver, index, javaValue);
            } catch (IndexOutOfBoundsException e) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = {"isMapEntry.execute(receiver)"}, limit = "1")
        @SuppressWarnings("unchecked")
        static void doMapEntry(HostObject receiver, long index, Object value,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry,
                        @Shared("toHost") @Cached HostToTypeNode toHostNode,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index == 1) {
                Object hostValue;
                try {
                    hostValue = toHostNode.execute(receiver.context, value, Object.class, null, true);
                } catch (RuntimeException e) {
                    error.enter();
                    RuntimeException ee = unboxEngineException(receiver, e);
                    if (ee != null) {
                        throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                    }
                    throw e;
                }
                try {
                    GuestToHostCalls.setMapEntryValue(receiver, hostValue);
                } catch (Throwable t) {
                    error.enter();
                    throw receiver.context.hostToGuestException(t);
                }
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isList.execute(receiver)", "!isArray.execute(receiver)", "!isMapEntry.execute(receiver)"}, limit = "1")
        static void doNotArrayOrList(HostObject receiver, long index, Object value,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    static class IsArrayElementRemovable {

        @Specialization(guards = "isList.execute(receiver)", limit = "1")
        static boolean doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("error") @Cached BranchProfile error) {
            try {
                return index >= 0 && index < GuestToHostCalls.getListSize(receiver);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = "!isList.execute(receiver)", limit = "1")
        static boolean doOther(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList) {
            return false;
        }

    }

    @ExportMessage
    static class RemoveArrayElement {
        @Specialization(guards = "isList.execute(receiver)", limit = "1")
        static void doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                GuestToHostCalls.removeListElement(receiver, index);
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = "!isList.execute(receiver)", limit = "1")
        static void doOther(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean hasArrayElements(@Shared("isList") @Cached IsListNode isList,
                    @Shared("isArray") @Cached IsArrayNode isArray,
                    @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) {
        return isList.execute(this) || isArray.execute(this) || isMapEntry.execute(this);
    }

    @ExportMessage
    abstract static class ReadArrayElement {

        @Specialization(guards = {"isArray.execute(receiver)"}, limit = "1")
        protected static Object doArray(HostObject receiver, long index,
                        @Cached ArrayGet arrayGet,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            Object obj = receiver.obj;
            Object val = null;
            try {
                val = arrayGet.execute(obj, (int) index);
            } catch (ArrayIndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return toGuest.execute(receiver.context, val);
        }

        @TruffleBoundary
        @Specialization(guards = {"isList.execute(receiver)"}, limit = "1")
        protected static Object doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            Object hostValue;
            try {
                hostValue = GuestToHostCalls.readListElement(receiver, index);
            } catch (IndexOutOfBoundsException e) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(receiver.context, hostValue);
        }

        @Specialization(guards = "isMapEntry.execute(receiver)", limit = "1")
        protected static Object doMapEntry(HostObject receiver, long index,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            Object hostResult;
            if (index == 0L) {
                try {
                    hostResult = GuestToHostCalls.getMapEntryKey(receiver);
                } catch (Throwable t) {
                    error.enter();
                    throw receiver.context.hostToGuestException(t);
                }
            } else if (index == 1L) {
                try {
                    hostResult = GuestToHostCalls.getMapEntryValue(receiver);
                } catch (Throwable t) {
                    error.enter();
                    throw receiver.context.hostToGuestException(t);
                }
            } else {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return toGuest.execute(receiver.context, hostResult);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isArray.execute(receiver)", "!isList.execute(receiver)", "!isMapEntry.execute(receiver)"}, limit = "1")
        protected static Object doNotArrayOrList(HostObject receiver, long index,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    abstract static class GetArraySize {

        @Specialization(guards = {"isArray.execute(receiver)"}, limit = "1")
        protected static long doArray(HostObject receiver,
                        @Shared("isArray") @Cached IsArrayNode isArray) {
            return Array.getLength(receiver.obj);
        }

        @Specialization(guards = {"isList.execute(receiver)"}, limit = "1")
        protected static long doList(HostObject receiver,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("error") @Cached BranchProfile error) {
            try {
                return GuestToHostCalls.getListSize(receiver);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = "isMapEntry.execute(receiver)", limit = "1")
        protected static long doMapEntry(HostObject receiver,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) {
            return 2;
        }

        @Specialization(guards = {"!isArray.execute(receiver)", "!isList.execute(receiver)", "!isMapEntry.execute(receiver)"}, limit = "1")
        protected static long doNotArrayOrList(HostObject receiver,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("isMapEntry") @Cached IsMapEntryNode isMapEntry) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    // region Buffer Messages

    @ExportMessage
    boolean hasBufferElements(@Shared("isBuffer") @Cached IsBufferNode isBuffer) {
        return isBuffer.execute(this);
    }

    @ExportMessage
    boolean isBufferWritable(@Shared("isBuffer") @Cached IsBufferNode isBuffer, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isBuffer.execute(this)) {
            final ByteBuffer buffer = (ByteBuffer) obj;
            return isPEFriendlyBuffer(buffer) ? !buffer.isReadOnly() : isBufferWritableBoundary(buffer);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    private static boolean isBufferWritableBoundary(ByteBuffer buffer) {
        return !buffer.isReadOnly();
    }

    @ExportMessage
    long getBufferSize(@Shared("isBuffer") @Cached IsBufferNode isBuffer, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isBuffer.execute(this)) {
            final ByteBuffer buffer = (ByteBuffer) obj;
            return isPEFriendlyBuffer(buffer) ? buffer.limit() : getBufferSizeBoundary(buffer);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    private static long getBufferSizeBoundary(ByteBuffer buffer) {
        return buffer.limit();
    }

    private static boolean isPEFriendlyBuffer(ByteBuffer buffer) {
        final Class<? extends ByteBuffer> clazz = buffer.getClass();
        final boolean result = CompilerDirectives.isPartialEvaluationConstant(clazz) &&
                        (clazz == HEAP_BYTE_BUFFER_CLASS || clazz == HEAP_BYTE_BUFFER_R_CLASS || clazz == DIRECT_BYTE_BUFFER_CLASS || clazz == DIRECT_BYTE_BUFFER_R_CLASS);
        assert result : "Unexpected Buffer subclass";
        return result;
    }

    @ExportMessage
    public byte readBufferByte(long index,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Byte.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            return isPEFriendlyBuffer(buffer) ? buffer.get((int) index) : getBufferByteBoundary(buffer, (int) index);
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Byte.BYTES);
        }
    }

    @TruffleBoundary
    private static byte getBufferByteBoundary(ByteBuffer buffer, int index) {
        return buffer.get(index);
    }

    @ExportMessage
    public void writeBufferByte(long index, byte value,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Byte.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            if (isPEFriendlyBuffer(buffer)) {
                buffer.put((int) index, value);
            } else {
                putBufferByteBoundary(buffer, (int) index, value);
            }
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Byte.BYTES);
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static void putBufferByteBoundary(ByteBuffer buffer, int index, byte value) {
        buffer.put(index, value);
    }

    @ExportMessage
    public short readBufferShort(ByteOrder order, long index,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Short.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            final short result = isPEFriendlyBuffer(buffer) ? buffer.getShort((int) index) : getBufferShortBoundary(buffer, (int) index);
            buffer.order(originalOrder);
            return result;
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Short.BYTES);
        }
    }

    @TruffleBoundary
    private static short getBufferShortBoundary(ByteBuffer buffer, int index) {
        return buffer.getShort(index);
    }

    @ExportMessage
    public void writeBufferShort(ByteOrder order, long index, short value,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Short.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            if (isPEFriendlyBuffer(buffer)) {
                buffer.putShort((int) index, value);
            } else {
                putBufferShortBoundary(buffer, (int) index, value);
            }
            buffer.order(originalOrder);
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Short.BYTES);
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static void putBufferShortBoundary(ByteBuffer buffer, int index, short value) {
        buffer.putShort(index, value);
    }

    @ExportMessage
    public int readBufferInt(ByteOrder order, long index,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Integer.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            final int result = isPEFriendlyBuffer(buffer) ? buffer.getInt((int) index) : getBufferIntBoundary(buffer, (int) index);
            buffer.order(originalOrder);
            return result;
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Integer.BYTES);
        }
    }

    @TruffleBoundary
    private static int getBufferIntBoundary(ByteBuffer buffer, int index) {
        return buffer.getInt(index);
    }

    @ExportMessage
    public void writeBufferInt(ByteOrder order, long index, int value,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Integer.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            if (isPEFriendlyBuffer(buffer)) {
                buffer.putInt((int) index, value);
            } else {
                putBufferIntBoundary(buffer, (int) index, value);
            }
            buffer.order(originalOrder);
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Integer.BYTES);
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static void putBufferIntBoundary(ByteBuffer buffer, int index, int value) {
        buffer.putInt(index, value);
    }

    @ExportMessage
    public long readBufferLong(ByteOrder order, long index,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Long.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            final long result = isPEFriendlyBuffer(buffer) ? buffer.getLong((int) index) : getBufferLongBoundary(buffer, (int) index);
            buffer.order(originalOrder);
            return result;
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Long.BYTES);
        }
    }

    @TruffleBoundary
    private static long getBufferLongBoundary(ByteBuffer buffer, int index) {
        return buffer.getLong(index);
    }

    @ExportMessage
    public void writeBufferLong(ByteOrder order, long index, long value,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Long.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            if (isPEFriendlyBuffer(buffer)) {
                buffer.putLong((int) index, value);
            } else {
                putBufferLongBoundary(buffer, (int) index, value);
            }
            buffer.order(originalOrder);
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Long.BYTES);
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static void putBufferLongBoundary(ByteBuffer buffer, int index, long value) {
        buffer.putLong(index, value);
    }

    @ExportMessage
    public float readBufferFloat(ByteOrder order, long index,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Float.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            final float result = isPEFriendlyBuffer(buffer) ? buffer.getFloat((int) index) : getBufferFloatBoundary(buffer, (int) index);
            buffer.order(originalOrder);
            return result;
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Float.BYTES);
        }
    }

    @TruffleBoundary
    private static float getBufferFloatBoundary(ByteBuffer buffer, int index) {
        return buffer.getFloat(index);
    }

    @ExportMessage
    public void writeBufferFloat(ByteOrder order, long index, float value,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Float.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            if (isPEFriendlyBuffer(buffer)) {
                buffer.putFloat((int) index, value);
            } else {
                putBufferFloatBoundary(buffer, (int) index, value);
            }
            buffer.order(originalOrder);
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Float.BYTES);
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static void putBufferFloatBoundary(ByteBuffer buffer, int index, float value) {
        buffer.putFloat(index, value);
    }

    @ExportMessage
    public double readBufferDouble(ByteOrder order, long index,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Double.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            final double result = isPEFriendlyBuffer(buffer) ? buffer.getDouble((int) index) : getBufferDoubleBoundary(buffer, (int) index);
            buffer.order(originalOrder);
            return result;
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Double.BYTES);
        }
    }

    @TruffleBoundary
    private static double getBufferDoubleBoundary(ByteBuffer buffer, int index) {
        return buffer.getDouble(index);
    }

    @ExportMessage
    public void writeBufferDouble(ByteOrder order, long index, double value,
                    @Shared("isBuffer") @Cached IsBufferNode isBuffer,
                    @Shared("error") @Cached BranchProfile error,
                    @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (!isBuffer.execute(this)) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
        if (index < 0 || Integer.MAX_VALUE < index) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Double.BYTES);
        }
        try {
            final ByteBuffer buffer = (ByteBuffer) classProfile.profile(obj);
            final ByteOrder originalOrder = buffer.order();
            buffer.order(order);
            if (isPEFriendlyBuffer(buffer)) {
                buffer.putDouble((int) index, value);
            } else {
                putBufferDoubleBoundary(buffer, (int) index, value);
            }
            buffer.order(originalOrder);
        } catch (IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(index, Double.BYTES);
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static void putBufferDoubleBoundary(ByteBuffer buffer, int index, double value) {
        buffer.putDouble(index, value);
    }

    // endregion

    @ExportMessage
    boolean isNull() {
        return obj == null;
    }

    @ExportMessage
    static class IsInstantiable {

        @Specialization(guards = "!receiver.isClass()")
        @SuppressWarnings("unused")
        static boolean doUnsupported(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.isArrayClass()")
        static boolean doArrayCached(@SuppressWarnings("unused") HostObject receiver) {
            return true;
        }

        @Specialization(guards = "receiver.isDefaultClass()")
        static boolean doObjectCached(HostObject receiver,
                        @Shared("lookupConstructor") @Cached LookupConstructorNode lookupConstructor) {
            return lookupConstructor.execute(receiver, receiver.asClass()) != null;
        }
    }

    @ExportMessage
    boolean isExecutable(@Shared("lookupFunctionalMethod") @Cached LookupFunctionalMethodNode lookupMethod) {
        return !isNull() && !isClass() && lookupMethod.execute(this, getLookupClass()) != null;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Shared("hostExecute") @Cached HostExecuteNode doExecute,
                    @Shared("lookupFunctionalMethod") @Cached LookupFunctionalMethodNode lookupMethod,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        if (!isNull() && !isClass()) {
            HostMethodDesc method = lookupMethod.execute(this, getLookupClass());
            if (method != null) {
                return doExecute.execute(method, obj, args, context);
            }
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static class Instantiate {

        @Specialization(guards = "!receiver.isClass()")
        @SuppressWarnings("unused")
        static Object doUnsupported(HostObject receiver, Object[] args) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "receiver.isArrayClass()")
        static Object doArrayCached(HostObject receiver, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary indexes,
                        @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            if (args.length != 1) {
                error.enter();
                throw ArityException.create(1, 1, args.length);
            }
            Object arg0 = args[0];
            int length;
            if (indexes.fitsInInt(arg0)) {
                length = indexes.asInt(arg0);
            } else {
                error.enter();
                throw UnsupportedTypeException.create(args);
            }
            Object array = Array.newInstance(receiver.asClass().getComponentType(), length);
            return HostObject.forObject(array, receiver.context);
        }

        @Specialization(guards = "receiver.isDefaultClass()")
        static Object doObjectCached(HostObject receiver, Object[] arguments,
                        @Shared("lookupConstructor") @Cached LookupConstructorNode lookupConstructor,
                        @Shared("hostExecute") @Cached HostExecuteNode executeMethod,
                        @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            assert !receiver.isArrayClass();
            HostMethodDesc constructor = lookupConstructor.execute(receiver, receiver.asClass());
            if (constructor != null) {
                return executeMethod.execute(constructor, null, arguments, receiver.context);
            }
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isNumber(@Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) {
        if (isNull()) {
            return false;
        }

        Class<?> c = classProfile.profile(obj).getClass();
        return c == Byte.class || c == Short.class || c == Integer.class || c == Long.class || c == Float.class || c == Double.class;
    }

    private static boolean isJavaPrimitiveNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double;
    }

    @ExportMessage
    boolean fitsInByte(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (thisLibrary.isNumber(this)) {
            return numbers.fitsInByte(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInShort(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (thisLibrary.isNumber(this)) {
            return numbers.fitsInShort(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInInt(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (thisLibrary.isNumber(this)) {
            return numbers.fitsInInt(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInLong(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (thisLibrary.isNumber(this)) {
            return numbers.fitsInLong(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInFloat(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (thisLibrary.isNumber(this)) {
            return numbers.fitsInFloat(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInDouble(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (thisLibrary.isNumber(this)) {
            return numbers.fitsInDouble(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    byte asByte(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isNumber(this)) {
            return numbers.asByte(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    short asShort(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isNumber(this)) {
            return numbers.asShort(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    int asInt(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isNumber(this)) {
            return numbers.asInt(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    long asLong(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isNumber(this)) {
            return numbers.asLong(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    float asFloat(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isNumber(this)) {
            return numbers.asFloat(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    double asDouble(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isNumber(this)) {
            return numbers.asDouble(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isString(@Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) {
        if (isNull()) {
            return false;
        }
        Class<?> c = classProfile.profile(obj).getClass();
        return c == String.class || c == Character.class;
    }

    @ExportMessage
    String asString(@CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary strings,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isString(this)) {
            return strings.asString(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isBoolean() {
        if (isNull()) {
            return false;
        }
        return obj.getClass() == Boolean.class;
    }

    @ExportMessage
    boolean asBoolean(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isBoolean()) {
            return (boolean) obj;
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isDate() {
        return obj instanceof LocalDate || obj instanceof LocalDateTime || obj instanceof Instant || obj instanceof ZonedDateTime || obj instanceof java.sql.Date || isInstantDate(obj);
    }

    @ExportMessage
    @TruffleBoundary
    LocalDate asDate() throws UnsupportedMessageException {
        if (obj instanceof LocalDate) {
            return ((LocalDate) obj);
        } else if (obj instanceof LocalDateTime) {
            return ((LocalDateTime) obj).toLocalDate();
        } else if (obj instanceof Instant) {
            return ((Instant) obj).atZone(UTC).toLocalDate();
        } else if (obj instanceof ZonedDateTime) {
            return ((ZonedDateTime) obj).toLocalDate();
        } else if (obj instanceof java.sql.Date) {
            return ((java.sql.Date) obj).toLocalDate();
        } else if (isInstantDate(obj)) {
            return ((Date) obj).toInstant().atZone(UTC).toLocalDate();
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isTime() {
        return obj instanceof LocalTime || obj instanceof LocalDateTime || obj instanceof Instant || obj instanceof ZonedDateTime || obj instanceof java.sql.Time || isInstantDate(obj);
    }

    @ExportMessage
    @TruffleBoundary
    LocalTime asTime() throws UnsupportedMessageException {
        if (obj instanceof LocalTime) {
            return ((LocalTime) obj);
        } else if (obj instanceof LocalDateTime) {
            return ((LocalDateTime) obj).toLocalTime();
        } else if (obj instanceof ZonedDateTime) {
            return ((ZonedDateTime) obj).toLocalTime();
        } else if (obj instanceof Instant) {
            return ((Instant) obj).atZone(UTC).toLocalTime();
        } else if (obj instanceof java.sql.Time) {
            return ((java.sql.Time) obj).toLocalTime();
        } else if (isInstantDate(obj)) {
            return ((Date) obj).toInstant().atZone(UTC).toLocalTime();
        }
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code> if this date object can be reliably converted to an instant.
     * Weirdly, despite the contract of the base class the two subclasses {@link Time} and
     * {@link java.sql.Date} are not supported to be convertable to an instant.
     */
    private static boolean isInstantDate(Object v) {
        return v instanceof Date && !(v instanceof Time) && !(v instanceof java.sql.Date);
    }

    @ExportMessage
    boolean isTimeZone() {
        return obj instanceof ZoneId || obj instanceof Instant || obj instanceof ZonedDateTime || isInstantDate(obj);
    }

    @ExportMessage
    ZoneId asTimeZone() throws UnsupportedMessageException {
        if (obj instanceof ZoneId) {
            return (ZoneId) obj;
        } else if (obj instanceof ZonedDateTime) {
            return ((ZonedDateTime) obj).getZone();
        } else if (obj instanceof Instant) {
            return UTC;
        } else if (isInstantDate(obj)) {
            return UTC;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    Instant asInstant() throws UnsupportedMessageException {
        if (obj instanceof ZonedDateTime) {
            return ((ZonedDateTime) obj).toInstant();
        } else if (obj instanceof Instant) {
            return (Instant) obj;
        } else if (isInstantDate(obj)) {
            return ((Date) obj).toInstant();
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isDuration() {
        return obj instanceof Duration;
    }

    @ExportMessage
    Duration asDuration() throws UnsupportedMessageException {
        if (isDuration()) {
            return (Duration) obj;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isException() {
        return obj instanceof Throwable;
    }

    @ExportMessage
    ExceptionType getExceptionType(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isException()) {
            return obj instanceof InterruptedException ? ExceptionType.INTERRUPT : ExceptionType.RUNTIME_ERROR;
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isExceptionIncompleteSource(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isException()) {
            return false;
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    int getExceptionExitStatus(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasExceptionMessage() {
        return isException() && ((Throwable) obj).getMessage() != null;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExceptionMessage(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        String message = isException() ? ((Throwable) obj).getMessage() : null;
        if (message != null) {
            return message;
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasExceptionCause() {
        return isException() && ((Throwable) obj).getCause() instanceof AbstractTruffleException;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExceptionCause() throws UnsupportedMessageException {
        if (isException()) {
            Throwable cause = ((Throwable) obj).getCause();
            if (cause instanceof AbstractTruffleException) {
                return cause;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasExceptionStackTrace() {
        return isException() && TruffleStackTrace.fillIn((Throwable) obj) != null;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExceptionStackTrace() throws UnsupportedMessageException {
        if (isException()) {
            return HostAccessor.EXCEPTION.getExceptionStackTrace(obj);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    RuntimeException throwException(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isException()) {
            HostException ex = (HostException) extraInfo;
            if (ex == null) {
                ex = new HostException((Throwable) obj, context);
            }
            throw ex;
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return HostLanguage.class;
    }

    @ExportMessage
    String toDisplayString(boolean allowSideEffects) {
        return toStringImpl(context, this.obj, 0, allowSideEffects);
    }

    @TruffleBoundary
    private static String toStringImpl(HostContext context, Object javaObject, int level, boolean allowSideEffects) {
        try {
            if (javaObject == null) {
                return "null";
            } else if (javaObject.getClass().isArray()) {
                return arrayToString(context, javaObject, level, allowSideEffects);
            } else if (javaObject instanceof Class) {
                return getTypeNameSafe((Class<?>) javaObject);
            } else {
                if (allowSideEffects && context != null) {
                    Object hostObject = HostObject.forObject(javaObject, context);
                    try {
                        InteropLibrary thisLib = InteropLibrary.getUncached(hostObject);
                        if (thisLib.isBoolean(hostObject)) {
                            return Boolean.toString(thisLib.asBoolean(hostObject));
                        } else if (thisLib.isString(hostObject)) {
                            return thisLib.asString(hostObject);
                        } else if (thisLib.isNumber(hostObject)) {
                            assert isJavaPrimitiveNumber(javaObject) : javaObject;
                            return javaObject.toString();
                        } else if (thisLib.isMemberInvocable(hostObject, "toString")) {
                            Object result = thisLib.invokeMember(hostObject, "toString");
                            return InteropLibrary.getUncached().asString(result);
                        }
                    } catch (InteropException e) {
                        // ignore exception and fall back to the !allowSideEffects version
                    }
                }
                return getTypeNameSafe(javaObject.getClass());
            }
        } catch (Throwable t) {
            throw context.hostToGuestException(t);
        }
    }

    /**
     * Safe version of {@link Class#getTypeName()} that strips any hidden class suffix from the
     * class name.
     */
    @TruffleBoundary
    private static String getTypeNameSafe(Class<?> type) {
        String typeName = type.getTypeName();
        int slash = typeName.indexOf('/');
        if (slash != -1) {
            return typeName.substring(0, slash);
        }
        return typeName;
    }

    private static String arrayToString(HostContext context, Object array, int level, boolean allowSideEffects) {
        CompilerAsserts.neverPartOfCompilation();
        if (array == null) {
            return "null";
        }
        if (level > 0) {
            // avoid recursions all together
            return "[...]";
        }
        int iMax = Array.getLength(array) - 1;
        if (iMax == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            Object arrayValue = Array.get(array, i);
            b.append(toStringImpl(context, arrayValue, level + 1, allowSideEffects));
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    @ExportMessage
    boolean hasIterator(@Shared("isIterable") @Cached IsIterableNode isIterable,
                    @Shared("isArray") @Cached IsArrayNode isArray) {
        return isIterable.execute(this) || isArray.execute(this);
    }

    @ExportMessage
    abstract static class GetIterator {

        @Specialization(guards = {"isArray.execute(receiver)"}, limit = "1")
        protected static Object doArray(HostObject receiver,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest) {
            return toGuest.execute(receiver.context, arrayIteratorImpl(receiver));
        }

        @TruffleBoundary
        private static Object arrayIteratorImpl(Object receiver) {
            return HostAccessor.INTEROP.createDefaultIterator(receiver);
        }

        @Specialization(guards = {"isIterable.execute(receiver)"}, limit = "1")
        protected static Object doIterable(HostObject receiver,
                        @Shared("isIterable") @Cached IsIterableNode isIterable,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) {
            Object hostValue;
            try {
                hostValue = GuestToHostCalls.getIterator(receiver);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(receiver.context, hostValue);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isArray.execute(receiver)", "!isIterable.execute(receiver)"}, limit = "1")
        protected static Object doNotArrayOrIterable(HostObject receiver,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("isIterable") @Cached IsIterableNode isIterable) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isIterator(@Shared("isIterator") @Cached IsIteratorNode isIterator) {
        return isIterator.execute(this);
    }

    @ExportMessage
    abstract static class HasIteratorNextElement {

        @Specialization(guards = {"isIterator.execute(receiver)"}, limit = "1")
        protected static boolean doIterator(HostObject receiver,
                        @Shared("isIterator") @Cached IsIteratorNode isIterator,
                        @Shared("error") @Cached BranchProfile error) {
            try {
                return GuestToHostCalls.hasIteratorNext(receiver);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isIterator.execute(receiver)"}, limit = "1")
        protected static boolean doNotIterator(HostObject receiver,
                        @Shared("isIterator") @Cached IsIteratorNode isIterator) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class GetIteratorNextElement {

        @Specialization(guards = {"isIterator.execute(receiver)"}, limit = "1")
        protected static Object doIterator(HostObject receiver,
                        @Shared("isIterator") @Cached IsIteratorNode isIterator,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error,
                        @Exclusive @Cached BranchProfile stopIteration) throws StopIterationException {
            Object next;
            try {
                next = GuestToHostCalls.getIteratorNext(receiver);
            } catch (NoSuchElementException e) {
                stopIteration.enter();
                throw StopIterationException.create();
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(receiver.context, next);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isIterator.execute(receiver)"}, limit = "1")
        protected static Object doNotIterator(HostObject receiver,
                        @Shared("isIterator") @Cached IsIteratorNode isIterator) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean hasHashEntries(@Shared("isMap") @Cached IsMapNode isMap) {
        return isMap.execute(this);
    }

    @ExportMessage
    abstract static class GetHashSize {

        @Specialization(guards = "isMap.execute(receiver)", limit = "1")
        protected static long doMap(HostObject receiver,
                        @Shared("isMap") @Cached IsMapNode isMap,
                        @Shared("error") @Cached BranchProfile error) {
            try {
                return GuestToHostCalls.getMapSize(receiver);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isMap.execute(receiver)", limit = "1")
        protected static long doNotMap(HostObject receiver, @Shared("isMap") @Cached IsMapNode isMap) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isHashEntryReadable")
    @ExportMessage(name = "isHashEntryModifiable")
    @ExportMessage(name = "isHashEntryRemovable")
    boolean isHashEntryReadable(Object key,
                    @Shared("isMap") @Cached IsMapNode isMap,
                    @Shared("containsKey") @Cached ContainsKeyNode containsKey) {
        return isMap.execute(this) && containsKey.execute(this, key);
    }

    @ExportMessage
    abstract static class ReadHashValue {

        private static final Object UNDEFINED = new Object();

        @SuppressWarnings("unchecked")
        @Specialization(guards = "isMap.execute(receiver)", limit = "1")
        protected static Object doMap(HostObject receiver, Object key,
                        @Shared("isMap") @Cached IsMapNode isMap,
                        @Shared("toHost") @Cached HostToTypeNode toHost,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) throws UnknownKeyException {
            Object hostKey;
            try {
                hostKey = toHost.execute(receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnknownKeyException.create(key);
                }
                throw e;
            }
            Object hostResult;
            try {
                hostResult = GuestToHostCalls.getMapValue(receiver, hostKey, UNDEFINED);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
            if (hostResult == UNDEFINED) {
                error.enter();
                throw UnknownKeyException.create(key);
            }
            return toGuest.execute(receiver.context, hostResult);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isMap.execute(receiver)", limit = "1")
        protected static Object doNotMap(HostObject receiver, Object key, @Shared("isMap") @Cached IsMapNode isMap) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isHashEntryInsertable(Object key,
                    @Shared("isMap") @Cached IsMapNode isMap,
                    @Shared("containsKey") @Cached ContainsKeyNode containsKey) {
        return isMap.execute(this) && !containsKey.execute(this, key);
    }

    @ExportMessage
    abstract static class WriteHashEntry {

        @SuppressWarnings("unchecked")
        @Specialization(guards = "isMap.execute(receiver)", limit = "1")
        protected static void doMap(HostObject receiver, Object key, Object value,
                        @Shared("isMap") @Cached IsMapNode isMap,
                        @Shared("toHost") @Cached HostToTypeNode toHost,
                        @Shared("error") @Cached BranchProfile error) throws UnsupportedTypeException {

            Object hostKey;
            Object hostValue;
            try {
                hostKey = toHost.execute(receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{key}, getMessage(ee));
                }
                throw e;
            }

            try {
                hostValue = toHost.execute(receiver.context, value, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                }
                throw e;
            }
            try {
                GuestToHostCalls.putMapValue(receiver, hostKey, hostValue);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isMap.execute(receiver)", limit = "1")
        protected static void doNotMap(HostObject receiver, Object key, Object value, @Shared("isMap") @Cached IsMapNode isMap) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class RemoveHashEntry {

        @SuppressWarnings("unchecked")
        @Specialization(guards = "isMap.execute(receiver)", limit = "1")
        protected static void doMap(HostObject receiver, Object key,
                        @Shared("isMap") @Cached IsMapNode isMap,
                        @Shared("toHost") @Cached HostToTypeNode toHost,
                        @Shared("error") @Cached BranchProfile error) throws UnknownKeyException {
            Object hostKey;
            try {
                hostKey = toHost.execute(receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnknownKeyException.create(key);
                }
                throw e;
            }
            boolean removed;
            try {
                removed = GuestToHostCalls.removeMapValue(receiver, hostKey);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
            if (!removed) {
                error.enter();
                throw UnknownKeyException.create(key);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isMap.execute(receiver)", limit = "1")
        protected static void doNotMap(HostObject receiver, Object key, @Shared("isMap") @Cached IsMapNode isMap) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class GetHashEntriesIterator {

        @SuppressWarnings("unchecked")
        @Specialization(guards = "isMap.execute(receiver)", limit = "1")
        protected static Object doMap(HostObject receiver,
                        @Shared("isMap") @Cached IsMapNode isMap,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) {
            Object hostValue;
            try {
                hostValue = GuestToHostCalls.getEntriesIterator(receiver);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(receiver.context, hostValue);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isMap.execute(receiver)", limit = "1")
        protected static Object doNotMap(HostObject receiver, @Shared("isMap") @Cached IsMapNode isMap) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMetaObject() {
        return !isNull();
    }

    @ExportMessage
    Object getMetaObject() throws UnsupportedMessageException {
        if (hasMetaObject()) {
            Object javaObject = this.obj;
            Class<?> javaType = javaObject.getClass();
            return HostObject.forClass(javaType, context);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMetaObject() {
        return isClass();
    }

    @ExportMessage
    @TruffleBoundary
    Object getMetaQualifiedName() throws UnsupportedMessageException {
        if (isClass()) {
            return asClass().getTypeName();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getMetaSimpleName() throws UnsupportedMessageException {
        if (isClass()) {
            return asClass().getSimpleName();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMetaInstance(Object other,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isClass()) {
            Class<?> c = asClass();
            HostLanguage language = context != null ? HostLanguage.get(library) : null;
            if (HostObject.isInstance(language, other)) {
                Object otherHostObj = HostObject.valueOf(language, other);
                if (otherHostObj == null) {
                    return false;
                } else {
                    return c.isInstance(otherHostObj);
                }
            } else if (HostProxy.isProxyGuestObject(language, other)) {
                Proxy otherHost = HostProxy.toProxyHostObject(language, other);
                return c.isInstance(otherHost);
            } else {
                boolean canConvert = HostToTypeNode.canConvert(other, c, c,
                                HostToTypeNode.allowsImplementation(context, c),
                                context, HostToTypeNode.LOWEST,
                                InteropLibrary.getFactory().getUncached(other),
                                HostTargetMappingNode.getUncached());
                return canConvert;
            }
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasMetaParents() {
        return isClass() && (asClass().getSuperclass() != null || asClass().getInterfaces().length > 0);
    }

    @ExportMessage
    @TruffleBoundary
    Object getMetaParents() throws UnsupportedMessageException {
        if (!hasMetaParents()) {
            throw UnsupportedMessageException.create();
        }
        Class<?> superClass = asClass().getSuperclass();
        Class<?>[] interfaces = asClass().getInterfaces();
        HostObject[] metaObjects = new HostObject[superClass == null ? interfaces.length : interfaces.length + 1];

        int i = 0;
        if (superClass != null) {
            metaObjects[i++] = HostObject.forClass(superClass, context);
        }
        for (int j = 0; j < interfaces.length; j++) {
            metaObjects[i++] = HostObject.forClass(interfaces[j], context);
        }
        return new TypesArray(metaObjects);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TypesArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final HostObject[] types;

        TypesArray(HostObject[] types) {
            this.types = types;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return types.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < types.length;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                error.enter();
                throw InvalidArrayIndexException.create(idx);
            }
            return types[(int) idx];
        }
    }

    boolean isStaticClass() {
        return extraInfo instanceof Class<?>;
    }

    Class<?> getObjectClass() {
        return obj == null ? null : obj.getClass();
    }

    Class<?> asStaticClass() {
        assert isStaticClass();
        return (Class<?>) obj;
    }

    Class<?> asClass() {
        assert isClass();
        return (Class<?>) obj;
    }

    /**
     * Gets the {@link Class} for member lookups.
     */
    Class<?> getLookupClass() {
        if (obj == null) {
            return null;
        } else if (isStaticClass()) {
            return asStaticClass();
        } else {
            return obj.getClass();
        }
    }

    HostClassCache getHostClassCache() {
        assert context != null : "host cache must not be used for null";
        return HostClassCache.forInstance(this);
    }

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doHostObject(HostObject receiver, HostObject other) {
            return TriState.valueOf(receiver.obj == other.obj && receiver.isStaticClass() == other.isStaticClass());
        }

        @Fallback
        static TriState doOther(HostObject receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    static int identityHashCode(HostObject receiver) {
        return System.identityHashCode(receiver.obj);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HostObject) {
            HostObject other = (HostObject) o;
            return this.obj == other.obj && this.extraInfo == other.extraInfo && this.context == other.context;
        }
        return false;
    }

    @Override
    public String toString() {
        if (obj == null) {
            return "null";
        }
        if (isClass()) {
            return "JavaClass[" + asClass().getTypeName() + "]";
        }
        return "JavaObject[" + obj + " (" + getObjectClass().getTypeName() + ")" + "]";
    }

    @GenerateUncached
    abstract static class ArraySet extends Node {

        protected abstract void execute(Object array, int index, Object value);

        @Specialization
        static void doBoolean(boolean[] array, int index, boolean value) {
            array[index] = value;
        }

        @Specialization
        static void doByte(byte[] array, int index, byte value) {
            array[index] = value;
        }

        @Specialization
        static void doShort(short[] array, int index, short value) {
            array[index] = value;
        }

        @Specialization
        static void doChar(char[] array, int index, char value) {
            array[index] = value;
        }

        @Specialization
        static void doInt(int[] array, int index, int value) {
            array[index] = value;
        }

        @Specialization
        static void doLong(long[] array, int index, long value) {
            array[index] = value;
        }

        @Specialization
        static void doFloat(float[] array, int index, float value) {
            array[index] = value;
        }

        @Specialization
        static void doDouble(double[] array, int index, double value) {
            array[index] = value;
        }

        @Specialization
        static void doObject(Object[] array, int index, Object value) {
            array[index] = value;
        }
    }

    @GenerateUncached
    abstract static class ArrayGet extends Node {

        protected abstract Object execute(Object array, int index);

        @Specialization
        static boolean doBoolean(boolean[] array, int index) {
            return array[index];
        }

        @Specialization
        static byte doByte(byte[] array, int index) {
            return array[index];
        }

        @Specialization
        static short doShort(short[] array, int index) {
            return array[index];
        }

        @Specialization
        static char doChar(char[] array, int index) {
            return array[index];
        }

        @Specialization
        static int doInt(int[] array, int index) {
            return array[index];
        }

        @Specialization
        static long doLong(long[] array, int index) {
            return array[index];
        }

        @Specialization
        static float doFloat(float[] array, int index) {
            return array[index];
        }

        @Specialization
        static double doDouble(double[] array, int index) {
            return array[index];
        }

        @Specialization
        static Object doObject(Object[] array, int index) {
            return array[index];
        }
    }

    @GenerateUncached
    abstract static class LookupConstructorNode extends Node {
        static final int LIMIT = 3;

        LookupConstructorNode() {
        }

        public abstract HostMethodDesc execute(HostObject receiver, Class<?> clazz);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz"}, limit = "LIMIT")
        HostMethodDesc doCached(HostObject receiver, Class<?> clazz,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("doUncached(receiver, clazz)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(receiver, clazz);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        HostMethodDesc doUncached(HostObject receiver, Class<?> clazz) {
            return HostClassDesc.forClass(receiver.context, clazz).lookupConstructor();
        }
    }

    @GenerateUncached
    abstract static class LookupFieldNode extends Node {
        static final int LIMIT = 3;

        LookupFieldNode() {
        }

        public abstract HostFieldDesc execute(HostObject receiver, Class<?> clazz, String name, boolean onlyStatic);

        @SuppressWarnings("unused")
        @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        HostFieldDesc doCached(HostObject receiver, Class<?> clazz, String name, boolean onlyStatic,
                        @Cached("onlyStatic") boolean cachedStatic,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, clazz, name, onlyStatic)") HostFieldDesc cachedField) {
            assert cachedField == doUncached(receiver, clazz, name, onlyStatic);
            return cachedField;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        HostFieldDesc doUncached(HostObject receiver, Class<?> clazz, String name, boolean onlyStatic) {
            return HostInteropReflect.findField(receiver.context, clazz, name, onlyStatic);
        }
    }

    @GenerateUncached
    abstract static class LookupFunctionalMethodNode extends Node {
        static final int LIMIT = 3;

        LookupFunctionalMethodNode() {
        }

        public abstract HostMethodDesc execute(HostObject object, Class<?> clazz);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz"}, limit = "LIMIT")
        HostMethodDesc doCached(HostObject object, Class<?> clazz,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("doUncached(object, clazz)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(object, clazz);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static HostMethodDesc doUncached(HostObject object, Class<?> clazz) {
            return HostClassDesc.forClass(object.context, clazz).getFunctionalMethod();
        }
    }

    @GenerateUncached
    abstract static class LookupInnerClassNode extends Node {
        static final int LIMIT = 3;

        LookupInnerClassNode() {
        }

        public abstract Class<?> execute(Class<?> outerclass, String name);

        @SuppressWarnings("unused")
        @Specialization(guards = {"clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        Class<?> doCached(Class<?> clazz, String name,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(clazz, name)") Class<?> cachedInnerClass) {
            assert cachedInnerClass == doUncached(clazz, name);
            return cachedInnerClass;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        Class<?> doUncached(Class<?> clazz, String name) {
            return HostInteropReflect.findInnerClass(clazz, name);
        }
    }

    @GenerateUncached
    abstract static class LookupMethodNode extends Node {
        static final int LIMIT = 3;

        LookupMethodNode() {
        }

        public abstract HostMethodDesc execute(HostObject receiver, Class<?> clazz, String name, boolean onlyStatic);

        @SuppressWarnings("unused")
        @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
        HostMethodDesc doCached(HostObject receiver, Class<?> clazz, String name, boolean onlyStatic,
                        @Cached("onlyStatic") boolean cachedStatic,
                        @Cached("clazz") Class<?> cachedClazz,
                        @Cached("name") String cachedName,
                        @Cached("doUncached(receiver, clazz, name, onlyStatic)") HostMethodDesc cachedMethod) {
            assert cachedMethod == doUncached(receiver, clazz, name, onlyStatic);
            return cachedMethod;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        HostMethodDesc doUncached(HostObject receiver, Class<?> clazz, String name, boolean onlyStatic) {
            return HostInteropReflect.findMethod(receiver.context, clazz, name, onlyStatic);
        }
    }

    @GenerateUncached
    abstract static class ReadFieldNode extends Node {
        static final int LIMIT = 3;

        ReadFieldNode() {
        }

        public abstract Object execute(HostFieldDesc field, HostObject object);

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        static Object doCached(HostFieldDesc field, HostObject object,
                        @Cached("field") HostFieldDesc cachedField,
                        @Cached ToGuestValueNode toGuest) {
            Object val = cachedField.get(object.obj);
            return toGuest.execute(object.context, val);
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Object doUncached(HostFieldDesc field, HostObject object,
                        @Cached ToGuestValueNode toGuest) {
            Object val = field.get(object.obj);
            return toGuest.execute(object.context, val);
        }
    }

    @GenerateUncached
    abstract static class WriteFieldNode extends Node {
        static final int LIMIT = 3;

        WriteFieldNode() {
        }

        public abstract void execute(HostFieldDesc field, HostObject object, Object value) throws UnsupportedTypeException, UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        static void doCached(HostFieldDesc field, HostObject object, Object rawValue,
                        @Cached("field") HostFieldDesc cachedField,
                        @Cached HostToTypeNode toHost,
                        @Cached BranchProfile error) throws UnsupportedTypeException, UnknownIdentifierException {
            if (field.isFinal()) {
                error.enter();
                throw UnknownIdentifierException.create(field.getName());
            }
            try {
                Object value = toHost.execute(object.context, rawValue, cachedField.getType(), cachedField.getGenericType(), true);
                cachedField.set(object.obj, value);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(object, e);
                if (ee != null) {
                    throw HostInteropErrors.unsupportedTypeException(rawValue, ee);
                }
                throw e;
            }
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static void doUncached(HostFieldDesc field, HostObject object, Object rawValue,
                        @Cached HostToTypeNode toHost) throws UnsupportedTypeException, UnknownIdentifierException {
            if (field.isFinal()) {
                throw UnknownIdentifierException.create(field.getName());
            }
            try {
                Object val = toHost.execute(object.context, rawValue, field.getType(), field.getGenericType(), true);
                field.set(object.obj, val);
            } catch (RuntimeException e) {
                RuntimeException ee = unboxEngineException(object, e);
                if (ee != null) {
                    throw HostInteropErrors.unsupportedTypeException(rawValue, ee);
                }
                throw e;
            }
        }
    }

    @GenerateUncached
    abstract static class IsListNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization(guards = "receiver.obj == null")
        public boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.obj != null")
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isListAccess()", allowUncached = true) boolean isListAccess) {
            assert receiver.getHostClassCache().isListAccess() == isListAccess;
            return isListAccess && receiver.obj instanceof List;
        }

    }

    @GenerateUncached
    abstract static class IsArrayNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization(guards = "receiver.obj == null")
        public boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.obj != null")
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isArrayAccess()", allowUncached = true) boolean isArrayAccess) {
            assert receiver.getHostClassCache().isArrayAccess() == isArrayAccess;
            return isArrayAccess && receiver.obj.getClass().isArray();
        }

    }

    @GenerateUncached
    abstract static class IsBufferNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization(guards = "receiver.obj == null")
        public boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.obj != null")
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isBufferAccess()", allowUncached = true) boolean isBufferAccess) {
            assert receiver.getHostClassCache().isBufferAccess() == isBufferAccess;
            return isBufferAccess && ByteBuffer.class.isAssignableFrom(receiver.obj.getClass());
        }

    }

    @GenerateUncached
    abstract static class IsIterableNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization(guards = "receiver.obj == null")
        public boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.obj != null")
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isIterableAccess()", allowUncached = true) boolean isIterableAccess) {
            assert receiver.getHostClassCache().isIterableAccess() == isIterableAccess;
            return isIterableAccess && receiver.obj instanceof Iterable;
        }
    }

    @GenerateUncached
    abstract static class IsIteratorNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization(guards = "receiver.obj == null")
        public boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.obj != null")
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isIteratorAccess()", allowUncached = true) boolean isIteratorAccess) {
            assert receiver.getHostClassCache().isIteratorAccess() == isIteratorAccess;
            return isIteratorAccess && receiver.obj instanceof Iterator;
        }
    }

    @GenerateUncached
    abstract static class IsMapNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization(guards = "receiver.obj == null")
        public boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.obj != null")
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isMapAccess()", allowUncached = true) boolean isMapAccess) {
            assert receiver.getHostClassCache().isMapAccess() == isMapAccess;
            return isMapAccess && receiver.obj instanceof Map;
        }
    }

    @GenerateUncached
    abstract static class ContainsKeyNode extends Node {

        public abstract boolean execute(HostObject receiver, Object key);

        @Specialization(guards = "isMap.execute(receiver)", limit = "1")
        protected static boolean doMap(HostObject receiver, Object key,
                        @Shared("isMap") @Cached IsMapNode isMap,
                        @Cached HostToTypeNode toHost,
                        @Cached BranchProfile error) {
            Object hostKey;
            try {
                hostKey = toHost.execute(receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter();
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    return false;
                }
                throw e;
            }
            try {
                return GuestToHostCalls.containsMapKey(receiver, hostKey);
            } catch (Throwable t) {
                error.enter();
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isMap.execute(receiver)", limit = "1")
        protected static boolean doNotMap(HostObject receiver, Object key, @Shared("isMap") @Cached IsMapNode isMap) {
            return false;
        }
    }

    @GenerateUncached
    abstract static class IsMapEntryNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization(guards = "receiver.obj == null")
        public boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "receiver.obj != null")
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isMapAccess()", allowUncached = true) boolean isMapAccess) {
            assert receiver.getHostClassCache().isMapAccess() == isMapAccess;
            return isMapAccess && receiver.obj instanceof Map.Entry;
        }
    }

    /**
     * Calls from a guest language to host. Whenever HostObject interop message does a host call
     * which can throw an exception the call must be done in the {@link GuestToHostCalls} to
     * correctly merge host an guest stack frames.
     *
     * @see PolyglotExceptionImpl.MergedHostGuestIterator#isGuestToHost(StackTraceElement,
     *      StackTraceElement[], int)
     */
    abstract static class GuestToHostCalls {

        private GuestToHostCalls() {
        }

        @TruffleBoundary(allowInlining = true)
        static int getListSize(HostObject hostObject) {
            return ((List<?>) hostObject.obj).size();
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        static void setListElement(HostObject receiver, long index, final Object hostValue) {
            List<Object> list = ((List<Object>) receiver.obj);
            if (index == list.size()) {
                list.add(hostValue);
            } else {
                list.set((int) index, hostValue);
            }
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        static Object removeListElement(HostObject receiver, long index) {
            return ((List<Object>) receiver.obj).remove((int) index);
        }

        @TruffleBoundary
        static Object readListElement(HostObject receiver, long index) {
            return ((List<?>) receiver.obj).get((int) index);
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        static Object setMapEntryValue(HostObject receiver, Object value) {
            return ((Map.Entry<Object, Object>) receiver.obj).setValue(value);
        }

        @TruffleBoundary
        static Object getMapEntryKey(HostObject receiver) {
            return ((Map.Entry<?, ?>) receiver.obj).getKey();
        }

        @TruffleBoundary
        static Object getMapEntryValue(HostObject receiver) {
            return ((Map.Entry<?, ?>) receiver.obj).getValue();
        }

        @TruffleBoundary
        static Object getIterator(HostObject receiver) {
            return ((Iterable<?>) receiver.obj).iterator();
        }

        @TruffleBoundary
        static boolean hasIteratorNext(HostObject receiver) {
            return ((Iterator<?>) receiver.obj).hasNext();
        }

        @TruffleBoundary
        static Object getIteratorNext(HostObject receiver) {
            return (((Iterator<?>) receiver.obj)).next();
        }

        @TruffleBoundary
        static int getMapSize(HostObject receiver) {
            return ((Map<?, ?>) receiver.obj).size();
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        static Object getMapValue(HostObject receiver, Object key, Object defaultValue) {
            return ((Map<Object, Object>) receiver.obj).getOrDefault(key, defaultValue);
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        static void putMapValue(HostObject receiver, Object key, Object value) {
            ((Map<Object, Object>) receiver.obj).put(key, value);
        }

        @TruffleBoundary
        static boolean removeMapValue(HostObject receiver, Object key) {
            Map<?, ?> map = (Map<?, ?>) receiver.obj;
            if (map.containsKey(key)) {
                map.remove(key);
                return true;
            } else {
                return false;
            }
        }

        @TruffleBoundary
        static Object getEntriesIterator(HostObject receiver) {
            return ((Map<?, ?>) receiver.obj).entrySet().iterator();
        }

        @TruffleBoundary
        static boolean containsMapKey(HostObject receiver, Object key) {
            return ((Map<?, ?>) receiver.obj).containsKey(key);
        }
    }
}
