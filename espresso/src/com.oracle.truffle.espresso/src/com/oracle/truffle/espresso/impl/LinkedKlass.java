/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;

import java.lang.reflect.Modifier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.StaticObject.StaticObjectFactory;

// Structural shareable klass (superklass in superinterfaces resolved and linked)
// contains shape, field locations.
// Klass shape, vtable and field locations can be computed at the structural level.
public final class LinkedKlass {

    public static final LinkedKlass[] EMPTY_ARRAY = new LinkedKlass[0];
    private final ParserKlass parserKlass;

    // Linked structural references.
    private final LinkedKlass superKlass;

    @CompilationFinal(dimensions = 1) //
    private final LinkedKlass[] interfaces;

    @CompilationFinal(dimensions = 1) //
    private final LinkedMethod[] methods;

    private final boolean hasFinalizer;

    private final StaticShape<StaticObjectFactory> instanceShape;
    private final StaticShape<StaticObjectFactory> staticShape;

    // instance fields declared in the corresponding LinkedKlass (includes hidden fields)
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final LinkedField[] instanceFields;
    // static fields declared in the corresponding LinkedKlass (no hidden fields)
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final LinkedField[] staticFields;

    final int fieldTableLength;

    private LinkedKlass(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces, StaticShape<StaticObjectFactory> instanceShape,
                    StaticShape<StaticObjectFactory> staticShape, LinkedField[] instanceFields, LinkedField[] staticFields, int fieldTableLength) {
        this.parserKlass = parserKlass;
        this.superKlass = superKlass;
        this.interfaces = interfaces;
        this.instanceShape = instanceShape;
        this.staticShape = staticShape;
        this.instanceFields = instanceFields;
        this.staticFields = staticFields;
        this.fieldTableLength = fieldTableLength;

        // Streams are forbidden in Espresso.
        // assert Arrays.stream(interfaces).allMatch(i -> Modifier.isInterface(i.getFlags()));
        assert superKlass == null || !Modifier.isInterface(superKlass.getFlags());

        // Super interfaces are not checked for finalizers; a default .finalize method will be
        // resolved to Object.finalize, making the finalizer not observable.
        this.hasFinalizer = ((parserKlass.getFlags() & ACC_FINALIZER) != 0) || (superKlass != null && (superKlass.getFlags() & ACC_FINALIZER) != 0);
        assert !this.hasFinalizer || !Type.java_lang_Object.equals(parserKlass.getType()) : "java.lang.Object cannot be marked as finalizable";

        final int methodCount = parserKlass.getMethods().length;
        LinkedMethod[] linkedMethods = new LinkedMethod[methodCount];

        for (int i = 0; i < methodCount; ++i) {
            ParserMethod parserMethod = parserKlass.getMethods()[i];
            // TODO(peterssen): Methods with custom constant pool should spawned here, but not
            // supported.
            linkedMethods[i] = new LinkedMethod(parserMethod);
        }
        this.methods = linkedMethods;
    }

    public static LinkedKlass create(ContextDescription description, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
        LinkedKlassFieldLayout fieldLayout = new LinkedKlassFieldLayout(description, parserKlass, superKlass);
        return new LinkedKlass(
                        parserKlass,
                        superKlass,
                        interfaces,
                        fieldLayout.instanceShape,
                        fieldLayout.staticShape,
                        fieldLayout.instanceFields,
                        fieldLayout.staticFields,
                        fieldLayout.fieldTableLength);
    }

    public static LinkedKlass redefine(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces, LinkedKlass redefinedKlass) {
        // On class redefinition we need to re-use the old shape.
        // If we don't do it, shape checks on field accesses fail because `Field` instances in
        // `ObjectKlass.fieldTable` hold references to the old shape, which does not match the shape
        // of the new object instances.
        // We work around this by means of an extension mechanism where all shapes contain
        // one extra element
        return new LinkedKlass(
                        parserKlass,
                        superKlass,
                        interfaces,
                        redefinedKlass.instanceShape,
                        redefinedKlass.staticShape,
                        redefinedKlass.instanceFields,
                        redefinedKlass.staticFields,
                        redefinedKlass.fieldTableLength);
    }

    int getFlags() {
        int flags = parserKlass.getFlags();
        if (hasFinalizer) {
            flags |= ACC_FINALIZER;
        }
        return flags;
    }

    ConstantPool getConstantPool() {
        return parserKlass.getConstantPool();
    }

    Attribute getAttribute(Symbol<Name> name) {
        return parserKlass.getAttribute(name);
    }

    Symbol<Type> getType() {
        return parserKlass.getType();
    }

    Symbol<Name> getName() {
        return parserKlass.getName();
    }

    public ParserKlass getParserKlass() {
        return parserKlass;
    }

    LinkedKlass getSuperKlass() {
        return superKlass;
    }

    LinkedKlass[] getInterfaces() {
        return interfaces;
    }

    int getMajorVersion() {
        return getConstantPool().getMajorVersion();
    }

    int getMinorVersion() {
        return getConstantPool().getMinorVersion();
    }

    LinkedMethod[] getLinkedMethods() {
        return methods;
    }

    LinkedField[] getInstanceFields() {
        return instanceFields;
    }

    LinkedField[] getStaticFields() {
        return staticFields;
    }

    int getFieldTableLength() {
        return fieldTableLength;
    }

    public StaticShape<StaticObjectFactory> getShape(boolean isStatic) {
        return isStatic ? staticShape : instanceShape;
    }

    @Override
    public String toString() {
        return "LinkedKlass<" + getType() + ">";
    }
}
