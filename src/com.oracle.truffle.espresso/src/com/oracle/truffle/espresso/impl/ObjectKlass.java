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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.CodeAttribute;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.substitutions.Host;

/**
 * Resolved non-primitive, non-array types in Espresso.
 */
public final class ObjectKlass extends Klass {

    public static final ObjectKlass[] EMPTY_ARRAY = new ObjectKlass[0];

    private final EnclosingMethodAttribute enclosingMethod;

    private final RuntimeConstantPool pool;

    private final LinkedKlass linkedKlass;

    @CompilationFinal //
    private StaticObject statics;

    @CompilationFinal(dimensions = 1) //
    private Field[] declaredFields;

    @CompilationFinal(dimensions = 1) //
    private Method[] declaredMethods;

    private final InnerClassesAttribute innerClasses;

    private final Attribute runtimeVisibleAnnotations;

    private int initState = LINKED;

    public static final int LOADED = 0;
    public static final int LINKED = 1;
    public static final int PREPARED = 2;
    public static final int INITIALIZED = 3;

    private final Attribute getAttribute(ByteString<Name> name) {
        return linkedKlass.getAttribute(name);
    }

    public ObjectKlass(EspressoContext context, LinkedKlass linkedKlass, ObjectKlass superKlass, ObjectKlass[] superInterfaces,
                    StaticObject classLoader) {
        super(context, linkedKlass.getType(), superKlass, superInterfaces);

        this.linkedKlass = linkedKlass;

        this.enclosingMethod = (EnclosingMethodAttribute) getAttribute(EnclosingMethodAttribute.NAME);
        this.innerClasses = (InnerClassesAttribute) getAttribute(InnerClassesAttribute.NAME);

        // Move attribute name to better location.
        this.runtimeVisibleAnnotations = getAttribute(Name.RuntimeVisibleAnnotations);

        // TODO(peterssen): Make writable copy.
        this.pool = new RuntimeConstantPool(linkedKlass.getConstantPool(), classLoader);

        LinkedField[] linkedFields = linkedKlass.getLinkedFields();
        Field[] fields = new Field[linkedFields.length];
        for (int i = 0; i < fields.length; ++i) {
            fields[i] = new Field(linkedFields[i], this);
        }

        LinkedMethod[] linkedMethods = linkedKlass.getLinkedMethods();
        Method[] methods = new Method[linkedMethods.length];
        for (int i = 0; i < methods.length; ++i) {
            methods[i] = new Method(this, linkedMethods[i]);
        }

        this.declaredFields = fields;
        this.declaredMethods = methods;
    }

    @Override
    public StaticObject tryInitializeAndGetStatics() {
        initialize();
        return getStatics();
    }

    @Override
    public StaticObject getStatics() {
        if (statics == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            statics = new StaticObjectImpl(this, true);
        }
        return statics;
    }

    @Override
    public boolean isInstanceClass() {
        throw EspressoError.unimplemented();
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
            Method clinit = getClassInitializer();
            if (clinit != null) {
                clinit.getCallTarget().call();
            }
            assert isInitialized();
        }
    }

    @Override
    public Klass getElementalType() {
        return null;
    }
//
//    @Override
//    public Field[] getInstanceFields(boolean includeSuperclasses) {
//        return new Field[0];
//    }

//    @Override
//    public Field[] getStaticFields() {
//        return new Field[0];
//    }

// @Override
// public Method resolveMethod(Method method, Klass callerType) {
// return null;
// }

    @Override
    public @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return pool.getClassLoader();
    }

    @Override
    public RuntimeConstantPool getConstantPool() {
        return pool;
    }
//
// @Override
// public Field[] getInstanceFields(boolean includeSuperclasses) {
// if (!includeSuperclasses) {
// if (declaredInstanceFieldsCache == null) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// declaredInstanceFieldsCache = Arrays.stream(declaredFields).filter(new Predicate<Field>() {
// @Override
// public boolean test(Field f) {
// return !f.isStatic();
// }
// }).toArray(new IntFunction<Field[]>() {
// @Override
// public Field[] apply(int value) {
// return new Field[value];
// }
// });
// }
// return declaredInstanceFieldsCache;
// }
// if (instanceFieldsCache == null) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// Stream<Field> fields = Arrays.stream(declaredFields).filter(new Predicate<Field>() {
// @Override
// public boolean test(Field f) {
// return !f.isStatic();
// }
// });
// if (includeSuperclasses && getSuperclass() != null) {
// fields = Stream.concat(Arrays.stream(getSuperclass().getInstanceFields(includeSuperclasses)),
// fields);
// }
// instanceFieldsCache = fields.toArray(new IntFunction<Field[]>() {
// @Override
// public Field[] apply(int value) {
// return new Field[value];
// }
// });
// }
// return instanceFieldsCache;
// }
//
// @Override
// public Field[] getStaticFields() {
// // TODO(peterssen): Cache static fields.
// if (staticFieldsCache == null) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// staticFieldsCache = Arrays.stream(declaredFields).filter(new Predicate<Field>() {
// @Override
// public boolean test(Field fieldInfo) {
// return fieldInfo.isStatic();
// }
// }).toArray(new IntFunction<Field[]>() {
// @Override
// public Field[] apply(int value) {
// return new Field[value];
// }
// });
// }
// return staticFieldsCache;
// }

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
    public Method[] getDeclaredConstructors() {
        return new Method[0];
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

    public final LinkedKlass getLinkedKlass() {
        return linkedKlass;
    }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }
}
