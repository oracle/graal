/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.impl;

import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.api.RedefineObject;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class CachedRedefineObject extends RedefineObjectImpl {

    protected final EspressoContext context;
    private final HashMap<String, Method> methodsCache = new HashMap<>(1);
    private final HashMap<String, Field> fieldsCache = new HashMap<>(1);

    public CachedRedefineObject(StaticObject object) {
        super(object);
        this.context = object.getKlass().getContext();
    }

    public CachedRedefineObject(KlassRef klass) {
        super(klass);
        this.context = ((Klass) klass).getContext();
    }

    @Override
    @TruffleBoundary
    public Object invoke(String name, RedefineObject... args) throws NoSuchMethodException {
        StringBuilder stringBuffer = new StringBuilder(name);
        for (RedefineObject arg : args) {
            stringBuffer.append(((RedefineObjectImpl) arg).instance.get().getKlass().getNameAsString());
        }
        String mapKey = stringBuffer.toString();
        Method method = methodsCache.get(mapKey);
        if (method == null) {
            method = lookupMethod(name, args);
            if (method != null) {
                methodsCache.put(mapKey, method);
            }
        }
        if (method != null) {
            StaticObject theInstance = instance.get();
            if (theInstance == null) {
                throw new IllegalStateException("cannot invoke method on garbage collection instance");
            }
            RedefineObjectImpl[] internalArgs = new RedefineObjectImpl[args.length];
            for (int i = 0; i < args.length; i++) {
                internalArgs[i] = (RedefineObjectImpl) args[i];
            }
            return method.invokeDirect(theInstance, rawObjects(internalArgs));
        }
        throw new NoSuchMethodException();
    }

    @Override
    public RedefineObject invokePrecise(String className, String methodName, RedefineObject... args) throws NoSuchMethodException, IllegalStateException {
        // fetch the known declaring class of the method
        StaticObject theInstance = instance.get();
        if (instance == null) {
            throw new IllegalStateException();
        }
        Symbol<Symbol.Type> type = context.getTypes().fromClassGetName(className);
        Klass klassRef = context.getRegistries().findLoadedClass(type, klass.getDefiningClassLoader());
        if (klassRef != null) {
            Method method = lookupMethod(klassRef, methodName, args);
            if (method != null) {
                RedefineObjectImpl[] internalArgs = new RedefineObjectImpl[args.length];
                for (int i = 0; i < args.length; i++) {
                    internalArgs[i] = (RedefineObjectImpl) args[i];
                }
                return InternalRedefinitionPlugin.createUncached(method.invokeDirect(theInstance, rawObjects(internalArgs)));
            }
        }
        throw new NoSuchMethodException();
    }

    @Override
    public RedefineObject getInstanceField(String fieldName) throws NoSuchFieldException {
        StaticObject theInstance = instance.get();
        if (theInstance == null) {
            throw new IllegalStateException("cannot get field on garbage collection instance");
        }
        Field field = fieldsCache.get(fieldName);
        if (field == null) {
            Klass klassRef = klass;
            while (klassRef != null) {
                for (Field declaredField : klassRef.getDeclaredFields()) {
                    if (declaredField.getNameAsString().equals(fieldName)) {
                        field = declaredField;
                        fieldsCache.put(fieldName, field);
                        break;
                    }
                }
                klassRef = klassRef.getSuperKlass();
            }
            if (field == null) {
                throw new NoSuchFieldException();
            }
        }
        return InternalRedefinitionPlugin.createUncached(field.get(theInstance));
    }
}
