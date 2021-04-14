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

import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SUPER;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_ACC_WRITTEN_FLAGS;

import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.NestHostAttribute;
import com.oracle.truffle.espresso.classfile.attributes.NestMembersAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceDebugExtensionAttribute;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.impl.PackageTable.PackageEntry;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.impl.JDWP;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.ChangePacket;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.redefinition.DetectedChange;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
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

    // instance and hidden fields declared in this class and in its super classes
    @CompilationFinal(dimensions = 1) //
    private final Field[] fieldTable;

    // points to the first element in the FieldTable that refers to a field declared in this class,
    // or is equal to fieldTable.length if this class does not declare fields
    private final int localFieldTableIndex;

    // static fields declared in this class (no hidden fields)
    @CompilationFinal(dimensions = 1) //
    private final Field[] staticFieldTable;

    private final InnerClassesAttribute innerClasses;

    private final Attribute runtimeVisibleAnnotations;

    private final Klass hostKlass;

    @CompilationFinal //
    private Klass nest;

    @CompilationFinal //
    private PackageEntry packageEntry;

    private String genericSignature;

    @CompilationFinal private volatile int initState = LOADED;

    @CompilationFinal //
    boolean hasDeclaredDefaultMethods = false;

    @CompilationFinal private int computedModifiers = -1;

    @CompilationFinal volatile KlassVersion klassVersion;

    // used for class redefintion when refreshing vtables etc.
    private volatile ArrayList<WeakReference<ObjectKlass>> subTypes;

    public static final int LOADED = 0;
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;
    public static final int ERRONEOUS = 99;

    private final StaticObject definingClassLoader;

    public Attribute getAttribute(Symbol<Name> attrName) {
        return getLinkedKlass().getAttribute(attrName);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader) {
        this(context, linkedKlass, superKlass, superInterfaces, classLoader, null);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader, Klass hostKlass) {
        super(context, linkedKlass.getName(), linkedKlass.getType(), superKlass, superInterfaces, linkedKlass.getFlags());

        this.hostKlass = hostKlass;
        // TODO(peterssen): Make writable copy.
        RuntimeConstantPool pool = new RuntimeConstantPool(getContext(), linkedKlass.getConstantPool(), classLoader);
        definingClassLoader = pool.getClassLoader();
        Field[] skFieldTable = superKlass != null ? superKlass.getFieldTable() : new Field[0];
        LinkedField[] lkInstanceFields = linkedKlass.getInstanceFields();
        LinkedField[] lkStaticFields = linkedKlass.getStaticFields();

        fieldTable = new Field[skFieldTable.length + lkInstanceFields.length];
        staticFieldTable = new Field[lkStaticFields.length];

        assert fieldTable.length == linkedKlass.getFieldTableLength();
        System.arraycopy(skFieldTable, 0, fieldTable, 0, skFieldTable.length);
        localFieldTableIndex = skFieldTable.length;
        for (int i = 0; i < lkInstanceFields.length; i++) {
            Field instanceField = new Field(this, lkInstanceFields[i]);
            fieldTable[localFieldTableIndex + i] = instanceField;
        }
        for (int i = 0; i < lkStaticFields.length; i++) {
            Field staticField = new Field(this, lkStaticFields[i]);
            staticFieldTable[i] = staticField;
        }

        LinkedMethod[] linkedMethods = linkedKlass.getLinkedMethods();
        Method[] methods = new Method[linkedMethods.length];
        for (int i = 0; i < methods.length; i++) {
            methods[i] = new Method(this, linkedMethods[i], pool);
        }

        this.enclosingMethod = (EnclosingMethodAttribute) linkedKlass.getAttribute(EnclosingMethodAttribute.NAME);
        this.innerClasses = (InnerClassesAttribute) linkedKlass.getAttribute(InnerClassesAttribute.NAME);

        // Move attribute name to better location.
        this.runtimeVisibleAnnotations = linkedKlass.getAttribute(Name.RuntimeVisibleAnnotations);
        // Package initialization must be done before vtable creation, as there are same package
        // checks.
        initPackage(classLoader);

        Method[][] itable = null;
        Method[] vtable;
        ObjectKlass[] iKlassTable;
        Method[] mirandaMethods = null;

        if (this.isInterface()) {
            InterfaceTables.InterfaceCreationResult icr = InterfaceTables.constructInterfaceItable(this, methods);
            vtable = icr.methodtable;
            iKlassTable = icr.klassTable;
        } else {
            InterfaceTables.CreationResult methodCR = InterfaceTables.create(superKlass, superInterfaces, methods);
            iKlassTable = methodCR.klassTable;
            mirandaMethods = methodCR.mirandas;
            vtable = VirtualTable.create(superKlass, methods, this, mirandaMethods, false);
            itable = InterfaceTables.fixTables(vtable, mirandaMethods, methods, methodCR.tables, iKlassTable);
        }
        if (superKlass != null) {
            superKlass.addSubType(this);
        }
        for (ObjectKlass superInterface : superInterfaces) {
            superInterface.addSubType(this);
        }
        this.klassVersion = new KlassVersion(pool, linkedKlass, methods, mirandaMethods, vtable, itable, iKlassTable);
        this.initState = LINKED;
        assert verifyTables();
    }

    private void addSubType(ObjectKlass objectKlass) {
        // We only build subtypes model iff jdwp is enabled
        if (getContext().JDWPOptions != null) {
            if (subTypes == null) {
                synchronized (this) {
                    // double-checked locking
                    if (subTypes == null) {
                        subTypes = new ArrayList<>(1);
                    }
                }
            }
            synchronized (subTypes) {
                subTypes.add(new WeakReference<>(objectKlass));
            }
        }
    }

    private boolean verifyTables() {
        Method[] vtable = getKlassVersion().vtable;
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
        if (getItable() != null) {
            for (Method[] table : getItable()) {
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
        checkErroneousInitialization();
        synchronized (this) {
            // Double-check under lock
            checkErroneousInitialization();
            if (!(isInitializedOrPrepared())) {
                try {
                    /*
                     * Spec fragment: Then, initialize each final static field of C with the
                     * constant value in its ConstantValue attribute (&sect;4.7.2), in the order the
                     * fields appear in the ClassFile structure.
                     */
                    prepare();

                    initState = PREPARED;
                    if (getContext().isMainThreadCreated()) {
                        if (getContext().getJDWPListener() != null) {
                            prepareThread = getContext().getGuestThreadFromHost(Thread.currentThread());
                            getContext().getJDWPListener().classPrepared(this, prepareThread);
                        }
                    }
                    if (!isInterface()) {
                        /*
                         * Next, if C is a class rather than an interface, then let SC be its
                         * superclass and let SI1, ..., SIn be all superinterfaces of C [...] For
                         * each S in the list [ SC, SI1, ..., SIn ], if S has not yet been
                         * initialized, then recursively perform this entire procedure for S. If
                         * necessary, verify and prepare S first.
                         */
                        if (getSuperKlass() != null) {
                            getSuperKlass().initialize();
                        }
                        for (ObjectKlass interf : getSuperInterfaces()) {
                            // Initialize all super interfaces, direct and indirect, with default
                            // methods.
                            interf.recursiveInitialize();
                        }
                    }
                    // Next, execute the class or interface initialization method of C.
                    Method clinit = getClassInitializer();
                    if (clinit != null) {
                        clinit.getCallTarget().call();
                    }
                } catch (EspressoException e) {
                    setErroneousInitialization();
                    StaticObject cause = e.getExceptionObject();
                    Meta meta = getMeta();
                    if (!InterpreterToVM.instanceOf(cause, meta.java_lang_Error)) {
                        throw meta.throwExceptionWithCause(meta.java_lang_ExceptionInInitializerError, cause);
                    } else {
                        throw e;
                    }
                } catch (EspressoExitException e) {
                    setErroneousInitialization();
                    throw e;
                } catch (Throwable e) {
                    getContext().getLogger().log(Level.WARNING, "Host exception during class initialization: {0}", this.getNameAsString());
                    e.printStackTrace();
                    setErroneousInitialization();
                    throw e;
                }
                checkErroneousInitialization();
                initState = INITIALIZED;
                assert isInitialized();
            }
        }
    }

    private void prepare() {
        checkLoadingConstraints();
        for (Field f : staticFieldTable) {
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
            for (ObjectKlass interf : getSuperInterfaces()) {
                interf.recursiveInitialize();
            }
            if (hasDeclaredDefaultMethods()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                verify();
                actualInit(); // Does not recursively initialize interfaces
            }
        }
    }

    @Override
    public Klass getElementalType() {
        return this;
    }

    @Override
    public @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return definingClassLoader;
    }

    @Override
    public RuntimeConstantPool getConstantPool() {
        return getKlassVersion().pool;
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
        return getKlassVersion().mirandaMethods;
    }

    @Override
    public Method[] getDeclaredMethods() {
        return getKlassVersion().declaredMethods;
    }

    @Override
    public MethodRef[] getDeclaredMethodRefs() {
        MethodRef[] result = new MethodRef[getDeclaredMethods().length];
        for (int i = 0; i < result.length; i++) {
            result[i] = getDeclaredMethods()[i].getMethodVersion();
        }
        return result;
    }

    @Override
    public Field[] getDeclaredFields() {
        // Speculate that there are no hidden fields
        Field[] declaredFields = Arrays.copyOf(staticFieldTable, staticFieldTable.length + fieldTable.length - localFieldTableIndex);
        int insertionIndex = staticFieldTable.length;
        for (int i = localFieldTableIndex; i < fieldTable.length; i++) {
            Field f = fieldTable[i];
            if (!f.isHidden()) {
                declaredFields[insertionIndex++] = f;
            }
        }
        return insertionIndex == declaredFields.length ? declaredFields : Arrays.copyOf(declaredFields, insertionIndex);
    }

    public EnclosingMethodAttribute getEnclosingMethod() {
        return enclosingMethod;
    }

    public InnerClassesAttribute getInnerClasses() {
        return innerClasses;
    }

    public LinkedKlass getLinkedKlass() {
        return getKlassVersion().linkedKlass;
    }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }

    Klass getHostClassImpl() {
        return hostKlass;
    }

    @Override
    public Klass nest() {
        if (nest == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NestHostAttribute nestHost = (NestHostAttribute) getAttribute(NestHostAttribute.NAME);
            if (nestHost == null) {
                nest = this;
            } else {
                RuntimeConstantPool thisPool = getConstantPool();
                Klass host = thisPool.resolvedKlassAt(this, nestHost.hostClassIndex);

                if (!host.nestMembersCheck(this)) {
                    Meta meta = getMeta();
                    throw meta.throwException(meta.java_lang_IncompatibleClassChangeError);
                }
                nest = host;
            }
        }
        return nest;
    }

    @Override
    public boolean nestMembersCheck(Klass k) {
        NestMembersAttribute nestMembers = (NestMembersAttribute) getAttribute(NestMembersAttribute.NAME);
        if (nestMembers == null) {
            return false;
        }
        if (!this.sameRuntimePackage(k)) {
            return false;
        }
        RuntimeConstantPool pool = getConstantPool();
        for (int index : nestMembers.getClasses()) {
            if (k.getName().equals(pool.classAt(index).getName(pool))) {
                if (k == pool.resolvedKlassAt(this, index)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Klass[] getNestMembers() {
        NestMembersAttribute nestMembers = (NestMembersAttribute) getAttribute(NestMembersAttribute.NAME);
        if (nestMembers == null || nestMembers.getClasses().length == 0) {
            return EMPTY_ARRAY;
        }
        RuntimeConstantPool pool = getConstantPool();
        Klass[] result = new Klass[nestMembers.getClasses().length];
        for (int i = 0; i < result.length; i++) {
            int index = nestMembers.getClasses()[i];
            result[i] = pool.resolvedKlassAt(this, index);
        }
        return result;
    }

    Field lookupFieldTableImpl(int slot) {
        assert slot >= 0 && slot < fieldTable.length && !fieldTable[slot].isHidden();
        return fieldTable[slot];
    }

    Field lookupStaticFieldTableImpl(int slot) {
        assert slot >= 0 && slot < getStaticFieldTable().length;
        return staticFieldTable[slot];
    }

    public Field requireHiddenField(Symbol<Name> fieldName) {
        // Hidden fields are (usually) located at the end of the field table.
        for (int i = fieldTable.length - 1; i >= 0; i--) {
            Field f = fieldTable[i];
            if (f.getName() == fieldName && f.isHidden()) {
                return f;
            }
        }
        throw EspressoError.shouldNotReachHere("Missing hidden field ", fieldName, " in ", this);
    }

    // Exposed to LookupVirtualMethodNode
    public Method[] getVTable() {
        assert !isInterface();
        return getKlassVersion().vtable;
    }

    Method[] getInterfaceMethodsTable() {
        assert isInterface();
        return getKlassVersion().vtable;
    }

    Method vtableLookupImpl(int vtableIndex) {
        assert (vtableIndex >= 0) : "Undeclared virtual method";
        return getVTable()[vtableIndex];
    }

    public Method itableLookup(Klass interfKlass, int index) {
        assert (index >= 0) : "Undeclared interface method";
        try {
            return getItable()[fastLookup(interfKlass, getiKlassTable())][index];
        } catch (IndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "Class " + getName() + " does not implement interface " + interfKlass.getName());
        }
    }

    Method[][] getItable() {
        return getKlassVersion().itable;
    }

    ObjectKlass[] getiKlassTable() {
        return getKlassVersion().iKlassTable;
    }

    int findVirtualMethodIndex(Symbol<Name> methodName, Symbol<Signature> signature, Klass subClass) {
        for (int i = 0; i < getVTable().length; i++) {
            Method m = getVTable()[i];
            if (!m.isStatic() && !m.isPrivate() && m.getName() == methodName && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic() || m.getDeclaringKlass().sameRuntimePackage(subClass)) {
                    return i;
                }
            }
        }
        return -1;
    }

    void lookupVirtualMethodOverrides(Method current, Klass subKlass, List<Method> result) {
        Symbol<Name> methodName = current.getName();
        Symbol<Signature> signature = current.getRawSignature();
        for (Method m : getVTable()) {
            if (!m.isStatic() && !m.isPrivate() && m.getName() == methodName && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic()) {
                    result.add(m);
                } else {
                    if (m.getDeclaringKlass().sameRuntimePackage(subKlass)) {
                        result.add(m);
                    } else {
                        ObjectKlass currentKlass = this.getSuperKlass();
                        int index = m.getVTableIndex();
                        while (currentKlass != null) {
                            if (index >= currentKlass.getVTable().length) {
                                break;
                            }
                            Method toExamine = currentKlass.getVTable()[index];
                            if (current.canOverride(toExamine)) {
                                result.add(toExamine);
                                break;
                            }
                            currentKlass = currentKlass.getSuperKlass();
                        }
                    }
                }
            }
        }
    }

    public Method resolveInterfaceMethod(Symbol<Name> methodName, Symbol<Signature> signature) {
        assert isInterface();
        /*
         * 2. Otherwise, if C declares a method with the name and descriptor specified by the
         * interface method reference, method lookup succeeds.
         */
        for (Method m : getDeclaredMethods()) {
            if (methodName == m.getName() && signature == m.getRawSignature()) {
                return m;
            }
        }
        /*
         * 3. Otherwise, if the class Object declares a method with the name and descriptor
         * specified by the interface method reference, which has its ACC_PUBLIC flag set and does
         * not have its ACC_STATIC flag set, method lookup succeeds.
         */
        assert getSuperKlass().getType() == Type.java_lang_Object;
        Method m = getSuperKlass().lookupDeclaredMethod(methodName, signature);
        if (m != null && m.isPublic() && !m.isStatic()) {
            return m;
        }

        Method resolved = null;
        /*
         * Interfaces are sorted, superinterfaces first; traverse in reverse order to get
         * maximally-specific first.
         */
        for (int i = getiKlassTable().length - 1; i >= 0; i--) {
            ObjectKlass superInterf = getiKlassTable()[i];
            for (Method superM : superInterf.getInterfaceMethodsTable()) {
                /*
                 * Methods in superInterf.getInterfaceMethodsTable() are all non-static non-private
                 * methods declared in superInterf.
                 */
                if (methodName == superM.getName() && signature == superM.getRawSignature()) {
                    if (resolved == null) {
                        resolved = superM;
                    } else {
                        /*
                         * 4. Otherwise, if the maximally-specific superinterface methods
                         * (&sect;5.4.3.3) of C for the name and descriptor specified by the method
                         * reference include exactly one method that does not have its ACC_ABSTRACT
                         * flag set, then this method is chosen and method lookup succeeds.
                         *
                         * 5. Otherwise, if any superinterface of C declares a method with the name
                         * and descriptor specified by the method reference that has neither its
                         * ACC_PRIVATE flag nor its ACC_STATIC flag set, one of these is arbitrarily
                         * chosen and method lookup succeeds.
                         */
                        resolved = InterfaceTables.resolveMaximallySpecific(resolved, superM);
                        if (resolved.getITableIndex() == -1) {
                            /*
                             * Multiple maximally specific: this method has a poison pill.
                             *
                             * NOTE: Since java 9, we can invokespecial interface methods (ie: a
                             * call directly to the resolved method, rather than after an interface
                             * lookup). We are looking up a method taken from the implemented
                             * interface (and not from a currently non-existing itable of the
                             * implementing interface). This difference, and the possibility of
                             * invokespecial, means that we cannot return the looked up method
                             * directly in case of multiple maximally specific method. thus, we
                             * spawn a new proxy method, attached to no method table, just to fail
                             * if invokespecial.
                             */
                            assert (resolved.identity() == superM.identity());
                            resolved.setITableIndex(superM.getITableIndex());
                        }
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
        if (method == null &&
                        (getType() == Type.java_lang_invoke_MethodHandle ||
                                        getType() == Type.java_lang_invoke_VarHandle)) {
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
        if (getMirandaMethods() == null) {
            return null;
        }
        for (Method miranda : getMirandaMethods()) {
            if (miranda.getName() == methodName && miranda.getRawSignature() == signature) {
                return miranda;
            }
        }
        return null;
    }

    @Override
    public void verify() {
        if (!isVerified()) {
            checkErroneousVerification();
            synchronized (this) {
                if (!isVerifyingOrVerified()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setVerificationStatus(VERIFYING);
                    try {
                        verifyImpl();
                    } catch (EspressoException e) {
                        setErroneousVerification(e);
                        throw e;
                    }
                    setVerificationStatus(VERIFIED);
                }
            }
            checkErroneousVerification();
        }
    }

    private void verifyImpl() {
        CompilerAsserts.neverPartOfCompilation();
        if (getContext().needsVerify(getDefiningClassLoader())) {
            Meta meta = getMeta();
            if (getSuperKlass() != null && getSuperKlass().isFinalFlagSet()) {
                throw meta.throwException(meta.java_lang_VerifyError);
            }
            if (getSuperKlass() != null) {
                getSuperKlass().verify();
            }
            for (ObjectKlass interf : getSuperInterfaces()) {
                interf.verify();
            }
            if (meta.sun_reflect_MagicAccessorImpl.isAssignableFrom(this)) {
                /*
                 * Hotspot comment:
                 * 
                 * As of the fix for 4486457 we disable verification for all of the
                 * dynamically-generated bytecodes associated with the 1.4 reflection
                 * implementation, not just those associated with
                 * sun/reflect/SerializationConstructorAccessor. NOTE: this is called too early in
                 * the bootstrapping process to be guarded by
                 * Universe::is_gte_jdk14x_version()/UseNewReflection. Also for lambda generated
                 * code, gte jdk8
                 */
                return;
            }
            for (Method m : getDeclaredMethods()) {
                try {
                    MethodVerifier.verify(m);
                    /*
                     * The verifier convention use host exceptions and they must be explicitly
                     * converted. This is acceptable since these particular set of host exceptions
                     * are not expected at all e.g. we don't expect any host
                     * VerifyError/ClassFormatError to be thrown by the host itself (at this point,
                     * or even ever at all).
                     */
                } catch (VerifyError e) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_VerifyError, e.getMessage());
                } catch (ClassFormatError e) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, e.getMessage());
                } catch (IncompatibleClassChangeError e) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, e.getMessage());
                } catch (NoClassDefFoundError e) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, e.getMessage());
                }
            }
        }
    }

    void print(PrintStream out) {
        out.println(getType());
        for (Method m : getDeclaredMethods()) {
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

    private void checkErroneousVerification() {
        if (verificationStatus == ERRONEOUS) {
            throw verificationError;
        }
    }

    private void setErroneousVerification(EspressoException e) {
        verificationStatus = ERRONEOUS;
        verificationError = e;
    }

    private void setErroneousInitialization() {
        initState = ERRONEOUS;
    }

    private void checkErroneousInitialization() {
        if (initState == ERRONEOUS) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, "Erroneous class: " + getName());
        }
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
                            m.checkLoadingConstraints(interfKlass.getDefiningClassLoader(), m.getDeclaringKlass().getDefiningClassLoader());
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

    private void initPackage(@Host(ClassLoader.class) StaticObject classLoader) {
        if (!Names.isUnnamedPackage(getRuntimePackage())) {
            ClassRegistry registry = getRegistries().getClassRegistry(classLoader);
            packageEntry = registry.packages().lookup(getRuntimePackage());
            // If the package name is not found in the loader's package
            // entry table, it is an indication that the package has not
            // been defined. Consider it defined within the unnamed module.
            if (packageEntry == null) {
                if (!getRegistries().javaBaseDefined()) {
                    // Before java.base is defined during bootstrapping, define all packages in
                    // the java.base module.
                    packageEntry = registry.packages().lookupOrCreate(getRuntimePackage(), getRegistries().getJavaBaseModule());
                } else {
                    packageEntry = registry.packages().lookupOrCreate(getRuntimePackage(), registry.getUnnamedModule());
                }
            }
        }
    }

    @Override
    public ModuleEntry module() {
        if (!inUnnamedPackage()) {
            return packageEntry.module();
        }
        if (getHostClass() != null) {
            return getRegistries().getClassRegistry(getHostClass().getDefiningClassLoader()).getUnnamedModule();
        }
        return getRegistries().getClassRegistry(getDefiningClassLoader()).getUnnamedModule();
    }

    @Override
    public PackageEntry packageEntry() {
        return packageEntry;
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

    public KlassVersion getKlassVersion() {
        // block execution during class redefinition
        ClassRedefinition.check();

        KlassVersion cache = klassVersion;
        if (!cache.assumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            do {
                cache = klassVersion;
            } while (!cache.assumption.isValid());
        }
        return cache;
    }

    public void redefineClass(ChangePacket packet, List<ObjectKlass> refreshSubClasses, Ids<Object> ids) {
        ParserKlass parserKlass = packet.parserKlass;
        DetectedChange change = packet.detectedChange;
        KlassVersion oldVersion = klassVersion;
        RuntimeConstantPool pool = new RuntimeConstantPool(getContext(), parserKlass.getConstantPool(), oldVersion.pool.getClassLoader());
        ObjectKlass[] superInterfaces = getSuperInterfaces();
        LinkedKlass[] interfaces = new LinkedKlass[superInterfaces.length];
        for (int i = 0; i < superInterfaces.length; i++) {
            interfaces[i] = superInterfaces[i].getLinkedKlass();
        }

        LinkedKlass linkedKlass = LinkedKlass.redefine(parserKlass, getSuperKlass().getLinkedKlass(), interfaces, getLinkedKlass());

        // fields
        if (!change.getOuterFields().isEmpty()) {
            LinkedField[] instanceFields = linkedKlass.getInstanceFields();
            for (Field outerField : change.getOuterFields()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();

                // we know that the special field is always at the same index
                for (int i = 0; i < fieldTable.length; i++) {
                    Field oldField = fieldTable[i];
                    if (outerField == oldField) {
                        for (LinkedField instanceField : instanceFields) {
                            if (instanceField.getName().equals(outerField.getName())) {
                                // replace with new field
                                fieldTable[i] = new Field(this, instanceField);
                            }
                        }
                    }
                }
            }
        }

        Method[][] itable = oldVersion.itable;
        Method[] vtable = oldVersion.vtable;
        ObjectKlass[] iKlassTable;
        Method[] mirandaMethods = oldVersion.mirandaMethods;

        // changed methods
        Map<Method, ParserMethod> changedMethodBodies = packet.detectedChange.getChangedMethodBodies();
        for (Map.Entry<Method, ParserMethod> entry : changedMethodBodies.entrySet()) {
            Method method = entry.getKey();
            ParserMethod newMethod = entry.getValue();
            Method.SharedRedefinitionContent redefineContent = method.redefine(newMethod, packet.parserKlass, ids);
            JDWP.LOGGER.fine(() -> "Redefining method " + method.getDeclaringKlass().getName() + "." + method.getName());

            // look in tables for copied methods that also needs to be invalidated
            if (!method.isStatic() && !method.isPrivate() && !Name._init_.equals(method.getName())) {
                checkCopyMethods(method, itable, redefineContent, ids);
                checkCopyMethods(method, vtable, redefineContent, ids);
                checkCopyMethods(method, mirandaMethods, redefineContent, ids);
            }
        }

        Set<Method> removedMethods = change.getRemovedMethods();
        List<ParserMethod> addedMethods = change.getAddedMethods();

        LinkedList<Method> declaredMethods = new LinkedList<>(Arrays.asList(oldVersion.declaredMethods));
        declaredMethods.removeAll(removedMethods);

        // in case of an added/removed virtual method, we must also update the tables
        // which might have ripple implications on all subclasses
        boolean virtualMethodsModified = false;

        for (Method removedMethod : removedMethods) {
            virtualMethodsModified |= isVirtual(removedMethod.getLinkedMethod().getParserMethod());
            ParserMethod parserMethod = removedMethod.getLinkedMethod().getParserMethod();
            updateOverrideMethods(ids, parserMethod.getFlags(), parserMethod.getName(), parserMethod.getSignature());
            removedMethod.removedByRedefinition();
            removedMethod.getMethodVersion().getAssumption().invalidate();
            JDWP.LOGGER.fine(() -> "Removed method " + removedMethod.getDeclaringKlass().getName() + "." + removedMethod.getName());
        }

        for (ParserMethod addedMethod : addedMethods) {
            LinkedMethod linkedMethod = new LinkedMethod(addedMethod);
            Method added = new Method(this, linkedMethod, pool);
            declaredMethods.addLast(added);
            virtualMethodsModified |= isVirtual(addedMethod);
            updateOverrideMethods(ids, addedMethod.getFlags(), addedMethod.getName(), addedMethod.getSignature());
            JDWP.LOGGER.fine(() -> "Added method " + added.getDeclaringKlass().getName() + "." + added.getName());
        }

        Method[] newDeclaredMethods = declaredMethods.toArray(new Method[declaredMethods.size()]);

        if (isInterface()) {
            InterfaceTables.InterfaceCreationResult icr = InterfaceTables.constructInterfaceItable(this, newDeclaredMethods);
            vtable = icr.methodtable;
            iKlassTable = icr.klassTable;
        } else {
            InterfaceTables.CreationResult methodCR = InterfaceTables.create(getSuperKlass(), superInterfaces, newDeclaredMethods);
            iKlassTable = methodCR.klassTable;
            mirandaMethods = methodCR.mirandas;
            vtable = VirtualTable.create(getSuperKlass(), newDeclaredMethods, this, mirandaMethods, true);
            itable = InterfaceTables.fixTables(vtable, mirandaMethods, newDeclaredMethods, methodCR.tables, iKlassTable);
        }

        if (virtualMethodsModified) {
            refreshSubClasses.addAll(getSubTypes());
        }

        klassVersion = new KlassVersion(pool, linkedKlass, newDeclaredMethods, mirandaMethods, vtable, itable, iKlassTable);

        incrementKlassRedefinitionCount();
        oldVersion.assumption.invalidate();
    }

    // used by some plugins during klass redefitnion
    public void reRunClinit() {
        getClassInitializer().getCallTarget().call();
    }

    private static void checkCopyMethods(Method method, Method[][] table, Method.SharedRedefinitionContent content, Ids<Object> ids) {
        for (Method[] methods : table) {
            checkCopyMethods(method, methods, content, ids);
        }
    }

    private static void checkCopyMethods(Method method, Method[] table, Method.SharedRedefinitionContent content, Ids<Object> ids) {
        for (Method m : table) {
            if (m.identity() == method.identity() && m != method) {
                m.redefine(content, ids);
            }
        }
    }

    private void incrementKlassRedefinitionCount() {
        // increment the redefine count on the class instance to flush reflection caches
        int value = InterpreterToVM.getFieldInt(mirror(), getMeta().java_lang_Class_classRedefinedCount);
        InterpreterToVM.setFieldInt(++value, mirror(), getMeta().java_lang_Class_classRedefinedCount);
    }

    // if an added/removed method is an override of a super method
    // we need to invalidate the super class method, to allow
    // for new method dispatch lookup
    private void updateOverrideMethods(Ids<Object> ids, int flags, Symbol<Name> methodName, Symbol<Signature> signature) {
        if (!Modifier.isStatic(flags) && !Modifier.isPrivate(flags) && !Name._init_.equals(methodName)) {
            ObjectKlass superKlass = getSuperKlass();

            while (superKlass != null) {
                // look for the method
                int vtableIndex = superKlass.findVirtualMethodIndex(methodName, signature, this);
                if (vtableIndex != -1) {
                    superKlass.getVTable()[vtableIndex].onSubclassMethodChanged(ids);
                }
                superKlass = superKlass.getSuperKlass();
            }
        }
    }

    public void onSuperKlassUpdate() {
        KlassVersion oldVersion = klassVersion;

        Method[][] itable = oldVersion.itable;
        Method[] vtable;
        ObjectKlass[] iKlassTable;
        Method[] mirandaMethods = oldVersion.mirandaMethods;
        Method[] newDeclaredMethods = oldVersion.declaredMethods;

        if (this.isInterface()) {
            InterfaceTables.InterfaceCreationResult icr = InterfaceTables.constructInterfaceItable(this, newDeclaredMethods);
            vtable = icr.methodtable;
            iKlassTable = icr.klassTable;
        } else {
            InterfaceTables.CreationResult methodCR = InterfaceTables.create(getSuperKlass(), getSuperInterfaces(), newDeclaredMethods);
            iKlassTable = methodCR.klassTable;
            mirandaMethods = methodCR.mirandas;
            vtable = VirtualTable.create(getSuperKlass(), newDeclaredMethods, this, mirandaMethods, true);
            itable = InterfaceTables.fixTables(vtable, mirandaMethods, newDeclaredMethods, methodCR.tables, iKlassTable);
        }

        klassVersion = new KlassVersion(oldVersion.pool, oldVersion.linkedKlass, newDeclaredMethods, mirandaMethods, vtable, itable, iKlassTable);

        // flush caches before invalidating to avoid races
        // a potential thread fetching new reflection data
        // will be blocked at entry until the redefinition
        // transaction is ended
        incrementKlassRedefinitionCount();
        oldVersion.assumption.invalidate();
    }

    private List<ObjectKlass> getSubTypes() {
        if (subTypes != null) {
            List<ObjectKlass> result = new ArrayList<>();
            synchronized (subTypes) {
                for (WeakReference<ObjectKlass> subType : subTypes) {
                    ObjectKlass sub = subType.get();
                    if (sub != null) {
                        result.add(sub);
                        result.addAll(sub.getSubTypes());
                    }
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static boolean isVirtual(ParserMethod m) {
        return !Modifier.isStatic(m.getFlags()) && !Modifier.isPrivate(m.getFlags()) && !Name._init_.equals(m.getName());
    }

    public void patchClassName(Symbol<Symbol.Name> newName, Symbol<Symbol.Type> newType) {
        name = newName;
        type = newType;
    }

    public void removeByRedefinition() {
        // currently implemented by marking
        // all methods as removed
        for (Method declaredMethod : getDeclaredMethods()) {
            declaredMethod.removedByRedefinition();
        }
    }

    public final class KlassVersion {
        final Assumption assumption;
        final RuntimeConstantPool pool;
        final LinkedKlass linkedKlass;
        // Stores the VTable for classes, holds public non-static methods for interfaces.
        private final Method[] vtable;
        // TODO(garcia) Sort itables (according to an arbitrary key) for dichotomic search?
        private final Method[][] itable;
        private final ObjectKlass[] iKlassTable;
        private final Method[] declaredMethods;
        private final Method[] mirandaMethods;

        KlassVersion(RuntimeConstantPool pool, LinkedKlass linkedKlass, Method[] declaredMethods, Method[] mirandaMethods, Method[] vtable, Method[][] itable, ObjectKlass[] iKlassTable) {
            this.assumption = Truffle.getRuntime().createAssumption();
            this.pool = pool;
            this.linkedKlass = linkedKlass;
            this.declaredMethods = declaredMethods;
            this.mirandaMethods = mirandaMethods;
            this.itable = itable;
            this.vtable = vtable;
            this.iKlassTable = iKlassTable;
        }

        public Assumption getAssumption() {
            return assumption;
        }

        public ObjectKlass getKlass() {
            return ObjectKlass.this;
        }

        Object getAttribute(Symbol<Name> attrName) {
            return linkedKlass.getAttribute(attrName);
        }

        ConstantPool getConstantPool() {
            return pool;
        }

    }
}
