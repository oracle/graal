/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyAssumption;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyOracle;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyOracle.ClassHierarchyAccessor;
import com.oracle.truffle.espresso.analysis.hierarchy.SingleImplementor;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.NestHostAttribute;
import com.oracle.truffle.espresso.classfile.attributes.NestMembersAttribute;
import com.oracle.truffle.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.RecordAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceDebugExtensionAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceFileAttribute;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
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
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.verifier.MethodVerifier;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Resolved non-primitive, non-array types in Espresso.
 */
public final class ObjectKlass extends Klass {

    public static final ObjectKlass[] EMPTY_ARRAY = new ObjectKlass[0];
    public static final KlassVersion[] EMPTY_KLASSVERSION_ARRAY = new KlassVersion[0];

    private final EnclosingMethodAttribute enclosingMethod;

    @CompilationFinal //
    private StaticObject statics;

    static {
        // Ensures that 'statics' field can be non-volatile. This uses "Unsafe Local DCL + Safe
        // Singleton" as described in https://shipilev.net/blog/2014/safe-public-construction
        assert hasFinalInstanceField(StaticObject.class);
    }

    private final Klass hostKlass;

    @CompilationFinal //
    private Klass nest;

    @CompilationFinal //
    private PackageEntry packageEntry;

    private String genericSignature;

    @CompilationFinal //
    private volatile EspressoLock initLock;

    @CompilationFinal //
    private volatile int initState = LOADED;

    @CompilationFinal volatile KlassVersion klassVersion;

    // instance and hidden fields declared in this class and in its super classes
    @CompilationFinal(dimensions = 1) //
    private final Field[] fieldTable;

    // points to the first element in the FieldTable that refers to a field declared in this
    // class, or is equal to fieldTable.length if this class does not declare fields
    private final int localFieldTableIndex;

    // static fields declared in this class (no hidden fields)
    @CompilationFinal(dimensions = 1) //
    private final Field[] staticFieldTable;

    @CompilationFinal private ExtensionFieldsMetadata extensionFieldsMetadata;

    // used for class redefinition when refreshing vtables etc.
    private volatile ArrayList<WeakReference<ObjectKlass>> subTypes;

    private Source source;

    public static final int LOADED = 0;
    public static final int LINKING = 1;
    public static final int PREPARED = 2;
    public static final int LINKED = 3;
    // Can be erroneous only if initialization triggered !
    public static final int ERRONEOUS = 4;
    public static final int INITIALIZING = 5;
    public static final int INITIALIZED = 6;

    private final StaticObject definingClassLoader;

    // Class hierarchy information is managed by ClassHierarchyOracle,
    // stored in ObjectKlass only for convenience.
    // region class hierarchy information
    private final SingleImplementor implementor;
    private final ClassHierarchyAssumption noConcreteSubclassesAssumption;
    // endregion

    public Attribute getAttribute(Symbol<Name> attrName) {
        return getLinkedKlass().getAttribute(attrName);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader) {
        this(context, linkedKlass, superKlass, superInterfaces, classLoader, ClassRegistry.ClassDefinitionInfo.EMPTY);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces, StaticObject classLoader, ClassRegistry.ClassDefinitionInfo info) {
        super(context, linkedKlass.getName(), linkedKlass.getType(), linkedKlass.getFlags(), info.klassID);

        this.nest = info.dynamicNest;
        this.hostKlass = info.hostKlass;
        // TODO(peterssen): Make writable copy.
        RuntimeConstantPool pool = new RuntimeConstantPool(getContext(), linkedKlass.getConstantPool(), classLoader);
        definingClassLoader = classLoader;
        this.enclosingMethod = (EnclosingMethodAttribute) linkedKlass.getAttribute(EnclosingMethodAttribute.NAME);
        this.klassVersion = new KlassVersion(pool, linkedKlass, superKlass, superInterfaces);

        Field[] skFieldTable = superKlass != null ? superKlass.getInitialFieldTable() : new Field[0];
        LinkedField[] lkInstanceFields = linkedKlass.getInstanceFields();
        LinkedField[] lkStaticFields = linkedKlass.getStaticFields();

        fieldTable = new Field[skFieldTable.length + lkInstanceFields.length];
        staticFieldTable = new Field[lkStaticFields.length];

        assert fieldTable.length == linkedKlass.getFieldTableLength();
        System.arraycopy(skFieldTable, 0, fieldTable, 0, skFieldTable.length);
        localFieldTableIndex = skFieldTable.length;
        for (int i = 0; i < lkInstanceFields.length; i++) {
            Field instanceField = new Field(klassVersion, lkInstanceFields[i], pool);
            fieldTable[localFieldTableIndex + i] = instanceField;
        }
        for (int i = 0; i < lkStaticFields.length; i++) {
            Field staticField;
            LinkedField lkField = lkStaticFields[i];
            // User-defined static non-final fields should remain modifiable.
            if (superKlass == getMeta().java_lang_Enum && !isEnumValuesField(lkField) //
                            && Types.isReference(lkField.getType()) && Modifier.isFinal(lkField.getFlags())) {
                staticField = new EnumConstantField(klassVersion, lkField, pool);
            } else {
                staticField = new Field(klassVersion, lkField, pool);
            }
            staticFieldTable[i] = staticField;
        }

        // Only forcefully initialization of the mirror if necessary
        if (info.protectionDomain != null && !StaticObject.isNull(info.protectionDomain)) {
            // Protection domain should not be host null, and will be initialized to guest null on
            // mirror creation.
            getMeta().HIDDEN_PROTECTION_DOMAIN.setHiddenObject(initializeEspressoClass(), info.protectionDomain);
        }
        if (info.classData != null) {
            getMeta().java_lang_Class_classData.setObject(initializeEspressoClass(), info.classData);
        }
        if (!info.addedToRegistry()) {
            initSelfReferenceInPool();
        }
        this.noConcreteSubclassesAssumption = getContext().getClassHierarchyOracle().createAssumptionForNewKlass(this);
        this.implementor = getContext().getClassHierarchyOracle().initializeImplementorForNewKlass(this);
        getContext().getClassHierarchyOracle().registerNewKlassVersion(klassVersion);
        this.initState = LOADED;
        if (getMeta().java_lang_Class != null) {
            initializeEspressoClass();
        }
        assert verifyTables();
    }

    private static boolean isEnumValuesField(LinkedField lkStaticFields) {
        return lkStaticFields.getName() == Name.$VALUES ||
                        lkStaticFields.getName() == Name.ENUM$VALUES;
    }

    private void addSubType(ObjectKlass objectKlass) {
        // We only build subtypes model iff jdwp is enabled
        if (getContext().getEspressoEnv().JDWPOptions != null) {
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

    public void removeAsSubType() {
        getSuperKlass().removeSubType(this);
        for (ObjectKlass superInterface : getSuperInterfaces()) {
            superInterface.removeSubType(this);
        }
    }

    private void removeSubType(ObjectKlass klass) {
        assert subTypes != null;
        synchronized (subTypes) {
            boolean removed = false;
            Iterator<WeakReference<ObjectKlass>> it = subTypes.iterator();
            while (it.hasNext()) {
                WeakReference<ObjectKlass> next = it.next();
                if (next.get() == klass) {
                    it.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                throw EspressoError.shouldNotReachHere();
            }
        }
    }

    private boolean verifyTables() {
        Method.MethodVersion[] vtable = getKlassVersion().getVtable();
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
        Method.MethodVersion[][] itable = getItable();
        if (itable != null) {
            for (Method.MethodVersion[] table : itable) {
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
        StaticObject result = this.statics;
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            result = createStatics();
        }
        return result;
    }

    private synchronized StaticObject createStatics() {
        CompilerAsserts.neverPartOfCompilation();
        StaticObject result = this.statics;
        if (result == null) {
            this.statics = result = getAllocator().createStatics(this);
        }
        return result;
    }

    // region InitStatus

    private EspressoLock getInitLock() {
        EspressoLock iLock = initLock;
        if (iLock == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                iLock = initLock;
                if (iLock == null) {
                    iLock = this.initLock = EspressoLock.create(getContext().getBlockingSupport());
                }
            }
        }
        return iLock;
    }

    public int getState() {
        return initState;
    }

    private boolean isLinkingOrLinked() {
        return initState >= LINKING;
    }

    boolean isPrepared() {
        return initState >= PREPARED;
    }

    private boolean isLinked() {
        return initState >= LINKED;
    }

    boolean isInitializingOrInitializedImpl() {
        return (initState == INITIALIZED) ||
                        /* Initializing thread */
                        (initState == INITIALIZING && getInitLock().isHeldByCurrentThread()) ||
                        (initState == ERRONEOUS);
    }

    boolean isInitializedImpl() {
        return initState >= INITIALIZED;
    }

    private void setErroneousInitialization() {
        initState = ERRONEOUS;
    }

    boolean isErroneous() {
        return initState == ERRONEOUS;
    }

    private void checkErroneousInitialization() {
        if (isErroneous()) {
            throw throwNoClassDefFoundError();
        }
    }

    @TruffleBoundary
    private EspressoException throwNoClassDefFoundError() {
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, "Erroneous class: " + getName());
    }

    @TruffleBoundary
    private void actualInit() {
        checkErroneousInitialization();
        getInitLock().lock();
        try {
            // Double-check under lock
            checkErroneousInitialization();
            if (isInitializingOrInitializedImpl()) {
                return;
            }
            initState = INITIALIZING;
            try {
                if (!isInterface()) {
                    /*
                     * Next, if C is a class rather than an interface, then let SC be its superclass
                     * and let SI1, ..., SIn be all superinterfaces of C [...] For each S in the
                     * list [ SC, SI1, ..., SIn ], if S has not yet been initialized, then
                     * recursively perform this entire procedure for S. If necessary, verify and
                     * prepare S first.
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
                StaticObject cause = e.getGuestException();
                Meta meta = getMeta();
                if (!InterpreterToVM.instanceOf(cause, meta.java_lang_Error)) {
                    throw meta.throwExceptionWithCause(meta.java_lang_ExceptionInInitializerError, cause);
                } else {
                    throw e;
                }
            } catch (AbstractTruffleException e) {
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
        } finally {
            getInitLock().unlock();
        }
    }

    /*
     * Spec fragment: Then, initialize each final static field of C with the constant value in its
     * ConstantValue attribute (&sect;4.7.2), in the order the fields appear in the ClassFile
     * structure.
     */
    private void prepare() {
        getInitLock().lock();
        try {
            if (!isPrepared()) {
                checkLoadingConstraints();
                for (Field f : getInitialStaticFields()) {
                    if (!f.isRemoved()) {
                        initField(f);
                    }
                }
                initState = PREPARED;
                if (getContext().isMainThreadCreated()) {
                    if (getContext().shouldReportVMEvents()) {
                        prepareThread = getContext().getCurrentThread();
                        getContext().reportClassPrepared(this, prepareThread);
                    }
                }
            }
        } finally {
            getInitLock().unlock();
        }
    }

    void initField(Field f) {
        ConstantValueAttribute a = (ConstantValueAttribute) f.getAttribute(Name.ConstantValue);
        if (a == null) {
            return;
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
                CompilerAsserts.neverPartOfCompilation();
                throw EspressoError.shouldNotReachHere("invalid constant field kind");
        }
    }

    private void checkLoadingConstraints() {
        if (getSuperKlass() != null) {
            if (!isInterface()) {
                Method.MethodVersion[] thisVTable = getVTable();
                if (thisVTable != null) {
                    Method.MethodVersion[] superVTable = getSuperKlass().getVTable();
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
                Method.MethodVersion[][] itables = getItable();
                KlassVersion[] klassTable = getiKlassTable();
                for (int i = 0; i < itables.length; i++) {
                    KlassVersion interfKlass = klassTable[i];
                    Method.MethodVersion[] table = itables[i];
                    for (Method.MethodVersion m : table) {
                        if (m.getDeclaringKlass() == this) {
                            m.checkLoadingConstraints(this.getDefiningClassLoader(), interfKlass.getKlass().getDefiningClassLoader());
                        } else {
                            m.checkLoadingConstraints(interfKlass.getKlass().getDefiningClassLoader(), m.getDeclaringKlass().getDefiningClassLoader());
                            m.checkLoadingConstraints(this.getDefiningClassLoader(), m.getDeclaringKlass().getDefiningClassLoader());
                        }
                    }
                }
            }
        }
    }

    // Need to carefully synchronize, as the work of other threads can erase our own work.

    @Override
    public void ensureLinked() {
        if (!isLinked()) {
            checkErroneousVerification();
            if (CompilerDirectives.isCompilationConstant(this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            doLink();
        }
    }

    @TruffleBoundary
    private void doLink() {
        getInitLock().lock();
        try {
            if (!isLinkingOrLinked()) {
                initState = LINKING;
                if (getSuperKlass() != null) {
                    getSuperKlass().ensureLinked();
                }
                for (ObjectKlass interf : getSuperInterfaces()) {
                    interf.ensureLinked();
                }
                prepare();
                verify();
                initState = LINKED;
            }
        } finally {
            getInitLock().unlock();
        }
        checkErroneousVerification();
    }

    void initializeImpl() {
        if (!isInitializedImpl()) { // Skip synchronization and locks if already init.
            // Allow folding the exception path if erroneous
            doInitialize();
        }
    }

    @HostCompilerDirectives.InliningCutoff
    private void doInitialize() {
        checkErroneousVerification();
        checkErroneousInitialization();
        if (CompilerDirectives.isCompilationConstant(this)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        ensureLinked();
        actualInit();
    }

    private void recursiveInitialize() {
        if (!isInitializedImpl()) { // Skip synchronization and locks if already init.
            for (ObjectKlass interf : getSuperInterfaces()) {
                interf.recursiveInitialize();
            }
            if (hasDeclaredDefaultMethods()) {
                initializeImpl(); // Does not recursively initialize interfaces
            }
        }
    }

    // endregion InitStatus

    // region Verification

    @CompilationFinal //
    private volatile int verificationStatus = UNVERIFIED;

    @CompilationFinal //
    private EspressoException verificationError = null;

    private static final int FAILED_VERIFICATION = -1;
    private static final int UNVERIFIED = 0;
    private static final int VERIFYING = 1;
    private static final int VERIFIED = 2;

    private void setVerificationStatus(int status) {
        verificationStatus = status;
    }

    private boolean isVerifyingOrVerified() {
        return verificationStatus >= VERIFYING;
    }

    boolean isVerified() {
        return verificationStatus >= VERIFIED;
    }

    private void checkErroneousVerification() {
        if (verificationStatus == FAILED_VERIFICATION) {
            throw verificationError;
        }
    }

    private void setErroneousVerification(EspressoException e) {
        verificationStatus = FAILED_VERIFICATION;
        verificationError = e;
    }

    private void verify() {
        if (!isVerified()) {
            checkErroneousVerification();
            getInitLock().lock();
            try {
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
            } finally {
                getInitLock().unlock();
            }
            checkErroneousVerification();
        }
    }

    private void verifyImpl() {
        CompilerAsserts.neverPartOfCompilation();
        if (MethodVerifier.needsVerify(getLanguage(), getDefiningClassLoader())) {
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
                } catch (MethodVerifier.VerifierError e) {
                    String message = String.format("Verification for class `%s` failed for method `%s` with message `%s`", getExternalName(), m.getNameAsString(), e.getMessage());
                    switch (e.kind()) {
                        case Verify:
                            throw meta.throwExceptionWithMessage(meta.java_lang_VerifyError, message);
                        case ClassFormat:
                            throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, message);
                        case NoClassDefFound:
                            throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, message);
                    }
                }
            }
        }

    }

    // endregion Verification

    @Override
    public Klass getElementalType() {
        return this;
    }

    @Override
    public @JavaType(ClassLoader.class) StaticObject getDefiningClassLoader() {
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

    Method.MethodVersion[] getMirandaMethods() {
        return getKlassVersion().mirandaMethods;
    }

    @Override
    public Method[] getDeclaredMethods() {
        Method.MethodVersion[] declaredMethodVersions = getKlassVersion().declaredMethods;
        Method[] methods = new Method[declaredMethodVersions.length];
        for (int i = 0; i < declaredMethodVersions.length; i++) {
            methods[i] = declaredMethodVersions[i].getMethod();
        }
        return methods;
    }

    @Override
    public MethodRef[] getDeclaredMethodRefs() {
        return getDeclaredMethodVersions();
    }

    @Override
    public Method.MethodVersion[] getDeclaredMethodVersions() {
        return getKlassVersion().getDeclaredMethodVersions();
    }

    @Override
    public Field[] getDeclaredFields() {
        // Speculate that there are no hidden fields
        Field[] declaredFields = new Field[staticFieldTable.length + fieldTable.length - localFieldTableIndex];
        int insertionIndex = 0;
        for (int i = 0; i < staticFieldTable.length; i++) {
            Field f = staticFieldTable[i];
            if (!f.isHidden() && !f.isRemoved()) {
                declaredFields[insertionIndex++] = f;
            }
        }
        for (int i = localFieldTableIndex; i < fieldTable.length; i++) {
            Field f = fieldTable[i];
            if (!f.isHidden() && !f.isRemoved()) {
                declaredFields[insertionIndex++] = f;
            }
        }
        declaredFields = insertionIndex == declaredFields.length ? declaredFields : Arrays.copyOf(declaredFields, insertionIndex);
        if (getExtensionFieldsMetadata(false) != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // add from extension fields too
            Field[] extensionFields = extensionFieldsMetadata.getDeclaredAddedFields();
            declaredFields = Arrays.copyOf(declaredFields, insertionIndex + extensionFields.length);
            System.arraycopy(extensionFields, 0, declaredFields, insertionIndex, extensionFields.length);
        }
        return declaredFields;
    }

    public EnclosingMethodAttribute getEnclosingMethod() {
        return enclosingMethod;
    }

    public InnerClassesAttribute getInnerClasses() {
        return getKlassVersion().innerClasses;
    }

    public LinkedKlass getLinkedKlass() {
        return getKlassVersion().linkedKlass;
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

    public boolean isSealed() {
        PermittedSubclassesAttribute permittedSubclasses = (PermittedSubclassesAttribute) getAttribute(PermittedSubclassesAttribute.NAME);
        return permittedSubclasses != null && permittedSubclasses.getClasses().length > 0;
    }

    public boolean permittedSubclassCheck(ObjectKlass k) {
        CompilerAsserts.neverPartOfCompilation();
        if (!getContext().getJavaVersion().java17OrLater()) {
            return true;
        }
        PermittedSubclassesAttribute permittedSubclasses = (PermittedSubclassesAttribute) getAttribute(PermittedSubclassesAttribute.NAME);
        if (permittedSubclasses == null) {
            return true;
        }
        if (module() != k.module()) {
            return false;
        }
        if (!isPublic() && !sameRuntimePackage(k)) {
            return false;
        }
        RuntimeConstantPool pool = getConstantPool();
        for (int index : permittedSubclasses.getClasses()) {
            if (k.getName().equals(pool.classAt(index).getName(pool))) {
                // There should be no need to resolve: the previous checks guarantees it would
                // resolve to k, but resolving here would cause circularity errors.
                return true;
            }
        }
        return false;
    }

    @Override
    public Klass[] getNestMembers() {
        if (this != nest()) {
            return nest().getNestMembers();
        }
        NestMembersAttribute nestMembers = (NestMembersAttribute) getAttribute(NestMembersAttribute.NAME);
        if (nestMembers == null || nestMembers.getClasses().length == 0) {
            return new Klass[]{nest()};
        }
        RuntimeConstantPool pool = getConstantPool();
        ArrayList<Klass> klasses = new ArrayList<>(1 + nestMembers.getClasses().length);
        klasses.add(nest());
        for (int i = 0; i < nestMembers.getClasses().length; i++) {
            int index = nestMembers.getClasses()[i];
            try {
                klasses.add(pool.resolvedKlassAt(this, index));
            } catch (EspressoException e) {
                /*
                 * Don't allow badly constructed nest members to break execution here, only report
                 * well-constructed entries.
                 */
            }
        }
        return klasses.toArray(Klass.EMPTY_ARRAY);
    }

    Field lookupFieldTableImpl(int slot) {
        if (slot >= 0) {
            assert slot < fieldTable.length && !fieldTable[slot].isHidden();
            return fieldTable[slot];
        } else { // negative values used for extension fields
            ObjectKlass objectKlass = this;
            while (objectKlass != null) {
                if (objectKlass.extensionFieldsMetadata != null) {
                    Field field = objectKlass.extensionFieldsMetadata.getInstanceFieldAtSlot(slot);
                    if (field != null) {
                        return field;
                    }
                }
                objectKlass = objectKlass.getSuperKlass();
            }
            throw new IndexOutOfBoundsException("Index out of range: " + slot);
        }
    }

    Field lookupStaticFieldTableImpl(int slot) {
        if (slot >= 0) {
            assert slot < staticFieldTable.length;
            return staticFieldTable[slot];
        } else { // negative values used for extension fields
            return extensionFieldsMetadata.getStaticFieldAtSlot(slot);
        }
    }

    public Field requireHiddenField(Symbol<Name> fieldName) {
        // Hidden fields are (usually) located at the end of the field table.
        Field[] fTable = fieldTable;
        for (int i = fTable.length - 1; i >= 0; i--) {
            Field f = fTable[i];
            if (f.getName() == fieldName && f.isHidden()) {
                return f;
            }
        }
        throw EspressoError.shouldNotReachHere("Missing hidden field " + fieldName + " in " + this);
    }

    // Exposed to LookupVirtualMethodNode
    public Method.MethodVersion[] getVTable() {
        assert !isInterface();
        return getKlassVersion().getVtable();
    }

    public Method.MethodVersion[] getInterfaceMethodsTable() {
        assert isInterface();
        return getKlassVersion().getVtable();
    }

    public Method.MethodVersion[][] getItable() {
        return getKlassVersion().getItable();
    }

    public ObjectKlass.KlassVersion[] getiKlassTable() {
        return getKlassVersion().getiKlassTable();
    }

    Method vtableLookupImpl(int vtableIndex) {
        assert (vtableIndex >= 0) : "Undeclared virtual method";
        return getVTable()[vtableIndex].getMethod();
    }

    public Method itableLookup(Klass interfKlass, int index) {
        assert (index >= 0) : "Undeclared interface method";
        try {
            return getItable()[fastLookup(interfKlass, getiKlassTable())][index].getMethod();
        } catch (IndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "Class %s does not implement interface %s", getName(), interfKlass.getName());
        }
    }

    int findVirtualMethodIndex(Symbol<Name> methodName, Symbol<Signature> signature, Klass subClass) {
        for (int i = 0; i < getVTable().length; i++) {
            Method.MethodVersion m = getVTable()[i];
            if (!m.isStatic() && !m.isPrivate() && m.getName() == methodName && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic() || m.getDeclaringKlass().sameRuntimePackage(subClass)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public void lookupVirtualMethodOverrides(Method current, Klass subKlass, List<Method.MethodVersion> result) {
        Symbol<Name> methodName = current.getName();
        Symbol<Signature> signature = current.getRawSignature();
        for (Method.MethodVersion m : getVTable()) {
            if (!m.isStatic() && !m.isPrivate() && m.getName() == methodName && m.getRawSignature() == signature) {
                if (m.isProtected() || m.isPublic()) {
                    result.add(m);
                } else {
                    if (m.getMethod().getDeclaringKlass().sameRuntimePackage(subKlass)) {
                        result.add(m);
                    } else {
                        ObjectKlass currentKlass = this.getSuperKlass();
                        int index = m.getVTableIndex();
                        while (currentKlass != null) {
                            if (index >= currentKlass.getVTable().length) {
                                break;
                            }
                            Method.MethodVersion toExamine = currentKlass.getVTable()[index];
                            if (current.canOverride(toExamine.getMethod())) {
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
            ObjectKlass superInterf = getiKlassTable()[i].getKlass();
            for (Method.MethodVersion superMVersion : superInterf.getInterfaceMethodsTable()) {
                Method superM = superMVersion.getMethod();
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
                        resolved = InterfaceTables.resolveMaximallySpecific(resolved, superM).getMethod();
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
    public Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass, LookupMode lookupMode) {
        KLASS_LOOKUP_METHOD_COUNT.inc();
        Method method = lookupDeclaredMethod(methodName, signature, lookupMode);
        if (method == null) {
            // Implicit interface methods.
            method = lookupMirandas(methodName, signature);
        }
        if (method == null &&
                        (getType() == Type.java_lang_invoke_MethodHandle ||
                                        getType() == Type.java_lang_invoke_VarHandle)) {
            method = lookupPolysigMethod(methodName, signature, lookupMode);
        }
        if (method == null && getSuperKlass() != null) {
            method = getSuperKlass().lookupMethod(methodName, signature, accessingKlass, lookupMode);
        }
        return method;
    }

    public Field[] getFieldTable() {
        if (!getContext().advancedRedefinitionEnabled()) {
            return fieldTable;
        }
        ExtensionFieldsMetadata extensionMetadata = getExtensionFieldsMetadata(false);
        if (extensionMetadata != null) {
            Field[] addedFields = extensionMetadata.getAddedInstanceFields();
            Field[] allFields = new Field[fieldTable.length + addedFields.length];
            System.arraycopy(fieldTable, 0, allFields, 0, fieldTable.length);
            System.arraycopy(addedFields, 0, allFields, fieldTable.length, addedFields.length);
            return allFields;
        } else {
            // Note that caller is responsible for filtering out removed fields
            return fieldTable;
        }
    }

    public Field[] getInitialFieldTable() {
        return fieldTable;
    }

    public Field[] getInitialStaticFields() {
        // Note that caller is responsible for filtering out removed fields
        return staticFieldTable;
    }

    public Field[] getStaticFieldTable() {
        if (!getContext().advancedRedefinitionEnabled()) {
            return staticFieldTable;
        }
        // add non-removed fields from static field table
        ExtensionFieldsMetadata extensionMetadata = getExtensionFieldsMetadata(false);
        if (extensionMetadata != null) {
            Field[] addedStaticFields = extensionMetadata.getAddedStaticFields();
            Field[] allStaticFields = new Field[staticFieldTable.length + addedStaticFields.length];
            System.arraycopy(staticFieldTable, 0, allStaticFields, 0, staticFieldTable.length);
            System.arraycopy(addedStaticFields, 0, allStaticFields, staticFieldTable.length, addedStaticFields.length);
            return allStaticFields;
        } else {
            // Note that caller is responsible for filtering out removed fields
            return staticFieldTable;
        }
    }

    private Method lookupMirandas(Symbol<Name> methodName, Symbol<Signature> signature) {
        if (getMirandaMethods() == null) {
            return null;
        }
        for (Method.MethodVersion miranda : getMirandaMethods()) {
            Method method = miranda.getMethod();
            if (method.getName() == methodName && method.getRawSignature() == signature) {
                return method;
            }
        }
        return null;
    }

    void print(PrintStream out) {
        out.println(getType());
        for (Method m : getDeclaredMethods()) {
            out.println(m);
            m.printBytecodes(out);
            out.println();
        }
    }

    public boolean hasFinalizer(EspressoContext context) {
        return (getModifiers(context) & ACC_FINALIZER) != 0;
    }

    @TruffleBoundary
    public List<Symbol<Name>> getNestedTypeNames() {
        ArrayList<Symbol<Name>> result = new ArrayList<>();
        InnerClassesAttribute innerClasses = getKlassVersion().innerClasses;
        if (innerClasses != null) {
            for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
                if (entry.innerClassIndex != 0) {
                    result.add(getConstantPool().classAt(entry.innerClassIndex).getName(getConstantPool()));
                }
            }
        }
        return result;
    }

    private void initPackage(@JavaType(ClassLoader.class) StaticObject classLoader) {
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

    @Override
    public int getClassModifiers() {
        return getKlassVersion().getClassModifiers();
    }

    @Override
    public Klass[] getSuperTypes() {
        return getKlassVersion().getSuperTypes();
    }

    @Override
    protected int getHierarchyDepth() {
        return getKlassVersion().getHierarchyDepth();
    }

    @Override
    protected ObjectKlass.KlassVersion[] getTransitiveInterfacesList() {
        return getKlassVersion().getTransitiveInterfacesList();
    }

    /**
     * Returns true if the interface has declared (not inherited) default methods, false otherwise.
     */
    private boolean hasDeclaredDefaultMethods() {
        assert !getKlassVersion().hasDeclaredDefaultMethods || isInterface();
        return getKlassVersion().hasDeclaredDefaultMethods;
    }

    public void initSelfReferenceInPool() {
        getConstantPool().setKlassAt(getLinkedKlass().getParserKlass().getThisKlassIndex(), this);
    }

    public boolean isRecord() {
        return isFinalFlagSet() && getSuperKlass() == getMeta().java_lang_Record && getAttribute(RecordAttribute.NAME) != null;
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

    public SingleImplementor getImplementor(ClassHierarchyAccessor accessor) {
        Objects.requireNonNull(accessor);
        return getKlassVersion().getImplementor(accessor);
    }

    @Override
    public ObjectKlass getSuperKlass() {
        return getKlassVersion().superKlass;
    }

    @Override
    public ObjectKlass[] getSuperInterfaces() {
        return getKlassVersion().superInterfaces;
    }

    @Override
    public Assumption getRedefineAssumption() {
        return getKlassVersion().getAssumption();
    }

    public KlassVersion getKlassVersion() {
        KlassVersion cache = klassVersion;
        if (!cache.assumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            do {
                cache = klassVersion;
            } while (!cache.assumption.isValid());
        }
        return cache;
    }

    public void redefineClass(ChangePacket packet, List<ObjectKlass> invalidatedClasses, Ids<Object> ids) {
        DetectedChange change = packet.detectedChange;

        if (change.isChangedSuperClass()) {
            // this class was marked as a super class change, which means at least one subclass
            // hereof has this class as a new superclass.

            // All fields must be redefined as a removed -> added combo to
            // allow old instances of the changed subclass to access the fields
            // within this class.
            if (getDeclaredFields().length > 0) {
                ExtensionFieldsMetadata extension = getExtensionFieldsMetadata(true);
                for (Field declaredField : getDeclaredFields()) {
                    if (!declaredField.isStatic()) {
                        declaredField.removeByRedefintion();

                        int nextFieldSlot = getContext().getClassRedefinition().getNextAvailableFieldSlot();
                        LinkedField.IdMode mode = LinkedKlassFieldLayout.getIdMode(getLinkedKlass().getParserKlass());
                        LinkedField linkedField = new LinkedField(declaredField.linkedField.getParserField(), nextFieldSlot, mode);
                        Field field = new RedefineAddedField(getKlassVersion(), linkedField, getConstantPool(), false);
                        extension.addNewInstanceField(field);
                    }
                }
            }
        }
        if (packet.parserKlass == null) {
            // no further changes
            return;
        }

        ParserKlass parserKlass = packet.parserKlass;
        KlassVersion oldVersion = klassVersion;
        LinkedKlass oldLinkedKlass = oldVersion.linkedKlass;
        RuntimeConstantPool pool = new RuntimeConstantPool(getContext(), parserKlass.getConstantPool(), oldVersion.pool.getClassLoader());

        // class structure
        ObjectKlass[] superInterfaces = change.getSuperInterfaces();
        LinkedKlass[] interfaces = new LinkedKlass[superInterfaces.length];
        for (int i = 0; i < superInterfaces.length; i++) {
            interfaces[i] = superInterfaces[i].getLinkedKlass();
        }
        LinkedKlass linkedKlass;
        if (Modifier.isInterface(change.getSuperKlass().getModifiers())) {
            linkedKlass = LinkedKlass.redefine(parserKlass, null, interfaces, oldLinkedKlass);
        } else {
            linkedKlass = LinkedKlass.redefine(parserKlass, change.getSuperKlass().getLinkedKlass(), interfaces, oldLinkedKlass);
        }
        klassVersion = new KlassVersion(oldVersion, pool, linkedKlass, packet, invalidatedClasses, ids);

        // fields
        if (!change.getAddedStaticFields().isEmpty() || !change.getAddedInstanceFields().isEmpty()) {
            Map<ParserField, Field> compatibleFields = change.getMappedCompatibleFields();

            ExtensionFieldsMetadata extension = getExtensionFieldsMetadata(true);
            // add new fields to the extension object
            extension.addNewStaticFields(klassVersion, change.getAddedStaticFields(), pool, compatibleFields, getContext().getClassRedefinition());
            extension.addNewInstanceFields(klassVersion, change.getAddedInstanceFields(), pool, compatibleFields, getContext().getClassRedefinition());

            // make sure all new fields trigger re-resolution of fields
            // with same name + type in the full class hierarchy
            markForReResolution(change.getAddedStaticFields(), invalidatedClasses);
            markForReResolution(change.getAddedInstanceFields(), invalidatedClasses);
        }

        for (Field removedField : change.getRemovedFields()) {
            removedField.removeByRedefintion();
        }

        getContext().getClassHierarchyOracle().registerNewKlassVersion(klassVersion);

        incrementKlassRedefinitionCount();
        oldVersion.assumption.invalidate();
    }

    private ExtensionFieldsMetadata getExtensionFieldsMetadata(boolean create) {
        ExtensionFieldsMetadata metadata = extensionFieldsMetadata;
        if (metadata == null) {
            if (create) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                metadata = createExtensionFieldsMetadata();
            }
        }
        return metadata;
    }

    @TruffleBoundary
    private synchronized ExtensionFieldsMetadata createExtensionFieldsMetadata() {
        if (extensionFieldsMetadata == null) {
            extensionFieldsMetadata = new ExtensionFieldsMetadata();
        }
        return extensionFieldsMetadata;
    }

    private void markForReResolution(List<ParserField> addedFields, List<ObjectKlass> invalidatedClasses) {
        for (ParserField addedField : addedFields) {
            // search the super hierarchy
            ObjectKlass superClass = getSuperKlass();
            while (superClass != null) {
                Field field = superClass.lookupDeclaredField(addedField.getName(), addedField.getType());
                if (field != null) {
                    invalidatedClasses.add(superClass);
                }
                superClass = superClass.getSuperKlass();
            }

            // search in subtypes
            List<ObjectKlass> klass = getSubTypes();
            for (ObjectKlass subType : klass) {
                Field field = subType.lookupDeclaredField(addedField.getName(), addedField.getType());
                if (field != null) {
                    invalidatedClasses.add(subType);
                }
            }
        }
    }

    // used by some plugins during klass redefinition
    public void reRunClinit() {
        getClassInitializer().getCallTarget().call();
    }

    private static void checkCopyMethods(KlassVersion klassVersion, Method method, Method.MethodVersion[][] table, Method.SharedRedefinitionContent content, Ids<Object> ids) {
        for (Method.MethodVersion[] methods : table) {
            checkCopyMethods(klassVersion, method, methods, content, ids);
        }
    }

    private static void checkCopyMethods(KlassVersion klassVersion, Method method, Method.MethodVersion[] table, Method.SharedRedefinitionContent content, Ids<Object> ids) {
        for (Method.MethodVersion m : table) {
            Method otherMethod = m.getMethod();
            if (method.identity() == otherMethod.identity() && otherMethod != method) {
                otherMethod.redefine(klassVersion, content, ids);
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
    private void checkSuperMethods(ObjectKlass superKlass, int flags, Symbol<Name> methodName, Symbol<Signature> signature, List<ObjectKlass> invalidatedClasses) {
        if (!Modifier.isStatic(flags) && !Modifier.isPrivate(flags) && !Name._init_.equals(methodName)) {
            ObjectKlass currentKlass = this;
            ObjectKlass currentSuper = superKlass;
            while (currentSuper != null) {
                // look for the method
                int vtableIndex = currentSuper.findVirtualMethodIndex(methodName, signature, currentKlass);
                if (vtableIndex != -1) {
                    invalidatedClasses.add(currentSuper);
                }
                currentKlass = currentSuper;
                currentSuper = currentSuper.getSuperKlass();
            }
        }
    }

    public void swapKlassVersion(Ids<Object> ids) {
        KlassVersion oldVersion = klassVersion;
        klassVersion = oldVersion.replace(ids);
        getContext().getClassHierarchyOracle().registerNewKlassVersion(klassVersion);
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

    /**
     * This getter must only be used by {@link ClassHierarchyOracle}, which is ensured by
     * {@code assumptionAccessor}. The assumption is stored in ObjectKlass for easy mapping between
     * classes and corresponding assumptions.
     *
     * @see ClassHierarchyOracle#isLeafKlass(ObjectKlass)
     * @see ClassHierarchyOracle#hasNoImplementors(ObjectKlass)
     */
    public ClassHierarchyAssumption getNoConcreteSubclassesAssumption(ClassHierarchyAccessor assumptionAccessor) {
        Objects.requireNonNull(assumptionAccessor);
        return getKlassVersion().getNoConcreteSubclassesAssumption(assumptionAccessor);
    }

    Field getRemovedField(Field other) {
        for (Field field : getFieldTable()) {
            if (field.isRemoved()) {
                if (field.getName() == other.getName() && field.getType() == other.getType()) {
                    return field;
                }
            }
        }
        return null;
    }

    public String getSourceFile() {
        return getKlassVersion().getSourceFile();
    }

    public Source getSource() {
        return getKlassVersion().getSource();
    }

    public final class KlassVersion {
        final Assumption assumption;
        final RuntimeConstantPool pool;
        final LinkedKlass linkedKlass;
        final ObjectKlass superKlass;
        @CompilationFinal(dimensions = 1) final ObjectKlass[] superInterfaces;
        // Stores the VTable for classes, holds public non-static methods for interfaces.
        @CompilationFinal(dimensions = 1) private final Method.MethodVersion[] vtable;
        // TODO(garcia) Sort itables (according to an arbitrary key) for dichotomic search?
        @CompilationFinal(dimensions = 2) private final Method.MethodVersion[][] itable;
        @CompilationFinal(dimensions = 1) private final KlassVersion[] iKlassTable;
        @CompilationFinal(dimensions = 1) private final Method.MethodVersion[] declaredMethods;
        @CompilationFinal(dimensions = 1) private final Method.MethodVersion[] mirandaMethods;
        private final InnerClassesAttribute innerClasses;
        private final int modifiers;
        @CompilationFinal private int computedModifiers = -1;

        @CompilationFinal //
        boolean hasDeclaredDefaultMethods = false;

        @CompilationFinal private HierarchyInfo hierarchyInfo;

        // used to create the first version only
        private KlassVersion(RuntimeConstantPool pool, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces) {
            this.assumption = Truffle.getRuntime().createAssumption();
            this.superKlass = superKlass;
            this.superInterfaces = superInterfaces;
            this.pool = pool;
            this.linkedKlass = linkedKlass;
            this.modifiers = linkedKlass.getFlags();
            this.innerClasses = (InnerClassesAttribute) linkedKlass.getAttribute(InnerClassesAttribute.NAME);

            LinkedMethod[] linkedMethods = linkedKlass.getLinkedMethods();

            Method.MethodVersion[] methods = new Method.MethodVersion[linkedMethods.length];
            for (int i = 0; i < linkedMethods.length; i++) {
                methods[i] = new Method(this, linkedMethods[i], pool).getMethodVersion();
            }

            // Package initialization must be done before vtable creation,
            // as there are same package checks.
            initPackage(pool.getClassLoader());

            if (isInterface()) {
                InterfaceTables.InterfaceCreationResult icr = InterfaceTables.constructInterfaceItable(this, superInterfaces, methods);
                vtable = icr.methodtable;
                iKlassTable = icr.klassTable;
                mirandaMethods = null;
                itable = null;
            } else {
                InterfaceTables.CreationResult methodCR = InterfaceTables.create(superKlass, superInterfaces, methods);
                iKlassTable = methodCR.klassTable;
                mirandaMethods = methodCR.mirandas;
                vtable = VirtualTable.create(superKlass, methods, this, mirandaMethods, false);
                itable = InterfaceTables.fixTables(this, vtable, mirandaMethods, methods, methodCR.tables, iKlassTable);
            }
            if (superKlass != null) {
                superKlass.addSubType(getKlass());
            }
            for (ObjectKlass superInterface : superInterfaces) {
                superInterface.addSubType(getKlass());
            }

            this.declaredMethods = methods;
        }

        // used to create a redefined version
        private KlassVersion(KlassVersion oldVersion, RuntimeConstantPool pool, LinkedKlass linkedKlass, ChangePacket packet, List<ObjectKlass> invalidatedClasses, Ids<Object> ids) {
            this.assumption = Truffle.getRuntime().createAssumption();
            this.pool = pool;
            this.linkedKlass = linkedKlass;
            this.modifiers = linkedKlass.getFlags();
            this.innerClasses = (InnerClassesAttribute) linkedKlass.getAttribute(InnerClassesAttribute.NAME);

            DetectedChange change = packet.detectedChange;
            this.superKlass = change.getSuperKlass();
            this.superInterfaces = change.getSuperInterfaces();

            Set<Method.MethodVersion> removedMethods = change.getRemovedMethods();
            List<ParserMethod> addedMethods = change.getAddedMethods();

            LinkedList<Method.MethodVersion> newDeclaredMethods = new LinkedList<>(Arrays.asList(oldVersion.declaredMethods));
            newDeclaredMethods.removeAll(removedMethods);

            // in case of an added/removed virtual method, we must also update the tables
            // which might have ripple implications on all subclasses
            boolean virtualMethodsModified = false;

            for (Method.MethodVersion removedMethod : removedMethods) {
                virtualMethodsModified |= isVirtual(removedMethod.getLinkedMethod().getParserMethod());
                ParserMethod parserMethod = removedMethod.getLinkedMethod().getParserMethod();
                checkSuperMethods(superKlass, parserMethod.getFlags(), parserMethod.getName(), parserMethod.getSignature(), invalidatedClasses);
                removedMethod.getMethod().removedByRedefinition();
                JDWP.LOGGER.fine(() -> "Removed method " + removedMethod.getMethod().getDeclaringKlass().getName() + "." + removedMethod.getLinkedMethod().getName());
            }

            for (ParserMethod addedMethod : addedMethods) {
                LinkedMethod linkedMethod = new LinkedMethod(addedMethod);
                Method.MethodVersion added = new Method(this, linkedMethod, pool).getMethodVersion();
                newDeclaredMethods.addLast(added);
                virtualMethodsModified |= isVirtual(addedMethod);
                checkSuperMethods(superKlass, addedMethod.getFlags(), addedMethod.getName(), addedMethod.getSignature(), invalidatedClasses);
                JDWP.LOGGER.fine(() -> "Added method " + added.getMethod().getDeclaringKlass().getName() + "." + added.getName());
            }

            if (virtualMethodsModified) {
                invalidatedClasses.addAll(getSubTypes());
            }

            Method.MethodVersion[] methods = newDeclaredMethods.toArray(new Method.MethodVersion[0]);
            // first replace existing method versions with new versions
            // to ensure new declared methods are up-to-date
            Map<Method, ParserMethod> changedMethodBodies = change.getChangedMethodBodies();
            Map<Method, Method.SharedRedefinitionContent> copyCheckMap = new HashMap<>();
            for (int i = 0; i < methods.length; i++) {
                Method declMethod = methods[i].getMethod();
                if (changedMethodBodies.containsKey(declMethod)) {
                    ParserMethod newMethod = changedMethodBodies.get(declMethod);
                    Method.SharedRedefinitionContent redefineContent = declMethod.redefine(this, newMethod, packet.parserKlass, ids);
                    JDWP.LOGGER.fine(() -> "Redefining method " + declMethod.getDeclaringKlass().getName() + "." + declMethod.getName());
                    methods[i] = redefineContent.getMethodVersion();

                    int flags = newMethod.getFlags();
                    if (!Modifier.isStatic(flags) && !Modifier.isPrivate(flags) && !Name._init_.equals(newMethod.getName())) {
                        copyCheckMap.put(declMethod, redefineContent);
                    }
                }
                if (change.getUnchangedMethods().contains(declMethod)) {
                    methods[i] = declMethod.swapMethodVersion(this, ids);
                }
            }
            // create new tables
            if (isInterface()) {
                InterfaceTables.InterfaceCreationResult icr = InterfaceTables.constructInterfaceItable(this, superInterfaces, methods);
                vtable = icr.methodtable;
                iKlassTable = icr.klassTable;
                mirandaMethods = null;
                itable = null;
            } else {
                InterfaceTables.CreationResult methodCR = InterfaceTables.create(superKlass, superInterfaces, methods);
                iKlassTable = methodCR.klassTable;
                mirandaMethods = methodCR.mirandas;
                vtable = VirtualTable.create(superKlass, methods, this, mirandaMethods, true);
                itable = InterfaceTables.fixTables(this, vtable, mirandaMethods, methods, methodCR.tables, iKlassTable);
            }

            // check and replace copied methods too
            for (Map.Entry<Method, Method.SharedRedefinitionContent> entry : copyCheckMap.entrySet()) {
                Method key = entry.getKey();
                Method.SharedRedefinitionContent value = entry.getValue();

                checkCopyMethods(this, key, itable, value, ids);
                checkCopyMethods(this, key, vtable, value, ids);
                checkCopyMethods(this, key, mirandaMethods, value, ids);
            }

            // only update subtype lists if class hierarchy changed
            if (packet.classChange == ClassRedefinition.ClassChange.CLASS_HIERARCHY_CHANGED) {
                if (superKlass != null) {
                    superKlass.addSubType(getKlass());
                }
                for (ObjectKlass superInterface : superInterfaces) {
                    superInterface.addSubType(getKlass());
                }
            }

            this.declaredMethods = methods;
        }

        public KlassVersion replace(Ids<Object> ids) {
            DetectedChange detectedChange = new DetectedChange();
            detectedChange.addSuperKlass(superKlass);
            detectedChange.addSuperInterfaces(superInterfaces);
            for (Method.MethodVersion declaredMethod : declaredMethods) {
                detectedChange.addUnchangedMethod(declaredMethod.getMethod());
            }
            for (Method.MethodVersion mirandaMethod : mirandaMethods) {
                detectedChange.addUnchangedMethod(mirandaMethod.getMethod());
            }

            ChangePacket packet = new ChangePacket(null, linkedKlass.getParserKlass(), null, detectedChange);
            return new KlassVersion(this, pool, linkedKlass, packet, Collections.emptyList(), ids);
        }

        public Method.MethodVersion[][] getItable() {
            return itable;
        }

        public Method.MethodVersion[] getDeclaredMethodVersions() {
            return declaredMethods;
        }

        public KlassVersion[] getiKlassTable() {
            return iKlassTable;
        }

        public Assumption getAssumption() {
            return assumption;
        }

        public ObjectKlass getKlass() {
            return ObjectKlass.this;
        }

        public ObjectKlass getSuperKlass() {
            return superKlass;
        }

        public ObjectKlass[] getSuperInterfaces() {
            return superInterfaces;
        }

        public ClassHierarchyAssumption getNoConcreteSubclassesAssumption(ClassHierarchyAccessor assumptionAccessor) {
            Objects.requireNonNull(assumptionAccessor);
            return noConcreteSubclassesAssumption;
        }

        public SingleImplementor getImplementor(ClassHierarchyAccessor accessor) {
            Objects.requireNonNull(accessor);
            return implementor;
        }

        Object getAttribute(Symbol<Name> attrName) {
            return linkedKlass.getAttribute(attrName);
        }

        ConstantPool getConstantPool() {
            return pool;
        }

        public boolean isFinalFlagSet() {
            return Modifier.isFinal(modifiers);
        }

        public boolean isInterface() {
            return Modifier.isInterface(modifiers);
        }

        public boolean isAbstract() {
            return Modifier.isAbstract(modifiers);
        }

        public boolean isConcrete() {
            return !isAbstract();
        }

        public int getModifiers() {
            return modifiers;
        }

        public Method.MethodVersion[] getVtable() {
            return vtable;
        }

        @TruffleBoundary
        private int computeModifiers() {
            int flags = modifiers;
            if (innerClasses != null) {
                for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
                    if (entry.innerClassIndex != 0) {
                        Symbol<Name> innerClassName = getConstantPool().classAt(entry.innerClassIndex).getName(getConstantPool());
                        if (innerClassName.equals(getName())) {
                            flags = entry.innerClassAccessFlags;
                            break;
                        }
                    }
                }
            }
            return flags;
        }

        public int getClassModifiers() {
            int flags = computedModifiers;
            if (flags == -1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                computedModifiers = flags = computeModifiers();
            }
            // Remember to strip ACC_SUPER bit
            return flags & ~ACC_SUPER & JVM_ACC_WRITTEN_FLAGS;
        }

        // index 0 is Object, index hierarchyDepth is this
        public Klass[] getSuperTypes() {
            return getHierarchyInfo().supertypesWithSelfCache;
        }

        public int getHierarchyDepth() {
            return getHierarchyInfo().hierarchyDepth;
        }

        public ObjectKlass.KlassVersion[] getTransitiveInterfacesList() {
            return getHierarchyInfo().transitiveInterfaceCache;
        }

        private HierarchyInfo getHierarchyInfo() {
            HierarchyInfo info = hierarchyInfo;
            if (info == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                info = hierarchyInfo = updateHierarchyInfo();
            }
            return info;
        }

        private HierarchyInfo updateHierarchyInfo() {
            int depth = superKlass == null ? 0 : superKlass.getHierarchyDepth() + 1;

            Klass[] supertypes;
            if (superKlass == null) {
                supertypes = new Klass[]{this.getKlass()};
            } else {
                Klass[] superKlassTypes = superKlass.getSuperTypes();
                supertypes = new Klass[superKlassTypes.length + 1];
                assert supertypes.length == depth + 1;
                System.arraycopy(superKlassTypes, 0, supertypes, 0, depth);
                supertypes[depth] = this.getKlass();
            }
            return new HierarchyInfo(supertypes, depth, iKlassTable);
        }

        public String getSourceFile() {
            SourceFileAttribute sfa = (SourceFileAttribute) getAttribute(Name.SourceFile);
            if (sfa == null) {
                return null;
            }
            return getConstantPool().utf8At(sfa.getSourceFileIndex()).toString();
        }

        public Source getSource() {
            if (source == null) {
                source = getContext().findOrCreateSource(ObjectKlass.this);
            }
            return source;
        }
    }
}
