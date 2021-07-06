/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObject.StaticObjectFactory;
import com.oracle.truffle.espresso.staticobject.ClassLoaderCache;
import com.oracle.truffle.espresso.staticobject.StaticShape;

final class LinkedKlassFieldLayout {
    final StaticShape<StaticObjectFactory> instanceShape;
    final StaticShape<StaticObjectFactory> staticShape;

    // instance fields declared in the corresponding LinkedKlass (includes hidden fields)
    @CompilationFinal(dimensions = 1) //
    final LinkedField[] instanceFields;
    // static fields declared in the corresponding LinkedKlass (no hidden fields)
    @CompilationFinal(dimensions = 1) //
    final LinkedField[] staticFields;

    final int fieldTableLength;

    LinkedKlassFieldLayout(ClassLoaderCache clc, ParserKlass parserKlass, LinkedKlass superKlass) {
        StaticShape.Builder instanceBuilder = StaticShape.newBuilder(clc);
        StaticShape.Builder staticBuilder = StaticShape.newBuilder(clc);

        FieldCounter fieldCounter = new FieldCounter(parserKlass);
        int nextInstanceFieldIndex = 0;
        int nextStaticFieldIndex = 0;
        int nextInstanceFieldSlot = superKlass == null ? 0 : superKlass.getFieldTableLength();
        int nextStaticFieldSlot = 0;
        instanceFields = new LinkedField[fieldCounter.instanceFields];
        staticFields = new LinkedField[fieldCounter.staticFields];

        LinkedField.IdMode idMode = getIdMode(parserKlass);

        for (ParserField parserField : parserKlass.getFields()) {
            if (parserField.isStatic()) {
                LinkedField field = new LinkedField(parserField, nextStaticFieldSlot++, storeAsFinal(parserKlass, parserField), idMode);
                staticBuilder.property(field);
                staticFields[nextStaticFieldIndex++] = field;
            } else {
                LinkedField field = new LinkedField(parserField, nextInstanceFieldSlot++, storeAsFinal(parserKlass, parserField), idMode);
                instanceBuilder.property(field);
                instanceFields[nextInstanceFieldIndex++] = field;
            }
        }
        for (Symbol<Name> hiddenFieldName : fieldCounter.hiddenFieldNames) {
            ParserField hiddenParserField = new ParserField(ParserField.HIDDEN, hiddenFieldName, Type.java_lang_Object, null);
            LinkedField field = new LinkedField(hiddenParserField, nextInstanceFieldSlot++, storeAsFinal(parserKlass, hiddenParserField), idMode);
            instanceBuilder.property(field);
            instanceFields[nextInstanceFieldIndex++] = field;
        }
        if (superKlass == null) {
            instanceShape = instanceBuilder.build(StaticObject.class, StaticObjectFactory.class);
        } else {
            instanceShape = instanceBuilder.build(superKlass.getShape(false));
        }
        staticShape = staticBuilder.build(StaticObject.class, StaticObjectFactory.class);
        fieldTableLength = nextInstanceFieldSlot;
    }

    /**
     * Makes sure that the field IDs passed to the shape builder are all unique.
     */
    private static LinkedField.IdMode getIdMode(ParserKlass parserKlass) {
        ParserField[] parserFields = parserKlass.getFields();

        boolean noDup = true;
        Set<String> present = new HashSet<>(parserFields.length);
        for (ParserField parserField : parserFields) {
            if (!present.add(parserField.getName().toString())) {
                noDup = false;
                break;
            }
        }
        if (noDup) {
            // All fields have unique names, we can use said names as ID.
            return LinkedField.IdMode.REGULAR;
        }
        present.clear();
        for (ParserField parserField : parserFields) {
            String id = LinkedField.idFromNameAndType(parserField.getName(), parserField.getType());
            if (!present.add(id)) {
                // Concatenating name and type does not result in no duplicates. Give up giving
                // meaningful information as an ID, and fall back to field{n}
                return LinkedField.IdMode.OBFUSCATED;
            }
        }
        // All fields have unique {name, type} pairs, use the concatenation of both for the ID.
        return LinkedField.IdMode.WITH_TYPE;
    }

    private static boolean storeAsFinal(ParserKlass klass, ParserField field) {
        Symbol<Type> klassType = klass.getType();
        Symbol<Name> fieldName = field.getName();
        // The Graal compiler folds final fields, with some exceptions (e.g., `System.out`). If the
        // value of one of these fields is stored as final, the corresponding set method has no
        // effect on already compiled methods that folded the read of the field value during
        // compilation.
        if (klassType == Type.java_lang_System && (fieldName == Name.in || fieldName == Name.out || fieldName == Name.err)) {
            return false;
        }
        return field.isFinal();
    }

    private static final class FieldCounter {
        final Symbol<Name>[] hiddenFieldNames;

        // Includes hidden fields
        final int instanceFields;
        final int staticFields;

        FieldCounter(ParserKlass parserKlass) {
            int iFields = 0;
            int sFields = 0;
            for (ParserField f : parserKlass.getFields()) {
                if (f.isStatic()) {
                    sFields++;
                } else {
                    iFields++;
                }
            }
            // All hidden fields are of Object kind
            hiddenFieldNames = getHiddenFieldNames(parserKlass);
            instanceFields = iFields + hiddenFieldNames.length;
            staticFields = sFields;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Symbol<Name>[] getHiddenFieldNames(ParserKlass parserKlass) {
            Symbol<Type> type = parserKlass.getType();
            if (type == Type.java_lang_invoke_MemberName) {
                return new Symbol[]{
                                Name.HIDDEN_VMTARGET,
                                Name.HIDDEN_VMINDEX
                };
            } else if (type == Type.java_lang_reflect_Method) {
                return new Symbol[]{
                                Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS,
                                Name.HIDDEN_METHOD_KEY
                };
            } else if (type == Type.java_lang_reflect_Constructor) {
                return new Symbol[]{
                                Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS,
                                Name.HIDDEN_CONSTRUCTOR_KEY
                };
            } else if (type == Type.java_lang_reflect_Field) {
                return new Symbol[]{
                                Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS,
                                Name.HIDDEN_FIELD_KEY
                };
            } else if (type == Type.java_lang_ref_Reference) {
                return new Symbol[]{
                                // All references (including strong) get an extra hidden field, this
                                // simplifies the code for weak/soft/phantom/final references.
                                Name.HIDDEN_HOST_REFERENCE
                };
            } else if (type == Type.java_lang_Throwable) {
                return new Symbol[]{
                                Name.HIDDEN_FRAMES,
                                Name.HIDDEN_EXCEPTION_WRAPPER
                };
            } else if (type == Type.java_lang_Thread) {
                return new Symbol[]{
                                Name.HIDDEN_HOST_THREAD,
                                Name.HIDDEN_IS_ALIVE,
                                Name.HIDDEN_INTERRUPTED,
                                Name.HIDDEN_DEATH,
                                Name.HIDDEN_DEATH_THROWABLE,
                                Name.HIDDEN_SUSPEND_LOCK,

                                // Only used for j.l.management bookkeeping.
                                Name.HIDDEN_THREAD_BLOCKED_OBJECT,
                                Name.HIDDEN_THREAD_BLOCKED_COUNT,
                                Name.HIDDEN_THREAD_WAITED_COUNT
                };
            } else if (type == Type.java_lang_Class) {
                return new Symbol[]{
                                Name.HIDDEN_SIGNERS,
                                Name.HIDDEN_MIRROR_KLASS,
                                Name.HIDDEN_PROTECTION_DOMAIN
                };
            } else if (type == Type.java_lang_ClassLoader) {
                return new Symbol[]{
                                Name.HIDDEN_CLASS_LOADER_REGISTRY
                };
            } else if (type == Type.java_lang_Module) {
                return new Symbol[]{
                                Name.HIDDEN_MODULE_ENTRY
                };
            }
            return Symbol.EMPTY_ARRAY;
        }
    }
}
