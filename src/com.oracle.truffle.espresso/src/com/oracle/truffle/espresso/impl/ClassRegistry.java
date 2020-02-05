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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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

    /**
     * Traces the classes being initialized by this thread. Its only use is to be able to detect
     * class circularity errors. A class being defined, that needs its superclass also to be defined
     * will be pushed onto this stack. If the superclass is already present, then there is a
     * circularity error.
     */
    // TODO: Rework this, a thread local is certainly less than optimal.
    static final ThreadLocal<TypeStack> stack = ThreadLocal.withInitial(TypeStack.supplier);

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

    /**
     * Queries a registry to load a Klass for us.
     * 
     * @param type the symbolic reference to the Klass we want to load
     * @return The Klass corresponding to given type
     */
    protected Klass loadKlass(Symbol<Type> type) {
        if (Types.isArray(type)) {
            Klass elemental = loadKlass(getTypes().getElementalType(type));
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }

        loadKlassCountInc();

        // Double-checked locking on the symbol (globally unique).
        Klass klass = classes.get(type);
        if (klass == null) {
            synchronized (type) {
                klass = classes.get(type);
                if (klass == null) {
                    klass = loadKlassImpl(type);
                }
            }
        } else {
            // Grabbing a lock to fetch the class is not considered a hit.
            loadKlassCacheHitsInc();
        }
        return klass;
    }

    protected abstract Klass loadKlassImpl(Symbol<Type> type);

    protected abstract void loadKlassCountInc();

    protected abstract void loadKlassCacheHitsInc();

    public abstract @Host(ClassLoader.class) StaticObject getClassLoader();

    public Klass[] getLoadedKlasses() {
        return classes.values().toArray(Klass.EMPTY_ARRAY);
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

    public ObjectKlass defineKlass(Symbol<Type> typeOrNull, final byte[] bytes) {
        Meta meta = getMeta();
        String strType = typeOrNull == null ? null : typeOrNull.toString();
        ParserKlass parserKlass = getParserKlass(bytes, strType);
        Symbol<Type> type = typeOrNull == null ? parserKlass.getType() : typeOrNull;

        Klass maybeLoaded = findLoadedKlass(type);
        if (maybeLoaded != null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_LinkageError, "Class " + type + " already defined");
        }

        Symbol<Type> superKlassType = parserKlass.getSuperKlass();

        return createAndPutKlass(meta, parserKlass, type, superKlassType);
    }

    private ParserKlass getParserKlass(byte[] bytes, String strType) {
        // May throw guest ClassFormatError, NoClassDefFoundError.
        ParserKlass parserKlass = ClassfileParser.parse(new ClassfileStream(bytes, null), strType, null, context);
        if (StaticObject.notNull(getClassLoader()) && parserKlass.getName().toString().startsWith("java/")) {
            throw getMeta().throwExceptionWithMessage(getMeta().java_lang_SecurityException, "Define class in prohibited package name: " + parserKlass.getName());
        }
        return parserKlass;
    }

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

        // FIXME(peterssen): Do NOT create a LinkedKlass every time, use a global cache.
        LinkedKlass linkedKlass = new LinkedKlass(parserKlass, superKlass == null ? null : superKlass.getLinkedKlass(), linkedInterfaces);

        ObjectKlass klass = new ObjectKlass(context, linkedKlass, superKlass, superInterfaces, getClassLoader());

        if (superKlass != null && !Klass.checkAccess(superKlass, klass)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, "class " + type + " cannot access its superclass " + superKlassType);
        }

        for (ObjectKlass interf : superInterfaces) {
            if (interf != null && !Klass.checkAccess(interf, klass)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, "class " + type + " cannot access its superinterface " + interf.getType());
            }
        }

        Klass previous = classes.putIfAbsent(type, klass);
        EspressoError.guarantee(previous == null, "Class " + type + " is already defined");

        getRegistries().recordConstraint(type, klass, getClassLoader());
        return klass;
    }

    private ObjectKlass loadKlassRecursively(Meta meta, Symbol<Type> type, boolean notInterface) {
        Klass klass;
        try {
            klass = loadKlass(type);
        } catch (EspressoException e) {
            if (meta.java_lang_ClassNotFoundException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                // NoClassDefFoundError has no <init>(Throwable cause). Set cause manually.
                StaticObject ncdfe = Meta.initException(meta.java_lang_NoClassDefFoundError);
                meta.java_lang_Throwable_cause.set(ncdfe, e.getExceptionObject());
                throw new EspressoException(ncdfe);
            }
            throw e;
        }
        if (notInterface == klass.isInterface()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "Super interface of " + type + " is in fact not an interface.");
        }
        return (ObjectKlass) klass;
    }
}
