/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.InvokeBasic;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.InvokeGeneric;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToInterface;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToSpecial;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToStatic;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToVirtual;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.toBasic;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.jdwp.api.ClassStatusConstants;
import com.oracle.truffle.espresso.jdwp.api.JDWPConstantPool;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethod;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.object.DebugCounter;

@ExportLibrary(InteropLibrary.class)
public abstract class Klass implements ModifiersProvider, ContextAccess, KlassRef, TruffleObject {

    // region Interop

    private static final String STATIC_TO_CLASS = "class";
    private static final String ARRAY = "array";
    private static final String COMPONENT = "component";
    private static final String SUPER = "super";

    @ExportMessage
    boolean isMemberReadable(String member) {
        if (STATIC_TO_CLASS.equals(member)) {
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
    Object readMember(String member) throws UnknownIdentifierException {
        if (STATIC_TO_CLASS.equals(member)) {
            return mirror();
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
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        for (Method m : getDeclaredMethods()) {
            if (m.isStatic() && m.isPublic() && member.equals(m.getName().toString())) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    Object invokeMember(String member,
                    Object[] arguments,
                    @Cached LookupDeclaredMethod lookupMethod,
                    @Exclusive @Cached InvokeEspressoNode invoke)
                    throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        Method method = lookupMethod.execute(this, member, true, true, arguments.length);
        if (method != null) {
            assert method.isStatic() && method.isPublic() && member.equals(method.getName().toString()) && method.getParameterCount() == arguments.length;
            return invoke.execute(method, null, arguments);
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        EconomicSet<String> members = EconomicSet.create();
        members.add(STATIC_TO_CLASS);
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
        return new KeysArray(members.toArray(new String[members.size()]));
    }

    // endregion Interop

    static final Comparator<Klass> KLASS_ID_COMPARATOR = new Comparator<Klass>() {
        @Override
        public int compare(Klass k1, Klass k2) {
            return Integer.compare(k1.id, k2.id);
        }
    };

    public static final Klass[] EMPTY_ARRAY = new Klass[0];

    static final DebugCounter KLASS_LOOKUP_METHOD_COUNT = DebugCounter.create("Klass.lookupMethod call count");
    static final DebugCounter KLASS_LOOKUP_FIELD_COUNT = DebugCounter.create("Klass.lookupField call count");
    static final DebugCounter KLASS_LOOKUP_DECLARED_METHOD_COUNT = DebugCounter.create("Klass.lookupDeclaredMethod call count");
    static final DebugCounter KLASS_LOOKUP_DECLARED_FIELD_COUNT = DebugCounter.create("Klass.lookupDeclaredField call count");

    private final Symbol<Name> name;
    private final Symbol<Type> type;
    private final EspressoContext context;
    private final ObjectKlass superKlass;

    private final int id;

    @CompilationFinal(dimensions = 1) //
    private final ObjectKlass[] superInterfaces;

    @CompilationFinal //
    private volatile ArrayKlass arrayClass;

    @CompilationFinal //
    private volatile StaticObject mirrorCache;

    @CompilationFinal private int hierarchyDepth = -1;

    protected Object prepareThread;

    // Raw modifiers provided by the VM.
    private final int modifiers;

    /**
     * A class or interface C is accessible to a class or interface D if and only if either of the
     * following is true:
     * <ul>
     * <li>C is public.
     * <li>C and D are members of the same run-time package (&sect;5.3).
     * </ul>
     */
    public static boolean checkAccess(Klass klass, Klass accessingKlass) {
        if (accessingKlass == null) {
            return true;
        }
        if (klass.isPublic() || klass.sameRuntimePackage(accessingKlass)) {
            return true;
        }
        return (klass.getMeta().sun_reflect_MagicAccessorImpl.isAssignableFrom(accessingKlass));
    }

    public final ObjectKlass[] getSuperInterfaces() {
        return superInterfaces;
    }

    Klass(EspressoContext context, Symbol<Name> name, Symbol<Type> type, ObjectKlass superKlass, ObjectKlass[] superInterfaces, int modifiers) {
        this.context = context;
        this.name = name;
        this.type = type;
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
        this.id = context.getNewId();
        this.modifiers = modifiers;
    }

    public abstract @Host(ClassLoader.class) StaticObject getDefiningClassLoader();

    public abstract ConstantPool getConstantPool();

    public final JavaKind getJavaKind() {
        return (this instanceof PrimitiveKlass)
                        ? ((PrimitiveKlass) this).getPrimitiveJavaKind()
                        : JavaKind.Object;
    }

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

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof Klass)) {
            return false;
        }
        Klass that = (Klass) obj;
        return this.mirror().equals(that.mirror());
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
    public final int hashCode() {
        return getType().hashCode();
    }

    @Override
    public final EspressoContext getContext() {
        return context;
    }

    /**
     * Final override for performance reasons.
     *
     * The compiler cannot see that the {@link Klass} hierarchy is sealed, there's a single
     * {@link ContextAccess#getMeta} implementation.
     *
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
    public final boolean isPrimitive() {
        return getJavaKind().isPrimitive();
    }

    /*
     * The setting of the final bit for types is a bit confusing since arrays are marked as final.
     * This method provides a semantically equivalent test that appropriate for types.
     */
    public boolean isLeaf() {
        return getElementalType().isFinalFlagSet();
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

    /**
     * Initializes this type.
     */
    public final void initialize() {
        if (this instanceof ObjectKlass) {
            ((ObjectKlass) this).initializeImpl();
        }
        // Array and primitive classes do not require initialization.
    }

    public void verify() {
        /* nop */
    }

    /**
     * Determines if this type is either the same as, or is a superclass or superinterface of, the
     * type represented by the specified parameter. This method is identical to
     * {@link Class#isAssignableFrom(Class)} in terms of the value return for this type.
     *
     * Fast check for Object types (as opposed to interface types) -> do not need to walk the entire
     * class hierarchy.
     *
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
        if (isInterface()) {
            return checkInterfaceSubclassing(other);
        }
        int depth = getHierarchyDepth();
        return other.getHierarchyDepth() >= depth && other.getSuperTypes()[depth] == this;
    }

    public final int getId() {
        return id;
    }

    boolean checkInterfaceSubclassing(Klass other) {
        Klass[] interfaces = other.getTransitiveInterfacesList();
        if (interfaces.length < 5) {
            for (Klass k : interfaces) {
                if (k == this) {
                    return true;
                }
            }
            return false;
        } else {
            return Arrays.binarySearch(interfaces, this, KLASS_ID_COMPARATOR) >= 0;
        }
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
    public final ObjectKlass[] getInterfaces() {
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
     * Returns an array reflecting all the fields declared by this type. This method is similar to
     * {@link Class#getDeclaredFields()} in terms of returned fields.
     */
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
                throw Meta.throwExceptionWithCause(meta.java_lang_ExceptionInInitializerError, cause);
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

    @CompilationFinal(dimensions = 1) private Klass[] transitiveInterfaceCache;

    protected final Klass[] getTransitiveInterfacesList() {
        Klass[] transitiveInterfaces = transitiveInterfaceCache;
        if (transitiveInterfaces == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (this.isArray() || this.isPrimitive()) {
                transitiveInterfaces = this.getSuperInterfaces();
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

    public final Field lookupField(Symbol<Name> fieldName, Symbol<Type> fieldType, boolean isStatic) {
        KLASS_LOOKUP_FIELD_COUNT.inc();
        // TODO(peterssen): Improve lookup performance.

        Field field = lookupDeclaredField(fieldName, fieldType);
        if (field != null && field.isStatic() == isStatic) {
            return field;
        }

        if (isStatic) {
            for (ObjectKlass superI : getSuperInterfaces()) {
                field = superI.lookupField(fieldName, fieldType, isStatic);
                if (field != null) {
                    assert field.isStatic();
                    return field;
                }
            }
        }

        if (getSuperKlass() != null) {
            return getSuperKlass().lookupField(fieldName, fieldType, isStatic);
        }

        return null;
    }

    public final Field lookupField(Symbol<Name> fieldName, Symbol<Type> fieldType) {
        KLASS_LOOKUP_FIELD_COUNT.inc();
        // TODO(peterssen): Improve lookup performance.

        Field field = lookupDeclaredField(fieldName, fieldType);
        if (field == null && getSuperKlass() != null) {
            return getSuperKlass().lookupField(fieldName, fieldType);
        }
        return field;
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

    @ExplodeLoop
    public final Method lookupDeclaredMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        KLASS_LOOKUP_DECLARED_METHOD_COUNT.inc();
        // TODO(peterssen): Improve lookup performance.
        for (Method method : getDeclaredMethods()) {
            if (methodName.equals(method.getName()) && signature.equals(method.getRawSignature())) {
                return method;
            }
        }
        return null;
    }

    public Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        return lookupMethod(methodName, signature, null);
    }

    /**
     * Give the accessing klass if there is a chance the method to be resolved is a method handle
     * intrinsics.
     */
    public abstract Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass);

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

    public Method lookupPolysigMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        if (methodName == Name.invoke || methodName == Name.invokeExact) {
            return findMethodHandleIntrinsic(methodName, signature, InvokeGeneric);
        } else if (methodName == Name.invokeBasic) {
            return findMethodHandleIntrinsic(methodName, signature, InvokeBasic);
        } else if (methodName == Name.linkToInterface) {
            return findMethodHandleIntrinsic(methodName, signature, LinkToInterface);
        } else if (methodName == Name.linkToSpecial) {
            return findMethodHandleIntrinsic(methodName, signature, LinkToSpecial);
        } else if (methodName == Name.linkToStatic) {
            return findMethodHandleIntrinsic(methodName, signature, LinkToStatic);
        } else if (methodName == Name.linkToVirtual) {
            return findMethodHandleIntrinsic(methodName, signature, LinkToVirtual);
        }
        for (Method m : getDeclaredMethods()) {
            if (m.isNative() && m.isVarargs() && m.getName() == methodName) {
                // check signature?
                throw EspressoError.unimplemented("New method handle invoke method? " + methodName);
            }
        }
        return null;
    }

    @TruffleBoundary
    private Method findMethodHandleIntrinsic(Symbol<Name> methodName,
                    Symbol<Signature> signature,
                    MethodHandleIntrinsics.PolySigIntrinsics methodHandleId) {
        if (methodHandleId == InvokeGeneric) {
            return (methodName == Name.invoke ? getMeta().java_lang_invoke_MethodHandle_invoke : getMeta().java_lang_invoke_MethodHandle_invokeExact).findIntrinsic(signature, methodHandleId);
        } else if (methodHandleId == InvokeBasic) {
            return getMeta().java_lang_invoke_MethodHandle_invokeBasic.findIntrinsic(signature, methodHandleId);
        } else {
            Symbol<Signature> basicSignature = toBasic(getSignatures().parsed(signature), true, getSignatures());
            switch (methodHandleId) {
                case LinkToInterface:
                    return findLinkToIntrinsic(getMeta().java_lang_invoke_MethodHandle_linkToInterface, basicSignature, methodHandleId);
                case LinkToSpecial:
                    return findLinkToIntrinsic(getMeta().java_lang_invoke_MethodHandle_linkToSpecial, basicSignature, methodHandleId);
                case LinkToStatic:
                    return findLinkToIntrinsic(getMeta().java_lang_invoke_MethodHandle_linkToStatic, basicSignature, methodHandleId);
                case LinkToVirtual:
                    return findLinkToIntrinsic(getMeta().java_lang_invoke_MethodHandle_linkToVirtual, basicSignature, methodHandleId);
                default:
                    throw EspressoError.shouldNotReachHere();
            }
        }
    }

    private static Method findLinkToIntrinsic(Method m, Symbol<Signature> signature, MethodHandleIntrinsics.PolySigIntrinsics id) {
        return m.findIntrinsic(signature, id);
    }

    /**
     * Returns the access flags provided by the .class file, e.g. ignores inner class access flags.
     */
    public final int getModifiers() {
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

    // TODO(garcia) Symbolify package ?
    @CompilationFinal private String runtimePackage;

    public final String getRuntimePackage() {
        String pkg = runtimePackage;
        if (runtimePackage == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            assert !isArray();
            pkg = Types.getRuntimePackage(getType());
            assert !pkg.endsWith(";");
            runtimePackage = pkg;
        }
        return pkg;
    }

    public Symbol<Name> getName() {
        return name;
    }

    public boolean sameRuntimePackage(Klass other) {
        return this.getDefiningClassLoader() == other.getDefiningClassLoader() && this.getRuntimePackage().equals(other.getRuntimePackage());
    }

    // region jdwp-specific

    public String getNameAsString() {
        return name.toString();
    }

    public String getTypeAsString() {
        return type.toString();
    }

    @Override
    public String getGenericTypeAsString() {
        // only ObjectKlass(es) can have a generic signature
        return "";
    }

    @Override
    public Klass[] getImplementedInterfaces() {
        return getInterfaces();
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
            int state = objectKlass.getState();
            switch (state) {
                case ObjectKlass.LOADED:
                    return ClassStatusConstants.VERIFIED;
                case ObjectKlass.PREPARED:
                case ObjectKlass.LINKED:
                    return ClassStatusConstants.VERIFIED | ClassStatusConstants.PREPARED;
                case ObjectKlass.INITIALIZED:
                    return ClassStatusConstants.VERIFIED | ClassStatusConstants.PREPARED | ClassStatusConstants.INITIALIZED;
                default:
                    return ClassStatusConstants.ERROR;
            }
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

    // endregion jdwp-specific
}
