/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.impl.LoadingConstraints.INVALID_LOADER_ID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.cds.ArchivedRegistryData;
import com.oracle.truffle.espresso.cds.CDSSupport;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.tables.AbstractModuleTable;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.DefineKlassListener;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.meta.ErrorType;
import com.oracle.truffle.espresso.substitutions.JavaType;

public final class ClassRegistries {

    private ModuleTable.ModuleEntry javaBaseModule;
    private ModuleTable.ModuleEntry polyglotAPIModule;

    private final ClassRegistry bootClassRegistry;
    private final LoadingConstraints constraints;
    private final EspressoContext context;

    // access to this list is done under the bootloader's package table lock
    private List<Klass> fixupModuleList = new ArrayList<>();

    private final Set<StaticObject> weakClassLoaderSet = Collections.newSetFromMap(new WeakHashMap<>());

    // Used as a volatile field. All accesses are done in synchronized blocks, so we do not need to
    // specify it as volatile.
    private int totalClassLoadersSet = 0;

    private DefineKlassListener defineKlassListener;

    public ClassRegistries(EspressoContext context) {
        this.context = context;
        this.bootClassRegistry = createBootClassRegistry(context);
        this.constraints = new LoadingConstraints(context);
    }

    private static BootClassRegistry createBootClassRegistry(EspressoContext context) {
        ArchivedRegistryData archivedRegistryData = null;
        CDSSupport cds = context.getCDS();
        if (cds != null && cds.isUsingArchive()) {
            archivedRegistryData = cds.getBootClassRegistryData();
        }
        return new BootClassRegistry(context.getClassLoadingEnv().getNewLoaderId(), archivedRegistryData);
    }

    public void initJavaBaseModule() {
        // Do not create java.base new if already (partially) loaded from CDS archive.
        CDSSupport cds = context.getCDS();
        // java.base is already registered in the boot registry IFF CDS is enabled.
        assert (cds == null || !cds.isUsingArchive()) == (bootClassRegistry.modules().lookup(Names.java_base) == null);
        this.javaBaseModule = bootClassRegistry.modules().lookupOrCreate(Names.java_base, new AbstractModuleTable.ModuleData<>(null, null, null, 0, false));
    }

    public ClassRegistry getClassRegistry(@JavaType(ClassLoader.class) StaticObject classLoader) {
        if (StaticObject.isNull(classLoader)) {
            return bootClassRegistry;
        }
        ClassRegistry classRegistry = getOrInitializeClassRegistry(classLoader, null);
        assert classRegistry != null;
        return classRegistry;
    }

    public ClassRegistry getOrInitializeClassRegistry(StaticObject classLoader, ArchivedRegistryData archivedRegistryData) {
        // Double-checked locking to attach class registry to guest instance.
        ClassRegistry classRegistry = (ClassRegistry) context.getMeta().HIDDEN_CLASS_LOADER_REGISTRY.getHiddenObject(classLoader, true);
        if (classRegistry == null) {
            // Synchronizing on the classLoader instance would be the natural choice here, but:
            // On SubstrateVM, synchronizing on a StaticObject instance will add an extra slot/field
            // to all StaticObject instances. Locking on the weak set instead (maybe) spares one
            // slot/field in every single guest object.
            // Setting the class registry happens only once, for such rare operations, no contention
            // is expected.
            synchronized (weakClassLoaderSet) {
                classRegistry = (ClassRegistry) context.getMeta().HIDDEN_CLASS_LOADER_REGISTRY.getHiddenObject(classLoader, true);
                if (classRegistry == null) {
                    classRegistry = registerRegistry(classLoader, archivedRegistryData);
                }
            }
        }
        return classRegistry;
    }

    @TruffleBoundary
    private ClassRegistry registerRegistry(@JavaType(ClassLoader.class) StaticObject classLoader, ArchivedRegistryData archivedRegistryData) {
        assert Thread.holdsLock(weakClassLoaderSet);
        ClassRegistry classRegistry;
        classRegistry = new GuestClassRegistry(context.getClassLoadingEnv(), classLoader, archivedRegistryData);
        context.getMeta().HIDDEN_CLASS_LOADER_REGISTRY.setHiddenObject(classLoader, classRegistry, true);
        // Register the class loader in the weak set.
        weakClassLoaderSet.add(classLoader);
        totalClassLoadersSet++;
        int size = weakClassLoaderSet.size();
        if (totalClassLoadersSet > size) {
            constraints.purge();
            totalClassLoadersSet = size;
        }
        return classRegistry;
    }

    public ModuleTable.ModuleEntry getJavaBaseModule() {
        return javaBaseModule;
    }

    public ModuleTable.ModuleEntry getPolyglotAPIModule() {
        if (polyglotAPIModule == null) {
            polyglotAPIModule = findPlatformOrBootModule(Names.espresso_polyglot);
        }
        return polyglotAPIModule;
    }

    private ModuleTable.ModuleEntry findPlatformOrBootModule(Symbol<Name> name) {
        ModuleTable.ModuleEntry m = bootClassRegistry.modules().lookup(name);
        if (m != null) {
            return m;
        }
        ObjectKlass platformClassLoaderKlass = context.getMeta().jdk_internal_loader_ClassLoaders$PlatformClassLoader;
        if (platformClassLoaderKlass == null) {
            return null;
        }
        synchronized (weakClassLoaderSet) {
            for (StaticObject loader : weakClassLoaderSet) {
                if (loader != null) {
                    if (platformClassLoaderKlass.isAssignableFrom(loader.getKlass())) {
                        m = getClassRegistry(loader).modules().lookup(name);
                        if (m != null) {
                            return m;
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean javaBaseDefined() {
        boolean result = javaBaseModule != null && javaBaseModule.module() != null;
        assert !result || StaticObject.notNull(javaBaseModule.module());
        return result;
    }

    @TruffleBoundary
    public Klass findLoadedClass(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";

        if (TypeSymbols.isArray(type)) {
            Klass elemental = findLoadedClass(context.getTypes().getElementalType(type), classLoader);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayKlass(TypeSymbols.getArrayDimensions(type));
        }

        ClassRegistry registry = getClassRegistry(classLoader);
        assert registry != null;
        return registry.findLoadedKlass(context.getClassLoadingEnv(), type);
    }

    @TruffleBoundary
    public Set<Klass> getLoadedClassesByLoader(StaticObject classLoader, boolean includeHidden) {
        if (classLoader == StaticObject.NULL) {
            Set<Klass> result = new HashSet<>();
            for (RegistryEntry value : bootClassRegistry.classes.values()) {
                result.add(value.klass());
            }
            if (includeHidden) {
                addAllHiddenKlasses(bootClassRegistry, result);
            }
            // include array classes
            result.addAll(getLoadedArrayClasses(result));
            // include primitive array classes, but not the primitive classes themselves
            result.addAll(getLoadedArrayClasses(Arrays.asList(context.getMeta().PRIMITIVE_KLASSES)));
            return result;
        }
        ClassRegistry classRegistry = getClassRegistry(classLoader);
        if (classRegistry == null) {
            return Collections.emptySet();
        }
        Set<Klass> result = classRegistry.getLoadedKlasses();
        if (includeHidden) {
            addAllHiddenKlasses(classRegistry, result);
        }

        // include loaded array classes
        result.addAll(getLoadedArrayClasses(result));
        return result;
    }

    private static void addAllHiddenKlasses(ClassRegistry registry, Set<Klass> result) {
        synchronized (registry.getStrongHiddenClassRegistrationLock()) {
            if (registry.strongHiddenKlasses != null) {
                result.addAll(registry.strongHiddenKlasses);
            }
            if (registry.getHiddenKlasses() != null) {
                result.addAll(registry.getHiddenKlasses());
            }
        }
    }

    @TruffleBoundary
    public Klass[] findLoadedClassAny(Symbol<Type> type) {
        ArrayList<Klass> klasses = new ArrayList<>();
        // look in boot class registry
        if (bootClassRegistry.classes.containsKey(type)) {
            klasses.add(bootClassRegistry.classes.get(type).klass());
            // if a type loaded by the boot loader, there can't
            // be any others, so return immediately
            return klasses.toArray(Klass.EMPTY_ARRAY);
        }
        // continue search in all other registries
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                ClassRegistry registry = getClassRegistry(classLoader);
                if (registry != null && registry.classes != null && registry.classes.containsKey(type)) {
                    klasses.add(registry.classes.get(type).klass());
                }
            }
        }
        return klasses.toArray(Klass.EMPTY_ARRAY);
    }

    @TruffleBoundary
    public Set<Klass> getAllLoadedClasses() {
        // first add classes from boot registry
        HashSet<Klass> set = new HashSet<>(getLoadedClassesByLoader(StaticObject.NULL, true));

        // add classes from all other registries
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                set.addAll(getLoadedClassesByLoader(classLoader, true));
            }
        }
        return set;
    }

    private static Set<Klass> getLoadedArrayClasses(Collection<Klass> elementalKlasses) {
        Set<Klass> result = new HashSet<>();
        for (Klass elementalKlass : elementalKlasses) {
            ArrayKlass arrayKlass = elementalKlass.getArrayKlass(false);
            while (arrayKlass != null) {
                result.add(arrayKlass);
                arrayKlass = arrayKlass.getArrayKlass(false);
            }
        }
        return result;
    }

    public ModuleRef[] getAllModuleRefs() {
        ArrayList<ModuleRef> list = new ArrayList<>();
        // add modules from boot registry
        bootClassRegistry.modules().collectValues(list::add);

        // add modules from all other registries
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                getClassRegistry(classLoader).modules().collectValues(list::add);
            }
        }
        return list.toArray(ModuleRef.EMPTY_ARRAY);
    }

    public ModuleTable.ModuleEntry findUniqueModule(Symbol<Name> name) {
        ModuleTable.ModuleEntry result = bootClassRegistry.modules().lookup(name);
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                ModuleTable.ModuleEntry newResult = getClassRegistry(classLoader).modules().lookup(name);
                if (newResult != null) {
                    if (result == null) {
                        result = newResult;
                    } else {
                        throw EspressoError.shouldNotReachHere("Found more than one module named " + name);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Do not call directly. Use
     * {@link com.oracle.truffle.espresso.meta.Meta#loadKlassOrFail(Symbol, StaticObject, StaticObject)}
     * or
     * {@link com.oracle.truffle.espresso.meta.Meta#loadKlassOrNull(Symbol, StaticObject, StaticObject)}
     * .
     */
    @TruffleBoundary
    public Klass loadKlass(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader, StaticObject protectionDomain) throws EspressoClassLoadingException {
        assert classLoader != null : "use StaticObject.NULL for BCL";

        if (TypeSymbols.isArray(type)) {
            Klass elemental = loadKlass(context.getTypes().getElementalType(type), classLoader, protectionDomain);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayKlass(TypeSymbols.getArrayDimensions(type));
        }
        ClassRegistry registry = getClassRegistry(classLoader);
        return registry.loadKlass(context, type, protectionDomain);
    }

    @TruffleBoundary
    public ObjectKlass defineKlass(Symbol<Type> type, byte[] bytes, StaticObject classLoader, ClassRegistry.ClassDefinitionInfo info) throws EspressoClassLoadingException {
        assert classLoader != null;
        ClassRegistry registry = getClassRegistry(classLoader);
        return registry.defineKlass(context, type, bytes, info);
    }

    public BootClassRegistry getBootClassRegistry() {
        return (BootClassRegistry) bootClassRegistry;
    }

    @TruffleBoundary
    public void checkLoadingConstraint(Symbol<Type> type, StaticObject loader1, StaticObject loader2, Function<String, RuntimeException> errorHandler) {
        Symbol<Type> toCheck = context.getTypes().getElementalType(type);
        if (!TypeSymbols.isPrimitive(toCheck) && loader1 != loader2) {
            constraints.checkConstraint(toCheck, loader1, loader2, errorHandler);
        }
    }

    void recordConstraint(Symbol<Type> type, Klass klass, StaticObject loader) {
        assert !TypeSymbols.isArray(type);
        if (!TypeSymbols.isPrimitive(type)) {
            constraints.recordConstraint(type, klass, loader, m -> {
                throw context.throwError(ErrorType.LinkageError, m);
            });
        }
    }

    void removeUnloadedKlassConstraint(Klass klass, Symbol<Type> type) {
        assert klass.isInstanceClass();
        constraints.removeUnloadedKlassConstraint(klass, type);
    }

    @TruffleBoundary
    public long getLoadedClassesCount() {
        long result = bootClassRegistry.classes.size();
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                result += getClassRegistry(classLoader).classes.size();
            }
        }
        assert result >= 0;
        return result;
    }

    public void addToFixupList(Klass k) {
        fixupModuleList.add(k);
    }

    public void processFixupList(StaticObject javaBase) {
        assert StaticObject.notNull(javaBase);
        for (PrimitiveKlass k : context.getMeta().PRIMITIVE_KLASSES) {
            context.getMeta().java_lang_Class_module.setObject(k.initializeEspressoClass(), javaBase);
        }
        for (Klass k : fixupModuleList) {
            context.getMeta().java_lang_Class_module.setObject(k.initializeEspressoClass(), javaBase);
        }
        fixupModuleList = null;
    }

    /**
     * Collects IDs of all class loaders that have not been collected by the GC.
     */
    long[] aliveLoaders() {
        long[] loaders = new long[weakClassLoaderSet.size() + 1];
        loaders[0] = context.getBootClassLoaderID(); // Boot loader is always alive
        int i = 1;
        synchronized (weakClassLoaderSet) {
            for (StaticObject loader : weakClassLoaderSet) {
                if (loader != null) {
                    loaders[i++] = getClassRegistry(loader).getLoaderID();
                }
            }
        }
        if (i < loaders.length) {
            loaders[i++] = INVALID_LOADER_ID;
        }
        return loaders;
    }

    public void registerListener(DefineKlassListener listener) {
        this.defineKlassListener = listener;
    }

    @TruffleBoundary
    public void onKlassDefined(ObjectKlass klass) {
        if (defineKlassListener != null) {
            defineKlassListener.onKlassDefined(klass);
        }
    }

    static class RegistryEntry {
        private final Klass klass;
        private volatile Set<StaticObject> domains = null;

        RegistryEntry(Klass k) {
            assert k != null;
            this.klass = k;
        }

        public Klass klass() {
            return klass;
        }

        void checkPackageAccess(Meta meta, StaticObject classLoader, StaticObject protectionDomain) {
            CompilerAsserts.neverPartOfCompilation();
            if (StaticObject.isNull(protectionDomain)) {
                return;
            }
            Set<StaticObject> cachedDomains = getCachedDomains();
            if (cachedDomains.contains(protectionDomain)) {
                return;
            }
            // throws SecurityException if access is not allowed.
            meta.java_lang_ClassLoader_checkPackageAccess.invokeDirectSpecial(classLoader, klass.mirror(), protectionDomain);
            cachedDomains.add(protectionDomain);
        }

        private Set<StaticObject> getCachedDomains() {
            if (domains == null) {
                synchronized (this) {
                    if (domains == null) {
                        // We do not expect a lot of different protection domains
                        domains = Collections.newSetFromMap(new ConcurrentHashMap<>(2));
                    }
                }
            }
            return domains;
        }
    }
}
