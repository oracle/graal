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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.DefineKlassListener;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

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
        this.bootClassRegistry = new BootClassRegistry(context.getClassLoadingEnv().getNewLoaderId());
        this.constraints = new LoadingConstraints(context);
    }

    public void initJavaBaseModule() {
        this.javaBaseModule = bootClassRegistry.modules().createAndAddEntry(Symbol.Name.java_base, bootClassRegistry);
    }

    public ClassRegistry getClassRegistry(@JavaType(ClassLoader.class) StaticObject classLoader) {
        if (StaticObject.isNull(classLoader)) {
            return bootClassRegistry;
        }

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
                    classRegistry = registerRegistry(classLoader);
                }
            }
        }

        assert classRegistry != null;
        return classRegistry;
    }

    @TruffleBoundary
    private ClassRegistry registerRegistry(@JavaType(ClassLoader.class) StaticObject classLoader) {
        assert Thread.holdsLock(weakClassLoaderSet);
        ClassRegistry classRegistry;
        classRegistry = new GuestClassRegistry(context.getClassLoadingEnv(), classLoader);
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
            ModuleRef[] allModuleRefs = getAllModuleRefs();
            for (ModuleRef module : allModuleRefs) {
                if ("espresso.polyglot".equals(module.jdwpName())) {
                    polyglotAPIModule = (ModuleTable.ModuleEntry) module;
                    break;
                }
            }
        }
        return polyglotAPIModule;
    }

    public boolean javaBaseDefined() {
        return javaBaseModule != null && !StaticObject.isNull(javaBaseModule.module());
    }

    @TruffleBoundary
    public Klass findLoadedClass(Symbol<Type> type, @JavaType(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";

        if (Types.isArray(type)) {
            Klass elemental = findLoadedClass(context.getTypes().getElementalType(type), classLoader);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }

        ClassRegistry registry = getClassRegistry(classLoader);
        assert registry != null;
        return registry.findLoadedKlass(context.getClassLoadingEnv(), type);
    }

    @TruffleBoundary
    public List<Klass> getLoadedClassesByLoader(StaticObject classLoader) {
        if (classLoader == StaticObject.NULL) {
            ArrayList<Klass> result = new ArrayList<>(bootClassRegistry.classes.size());
            for (RegistryEntry value : bootClassRegistry.classes.values()) {
                result.add(value.klass());
            }
            return result;
        }
        return getClassRegistry(classLoader).getLoadedKlasses();
    }

    @TruffleBoundary
    public Klass[] findLoadedClassAny(Symbol<Type> type) {
        ArrayList<Klass> klasses = new ArrayList<>();
        // look in boot class registry
        if (bootClassRegistry.classes.containsKey(type)) {
            klasses.add(bootClassRegistry.classes.get(type).klass());
            // if a type loaded by the boot loader, there can't
            // be any others, so return immediately
            return klasses.toArray(new Klass[0]);
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
        return klasses.toArray(new Klass[0]);
    }

    @TruffleBoundary
    public Klass[] getAllLoadedClasses() {
        ArrayList<Klass> list = new ArrayList<>();
        // add classes from boot registry
        for (RegistryEntry entry : bootClassRegistry.classes.values()) {
            list.add(entry.klass());
        }
        // add classes from all other registries
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                for (RegistryEntry entry : getClassRegistry(classLoader).classes.values()) {
                    list.add(entry.klass());
                }
            }
        }
        return list.toArray(Klass.EMPTY_ARRAY);
    }

    public ModuleRef[] getAllModuleRefs() {
        ArrayList<ModuleRef> list = new ArrayList<>();
        // add modules from boot registry
        list.addAll(bootClassRegistry.modules().values());

        // add modules from all other registries
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                list.addAll(getClassRegistry(classLoader).modules().values());
            }
        }
        return list.toArray(ModuleRef.EMPTY_ARRAY);
    }

    public ModuleTable.ModuleEntry findUniqueModule(Symbol<Symbol.Name> name) {
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

        if (Types.isArray(type)) {
            Klass elemental = loadKlass(context.getTypes().getElementalType(type), classLoader, protectionDomain);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }
        ClassRegistry registry = getClassRegistry(classLoader);
        return registry.loadKlass(context, type, protectionDomain);
    }

    @TruffleBoundary
    public ObjectKlass defineKlass(Symbol<Type> type, byte[] bytes, StaticObject classLoader) throws EspressoClassLoadingException {
        return defineKlass(type, bytes, classLoader, ClassRegistry.ClassDefinitionInfo.EMPTY);
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
    public void checkLoadingConstraint(Symbol<Type> type, StaticObject loader1, StaticObject loader2) {
        Symbol<Type> toCheck = context.getTypes().getElementalType(type);
        if (!Types.isPrimitive(toCheck) && loader1 != loader2) {
            constraints.checkConstraint(toCheck, loader1, loader2);
        }
    }

    void recordConstraint(Symbol<Type> type, Klass klass, StaticObject loader) {
        assert !Types.isArray(type);
        if (!Types.isPrimitive(type)) {
            constraints.recordConstraint(type, klass, loader);
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

    public boolean isClassLoader(StaticObject object) {
        if (InterpreterToVM.instanceOf(object, context.getMeta().java_lang_ClassLoader)) {
            synchronized (weakClassLoaderSet) {
                return weakClassLoaderSet.contains(object);
            }
        }
        return false;
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
            meta.java_lang_ClassLoader_checkPackageAccess.invokeDirect(classLoader, klass.mirror(), protectionDomain);
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
