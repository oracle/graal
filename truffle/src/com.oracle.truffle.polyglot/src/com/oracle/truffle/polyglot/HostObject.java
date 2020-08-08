/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Array;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("unused")
final class HostObject implements TruffleObject {

    static final int LIMIT = 5;

    private static final ZoneId UTC = ZoneId.of("UTC");
    static final HostObject NULL = new HostObject(null, null, null);

    final Object obj;
    final PolyglotLanguageContext languageContext;
    /**
     * Either null (default), the Class if this is a static class, or an optional HostException if
     * the object is an instance of Throwable.
     */
    private final Object extraInfo;

    private HostObject(Object obj, PolyglotLanguageContext languageContext, Object extraInfo) {
        this.obj = obj;
        this.languageContext = languageContext;
        this.extraInfo = extraInfo;
    }

    static HostObject forClass(Class<?> clazz, PolyglotLanguageContext languageContext) {
        assert clazz != null;
        return new HostObject(clazz, languageContext, null);
    }

    static HostObject forStaticClass(Class<?> clazz, PolyglotLanguageContext languageContext) {
        assert clazz != null;
        return new HostObject(clazz, languageContext, clazz);
    }

    static HostObject forObject(Object object, PolyglotLanguageContext languageContext) {
        assert object != null && !(object instanceof Class<?>);
        return new HostObject(object, languageContext, null);
    }

    static HostObject forException(Throwable object, PolyglotLanguageContext languageContext, HostException hostException) {
        Objects.requireNonNull(object);
        return new HostObject(object, languageContext, hostException);
    }

    static boolean isInstance(Object obj) {
        return obj instanceof HostObject;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof HostObject;
    }

    HostObject withContext(PolyglotLanguageContext context) {
        return new HostObject(this.obj, context, this.extraInfo);
    }

    static boolean isJavaInstance(Class<?> targetType, Object javaObject) {
        if (javaObject instanceof HostObject) {
            final Object value = valueOf(javaObject);
            return targetType.isInstance(value);
        } else {
            return false;
        }
    }

    static Object valueOf(Object value) {
        final HostObject obj = (HostObject) value;
        return obj.obj;
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
    final class KeysArray implements TruffleObject {

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
        String[] fields = HostInteropReflect.findUniquePublicMemberNames(getEngine(), getLookupClass(), isStaticClass(), isClass(), includeInternal);
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
            return new HostFunction(foundMethod, this.obj, this.languageContext);
        }

        if (isStatic) {
            LookupInnerClassNode lookupInnerClassNode = lookupInnerClass;
            if (HostInteropReflect.STATIC_TO_CLASS.equals(name)) {
                return HostObject.forClass(lookupClass, languageContext);
            }
            Class<?> innerclass = lookupInnerClassNode.execute(lookupClass, name);
            if (innerclass != null) {
                return HostObject.forStaticClass(innerclass, languageContext);
            }
        } else if (isClass() && HostInteropReflect.CLASS_TO_STATIC.equals(name)) {
            return HostObject.forStaticClass(asClass(), languageContext);
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
            return executeMethod.execute(foundMethod, obj, args, languageContext);
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

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    static class IsArrayElementExisting {

        @Specialization(guards = "isArray.execute(receiver)", limit = "1")
        static boolean doArray(HostObject receiver, long index,
                        @Shared("isArray") @Cached IsArrayNode isArray) {
            long size = getArrayLength(receiver.obj);
            return index >= 0 && index < size;
        }

        @Specialization(guards = "isList.execute(receiver)", limit = "1")
        static boolean doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList) {
            long size = receiver.getListSize();
            return index >= 0 && index < size;
        }

        @Specialization(guards = {"!isList.execute(receiver)", "!isArray.execute(receiver)"}, limit = "1")
        static boolean doNotArrayOrList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("isArray") @Cached IsArrayNode isArray) {
            return false;
        }
    }

    @ExportMessage
    boolean isArrayElementInsertable(long index, @Shared("isList") @Cached IsListNode isList) {
        return isList.execute(this) && getListSize() == index;
    }

    @ExportMessage
    static class WriteArrayElement {

        @Specialization(guards = {"isArray.execute(receiver)"}, limit = "1")
        @SuppressWarnings("unchecked")
        static void doArray(HostObject receiver, long index, Object value,
                        @Shared("toHost") @Cached ToHostNode toHostNode,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Cached ArraySet arraySet,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            Object obj = receiver.obj;
            Object javaValue;
            try {
                javaValue = toHostNode.execute(value, obj.getClass().getComponentType(), null, receiver.languageContext, true);
            } catch (PolyglotEngineException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessage(e));
            }
            try {
                arraySet.execute(obj, (int) index, javaValue);
            } catch (ArrayIndexOutOfBoundsException e) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @TruffleBoundary
        private static String getMessage(PolyglotEngineException e) {
            return e.e.getMessage();
        }

        @Specialization(guards = {"isList.execute(receiver)"}, limit = "1")
        @SuppressWarnings("unchecked")
        static void doList(HostObject receiver, long index, Object value,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("toHost") @Cached ToHostNode toHostNode,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException, UnsupportedTypeException {
            if (index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            Object javaValue;
            try {
                javaValue = toHostNode.execute(value, Object.class, null, receiver.languageContext, true);
            } catch (PolyglotEngineException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, getMessage(e));
            }
            try {
                List<Object> list = ((List<Object>) receiver.obj);
                setList(list, index, javaValue);
            } catch (IndexOutOfBoundsException e) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @TruffleBoundary
        private static void setList(List<Object> list, long index, final Object hostValue) {
            if (index == list.size()) {
                list.add(hostValue);
            } else {
                list.set((int) index, hostValue);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isList.execute(receiver)", "!isArray.execute(receiver)"}, limit = "1")
        static void doNotArrayOrList(HostObject receiver, long index, Object value,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("isArray") @Cached IsArrayNode isArray) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    @ExportMessage
    static class IsArrayElementRemovable {

        @Specialization(guards = "isList.execute(receiver)", limit = "1")
        static boolean doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList) {
            return index >= 0 && index < callSize(receiver);
        }

        @TruffleBoundary
        private static int callSize(HostObject receiver) {
            return ((List<?>) receiver.obj).size();
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
            if (index > Integer.MAX_VALUE) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                boundaryRemove(receiver, index);
            } catch (IndexOutOfBoundsException outOfBounds) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        private static Object boundaryRemove(HostObject receiver, long index) throws IndexOutOfBoundsException {
            return ((List<Object>) receiver.obj).remove((int) index);
        }

        @Specialization(guards = "!isList.execute(receiver)", limit = "1")
        static void doOther(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean hasArrayElements(@Shared("isList") @Cached IsListNode isList,
                    @Shared("isArray") @Cached IsArrayNode isArray) {
        return isList.execute(this) || isArray.execute(this);
    }

    @ExportMessage
    abstract static class ReadArrayElement {

        @Specialization(guards = {"isArray.execute(receiver)"}, limit = "1")
        protected static Object doArray(HostObject receiver, long index,
                        @Cached ArrayGet arrayGet,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            if (index > Integer.MAX_VALUE) {
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
            return toGuest.execute(receiver.languageContext, val);
        }

        @TruffleBoundary
        @Specialization(guards = {"isList.execute(receiver)"}, limit = "1")
        protected static Object doList(HostObject receiver, long index,
                        @Shared("isList") @Cached IsListNode isList,
                        @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                        @Shared("error") @Cached BranchProfile error) throws InvalidArrayIndexException {
            try {
                if (index > Integer.MAX_VALUE) {
                    error.enter();
                    throw InvalidArrayIndexException.create(index);
                }
                return toGuest.execute(receiver.languageContext, ((List<?>) receiver.obj).get((int) index));
            } catch (IndexOutOfBoundsException e) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isArray.execute(receiver)", "!isList.execute(receiver)"}, limit = "1")
        protected static Object doNotArrayOrList(HostObject receiver, long index,
                        @Shared("isArray") @Cached IsArrayNode isArray,
                        @Shared("isList") @Cached IsListNode isList) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

    }

    /**
     * java.lang.reflect.Array.getLength is not PE-safe if the error conditions (null or not an
     * array) can happen. In our case, we know the error conditions can't happen because we check
     * them manually. This helper function is here to help the static analysis of native-image
     * figure out that the error condition can not happen.
     *
     * TODO: Remove when Array.getLength is made PE-safe on native-image (GR-23860).
     */
    private static int getArrayLength(Object array) {
        if (array == null || !array.getClass().isArray()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }
        return Array.getLength(array);
    }

    @ExportMessage
    long getArraySize(@Shared("isArray") @Cached IsArrayNode isArray,
                    @Shared("isList") @Cached IsListNode isList,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isArray.execute(this)) {
            return getArrayLength(obj);
        } else if (isList.execute(this)) {
            return getListSize();
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary(allowInlining = true)
    int getListSize() {
        return ((List<?>) obj).size();
    }

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
                return doExecute.execute(method, obj, args, languageContext);
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
                throw ArityException.create(1, args.length);
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
            return HostObject.forObject(array, receiver.languageContext);
        }

        @Specialization(guards = "receiver.isDefaultClass()")
        static Object doObjectCached(HostObject receiver, Object[] arguments,
                        @Shared("lookupConstructor") @Cached LookupConstructorNode lookupConstructor,
                        @Shared("hostExecute") @Cached HostExecuteNode executeMethod,
                        @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            assert !receiver.isArrayClass();
            HostMethodDesc constructor = lookupConstructor.execute(receiver, receiver.asClass());
            if (constructor != null) {
                return executeMethod.execute(constructor, null, arguments, receiver.languageContext);
            }
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isNumber() {
        if (isNull()) {
            return false;
        }
        Class<?> c = obj.getClass();
        return c == Byte.class || c == Short.class || c == Integer.class || c == Long.class || c == Float.class || c == Double.class;
    }

    @ExportMessage
    boolean fitsInByte(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInByte(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInShort(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInShort(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInInt(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInInt(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInLong(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInLong(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInFloat(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInFloat(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean fitsInDouble(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers) {
        if (isNumber()) {
            return numbers.fitsInDouble(obj);
        } else {
            return false;
        }
    }

    @ExportMessage
    byte asByte(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asByte(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    short asShort(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asShort(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    int asInt(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asInt(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    long asLong(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asLong(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    float asFloat(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asFloat(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    double asDouble(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary numbers,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isNumber()) {
            return numbers.asDouble(obj);
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isString() {
        if (isNull()) {
            return false;
        }
        Class<?> c = obj.getClass();
        return c == String.class || c == Character.class;
    }

    @ExportMessage
    String asString(@Shared("numbers") @CachedLibrary(limit = "LIMIT") InteropLibrary strings,
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isString()) {
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
        return obj instanceof LocalDate || obj instanceof LocalDateTime || obj instanceof Instant || obj instanceof ZonedDateTime || obj instanceof Date;
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
        } else if (obj instanceof Date) {
            return ((Date) obj).toInstant().atZone(UTC).toLocalDate();
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isTime() {
        return obj instanceof LocalTime || obj instanceof LocalDateTime || obj instanceof Instant || obj instanceof ZonedDateTime || obj instanceof Date;
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
        } else if (obj instanceof Date) {
            return ((Date) obj).toInstant().atZone(UTC).toLocalTime();
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isTimeZone() {
        return obj instanceof ZoneId || obj instanceof Instant || obj instanceof ZonedDateTime || obj instanceof Date;
    }

    @ExportMessage
    ZoneId asTimeZone() throws UnsupportedMessageException {
        if (obj instanceof ZoneId) {
            return (ZoneId) obj;
        } else if (obj instanceof ZonedDateTime) {
            return ((ZonedDateTime) obj).getZone();
        } else if (obj instanceof Instant) {
            return UTC;
        } else if (obj instanceof Date) {
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
        } else if (obj instanceof Date) {
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
    RuntimeException throwException(@Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isException()) {
            HostException ex = (HostException) extraInfo;
            if (ex == null) {
                ex = new HostException((Throwable) obj);
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
        return toStringImpl(languageContext, this.obj, 0, allowSideEffects);
    }

    @TruffleBoundary
    static String toStringImpl(PolyglotLanguageContext context, Object javaObject, int level, boolean allowSideEffects) {
        try {
            if (javaObject == null) {
                return "null";
            } else if (javaObject.getClass().isArray()) {
                return arrayToString(context, javaObject, level, allowSideEffects);
            } else if (javaObject instanceof Class) {
                return ((Class<?>) javaObject).getTypeName();
            } else {
                if (allowSideEffects) {
                    return Objects.toString(javaObject);
                } else {
                    return javaObject.getClass().getTypeName() + "@" + Integer.toHexString(System.identityHashCode(javaObject));
                }
            }
        } catch (Throwable t) {
            throw PolyglotImpl.hostToGuestException(context, t);
        }
    }

    private static String arrayToString(PolyglotLanguageContext context, Object array, int level, boolean allowSideEffects) {
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

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject() throws UnsupportedMessageException {
        Object javaObject = this.obj;
        Class<?> javaType;
        if (javaObject == null) {
            javaType = Void.class;
        } else {
            javaType = javaObject.getClass();
        }
        return HostObject.forClass(javaType, languageContext);
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
                    @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        if (isClass()) {
            Class<?> c = asClass();
            if (HostObject.isInstance(other)) {
                HostObject otherHost = ((HostObject) other);
                if (otherHost.isNull()) {
                    return c == Void.class;
                } else {
                    return c.isInstance(otherHost.obj);
                }
            } else if (PolyglotProxy.isProxyGuestObject(other)) {
                PolyglotProxy otherHost = (PolyglotProxy) other;
                return c.isInstance(otherHost.proxy);
            } else {
                boolean canConvert = ToHostNode.canConvert(other, c, c,
                                ToHostNode.allowsImplementation(languageContext, c),
                                languageContext, ToHostNode.MAX,
                                InteropLibrary.getFactory().getUncached(other),
                                TargetMappingNode.getUncached());
                return canConvert;
            }
        } else {
            error.enter();
            throw UnsupportedMessageException.create();
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

    PolyglotEngineImpl getEngine() {
        PolyglotContextImpl context = languageContext != null ? languageContext.context : null;
        if (context == null) {
            context = PolyglotContextImpl.currentNotEntered();
        }
        return context.engine;
    }

    HostClassCache getHostClassCache() {
        return HostClassCache.forInstance(this);
    }

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doHostObject(HostObject receiver, HostObject other) {
            return receiver.obj == other.obj ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(HostObject receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    static int identityHashCode(HostObject receiver) {
        return System.identityHashCode(receiver.obj);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HostObject) {
            HostObject other = (HostObject) o;
            return this.obj == other.obj && this.languageContext == other.languageContext;
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
            return HostClassDesc.forClass(receiver.getEngine(), clazz).lookupConstructor();
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
            return HostInteropReflect.findField(receiver.getEngine(), clazz, name, onlyStatic);
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
            return HostClassDesc.forClass(object.getEngine(), clazz).getFunctionalMethod();
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
            return HostInteropReflect.findMethod(receiver.getEngine(), clazz, name, onlyStatic);
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
            return toGuest.execute(object.languageContext, val);
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Object doUncached(HostFieldDesc field, HostObject object,
                        @Cached ToGuestValueNode toGuest) {
            Object val = field.get(object.obj);
            return toGuest.execute(object.languageContext, val);
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
                        @Cached ToHostNode toHost,
                        @Cached BranchProfile error) throws UnsupportedTypeException, UnknownIdentifierException {
            if (field.isFinal()) {
                error.enter();
                throw UnknownIdentifierException.create(field.getName());
            }
            try {
                Object value = toHost.execute(rawValue, cachedField.getType(), cachedField.getGenericType(), object.languageContext, true);
                cachedField.set(object.obj, value);
            } catch (PolyglotEngineException e) {
                error.enter();
                throw HostInteropErrors.unsupportedTypeException(rawValue, e.e);
            }
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static void doUncached(HostFieldDesc field, HostObject object, Object rawValue,
                        @Cached ToHostNode toHost) throws UnsupportedTypeException, UnknownIdentifierException {
            if (field.isFinal()) {
                throw UnknownIdentifierException.create(field.getName());
            }
            try {
                Object val = toHost.execute(rawValue, field.getType(), field.getGenericType(), object.languageContext, true);
                field.set(object.obj, val);
            } catch (PolyglotEngineException e) {
                throw HostInteropErrors.unsupportedTypeException(rawValue, e.e);
            }
        }
    }

    @GenerateUncached
    abstract static class IsListNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isListAccess()", allowUncached = true) boolean isListAccess) {
            assert receiver.getHostClassCache().isListAccess() == isListAccess;
            return isListAccess && receiver.obj instanceof List;
        }

    }

    @GenerateUncached
    abstract static class IsArrayNode extends Node {

        public abstract boolean execute(HostObject receiver);

        @Specialization
        public boolean doDefault(HostObject receiver,
                        @Cached(value = "receiver.getHostClassCache().isArrayAccess()", allowUncached = true) boolean isArrayAccess) {
            assert receiver.getHostClassCache().isArrayAccess() == isArrayAccess;
            return isArrayAccess && receiver.obj != null && receiver.obj.getClass().isArray();
        }

    }
}
