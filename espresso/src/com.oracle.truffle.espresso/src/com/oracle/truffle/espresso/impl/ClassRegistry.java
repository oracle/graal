/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.cds.ArchivedRegistryData;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.NameSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.perf.DebugCloseable;
import com.oracle.truffle.espresso.classfile.perf.DebugTimer;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.DefineKlassListener;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * A {@link ClassRegistry} maps type names to resolved {@link Klass} instances. Each class loader is
 * associated with a {@link ClassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public abstract class ClassRegistry {

    private final DynamicModuleWrapper dynamicModuleWrapper = new DynamicModuleWrapper();

    /**
     * Storage class used to propagate information in the case of special kinds of class definition
     * (hidden, anonymous or with a specified protection domain).
     *
     * Regular class definitions will use the {@link #EMPTY} instance.
     *
     * Hidden and Unsafe anonymous classes are handled by not registering them in the class loader
     * registry.
     */
    public static final class ClassDefinitionInfo {
        public static final ClassDefinitionInfo EMPTY = new ClassDefinitionInfo(null, null, null, null, null, false, false, false);

        // Constructor for regular definition, but with a specified protection domain
        public ClassDefinitionInfo(StaticObject protectionDomain) {
            this(protectionDomain, null, null, null, null, false, false, false);
        }

        // Constructor for Unsafe anonymous class definition.
        public ClassDefinitionInfo(StaticObject protectionDomain, ObjectKlass hostKlass, StaticObject[] patches) {
            this(protectionDomain, hostKlass, patches, null, null, false, false, true);
        }

        // Constructor for Hidden class definition.
        public ClassDefinitionInfo(StaticObject protectionDomain, ObjectKlass dynamicNest, StaticObject classData, boolean isStrongHidden, boolean forceAllowVMAnnotations) {
            this(protectionDomain, null, null, dynamicNest, classData, true, isStrongHidden, forceAllowVMAnnotations);
        }

        private ClassDefinitionInfo(StaticObject protectionDomain,
                        ObjectKlass hostKlass,
                        StaticObject[] patches,
                        ObjectKlass dynamicNest,
                        StaticObject classData,
                        boolean isHidden,
                        boolean isStrongHidden,
                        boolean forceAllowVMAnnotations) {
            // isStrongHidden => isHidden
            assert !isStrongHidden || isHidden;
            this.protectionDomain = protectionDomain;
            this.hostKlass = hostKlass;
            this.patches = patches;
            this.dynamicNest = dynamicNest;
            this.classData = classData;
            this.isHidden = isHidden;
            this.isStrongHidden = isStrongHidden;
            this.forceAllowVMAnnotations = forceAllowVMAnnotations;
            assert isAnonymousClass() || patches == null;
        }

        public final StaticObject protectionDomain;

        // Unsafe Anonymous class
        public final ObjectKlass hostKlass;
        public final StaticObject[] patches;

        // Hidden class
        public final ObjectKlass dynamicNest;
        public final StaticObject classData;
        public final boolean isHidden;
        public final boolean isStrongHidden;
        public final boolean forceAllowVMAnnotations;

        public boolean addedToRegistry() {
            return !isAnonymousClass() && !isHidden();
        }

        public boolean isAnonymousClass() {
            return hostKlass != null;
        }

        public boolean isHidden() {
            return isHidden;
        }

        public boolean isStrongHidden() {
            return isStrongHidden;
        }

        public boolean forceAllowVMAnnotations() {
            return forceAllowVMAnnotations;
        }

        public int patchFlags(int classFlags) {
            int flags = classFlags;
            if (isHidden()) {
                flags |= Constants.ACC_IS_HIDDEN_CLASS;
            }
            return flags;
        }
    }

    private static final DebugTimer KLASS_PROBE = DebugTimer.create("klass probe");
    private static final DebugTimer KLASS_DEFINE = DebugTimer.create("klass define");
    private static final DebugTimer KLASS_PARSE = DebugTimer.create("klass parse");

    /**
     * Traces the classes being initialized by this thread. Its only use is to be able to detect
     * class circularity errors. A class being defined, that needs its superclass also to be defined
     * will be pushed onto this stack. If the superclass is already present, then there is a
     * circularity error.
     */
    // TODO: Rework this, a thread local is certainly less than optimal.

    private DefineKlassListener defineKlassListener;

    public void registerOnLoadListener(DefineKlassListener listener) {
        defineKlassListener = listener;
    }

    public static final class TypeStack {
        Node head;

        public TypeStack() {
        }

        static final class Node {
            Symbol<Type> entry;
            Node next;

            Node(Symbol<Type> entry, Node next) {
                this.entry = entry;
                this.next = next;
            }
        }

        boolean isEmpty() {
            return head == null;
        }

        boolean contains(Symbol<Type> type) {
            Node curr = head;
            while (curr != null) {
                if (curr.entry == type) {
                    return true;
                }
                curr = curr.next;
            }
            return false;
        }

        Symbol<Type> pop() {
            if (isEmpty()) {
                CompilerAsserts.neverPartOfCompilation();
                throw EspressoError.shouldNotReachHere();
            }
            Symbol<Type> res = head.entry;
            head = head.next;
            return res;
        }

        void push(Symbol<Type> type) {
            head = new Node(type, head);
        }
    }

    private final long loaderID;

    private ModuleEntry unnamed;
    private final PackageTable packages;
    private final ModuleTable modules;

    public ModuleEntry getUnnamedModule() {
        return unnamed;
    }

    public final long getLoaderID() {
        return loaderID;
    }

    public ModuleTable modules() {
        return modules;
    }

    public PackageTable packages() {
        return packages;
    }

    /**
     * The map from symbol to classes for the classes defined by the class loader associated with
     * this registry. Use of {@link ConcurrentHashMap} allows for atomic insertion while still
     * supporting fast, non-blocking lookup. There's no need for deletion as class unloading removes
     * a whole class registry and all its contained classes.
     */
    protected final ConcurrentHashMap<Symbol<Type>, ClassRegistries.RegistryEntry> classes = new ConcurrentHashMap<>();

    /**
     * The map from class name symbol hashcode to the up-to-date bytes that represent the input to a
     * retranformation as defined per
     * java.lang.instrument.Instrumentation#retransformClasses(Class[]). This map is only
     * initialized/used when java agents are present.
     */
    private volatile ConcurrentHashMap<Klass, byte[]> retransformBytes;

    @TruffleBoundary
    public void registerRetransformBytes(Klass klass, byte[] bytes) {
        if (retransformBytes == null) {
            synchronized (this) {
                // double-checked locking
                if (retransformBytes == null) {
                    retransformBytes = new ConcurrentHashMap<>();
                }
            }
        }
        retransformBytes.put(klass, bytes);
    }

    @TruffleBoundary
    public byte[] getRetransformBytes(Klass klass) {
        assert retransformBytes != null;
        return retransformBytes.get(klass);
    }

    /**
     * Strong hidden classes must be referenced by the class loader data to prevent them from being
     * reclaimed, while not appearing in the actual registry. This field simply keeps those hidden
     * classes strongly reachable from the class registry.
     */
    volatile Collection<Klass> strongHiddenKlasses = null;

    /**
     * Hidden classes must be reachable until they're unloaded, because JDWP and JVMTI must be able
     * to query all classes.
     */
    private volatile WeakHashMap<Klass, Void> hiddenKlasses = null;

    Object getStrongHiddenClassRegistrationLock() {
        return this;
    }

    private void registerStrongHiddenKlass(Klass klass) {
        synchronized (getStrongHiddenClassRegistrationLock()) {
            if (strongHiddenKlasses == null) {
                strongHiddenKlasses = new ArrayList<>();
            }
            strongHiddenKlasses.add(klass);
        }
    }

    private void registerHiddenKlass(Klass klass) {
        synchronized (getStrongHiddenClassRegistrationLock()) {
            if (hiddenKlasses == null) {
                hiddenKlasses = new WeakHashMap<>();
            }
            hiddenKlasses.put(klass, null);
        }
    }

    public Set<Klass> getHiddenKlasses() {
        return hiddenKlasses != null ? hiddenKlasses.keySet() : Collections.emptySet();
    }

    protected ClassRegistry(long loaderID, ArchivedRegistryData archivedData) {
        this.loaderID = loaderID;
        if (archivedData != null) {
            this.packages = archivedData.packageTable();
            this.modules = archivedData.moduleTable();
        } else {
            ReadWriteLock rwLock = new ReentrantReadWriteLock();
            this.packages = new PackageTable(rwLock);
            this.modules = new ModuleTable(rwLock);
        }
    }

    public void initUnnamedModule(StaticObject unnamedModule, ArchivedRegistryData archivedRegistryData) {
        if (archivedRegistryData != null) {
            this.unnamed = archivedRegistryData.unnamedModule();
        } else {
            this.unnamed = modules.createUnnamedModuleEntry(unnamedModule);
        }
    }

    /**
     * Queries a registry to load a Klass for us.
     *
     * @param type the symbolic reference to the Klass we want to load
     * @param protectionDomain The protection domain extracted from the guest class, or
     *            {@link StaticObject#NULL} if trusted.
     * @return The Klass corresponding to given type
     */
    @SuppressWarnings("try")
    Klass loadKlass(EspressoContext context, Symbol<Type> type, StaticObject protectionDomain) throws EspressoClassLoadingException {
        ClassLoadingEnv env = context.getClassLoadingEnv();
        if (TypeSymbols.isArray(type)) {
            Klass elemental = loadKlass(context, env.getTypes().getElementalType(type), protectionDomain);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayKlass(TypeSymbols.getArrayDimensions(type));
        }

        loadKlassCountInc();

        // Double-checked locking on the symbol (globally unique).
        ClassRegistries.RegistryEntry entry;
        try (DebugCloseable probe = KLASS_PROBE.scope(env.getTimers())) {
            entry = classes.get(type);
        }
        if (entry == null) {
            synchronized (type) {
                entry = classes.get(type);
                if (entry == null) {
                    if (loadKlassImpl(context, type) == null) {
                        return null;
                    }
                    entry = classes.get(type);
                }
            }
        } else {
            // Grabbing a lock to fetch the class is not considered a hit.
            loadKlassCacheHitsInc();
        }
        assert entry != null;
        StaticObject classLoader = getClassLoader();
        if (!StaticObject.isNull(classLoader) && context.getJavaVersion().java23OrEarlier()) {
            entry.checkPackageAccess(env.getMeta(), classLoader, protectionDomain);
        }
        return entry.klass();
    }

    protected abstract Klass loadKlassImpl(EspressoContext context, Symbol<Type> type) throws EspressoClassLoadingException;

    protected abstract void loadKlassCountInc();

    protected abstract void loadKlassCacheHitsInc();

    public abstract @JavaType(ClassLoader.class) StaticObject getClassLoader();

    @TruffleBoundary
    Set<Klass> getLoadedKlasses() {
        HashSet<Klass> klasses = new HashSet<>(classes.size());
        for (ClassRegistries.RegistryEntry entry : classes.values()) {
            klasses.add(entry.klass());
        }
        return klasses;
    }

    public Klass findLoadedKlass(ClassLoadingEnv env, Symbol<Type> type) {
        if (TypeSymbols.isArray(type)) {
            Symbol<Type> elemental = env.getTypes().getElementalType(type);
            Klass elementalKlass = findLoadedKlass(env, elemental);
            if (elementalKlass == null) {
                return null;
            }
            return elementalKlass.getArrayKlass(TypeSymbols.getArrayDimensions(type));
        }
        ClassRegistries.RegistryEntry entry = classes.get(type);
        if (entry == null) {
            return null;
        }
        return entry.klass();
    }

    public final ObjectKlass defineKlass(EspressoContext context, Symbol<Type> typeOrNull, final byte[] bytes) throws EspressoClassLoadingException {
        return defineKlass(context, typeOrNull, bytes, ClassDefinitionInfo.EMPTY);
    }

    @SuppressWarnings("try")
    public ObjectKlass defineKlass(EspressoContext context, Symbol<Type> typeOrNull, final byte[] initialBytes, ClassDefinitionInfo info) throws EspressoClassLoadingException {
        ClassLoadingEnv env = context.getClassLoadingEnv();
        byte[] bytes = initialBytes;
        // When agents are present we need to retain the bytes we must use for a future
        // retransformation. Those are the bytes resulting from applying all retransform incapable
        // transformers.
        byte[] beforeRetransformBytes = null;
        if (context.getJavaAgents() != null && !info.isHidden() && !info.isAnonymousClass()) {
            // we must not apply a transformation on a class that was loaded in response
            // to an ongoing transformation, so we check our ThreadLocal state
            EspressoThreadLocalState tls = context.getLanguage().getThreadLocalState();
            if (!tls.isInTransformer()) {
                try (EspressoThreadLocalState.TransformerScope transformerScope = tls.transformerScope()) {
                    ModuleEntry module = getGuestModuleInstance(typeOrNull, context);
                    StaticObject protectionDomain = info.protectionDomain == null ? StaticObject.NULL : info.protectionDomain;
                    beforeRetransformBytes = context.getJavaAgents().applyTransformers(StaticObject.NULL, module.module(), getClassLoader(), typeOrNull, protectionDomain, initialBytes);
                    bytes = context.getJavaAgents().applyRetransformers(StaticObject.NULL, module.module(), getClassLoader(), typeOrNull, protectionDomain, beforeRetransformBytes);
                    if (bytes != initialBytes) {
                        // When bytes are modified, we need to grant module read access
                        // to potentially injected helper classes in the unnamed module
                        // of the bootstrap and system class loader.
                        context.getJavaAgents().grantReadAccessToUnnamedModules(module);
                    }
                }
            }
        }

        ParserKlass parserKlass;
        try (DebugCloseable parse = KLASS_PARSE.scope(env.getTimers())) {
            parserKlass = parseKlass(env, bytes, typeOrNull, info);
        }
        Symbol<Type> type = typeOrNull == null ? parserKlass.getType() : typeOrNull;

        if (info.addedToRegistry()) {
            Klass maybeLoaded = findLoadedKlass(env, type);
            if (maybeLoaded != null) {
                throw EspressoClassLoadingException.linkageError("Class " + type + " already defined");
            }
        }

        Symbol<Type> superKlassType = parserKlass.getSuperKlass();

        ObjectKlass klass = createKlass(context, parserKlass, type, superKlassType, info);

        if (info.isAnonymousClass() && info.patches != null) {
            patchAnonymousClass(klass.getConstantPool(), info.patches);
        }
        if (ConstantPoolPatcher.shouldPatchPool(type, context)) {
            ConstantPoolPatcher.patchConstantPool(context, type, klass.getConstantPool());
        }

        if (info.addedToRegistry()) {
            registerKlass(klass, type, beforeRetransformBytes);
        } else if (info.isStrongHidden()) {
            registerStrongHiddenKlass(klass);
        } else {
            registerHiddenKlass(klass);
        }
        return klass;
    }

    private ModuleEntry getGuestModuleInstance(Symbol<Type> typeOrNull, EspressoContext context) {
        // we need the package symbol first
        Symbol<Name> pkgSymbol = typeOrNull == null ? context.getNames().getOrCreate(ByteSequence.EMPTY) : context.getNames().getOrCreate(TypeSymbols.getRuntimePackage(typeOrNull));
        // then obtain the package entry
        PackageTable.PackageEntry pkgEntry = getPackageEntry(context, pkgSymbol);
        // from the package entry we can now get the module
        return getModuleEntry(pkgEntry);
    }

    public PackageTable.PackageEntry getPackageEntry(EspressoContext context, Symbol<Name> pkgSymbol) {
        PackageTable.PackageEntry pkgEntry = null;
        if (!NameSymbols.isUnnamedPackage(pkgSymbol)) {
            pkgEntry = packages().lookup(pkgSymbol);
            // If the package name is not found in the entry table, it is an indication that the
            // package has not been defined. Consider it defined within the unnamed module.
            if (pkgEntry == null) {
                if (!context.getRegistries().javaBaseDefined()) {
                    // Before java.base is defined during bootstrapping, define all packages in
                    // the java.base module.
                    pkgEntry = packages().lookupOrCreate(pkgSymbol, context.getRegistries().getJavaBaseModule());
                } else {
                    pkgEntry = packages().lookupOrCreate(pkgSymbol, getUnnamedModule());
                }
            }
        }
        return pkgEntry;
    }

    private ModuleEntry getModuleEntry(PackageTable.PackageEntry pkgEntry) {
        ModuleEntry moduleEntry;
        if (pkgEntry != null) {
            moduleEntry = pkgEntry.module();
        } else {
            // unnamed package, thus unnamed module in loader
            moduleEntry = getUnnamedModule();
        }
        return moduleEntry;
    }

    private static void patchAnonymousClass(RuntimeConstantPool constantPool, StaticObject[] patches) {
        int maxCPIndex = Math.min(patches.length, constantPool.length());
        for (int i = 1; i < maxCPIndex; i++) {
            if (patches[i] != null && StaticObject.notNull(patches[i])) {
                ConstantPool.Tag tag = constantPool.tagAt(i);
                if (Objects.requireNonNull(tag) == ConstantPool.Tag.STRING) {
                    /*
                     * The runtime CP entry tag may be different from the actual constant that is
                     * pre-resolved. Pre-resolved/patched entries may contain arbitrary guest
                     * objects, like classes.
                     */
                    constantPool.patchAt(i, RuntimeConstantPool.preResolvedConstant(patches[i], tag));
                } else {
                    throw EspressoError.unimplemented("Patching anonymous class CP entry with: " + tag);
                }
            }
        }
    }

    private ParserKlass parseKlass(ClassLoadingEnv env, byte[] bytes, Symbol<Type> typeOrNull, ClassDefinitionInfo info) throws EspressoClassLoadingException.SecurityException {
        // May throw guest ClassFormatError, NoClassDefFoundError.
        ParserKlass parserKlass = env.getLanguage().getLanguageCache().getOrCreateParserKlass(env, getClassLoader(), typeOrNull, bytes, info);
        if (!env.loaderIsBootOrPlatform(getClassLoader()) && parserKlass.getName().toString().startsWith("java/")) {
            throw EspressoClassLoadingException.securityException("Define class in prohibited package name: " + parserKlass.getName());
        }
        return parserKlass;
    }

    @SuppressWarnings("try")
    private ObjectKlass createKlass(EspressoContext context, ParserKlass parserKlass, Symbol<Type> type, Symbol<Type> superKlassType, ClassDefinitionInfo info) throws EspressoClassLoadingException {
        ClassLoadingEnv env = context.getClassLoadingEnv();
        EspressoThreadLocalState threadLocalState = env.getLanguage().getThreadLocalState();
        TypeStack chain = threadLocalState.getTypeStack();

        ObjectKlass superKlass = null;
        ObjectKlass[] superInterfaces = null;
        LinkedKlass[] linkedInterfaces = null;

        chain.push(type);

        try {
            if (superKlassType != null) {
                if (chain.contains(superKlassType)) {
                    throw EspressoClassLoadingException.classCircularityError();
                }
                superKlass = loadKlassRecursively(context, superKlassType, true, type);
            }

            final Symbol<Type>[] superInterfacesTypes = parserKlass.getSuperInterfaces();

            linkedInterfaces = superInterfacesTypes.length == 0
                            ? LinkedKlass.EMPTY_ARRAY
                            : new LinkedKlass[superInterfacesTypes.length];

            superInterfaces = superInterfacesTypes.length == 0
                            ? ObjectKlass.EMPTY_ARRAY
                            : new ObjectKlass[superInterfacesTypes.length];

            for (int i = 0; i < superInterfacesTypes.length; ++i) {
                if (chain.contains(superInterfacesTypes[i])) {
                    throw EspressoClassLoadingException.classCircularityError();
                }
                ObjectKlass interf = loadKlassRecursively(context, superInterfacesTypes[i], false, type);
                superInterfaces[i] = interf;
                linkedInterfaces[i] = interf.getLinkedKlass();
            }
        } finally {
            chain.pop();
        }

        if (env.getJavaVersion().java16OrLater() && superKlass != null) {
            if (superKlass.isFinalFlagSet()) {
                throw EspressoClassLoadingException.incompatibleClassChangeError("class " + type + " is a subclass of final class " + superKlassType);
            }
        }

        ObjectKlass klass;

        try (DebugCloseable define = KLASS_DEFINE.scope(env.getTimers())) {
            // FIXME(peterssen): Do NOT create a LinkedKlass every time, use a global cache.
            LinkedKlass linkedSuperKlass = superKlass == null ? null : superKlass.getLinkedKlass();
            LinkedKlass linkedKlass = env.getLanguage().getLanguageCache().getOrCreateLinkedKlass(env, env.getLanguage(), getClassLoader(), parserKlass, linkedSuperKlass, linkedInterfaces, info);
            klass = new ObjectKlass(context, linkedKlass, superKlass, superInterfaces, getClassLoader(), info);
        }

        if (superKlass != null) {
            /*
             * These checks ensure that only classes defined in the boot or reflection class loader
             * can declare a superclass in the 'jdk.internal.reflect' package.
             *
             * In turn, this ensures that only these classes may be magic accessors.
             */
            if (context.getJavaVersion().java23OrEarlier()) {
                if (!env.loaderIsBoot(getClassLoader()) &&
                                env.isReflectPackage(superKlass.getRuntimePackage()) &&
                                !env.loaderIsReflection(getClassLoader())) {
                    throw EspressoClassLoadingException.illegalAccessError(
                                    String.format("class %s loaded by %s cannot access reflection superclass %s",
                                                    klass.getExternalName(),
                                                    loaderDesc(env, context.getMeta(), getClassLoader()),
                                                    superKlass.getExternalName()));
                }
            }
            if (!Klass.checkAccess(superKlass, klass)) {
                StringBuilder sb = new StringBuilder().append("class ").append(klass.getExternalName()).append(" cannot access its superclass ").append(superKlass.getExternalName());
                appendModuleAndLoadersDetails(env, klass, superKlass, sb, context);
                throw EspressoClassLoadingException.illegalAccessError(sb.toString());
            }
            if (!superKlass.permittedSubclassCheck(klass)) {
                throw EspressoClassLoadingException.incompatibleClassChangeError("class " + klass.getExternalName() + " is not a permitted subclass of class " + superKlass.getExternalName());
            }
        }

        for (ObjectKlass interf : superInterfaces) {
            if (interf != null) {
                if (!Klass.checkAccess(interf, klass)) {
                    StringBuilder sb = new StringBuilder().append("class ").append(klass.getExternalName()).append(" cannot access its superinterface ").append(interf.getExternalName());
                    appendModuleAndLoadersDetails(env, klass, interf, sb, context);
                    throw EspressoClassLoadingException.illegalAccessError(sb.toString());
                }
                if (!interf.permittedSubclassCheck(klass)) {
                    throw EspressoClassLoadingException.incompatibleClassChangeError("class " + klass.getExternalName() + " is not a permitted subclass of interface " + interf.getExternalName());
                }
            }
        }

        return klass;
    }

    public static void appendModuleAndLoadersDetails(ClassLoadingEnv env, Klass klass1, Klass klass2, StringBuilder sb, EspressoContext context) {
        if (context.getJavaVersion().modulesEnabled()) {
            sb.append(" (");
            Meta meta = context.getMeta();
            if (klass2.module() == klass1.module()) {
                sb.append(klass1.getExternalName());
                sb.append(" and ");
                classInModuleOfLoader(env, klass2, true, sb, meta);
            } else {
                classInModuleOfLoader(env, klass1, false, sb, meta);
                sb.append("; ");
                classInModuleOfLoader(env, klass2, false, sb, meta);
            }
            sb.append(")");
        }
    }

    public static void classInModuleOfLoader(ClassLoadingEnv env, Klass klass, boolean plural, StringBuilder sb, Meta meta) {
        assert meta.getJavaVersion().modulesEnabled() && meta.java_lang_ClassLoader_nameAndId != null;
        sb.append(klass.getExternalName());
        if (plural) {
            sb.append(" are in ");
        } else {
            sb.append(" is in ");
        }
        ModuleEntry module = klass.module();
        if (module.isNamed()) {
            sb.append("module ").append(module.getNameAsString());
            // TODO version
        } else {
            sb.append("unnamed module");
        }
        sb.append(" of loader ");
        sb.append(loaderDesc(env, meta, klass.getDefiningClassLoader()));
    }

    private static String loaderDesc(ClassLoadingEnv env, Meta meta, StaticObject loader) {
        if (env.loaderIsBoot(loader)) {
            return "bootstrap";
        }
        StaticObject nameAndId = meta.java_lang_ClassLoader_nameAndId.getObject(loader);
        if (StaticObject.isNull(nameAndId)) {
            return loader.getKlass().getExternalName();
        } else {
            return meta.toHostString(nameAndId);
        }
    }

    private void registerKlass(ObjectKlass klass, Symbol<Type> type, byte[] bytes) {
        ClassRegistries.RegistryEntry entry = new ClassRegistries.RegistryEntry(klass);
        ClassRegistries.RegistryEntry previous = classes.putIfAbsent(type, entry);
        if (bytes != null) {
            registerRetransformBytes(klass, bytes);
        }

        EspressoError.guarantee(previous == null, "Class already defined", type);

        klass.getRegistries().recordConstraint(type, klass, getClassLoader());
        klass.getRegistries().onKlassDefined(klass);
        if (defineKlassListener != null) {
            defineKlassListener.onKlassDefined(klass);
        }
    }

    private ObjectKlass loadKlassRecursively(EspressoContext context, Symbol<Type> type, boolean notInterface, Symbol<Type> root) throws EspressoClassLoadingException {
        Klass klass;
        try {
            klass = loadKlass(context, type, StaticObject.NULL);
        } catch (EspressoException e) {
            throw EspressoClassLoadingException.wrapClassNotFoundGuestException(context.getMeta(), e, root);
        }
        assert klass != null;
        if (notInterface == klass.isInterface()) {
            throw EspressoClassLoadingException.incompatibleClassChangeError("Super interface of " + type + " is in fact not an interface.");
        }
        return (ObjectKlass) klass;
    }

    public void onClassRenamed(ObjectKlass renamedKlass) {
        // this method is constructed so that any existing class loader constraint
        // for the new type is removed from the class registries first. This allows
        // a clean addition of a new class loader constraint for the new type for a
        // different klass object.

        // The old type of the renamed klass object will not be handled within this
        // method. There are two possible ways in which the old type is handled, 1)
        // if another renamed class instance now has the old type, it will also go
        // through this method directly or 2) if no klass instance has the new type
        // the old klass instance will be marked as removed and will follow a direct
        // path to ClassRegistries.removeUnloadedKlassConstraint().

        ClassLoadingEnv env = renamedKlass.getContext().getClassLoadingEnv();
        Klass loadedKlass = findLoadedKlass(env, renamedKlass.getType());
        if (loadedKlass != null) {
            loadedKlass.getRegistries().removeUnloadedKlassConstraint(loadedKlass, renamedKlass.getType());
        }

        classes.put(renamedKlass.getType(), new ClassRegistries.RegistryEntry(renamedKlass));
        // record the new loading constraint
        renamedKlass.getRegistries().recordConstraint(renamedKlass.getType(), renamedKlass, renamedKlass.getDefiningClassLoader());
    }

    public void onInnerClassRemoved(Symbol<Type> type) {
        // "unload" the class by removing from classes
        ClassRegistries.RegistryEntry removed = classes.remove(type);
        // purge class loader constraint for this type
        if (removed != null && removed.klass() != null) {
            removed.klass().getRegistries().removeUnloadedKlassConstraint(removed.klass(), type);
        }
    }

    public final DynamicModuleWrapper getProxyDynamicModuleWrapper() {
        return dynamicModuleWrapper;
    }

    public static final class DynamicModuleWrapper {
        private ModuleEntry dynamicProxyModule;

        public ModuleEntry getDynamicProxyModule() {
            return dynamicProxyModule;
        }

        public void setDynamicProxyModule(ModuleEntry module) {
            dynamicProxyModule = module;
        }
    }
}
