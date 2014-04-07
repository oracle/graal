/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta.test;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.Queue;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.runtime.*;

/**
 * Context for type related api.meta tests.
 */
public class TypeUniverse {

    public final Unsafe unsafe;
    public static final double JAVA_VERSION = Double.valueOf(System.getProperty("java.specification.version"));

    public final MetaAccessProvider metaAccess;
    public final ConstantReflectionProvider constantReflection;
    public final SnippetReflectionProvider snippetReflection;
    public final Collection<Class<?>> classes = new HashSet<>();
    public final Map<Class<?>, Class<?>> arrayClasses = new HashMap<>();
    public final List<Constant> constants = new ArrayList<>();

    public TypeUniverse() {
        Providers providers = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders();
        metaAccess = providers.getMetaAccess();
        constantReflection = providers.getConstantReflection();
        snippetReflection = Graal.getRequiredCapability(SnippetReflectionProvider.class);
        Unsafe theUnsafe = null;
        try {
            theUnsafe = Unsafe.getUnsafe();
        } catch (Exception e) {
            try {
                Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                theUnsafe = (Unsafe) theUnsafeField.get(null);
            } catch (Exception e1) {
                throw (InternalError) new InternalError("unable to initialize unsafe").initCause(e1);
            }
        }
        unsafe = theUnsafe;

        Class[] initialClasses = {void.class, boolean.class, byte.class, short.class, char.class, int.class, float.class, long.class, double.class, Object.class, Class.class, ClassLoader.class,
                        String.class, Serializable.class, Cloneable.class, Test.class, TestMetaAccessProvider.class, List.class, Collection.class, Map.class, Queue.class, HashMap.class,
                        LinkedHashMap.class, IdentityHashMap.class, AbstractCollection.class, AbstractList.class, ArrayList.class};
        for (Class c : initialClasses) {
            addClass(c);
        }
        for (Field f : Constant.class.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (f.getType() == Constant.class && Modifier.isPublic(mods) && Modifier.isStatic(mods) && Modifier.isFinal(mods)) {
                try {
                    Constant c = (Constant) f.get(null);
                    if (c != null) {
                        constants.add(c);
                    }
                } catch (Exception e) {
                }
            }
        }
        for (Class c : classes) {
            if (c != void.class && !c.isArray()) {
                constants.add(snippetReflection.forObject(Array.newInstance(c, 42)));
            }
        }
        constants.add(snippetReflection.forObject(new ArrayList<>()));
        constants.add(snippetReflection.forObject(new IdentityHashMap<>()));
        constants.add(snippetReflection.forObject(new LinkedHashMap<>()));
        constants.add(snippetReflection.forObject(new TreeMap<>()));
        constants.add(snippetReflection.forObject(new ArrayDeque<>()));
        constants.add(snippetReflection.forObject(new LinkedList<>()));
        constants.add(snippetReflection.forObject("a string"));
        constants.add(snippetReflection.forObject(42));
        constants.add(snippetReflection.forObject(String.class));
        constants.add(snippetReflection.forObject(String[].class));
    }

    public synchronized Class<?> getArrayClass(Class componentType) {
        Class<?> arrayClass = arrayClasses.get(componentType);
        if (arrayClass == null) {
            arrayClass = Array.newInstance(componentType, 0).getClass();
            arrayClasses.put(componentType, arrayClass);
        }
        return arrayClass;
    }

    public static int dimensions(Class c) {
        if (c.getComponentType() != null) {
            return 1 + dimensions(c.getComponentType());
        }
        return 0;
    }

    private void addClass(Class c) {
        if (classes.add(c)) {
            if (c.getSuperclass() != null) {
                addClass(c.getSuperclass());
            }
            for (Class sc : c.getInterfaces()) {
                addClass(sc);
            }
            for (Class dc : c.getDeclaredClasses()) {
                addClass(dc);
            }
            for (Method m : c.getDeclaredMethods()) {
                addClass(m.getReturnType());
                for (Class p : m.getParameterTypes()) {
                    addClass(p);
                }
            }

            if (c != void.class && dimensions(c) < 2) {
                Class<?> arrayClass = Array.newInstance(c, 0).getClass();
                arrayClasses.put(c, arrayClass);
                addClass(arrayClass);
            }
        }
    }
}
