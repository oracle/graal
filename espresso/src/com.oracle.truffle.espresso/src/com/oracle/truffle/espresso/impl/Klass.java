/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.runtime.staticobject.StaticObject.CLASS_TO_STATIC;
import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.impl.PackageTable.PackageEntry;
import com.oracle.truffle.espresso.jdwp.api.ClassStatusConstants;
import com.oracle.truffle.espresso.jdwp.api.JDWPConstantPool;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.InteropKlassesDispatch;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNodeGen;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethod;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethodNodeGen;
import com.oracle.truffle.espresso.nodes.interop.LookupFieldNode;
import com.oracle.truffle.espresso.nodes.interop.LookupFieldNodeGen;
import com.oracle.truffle.espresso.nodes.interop.OverLoadedMethodSelectorNode;
import com.oracle.truffle.espresso.nodes.interop.OverLoadedMethodSelectorNodeGen;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNodeFactory;
import com.oracle.truffle.espresso.nodes.interop.ToPrimitive;
import com.oracle.truffle.espresso.nodes.interop.ToPrimitiveFactory;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoFunction;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.InteropUtils;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.BaseInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.EspressoInterop;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

@ExportLibrary(InteropLibrary.class)
public abstract class Klass extends ContextAccessImpl implements ModifiersProvider, KlassRef, TruffleObject {

    // region Interop

    public static final String STATIC_TO_CLASS = "class";
    private static final String ARRAY = "array";
    private static final String COMPONENT = "component";
    private static final String SUPER = "super";

    @NonIdempotent
    static EspressoLanguage getLang(Node node) {
        return EspressoLanguage.get(node);
    }

    public static final byte UN_INITIALIZED = -1;
    public static final byte NOT_MAPPED = 0;
    public static final byte TYPE_MAPPED = 1;
    public static final byte INTERNAL_MAPPED = 2;
    public static final byte INTERFACE_MAPPED = 3;
    public static final byte INTERNAL_COLLECTION_MAPPED = 4;

    @CompilationFinal public byte typeConversionState = UN_INITIALIZED;

    @ExportMessage
    abstract static class IsMemberReadable {
        // Specialization prevents caching a node that would leak the context
        @Specialization(guards = "language.isShared()")
        static boolean doShared(Klass receiver, String member,
                        @CachedLibrary("receiver") InteropLibrary lib,
                        @Bind("getLang(lib)") @SuppressWarnings("unused") EspressoLanguage language) {
            return isMemberReadable(receiver, member, LookupFieldNodeGen.getUncached(), lib);
        }

        @Specialization
        static boolean isMemberReadable(Klass receiver, String member,
                        @Shared("lookupField") @Cached LookupFieldNode lookupField,
                        @CachedLibrary("receiver") InteropLibrary lib) {
            EspressoContext ctx = EspressoContext.get(lib);
            Field field = lookupField.execute(receiver, member, true);
            if (field != null) {
                return true;
            }
            if (lib.isMemberInvocable(receiver, member)) {
                return true;
            }
            if (STATIC_TO_CLASS.equals(member)) {
                return true;
            }
            if (CLASS_TO_STATIC.equals(member)) {
                return true;
            }
            if (ctx.getMeta()._void != receiver && ARRAY.equals(member)) {
                return true;
            }
            if (receiver.isArray() && COMPONENT.equals(member)) {
                return true;
            }
            if (receiver.getSuperKlass() != null && SUPER.equals(member)) {
                return true;
            }

            return false;
        }
    }

    @ExportMessage
    abstract static class ReadMember {
        // Specialization prevents caching a node that would leak the context
        @Specialization(guards = "language.isShared()")
        static Object doShared(Klass receiver, String member,
                        @CachedLibrary("receiver") InteropLibrary lib,
                        @Bind("getLang(lib)") @SuppressWarnings("unused") EspressoLanguage language) throws UnknownIdentifierException {
            return readMember(receiver, member, LookupFieldNodeGen.getUncached(), LookupDeclaredMethodNodeGen.getUncached(), BranchProfile.getUncached(), lib, language);
        }

        @Specialization
        static Object readMember(Klass receiver, String member,
                        @Shared("lookupField") @Cached LookupFieldNode lookupFieldNode,
                        @Shared("lookupMethod") @Cached LookupDeclaredMethod lookupMethod,
                        @Shared("error") @Cached BranchProfile error,
                        @CachedLibrary("receiver") InteropLibrary lib,
                        @Bind("getLang(lib)") @SuppressWarnings("unused") EspressoLanguage language) throws UnknownIdentifierException {
            EspressoContext ctx = EspressoContext.get(lib);
            Meta meta = ctx.getMeta();
            Field field = lookupFieldNode.execute(receiver, member, true);
            if (field != null) {
                Object result = field.get(receiver.tryInitializeAndGetStatics());
                if (result instanceof StaticObject) {
                    result = InteropUtils.unwrap(language, (StaticObject) result, meta);
                }
                return result;
            }
            try {
                Method[] candidates = lookupMethod.execute(receiver, member, true, true, -1 /*- skip */);
                if (candidates != null) {
                    if (candidates.length == 1) {
                        return EspressoFunction.createStaticInvocable(candidates[0]);
                    }
                }
            } catch (ArityException e) {
                /* ignore and continue */
            }

            // Klass<T>.class == Class<T>
            if (STATIC_TO_CLASS.equals(member)) {
                return receiver.mirror();
            }
            // Klass<T>.static == Klass<T>
            if (CLASS_TO_STATIC.equals(member)) {
                return receiver;
            }
            if (meta._void != receiver && ARRAY.equals(member)) {
                return receiver.array();
            }
            if (receiver.isArray() && COMPONENT.equals(member)) {
                return ((ArrayKlass) receiver).getComponentType();
            }
            if (receiver.getSuperKlass() != null && SUPER.equals(member)) {
                return receiver.getSuperKlass();
            }

            error.enter();
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    abstract static class IsMemberModifiable {
        // Specialization prevents caching a node that would leak the context
        @Specialization(guards = "language.isShared()")
        static boolean doShared(Klass receiver, String member,
                        @CachedLibrary("receiver") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("getLang(lib)") @SuppressWarnings("unused") EspressoLanguage language) {
            return isMemberModifiable(receiver, member, LookupFieldNodeGen.getUncached());
        }

        @Specialization
        static boolean isMemberModifiable(Klass receiver, String member,
                        @Shared("lookupField") @Cached LookupFieldNode lookupField) {
            Field field = lookupField.execute(receiver, member, true);
            return field != null && !field.isFinalFlagSet();
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    abstract static class WriteMember {
        // Specialization prevents caching a node that would leak the context
        @Specialization(guards = "language.isShared()")
        static void doShared(Klass receiver, String member, Object value,
                        @Shared("error") @Cached BranchProfile error,
                        @CachedLibrary("receiver") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("getLang(lib)") @SuppressWarnings("unused") EspressoLanguage language) throws UnknownIdentifierException, UnsupportedTypeException {
            writeMember(receiver, member, value, LookupFieldNodeGen.getUncached(), ToEspressoNodeFactory.DynamicToEspressoNodeGen.getUncached(), error);
        }

        @Specialization
        static void writeMember(Klass receiver, String member, Object value,
                        @Shared("lookupField") @Cached LookupFieldNode lookupFieldNode,
                        @Exclusive @Cached ToEspressoNode.DynamicToEspresso toEspressoNode,
                        @Shared("error") @Cached BranchProfile error) throws UnknownIdentifierException, UnsupportedTypeException {
            Field field = lookupFieldNode.execute(receiver, member, true);
            // Can only write to non-final fields.
            if (field != null && !field.isFinalFlagSet()) {
                Object espressoValue = toEspressoNode.execute(value, field.resolveTypeKlass());
                field.set(receiver.tryInitializeAndGetStatics(), espressoValue);
            } else {
                error.enter();
                throw UnknownIdentifierException.create(member);
            }
        }
    }

    @ExportMessage
    abstract static class IsMemberInvocable {
        // Specialization prevents caching a node that would leak the context
        @Specialization(guards = "language.isShared()")
        static boolean doShared(Klass receiver, String member,
                        @CachedLibrary("receiver") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("getLang(lib)") @SuppressWarnings("unused") EspressoLanguage language) {
            return isMemberInvocable(receiver, member, LookupDeclaredMethodNodeGen.getUncached());
        }

        @Specialization
        static boolean isMemberInvocable(Klass receiver, String member,
                        @Shared("lookupMethod") @Cached LookupDeclaredMethod lookupMethod) {
            return lookupMethod.isInvocable(receiver, member, true);
        }
    }

    @ExportMessage
    abstract static class InvokeMember {
        // Specialization prevents caching a node that would leak the context
        @Specialization(guards = "language.isShared()")
        static Object doShared(Klass receiver, String member, Object[] arguments,
                        @CachedLibrary("receiver") @SuppressWarnings("unused") InteropLibrary receiverInterop,
                        @Bind("getLang(receiverInterop)") @SuppressWarnings("unused") EspressoLanguage language) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
            return invokeMember(receiver, member, arguments, receiverInterop, LookupDeclaredMethodNodeGen.getUncached(), OverLoadedMethodSelectorNodeGen.getUncached(),
                            InvokeEspressoNodeGen.getUncached(),
                            ToEspressoNodeFactory.DynamicToEspressoNodeGen.getUncached());
        }

        @Specialization
        static Object invokeMember(Klass receiver, String member,
                        Object[] arguments,
                        @CachedLibrary("receiver") InteropLibrary receiverInterop,
                        @Cached LookupDeclaredMethod lookupMethod,
                        @Cached OverLoadedMethodSelectorNode overloadSelector,
                        @Exclusive @Cached InvokeEspressoNode invoke,
                        @Cached ToEspressoNode.DynamicToEspresso toEspressoNode)
                        throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
            if (!receiverInterop.isMemberInvocable(receiver, member)) {
                // Not invocable, no matter the arity or argument types.
                throw UnknownIdentifierException.create(member);
            }
            // The member (static method) may be invocable only for a certain arity and argument
            // types.
            Method[] candidates = lookupMethod.execute(receiver, member, true, true, arguments.length);
            if (candidates != null) {
                return EspressoInterop.invokeEspressoMethodHelper(null, member, arguments, overloadSelector, invoke, toEspressoNode, candidates);
            }
            // TODO(peterssen): The expected arity is not known, only that the given one is not
            // correct.
            throw ArityException.create(arguments.length + 1, -1, arguments.length);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean hasMembers() {
        return true;
    }

    @TruffleBoundary
    @ExportMessage
    final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        EconomicSet<String> members = EconomicSet.create();
        members.add(STATIC_TO_CLASS);
        members.add(CLASS_TO_STATIC);
        for (Method m : getDeclaredMethods()) {
            if (m.isStatic() && m.isPublic()) {
                members.add(m.getInteropString());
            }
        }
        if (getMeta()._void != this) {
            members.add(ARRAY);
        }
        if (isArray()) {
            members.add(COMPONENT);
        }
        if (getSuperKlass() != null) {
            members.add(SUPER);
        }

        for (Field f : getDeclaredFields()) {
            if (f.isPublic() && f.isStatic()) {
                members.add(f.getNameAsString());
            }
        }

        return new KeysArray<>(members.toArray(new String[members.size()]));
    }

    protected static boolean isObjectKlass(Klass receiver) {
        return receiver instanceof ObjectKlass;
    }

    @ExportMessage
    abstract static class IsInstantiable {
        @SuppressWarnings("unused")
        @Specialization(guards = "receiver.isPrimitive()")
        static boolean doPrimitive(Klass receiver) {
            return false;
        }

        @Specialization(guards = "isObjectKlass(receiver)")
        static boolean doObject(Klass receiver) {
            if (receiver.isAbstract()) {
                return false;
            }
            /* Check that the class has a public constructor */
            for (Method m : receiver.getDeclaredMethods()) {
                if (m.isPublic() && !m.isStatic() && m.getName().equals(Name._init_)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(guards = "receiver.isArray()")
        static boolean doArray(Klass receiver) {
            assert receiver.getElementalType().getJavaKind() != JavaKind.Void;
            return true;
        }
    }

    @ExportMessage
    abstract static class Instantiate {
        // Specialization prevents caching a node that would leak the context
        @Specialization(guards = "language.isShared()")
        static Object doShared(Klass receiver, Object[] args,
                        @CachedLibrary("receiver") @SuppressWarnings("unused") InteropLibrary receiverInterop,
                        @Bind("getLang(receiverInterop)") @SuppressWarnings("unused") EspressoLanguage language) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            if (receiver.isPrimitive()) {
                return doPrimitive(receiver, args);
            }
            if (isPrimitiveArray(receiver)) {
                return doPrimitiveArray(receiver, args, ToPrimitiveFactory.ToIntNodeGen.getUncached());
            }
            if (isReferenceArray(receiver)) {
                return doReferenceArray(receiver, args, ToPrimitiveFactory.ToIntNodeGen.getUncached());
            }
            if (isMultidimensionalArray(receiver)) {
                return doMultidimensionalArray(receiver, args, ToPrimitiveFactory.ToIntNodeGen.getUncached());
            }
            if (isObjectKlass(receiver)) {
                return doObject(receiver, args, receiverInterop, LookupDeclaredMethodNodeGen.getUncached(), OverLoadedMethodSelectorNodeGen.getUncached(), InvokeEspressoNodeGen.getUncached(),
                                ToEspressoNodeFactory.DynamicToEspressoNodeGen.getUncached());
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver.isPrimitive()")
        static Object doPrimitive(Klass receiver, Object[] arguments) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        private static int convertLength(Object argument, ToPrimitive.ToInt toInt) throws UnsupportedTypeException {
            int length = 0;
            try {
                length = (int) toInt.execute(argument);
            } catch (UnsupportedTypeException e) {
                throw UnsupportedTypeException.create(new Object[]{argument}, "Expected a single int");
            }
            if (length < 0) {
                throw UnsupportedTypeException.create(new Object[]{argument}, "Expected a non-negative length");
            }
            return length;
        }

        private static int getLength(Object[] arguments, ToPrimitive.ToInt toInt) throws UnsupportedTypeException, ArityException {
            if (arguments.length != 1) {
                throw ArityException.create(1, 1, arguments.length);
            }
            return convertLength(arguments[0], toInt);
        }

        protected static boolean isPrimitiveArray(Klass receiver) {
            return receiver instanceof ArrayKlass && ((ArrayKlass) receiver).getComponentType().isPrimitive();
        }

        /* 1-dimensional reference (non-primitive) array */
        protected static boolean isReferenceArray(Klass receiver) {
            return receiver instanceof ArrayKlass && ((ArrayKlass) receiver).getComponentType() instanceof ObjectKlass;
        }

        protected static boolean isMultidimensionalArray(Klass receiver) {
            return receiver instanceof ArrayKlass && ((ArrayKlass) receiver).getComponentType().isArray();
        }

        @Specialization(guards = "isPrimitiveArray(receiver)")
        static StaticObject doPrimitiveArray(Klass receiver, Object[] arguments,
                        @Cached ToPrimitive.ToInt toInt) throws ArityException, UnsupportedTypeException {
            ArrayKlass arrayKlass = (ArrayKlass) receiver;
            assert arrayKlass.getComponentType().getJavaKind() != JavaKind.Void;
            EspressoContext context = EspressoContext.get(toInt);
            int length = getLength(arguments, toInt);
            GuestAllocator.AllocationChecks.checkCanAllocateArray(context.getMeta(), length);
            return context.getAllocator().createNewPrimitiveArray(arrayKlass.getComponentType(), length);
        }

        @Specialization(guards = "isReferenceArray(receiver)")
        static StaticObject doReferenceArray(Klass receiver, Object[] arguments,
                        @Cached ToPrimitive.ToInt toInt) throws UnsupportedTypeException, ArityException {
            ArrayKlass arrayKlass = (ArrayKlass) receiver;
            EspressoContext context = EspressoContext.get(toInt);
            int length = getLength(arguments, toInt);
            GuestAllocator.AllocationChecks.checkCanAllocateArray(context.getMeta(), length);
            return context.getAllocator().createNewReferenceArray(arrayKlass.getComponentType(), length);
        }

        @Specialization(guards = "isMultidimensionalArray(receiver)")
        static StaticObject doMultidimensionalArray(Klass receiver, Object[] arguments,
                        @Cached ToPrimitive.ToInt toInt) throws ArityException, UnsupportedTypeException {
            ArrayKlass arrayKlass = (ArrayKlass) receiver;
            assert arrayKlass.getElementalType().getJavaKind() != JavaKind.Void;
            if (arrayKlass.getDimension() != arguments.length) {
                throw ArityException.create(arrayKlass.getDimension(), arrayKlass.getDimension(), arguments.length);
            }
            EspressoContext context = EspressoContext.get(toInt);
            int[] dimensions = new int[arguments.length];
            for (int i = 0; i < dimensions.length; ++i) {
                dimensions[i] = convertLength(arguments[i], toInt);
            }
            GuestAllocator.AllocationChecks.checkCanAllocateMultiArray(context.getMeta(), arrayKlass.getComponentType(), dimensions);
            return context.getAllocator().createNewMultiArray(arrayKlass.getComponentType(), dimensions);
        }

        static final String INIT_NAME = Name._init_.toString();

        @Specialization(guards = "isObjectKlass(receiver)")
        static Object doObject(Klass receiver, Object[] arguments,
                        @CachedLibrary("receiver") InteropLibrary receiverInterop,
                        @Shared("lookupMethod") @Cached LookupDeclaredMethod lookupMethod,
                        @Cached OverLoadedMethodSelectorNode overloadSelector,
                        @Exclusive @Cached InvokeEspressoNode invoke,
                        @Cached ToEspressoNode.DynamicToEspresso toEspressoNode) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            if (!receiverInterop.isInstantiable(receiver)) {
                throw UnsupportedMessageException.create();
            }
            ObjectKlass objectKlass = (ObjectKlass) receiver;
            Method[] initCandidates = lookupMethod.execute(receiver, INIT_NAME, true, false, arguments.length);
            if (initCandidates != null) {
                StaticObject instance = allocateNewInstance(EspressoContext.get(invoke), objectKlass);
                EspressoInterop.invokeEspressoMethodHelper(instance, INIT_NAME, arguments, overloadSelector, invoke, toEspressoNode, initCandidates);
                return instance;
            }
            // TODO(peterssen): We don't know the expected arity of this method, only that the given
            // arity is incorrect.
            throw ArityException.create(arguments.length + 1, -1, arguments.length);
        }

        private static StaticObject allocateNewInstance(EspressoContext context, ObjectKlass objectKlass) {
            GuestAllocator.AllocationChecks.checkCanAllocateNewReference(context.getMeta(), objectKlass, false);
            return context.getAllocator().createNew(objectKlass);
        }
    }

    // region ### Meta-objects

    @ExportMessage
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage
    public Object getMetaQualifiedName() {
        assert isMetaObject();
        return getTypeName();
    }

    @ExportMessage
    Object getMetaSimpleName() {
        assert isMetaObject();
        assert getContext().isInitialized();
        return getMeta().java_lang_Class_getSimpleName.invokeDirect(mirror());
    }

    @ExportMessage
    boolean isMetaInstance(Object instance) {
        return instance instanceof StaticObject && instanceOf((StaticObject) instance, this);
    }

    @ExportMessage
    boolean hasMetaParents() {
        if (isPrimitive()) {
            return false;
        }
        if (isInterface()) {
            return getSuperInterfaces().length > 0;
        }
        return this != getMeta().java_lang_Object;
    }

    @ExportMessage
    Object getMetaParents() throws UnsupportedMessageException {
        if (hasMetaParents()) {
            Klass[] result;
            if (isInterface()) {
                ObjectKlass[] superInterfaces = getSuperInterfaces();
                result = new Klass[superInterfaces.length];

                for (int i = 0; i < superInterfaces.length; i++) {
                    result[i] = superInterfaces[i];
                }
            } else {
                Klass superKlass = getSuperKlass();
                Klass[] superInterfaces = getSuperInterfaces();
                result = new Klass[superInterfaces.length + 1];
                // put the super class first in array
                result[0] = superKlass;

                for (int i = 0; i < superInterfaces.length; i++) {
                    result[i + 1] = superInterfaces[i];
                }
            }
            return new KeysArray<>(result);
        }
        throw UnsupportedMessageException.create();
    }

    // endregion ### Meta-objects

    // region ### Identity/hashCode

    @ExportMessage
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doKlass(Klass receiver, Klass other) {
            return receiver == other ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(@SuppressWarnings("unused") Klass receiver, @SuppressWarnings("unused") Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    int identityHashCode() {
        // In unit tests, Truffle performs additional sanity checks, this assert causes stack
        // overflow.
        // assert InteropLibrary.getUncached().hasIdentity(this);
        return VM.JVM_IHashCode(mirror(), null /*- path where language is needed is never reached through here. */);
    }

    // endregion ### Identity/hashCode

    public Class<?> getDispatch() {
        Class<?> result = dispatch;
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (getMeta().getContext().metaInitialized()) {
                result = getMeta().resolveDispatch(this);
                dispatch = result;
                dispatchId = InteropKlassesDispatch.dispatchToId(result);
            } else {
                /*
                 * Meta is not fully initialized: return the generic interop, without updating the
                 * dispatch cache. This is fine, as we are not expecting any meaningful interop
                 * until context is fully initialized.
                 */
                if (isPrimitive()) {
                    return BaseInterop.class;
                }
                return EspressoInterop.class;
            }
        }
        return result;
    }

    public int getDispatchId() {
        int result = dispatchId;
        if (result == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (getMeta().getContext().metaInitialized()) {
                dispatch = getMeta().resolveDispatch(this);
                result = dispatchId = InteropKlassesDispatch.dispatchToId(dispatch);
            } else {
                /*
                 * Meta is not fully initialized: return the generic interop, without updating the
                 * dispatch cache. This is fine, as we are not expecting any meaningful interop
                 * until context is fully initialized.
                 */
                if (isPrimitive()) {
                    return InteropKlassesDispatch.BASE_INTEROP_ID;
                }
                return InteropKlassesDispatch.ESPRESSO_INTEROP_ID;
            }
        }
        return result;
    }

    // endregion Interop

    // Threshold for using binary search instead of linear search for interface lookup.
    private static final int LINEAR_SEARCH_THRESHOLD = 8;

    static final Comparator<Klass> KLASS_ID_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(Klass k1, Klass k2) {
            return Long.compare(k1.id, k2.id);
        }
    };

    static final Comparator<ObjectKlass.KlassVersion> KLASS_VERSION_ID_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(ObjectKlass.KlassVersion k1, ObjectKlass.KlassVersion k2) {
            return Long.compare(k1.getKlass().getId(), k2.getKlass().getId());
        }
    };

    public static final Klass[] EMPTY_ARRAY = new Klass[0];

    static final DebugCounter KLASS_LOOKUP_METHOD_COUNT = DebugCounter.create("Klass.lookupMethod call count");
    static final DebugCounter KLASS_LOOKUP_FIELD_COUNT = DebugCounter.create("Klass.lookupField call count");
    static final DebugCounter KLASS_LOOKUP_DECLARED_METHOD_COUNT = DebugCounter.create("Klass.lookupDeclaredMethod call count");
    static final DebugCounter KLASS_LOOKUP_DECLARED_FIELD_COUNT = DebugCounter.create("Klass.lookupDeclaredField call count");

    protected Symbol<Name> name;
    protected Symbol<Type> type;

    private final long id;

    @CompilationFinal //
    private ArrayKlass arrayKlass;

    @CompilationFinal //
    private StaticObject espressoClass;

    @CompilationFinal //
    private Class<?> dispatch;
    @CompilationFinal //
    private int dispatchId = -1;

    @CompilationFinal //
    private StaticObject typeName;

    protected Object prepareThread;

    // Raw modifiers provided by the VM.
    private final int modifiers;

    protected static boolean hasFinalInstanceField(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                int modifiers = f.getModifiers();
                if (!Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    static {
        // Ensures that the 'arrayKlass' field can be non-volatile. This uses
        // "Unsafe Local DCL + Safe Singleton" as described in
        // https://shipilev.net/blog/2014/safe-public-construction
        assert hasFinalInstanceField(ArrayKlass.class);
    }

    /**
     * A class or interface C is accessible to a class or interface D if and only if either of the
     * following is true:
     * <h3>Java 8
     * <ul>
     * <li>C is public.
     * <li>C and D are members of the same run-time package (&sect;5.3).
     * </ul>
     * <h3>Java 11
     * <ul>
     * <li>C is public, and a member of the same run-time module as D (&sect;5.3.6).
     * <li>C is public, and a member of a different run-time module than D, and C's run-time module
     * is read by D's run-time module, and C's run-time module exports C's run-time package to D's
     * run-time module.
     * <li>C is not public, and C and D are members of the same run-time package.
     * </ul>
     */
    public static boolean checkAccess(Klass klass, ObjectKlass accessingKlass, boolean ignoreMagicAccessor) {
        if (accessingKlass == null) {
            return true;
        }
        if (klass == accessingKlass) {
            return true;
        }
        if (klass.isPrimitive()) {
            return true;
        }
        EspressoContext context = accessingKlass.getContext();
        if (context.getJavaVersion().modulesEnabled()) {
            if (klass.sameRuntimePackage(accessingKlass)) {
                return true;
            }
            if (klass.isPublic()) {
                if (doModuleAccessChecks(klass, accessingKlass, context)) {
                    return true;
                }
            }
        } else {
            if (klass.isPublic() || klass.sameRuntimePackage(accessingKlass)) {
                return true;
            }
        }

        if (ignoreMagicAccessor) {
            /*
             * Prevents any class inheriting from MagicAccessorImpl to have access to
             * MagicAccessorImpl just because it implements MagicAccessorImpl.
             *
             * Only generated accessors in the {sun|jdk.internal}.reflect package, defined by
             * {sun|jdk.internal}.reflect.DelegatingClassLoader(s) have access to MagicAccessorImpl.
             */
            ObjectKlass magicAccessorImpl = context.getMeta().sun_reflect_MagicAccessorImpl;
            return !StaticObject.isNull(accessingKlass.getDefiningClassLoader()) &&
                            context.getMeta().sun_reflect_DelegatingClassLoader.equals(accessingKlass.getDefiningClassLoader().getKlass()) &&
                            magicAccessorImpl.getRuntimePackage().equals(accessingKlass.getRuntimePackage()) &&
                            magicAccessorImpl.isAssignableFrom(accessingKlass);
        }

        return (context.getMeta().sun_reflect_MagicAccessorImpl.isAssignableFrom(accessingKlass));
    }

    public static boolean doModuleAccessChecks(Klass klass, ObjectKlass accessingKlass, EspressoContext context) {
        ModuleEntry moduleFrom = accessingKlass.module();
        ModuleEntry moduleTo = klass.module();
        if (moduleFrom == moduleTo) {
            return true;
        }
        /*
         * Acceptable access to a type in an unnamed module. Note that since unnamed modules can
         * read all unnamed modules, this also handles the case where module_from is also unnamed
         * but in a different class loader.
         */
        if (!moduleTo.isNamed() && (moduleFrom.canReadAllUnnamed() || moduleFrom.canRead(moduleTo, context))) {
            return true;
        }
        // Establish readability, check if moduleFrom is allowed to read moduleTo.
        if (!moduleFrom.canRead(moduleTo, context)) {
            return false;
        }
        // Access is allowed if moduleTo is open, i.e. all its packages are unqualifiedly
        // exported
        if (moduleTo.isOpen()) {
            return true;
        }

        PackageEntry packageTo = klass.packageEntry();
        // TODO: obtain packageTo table read lock.
        /*
         * Once readability is established, if module_to exports T unqualifiedly, (to all modules),
         * then whether module_from is in the unnamed module or not does not matter, access is
         * allowed.
         */
        if (packageTo.isUnqualifiedExported()) {
            return true;
        }
        /*-
         * Access is allowed if both 1 & 2 hold:
         *   1. Readability, module_from can read module_to (established above).
         *   2. Either module_to exports T to module_from qualifiedly.
         *      or
         *      module_to exports T to all unnamed modules and module_from is unnamed.
         *      or
         *      module_to exports T unqualifiedly to all modules (checked above).
         */
        return packageTo.isQualifiedExportTo(moduleFrom);
    }

    public ObjectKlass[] getSuperInterfaces() {
        return ObjectKlass.EMPTY_ARRAY;
    }

    Klass(EspressoContext context, Symbol<Name> name, Symbol<Type> type, int modifiers) {
        this(context, name, type, modifiers, -1);
    }

    Klass(EspressoContext context, Symbol<Name> name, Symbol<Type> type, int modifiers, long possibleID) {
        super(context);
        this.name = name;
        this.type = type;
        this.id = (possibleID >= 0) ? possibleID : context.getClassLoadingEnv().getNewKlassId();
        this.modifiers = modifiers;
        this.runtimePackage = initRuntimePackage();
    }

    @Override
    public abstract @JavaType(ClassLoader.class) StaticObject getDefiningClassLoader();

    public abstract ConstantPool getConstantPool();

    public final JavaKind getJavaKind() {
        return (this instanceof PrimitiveKlass)
                        ? ((PrimitiveKlass) this).getPrimitiveJavaKind()
                        : JavaKind.Object;
    }

    @Override
    public final boolean isArray() {
        return this instanceof ArrayKlass;
    }

    @Override
    @Idempotent
    public final boolean isInterface() {
        // conflict between ModifiersProvider and KlassRef interfaces,
        // so chose the default implementation in ModifiersProvider.
        return ModifiersProvider.super.isInterface();
    }

    /**
     * Returns the guest {@link Class} object associated with this {@link Klass} instance.
     */
    public final @JavaType(Class.class) StaticObject mirror() {
        StaticObject result = this.espressoClass;
        assert result != null;
        assert getMeta().java_lang_Class != null;
        return result;
    }

    @SuppressFBWarnings(value = "DC_DOUBLECHECK", //
                    justification = "espressoClass is deliberately non-volatile since it uses \"Unsafe Local DCL + Safe Singleton\" as described in https://shipilev.net/blog/2014/safe-public-construction\n" +
                                    "A static hasFinalInstanceField(StaticObject.class) assertion ensures correctness.")
    public final StaticObject initializeEspressoClass() {
        CompilerAsserts.neverPartOfCompilation();
        StaticObject result = this.espressoClass;
        if (result == null) {
            synchronized (this) {
                result = this.espressoClass;
                if (result == null) {
                    this.espressoClass = result = getAllocator().createClass(this);
                }
            }
        }
        return result;
    }

    private @JavaType(String.class) StaticObject computeTypeName() {
        CompilerAsserts.neverPartOfCompilation();
        if (!isArray()) {
            return getVM().JVM_GetClassName(this.mirror());
        }
        // Cannot call Class.getTypeName safely during context initialization, so it's computed here
        // manually.
        StaticObject elementalName = getVM().JVM_GetClassName(getElementalType().mirror());
        int dimensions = ((ArrayKlass) this).getDimension();
        String result = Meta.toHostStringStatic(elementalName) + "[]".repeat(dimensions);
        return getMeta().toGuestString(result);
    }

    public final @JavaType(String.class) StaticObject getTypeName() {
        if (typeName == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            typeName = computeTypeName();
        }
        assert checkTypeName(typeName);
        return typeName;
    }

    @TruffleBoundary
    private boolean checkTypeName(@JavaType(String.class) StaticObject computedTypeName) {
        if (!getContext().isInitialized()) {
            // Skip check: cannot safely call Class.getTypeName.
            return true;
        }
        StaticObject expected = (StaticObject) getMeta().java_lang_Class_getTypeName.invokeDirect(mirror());
        return getMeta().toHostString(computedTypeName).equals(getMeta().toHostString(expected));
    }

    /**
     * Gets the array class type representing an array with elements of this type.
     *
     * This method is equivalent to {@link Klass#getArrayClass()}.
     */
    public final ArrayKlass array() {
        return getArrayClass();
    }

    /**
     * Gets the array class type representing an array with elements of this type.
     */
    public final ArrayKlass getArrayClass() {
        ArrayKlass result = this.arrayKlass;
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            result = createArrayKlass();
        }
        return result;
    }

    private synchronized ArrayKlass createArrayKlass() {
        CompilerAsserts.neverPartOfCompilation();
        ArrayKlass result = this.arrayKlass;
        if (result == null && Type._void != getType()) { // ignore void[]
            this.arrayKlass = result = new ArrayKlass(this);
        }
        return result;
    }

    @Override
    public ArrayKlass getArrayClass(int dimensions) {
        assert dimensions > 0;
        ArrayKlass array = array();

        // Careful with of impossible void[].
        if (array == null) {
            return null;
        }

        for (int i = 1; i < dimensions; ++i) {
            array = array.getArrayClass();
        }
        return array;
    }

    @Override
    public final boolean equals(Object that) {
        return this == that;
    }

    @Override
    public final int hashCode() {
        return getType().hashCode();
    }

    @HostCompilerDirectives.InliningCutoff
    public final StaticObject tryInitializeAndGetStatics() {
        safeInitialize();
        return getStatics();
    }

    public final StaticObject getStatics() {
        if (this instanceof ObjectKlass) {
            return ((ObjectKlass) this).getStaticsImpl();
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Primitives/arrays do not have static fields");
    }

    /**
     * Checks whether this type is an instance class.
     *
     * @return {@code true} if this type is an instance class
     */
    public final boolean isInstanceClass() {
        return (this instanceof ObjectKlass) && !isInterface();
    }

    /**
     * Checks whether this type is primitive.
     *
     * @return {@code true} if this type is primitive
     */
    @Override
    public final boolean isPrimitive() {
        return this instanceof PrimitiveKlass;
    }

    /*
     * The setting of the final bit for types is a bit confusing since arrays are marked as final.
     * This method provides a semantically equivalent test that appropriate for types.
     */
    public boolean hasNoSubtypes() {
        return getElementalType().isFinalFlagSet();
    }

    @Override
    public final boolean isFinalFlagSet() {
        /*
         * HotSpot's Class Hierarchy Analysis does not allow inlining invoke interface pointing to
         * never overriden default interface methods. We cirumvent this CHA limitation here by using
         * an invokespecial, which is inlinable.
         */
        return ModifiersProvider.super.isFinalFlagSet() /* || isLeafAssumption() */;
    }

    /**
     * Checks whether this type is initialized. If a type is initialized it implies that it was
     * linked and that the static initializer has run.
     *
     * @return {@code true} if this type is initialized
     */
    public final boolean isInitialized() {
        return !(this instanceof ObjectKlass) || ((ObjectKlass) this).isInitializedImpl();
    }

    public final boolean isInitializedOrInitializing() {
        if (!(this instanceof ObjectKlass)) {
            return true; // primitives or arrays are considered initialized.
        }
        return ((ObjectKlass) this).isInitializingOrInitializedImpl();
    }

    /**
     * Initializes this type.
     */
    public final void initialize() {
        if (this instanceof ObjectKlass) {
            ((ObjectKlass) this).initializeImpl();
        }
        // Array and primitive classes do not require initialization.
    }

    public void ensureLinked() {
        /* nop */
    }

    /**
     * Determines if this type is either the same as, or is a superclass or superinterface of, the
     * type represented by the specified parameter. This method is identical to
     * {@link Class#isAssignableFrom(Class)} in terms of the value return for this type.
     * <p>
     * Fast check for Object types (as opposed to interface types) -> do not need to walk the entire
     * class hierarchy.
     * <p>
     * Interface check is still slow, though.
     */
    public final boolean isAssignableFrom(Klass other) {
        if (this == other) {
            return true;
        }
        if (this.isPrimitive() || other.isPrimitive()) {
            // Reference equality is enough within the same context.
            assert this.getContext() == other.getContext();
            assert this != other;
            return false;
        }
        if (this.isArray()) {
            if (other.isArray()) {
                return ((ArrayKlass) this).arrayTypeChecks((ArrayKlass) other);
            }
        } else {
            if (this.isFinalFlagSet()) {
                assert this != other;
                return false;
            }
        }
        if (Modifier.isInterface(getModifiers())) {
            return checkInterfaceSubclassing(other);
        }
        return checkOrdinaryClassSubclassing(other);
    }

    /**
     * Performs type checking for non-interface, non-array classes.
     *
     * @param other the class whose type is to be checked against {@code this}
     * @return true if {@code other} is a subclass of {@code this}
     */
    public boolean checkOrdinaryClassSubclassing(Klass other) {
        int depth = getHierarchyDepth();
        return other.getHierarchyDepth() >= depth && other.getSuperTypes()[depth] == this;
    }

    /**
     * Performs type checking for interface classes.
     *
     * @param other the class whose type is to be checked against {@code this}
     * @return true if {@code this} is a super interface of {@code other}
     */
    public boolean checkInterfaceSubclassing(Klass other) {
        ObjectKlass.KlassVersion[] interfaces = other.getTransitiveInterfacesList();
        return fastLookup(this, interfaces) >= 0;
    }

    public final long getId() {
        return id;
    }

    public final Klass findLeastCommonAncestor(Klass other) {
        if (isPrimitive() || other.isPrimitive()) {
            if (this == other) {
                return this;
            }
            return null;
        }
        Klass[] thisHierarchy = getSuperTypes();
        Klass[] otherHierarchy = other.getSuperTypes();
        for (int i = Math.min(getHierarchyDepth(), other.getHierarchyDepth()); i >= 0; i--) {
            if (thisHierarchy[i] == otherHierarchy[i]) {
                return thisHierarchy[i];
            }
        }
        throw EspressoError.shouldNotReachHere("Klasses should be either primitives, or have j.l.Object as common supertype.");
    }

    /**
     * Returns the {@link Klass} object representing the host class of this VM anonymous class (as
     * opposed to the unrelated concept specified by {@link Class#isAnonymousClass()}) or
     * {@code null} if this object does not represent a VM anonymous class.
     */
    public final ObjectKlass getHostClass() {
        return (this instanceof ObjectKlass)
                        ? ((ObjectKlass) this).getHostClassImpl()
                        : null;
    }

    /**
     * Returns {@code true} if the type is an anonymous class.
     */
    public final boolean isAnonymous() {
        return getHostClass() != null;
    }

    /**
     * Returns {@code true} if this represents a hidden class.
     */
    public final boolean isHidden() {
        return (getModifiers() & Constants.ACC_IS_HIDDEN_CLASS) != 0;
    }

    /**
     * Returns true if this type is exactly the type {@link java.lang.Object}.
     */
    public boolean isJavaLangObject() {
        // Removed assertion due to https://bugs.eclipse.org/bugs/show_bug.cgi?id=434442
        return getSuperKlass() == null && !isInterface() && getJavaKind() == JavaKind.Object;
    }

    /**
     * Gets the super class of this type. If this type represents either the {@code Object} class,
     * an interface, a primitive type, or void, then null is returned. If this object represents an
     * array class then the type object representing the {@code Object} class is returned.
     */
    public ObjectKlass getSuperKlass() {
        return null;
    }

    /**
     * Gets the interfaces implemented or extended by this type. This method is analogous to
     * {@link Class#getInterfaces()} and as such, only returns the interfaces directly implemented
     * or extended by this type.
     */
    @Override
    public Klass[] getImplementedInterfaces() {
        return getSuperInterfaces();
    }

    public abstract Klass getElementalType();

    /**
     * Returns {@code true} if the type is a local type.
     */
    public abstract boolean isLocal();

    /**
     * Returns {@code true} if the type is a member type.
     */
    public abstract boolean isMember();

    /**
     * Returns the enclosing type of this type, if it exists, or {@code null}.
     */
    public abstract Klass getEnclosingType();

    /**
     * Returns an array reflecting all the constructors declared by this type. This method is
     * similar to {@link Class#getDeclaredConstructors()} in terms of returned constructors.
     */
    public abstract Method[] getDeclaredConstructors();

    /**
     * Returns an array reflecting all the methods declared by this type. This method is similar to
     * {@link Class#getDeclaredMethods()} in terms of returned methods.
     */
    public abstract Method[] getDeclaredMethods();

    /**
     * Returns a version-specific array reflecting all the methods declared by this type. This
     * method is similar to {@link Class#getDeclaredMethods()} in terms of returned methods.
     */
    public abstract Method.MethodVersion[] getDeclaredMethodVersions();

    /**
     * Returns an array reflecting all the methods declared by this type. This method is similar to
     * {@link Class#getDeclaredMethods()} in terms of returned methods.
     */
    @Override
    public abstract MethodRef[] getDeclaredMethodRefs();

    /**
     * Returns an array reflecting all the fields declared by this type. This method is similar to
     * {@link Class#getDeclaredFields()} in terms of returned fields.
     */
    @Override
    public abstract Field[] getDeclaredFields();

    /**
     * Returns the {@code <clinit>} method for this class if there is one.
     */
    public Method getClassInitializer() {
        Method clinit = lookupDeclaredMethod(Name._clinit_, Signature._void);
        if (clinit != null && clinit.isStatic()) {
            return clinit;
        }
        return null;
    }

    public final Symbol<Type> getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Klass<" + getType() + ">";
    }

    // region Meta.Klass

    public void safeInitialize() {
        try {
            initialize();
        } catch (EspressoException e) {
            throw initializationFailed(e);
        }
    }

    @HostCompilerDirectives.InliningCutoff
    protected final RuntimeException initializationFailed(EspressoException e) {
        StaticObject cause = e.getGuestException();
        Meta meta = getMeta();
        if (InterpreterToVM.instanceOf(cause, meta.java_lang_Error)) {
            throw e;
        } else {
            throw throwExceptionInInitializerError(meta, cause);
        }
    }

    @TruffleBoundary
    private static EspressoException throwExceptionInInitializerError(Meta meta, StaticObject cause) {
        throw meta.throwExceptionWithCause(meta.java_lang_ExceptionInInitializerError, cause);
    }

    public final Klass getSupertype() {
        if (isPrimitive()) {
            return null;
        }
        if (isArray()) {
            Klass component = ((ArrayKlass) this).getComponentType();
            if (this == getMeta().java_lang_Object.array() || component.isPrimitive()) {
                return getMeta().java_lang_Object;
            }
            return component.getSupertype().array();
        }
        if (isInterface()) {
            return getMeta().java_lang_Object;
        }
        return getSuperKlass();
    }

    public final boolean isPrimaryType() {
        assert !isPrimitive();
        if (isArray()) {
            return getElementalType().isPrimaryType();
        }
        return !isInterface();
    }

    // index 0 is Object, index hierarchyDepth is this
    protected abstract Klass[] getSuperTypes();

    protected abstract int getHierarchyDepth();

    protected abstract ObjectKlass.KlassVersion[] getTransitiveInterfacesList();

    @TruffleBoundary
    public StaticObject allocateReferenceArray(int length) {
        Meta meta = getMeta(); // TODO: pass constant meta
        GuestAllocator.AllocationChecks.checkCanAllocateArray(meta, length);
        return meta.getAllocator().createNewReferenceArray(this, length);
    }

    @TruffleBoundary
    public StaticObject allocateReferenceArray(int length, IntFunction<StaticObject> generator) {
        // TODO(peterssen): Store check is missing.
        Meta meta = getMeta(); // TODO: pass constant meta
        StaticObject[] array = new StaticObject[length];
        for (int i = 0; i < array.length; ++i) {
            array[i] = generator.apply(i);
        }
        return meta.getAllocator().wrapArrayAs(getArrayClass(), array);
    }

    // region Lookup

    public enum LookupMode {
        ALL(true, true),
        INSTANCE_ONLY(true, false),
        STATIC_ONLY(false, true);

        private final boolean instances;
        private final boolean statics;

        LookupMode(boolean instances, boolean statics) {
            this.instances = instances;
            this.statics = statics;
        }

        public boolean include(Member<?> m) {
            if (m == null) {
                return false;
            }
            if (statics && m.isStatic()) {
                return true;
            }
            if (instances && !m.isStatic()) {
                return true;
            }
            return false;
        }
    }

    public final Field requireDeclaredField(Symbol<Name> fieldName, Symbol<Type> fieldType) {
        Field obj = lookupDeclaredField(fieldName, fieldType);
        if (obj == null) {
            throw EspressoError.shouldNotReachHere("Missing field: " + fieldName + ": " + fieldType + " in " + this);
        }
        return obj;
    }

    @ExplodeLoop
    public final Field lookupDeclaredField(Symbol<Name> fieldName, Symbol<Type> fieldType) {
        KLASS_LOOKUP_DECLARED_FIELD_COUNT.inc();
        // TODO(peterssen): Improve lookup performance.
        for (Field field : getDeclaredFields()) {
            if (fieldName.equals(field.getName()) && fieldType.equals(field.getType())) {
                return field;
            }
        }
        return null;
    }

    public final Field lookupField(Symbol<Name> fieldName, Symbol<Type> fieldType) {
        return lookupField(fieldName, fieldType, LookupMode.ALL);
    }

    /*
     * 5.4.3.2. Field Resolution:
     *
     * When resolving a field reference, field resolution first attempts to look up the referenced
     * field in C and its superclasses:
     *
     * 1) If C declares a field with the name and descriptor specified by the field reference, field
     * lookup succeeds. The declared field is the result of the field lookup.
     *
     * 2) Otherwise, field lookup is applied recursively to the direct superinterfaces of the
     * specified class or interface C.
     *
     * 3) Otherwise, if C has a superclass S, field lookup is applied recursively to S.
     *
     * 4) Otherwise, field lookup fails.
     *
     *
     */
    public final Field lookupField(Symbol<Name> fieldName, Symbol<Type> fieldType, LookupMode mode) {
        KLASS_LOOKUP_FIELD_COUNT.inc();
        // TODO(peterssen): Improve lookup performance.

        Field field = lookupDeclaredField(fieldName, fieldType);
        if (mode.include(field)) {
            return field;
        }

        for (ObjectKlass superI : getSuperInterfaces()) {
            field = superI.lookupField(fieldName, fieldType, mode);
            if (mode.include(field)) {
                return field;
            }
        }

        if (getSuperKlass() != null) {
            return getSuperKlass().lookupField(fieldName, fieldType, mode);
        }

        return null;
    }

    public final Field lookupFieldTable(int slot) {
        if (this instanceof ObjectKlass) {
            return ((ObjectKlass) this).lookupFieldTableImpl(slot);
        }
        return null;
    }

    public final Field lookupStaticFieldTable(int slot) {
        if (this instanceof ObjectKlass) {
            return ((ObjectKlass) this).lookupStaticFieldTableImpl(slot);
        }
        // Array nor primitives have static fields.
        return null;
    }

    public final Method requireMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        Method obj = lookupMethod(methodName, signature);
        if (obj == null) {
            throw EspressoError.shouldNotReachHere("Missing method: " + methodName + ": " + signature + " starting at " + this);
        }
        return obj;
    }

    public final Method requireDeclaredMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        Method obj = lookupDeclaredMethod(methodName, signature);
        if (obj == null) {
            throw EspressoError.shouldNotReachHere("Missing method: " + methodName + ": " + signature + " in " + this);
        }
        return obj;
    }

    public final Method lookupDeclaredMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        return lookupDeclaredMethod(methodName, signature, LookupMode.ALL);
    }

    @ExplodeLoop
    public final Method lookupDeclaredMethod(Symbol<Name> methodName, Symbol<Signature> signature, LookupMode lookupMode) {
        KLASS_LOOKUP_DECLARED_METHOD_COUNT.inc();
        // TODO(peterssen): Improve lookup performance.
        for (Method method : getDeclaredMethods()) {
            if (lookupMode.include(method)) {
                if (methodName.equals(method.getName()) && signature.equals(method.getRawSignature())) {
                    return method;
                }
            }
        }
        return null;
    }

    private static <T> boolean isSorted(T[] array, Comparator<T> comparator) {
        for (int i = 1; i < array.length; ++i) {
            if (comparator.compare(array[i - 1], array[i]) > 0) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary(allowInlining = true)
    protected static int fastLookupBoundary(Klass target, ObjectKlass.KlassVersion[] klasses) {
        return fastLookupImpl(target, klasses);
    }

    protected static int fastLookup(Klass target, ObjectKlass.KlassVersion[] klasses) {
        if (!CompilerDirectives.isPartialEvaluationConstant(klasses)) {
            return fastLookupBoundary(target, klasses);
        }
        // PE-friendly.
        CompilerAsserts.partialEvaluationConstant(klasses);
        return fastLookupImpl(target, klasses);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    protected static int fastLookupImpl(Klass target, ObjectKlass.KlassVersion[] klasses) {
        assert isSorted(klasses, KLASS_VERSION_ID_COMPARATOR);
        if (klasses.length <= LINEAR_SEARCH_THRESHOLD) {
            for (int i = 0; i < klasses.length; i++) {
                if (klasses[i].getKlass() == target) {
                    return i;
                }
            }
        } else {
            int lo = 0;
            int hi = klasses.length - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int cmp = KLASS_ID_COMPARATOR.compare(target, klasses[mid].getKlass());
                if (cmp < 0) {
                    hi = mid - 1;
                } else if (cmp > 0) {
                    lo = mid + 1;
                } else {
                    return mid;
                }
            }
        }
        return -1; // not found
    }

    /**
     * Give the accessing klass if there is a chance the method to be resolved is a method handle
     * intrinsics.
     */
    public abstract Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, LookupMode lookupMode);

    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        return lookupMethod(methodName, signature, LookupMode.ALL);
    }

    public final Method vtableLookup(int vtableIndex) {
        if (this instanceof ObjectKlass) {
            return ((ObjectKlass) this).vtableLookupImpl(vtableIndex);
        } else if (this instanceof ArrayKlass) {
            assert getMeta().java_lang_Object == getSuperClass();
            return getMeta().java_lang_Object.vtableLookup(vtableIndex);
        }
        // Unreachable?
        assert this instanceof PrimitiveKlass;
        return null;
    }

    public Method lookupPolysigMethod(Symbol<Name> methodName, Symbol<Signature> signature, LookupMode lookupMode) {
        Method m = lookupPolysignatureDeclaredMethod(methodName, lookupMode);
        if (m != null) {
            return findMethodHandleIntrinsic(m, signature);
        }
        return null;
    }

    public Method lookupPolysignatureDeclaredMethod(Symbol<Name> methodName, LookupMode lookupMode) {
        for (Method m : getDeclaredMethods()) {
            if (lookupMode.include(m)) {
                if (m.getName() == methodName && m.isSignaturePolymorphicDeclared()) {
                    return m;
                }
            }
        }
        return null;
    }

    @TruffleBoundary
    private Method findMethodHandleIntrinsic(Method m,
                    Symbol<Signature> signature) {
        assert m.isSignaturePolymorphicDeclared();
        MethodHandleIntrinsics.PolySigIntrinsics iid = MethodHandleIntrinsics.getId(m);
        Symbol<Signature> sig = signature;
        if (iid.isStaticPolymorphicSignature()) {
            sig = getSignatures().toBasic(signature, true);
        }
        return m.findIntrinsic(sig);
    }

    /**
     * Returns the access flags provided by the .class file, e.g. ignores inner class access flags.
     */
    @Override
    public final int getModifiers() {
        return getModifiers(getContext());
    }

    public final int getModifiers(EspressoContext context) {
        if (this instanceof ObjectKlass && context.advancedRedefinitionEnabled()) {
            // getKlassVersion().getModifiers() introduces a ~10%
            // perf hit on some benchmarks, so put behind a check
            return getRedefinitionAwareModifiers();
        } else {
            return modifiers;
        }
    }

    public int getRedefinitionAwareModifiers() {
        return getModifiers();
    }

    /**
     * Returns the modifiers for the guest Class, it takes into account inner classes which are
     * public at the JVM level, but protected/private at the Java level.
     */
    public abstract int getClassModifiers();

    @TruffleBoundary
    public final StaticObject allocateInstance() {
        return allocateInstance(getContext()); // May not be constant
    }

    public final StaticObject allocateInstance(EspressoContext ctx) {
        assert this instanceof ObjectKlass;
        GuestAllocator.AllocationChecks.checkCanAllocateNewReference(ctx.getMeta(), this, false);
        return ctx.getAllocator().createNew((ObjectKlass) this);
    }

    @CompilationFinal private Symbol<Name> runtimePackage;

    private Symbol<Name> initRuntimePackage() {
        ByteSequence hostPkgName = Types.getRuntimePackage(type);
        return getNames().getOrCreate(hostPkgName);
    }

    public Symbol<Name> getRuntimePackage() {
        return runtimePackage;
    }

    public abstract ModuleEntry module();

    public abstract PackageEntry packageEntry();

    public final boolean inUnnamedPackage() {
        return packageEntry() == null;
    }

    public Symbol<Name> getName() {
        return name;
    }

    public String getExternalName() {
        // Conversion from internal form.
        String externalName = MetaUtil.internalNameToJava(type.toString(), true, true);

        // Reflection relies on anonymous classes including a '/' on the name, to avoid generating
        // (invalid) fast method accessors. See
        // sun.reflect.misc.ReflectUtil#isVMAnonymousClass(Class<?>).
        if (isAnonymous()) {
            externalName = appendID(externalName);
        }
        if (isHidden()) {
            externalName = convertHidden(externalName);
        }
        return externalName;
    }

    @TruffleBoundary
    private String appendID(String externalName) {
        // A small improvement over HotSpot here, which uses the class identity hash code.
        return externalName + "/" + getId(); // VM.JVM_IHashCode(self);
    }

    @TruffleBoundary
    protected String convertHidden(String externalName) {
        // A small improvement over HotSpot here, which uses the class identity hash code.
        int idx = externalName.lastIndexOf('+');
        char[] chars = externalName.toCharArray();
        chars[idx] = '/';
        return new String(chars);

    }

    public boolean sameRuntimePackage(Klass other) {
        if (getDefiningClassLoader() != other.getDefiningClassLoader()) {
            return false;
        }
        if (getJavaVersion().modulesEnabled()) {
            return this.packageEntry() == other.packageEntry();
        } else {
            return this.getRuntimePackage().equals(other.getRuntimePackage());
        }
    }

    public final boolean isTypeMapped() {
        if (typeConversionState == UN_INITIALIZED) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            computeTypeConversionState();
        }
        return typeConversionState == TYPE_MAPPED;
    }

    private void computeTypeConversionState() {
        CompilerAsserts.neverPartOfCompilation();
        assert typeConversionState == UN_INITIALIZED;
        if (getContext().getPolyglotTypeMappings().mapTypeConversion(this) != null) {
            typeConversionState = TYPE_MAPPED;
        } else if (getContext().getPolyglotTypeMappings().mapInternalTypeConversion(this) != null) {
            typeConversionState = INTERNAL_MAPPED;
        } else if (getContext().getPolyglotTypeMappings().mapEspressoForeignCollection(this) != null) {
            typeConversionState = INTERNAL_COLLECTION_MAPPED;
        } else {
            typeConversionState = NOT_MAPPED;
        }
    }

    public final boolean isInternalTypeMapped() {
        if (typeConversionState == UN_INITIALIZED) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            computeTypeConversionState();
        }
        return typeConversionState == INTERNAL_MAPPED;
    }

    public final boolean isInternalCollectionTypeMapped() {
        if (typeConversionState == UN_INITIALIZED) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            computeTypeConversionState();
        }
        return typeConversionState == INTERNAL_COLLECTION_MAPPED;
    }

    // region jdwp-specific

    @Override
    public String getNameAsString() {
        return name.toString();
    }

    @Override
    public String getTypeAsString() {
        return type.toString();
    }

    @Override
    public String getGenericTypeAsString() {
        // only ObjectKlass(es) can have a generic signature
        return "";
    }

    @Override
    public Object getPrepareThread() {
        if (prepareThread == null) {
            prepareThread = getContext().getMainThread();
        }
        return prepareThread;
    }

    @Override
    public int getStatus() {
        if (this instanceof ObjectKlass) {
            ObjectKlass objectKlass = (ObjectKlass) this;
            int status = 0;
            if (objectKlass.isErroneous()) {
                return ClassStatusConstants.ERROR;
            }
            if (objectKlass.isPrepared()) {
                status |= ClassStatusConstants.PREPARED;
            }
            if (objectKlass.isVerified()) {
                status |= ClassStatusConstants.VERIFIED;
            }
            if (objectKlass.isInitializedImpl()) {
                status |= ClassStatusConstants.INITIALIZED;
            }
            return status;
        } else {
            return ClassStatusConstants.VERIFIED | ClassStatusConstants.PREPARED | ClassStatusConstants.INITIALIZED;
        }
    }

    @Override
    public KlassRef getSuperClass() {
        return getSuperKlass();
    }

    @Override
    public byte getTagConstant() {
        return getJavaKind().toTagConstant();
    }

    @Override
    public boolean isAssignable(KlassRef klass) {
        return isAssignableFrom((Klass) klass);
    }

    @Override
    public Object getKlassObject() {
        return mirror();
    }

    /**
     * Returns an identifier for the nest this klass is in. In practice, the nest is identified by
     * its nest host.
     *
     * @return The nest host of this klass.
     */
    public Klass nest() {
        return this;
    }

    /**
     * Checks that the given klass k is a nest member of {@code this} as a nest host. This does NOT
     * check whether this and k are in the same nest.
     */
    @SuppressWarnings("unused")
    public boolean nestMembersCheck(Klass k) {
        return false;
    }

    public StaticObject protectionDomain() {
        return (StaticObject) getMeta().HIDDEN_PROTECTION_DOMAIN.getHiddenObject(mirror());
    }

    /**
     * Returns an array containing the nest members of {@code this} as a nest host.
     */
    public Klass[] getNestMembers() {
        return EMPTY_ARRAY;
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public JDWPConstantPool getJDWPConstantPool() {
        ConstantPool pool = getConstantPool();
        return new JDWPConstantPool(pool.length(), pool.getRawBytes());
    }

    @Override
    public String getSourceDebugExtension() {
        return null;
    }

    @Override
    public final ModuleRef getModule() {
        return module();
    }

    // visible to TypeCheckNode
    public Assumption getRedefineAssumption() {
        return Assumption.ALWAYS_VALID;
    }

    // endregion jdwp-specific
}
