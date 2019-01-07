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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.types.TypeDescriptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ClassRegistries {

    private final ClassRegistry bootClassRegistry;
    private final ConcurrentHashMap<StaticObject, ClassRegistry> registries;
    private final EspressoContext context;

    public ClassRegistries(EspressoContext context) {
        this.context = context;
        this.registries = new ConcurrentHashMap<>();
        this.bootClassRegistry = new BootClassRegistry(context);
    }

    public Klass findLoadedClass(TypeDescriptor type, StaticObject classLoader) {
        assert classLoader != null;
        if (type.isArray()) {
            Klass pepe = findLoadedClass(type.getComponentType(), classLoader);
            if (pepe != null) {
                return pepe.getArrayClass();
            }
            return null;
        }
        if (StaticObject.isNull(classLoader)) {
            return bootClassRegistry.findLoadedClass(type);
        }
        ClassRegistry registry = registries.get(classLoader);
        if (registry == null) {
            return null;
        }
        return registry.findLoadedClass(type);
    }

    @CompilerDirectives.TruffleBoundary
    public Klass resolveWithBootClassLoader(TypeDescriptor type) {
        return resolve(type, StaticObject.NULL);
    }

    @CompilerDirectives.TruffleBoundary
    public Klass resolve(TypeDescriptor type, StaticObject classLoader) {
        assert classLoader != null;
        Klass k = findLoadedClass(type, classLoader);
        if (k != null) {
            return k;
        }
        if (StaticObject.isNull(classLoader)) {
            return bootClassRegistry.resolve(type);
        } else {
            ClassRegistry registry = registries.computeIfAbsent(classLoader, new Function<StaticObject, ClassRegistry>() {
                @Override
                public ClassRegistry apply(StaticObject cl) {
                    return new GuestClassRegistry(context, cl);
                }
            });
            return registry.resolve(type);
        }
    }

    public Klass defineKlass(String name, byte[] bytes, StaticObject classLoader) {
        assert classLoader != null;
        ClasspathFile cpf = new ClasspathFile(bytes, null, name);
        ClassfileParser parser = new ClassfileParser(classLoader, new ClassfileStream(bytes, 0, bytes.length, cpf), name, null, EspressoLanguage.getCurrentContext());

        // TODO(peterssen): Propagate errors to the guest.
        // Class parsing should be moved to ClassRegistry.
        StaticObjectClass klass = (StaticObjectClass) parser.parseClass().mirror();

        ClassRegistry registry = StaticObject.isNull(classLoader) ? bootClassRegistry : registries.get(classLoader);
        TypeDescriptor descriptor = context.getTypeDescriptors().make(MetaUtil.toInternalName(name));
        registry.defineKlass(descriptor, klass.getMirror());
        return klass.getMirror();
    }
}
