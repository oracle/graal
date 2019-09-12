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

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
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

    private final EspressoContext context;

    /**
     * The map from symbol to classes for the classes defined by the class loader associated with
     * this registry. Use of {@link ConcurrentHashMap} allows for atomic insertion while still
     * supporting fast, non-blocking lookup. There's no need for deletion as class unloading removes
     * a whole class registry and all its contained classes.
     */
    protected final ConcurrentHashMap<Symbol<Type>, Klass> classes = new ConcurrentHashMap<>();

    @Override
    public final EspressoContext getContext() {
        return context;
    }

    protected ClassRegistry(EspressoContext context) {
        this.context = context;
    }

    public Klass loadKlass(Symbol<Type> type) {
        return loadKlass(type, null);
    }

    /**
     * Queries a registry to load a Klass for us.
     * 
     * @param type the symbolic reference to the Klass we want to load
     * @param instigator Should the loading of a Klass require loading other Klasses (superKlass for
     *            example), this argument is the symbolic reference to the first Klass we attempted
     *            to load through the whole loading chain.
     * @return The Klass corresponding to given type
     */
    protected abstract Klass loadKlass(Symbol<Type> type, Symbol<Type> instigator);

    public abstract @Host(ClassLoader.class) StaticObject getClassLoader();

    public Method getAddClass() {
        throw EspressoError.shouldNotReachHere();
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
        return classes.get(type);
    }

    public ObjectKlass defineKlass(Symbol<Type> type, final byte[] bytes) {
        return defineKlass(type, bytes, null);
    }

    public ObjectKlass defineKlass(Symbol<Type> typeOrNull, final byte[] bytes, Symbol<Type> instigator) {
        Meta meta = getMeta();
        if (typeOrNull != null && classes.containsKey(typeOrNull)) {
            throw meta.throwExWithMessage(LinkageError.class, "Class " + typeOrNull + " already defined in the BCL");
        }

        String strType = typeOrNull == null ? null : typeOrNull.toString();
        ParserKlass parserKlass = getParserKlass(bytes, strType);
        Symbol<Type> type = typeOrNull == null ? parserKlass.getType() : typeOrNull;
        Symbol<Type> superKlassType = parserKlass.getSuperKlass();

        if (type == superKlassType || (superKlassType != null && instigator == superKlassType)) {
            throw meta.throwEx(ClassCircularityError.class);
        }

        ObjectKlass klass = createAndPutKlass(meta, parserKlass, type, superKlassType);

        return klass;
    }

    private ParserKlass getParserKlass(byte[] bytes, String strType) {
        ParserKlass parserKlass = null;
        try {
            parserKlass = ClassfileParser.parse(new ClassfileStream(bytes, null), strType, null, context);
        } catch (NoClassDefFoundError ncdfe) {
            throw getMeta().throwExWithMessage(NoClassDefFoundError.class, ncdfe.getMessage());
        }

        if (StaticObject.notNull(getClassLoader()) && parserKlass.getName().toString().startsWith("java/")) {
            throw getMeta().throwExWithMessage(SecurityException.class, "Define class in prohibited package name: " + parserKlass.getName());
        }
        return parserKlass;
    }

    private ObjectKlass createAndPutKlass(Meta meta, ParserKlass parserKlass, Symbol<Type> type, Symbol<Type> superKlassType) {
        ObjectKlass superKlass;
        if (superKlassType == null) {
            superKlass = null;
        } else {
            superKlass = loadKlassRecursively(meta, superKlassType, true);
        }

        final Symbol<Type>[] superInterfacesTypes = parserKlass.getSuperInterfaces();

        LinkedKlass[] linkedInterfaces = superInterfacesTypes.length == 0
                        ? LinkedKlass.EMPTY_ARRAY
                        : new LinkedKlass[superInterfacesTypes.length];

        ObjectKlass[] superInterfaces = superInterfacesTypes.length == 0
                        ? ObjectKlass.EMPTY_ARRAY
                        : new ObjectKlass[superInterfacesTypes.length];

        for (int i = 0; i < superInterfacesTypes.length; ++i) {
            ObjectKlass interf = loadKlassRecursively(meta, superInterfacesTypes[i], false);
            superInterfaces[i] = interf;
            linkedInterfaces[i] = interf.getLinkedKlass();
        }

        // FIXME(peterssen): Do NOT create a LinkedKlass every time, use a global cache.
        LinkedKlass linkedKlass = new LinkedKlass(parserKlass, superKlass == null ? null : superKlass.getLinkedKlass(), linkedInterfaces);

        ObjectKlass klass = new ObjectKlass(context, linkedKlass, superKlass, superInterfaces, getClassLoader());

        Klass previous = classes.putIfAbsent(type, klass);
        if (previous != null) {
            throw meta.throwExWithMessage(LinkageError.class, "Class " + previous + " loaded twice");
        }

        if (!StaticObject.isNull(getClassLoader())) {
            getAddClass().invokeDirect(getClassLoader(), klass.mirror());
        }

        if (superKlass != null) {
            if (!Klass.checkAccess(superKlass, klass)) {
                throw meta.throwExWithMessage(meta.IllegalAccessError, meta.toGuestString("class " + type + " cannot access its superclass " + superKlassType));
            }
        }

        for (ObjectKlass interf : superInterfaces) {
            if (interf != null && !Klass.checkAccess(interf, klass)) {
                throw meta.throwExWithMessage(meta.IllegalAccessError, meta.toGuestString("class " + type + " cannot access its superinterface " + interf.getType()));
            }
        }
        return klass;
    }

    private ObjectKlass loadKlassRecursively(Meta meta, Symbol<Type> type, boolean notInterface) {
        Klass klass;
        try {
            klass = loadKlass(type);
        } catch (EspressoException e) {
            if (meta.ClassNotFoundException.isAssignableFrom(e.getException().getKlass())) {
                // NoClassDefFoundError has no <init>(Throwable cause). Set cause manually.
                StaticObject ncdfe = Meta.initEx(meta.NoClassDefFoundError);
                meta.Throwable_cause.set(ncdfe, e.getException());
                throw new EspressoException(ncdfe);
            }
            throw e;
        }
        if (notInterface == klass.isInterface()) {
            throw meta.throwExWithMessage(IncompatibleClassChangeError.class, "Super interface of " + type + " is in fact not an interface.");
        }
        return (ObjectKlass) klass;
    }

    public ObjectKlass putKlass(Symbol<Type> type, final ObjectKlass klass) {
        if (classes.containsKey(type)) {
            throw getMeta().throwExWithMessage(LinkageError.class, "Class " + type + " already defined in the BCL");
        }
        Klass previous = classes.put(type, klass);
        if (previous != null) {
            throw getMeta().throwExWithMessage(LinkageError.class, "Class " + previous + " loaded twice");
        }
        return klass;
    }
}
