/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.LinkedClassfileParser;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.redefinition.DefineKlassListener;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class LinkedClassRegistry {

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

    /**
     * The map from symbol to classes for the classes defined by the class loader associated with
     * this registry. Use of {@link ConcurrentHashMap} allows for atomic insertion while still
     * supporting fast, non-blocking lookup. There's no need for deletion as class unloading removes
     * a whole class registry and all its contained classes.
     */
    protected final ConcurrentHashMap<Symbol<Symbol.Type>, ClassRegistries.RegistryEntry> classes = new ConcurrentHashMap<>();

    /**
     * Strong hidden classes must be referenced by the class loader data to prevent them from being
     * reclaimed, while not appearing in the actual registry. This field simply keeps those hidden
     * classes strongly reachable from the class registry.
     */
    private volatile Collection<Klass> strongHiddenKlasses = null;

    private Object getStrongHiddenClassRegistrationLock() {
        return this;
    }

    private void registerStrongHiddenClass(Klass klass) {
        synchronized (getStrongHiddenClassRegistrationLock()) {
            if (strongHiddenKlasses == null) {
                strongHiddenKlasses = new ArrayList<>();
            }
            strongHiddenKlasses.add(klass);
        }
    }

    private final int loaderID;

    private ModuleTable.ModuleEntry unnamed;
    private final PackageTable packages;
    private final ModuleTable modules;

    protected LinkedClassRegistry(int loaderID) {
        this.loaderID = loaderID;
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.packages = new PackageTable(rwLock);
        this.modules = new ModuleTable(rwLock);
    }

    ParserKlass loadParserKlass(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) {
        if (Types.isArray(type)) {
            throw EspressoError.shouldNotReachHere("Array type provided to loadParserKlass");
        }

        ParserKlass parserKlass;
        synchronized (type) {
            if (!performChecksPriorToLoading(type)) {
                return null;
            }
            byte[] bytes = getClassfileBytes(type);
            parserKlass = createParserKlass(env, bytes, type, info);
        }

        return parserKlass;
    }

    public ParserKlass createParserKlass(ClassLoadingEnv env, byte[] bytes, Symbol<Type> typeOrNull, ClassRegistry.ClassDefinitionInfo info) {
        // TODO (ivan-ristovic): Consult cache first
        ParserKlass parserKlass = LinkedClassfileParser.parse(env, new ClassfileStream(bytes, null), getClassLoader(), typeOrNull, info);
        // May throw guest ClassFormatError, NoClassDefFoundError.
        if (!env.isLoaderBootOrPlatform(getClassLoader()) && parserKlass.getName().toString().startsWith("java/")) {
            throw env.generateSecurityException("Define class in prohibited package name: " + parserKlass.getName());
        }
        return parserKlass;
    }

    LinkedKlass loadLinkedKlass(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) {
        ParserKlass parserKlass = loadParserKlass(env, type, info);
        return parserKlass != null ? createLinkedKlass(env, parserKlass, info) : null;
    }

    private LinkedKlass loadLinkedKlassRecursively(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info, boolean notInterface) {
        LinkedKlass linkedKlass;
        try {
            linkedKlass = loadLinkedKlass(env, type, info);
        } catch (EspressoException e) {
            throw env.wrapIntoClassDefNotFoundError(e);
        }
        if (notInterface == Modifier.isInterface(linkedKlass.getFlags())) {
            throw env.generateIncompatibleClassChangeError("Super interface of " + type + " is in fact not an interface.");
        }
        return linkedKlass;
    }

    LinkedKlass createLinkedKlass(ClassLoadingEnv env, ParserKlass parserKlass, ClassRegistry.ClassDefinitionInfo info) {
        Symbol<Type> type = parserKlass.getType();
        Symbol<Type> superKlassType = parserKlass.getSuperKlass();
        EspressoThreadLocalState threadLocalState = env.getLanguage().getThreadLocalState();
        ClassRegistry.TypeStack chain = threadLocalState.getTypeStack();

        LinkedKlass superKlass = null;
        LinkedKlass[] superInterfaces = null;
        LinkedKlass[] linkedInterfaces = null;

        chain.push(type);

        try {
            if (superKlassType != null) {
                if (chain.contains(superKlassType)) {
                    throw env.generateClassCircularityError();
                }
                superKlass = loadLinkedKlassRecursively(env, superKlassType, info, true);
            }

            final Symbol<Type>[] superInterfacesTypes = parserKlass.getSuperInterfaces();

            linkedInterfaces = superInterfacesTypes.length == 0
                    ? LinkedKlass.EMPTY_ARRAY
                    : new LinkedKlass[superInterfacesTypes.length];

            superInterfaces = superInterfacesTypes.length == 0
                    ? LinkedKlass.EMPTY_ARRAY
                    : new LinkedKlass[superInterfacesTypes.length];

            for (int i = 0; i < superInterfacesTypes.length; ++i) {
                if (chain.contains(superInterfacesTypes[i])) {
                    throw env.generateClassCircularityError();
                }
                LinkedKlass interf = loadLinkedKlassRecursively(env, superInterfacesTypes[i], info, false);
                superInterfaces[i] = interf;
                linkedInterfaces[i] = interf;
            }
        } finally {
            chain.pop();
        }

        if (env.getJavaVersion().java16OrLater() && superKlass != null) {
            if (Modifier.isFinal(superKlass.getFlags())) {
                throw env.generateIncompatibleClassChangeError("class " + type + " is a subclass of final class " + superKlassType);
            }
        }

        // TODO consult cache
        return LinkedKlass.create(env, parserKlass, superKlass, linkedInterfaces);
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
    Klass loadKlass(ClassLoadingEnv.InContext env, Symbol<Type> type, StaticObject protectionDomain) {
        if (Types.isArray(type)) {
            Klass elemental = loadKlass(env, env.getTypes().getElementalType(type), protectionDomain);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
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
                    // TODO (ivan-ristovic): info?
                    if (loadKlassImpl(env, type, ClassRegistry.ClassDefinitionInfo.EMPTY) == null) {
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
        entry.checkPackageAccess(env.getMeta(), getClassLoader(), protectionDomain);
        return entry.klass();
    }

    private Klass loadKlassImpl(ClassLoadingEnv.InContext env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) {
        if (!performChecksPriorToLoading(type)) {
            return null;
        }
        byte[] bytes = getClassfileBytes(type);
        ObjectKlass klass = defineKlass(env, type, bytes, info);
        afterLoading(klass);
        return klass;
    }

    public Klass findLoadedKlass(ClassLoadingEnv.InContext env, Symbol<Type> type) {
        if (Types.isArray(type)) {
            Symbol<Type> elemental = env.getTypes().getElementalType(type);
            Klass elementalKlass = findLoadedKlass(env, elemental);
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

    public final ObjectKlass defineKlass(ClassLoadingEnv.InContext env, Symbol<Type> typeOrNull, final byte[] bytes) {
        return defineKlass(env, typeOrNull, bytes, ClassRegistry.ClassDefinitionInfo.EMPTY);
    }

    @SuppressWarnings("try")
    public ObjectKlass defineKlass(ClassLoadingEnv.InContext env, Symbol<Type> typeOrNull, final byte[] bytes, ClassRegistry.ClassDefinitionInfo info) {
        Meta meta = env.getMeta();
        ParserKlass parserKlass;
        try (DebugCloseable parse = KLASS_PARSE.scope(env.getTimers())) {
            parserKlass = createParserKlass(env, bytes, typeOrNull, info);
        }
        Symbol<Type> type = typeOrNull == null ? parserKlass.getType() : typeOrNull;

        if (info.addedToRegistry()) {
            Klass maybeLoaded = findLoadedKlass(env, type);
            if (maybeLoaded != null) {
                throw meta.throwExceptionWithMessage(meta.java_lang_LinkageError, "Class " + type + " already defined");
            }
        }

        LinkedKlass linkedKlass = createLinkedKlass(env, parserKlass, info);
        ObjectKlass klass = createKlass(env, linkedKlass, info);
        if (info.addedToRegistry()) {
            registerKlass(env, klass, type);
        } else if (info.isStrongHidden()) {
            registerStrongHiddenClass(klass);
        }
        return klass;
    }

    @SuppressWarnings("try")
    private ObjectKlass createKlass(ClassLoadingEnv.InContext env, LinkedKlass linkedKlass, ClassRegistry.ClassDefinitionInfo info) {
        Symbol<Type> type = linkedKlass.getType();
        Symbol<Type> superKlassType = null;
        Meta meta = env.getMeta();

        EspressoThreadLocalState threadLocalState = env.getLanguage().getThreadLocalState();
        ClassRegistry.TypeStack chain = threadLocalState.getTypeStack();

        ObjectKlass superKlass = null;
        if (linkedKlass.getSuperKlass() != null) {
            superKlassType = linkedKlass.getSuperKlass().getType();
            superKlass = loadKlassRecursively(env, linkedKlass.getSuperKlass().getType(), true);
        }

        LinkedKlass[] linkedInterfaces = linkedKlass.getInterfaces();
        ObjectKlass[] superInterfaces = linkedInterfaces.length == 0
                ? ObjectKlass.EMPTY_ARRAY
                : new ObjectKlass[linkedInterfaces.length];

        for (int i = 0; i < linkedInterfaces.length; ++i) {
            ObjectKlass interf = loadKlassRecursively(env, linkedInterfaces[i].getType(), false);
            superInterfaces[i] = interf;
            linkedInterfaces[i] = interf.getLinkedKlass();
        }

        if (env.getJavaVersion().java16OrLater() && superKlass != null) {
            if (superKlass.isFinalFlagSet()) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "class " + type + " is a subclass of final class " + superKlassType);
            }
        }

        ObjectKlass klass;

        try (DebugCloseable define = KLASS_DEFINE.scope(env.getTimers())) {
            klass = new ObjectKlass(meta.getContext(), linkedKlass, superKlass, superInterfaces, getClassLoader(), info);
        }

        if (superKlass != null) {
            if (!Klass.checkAccess(superKlass, klass)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, "class " + type + " cannot access its superclass " + superKlassType);
            }
            if (!superKlass.permittedSubclassCheck(klass)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "class " + type + " is not a permitted subclass of class " + superKlassType);
            }
        }

        for (ObjectKlass interf : superInterfaces) {
            if (interf != null) {
                if (!Klass.checkAccess(interf, klass)) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, "class " + type + " cannot access its superinterface " + interf.getType());
                }
                if (!interf.permittedSubclassCheck(klass)) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "class " + type + " is not a permitted subclass of interface " + superKlassType);
                }
            }
        }

        return klass;
    }

    private ObjectKlass loadKlassRecursively(ClassLoadingEnv.InContext env, Symbol<Type> type, boolean notInterface) {
        Meta meta = env.getMeta();
        Klass klass;
        try {
            klass = loadKlass(env, type, StaticObject.NULL);
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

    private void registerKlass(ClassLoadingEnv.InContext env, ObjectKlass klass, Symbol<Type> type) {
        ClassRegistries.RegistryEntry entry = new ClassRegistries.RegistryEntry(klass);
        ClassRegistries.RegistryEntry previous = classes.putIfAbsent(type, entry);

        EspressoError.guarantee(previous == null, "Class " + type + " is already defined");

        env.getRegistries().recordConstraint(type, klass, getClassLoader());
        env.getRegistries().onKlassDefined(klass);
        if (defineKlassListener != null) {
            defineKlassListener.onKlassDefined(klass);
        }
    }

    protected abstract boolean performChecksPriorToLoading(Symbol<Type> type);

    protected abstract byte[] getClassfileBytes(Symbol<Type> type);

    protected abstract void afterLoading(Klass klass);

    protected abstract void loadKlassCountInc();

    protected abstract void loadKlassCacheHitsInc();

    public abstract @JavaType(ClassLoader.class) StaticObject getClassLoader();
}
