/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.runtime.StaticObject.CLASS_TO_STATIC;
import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import java.util.Comparator;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.TriState;
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
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethod;
import com.oracle.truffle.espresso.nodes.interop.LookupFieldNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.BaseInterop;
import com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

@ExportLibrary(InteropLibrary.class)
public abstract class Klass implements ModifiersProvider, ContextAccess, KlassRef, TruffleObject {

    // region Interop

    public static final String STATIC_TO_CLASS = "class";
    private static final String ARRAY = "array";
    private static final String COMPONENT = "component";
    private static final String SUPER = "super";

    @ExportMessage
    boolean isMemberReadable(String member, @Shared("lookupField") @Cached LookupFieldNode lookupField) {
        Field field = lookupField.execute(this, member, true);
        if (field != null) {
            return true;
        }

        if (STATIC_TO_CLASS.equals(member)) {
            return true;
        }
        if (CLASS_TO_STATIC.equals(member)) {
            return true;
        }
        if (getMeta()._void != this && ARRAY.equals(member)) {
            return true;
        }
        if (isArray() && COMPONENT.equals(member)) {
            return true;
        }
        if (getSuperKlass() != null && SUPER.equals(member)) {
            return true;
        }

        return false;
    }

    @ExportMessage
    Object readMember(String member,
                    @Shared("lookupField") @Cached LookupFieldNode lookupFieldNode,
                    @Shared("error") @Cached BranchProfile error) throws UnknownIdentifierException {

        Field field = lookupFieldNode.execute(this, member, true);
        if (field != null) {
            Object result = field.get(this.tryInitializeAndGetStatics());
            if (result instanceof StaticObject && ((StaticObject) result).isForeignObject()) {
                return ((StaticObject) result).rawForeignObject();
            }
            return result;
        }

        // Klass<T>.class == Class<T>
        if (STATIC_TO_CLASS.equals(member)) {
            return mirror();
        }
        // Klass<T>.static == Klass<T>
        if (CLASS_TO_STATIC.equals(member)) {
            return this;
        }
        if (getMeta()._void != this && ARRAY.equals(member)) {
            return array();
        }
        if (isArray() && COMPONENT.equals(member)) {
            return ((ArrayKlass) this).getComponentType();
        }
        if (getSuperKlass() != null && SUPER.equals(member)) {
            return getSuperKlass();
        }

        error.enter();
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean isMemberModifiable(String member, @Shared("lookupField") @Cached LookupFieldNode lookupField) {
        Field field = lookupField.execute(this, member, true);
        return field != null && !field.isFinalFlagSet();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    final void writeMember(String member, Object value,
                    @Shared("lookupField") @Cached LookupFieldNode lookupFieldNode,
                    @Shared("error") @Cached BranchProfile error,
                    @Exclusive @Cached ToEspressoNode toEspressoNode) throws UnknownIdentifierException, UnsupportedTypeException {
        Field field = lookupFieldNode.execute(this, member, true);
        // Can only write to non-final fields.
        if (field != null && !field.isFinalFlagSet()) {
            Object espressoValue = toEspressoNode.execute(value, field.resolveTypeKlass());
            field.set(tryInitializeAndGetStatics(), espressoValue);
        } else {
            error.enter();
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    final boolean isMemberInvocable(String member,
                    @Exclusive @Cached LookupDeclaredMethod lookupMethod) {
        return lookupMethod.isInvocable(this, member, true);
    }

    @ExportMessage
    final Object invokeMember(String member,
                    Object[] arguments,
                    @Exclusive @Cached LookupDeclaredMethod lookupMethod,
                    @Exclusive @Cached InvokeEspressoNode invoke)
                    throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        Method.MethodVersion methodVersion = lookupMethod.execute(this, member, true, true, arguments.length);
        if (methodVersion != null) {
            Method method = methodVersion.getMethod();
            assert method.isStatic() && method.isPublic();
            assert member.startsWith(method.getNameAsString());
            assert method.getParameterCount() == arguments.length;

            return invoke.execute(method, null, arguments);
        }
        throw UnknownIdentifierException.create(member);
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
                members.add(m.getName().toString());
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

        return new KeysArray(members.toArray(new String[members.size()]));
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
            return receiver.getElementalType().getJavaKind() != JavaKind.Void;
        }
    }

    @ExportMessage
    abstract static class Instantiate {
        @SuppressWarnings("unused")
        @Specialization(guards = "receiver.isPrimitive()")
        static Object doPrimitive(Klass receiver, Object[] arguments) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        private static int convertLength(Object argument, ToEspressoNode toEspressoNode, Meta meta) throws UnsupportedTypeException {
            int length = 0;
            try {
                length = (int) toEspressoNode.execute(argument, meta._int);
            } catch (UnsupportedTypeException e) {
                throw UnsupportedTypeException.create(new Object[]{argument}, "Expected a single int");
            }
            if (length < 0) {
                throw UnsupportedTypeException.create(new Object[]{argument}, "Expected a non-negative length");
            }
            return length;
        }

        private static int getLength(Object[] arguments, ToEspressoNode toEspressoNode, Meta meta) throws UnsupportedTypeException, ArityException {
            if (arguments.length != 1) {
                throw ArityException.create(1, 1, arguments.length);
            }
            return convertLength(arguments[0], toEspressoNode, meta);
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
                        @Shared("lengthConversion") @Cached ToEspressoNode toEspressoNode) throws ArityException, UnsupportedTypeException {
            ArrayKlass arrayKlass = (ArrayKlass) receiver;
            assert arrayKlass.getComponentType().getJavaKind() != JavaKind.Void;
            EspressoContext context = EspressoContext.get(toEspressoNode);
            int length = getLength(arguments, toEspressoNode, context.getMeta());
            return InterpreterToVM.allocatePrimitiveArray((byte) arrayKlass.getComponentType().getJavaKind().getBasicType(), length, context.getMeta());
        }

        @Specialization(guards = "isReferenceArray(receiver)")
        static StaticObject doReferenceArray(Klass receiver, Object[] arguments,
                        @Shared("lengthConversion") @Cached ToEspressoNode toEspressoNode) throws UnsupportedTypeException, ArityException {
            ArrayKlass arrayKlass = (ArrayKlass) receiver;
            int length = getLength(arguments, toEspressoNode, EspressoContext.get(toEspressoNode).getMeta());
            return InterpreterToVM.newReferenceArray(arrayKlass.getComponentType(), length);
        }

        @Specialization(guards = "isMultidimensionalArray(receiver)")
        static StaticObject doMultidimensionalArray(Klass receiver, Object[] arguments,
                        @Shared("lengthConversion") @Cached ToEspressoNode toEspressoNode) throws ArityException, UnsupportedTypeException {
            ArrayKlass arrayKlass = (ArrayKlass) receiver;
            assert arrayKlass.getElementalType().getJavaKind() != JavaKind.Void;
            if (arrayKlass.getDimension() != arguments.length) {
                throw ArityException.create(arrayKlass.getDimension(), arrayKlass.getDimension(), arguments.length);
            }
            EspressoContext context = EspressoContext.get(toEspressoNode);
            int[] dimensions = new int[arguments.length];
            for (int i = 0; i < dimensions.length; ++i) {
                dimensions[i] = convertLength(arguments[i], toEspressoNode, context.getMeta());
            }

            return context.getInterpreterToVM().newMultiArray(arrayKlass.getComponentType(), dimensions);
        }

        static final String INIT_NAME = Name._init_.toString();

        @Specialization(guards = "isObjectKlass(receiver)")
        static Object doObject(Klass receiver, Object[] arguments,
                        @Exclusive @Cached LookupDeclaredMethod lookupMethod,
                        @Exclusive @Cached InvokeEspressoNode invoke) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            ObjectKlass objectKlass = (ObjectKlass) receiver;
            Method.MethodVersion init = lookupMethod.execute(objectKlass, INIT_NAME, true, false, arguments.length);
            if (init != null) {
                Method initMethod = init.getMethod();
                assert !initMethod.isStatic() && initMethod.isPublic() && initMethod.getName().toString().equals(INIT_NAME) && initMethod.getParameterCount() == arguments.length;
                StaticObject newObject = StaticObject.createNew(objectKlass);
                invoke.execute(initMethod, newObject, arguments);
                return newObject;
            }
            // TODO(goltsova): throw ArityException whenever possible
            throw UnsupportedMessageException.create();
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
        return getMeta().java_lang_Class_getTypeName.invokeDirect(mirror());
    }

    @ExportMessage
    Object getMetaSimpleName() {
        assert isMetaObject();
        return getMeta().java_lang_Class_getSimpleName.invokeDirect(mirror());
    }

    @ExportMessage
    boolean isMetaInstance(Object instance) {
        return instance instanceof StaticObject && instanceOf((StaticObject) instance, this);
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
        return VM.JVM_IHashCode(mirror());
    }

    // endregion ### Identity/hashCode

    public Class<?> getDispatch() {
        Class<?> result = dispatch;
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (getMeta().getContext().metaInitialized()) {
                result = getMeta().resolveDispatch(this);
                dispatch = result;
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

    // endregion Interop

    // Threshold for using binary search instead of linear search for interface lookup.
    private static final int LINEAR_SEARCH_THRESHOLD = 8;

    static final Comparator<Klass> KLASS_ID_COMPARATOR = new Comparator<Klass>() {
        @Override
        public int compare(Klass k1, Klass k2) {
            return Integer.compare(k1.id, k2.id);
        }
    };

    static final Comparator<ObjectKlass.KlassVersion> KLASS_VERSION_ID_COMPARATOR = new Comparator<ObjectKlass.KlassVersion>() {
        @Override
        public int compare(ObjectKlass.KlassVersion k1, ObjectKlass.KlassVersion k2) {
            return Integer.compare(k1.getKlass().getId(), k2.getKlass().getId());
        }
    };

    public static final Klass[] EMPTY_ARRAY = new Klass[0];

    static final DebugCounter KLASS_LOOKUP_METHOD_COUNT = DebugCounter.create("Klass.lookupMethod call count");
    static final DebugCounter KLASS_LOOKUP_FIELD_COUNT = DebugCounter.create("Klass.lookupField call count");
    static final DebugCounter KLASS_LOOKUP_DECLARED_METHOD_COUNT = DebugCounter.create("Klass.lookupDeclaredMethod call count");
    static final DebugCounter KLASS_LOOKUP_DECLARED_FIELD_COUNT = DebugCounter.create("Klass.lookupDeclaredField call count");

    protected Symbol<Name> name;
    protected Symbol<Type> type;
    private final EspressoContext context;
    private final ObjectKlass superKlass;

    private final int id;

    @CompilationFinal(dimensions = 1) //
    private final ObjectKlass[] superInterfaces;

    @CompilationFinal //
    private volatile ArrayKlass arrayClass;

    @CompilationFinal //
    private volatile StaticObject mirrorCache;

    @CompilationFinal //
    private Class<?> dispatch;

    @CompilationFinal private int hierarchyDepth = -1;

    protected Object prepareThread;

    // Raw modifiers provided by the VM.
    private final int modifiers;

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
    public static boolean checkAccess(Klass klass, Klass accessingKlass) {
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
        return (context.getMeta().sun_reflect_MagicAccessorImpl.isAssignableFrom(accessingKlass));

    }

    public static boolean doModuleAccessChecks(Klass klass, Klass accessingKlass, EspressoContext context) {
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

    public final ObjectKlass[] getSuperInterfaces() {
        return superInterfaces;
    }

    Klass(EspressoContext context, Symbol<Name> name, Symbol<Type> type, ObjectKlass superKlass, ObjectKlass[] superInterfaces, int modifiers) {
        this(context, name, type, superKlass, superInterfaces, modifiers, -1);
    }

    Klass(EspressoContext context, Symbol<Name> name, Symbol<Type> type, ObjectKlass superKlass, ObjectKlass[] superInterfaces, int modifiers, int possibleID) {
        this.context = context;
        this.name = name;
        this.type = type;
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
        this.id = (possibleID >= 0) ? possibleID : context.getNewKlassId();
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
    public final boolean isInterface() {
        // conflict between ModifiersProvider and KlassRef interfaces,
        // so chose the default implementation in ModifiersProvider.
        return ModifiersProvider.super.isInterface();
    }

    public StaticObject mirror() {
        if (mirrorCache == null) {
            mirrorCreate();
        }
        return mirrorCache;
    }

    private synchronized void mirrorCreate() {
        if (mirrorCache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.mirrorCache = StaticObject.createClass(this);
        }
    }

    public final ArrayKlass getArrayClass() {
        ArrayKlass ak = arrayClass;
        if (ak == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                ak = arrayClass;
                if (ak == null) {
                    ak = createArrayKlass();
                    arrayClass = ak;
                }
            }
        }
        return ak;
    }

    public final ArrayKlass array() {
        return getArrayClass();
    }

    @Override
    public ArrayKlass getArrayClass(int dimensions) {
        assert dimensions > 0;
        ArrayKlass array = getArrayClass();

        // Careful with of impossible void[].
        if (array == null) {
            return null;
        }

        for (int i = 1; i < dimensions; ++i) {
            array = array.getArrayClass();
        }
        return array;
    }

    protected ArrayKlass createArrayKlass() {
        return new ArrayKlass(this);
    }

    @Override
    public final EspressoContext getContext() {
        return context;
    }

    @Override
    public final boolean equals(Object that) {
        return this == that;
    }

    @Override
    public final int hashCode() {
        return getType().hashCode();
    }

    /**
     * Final override for performance reasons.
     * <p>
     * The compiler cannot see that the {@link Klass} hierarchy is sealed, there's a single
     * {@link ContextAccess#getMeta} implementation.
     * <p>
     * This final override avoids the virtual call in compiled code.
     */
    @Override
    public final Meta getMeta() {
        return getContext().getMeta();
    }

    public final StaticObject tryInitializeAndGetStatics() {
        safeInitialize();
        return getStatics();
    }

    public final StaticObject getStatics() {
        if (this instanceof ObjectKlass) {
            return ((ObjectKlass) this).getStaticsImpl();
        }
        CompilerDirectives.transferToInterpreter();
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
        return getJavaKind().isPrimitive();
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
        int state = ((ObjectKlass) this).getState();
        return state == ObjectKlass.INITIALIZED ||
                        state == ObjectKlass.ERRONEOUS ||
                        // initializing thread
                        state == ObjectKlass.INITIALIZING && Thread.holdsLock(this);
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
            return this == other;
        }
        if (this.isArray() && other.isArray()) {
            return ((ArrayKlass) this).arrayTypeChecks((ArrayKlass) other);
        }
        if (!this.isArray() && ModifiersProvider.super.isFinalFlagSet()) {
            return this == other;
        }
        if (isInterface()) {
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

    public final int getId() {
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
    public final Klass getHostClass() {
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
    public final ObjectKlass getSuperKlass() {
        return superKlass;
    }

    /**
     * Gets the interfaces implemented or extended by this type. This method is analogous to
     * {@link Class#getInterfaces()} and as such, only returns the interfaces directly implemented
     * or extended by this type.
     */
    @Override
    public final Klass[] getImplementedInterfaces() {
        return superInterfaces;
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
            StaticObject cause = e.getExceptionObject();
            Meta meta = getMeta();
            if (!InterpreterToVM.instanceOf(cause, meta.java_lang_Error)) {
                throw meta.throwExceptionWithCause(meta.java_lang_ExceptionInInitializerError, cause);
            } else {
                throw e;
            }
        }
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

    @CompilationFinal(dimensions = 1) //
    private Klass[] supertypesWithSelfCache;

    // index 0 is Object, index hierarchyDepth is this
    Klass[] getSuperTypes() {
        Klass[] supertypes = supertypesWithSelfCache;
        if (supertypes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Klass supertype = getSupertype();
            if (supertype == null) {
                this.supertypesWithSelfCache = new Klass[]{this};
                return supertypesWithSelfCache;
            }
            Klass[] superKlassTypes = supertype.getSuperTypes();
            supertypes = new Klass[superKlassTypes.length + 1];
            int depth = getHierarchyDepth();
            assert supertypes.length == depth + 1;
            supertypes[depth] = this;
            System.arraycopy(superKlassTypes, 0, supertypes, 0, depth);
            supertypesWithSelfCache = supertypes;
        }
        return supertypes;
    }

    int getHierarchyDepth() {
        int result = hierarchyDepth;
        if (result == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (getSupertype() == null) {
                // Primitives or java.lang.Object
                result = 0;
            } else {
                result = getSupertype().getHierarchyDepth() + 1;
            }
            hierarchyDepth = result;
        }
        return result;
    }

    @CompilationFinal(dimensions = 1) private ObjectKlass.KlassVersion[] transitiveInterfaceCache;

    protected final ObjectKlass.KlassVersion[] getTransitiveInterfacesList() {
        ObjectKlass.KlassVersion[] transitiveInterfaces = transitiveInterfaceCache;
        if (transitiveInterfaces == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (this.isArray() || this.isPrimitive()) {
                ObjectKlass[] superItfs = this.getSuperInterfaces();
                transitiveInterfaces = new ObjectKlass.KlassVersion[superItfs.length];
                for (int i = 0; i < superItfs.length; i++) {
                    transitiveInterfaces[i] = superItfs[i].getKlassVersion();
                }
            } else {
                // Use the itable construction.
                transitiveInterfaces = ((ObjectKlass) this).getiKlassTable();
            }
            transitiveInterfaceCache = transitiveInterfaces;
        }
        return transitiveInterfaces;
    }

    @TruffleBoundary
    public StaticObject allocateReferenceArray(int length) {
        return InterpreterToVM.newReferenceArray(this, length);
    }

    @TruffleBoundary
    public StaticObject allocateReferenceArray(int length, IntFunction<StaticObject> generator) {
        // TODO(peterssen): Store check is missing.
        StaticObject[] array = new StaticObject[length];
        for (int i = 0; i < array.length; ++i) {
            array[i] = generator.apply(i);
        }
        return StaticObject.createArray(getArrayClass(), array);
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
            throw EspressoError.shouldNotReachHere("Missing field: ", fieldName, ": ", fieldType, " in ", this);
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
        } else if (this instanceof ArrayKlass) {
            // Arrays extend j.l.Object, which shouldn't have any declared instance fields.
            assert getMeta().java_lang_Object == getSuperKlass();
            assert getMeta().java_lang_Object.getDeclaredFields().length == 0;
            // Always null?
            return getMeta().java_lang_Object.lookupFieldTable(slot);
        }
        // Unreachable?
        assert this instanceof PrimitiveKlass;
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.shouldNotReachHere("lookupFieldTable on primitive type");
    }

    public final Field lookupStaticFieldTable(int slot) {
        if (this instanceof ObjectKlass) {
            return ((ObjectKlass) this).lookupStaticFieldTableImpl(slot);
        }
        // Array nor primitives have static fields.
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.shouldNotReachHere("lookupStaticFieldTable on primitive/array type");
    }

    public final Method requireMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        Method obj = lookupMethod(methodName, signature);
        if (obj == null) {
            throw EspressoError.shouldNotReachHere("Missing method: ", methodName, ": ", signature, " starting at ", this);
        }
        return obj;
    }

    public final Method requireDeclaredMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        Method obj = lookupDeclaredMethod(methodName, signature);
        if (obj == null) {
            throw EspressoError.shouldNotReachHere("Missing method: ", methodName, ": ", signature, " in ", this);
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
    public abstract Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass, LookupMode lookupMode);

    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        return lookupMethod(methodName, signature, null, LookupMode.ALL);
    }

    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, LookupMode lookupMode) {
        return lookupMethod(methodName, signature, null, lookupMode);
    }

    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass) {
        return lookupMethod(methodName, signature, accessingKlass, LookupMode.ALL);
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
    public int getModifiers() {
        // Note: making this method non-final
        // may cause heavy performance issues
        return modifiers;
    }

    /**
     * Returns the modifiers for the guest Class, it takes into account inner classes which are
     * public at the JVM level, but protected/private at the Java level.
     */
    public abstract int getClassModifiers();

    public final StaticObject allocateInstance() {
        return InterpreterToVM.newObject(this, false);
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

    // endregion jdwp-specific
}
