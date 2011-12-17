/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util;

import java.util.*;

import com.sun.max.program.*;

/**
 * The {@code Registry} class implements a type of configuration mechanism
 * that allows a short string name (called an alias) to refer to a class name.
 * The short name can be used to quickly look up an internally registered class and
 * instantiate it (assuming the target class as a public constructor which takes
 * no parameters). If the alias is not registered, the registry will try to use
 * the alias as a fully-qualified Java class and load it using reflection.
 */
public class Registry<C> {

    protected final boolean loadClass;
    protected final Class<C> classClass;
    protected final Map<String, Class<? extends C>> classMap;
    protected final Map<String, C> objectMap;
    protected final Map<String, String> stringMap;

    public Registry(Class<C> classType, boolean loadClass) {
        this.loadClass = loadClass;
        this.classClass = classType;
        this.classMap = new HashMap<String, Class<? extends C>>();
        this.objectMap = new HashMap<String, C>();
        this.stringMap = new HashMap<String, String>();
    }

    public void registerObject(String alias, C object) {
        objectMap.put(alias, object);
    }

    public void registerClass(String alias, Class<? extends C> classType) {
        classMap.put(alias, classType);
    }

    public void registerClass(String alias, String className) {
        stringMap.put(alias, className);
    }

    public C getInstance(String alias) {
        return getInstance(alias, true);
    }

    public C getInstance(String alias, boolean fatal) {
        final C object = objectMap.get(alias);
        if (object != null) {
            return object;
        }
        Class<? extends C> classRef = classMap.get(alias);
        String className = alias;
        try {
            if (classRef == null) {
                className = stringMap.get(alias);
                if (className != null) {
                    classRef = Class.forName(className).asSubclass(classClass);
                } else if (loadClass) {
                    classRef = Class.forName(alias).asSubclass(classClass);
                } else {
                    return genError(fatal, "cannot find alias", alias, className);
                }
            }
            className = classRef.getName();
            return classRef.newInstance();
        } catch (ClassNotFoundException e) {
            return genError(fatal, "cannot find class", alias, className);
        } catch (InstantiationException e) {
            return genError(fatal, "cannot instantiate class", alias, className);
        } catch (IllegalAccessException e) {
            return genError(fatal, "cannot instantiate class", alias, className);
        } catch (ClassCastException e) {
            return genError(fatal, "not a subclass of " + classClass.getName(), alias, className);
        }
    }

    public Iterable<String> getAliases() {
        final LinkedList<String> lista = new LinkedList<String>();
        lista.addAll(objectMap.keySet());
        lista.addAll(classMap.keySet());
        lista.addAll(stringMap.keySet());
        return lista;
    }

    private C genError(boolean fatal, String message, String alias, String className) {
        if (!fatal) {
            return null;
        }
        String mstr = message + ": " + alias;
        if (className != null) {
            mstr = mstr + "(" + className + ")";
        }
        throw ProgramError.unexpected(mstr);
    }

    public static <T> Registry<T> newRegistry(Class<T> cl) {
        return new Registry<T>(cl, true);
    }
}
