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
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.SharedConstantPool;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

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
    private final SharedConstantPool pool;

    @CompilerDirectives.CompilationFinal private StaticObject statics;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private FieldInfo[] instanceFieldsCache;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private FieldInfo[] declaredInstanceFieldsCache;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private FieldInfo[] staticFieldsCache;

    private final InnerClassesAttribute innerClasses;

    private final Attribute runtimeVisibleAnnotations;

    private final int instanceFieldSlots;

    public int getInstanceFieldSlots() {
        return instanceFieldSlots;
    }

    public int getStaticFieldSlots() {
        return getStaticFields().length;
    }

    private int initState = LOADED;
    public static final int LOADED = 0;
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;

    public ObjectKlass(String klassName, Klass superclass, Klass[] interfaces,
                       MethodInfo.Builder[] declaredMethodsBuilders,
                       FieldInfo.Builder[] declaredFieldBuilders,
                       int accessFlags,
                       EnclosingMethodAttribute enclosingMethod,
                       InnerClassesAttribute innerClasses,
                       SharedConstantPool pool, Attribute runtimeVisibleAnnotations) {
        super(klassName, JavaKind.Object);
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.accessFlags = accessFlags;
        this.enclosingMethod = enclosingMethod;
        this.innerClasses = innerClasses;
        this.pool = pool;
        this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;

        this.declaredMethods = new MethodInfo[declaredMethodsBuilders.length];
        this.declaredFields = new FieldInfo[declaredFieldBuilders.length];

        for (int i = 0; i < declaredMethods.length; ++i) {
            this.declaredMethods[i] = declaredMethodsBuilders[i].setDeclaringClass(this).build();
        }
        for (int i = 0; i < declaredFields.length; ++i) {
            this.declaredFields[i] = declaredFieldBuilders[i].setDeclaringClass(this).build();
        }

        this.instanceFieldSlots = countDeclaredInstanceFields(this.declaredFields) + (superclass == null ? 0 : ((ObjectKlass) superclass).getInstanceFieldSlots());
    }

    @Override
    public EspressoContext getContext() {
        return pool.getContext();
    }

    private static int countDeclaredInstanceFields(FieldInfo[] declaredFields) {
        int count = 0;
        for (FieldInfo fi : declaredFields) {
            if (!fi.isStatic()) {
                count++;
            }
        }
        return count;
    }

    public int getInitState() {
        return initState;
    }

    @Override
    public StaticObject tryInitializeAndGetStatics() {
        if (statics == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            statics = new StaticObjectImpl(this, true);
        }
        initialize();
        return statics;
    }

    @Override
    public SharedConstantPool getConstantPool() {
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (getSuperclass() != null) {
                getSuperclass().initialize();
            }
            initState = INITIALIZED;
            meta(this).getClassInitializer().ifPresent(new Consumer<Meta.Method.WithInstance>() {
                @Override
                public void accept(Meta.Method.WithInstance clinit) {
                    clinit.invokeDirect();
                }
            });
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
    public boolean isArray() {
        return false;
    }

    @Override
    public StaticObject getClassLoader() {
        return getConstantPool().getClassLoader();
    }

    @Override
    public FieldInfo[] getInstanceFields(boolean includeSuperclasses) {
        if (!includeSuperclasses) {
            if (declaredInstanceFieldsCache == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                declaredInstanceFieldsCache = Arrays.stream(declaredFields).filter(new Predicate<FieldInfo>() {
                    @Override
                    public boolean test(FieldInfo f) {
                        return !f.isStatic();
                    }
                }).toArray(new IntFunction<FieldInfo[]>() {
                    @Override
                    public FieldInfo[] apply(int value) {
                        return new FieldInfo[value];
                    }
                });
            }
            return declaredInstanceFieldsCache;
        }
        if (instanceFieldsCache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Stream<FieldInfo> fields = Arrays.stream(declaredFields).filter(new Predicate<FieldInfo>() {
                @Override
                public boolean test(FieldInfo f) {
                    return !f.isStatic();
                }
            });
            if (includeSuperclasses && getSuperclass() != null) {
                fields = Stream.concat(Arrays.stream(getSuperclass().getInstanceFields(includeSuperclasses)), fields);
            }
            instanceFieldsCache = fields.toArray(new IntFunction<FieldInfo[]>() {
                @Override
                public FieldInfo[] apply(int value) {
                    return new FieldInfo[value];
                }
            });
        }
        return instanceFieldsCache;
    }

    @Override
    public FieldInfo[] getStaticFields() {
        // TODO(peterssen): Cache static fields.
        if (staticFieldsCache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            staticFieldsCache = Arrays.stream(declaredFields).filter(new Predicate<FieldInfo>() {
                @Override
                public boolean test(FieldInfo fieldInfo) {
                    return fieldInfo.isStatic();
                }
            }).toArray(new IntFunction<FieldInfo[]>() {
                @Override
                public FieldInfo[] apply(int value) {
                    return new FieldInfo[value];
                }
            });
        }
        return staticFieldsCache;
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
        return Arrays.stream(declaredMethods).filter(new Predicate<MethodInfo>() {
            @Override
            public boolean test(MethodInfo m) {
                return "<init>".equals(m.getName());
            }
        }).toArray(new IntFunction<MethodInfo[]>() {
            @Override
            public MethodInfo[] apply(int value) {
                return new MethodInfo[value];
            }
        });
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

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }
}
