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

import static com.oracle.truffle.espresso.EspressoOptions.VerifyMode;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SUPER;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ACC_WRITTEN_FLAGS;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceDebugExtensionAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.verifier.MethodVerifier;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Resolved non-primitive, non-array types in Espresso.
 */
public final class ObjectKlass extends Klass {

    public static final ObjectKlass[] EMPTY_ARRAY = new ObjectKlass[0];

    private final EnclosingMethodAttribute enclosingMethod;

    @CompilationFinal //
    private StaticObject statics;

    @CompilationFinal(dimensions = 1) //
    private Field[] declaredFields;

    @CompilationFinal(dimensions = 2) private final int[][] leftoverHoles;
    @CompilationFinal(dimensions = 1) private final Field[] fieldTable;

    private final int primitiveFieldTotalByteCount;
    private final int primitiveStaticFieldTotalByteCount;

    private final int objectFields;
    private final int staticObjectFields;

    @CompilationFinal(dimensions = 1) private final Field[] staticFieldTable;

    @CompilationFinal(dimensions = 1) //
    private Method[] declaredMethods;

    @CompilationFinal(dimensions = 1) private Method[] mirandaMethods;

    private final InnerClassesAttribute innerClasses;

    private final Attribute runtimeVisibleAnnotations;

    private final Klass hostKlass;

    private String genericSignature;

    // Stores the VTable for classes, holds public non-static methods for interfaces.
    @CompilationFinal(dimensions = 1) private final Method[] vtable;

    // TODO(garcia) Sort itables (according to an arbitrary key) for dichotomic search?
    @CompilationFinal(dimensions = 2) private final Method[][] itable;
    @CompilationFinal(dimensions = 1) private final ObjectKlass[] iKlassTable;
    @CompilationFinal private final int itableLength;

    @CompilationFinal private volatile int initState = LOADED;

    @CompilationFinal //
    boolean hasDeclaredDefaultMethods = false;

    @CompilationFinal private int computedModifiers = -1;

    @CompilationFinal volatile RedefinitionCache redefineCache;

    public static final int LOADED = 0;
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;
    public static final int ERRONEOUS = 99;

    public Attribute getAttribute(Symbol<Name> name) {
        return getLinkedKlass().getAttribute(name);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader) {
        this(context, linkedKlass, superKlass, superInterfaces, classLoader, null);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader, Klass hostKlass) {
        super(context, linkedKlass.getName(), linkedKlass.getType(), superKlass, superInterfaces, linkedKlass.getFlags());

        this.hostKlass = hostKlass;
        // TODO(peterssen): Make writable copy.
        RuntimeConstantPool pool = new RuntimeConstantPool(getContext(), linkedKlass.getConstantPool(), classLoader);

        LinkedMethod[] linkedMethods = linkedKlass.getLinkedMethods();
        Method[] methods = new Method[linkedMethods.length];
        for (int i = 0; i < methods.length; ++i) {
            LinkedMethod linkedMethod = linkedMethods[i];
            methods[i] = new Method(this, linkedMethod, pool);
        }

        this.declaredMethods = methods;
        this.redefineCache = new RedefinitionCache(pool, linkedKlass);

        this.enclosingMethod = (EnclosingMethodAttribute) getAttribute(EnclosingMethodAttribute.NAME);
        this.innerClasses = (InnerClassesAttribute) getAttribute(InnerClassesAttribute.NAME);

        // Move attribute name to better location.
        this.runtimeVisibleAnnotations = getAttribute(Name.RuntimeVisibleAnnotations);

        FieldTable.CreationResult fieldCR = FieldTable.create(superKlass, this, linkedKlass);

        this.fieldTable = fieldCR.fieldTable;
        this.staticFieldTable = fieldCR.staticFieldTable;
        this.declaredFields = fieldCR.declaredFields;

        this.primitiveFieldTotalByteCount = fieldCR.primitiveFieldTotalByteCount;
        this.primitiveStaticFieldTotalByteCount = fieldCR.primitiveStaticFieldTotalByteCount;
        this.objectFields = fieldCR.objectFields;
        this.staticObjectFields = fieldCR.staticObjectFields;

        this.leftoverHoles = fieldCR.leftoverHoles;

        if (this.isInterface()) {
            this.itable = null;
            InterfaceTables.InterfaceCreationResult icr = InterfaceTables.constructInterfaceItable(this, declaredMethods);
            this.vtable = icr.methodtable;
            this.iKlassTable = icr.klassTable;
        } else {
            InterfaceTables.CreationResult methodCR = InterfaceTables.create(this, superKlass, superInterfaces);
            this.iKlassTable = methodCR.klassTable;
            this.mirandaMethods = methodCR.mirandas;
            this.vtable = VirtualTable.create(superKlass, declaredMethods, this);
            this.itable = InterfaceTables.fixTables(this, methodCR.tables, iKlassTable);
        }
        this.itableLength = iKlassTable.length;
        this.initState = LINKED;
        assert verifyTables();
    }

    private boolean verifyTables() {
        if (vtable != null) {
            for (int i = 0; i < vtable.length; i++) {
                if (isInterface()) {
                    if (vtable[i].getITableIndex() != i) {
                        return false;
                    }
                } else {
                    if (vtable[i].getVTableIndex() != i) {
                        return false;
                    }
                }
            }
        }
        if (itable != null) {
            for (Method[] table : itable) {
                for (int i = 0; i < table.length; i++) {
                    if (table[i].getITableIndex() != i) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    StaticObject getStaticsImpl() {
        if (statics == null) {
            obtainStatics();
        }
        return statics;
    }

    private synchronized void obtainStatics() {
        if (statics == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            statics = StaticObject.createStatics(this);
        }
    }

    boolean isInitializedImpl() {
        return initState == INITIALIZED;
    }

    private boolean isPrepared() {
        return initState == PREPARED;
    }

    public int getState() {
        return initState;
    }

    private boolean isInitializedOrPrepared() {
        return isPrepared() || isInitialized();
    }

    @ExplodeLoop
    private void actualInit() {
        synchronized (this) {
            if (!(isInitializedOrPrepared())) { // Check under lock
                if (initState == ERRONEOUS) {
                    throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoClassDefFoundError, "Erroneous class: " + getName());
                }
                try {
                    // Spec fragment: Then, initialize each final static field of C with the
                    // constant value in its ConstantValue attribute (&sect;4.7.2), in the order the
                    // fields appear in the ClassFile structure.
                    //
                    // ...
                    //
                    // Next, execute the class or interface initialization method of C.
                    prepare();
                    initState = PREPARED;
                    if (getContext().isMainThreadCreated()) {
                        if (getContext().getJDWPListener() != null) {
                            prepareThread = getContext().getGuestThreadFromHost(Thread.currentThread());
                            getContext().getJDWPListener().classPrepared(this, prepareThread);
                        }
                    }
                    if (getSuperKlass() != null) {
                        getSuperKlass().initialize();
                    }
                    for (ObjectKlass interf : getSuperInterfaces()) {
                        // Initialize all super interfaces, direct and indirect, with default
                        // methods.
                        interf.recursiveInitialize();
                    }
                    Method clinit = getClassInitializer();
                    if (clinit != null) {
                        clinit.getCallTarget().call();
                    }
                } catch (EspressoException e) {
                    setErroneous();
                    StaticObject cause = e.getExceptionObject();
                    Meta meta = getMeta();
                    if (!InterpreterToVM.instanceOf(cause, meta.java_lang_Error)) {
                        throw Meta.throwExceptionWithCause(meta.java_lang_ExceptionInInitializerError, cause);
                    } else {
                        throw e;
                    }
                } catch (Throwable e) {
                    getContext().getLogger().log(Level.WARNING, "Host exception during class initialization: {0}", this);
                    setErroneous();
                    throw e;
                }
                if (initState == ERRONEOUS) {
                    throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoClassDefFoundError, "Erroneous class: " + getName());
                }
                initState = INITIALIZED;
                assert isInitialized();
            }
        }
    }

    private void prepare() {
        checkLoadingConstraints();
        for (Field f : declaredFields) {
            if (f.isStatic()) {
                ConstantValueAttribute a = (ConstantValueAttribute) f.getAttribute(Name.ConstantValue);
                if (a == null) {
                    continue;
                }
                switch (f.getKind()) {
                    case Boolean: {
                        boolean c = getConstantPool().intAt(a.getConstantValueIndex()) != 0;
                        f.set(getStatics(), c);
                        break;
                    }
                    case Byte: {
                        byte c = (byte) getConstantPool().intAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    case Short: {
                        short c = (short) getConstantPool().intAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    case Char: {
                        char c = (char) getConstantPool().intAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    case Int: {
                        int c = getConstantPool().intAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    case Float: {
                        float c = getConstantPool().floatAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    case Long: {
                        long c = getConstantPool().longAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    case Double: {
                        double c = getConstantPool().doubleAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    case Object: {
                        StaticObject c = getConstantPool().resolvedStringAt(a.getConstantValueIndex());
                        f.set(getStatics(), c);
                        break;
                    }
                    default:
                        throw EspressoError.shouldNotReachHere("invalid constant field kind");
                }
            }
        }
    }

    // Need to carefully synchronize, as the work of other threads can erase our own work.

    void initializeImpl() {
        if (!isInitialized()) { // Skip synchronization and locks if already init.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            verify();
            actualInit();
        }
    }

    private void recursiveInitialize() {
        if (!isInitialized()) { // Skip synchronization and locks if already init.
            if (hasDeclaredDefaultMethods()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                verify();
                actualInit();
            } else {
                for (ObjectKlass interf : getSuperInterfaces()) {
                    interf.recursiveInitialize();
                }
            }
        }
    }

    @Override
    public Klass getElementalType() {
        return this;
    }

    @Override
    public @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return getConstantPool().getClassLoader();
    }

    @Override
    public RuntimeConstantPool getConstantPool() {
        return getRedefineCache().pool;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public Klass getEnclosingType() {
        return null;
    }

    @Override
    public Method[] getDeclaredConstructors() {
        List<Method> constructors = new ArrayList<>();
        for (Method m : getDeclaredMethods()) {
            if (Name._init_.equals(m.getName())) {
                constructors.add(m);
            }
        }
        return constructors.toArray(Method.EMPTY_ARRAY);
    }

    Method[] getMirandaMethods() {
        return mirandaMethods;
    }

    @Override
    public Method[] getDeclaredMethods() {
        return declaredMethods;
    }

    @Override
    public MethodRef[] getDeclaredMethodRefs() {
        MethodRef[] result = new MethodRef[declaredMethods.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = declaredMethods[i].getMethodVersion();
        }
        return result;
    }

    @Override
    public Field[] getDeclaredFields() {
        return declaredFields;
    }

    public EnclosingMethodAttribute getEnclosingMethod() {
        return enclosingMethod;
    }

    public InnerClassesAttribute getInnerClasses() {
        return innerClasses;
    }

    public LinkedKlass getLinkedKlass() {
        return getRedefineCache().linkedKlass;
    }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }

    public int getStaticFieldSlots() {
        return getLinkedKlass().staticFieldCount;
    }

    public int getInstanceFieldSlots() {
        return getLinkedKlass().instanceFieldCount;
    }

    public int getObjectFieldsCount() {
        return objectFields;
    }

    public int getPrimitiveFieldTotalByteCount() {
        return primitiveFieldTotalByteCount;
    }

    public int getStaticObjectFieldsCount() {
        return staticObjectFields;
    }

    public int getPrimitiveStaticFieldTotalByteCount() {
        return primitiveStaticFieldTotalByteCount;
    }

    Klass getHostClassImpl() {
        return hostKlass;
    }

    Field lookupFieldTableImpl(int slot) {
        assert (slot >= 0 && slot < getInstanceFieldSlots());
        return fieldTable[slot];
    }

    Field lookupStaticFieldTableImpl(int slot) {
        assert (slot >= 0 && slot < getStaticFieldSlots());
        return staticFieldTable[slot];
    }

    public Field lookupHiddenField(Symbol<Name> name) {
        // Hidden fields are (usually) located at the end of the field table.
        for (int i = fieldTable.length - 1; i > 0; i--) {
            Field f = fieldTable[i];
            if (f.getName() == name && f.isHidden()) {
                return f;
            }
        }
        throw EspressoError.shouldNotReachHere();
    }

    Method[] getVTable() {
        assert !isInterface();
        return vtable;
    }

    Method[] getInterfaceMethodsTable() {
        assert isInterface();
        return vtable;
    }

    Method vtableLookupImpl(int vtableIndex) {
        assert (vtableIndex >= 0) : "Undeclared virtual method";
        return vtable[vtableIndex];
    }

    public Method itableLookup(Klass interfKlass, int index) {
        assert (index >= 0) : "Undeclared interface method";
        try {
            return itable[fastLookup(interfKlass, iKlassTable)][index];
        } catch (IndexOutOfBoundsException e) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IncompatibleClassChangeError, "Class " + getName() + " does not implement interface " + interfKlass.getName());
        }
    }

    Method[][] getItable() {
        return itable;
    }

    ObjectKlass[] getiKlassTable() {
        return iKlassTable;
    }

    int lookupVirtualMethod(Symbol<Name> name, Symbol<Signature> signature, Klass subClass) {
        for (int i = 0; i < vtable.length; i++) {
            Method m = vtable[i];
            if (!m.isPrivate() && m.getName() == name && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic()) {
                    return i;
                } else if (sameRuntimePackage(subClass)) {
                    return i;
                }
            }
        }
        return -1;
    }

    List<Method> lookupVirtualMethodOverrides(Symbol<Name> name, Symbol<Signature> signature, Klass subKlass, List<Method> result) {
        for (Method m : vtable) {
            if (!m.isStatic() && !m.isPrivate() && m.getName() == name && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic()) {
                    result.add(m);
                } else if (m.getDeclaringKlass().sameRuntimePackage(subKlass)) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    public Method lookupInterfaceMethod(Symbol<Name> name, Symbol<Signature> signature) {
        assert isInterface();
        /*
         * 2. Otherwise, if C declares a method with the name and descriptor specified by the
         * interface method reference, method lookup succeeds.
         */
        for (Method m : getDeclaredMethods()) {
            if (name == m.getName() && signature == m.getRawSignature()) {
                return m;
            }
        }
        /*
         * 3. Otherwise, if the class Object declares a method with the name and descriptor
         * specified by the interface method reference, which has its ACC_PUBLIC flag set and does
         * not have its ACC_STATIC flag set, method lookup succeeds.
         */
        assert getSuperKlass().getType() == Type.java_lang_Object;
        Method m = getSuperKlass().lookupDeclaredMethod(name, signature);
        if (m != null && m.isPublic() && !m.isStatic()) {
            return m;
        }

        Method resolved = null;
        /*
         * Interfaces are sorted, superinterfaces first; traverse in reverse order to get
         * maximally-specific first.
         */
        for (int i = iKlassTable.length - 1; i >= 0; i--) {
            ObjectKlass superInterf = iKlassTable[i];
            for (Method superM : superInterf.getInterfaceMethodsTable()) {
                /*
                 * Methods in superInterf.getInterfaceMethodsTable() are all non-static non-private
                 * methods declared in superInterf.
                 */
                if (name == superM.getName() && signature == superM.getRawSignature()) {
                    if (!superM.isAbstract() && (resolved == null || !superInterf.isAssignableFrom(resolved.getDeclaringKlass()))) {
                        /*
                         * 4. Otherwise, if the maximally-specific superinterface methods
                         * (&sect;5.4.3.3) of C for the name and descriptor specified by the method
                         * reference include exactly one method that does not have its ACC_ABSTRACT
                         * flag set, then this method is chosen and method lookup succeeds.
                         * 
                         * Note: If there is more than one such method, we still select it, for it
                         * still complies with point 5.
                         */
                        return superM;
                    }
                    /*
                     * 5. Otherwise, if any superinterface of C declares a method with the name and
                     * descriptor specified by the method reference that has neither its ACC_PRIVATE
                     * flag nor its ACC_STATIC flag set, one of these is arbitrarily chosen and
                     * method lookup succeeds.
                     */
                    if (resolved == null) {
                        /*
                         * Since interfaces are sorted superinterfaces first, and we traverse in
                         * reverse order, we have the guarantee that the first such method we
                         * encounter will be a maximally-specific method. Thus, the only way
                         * returning this method is incorrect is if there is another
                         * maximally-specific non-abstract method
                         */
                        resolved = superM;
                    }
                }
            }
        }
        return resolved;
    }

    @Override
    public Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass) {
        KLASS_LOOKUP_METHOD_COUNT.inc();
        Method method = lookupDeclaredMethod(methodName, signature);
        if (method == null) {
            // Implicit interface methods.
            method = lookupMirandas(methodName, signature);
        }
        if (method == null && getType() == Type.java_lang_invoke_MethodHandle) {
            method = lookupPolysigMethod(methodName, signature);
        }
        if (method == null && getSuperKlass() != null) {
            method = getSuperKlass().lookupMethod(methodName, signature, accessingKlass);
        }
        return method;
    }

    public Field[] getFieldTable() {
        return fieldTable;
    }

    public Field[] getStaticFieldTable() {
        return staticFieldTable;
    }

    private Method lookupMirandas(Symbol<Name> methodName, Symbol<Signature> signature) {
        if (mirandaMethods == null) {
            return null;
        }
        for (Method miranda : mirandaMethods) {
            if (miranda.getName() == methodName && miranda.getRawSignature() == signature) {
                return miranda;
            }
        }
        return null;
    }

    @Override
    public void verify() {
        if (!isVerified()) {
            synchronized (this) {
                if (!isVerifyingOrVerified()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setVerificationStatus(VERIFYING);
                    try {
                        verifyImpl();
                    } catch (EspressoException e) {
                        setVerificationStatus(ERRONEOUS);
                        verificationError = e;
                        throw e;
                    } catch (Throwable e) {
                        setVerificationStatus(ERRONEOUS);
                        throw e;
                    }
                    setVerificationStatus(VERIFIED);
                }
            }
            if (verificationStatus == ERRONEOUS) {
                throw verificationError;
            }
        }
    }

    private void verifyImpl() {
        CompilerAsserts.neverPartOfCompilation();
        VerifyMode mode = getContext().Verify;
        if (mode != VerifyMode.NONE) {
            // Do not verify BootClassLoader classes, they are trusted.
            if (mode == VerifyMode.ALL || !StaticObject.isNull(getDefiningClassLoader())) {
                Meta meta = getMeta();
                if (getSuperKlass() != null && getSuperKlass().isFinalFlagSet()) {
                    throw Meta.throwException(meta.java_lang_VerifyError);
                }
                if (getSuperKlass() != null) {
                    getSuperKlass().verify();
                }
                for (ObjectKlass interf : getSuperInterfaces()) {
                    interf.verify();
                }
                if (meta.sun_reflect_MagicAccessorImpl.isAssignableFrom(this)) {
                    // Hotspot comment:
                    // As of the fix for 4486457 we disable verification for all of the
                    // dynamically-generated bytecodes associated with the 1.4
                    // reflection implementation, not just those associated with
                    // sun/reflect/SerializationConstructorAccessor.
                    // NOTE: this is called too early in the bootstrapping process to be
                    // guarded by Universe::is_gte_jdk14x_version()/UseNewReflection.
                    // Also for lambda generated code, gte jdk8
                    return;
                }
                for (Method m : getDeclaredMethods()) {
                    try {
                        MethodVerifier.verify(m);
                        // The verifier convention use host exceptions and they must be
                        // explicitly converted.
                        // This is acceptable since these particular set of host exceptions are not
                        // expected at all e.g. we don't expect any host
                        // VerifyError/ClassFormatError to be thrown by the host itself (at this
                        // point, or even ever at all).
                    } catch (VerifyError e) {
                        throw Meta.throwExceptionWithMessage(meta.java_lang_VerifyError, e.getMessage());
                    } catch (ClassFormatError e) {
                        throw Meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, e.getMessage());
                    } catch (IncompatibleClassChangeError e) {
                        throw Meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, e.getMessage());
                    } catch (NoClassDefFoundError e) {
                        throw Meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, e.getMessage());
                    }
                }
            }
        }
    }

    void print(PrintStream out) {
        out.println(getType());
        for (Method m : declaredMethods) {
            out.println(m);
            m.printBytecodes(out);
            out.println();
        }
    }

    public boolean hasFinalizer() {
        return (getModifiers() & ACC_FINALIZER) != 0;
    }

    // Verification data

    // Verification data
    @CompilationFinal //
    private volatile int verificationStatus = UNVERIFIED;

    @CompilationFinal //
    private EspressoException verificationError = null;

    private static final int UNVERIFIED = 0;

    private static final int VERIFYING = 1;
    private static final int VERIFIED = 2;

    private void setVerificationStatus(int status) {
        verificationStatus = status;
    }

    private boolean isVerifyingOrVerified() {
        return verificationStatus == VERIFYING || verificationStatus == VERIFIED;
    }

    private boolean isVerified() {
        return verificationStatus == VERIFIED;
    }

    private void setErroneous() {
        initState = ERRONEOUS;
    }

    public int[][] getLeftoverHoles() {
        return leftoverHoles;
    }

    private void checkLoadingConstraints() {
        if (getSuperKlass() != null) {
            if (!isInterface()) {
                Method[] thisVTable = getVTable();
                if (thisVTable != null) {
                    Method[] superVTable = getSuperKlass().getVTable();
                    Klass k1;
                    Klass k2;
                    for (int i = 0; i < superVTable.length; i++) {
                        k1 = thisVTable[i].getDeclaringKlass();
                        k2 = superVTable[i].getDeclaringKlass();
                        if (k1 == this) {
                            thisVTable[i].checkLoadingConstraints(k1.getDefiningClassLoader(), k2.getDefiningClassLoader());
                        }
                    }
                }
            }
            if (getItable() != null) {
                Method[][] itables = getItable();
                Klass[] klassTable = getiKlassTable();
                for (int i = 0; i < getItable().length; i++) {
                    Klass interfKlass = klassTable[i];
                    Method[] table = itables[i];
                    for (Method m : table) {
                        if (m.getDeclaringKlass() == this) {
                            m.checkLoadingConstraints(this.getDefiningClassLoader(), interfKlass.getDefiningClassLoader());
                        } else {
                            m.checkLoadingConstraints(this.getDefiningClassLoader(), m.getDeclaringKlass().getDefiningClassLoader());
                        }
                    }
                }
            }
        }
    }

    @TruffleBoundary
    public List<Symbol<Name>> getNestedTypeNames() {
        ArrayList<Symbol<Name>> result = new ArrayList<>();
        if (innerClasses != null) {
            for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
                if (entry.innerClassIndex != 0) {
                    result.add(getConstantPool().classAt(entry.innerClassIndex).getName(getConstantPool()));

                }
            }
        }
        return result;
    }

    @TruffleBoundary
    private int computeModifiers() {
        int modifiers = getModifiers();
        if (innerClasses != null) {
            for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
                if (entry.innerClassIndex != 0) {
                    Symbol<Name> innerClassName = getConstantPool().classAt(entry.innerClassIndex).getName(getConstantPool());
                    if (innerClassName.equals(this.getName())) {
                        modifiers = entry.innerClassAccessFlags;
                        break;
                    }
                }
            }
        }
        return modifiers;
    }

    @Override
    public int getClassModifiers() {
        int modifiers = computedModifiers;
        if (modifiers == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            computedModifiers = modifiers = computeModifiers();
        }
        // Remember to strip ACC_SUPER bit
        return modifiers & ~ACC_SUPER & JVM_ACC_WRITTEN_FLAGS;
    }

    /**
     * Returns true if the interface has declared (not inherited) default methods, false otherwise.
     */
    private boolean hasDeclaredDefaultMethods() {
        assert !hasDeclaredDefaultMethods || isInterface();
        return hasDeclaredDefaultMethods;
    }

    @Override
    public String getGenericTypeAsString() {
        if (genericSignature == null) {
            SignatureAttribute attr = (SignatureAttribute) getLinkedKlass().getAttribute(SignatureAttribute.NAME);
            if (attr == null) {
                genericSignature = ""; // if no generics, the generic signature is empty
            } else {
                genericSignature = getConstantPool().symbolAt(attr.getSignatureIndex()).toString();
            }
        }
        return genericSignature;
    }

    @Override
    public int getMajorVersion() {
        return getLinkedKlass().getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return getLinkedKlass().getMinorVersion();
    }

    @Override
    public String getSourceDebugExtension() {
        SourceDebugExtensionAttribute attribute = (SourceDebugExtensionAttribute) getAttribute(SourceDebugExtensionAttribute.NAME);
        return attribute != null ? attribute.getDebugExtension() : null;
    }

    public RedefinitionCache getRedefineCache() {
        RedefinitionCache cache = redefineCache;
        if (!cache.assumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            do {
                cache = redefineCache;
            } while (!cache.assumption.isValid());
        }
        return cache;
    }

    public void redefineClass(ParserKlass parserKlass) {
        RedefinitionCache oldVersion = redefineCache;
        StaticObject definingClassLoader = oldVersion.pool.getClassLoader();
        RuntimeConstantPool pool = new RuntimeConstantPool(getContext(), parserKlass.getConstantPool(), definingClassLoader);
        ObjectKlass[] superInterfaces = getSuperInterfaces();
        LinkedKlass[] interfaces = new LinkedKlass[superInterfaces.length];
        for (int i = 0; i < superInterfaces.length; i++) {
            interfaces[i] = superInterfaces[i].getLinkedKlass();
        }
        LinkedKlass linkedKlass = new LinkedKlass(parserKlass, getSuperKlass().getLinkedKlass(), interfaces);
        redefineCache = new RedefinitionCache(pool, linkedKlass);
        oldVersion.assumption.invalidate();
    }

    private static final class RedefinitionCache {
        final Assumption assumption;
        final RuntimeConstantPool pool;
        final LinkedKlass linkedKlass;

        RedefinitionCache(RuntimeConstantPool pool, LinkedKlass linkedKlass) {
            this.assumption = Truffle.getRuntime().createAssumption();
            this.pool = pool;
            this.linkedKlass = linkedKlass;
        }
    }
}
