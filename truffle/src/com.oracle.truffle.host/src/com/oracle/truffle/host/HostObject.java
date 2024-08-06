/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.math.BigDecimal;
import java.math.BigInteger;
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

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
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
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.host.HostContext.ToGuestValueNode;
import com.oracle.truffle.host.HostContextFactory.ToGuestValueNodeGen;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings({"unused"})
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
            return ((HostException) obj).withContext(context);
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
                        @Bind("$node") Node node,
                        @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                error.enter(node);
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
                    @Bind("$node") Node node,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Shared("readField") @Cached ReadFieldNode readField,
                    @Shared("lookupMethod") @Cached LookupMethodNode lookupMethod,
                    @Cached LookupInnerClassNode lookupInnerClass,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnknownIdentifierException {
        if (isNull()) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        boolean isStatic = isStaticClass();
        Class<?> lookupClass = getLookupClass();
        HostFieldDesc foundField = lookupField.execute(node, this, lookupClass, name, isStatic);
        if (foundField != null) {
            return readField.execute(node, foundField, this);
        }
        HostMethodDesc foundMethod = lookupMethod.execute(node, this, lookupClass, name, isStatic);
        if (foundMethod != null) {
            return new HostFunction(foundMethod, this.obj, this.context);
        }

        if (isStatic) {
            LookupInnerClassNode lookupInnerClassNode = lookupInnerClass;
            if (HostInteropReflect.STATIC_TO_CLASS.equals(name)) {
                return HostObject.forClass(lookupClass, context);
            }
            Class<?> innerclass = lookupInnerClassNode.execute(node, lookupClass, name);
            if (innerclass != null) {
                return HostObject.forStaticClass(innerclass, context);
            }
        } else if (isClass() && HostInteropReflect.CLASS_TO_STATIC.equals(name)) {
            return HostObject.forStaticClass(asClass(), context);
        } else if (HostInteropReflect.ADAPTER_SUPER_MEMBER.equals(name) && HostAdapterFactory.isAdapterInstance(this.obj)) {
            return HostAdapterFactory.getSuperAdapter(this);
        }
        error.enter(node);
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
                    @Bind("$node") Node node,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Cached WriteFieldNode writeField,
                    @Shared("error") @Cached InlinedBranchProfile error)
                    throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        if (isNull()) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
        HostFieldDesc f = lookupField.execute(node, this, getLookupClass(), member, isStaticClass());
        if (f == null) {
            error.enter(node);
            throw UnknownIdentifierException.create(member);
        }
        try {
            writeField.execute(node, f, this, value);
        } catch (ClassCastException | NullPointerException e) {
            // conversion failed by ToJavaNode
            error.enter(node);
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
                    @Bind("$node") Node node,
                    @Shared("lookupMethod") @Cached LookupMethodNode lookupMethod,
                    @Shared("hostExecute") @Cached HostExecuteNode executeMethod,
                    @Shared("lookupField") @Cached LookupFieldNode lookupField,
                    @Shared("readField") @Cached ReadFieldNode readField,
                    @CachedLibrary(limit = "5") InteropLibrary fieldValues,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedTypeException, ArityException, UnsupportedMessageException, UnknownIdentifierException {
        if (isNull()) {
            error.enter(node);
            throw UnsupportedMessageException.create();
        }

        boolean isStatic = isStaticClass();
        Class<?> lookupClass = getLookupClass();

        // (1) look for a method; if found, invoke it on obj.
        HostMethodDesc foundMethod = lookupMethod.execute(node, this, lookupClass, name, isStatic);
        if (foundMethod != null) {
            return executeMethod.execute(node, foundMethod, obj, args, context);
        }

        // (2) look for a field; if found, read its value and if that IsExecutable, Execute it.
        HostFieldDesc foundField = lookupField.execute(node, this, lookupClass, name, isStatic);
        if (foundField != null) {
            Object fieldValue = readField.execute(node, foundField, this);
            if (fieldValues.isExecutable(fieldValue)) {
                return fieldValues.execute(fieldValue, args);
            }
        }
        error.enter(node);
        throw UnknownIdentifierException.create(name);
    }

    @ExportMessage
    static class IsArrayElementReadable {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver, long index) {
            return false;
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isArray(hostClassCache)"})
        static boolean doArray(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            long size = Array.getLength(receiver.obj);
            return index >= 0 && index < size;
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isList(hostClassCache)"})
        static boolean doList(HostObject receiver, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            try {
                long size = GuestToHostCalls.getListSize(receiver);
                return index >= 0 && index < size;
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMapEntry(hostClassCache)"})
        static boolean doMapEntry(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return index >= 0 && index < 2;
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isList(hostClassCache)", "!receiver.isArray(hostClassCache)",
                        "!receiver.isMapEntry(hostClassCache)"})
        static boolean doNotArrayOrList(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return false;
        }
    }

    @ExportMessage
    static class IsArrayElementModifiable {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver, long index) {
            return false;
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isArray(hostClassCache)"})
        static boolean doArray(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            long size = Array.getLength(receiver.obj);
            return index >= 0 && index < size;
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isList(hostClassCache)"})
        static boolean doList(HostObject receiver, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            try {
                long size = GuestToHostCalls.getListSize(receiver);
                return index >= 0 && index < size;
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMapEntry(hostClassCache)"})
        static boolean doMapEntry(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return index == 1;
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isList(hostClassCache)", "!receiver.isArray(hostClassCache)",
                        "!receiver.isMapEntry(hostClassCache)"})
        static boolean doNotArrayOrList(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return false;
        }
    }

    @ExportMessage
    static class IsArrayElementInsertable {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver, long index) {
            return false;
        }

        @Specialization(guards = "!receiver.isNull()")
        static boolean doNonNull(HostObject receiver,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            try {
                return receiver.isList(hostClassCache) && GuestToHostCalls.getListSize(receiver) == index;
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }
    }

    @ExportMessage
    static class WriteArrayElement {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, long index, Object value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isArray(hostClassCache)"})
        static void doArray(HostObject receiver, long index, Object value,
                        @Bind("$node") Node node,
                        @Shared("toHost") @Cached(inline = true) HostToTypeNode toHostNode,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Cached ArraySet arraySet,
                        @Shared("error") @Cached InlinedBranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            Object obj = receiver.obj;
            Object javaValue;
            try {
                javaValue = toHostNode.execute(node, receiver.context, value, obj.getClass().getComponentType(), null, true);
            } catch (RuntimeException e) {
                error.enter(node);
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                }
                throw e;
            }
            try {
                arraySet.execute(node, obj, (int) index, javaValue);
            } catch (ArrayIndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isList(hostClassCache)"})
        static void doList(HostObject receiver, long index, Object value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toHost") @Cached(inline = true) HostToTypeNode toHostNode,
                        @Shared("error") @Cached InlinedBranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            Object javaValue;
            try {
                javaValue = toHostNode.execute(node, receiver.context, value, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter(node);
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                }
                throw e;
            }
            try {
                GuestToHostCalls.setListElement(receiver, index, javaValue);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMapEntry(hostClassCache)"})
        static void doMapEntry(HostObject receiver, long index, Object value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toHost") @Cached(inline = true) HostToTypeNode toHostNode,
                        @Shared("error") @Cached InlinedBranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index == 1) {
                Object hostValue;
                try {
                    hostValue = toHostNode.execute(node, receiver.context, value, Object.class, null, true);
                } catch (RuntimeException e) {
                    error.enter(node);
                    RuntimeException ee = unboxEngineException(receiver, e);
                    if (ee != null) {
                        throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                    }
                    throw e;
                }
                try {
                    GuestToHostCalls.setMapEntryValue(receiver, hostValue);
                } catch (Throwable t) {
                    error.enter(node);
                    throw receiver.context.hostToGuestException(t);
                }
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isList(hostClassCache)", "!receiver.isArray(hostClassCache)",
                        "!receiver.isMapEntry(hostClassCache)"})
        static void doNotArrayOrList(HostObject receiver, long index, Object value,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    static class IsArrayElementRemovable {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver, long index) {
            return false;
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isList(hostClassCache)"})
        static boolean doList(HostObject receiver, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            try {
                return index >= 0 && index < GuestToHostCalls.getListSize(receiver);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isList(hostClassCache)"})
        static boolean doOther(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return false;
        }

    }

    @ExportMessage
    static class RemoveArrayElement {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isList(hostClassCache)"})
        static void doList(HostObject receiver, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            try {
                GuestToHostCalls.removeListElement(receiver, index);
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isList(hostClassCache)"})
        static void doOther(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class HasArrayElements {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "!receiver.isNull()")
        static boolean doNotNull(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return receiver.isList(hostClassCache) || receiver.isArray(hostClassCache) || receiver.isMapEntry(hostClassCache);
        }
    }

    @ExportMessage
    abstract static class ReadArrayElement {

        @Specialization(guards = "receiver.isNull()")
        protected static Object doNull(HostObject receiver, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isArray(hostClassCache)"})
        protected static Object doArray(HostObject receiver, long index,
                        @Bind("$node") Node node,
                        @Cached ArrayGet arrayGet,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest,
                        @Shared("error") @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            Object obj = receiver.obj;
            Object val = null;
            try {
                val = arrayGet.execute(node, obj, (int) index);
            } catch (ArrayIndexOutOfBoundsException outOfBounds) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            return toGuest.execute(node, receiver.context, val);
        }

        @TruffleBoundary
        @Specialization(guards = {"!receiver.isNull()", "receiver.isList(hostClassCache)"})
        protected static Object doList(HostObject receiver, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest,
                        @Shared("error") @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            Object hostValue;
            try {
                hostValue = GuestToHostCalls.readListElement(receiver, index);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(node, receiver.context, hostValue);
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMapEntry(hostClassCache)"})
        protected static Object doMapEntry(HostObject receiver, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest,
                        @Shared("error") @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            Object hostResult;
            if (index == 0L) {
                try {
                    hostResult = GuestToHostCalls.getMapEntryKey(receiver);
                } catch (Throwable t) {
                    error.enter(node);
                    throw receiver.context.hostToGuestException(t);
                }
            } else if (index == 1L) {
                try {
                    hostResult = GuestToHostCalls.getMapEntryValue(receiver);
                } catch (Throwable t) {
                    error.enter(node);
                    throw receiver.context.hostToGuestException(t);
                }
            } else {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            return toGuest.execute(node, receiver.context, hostResult);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isArray(hostClassCache)", "!receiver.isList(hostClassCache)",
                        "!receiver.isMapEntry(hostClassCache)"})
        protected static Object doNotArrayOrList(HostObject receiver, long index,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    abstract static class GetArraySize {

        @Specialization(guards = "receiver.isNull()")
        protected static long doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isArray(hostClassCache)"})
        protected static long doArray(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return Array.getLength(receiver.obj);
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isList(hostClassCache)"})
        protected static long doList(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            try {
                return GuestToHostCalls.getListSize(receiver);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMapEntry(hostClassCache)"})
        protected static long doMapEntry(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return 2;
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isArray(hostClassCache)", "!receiver.isList(hostClassCache)",
                        "!receiver.isMapEntry(hostClassCache)"})
        protected static long doNotArrayOrList(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    // region Buffer Messages

    boolean isByteSequence() {
        return context.language.api.isByteSequence(obj);
    }

    @ExportMessage
    static class HasBufferElements {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static boolean doByteSequence(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return hostClassCache.isBufferAccess();
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static boolean doOther(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return receiver.isBuffer(hostClassCache);
        }
    }

    @ExportMessage
    static class IsBufferWritable {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static boolean doByteSequence(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return false;
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static boolean doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiver.isBuffer(hostClassCache)) {
                final ByteBuffer buffer = (ByteBuffer) receiver.obj;
                return isPEFriendlyBuffer(buffer) ? !buffer.isReadOnly() : isBufferWritableBoundary(buffer);
            }
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static boolean isBufferWritableBoundary(ByteBuffer buffer) {
        return !buffer.isReadOnly();
    }

    @ExportMessage
    static class GetBufferSize {

        @Specialization(guards = "receiver.isNull()")
        static long doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static long doByteSequence(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBufferAccess()) {
                return getByteSequenceLengthBoundary(receiver.context.language.api, receiver.obj);
            }
            error.enter(node);
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static long doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiver.isBuffer(hostClassCache)) {
                final ByteBuffer buffer = (ByteBuffer) receiver.obj;
                return isPEFriendlyBuffer(buffer) ? buffer.limit() : getBufferSizeBoundary(buffer);
            }
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private static long getByteSequenceLengthBoundary(APIAccess apiAccess, Object byteSequence) {
        return apiAccess.byteSequenceLength(byteSequence);
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
    static class ReadBufferByte {

        @Specialization(guards = "receiver.isNull()")
        static byte doNull(HostObject receiver, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static byte doByteSequence(HostObject receiver,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!hostClassCache.isBufferAccess()) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Byte.BYTES);
            }
            try {
                return getByteSequenceByteBoundary(receiver.context.language.api, receiver.obj, (int) index);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Byte.BYTES);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static byte doOther(HostObject receiver,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Byte.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                return isPEFriendlyBuffer(buffer) ? buffer.get((int) index) : getBufferByteBoundary(buffer, (int) index);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Byte.BYTES);
            }
        }
    }

    @TruffleBoundary
    private static byte getByteSequenceByteBoundary(APIAccess apiAccess, Object byteSequence, int index) {
        return apiAccess.byteSequenceByteAt(byteSequence, index);
    }

    @TruffleBoundary
    private static byte getBufferByteBoundary(ByteBuffer buffer, int index) {
        return buffer.get(index);
    }

    @ExportMessage
    static class WriteBufferByte {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, long index, byte value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "!receiver.isNull()")
        static void doNonNull(HostObject receiver, long index, byte value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Byte.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                if (isPEFriendlyBuffer(buffer)) {
                    buffer.put((int) index, value);
                } else {
                    putBufferByteBoundary(buffer, (int) index, value);
                }
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Byte.BYTES);
            } catch (ReadOnlyBufferException e) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    private static void putBufferByteBoundary(ByteBuffer buffer, int index, byte value) {
        buffer.put(index, value);
    }

    @ExportMessage
    static class ReadBufferShort {

        @Specialization(guards = "receiver.isNull()")
        static short doNull(HostObject receiver, ByteOrder order, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static short doByteSequence(HostObject receiver,
                        ByteOrder order,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!hostClassCache.isBufferAccess()) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Short.BYTES);
            }
            try {
                return getByteSequenceShortBoundary(receiver.context.language.api, receiver.obj, (int) index, order);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Short.BYTES);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static short doOther(HostObject receiver, ByteOrder order, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Short.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                final short result = isPEFriendlyBuffer(buffer) ? buffer.getShort((int) index) : getBufferShortBoundary(buffer, (int) index);
                buffer.order(originalOrder);
                return result;
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Short.BYTES);
            }
        }
    }

    @TruffleBoundary
    private static short getByteSequenceShortBoundary(APIAccess apiAccess, Object byteSequence, int index, ByteOrder order) {
        int b1 = apiAccess.byteSequenceByteAt(byteSequence, index) & 0xFF;
        int b2 = apiAccess.byteSequenceByteAt(byteSequence, index + 1) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) ((b1 << 8) | b2);
        } else {
            return (short) ((b2 << 8) | b1);
        }
    }

    @TruffleBoundary
    private static short getBufferShortBoundary(ByteBuffer buffer, int index) {
        return buffer.getShort(index);
    }

    @ExportMessage
    static class WriteBufferShort {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, ByteOrder order, long index, short value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "!receiver.isNull()")
        static void doNonNull(HostObject receiver, ByteOrder order, long index, short value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Short.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                if (isPEFriendlyBuffer(buffer)) {
                    buffer.putShort((int) index, value);
                } else {
                    putBufferShortBoundary(buffer, (int) index, value);
                }
                buffer.order(originalOrder);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Short.BYTES);
            } catch (ReadOnlyBufferException e) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    private static void putBufferShortBoundary(ByteBuffer buffer, int index, short value) {
        buffer.putShort(index, value);
    }

    @ExportMessage
    static class ReadBufferInt {

        @Specialization(guards = "receiver.isNull()")
        static int doNull(HostObject receiver, ByteOrder order, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static int doByteSequence(HostObject receiver,
                        ByteOrder order,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!hostClassCache.isBufferAccess()) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Integer.BYTES);
            }
            try {
                return getByteSequenceIntBoundary(receiver.context.language.api, receiver.obj, (int) index, order);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Integer.BYTES);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static int doOther(HostObject receiver, ByteOrder order, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Integer.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                final int result = isPEFriendlyBuffer(buffer) ? buffer.getInt((int) index) : getBufferIntBoundary(buffer, (int) index);
                buffer.order(originalOrder);
                return result;
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Integer.BYTES);
            }
        }
    }

    @TruffleBoundary
    private static int getBufferIntBoundary(ByteBuffer buffer, int index) {
        return buffer.getInt(index);
    }

    @TruffleBoundary
    private static int getByteSequenceIntBoundary(APIAccess apiAccess, Object byteSequence, int index, ByteOrder order) {
        int b1 = apiAccess.byteSequenceByteAt(byteSequence, index) & 0xFF;
        int b2 = apiAccess.byteSequenceByteAt(byteSequence, index + 1) & 0xFF;
        int b3 = apiAccess.byteSequenceByteAt(byteSequence, index + 2) & 0xFF;
        int b4 = apiAccess.byteSequenceByteAt(byteSequence, index + 3) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
        } else {
            return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
        }
    }

    @ExportMessage
    static class WriteBufferInt {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, ByteOrder order, long index, int value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "!receiver.isNull()")
        static void doNonNull(HostObject receiver, ByteOrder order, long index, int value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Integer.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                if (isPEFriendlyBuffer(buffer)) {
                    buffer.putInt((int) index, value);
                } else {
                    putBufferIntBoundary(buffer, (int) index, value);
                }
                buffer.order(originalOrder);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Integer.BYTES);
            } catch (ReadOnlyBufferException e) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    private static void putBufferIntBoundary(ByteBuffer buffer, int index, int value) {
        buffer.putInt(index, value);
    }

    @ExportMessage
    static class ReadBufferLong {

        @Specialization(guards = "receiver.isNull()")
        static long doNull(HostObject receiver, ByteOrder order, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static long doByteSequence(HostObject receiver,
                        ByteOrder order,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!hostClassCache.isBufferAccess()) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Long.BYTES);
            }
            try {
                return getByteSequenceLongBoundary(receiver.context.language.api, receiver.obj, (int) index, order);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Long.BYTES);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static long doOther(HostObject receiver, ByteOrder order, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Long.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                final long result = isPEFriendlyBuffer(buffer) ? buffer.getLong((int) index) : getBufferLongBoundary(buffer, (int) index);
                buffer.order(originalOrder);
                return result;
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Long.BYTES);
            }
        }
    }

    @TruffleBoundary
    private static long getBufferLongBoundary(ByteBuffer buffer, int index) {
        return buffer.getLong(index);
    }

    @TruffleBoundary
    private static long getByteSequenceLongBoundary(APIAccess apiAccess, Object byteSequence, int index, ByteOrder order) {
        long b1 = apiAccess.byteSequenceByteAt(byteSequence, index) & 0xFF;
        long b2 = apiAccess.byteSequenceByteAt(byteSequence, index + 1) & 0xFF;
        long b3 = apiAccess.byteSequenceByteAt(byteSequence, index + 2) & 0xFF;
        long b4 = apiAccess.byteSequenceByteAt(byteSequence, index + 3) & 0xFF;
        long b5 = apiAccess.byteSequenceByteAt(byteSequence, index + 4) & 0xFF;
        long b6 = apiAccess.byteSequenceByteAt(byteSequence, index + 5) & 0xFF;
        long b7 = apiAccess.byteSequenceByteAt(byteSequence, index + 6) & 0xFF;
        long b8 = apiAccess.byteSequenceByteAt(byteSequence, index + 7) & 0xFF;
        if (order == ByteOrder.BIG_ENDIAN) {
            return (b1 << 56) | (b2 << 48) | (b3 << 40) | (b4 << 32) | (b5 << 24) | (b6 << 16) | (b7 << 8) | b8;
        } else {
            return (b8 << 56) | (b7 << 48) | (b6 << 40) | (b5 << 32) | (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
        }
    }

    @ExportMessage
    static class WriteBufferLong {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, ByteOrder order, long index, long value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "!receiver.isNull()")
        static void doNonNull(HostObject receiver, ByteOrder order, long index, long value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Long.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                if (isPEFriendlyBuffer(buffer)) {
                    buffer.putLong((int) index, value);
                } else {
                    putBufferLongBoundary(buffer, (int) index, value);
                }
                buffer.order(originalOrder);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Long.BYTES);
            } catch (ReadOnlyBufferException e) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    private static void putBufferLongBoundary(ByteBuffer buffer, int index, long value) {
        buffer.putLong(index, value);
    }

    @ExportMessage
    static class ReadBufferFloat {

        @Specialization(guards = "receiver.isNull()")
        static float doNull(HostObject receiver, ByteOrder order, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static float doByteSequence(HostObject receiver,
                        ByteOrder order,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!hostClassCache.isBufferAccess()) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Float.BYTES);
            }
            try {
                return getByteSequenceFloatBoundary(receiver.context.language.api, receiver.obj, (int) index, order);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Float.BYTES);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static float doOther(HostObject receiver, ByteOrder order, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Float.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                final float result = isPEFriendlyBuffer(buffer) ? buffer.getFloat((int) index) : getBufferFloatBoundary(buffer, (int) index);
                buffer.order(originalOrder);
                return result;
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Float.BYTES);
            }
        }
    }

    @TruffleBoundary
    private static float getBufferFloatBoundary(ByteBuffer buffer, int index) {
        return buffer.getFloat(index);
    }

    @TruffleBoundary
    private static float getByteSequenceFloatBoundary(APIAccess apiAccess, Object byteSequence, int index, ByteOrder order) {
        return Float.intBitsToFloat(getByteSequenceIntBoundary(apiAccess, byteSequence, index, order));
    }

    @ExportMessage
    static class WriteBufferFloat {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, ByteOrder order, long index, float value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "!receiver.isNull()")
        static void doNonNull(HostObject receiver, ByteOrder order, long index, float value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Float.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                if (isPEFriendlyBuffer(buffer)) {
                    buffer.putFloat((int) index, value);
                } else {
                    putBufferFloatBoundary(buffer, (int) index, value);
                }
                buffer.order(originalOrder);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Float.BYTES);
            } catch (ReadOnlyBufferException e) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    private static void putBufferFloatBoundary(ByteBuffer buffer, int index, float value) {
        buffer.putFloat(index, value);
    }

    @ExportMessage
    static class ReadBufferDouble {

        @Specialization(guards = "receiver.isNull()")
        static double doNull(HostObject receiver, ByteOrder order, long index) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static double doByteSequence(HostObject receiver,
                        ByteOrder order,
                        long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!hostClassCache.isBufferAccess()) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Double.BYTES);
            }
            try {
                return getByteSequenceDoubleBoundary(receiver.context.language.api, receiver.obj, (int) index, order);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Double.BYTES);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static double doOther(HostObject receiver, ByteOrder order, long index,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Double.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                final double result = isPEFriendlyBuffer(buffer) ? buffer.getDouble((int) index) : getBufferDoubleBoundary(buffer, (int) index);
                buffer.order(originalOrder);
                return result;
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Double.BYTES);
            }
        }
    }

    @TruffleBoundary
    private static double getBufferDoubleBoundary(ByteBuffer buffer, int index) {
        return buffer.getDouble(index);
    }

    @TruffleBoundary
    private static double getByteSequenceDoubleBoundary(APIAccess apiAccess, Object byteSequence, int index, ByteOrder order) {
        return Double.longBitsToDouble(getByteSequenceLongBoundary(apiAccess, byteSequence, index, order));
    }

    @ExportMessage
    static class WriteBufferDouble {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, ByteOrder order, long index, double value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "!receiver.isNull()")
        static void doNonNull(HostObject receiver, ByteOrder order, long index, double value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Double.BYTES);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                final ByteOrder originalOrder = buffer.order();
                buffer.order(order);
                if (isPEFriendlyBuffer(buffer)) {
                    buffer.putDouble((int) index, value);
                } else {
                    putBufferDoubleBoundary(buffer, (int) index, value);
                }
                buffer.order(originalOrder);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(index, Double.BYTES);
            } catch (ReadOnlyBufferException e) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    private static void putBufferDoubleBoundary(ByteBuffer buffer, int index, double value) {
        buffer.putDouble(index, value);
    }

    @ExportMessage
    static class ReadBuffer {

        @Specialization(guards = "receiver.isNull()")
        static void doNull(HostObject receiver, long bufferByteOffset, byte[] destination, int destinationOffset, int byteLength) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isByteSequence()"})
        static void doByteSequence(HostObject receiver,
                        long bufferByteOffset,
                        byte[] destination,
                        int destinationOffset,
                        int byteLength,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!hostClassCache.isBufferAccess()) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (bufferByteOffset < 0 || Integer.MAX_VALUE < bufferByteOffset + byteLength) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(bufferByteOffset, byteLength);
            }
            try {
                getByteSequenceBytesBoundary(receiver.context.language.api, receiver.obj, (int) bufferByteOffset, destination, destinationOffset, byteLength);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(bufferByteOffset, byteLength);
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isByteSequence()"})
        static void doOther(HostObject receiver,
                        long bufferByteOffset,
                        byte[] destination,
                        int destinationOffset,
                        int byteLength,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (!receiver.isBuffer(hostClassCache)) {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
            if (bufferByteOffset < 0 || Integer.MAX_VALUE < bufferByteOffset + byteLength) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(bufferByteOffset, byteLength);
            }
            try {
                final ByteBuffer buffer = (ByteBuffer) classProfile.profile(node, receiver.obj);
                getBufferBytesBoundary(buffer, (int) bufferByteOffset, destination, destinationOffset, byteLength);
            } catch (IndexOutOfBoundsException e) {
                error.enter(node);
                throw InvalidBufferOffsetException.create(bufferByteOffset, byteLength);
            }
        }
    }

    @TruffleBoundary
    private static void getByteSequenceBytesBoundary(APIAccess apiAccess, Object byteSequence, int index, byte[] destination, int destinationOffset, int byteLength) {
        for (int i = index; i < index + byteLength; i++) {
            destination[destinationOffset + (i - index)] = apiAccess.byteSequenceByteAt(byteSequence, i);
        }
    }

    @TruffleBoundary
    private static void getBufferBytesBoundary(ByteBuffer buffer, int index, byte[] destination, int destinationOffset, int byteLength) {
        buffer.get(index, destination, destinationOffset, byteLength);
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
                        @Bind("$node") Node node,
                        @Shared("lookupConstructor") @Cached LookupConstructorNode lookupConstructor) {
            return lookupConstructor.execute(node, receiver, receiver.asClass()) != null;
        }
    }

    @ExportMessage
    boolean isExecutable(
                    @Bind("$node") Node node,
                    @Shared("lookupFunctionalMethod") @Cached LookupFunctionalMethodNode lookupMethod) {
        return !isNull() && !isClass() && lookupMethod.execute(node, this, getLookupClass()) != null;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Bind("$node") Node node,
                    @Shared("hostExecute") @Cached HostExecuteNode doExecute,
                    @Shared("lookupFunctionalMethod") @Cached LookupFunctionalMethodNode lookupMethod,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        if (!isNull() && !isClass()) {
            HostMethodDesc method = lookupMethod.execute(node, this, getLookupClass());
            if (method != null) {
                return doExecute.execute(node, method, obj, args, context);
            }
        }
        error.enter(node);
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
                        @Bind("$node") Node node,
                        @CachedLibrary(limit = "1") InteropLibrary indexes,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            if (args.length != 1) {
                error.enter(node);
                throw ArityException.create(1, 1, args.length);
            }
            Object arg0 = args[0];
            int length;
            if (indexes.fitsInInt(arg0)) {
                length = indexes.asInt(arg0);
            } else {
                error.enter(node);
                throw UnsupportedTypeException.create(args);
            }
            Object array = Array.newInstance(receiver.asClass().getComponentType(), length);
            return HostObject.forObject(array, receiver.context);
        }

        @Specialization(guards = "receiver.isDefaultClass()")
        static Object doObjectCached(HostObject receiver, Object[] arguments,
                        @Bind("$node") Node node,
                        @Shared("lookupConstructor") @Cached LookupConstructorNode lookupConstructor,
                        @Shared("hostExecute") @Cached HostExecuteNode executeMethod,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            assert !receiver.isArrayClass();
            HostMethodDesc constructor = lookupConstructor.execute(node, receiver, receiver.asClass());
            if (constructor != null) {
                return executeMethod.execute(node, constructor, null, arguments, receiver.context);
            }
            error.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class IsNumber {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return hostClassCache.isBigIntegerNumberAccess();
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) {
            Class<?> c = classProfile.profile(node, receiver.obj).getClass();
            return c == Byte.class || c == Short.class || c == Integer.class || c == Long.class || c == Float.class || c == Double.class;
        }
    }

    private static boolean isJavaSupportedNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double || value instanceof BigInteger;
    }

    boolean isBigInteger() {
        return CompilerDirectives.isExact(obj, BigInteger.class);
    }

    @ExportMessage
    static class FitsInByte {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerFitsInByte();
            } else {
                return false;
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.fitsInByte(receiver.obj);
            } else {
                return false;
            }
        }
    }

    @TruffleBoundary
    boolean bigIntegerFitsInByte() {
        return ((BigInteger) obj).bitLength() < Byte.SIZE;
    }

    @ExportMessage
    static class FitsInShort {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerFitsInShort();
            } else {
                return false;
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.fitsInShort(receiver.obj);
            } else {
                return false;
            }
        }
    }

    @TruffleBoundary
    boolean bigIntegerFitsInShort() {
        return ((BigInteger) obj).bitLength() < Short.SIZE;
    }

    @ExportMessage
    static class FitsInInt {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerFitsInInt();
            } else {
                return false;
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.fitsInInt(receiver.obj);
            } else {
                return false;
            }
        }
    }

    @TruffleBoundary
    boolean bigIntegerFitsInInt() {
        return ((BigInteger) obj).bitLength() < Integer.SIZE;
    }

    @ExportMessage
    static class FitsInLong {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerFitsInLong();
            } else {
                return false;
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.fitsInLong(receiver.obj);
            } else {
                return false;
            }
        }
    }

    @TruffleBoundary
    boolean bigIntegerFitsInLong() {
        return ((BigInteger) obj).bitLength() < Long.SIZE;
    }

    @ExportMessage
    static class FitsInBigInteger {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return hostClassCache.isBigIntegerNumberAccess();
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.fitsInBigInteger(receiver.obj);
            } else {
                return false;
            }
        }
    }

    @ExportMessage
    static class FitsInFloat {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerFitsInFloat();
            } else {
                return false;
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.fitsInFloat(receiver.obj);
            } else {
                return false;
            }
        }
    }

    @TruffleBoundary
    boolean bigIntegerFitsInFloat() {
        BigInteger b = (BigInteger) obj;
        return bigIntegerFitsInFloat(b);
    }

    static boolean bigIntegerFitsInFloat(BigInteger b) {
        if (b.bitLength() <= 24) { // 24 = size of float mantissa + 1
            return true;
        } else {
            float floatValue = b.floatValue();
            if (!Float.isFinite(floatValue)) {
                return false;
            }
            /*
             * The floatValue is an integer (no fractional part), because it came from a BigInteger
             * that isn't so big to be converted to negative or positive infinity, but it might not
             * be the same integer that was represented by the original BigInteger. We might have
             * lost precision.
             */
            try {
                return new BigDecimal(floatValue).toBigIntegerExact().equals(b);
            } catch (ArithmeticException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @ExportMessage
    static class FitsInDouble {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static boolean doBigInteger(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerFitsInDouble();
            } else {
                return false;
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static boolean doOther(HostObject receiver,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.fitsInDouble(receiver.obj);
            } else {
                return false;
            }
        }
    }

    @TruffleBoundary
    boolean bigIntegerFitsInDouble() {
        BigInteger b = (BigInteger) obj;
        return bigIntegerFitsInDouble(b);
    }

    static boolean bigIntegerFitsInDouble(BigInteger b) {
        if (b.bitLength() <= 53) { // 53 = size of double mantissa + 1
            return true;
        } else {
            double doubleValue = b.doubleValue();
            if (!Double.isFinite(doubleValue)) {
                return false;
            }
            /*
             * The doubleValue is an integer (no fractional part), because it came from a BigInteger
             * that isn't so big to be converted to negative or positive infinity, but it might not
             * be the same integer that was represented by the original BigInteger. We might have
             * lost precision.
             */
            try {
                return new BigDecimal(doubleValue).toBigIntegerExact().equals(b);
            } catch (ArithmeticException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @ExportMessage
    static class AsByte {

        @Specialization(guards = "receiver.isNull()")
        static byte doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static byte doBigInteger(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerAsByte();
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static byte doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.asByte(receiver.obj);
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    byte bigIntegerAsByte() throws UnsupportedMessageException {
        try {
            return ((BigInteger) obj).byteValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class AsShort {

        @Specialization(guards = "receiver.isNull()")
        static short doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static short doBigInteger(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerAsShort();
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static short doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.asShort(receiver.obj);
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    short bigIntegerAsShort() throws UnsupportedMessageException {
        try {
            return ((BigInteger) obj).shortValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class AsInt {

        @Specialization(guards = "receiver.isNull()")
        static int doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static int doBigInteger(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerAsInt();
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static int doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.asInt(receiver.obj);
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    int bigIntegerAsInt() throws UnsupportedMessageException {
        try {
            return ((BigInteger) obj).intValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class AsLong {

        @Specialization(guards = "receiver.isNull()")
        static long doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static long doBigInteger(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerAsLong();
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static long doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.asLong(receiver.obj);
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    long bigIntegerAsLong() throws UnsupportedMessageException {
        try {
            return ((BigInteger) obj).longValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class AsBigInteger {

        @Specialization(guards = "receiver.isNull()")
        static BigInteger doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static BigInteger doBigInteger(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return (BigInteger) receiver.obj;
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static BigInteger doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.asBigInteger(receiver.obj);
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @ExportMessage
    static class AsFloat {

        @Specialization(guards = "receiver.isNull()")
        static float doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static float doBigInteger(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerAsFloat();
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static float doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.asFloat(receiver.obj);
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    float bigIntegerAsFloat() throws UnsupportedMessageException {
        if (bigIntegerFitsInFloat()) {
            return ((BigInteger) obj).floatValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class AsDouble {

        @Specialization(guards = "receiver.isNull()")
        static double doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"receiver.isBigInteger()"})
        static double doBigInteger(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (hostClassCache.isBigIntegerNumberAccess()) {
                return receiver.bigIntegerAsDouble();
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }

        @Specialization(guards = {"!receiver.isNull()", "!receiver.isBigInteger()"})
        static double doOther(HostObject receiver,
                        @Bind("$node") Node node,
                        @CachedLibrary("receiver") InteropLibrary receiverLibrary,
                        @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
            if (receiverLibrary.isNumber(receiver)) {
                return numbers.asDouble(receiver.obj);
            } else {
                error.enter(node);
                throw UnsupportedMessageException.create();
            }
        }
    }

    @TruffleBoundary
    double bigIntegerAsDouble() throws UnsupportedMessageException {
        if (bigIntegerFitsInDouble()) {
            return ((BigInteger) obj).doubleValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isString(
                    @Bind("$node") Node node,
                    @Shared("classProfile") @Cached InlinedExactClassProfile classProfile) {
        if (isNull()) {
            return false;
        }
        Class<?> c = classProfile.profile(node, obj).getClass();
        return c == String.class || c == Character.class;
    }

    @ExportMessage
    String asString(@Bind("$node") Node node,
                    @CachedLibrary("this") InteropLibrary thisLibrary,
                    @Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary strings,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
        if (thisLibrary.isString(this)) {
            return strings.asString(obj);
        } else {
            error.enter(node);
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
    boolean asBoolean(
                    @Bind("$node") Node node,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
        if (isBoolean()) {
            return (boolean) obj;
        } else {
            error.enter(node);
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
    ExceptionType getExceptionType(
                    @Bind("$node") Node node,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
        if (isException()) {
            return obj instanceof InterruptedException ? ExceptionType.INTERRUPT : ExceptionType.RUNTIME_ERROR;
        }
        error.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isExceptionIncompleteSource(
                    @Bind("$node") Node node,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
        if (isException()) {
            return false;
        }
        error.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    int getExceptionExitStatus(
                    @Bind("$node") Node node,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
        error.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasExceptionMessage() {
        return isException() && ((Throwable) obj).getMessage() != null;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExceptionMessage(
                    @Bind("$node") Node node,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
        String message = isException() ? ((Throwable) obj).getMessage() : null;
        if (message != null) {
            return message;
        }
        error.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasExceptionCause() {
        if (isException()) {
            Throwable cause = ((Throwable) obj).getCause();
            if (cause != null) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExceptionCause() throws UnsupportedMessageException {
        if (isException()) {
            Throwable cause = ((Throwable) obj).getCause();
            if (cause != null) {
                if (cause instanceof AbstractTruffleException) {
                    return cause;
                } else {
                    return HostException.wrap(cause, context);
                }
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasExceptionStackTrace() {
        if (isException()) {
            Object hostExceptionOrOriginal = extraInfo != null ? extraInfo : obj;
            return TruffleStackTrace.fillIn((Throwable) hostExceptionOrOriginal) != null;
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExceptionStackTrace() throws UnsupportedMessageException {
        if (isException()) {
            // Using HostException here allows us to make use of its getLocation(), if available.
            Object hostExceptionOrOriginal = extraInfo != null ? extraInfo : obj;
            return HostAccessor.EXCEPTION.getExceptionStackTrace(hostExceptionOrOriginal, context.internalContext);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    RuntimeException throwException(
                    @Bind("$node") Node node,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
        if (isException()) {
            RuntimeException ex = (HostException) extraInfo;
            if (ex == null) {
                ex = context.hostToGuestException((Throwable) obj, node);
            }
            throw ex;
        }
        error.enter(node);
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
                            assert isJavaSupportedNumber(javaObject) : javaObject;
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
    static class HasIterator {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "!receiver.isNull()")
        static boolean doNonNull(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return receiver.isIterable(hostClassCache) || receiver.isArray(hostClassCache);
        }
    }

    @ExportMessage
    abstract static class GetIterator {

        @Specialization(guards = "receiver.isNull()")
        protected static boolean doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isArray(hostClassCache)"})
        protected static Object doArray(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest) {
            return toGuest.execute(node, receiver.context, arrayIteratorImpl(receiver));
        }

        @TruffleBoundary
        private static Object arrayIteratorImpl(Object receiver) {
            return HostAccessor.INTEROP.createDefaultIterator(receiver);
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isIterable(hostClassCache)"})
        protected static Object doIterable(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            Object hostValue;
            try {
                hostValue = GuestToHostCalls.getIterator(receiver);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(node, receiver.context, hostValue);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isArray(hostClassCache)", "!receiver.isIterable(hostClassCache)"})
        protected static Object doNotArrayOrIterable(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class IsIterator {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "!receiver.isNull()")
        static boolean doNonNull(
                        HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return receiver.isIteratorLocal(hostClassCache);
        }
    }

    @ExportMessage
    abstract static class HasIteratorNextElement {

        @Specialization(guards = "receiver.isNull()")
        protected static boolean doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isIteratorLocal(hostClassCache)"})
        protected static boolean doIterator(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            try {
                return GuestToHostCalls.hasIteratorNext(receiver);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isIteratorLocal(hostClassCache)"})
        protected static boolean doNotIterator(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class GetIteratorNextElement {

        @Specialization(guards = "receiver.isNull()")
        protected static boolean doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isIteratorLocal(hostClassCache)"})
        protected static Object doIterator(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest,
                        @Shared("error") @Cached InlinedBranchProfile error,
                        @Exclusive @Cached InlinedBranchProfile stopIteration) throws StopIterationException {
            Object next;
            try {
                next = GuestToHostCalls.getIteratorNext(receiver);
            } catch (NoSuchElementException e) {
                stopIteration.enter(node);
                throw StopIterationException.create();
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(node, receiver.context, next);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isIteratorLocal(hostClassCache)"})
        protected static Object doNotIterator(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class HasHashEntries {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver) {
            return false;
        }

        @Specialization(guards = "!receiver.isNull()")
        static boolean doNonNull(
                        HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return receiver.isMap(hostClassCache);
        }
    }

    @ExportMessage
    abstract static class GetHashSize {

        @Specialization(guards = "receiver.isNull()")
        protected static long doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMap(hostClassCache)"})
        protected static long doMap(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            try {
                return GuestToHostCalls.getMapSize(receiver);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isMap(hostClassCache)"})
        protected static long doNotMap(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isHashEntryReadable")
    @ExportMessage(name = "isHashEntryModifiable")
    @ExportMessage(name = "isHashEntryRemovable")
    static class IsHashEntryReadable {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver, Object key) {
            return false;
        }

        @Specialization(guards = "!receiver.isNull()")
        static boolean doNonNull(HostObject receiver,
                        Object key,
                        @Bind("$node") Node node,
                        @Shared("containsKey") @Cached ContainsKeyNode containsKey,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return receiver.isMap(hostClassCache) && containsKey.execute(node, receiver, key, hostClassCache);
        }
    }

    @ExportMessage
    abstract static class ReadHashValue {

        private static final Object UNDEFINED = new Object();

        @Specialization(guards = "receiver.isNull()")
        protected static Object doNull(HostObject receiver, Object key) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMap(hostClassCache)"})
        protected static Object doMap(HostObject receiver, Object key,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toHost") @Cached(inline = true) HostToTypeNode toHost,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnknownKeyException {
            Object hostKey;
            try {
                hostKey = toHost.execute(node, receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter(node);
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
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
            if (hostResult == UNDEFINED) {
                error.enter(node);
                throw UnknownKeyException.create(key);
            }
            return toGuest.execute(node, receiver.context, hostResult);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isMap(hostClassCache)"})
        protected static Object doNotMap(HostObject receiver, Object key,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class IsHashEntryInsertable {

        @Specialization(guards = "receiver.isNull()")
        static boolean doNull(HostObject receiver, Object key) {
            return false;
        }

        @Specialization(guards = "!receiver.isNull()")
        static boolean doNonNull(
                        HostObject receiver,
                        Object key,
                        @Bind("$node") Node node,
                        @Shared("containsKey") @Cached ContainsKeyNode containsKey,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) {
            return receiver.isMap(hostClassCache) && !containsKey.execute(node, receiver, key, hostClassCache);
        }
    }

    @ExportMessage
    abstract static class WriteHashEntry {

        @Specialization(guards = "receiver.isNull()")
        protected static void doNull(HostObject receiver, Object key, Object value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMap(hostClassCache)"})
        protected static void doMap(HostObject receiver, Object key, Object value,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toHost") @Cached(inline = true) HostToTypeNode toHost,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedTypeException {

            Object hostKey;
            Object hostValue;
            try {
                hostKey = toHost.execute(node, receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter(node);
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{key}, getMessage(ee));
                }
                throw e;
            }

            try {
                hostValue = toHost.execute(node, receiver.context, value, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter(node);
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    throw UnsupportedTypeException.create(new Object[]{value}, getMessage(ee));
                }
                throw e;
            }
            try {
                GuestToHostCalls.putMapValue(receiver, hostKey, hostValue);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isMap(hostClassCache)"})
        protected static void doNotMap(HostObject receiver, Object key, Object value,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class RemoveHashEntry {

        @Specialization(guards = "receiver.isNull()")
        protected static void doNull(HostObject receiver, Object key) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMap(hostClassCache)"})
        protected static void doMap(HostObject receiver, Object key,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toHost") @Cached(inline = true) HostToTypeNode toHost,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnknownKeyException {
            Object hostKey;
            try {
                hostKey = toHost.execute(node, receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter(node);
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
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
            if (!removed) {
                error.enter(node);
                throw UnknownKeyException.create(key);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isMap(hostClassCache)"})
        protected static void doNotMap(HostObject receiver, Object key,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    abstract static class GetHashEntriesIterator {

        @Specialization(guards = "receiver.isNull()")
        protected static Object doNull(HostObject receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMap(hostClassCache)"})
        protected static Object doMap(HostObject receiver,
                        @Bind("$node") Node node,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache,
                        @Shared("toGuest") @Cached(inline = true) ToGuestValueNode toGuest,
                        @Shared("error") @Cached InlinedBranchProfile error) {
            Object hostValue;
            try {
                hostValue = GuestToHostCalls.getEntriesIterator(receiver);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
            return toGuest.execute(node, receiver.context, hostValue);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isMap(hostClassCache)"})
        protected static Object doNotMap(HostObject receiver,
                        @Shared @Cached(value = "receiver.getHostClassCache()", allowUncached = true) HostClassCache hostClassCache) throws UnsupportedMessageException {
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
                    @Bind("$node") Node node,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("error") @Cached InlinedBranchProfile error) throws UnsupportedMessageException {
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
                Object otherHost = HostProxy.toProxyHostObject(language, other);
                return c.isInstance(otherHost);
            } else {
                boolean canConvert = HostToTypeNode.canConvert(null, other, c, c,
                                HostToTypeNode.allowsImplementation(context, c),
                                context, HostToTypeNode.LOWEST,
                                InteropLibrary.getFactory().getUncached(other),
                                HostTargetMappingNode.getUncached());
                return canConvert;
            }
        } else {
            error.enter(node);
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
                        @Bind("$node") Node node,
                        @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                error.enter(node);
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

    @NeverDefault
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
    @GenerateInline
    @GenerateCached(false)
    abstract static class ArraySet extends Node {

        protected abstract void execute(Node node, Object array, int index, Object value);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class ArrayGet extends Node {

        protected abstract Object execute(Node node, Object array, int index);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class LookupConstructorNode extends Node {
        static final int LIMIT = 3;

        LookupConstructorNode() {
        }

        public abstract HostMethodDesc execute(Node node, HostObject receiver, Class<?> clazz);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class LookupFieldNode extends Node {
        static final int LIMIT = 3;

        LookupFieldNode() {
        }

        public abstract HostFieldDesc execute(Node node, HostObject receiver, Class<?> clazz, String name, boolean onlyStatic);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class LookupFunctionalMethodNode extends Node {
        static final int LIMIT = 3;

        LookupFunctionalMethodNode() {
        }

        public abstract HostMethodDesc execute(Node node, HostObject object, Class<?> clazz);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class LookupInnerClassNode extends Node {
        static final int LIMIT = 3;

        LookupInnerClassNode() {
        }

        public abstract Class<?> execute(Node node, Class<?> outerclass, String name);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class LookupMethodNode extends Node {
        static final int LIMIT = 3;

        LookupMethodNode() {
        }

        public abstract HostMethodDesc execute(Node node, HostObject receiver, Class<?> clazz, String name, boolean onlyStatic);

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
    @GenerateInline
    @GenerateCached(false)
    abstract static class ReadFieldNode extends Node {
        static final int LIMIT = 3;

        ReadFieldNode() {
        }

        public abstract Object execute(Node node, HostFieldDesc field, HostObject object);

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        static Object doCached(Node node, HostFieldDesc field, HostObject object,
                        @Cached("field") HostFieldDesc cachedField,
                        @Cached ToGuestValueNode toGuest) {
            Object val = cachedField.get(object.obj);
            return toGuest.execute(node, object.context, val);
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Object doUncached(HostFieldDesc field, HostObject object) {
            Object val = field.get(object.obj);
            return ToGuestValueNodeGen.getUncached().execute(null, object.context, val);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class WriteFieldNode extends Node {
        static final int LIMIT = 3;

        WriteFieldNode() {
        }

        public abstract void execute(Node node, HostFieldDesc field, HostObject object, Object value) throws UnsupportedTypeException, UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"field == cachedField"}, limit = "LIMIT")
        static void doCached(Node node, HostFieldDesc field, HostObject object, Object rawValue,
                        @Cached("field") HostFieldDesc cachedField,
                        @Cached HostToTypeNode toHost,
                        @Cached InlinedBranchProfile error) throws UnsupportedTypeException, UnknownIdentifierException {
            if (field.isFinal()) {
                error.enter(node);
                throw UnknownIdentifierException.create(field.getName());
            }
            try {
                Object value = toHost.execute(node, object.context, rawValue, cachedField.getType(), cachedField.getGenericType(), true);
                cachedField.set(object.obj, value);
            } catch (RuntimeException e) {
                error.enter(node);
                RuntimeException ee = unboxEngineException(object, e);
                if (ee != null) {
                    throw HostInteropErrors.unsupportedTypeException(rawValue, ee);
                }
                throw e;
            }
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static void doUncached(HostFieldDesc field, HostObject object, Object rawValue) throws UnsupportedTypeException, UnknownIdentifierException {
            if (field.isFinal()) {
                throw UnknownIdentifierException.create(field.getName());
            }
            try {
                Object val = HostToTypeNodeGen.getUncached().execute(null, object.context, rawValue, field.getType(), field.getGenericType(), true);
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

    boolean isList(HostClassCache hostClassCache) {
        return hostClassCache.isListAccess() && obj instanceof List;
    }

    boolean isArray(HostClassCache hostClassCache) {
        return hostClassCache.isArrayAccess() && obj.getClass().isArray();
    }

    boolean isBuffer(HostClassCache hostClassCache) {
        return hostClassCache.isBufferAccess() && ByteBuffer.class.isAssignableFrom(obj.getClass());
    }

    boolean isIterable(HostClassCache hostClassCache) {
        return hostClassCache.isIterableAccess() && obj instanceof Iterable;
    }

    /**
     * This method cannot be called "isIterator: because it would conflict with an interop library
     * message name.
     */
    boolean isIteratorLocal(HostClassCache hostClassCache) {
        return hostClassCache.isIteratorAccess() && obj instanceof Iterator;
    }

    boolean isMap(HostClassCache hostClassCache) {
        return hostClassCache.isMapAccess() && obj instanceof Map;
    }

    boolean isMapEntry(HostClassCache hostClassCache) {
        return hostClassCache.isMapAccess() && obj instanceof Map.Entry;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class ContainsKeyNode extends Node {

        public abstract boolean execute(Node node, HostObject receiver, Object key, HostClassCache hostClassCache);

        @Specialization(guards = {"!receiver.isNull()", "receiver.isMap(hostClassCache)"})
        protected static boolean doMap(Node node, HostObject receiver, Object key, HostClassCache hostClassCache,
                        @Cached HostToTypeNode toHost,
                        @Cached InlinedBranchProfile error) {
            Object hostKey;
            try {
                hostKey = toHost.execute(node, receiver.context, key, Object.class, null, true);
            } catch (RuntimeException e) {
                error.enter(node);
                RuntimeException ee = unboxEngineException(receiver, e);
                if (ee != null) {
                    return false;
                }
                throw e;
            }
            try {
                return GuestToHostCalls.containsMapKey(receiver, hostKey);
            } catch (Throwable t) {
                error.enter(node);
                throw receiver.context.hostToGuestException(t);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!receiver.isNull()", "!receiver.isMap(hostClassCache)"})
        protected static boolean doNotMap(Node node, HostObject receiver, Object key, HostClassCache hostClassCache) {
            return false;
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
