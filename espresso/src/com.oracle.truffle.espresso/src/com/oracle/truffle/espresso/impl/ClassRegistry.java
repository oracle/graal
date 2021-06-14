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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.redefinition.DefineKlassListener;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

/**
 * A {@link ClassRegistry} maps type names to resolved {@link Klass} instances. Each class loader is
 * associated with a {@link ClassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public abstract class ClassRegistry implements ContextAccess {

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
    static final ThreadLocal<TypeStack> stack = ThreadLocal.withInitial(TypeStack.supplier);

    private DefineKlassListener defineKlassListener;

    public void registerOnLoadListener(DefineKlassListener listener) {
        defineKlassListener = listener;
    }

    static final class TypeStack {
        static final Supplier<TypeStack> supplier = new Supplier<TypeStack>() {
            @Override
            public TypeStack get() {
                return new TypeStack();
            }
        };
        Node head = null;

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
                throw EspressoError.shouldNotReachHere();
            }
            Symbol<Type> res = head.entry;
            head = head.next;
            return res;
        }

        void push(Symbol<Type> type) {
            head = new Node(type, head);
        }

        private TypeStack() {
        }
    }

    private final EspressoContext context;

    private final int loaderID;

    private ModuleEntry unnamed;
    private final PackageTable packages;
    private final ModuleTable modules;

    public ModuleEntry getUnnamedModule() {
        return unnamed;
    }

    public final int getLoaderID() {
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

    @Override
    public final EspressoContext getContext() {
        return context;
    }

    protected ClassRegistry(EspressoContext context) {
        this.context = context;
        this.loaderID = context.getNewLoaderId();
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.packages = new PackageTable(rwLock);
        this.modules = new ModuleTable(rwLock);

    }

    public void initUnnamedModule(StaticObject unnamedModule) {
        this.unnamed = ModuleEntry.createUnnamedModuleEntry(unnamedModule, this);
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
    Klass loadKlass(Symbol<Type> type, StaticObject protectionDomain) {
        if (Types.isArray(type)) {
            Klass elemental = loadKlass(getTypes().getElementalType(type), protectionDomain);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }

        loadKlassCountInc();

        // Double-checked locking on the symbol (globally unique).
        ClassRegistries.RegistryEntry entry;
        try (DebugCloseable probe = KLASS_PROBE.scope(getContext().getTimers())) {
            entry = classes.get(type);
        }
        if (entry == null) {
            synchronized (type) {
                entry = classes.get(type);
                if (entry == null) {
                    if (loadKlassImpl(type) == null) {
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
        entry.checkPackageAccess(getMeta(), getClassLoader(), protectionDomain);
        return entry.klass();
    }

    protected abstract Klass loadKlassImpl(Symbol<Type> type);

    protected abstract void loadKlassCountInc();

    protected abstract void loadKlassCacheHitsInc();

    public abstract @Host(ClassLoader.class) StaticObject getClassLoader();

    public List<Klass> getLoadedKlasses() {
        ArrayList<Klass> klasses = new ArrayList<>(classes.size());
        for (ClassRegistries.RegistryEntry entry : classes.values()) {
            klasses.add(entry.klass());
        }
        return klasses;
    }

    public Klass findLoadedKlass(Symbol<Type> type) {
        if (Types.isArray(type)) {
            Symbol<Type> elemental = context.getTypes().getElementalType(type);
            Klass elementalKlass = findLoadedKlass(elemental);
            if (elementalKlass == null) {
                return null;
            }
            return elementalKlass.getArrayClass(Types.getArrayDimensions(type));
        }
        ClassRegistries.RegistryEntry entry = classes.get(type);
        if (entry == null) {
            return null;
        }
        return entry.klass();
    }

    @SuppressWarnings("try")
    public ObjectKlass defineKlass(Symbol<Type> typeOrNull, final byte[] bytes) {
        Meta meta = getMeta();
        String strType = typeOrNull == null ? null : typeOrNull.toString();
        ParserKlass parserKlass;
        try (DebugCloseable parse = KLASS_PARSE.scope(getContext().getTimers())) {
            parserKlass = getParserKlass(bytes, strType);
        }
        Symbol<Type> type = typeOrNull == null ? parserKlass.getType() : typeOrNull;

        Klass maybeLoaded = findLoadedKlass(type);
        if (maybeLoaded != null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_LinkageError, "Class " + type + " already defined");
        }

        Symbol<Type> superKlassType = parserKlass.getSuperKlass();

        return createAndPutKlass(meta, parserKlass, type, superKlassType);
    }

    private ParserKlass getParserKlass(byte[] bytes, String strType) {
        ParserKlass parserKlass = context.getCache().getOrCreateParserKlass(getClassLoader(), strType, bytes, context);
        Meta meta = getMeta();
        if (!loaderIsBootOrPlatform(getClassLoader(), meta) && parserKlass.getName().toString().startsWith("java/")) {
            throw meta.throwExceptionWithMessage(meta.java_lang_SecurityException, "Define class in prohibited package name: " + parserKlass.getName());
        }
        return parserKlass;
    }

    public static boolean loaderIsBootOrPlatform(StaticObject loader, Meta meta) {
        return StaticObject.isNull(loader) ||
                        (meta.getJavaVersion().java9OrLater() && meta.jdk_internal_loader_ClassLoaders$PlatformClassLoader.isAssignableFrom(loader.getKlass()));
    }

    @SuppressWarnings("try")
    private ObjectKlass createAndPutKlass(Meta meta, ParserKlass parserKlass, Symbol<Type> type, Symbol<Type> superKlassType) {
        TypeStack chain = stack.get();

        ObjectKlass superKlass = null;
        ObjectKlass[] superInterfaces = null;
        LinkedKlass[] linkedInterfaces = null;

        chain.push(type);

        try {
            if (superKlassType != null) {
                if (chain.contains(superKlassType)) {
                    throw meta.throwException(meta.java_lang_ClassCircularityError);
                }
                superKlass = loadKlassRecursively(meta, superKlassType, true);
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
                    throw meta.throwException(meta.java_lang_ClassCircularityError);
                }
                ObjectKlass interf = loadKlassRecursively(meta, superInterfacesTypes[i], false);
                superInterfaces[i] = interf;
                linkedInterfaces[i] = interf.getLinkedKlass();
            }
        } finally {
            chain.pop();
        }
        ObjectKlass klass;

        try (DebugCloseable define = KLASS_DEFINE.scope(getContext().getTimers())) {
            LinkedKlass linkedSuperKlass = superKlass == null ? null : superKlass.getLinkedKlass();
            LinkedKlass linkedKlass = context.getCache().getOrCreateLinkedKlass(context.getLanguage(), parserKlass, linkedSuperKlass, linkedInterfaces);
            klass = new ObjectKlass(context, linkedKlass, superKlass, superInterfaces, getClassLoader());
        }

        if (superKlass != null && !Klass.checkAccess(superKlass, klass)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, "class " + type + " cannot access its superclass " + superKlassType);
        }

        for (ObjectKlass interf : superInterfaces) {
            if (interf != null && !Klass.checkAccess(interf, klass)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, "class " + type + " cannot access its superinterface " + interf.getType());
            }
        }

        ClassRegistries.RegistryEntry entry = new ClassRegistries.RegistryEntry(klass);
        ClassRegistries.RegistryEntry previous = classes.putIfAbsent(type, entry);

        EspressoError.guarantee(previous == null, "Class " + type + " is already defined");

        getRegistries().recordConstraint(type, klass, getClassLoader());
        getRegistries().onKlassDefined(klass);
        if (defineKlassListener != null) {
            defineKlassListener.onKlassDefined(klass);
        }
        return klass;
    }

    private ObjectKlass loadKlassRecursively(Meta meta, Symbol<Type> type, boolean notInterface) {
        Klass klass;
        try {
            klass = loadKlass(type, StaticObject.NULL);
        } catch (EspressoException e) {
            if (meta.java_lang_ClassNotFoundException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                // NoClassDefFoundError has no <init>(Throwable cause). Set cause manually.
                StaticObject ncdfe = Meta.initException(meta.java_lang_NoClassDefFoundError);
                meta.java_lang_Throwable_cause.set(ncdfe, e.getExceptionObject());
                throw meta.throwException(ncdfe);
            }
            throw e;
        }
        if (notInterface == klass.isInterface()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "Super interface of " + type + " is in fact not an interface.");
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

        Klass loadedKlass = findLoadedKlass(renamedKlass.getType());
        if (loadedKlass != null) {
            context.getRegistries().removeUnloadedKlassConstraint(loadedKlass, renamedKlass.getType());
        }

        classes.put(renamedKlass.getType(), new ClassRegistries.RegistryEntry(renamedKlass));
        // record the new loading constraint
        context.getRegistries().recordConstraint(renamedKlass.getType(), renamedKlass, renamedKlass.getDefiningClassLoader());
    }

    public void onInnerClassRemoved(Symbol<Symbol.Type> type) {
        // "unload" the class by removing from classes
        ClassRegistries.RegistryEntry removed = classes.remove(type);
        // purge class loader constraint for this type
        if (removed != null && removed.klass() != null) {
            getRegistries().removeUnloadedKlassConstraint(removed.klass(), type);
        }
    }
}
