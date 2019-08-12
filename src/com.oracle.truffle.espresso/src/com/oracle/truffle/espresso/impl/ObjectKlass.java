/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.EspressoOptions.VerifyMode;
import com.oracle.truffle.espresso.classfile.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
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

    public static final JavaKind FIELD_REPRESENTATION = JavaKind.Byte;

    private final Object initializationLock = new Object();

    private final EnclosingMethodAttribute enclosingMethod;

    private final RuntimeConstantPool pool;

    private final LinkedKlass linkedKlass;

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

    @CompilationFinal(dimensions = 1) private final Method[] vtable;

    // TODO(garcia) Sort itables (according to an arbitrary key) for dichotomic search?
    @CompilationFinal(dimensions = 2) private final Method[][] itable;
    @CompilationFinal(dimensions = 1) private final Klass[] iKlassTable;
    @CompilationFinal private final int itableLength;

    @CompilationFinal private volatile int initState = LINKED;

    public static final int LOADED = 0;
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;
    public static final int ERRONEOUS = 99;

    public final Attribute getAttribute(Symbol<Name> name) {
        return linkedKlass.getAttribute(name);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader) {
        this(context, linkedKlass, superKlass, superInterfaces, classLoader, null);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader, Klass hostKlass) {
        super(context, linkedKlass.getName(), linkedKlass.getType(), superKlass, superInterfaces);

        this.linkedKlass = linkedKlass;
        this.hostKlass = hostKlass;

        this.enclosingMethod = (EnclosingMethodAttribute) getAttribute(EnclosingMethodAttribute.NAME);
        this.innerClasses = (InnerClassesAttribute) getAttribute(InnerClassesAttribute.NAME);

        // Move attribute name to better location.
        this.runtimeVisibleAnnotations = getAttribute(Name.RuntimeVisibleAnnotations);

        // TODO(peterssen): Make writable copy.
        this.pool = new RuntimeConstantPool(getContext(), linkedKlass.getConstantPool(), classLoader);

        FieldTable.CreationResult fieldCR = FieldTable.create(superKlass, this, linkedKlass);

        this.fieldTable = fieldCR.fieldTable;
        this.staticFieldTable = fieldCR.staticFieldTable;
        this.declaredFields = fieldCR.declaredFields;

        this.primitiveFieldTotalByteCount = fieldCR.primitiveFieldTotalByteCount;
        this.primitiveStaticFieldTotalByteCount = fieldCR.primitiveStaticFieldTotalByteCount;
        this.objectFields = fieldCR.objectFields;
        this.staticObjectFields = fieldCR.staticObjectFields;

        this.leftoverHoles = fieldCR.leftoverHoles;

        LinkedMethod[] linkedMethods = linkedKlass.getLinkedMethods();
        Method[] methods = new Method[linkedMethods.length];
        for (int i = 0; i < methods.length; ++i) {
            LinkedMethod linkedMethod = linkedMethods[i];
            methods[i] = new Method(this, linkedMethod);
        }

        this.declaredMethods = methods;
        if (this.isInterface()) {
            this.itable = null;
            this.vtable = null;
            this.iKlassTable = InterfaceTables.getiKlassTable(this, declaredMethods);
        } else {
            InterfaceTables.CreationResult methodCR = InterfaceTables.create(this, superKlass, superInterfaces);
            this.iKlassTable = methodCR.klassTable;
            this.mirandaMethods = methodCR.mirandas;
            this.vtable = VirtualTable.create(superKlass, declaredMethods, this);
            this.itable = InterfaceTables.fixTables(this, methodCR.tables, iKlassTable);
        }
        this.itableLength = iKlassTable.length;

    }

    @Override
    public StaticObject getStatics() {
        if (statics == null) {
            obtainStatics();
        }
        return statics;
    }

    private synchronized void obtainStatics() {
        if (statics == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            statics = new StaticObject(this, true);
        }
    }

    @Override
    public boolean isInstanceClass() {
        return /* !isArray() && */ !isInterface();
    }

    @Override
    public int getFlags() {
        return linkedKlass.getFlags();
    }

    @Override
    public boolean isInitialized() {
        return initState == INITIALIZED;
    }

    private boolean isPrepared() {
        return initState == PREPARED;
    }

    private boolean isInitializedOrPrepared() {
        return isPrepared() || isInitialized();
    }

    @ExplodeLoop
    private void actualInit() {
        synchronized (initializationLock) {
            if (!(isInitializedOrPrepared())) { // Check under lock
                if (initState == ERRONEOUS) {
                    throw getMeta().throwExWithMessage(NoClassDefFoundError.class, "Erroneous class: " + getName());
                }
                initState = PREPARED;
                verifyKlass();
                try {
                    if (getSuperKlass() != null) {
                        getSuperKlass().initialize();
                    }
                    /**
                     * Spec fragment: Then, initialize each final static field of C with the
                     * constant value in its ConstantValue attribute (§4.7.2), in the order the
                     * fields appear in the ClassFile structure.
                     *
                     * ...
                     *
                     * Next, execute the class or interface initialization method of C.
                     */
                    for (Field f : declaredFields) {
                        if (f.isStatic()) {
                            ConstantValueAttribute a = (ConstantValueAttribute) f.getAttribute(Name.ConstantValue);
                            if (a == null) {
                                continue;
                            }
                            switch (f.getKind()) {
                                case Boolean: {
                                    boolean c = getConstantPool().intAt(a.getConstantvalueIndex()) != 0;
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Byte: {
                                    byte c = (byte) getConstantPool().intAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Short: {
                                    short c = (short) getConstantPool().intAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Char: {
                                    char c = (char) getConstantPool().intAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Int: {
                                    int c = getConstantPool().intAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Float: {
                                    float c = getConstantPool().floatAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Long: {
                                    long c = getConstantPool().longAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Double: {
                                    double c = getConstantPool().doubleAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                case Object: {
                                    StaticObject c = getConstantPool().resolvedStringAt(a.getConstantvalueIndex());
                                    f.set(getStatics(), c);
                                    break;
                                }
                                default:
                                    throw EspressoError.shouldNotReachHere("invalid constant field kind");
                            }
                        }
                    }
                    Method clinit = getClassInitializer();
                    if (clinit != null) {
                        clinit.getCallTarget().call();
                    }
                } catch (EspressoException e) {
                    setErroneous();
                    StaticObject cause = e.getException();
                    if (!InterpreterToVM.instanceOf(cause, getMeta().Error)) {
                        throw getMeta().throwExWithCause(ExceptionInInitializerError.class, cause);
                    } else {
                        throw e;
                    }
                } catch (Throwable e) {
                    System.err.println("Host exception happened during class initialization");
                    setErroneous();
                    throw e;
                }
                if (initState == ERRONEOUS) {
                    throw getMeta().throwExWithMessage(NoClassDefFoundError.class, "Erroneous class: " + getName());
                }
                initState = INITIALIZED;
                assert isInitialized();
            }
        }
    }

    // Need to carefully synchronize, as the work of other threads can erase our own work.
    @Override
    public void initialize() {
        if (!isInitialized()) { // Skip synchronization and locks if already init.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            actualInit();
        }
    }

    @Override
    public Klass getElementalType() {
        return this;
    }

    @Override
    public @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return pool.getClassLoader();
    }

    @Override
    public RuntimeConstantPool getConstantPool() {
        return pool;
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
            if (Name.INIT.equals(m.getName())) {
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
    public Field[] getDeclaredFields() {
        return declaredFields;
    }

    @Override
    public Klass getComponentType() {
        return null;
    }

    public EnclosingMethodAttribute getEnclosingMethod() {
        return enclosingMethod;
    }

    public InnerClassesAttribute getInnerClasses() {
        return innerClasses;
    }

    public final LinkedKlass getLinkedKlass() {
        return linkedKlass;
    }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }

    public int getStaticFieldSlots() {
        return linkedKlass.staticFieldCount;
    }

    public int getInstanceFieldSlots() {
        return linkedKlass.instanceFieldCount;
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

    @Override
    public Klass getHostClass() {
        return hostKlass;
    }

    @Override
    public final Field lookupFieldTable(int slot) {
        assert (slot >= 0 && slot < getInstanceFieldSlots());
        return fieldTable[slot];
    }

    @Override
    public final Field lookupStaticFieldTable(int slot) {
        assert (slot >= 0 && slot < getStaticFieldSlots());
        return staticFieldTable[slot];
    }

    public final Field lookupHiddenField(Symbol<Name> name) {
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
        return vtable;
    }

    @Override
    public final Method vtableLookup(int index) {
        assert (index >= 0) : "Undeclared virtual method";
        return vtable[index];
    }

    public final Method itableLookup(Klass interfKlass, int index) {
        assert (index >= 0) : "Undeclared interface method";
        try {
            return itable[findITableIndex(interfKlass)][index];
        } catch (IndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(IncompatibleClassChangeError.class, "Class " + getName() + " does not implement interface " + interfKlass.getName());
        }
    }

    private int findITableIndex(Klass interfKlass) {
        if (itableLength < 5) {
            for (int i = 0; i < itableLength; i++) {
                if (iKlassTable[i] == interfKlass) {
                    return i;
                }
            }
            return -1;
        } else {
            return Arrays.binarySearch(iKlassTable, interfKlass, COMPARATOR);
        }
    }

    final Method[][] getItable() {
        return itable;
    }

    final Klass[] getiKlassTable() {
        return iKlassTable;
    }

    final Method lookupVirtualMethod(Symbol<Name> name, Symbol<Signature> signature, Klass subClass) {
        for (Method m : vtable) {
            if (!m.isPrivate() && m.getName() == name && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic()) {
                    return m;
                } else if (sameRuntimePackage(subClass)) {
                    return m;
                }
            }
        }
        return null;
    }

    final List<Method> lookupVirtualMethodOverrides(Symbol<Name> name, Symbol<Signature> signature, Klass subKlass, List<Method> result) {
        for (Method m : vtable) {
            if (!m.isPrivate() && m.getName() == name && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic()) {
                    result.add(m);
                } else if (this.sameRuntimePackage(subKlass)) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    public final Method lookupInterfaceMethod(Symbol<Name> name, Symbol<Signature> signature) {
        assert isInterface();
        for (Klass k : iKlassTable) {
            for (Method m : k.getDeclaredMethods()) {
                if (name == m.getName() && signature == m.getRawSignature()) {
                    return m;
                }
            }
        }
        assert getSuperKlass().getType() == Type.Object;
        Method m = getSuperKlass().lookupDeclaredMethod(name, signature);
        if (m != null && m.isPublic() && !m.isStatic()) {
            return m;
        }
        return null;
    }

    @Override
    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass) {
        methodLookupCount.inc();
        Method method = lookupDeclaredMethod(methodName, signature);
        if (method == null) {
            // Implicit interface methods.
            method = lookupMirandas(methodName, signature);
        }
        if (method == null && getType() == Type.MethodHandle) {
            method = lookupPolysigMethod(methodName, signature, accessingKlass);
        }
        if (method == null && getSuperKlass() != null) {
            method = getSuperKlass().lookupMethod(methodName, signature, accessingKlass);
        }
        return method;
    }

    public final Field[] getFieldTable() {
        return fieldTable;
    }

    public final Field[] getStaticFieldTable() {
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

    @TruffleBoundary
    private void verifyKlass() {
        VerifyMode mode = getContext().getEnv().getOptions().get(EspressoOptions.Verify);
        if (mode != VerifyMode.NONE) {
            // Do not verify BootClassLoader classes, they are trusted.
            if (mode == VerifyMode.ALL || !StaticObject.isNull(getDefiningClassLoader())) {
                for (Method m : declaredMethods) {
                    try {
                        MethodVerifier.verify(m);
                    } catch (VerifyError | ClassFormatError | IncompatibleClassChangeError | NoClassDefFoundError e) {
                        // new BytecodeStream(m.getCodeAttribute().getCode()).printBytecode(this);
                        setErroneous();
                        throw getMeta().throwExWithMessage(e.getClass(), e.getMessage());
                    } catch (EspressoException e) {
                        throw e;
                    } catch (Throwable e) {
                        System.err.println("Unexpected host exception happened during bytecode verification: " + e);
                        throw EspressoError.shouldNotReachHere(e.toString());
                    }
                }
            }
        }
    }

    private void setErroneous() {
        initState = ERRONEOUS;
    }

    public int[][] getLeftoverHoles() {
        return leftoverHoles;
    }
}
