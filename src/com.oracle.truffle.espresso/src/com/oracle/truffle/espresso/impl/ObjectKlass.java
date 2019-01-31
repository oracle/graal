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

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

/**
 * Represents resolved non-primitive, non-array types in Espresso.
 */
public final class ObjectKlass extends Klass {

    public static final ObjectKlass[] EMPTY_ARRAY = new ObjectKlass[0];

    // private final Method[] declaredMethods;
    // private final Field[] declaredFields;

    private final EnclosingMethodAttribute enclosingMethod;
    private final ConstantPool pool;

    private final LinkedKlass linkedKlass;

    @CompilationFinal private StaticObject statics;

    @CompilationFinal(dimensions = 1) //
    private Field[] instanceFieldsCache;

    @CompilationFinal(dimensions = 1) //
    private Field[] declaredInstanceFieldsCache;

    @CompilationFinal(dimensions = 1) //
    private Field[] staticFieldsCache;

    private final InnerClassesAttribute innerClasses;

    private final Attribute runtimeVisibleAnnotations;

// public int getInstanceFieldSlots() {
// return instanceFieldSlots;
// }
//
// public int getStaticFieldSlots() {
// return getStaticFields().length;
// }

    private int initState = LINKED;
    public static final int LOADED = 0; //
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces,
                    EnclosingMethodAttribute enclosingMethod,
                    InnerClassesAttribute innerClasses,
                    Attribute runtimeVisibleAnnotations) {
        super(context, linkedKlass.getType(), superKlass, superInterfaces);
        this.enclosingMethod = enclosingMethod;
        this.linkedKlass = linkedKlass;
        this.innerClasses = innerClasses;
        // this.pool = pool;
        this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
    }

    private static int countDeclaredInstanceFields(Field[] declaredFields) {
        int count = 0;
        for (Field fi : declaredFields) {
            if (!fi.isStatic()) {
                count++;
            }
        }
        return count;
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
    public int getFlags() {
        return linkedKlass.getFlags();
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
    public Method resolveMethod(Method method, Klass callerType) {
        return null;
    }

    @Override
    public StaticObject getClassLoader() {
        return getConstantPool().getClassLoader();
    }

    @Override
    public Field[] getInstanceFields(boolean includeSuperclasses) {
        if (!includeSuperclasses) {
            if (declaredInstanceFieldsCache == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                declaredInstanceFieldsCache = Arrays.stream(declaredFields).filter(new Predicate<Field>() {
                    @Override
                    public boolean test(Field f) {
                        return !f.isStatic();
                    }
                }).toArray(new IntFunction<Field[]>() {
                    @Override
                    public Field[] apply(int value) {
                        return new Field[value];
                    }
                });
            }
            return declaredInstanceFieldsCache;
        }
        if (instanceFieldsCache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Stream<Field> fields = Arrays.stream(declaredFields).filter(new Predicate<Field>() {
                @Override
                public boolean test(Field f) {
                    return !f.isStatic();
                }
            });
            if (includeSuperclasses && getSuperclass() != null) {
                fields = Stream.concat(Arrays.stream(getSuperclass().getInstanceFields(includeSuperclasses)), fields);
            }
            instanceFieldsCache = fields.toArray(new IntFunction<Field[]>() {
                @Override
                public Field[] apply(int value) {
                    return new Field[value];
                }
            });
        }
        return instanceFieldsCache;
    }

    @Override
    public Field[] getStaticFields() {
        // TODO(peterssen): Cache static fields.
        if (staticFieldsCache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            staticFieldsCache = Arrays.stream(declaredFields).filter(new Predicate<Field>() {
                @Override
                public boolean test(Field fieldInfo) {
                    return fieldInfo.isStatic();
                }
            }).toArray(new IntFunction<Field[]>() {
                @Override
                public Field[] apply(int value) {
                    return new Field[value];
                }
            });
        }
        return staticFieldsCache;
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

// @Override
// public Method[] getDeclaredConstructors() {
// return Arrays.stream(declaredMethods).filter(new Predicate<Method>() {
// @Override
// public boolean test(Method m) {
// return Method.INIT.equals(m.getName());
// }
// }).toArray(new IntFunction<Method[]>() {
// @Override
// public Method[] apply(int value) {
// return new Method[value];
// }
// });
// }

    @Override
    public Method[] getDeclaredMethods() {
        return declaredMethods;
    }

    @Override
    public Field[] getDeclaredFields() {
        return declaredFields;
    }

    @Override
    public Klass getComponentType() {
        return null;
    }

    public EnclosingMethodAttribute getEnclosingMethod() {
        return enclosingMethod;
    }

    public InnerClassesAttribute getInnerClasses() {
        return innerClasses;
    }

// @Override
// public ObjectKlass getSupertype() {
// if (isInterface()) {
// return getContext().getMeta().OBJECT;
// }
// return getSuperclass();
// }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }
}
