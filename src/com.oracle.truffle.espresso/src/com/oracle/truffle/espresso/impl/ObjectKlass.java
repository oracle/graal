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

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.util.Arrays;
import java.util.stream.Stream;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Represents resolved non-primitive, non-array types in Espresso.
 */
public final class ObjectKlass extends Klass {
    private final Klass superclass;
    private final Klass[] interfaces;
    private final MethodInfo[] declaredMethods;

    private final FieldInfo[] declaredFields;
    private final int accessFlags;
    private final EnclosingMethodAttribute enclosingMethod;
    private final ConstantPool pool;

    private FieldInfo[] instanceFieldsCache;
    private final InnerClassesAttribute innerClasses;

    private int initState = LOADED;
    public static final int LOADED = 0;
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;

    public ObjectKlass(EspressoContext context, String name, Klass superclass, Klass[] interfaces,
                    MethodInfo[] declaredMethods,
                    FieldInfo[] declaredFields,
                    int accessFlags,
                    EnclosingMethodAttribute enclosingMethod,
                    InnerClassesAttribute innerClasses,
                    ConstantPool pool) {
        super(name);
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.declaredMethods = declaredMethods;
        this.declaredFields = declaredFields;
        this.accessFlags = accessFlags;
        this.enclosingMethod = enclosingMethod;
        this.innerClasses = innerClasses;
        this.pool = pool;
    }

    @Override
    public EspressoContext getContext() {
        return pool.getContext();
    }

    public static Klass create(EspressoContext context, String className, Klass superClass, Klass[] localInterfaces, MethodInfo.Builder[] methodBuilders, FieldInfo.Builder[] fieldBuilders,
                    int accessFlags, EnclosingMethodAttribute enclosingMethod, InnerClassesAttribute innerClasses, ConstantPool pool) {
        MethodInfo[] methods = new MethodInfo[methodBuilders.length];
        FieldInfo[] fields = new FieldInfo[fieldBuilders.length];
        ObjectKlass result = new ObjectKlass(context, className, superClass, localInterfaces, methods, fields, accessFlags, enclosingMethod, innerClasses, pool);
        for (int i = 0; i < methods.length; ++i) {
            methods[i] = methodBuilders[i].setDeclaringClass(result).build();
        }
        for (int i = 0; i < fields.length; ++i) {
            fields[i] = fieldBuilders[i].setDeclaringClass(result).build();
        }
        assert result.getConstantPool().getContext() == context;
        return result;
    }

    public int getInitState() {
        return initState;
    }

    @Override
    public ConstantPool getConstantPool() {
        return pool;
    }

    @Override
    public boolean hasFinalizer() {
        throw EspressoError.unimplemented();
    }

    @Override
    public int getModifiers() {
        return getAccessFlags() & EspressoModifiers.jvmClassModifiers();
    }

    private int getAccessFlags() {
        return accessFlags;
    }

    @Override
    public boolean isInstanceClass() {
        return !isArray() && !isInterface();
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return initState == INITIALIZED;
    }

    @Override
    public void initialize() {
        if (!isInitialized()) {
            if (getSuperclass() != null) {
                getSuperclass().initialize();
            }
            initState = INITIALIZED;
            meta(this).getClassInitializer().ifPresent(clinit -> clinit.invokeDirect());
            assert isInitialized();
        }
    }

    @Override
    public boolean isLinked() {
        throw EspressoError.unimplemented();
    }

    @Override
    public boolean isAssignableFrom(Klass other) {
        throw EspressoError.unimplemented();
    }

    @Override
    public Klass getHostClass() {
        return null;
    }

    @Override
    public Klass getSuperclass() {
        return superclass;
    }

    @Override
    public Klass[] getInterfaces() {
        return interfaces;
    }

    @Override
    public Klass findLeastCommonAncestor(Klass otherType) {
        return null;
    }

    @Override
    public Klass getComponentType() {
        return null;
    }

    @Override
    public MethodInfo resolveMethod(MethodInfo method, Klass callerType) {
        return null;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public Object getClassLoader() {
        return getConstantPool().getClassLoader();
    }

    @Override
    public FieldInfo[] getInstanceFields(boolean includeSuperclasses) {
        if (!includeSuperclasses) {
            return Arrays.stream(declaredFields).filter(f -> !f.isStatic()).toArray(FieldInfo[]::new);
        }
        if (instanceFieldsCache == null) {
            Stream<FieldInfo> fields = Arrays.stream(declaredFields).filter(f -> !f.isStatic());
            if (includeSuperclasses && getSuperclass() != null) {
                fields = Stream.concat(Arrays.stream(getSuperclass().getInstanceFields(includeSuperclasses)), fields);
            }
            instanceFieldsCache = fields.toArray(FieldInfo[]::new);
        }
        return instanceFieldsCache;
    }

    @Override
    public FieldInfo[] getStaticFields() {
        // TODO(peterssen): Cache static fields.
        return Arrays.stream(declaredFields).filter(ModifiersProvider::isStatic).toArray(FieldInfo[]::new);
    }

    @Override
    public FieldInfo findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        return null;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public Klass getEnclosingType() {
        return null;
    }

    @Override
    public MethodInfo[] getDeclaredConstructors() {
        return Arrays.stream(declaredMethods).filter(m -> "<init>".equals(m.getName())).toArray(MethodInfo[]::new);
    }

    @Override
    public MethodInfo[] getDeclaredMethods() {
        return declaredMethods;
    }

    @Override
    public FieldInfo[] getDeclaredFields() {
        return declaredFields;
    }

    public EnclosingMethodAttribute getEnclosingMethod() {
        return enclosingMethod;
    }

    public InnerClassesAttribute getInnerClasses() {
        return innerClasses;
    }

    public ObjectKlass getSupertype() {
        if (isInterface()) {
            return (ObjectKlass) getContext().getMeta().OBJECT.rawKlass();
        }
        return (ObjectKlass) getSuperclass();
    }
}
