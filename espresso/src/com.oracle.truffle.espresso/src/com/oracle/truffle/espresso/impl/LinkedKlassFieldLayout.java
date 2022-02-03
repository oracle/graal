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
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.api.staticobject.StaticShape.Builder;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.JavaVersion.VersionRange;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObject.StaticObjectFactory;

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

    LinkedKlassFieldLayout(ContextDescription description, ParserKlass parserKlass, LinkedKlass superKlass) {
        StaticShape.Builder instanceBuilder = StaticShape.newBuilder(description.language);
        StaticShape.Builder staticBuilder = StaticShape.newBuilder(description.language);

        FieldCounter fieldCounter = new FieldCounter(parserKlass, description.javaVersion);
        int nextInstanceFieldIndex = 0;
        int nextStaticFieldIndex = 0;
        int nextInstanceFieldSlot = superKlass == null ? 0 : superKlass.getFieldTableLength();
        int nextStaticFieldSlot = 0;

        staticFields = new LinkedField[fieldCounter.staticFields];
        instanceFields = new LinkedField[fieldCounter.instanceFields];

        LinkedField.IdMode idMode = getIdMode(parserKlass);

        for (ParserField parserField : parserKlass.getFields()) {
            if (parserField.isStatic()) {
                createAndRegisterLinkedField(parserKlass, parserField, nextStaticFieldSlot++, nextStaticFieldIndex++, idMode, staticBuilder, staticFields);
            } else {
                createAndRegisterLinkedField(parserKlass, parserField, nextInstanceFieldSlot++, nextInstanceFieldIndex++, idMode, instanceBuilder, instanceFields);
            }
        }

        for (HiddenField hiddenField : fieldCounter.hiddenFieldNames) {
            if (hiddenField.versionRange.contains(description.javaVersion)) {
                ParserField hiddenParserField = new ParserField(ParserField.HIDDEN, hiddenField.name, hiddenField.type, null);
                createAndRegisterLinkedField(parserKlass, hiddenParserField, nextInstanceFieldSlot++, nextInstanceFieldIndex++, idMode, instanceBuilder, instanceFields);
            }
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
    static LinkedField.IdMode getIdMode(ParserKlass parserKlass) {
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

    private static void createAndRegisterLinkedField(ParserKlass parserKlass, ParserField parserField, int slot, int index, LinkedField.IdMode idMode, Builder builder, LinkedField[] linkedFields) {
        LinkedField field = new LinkedField(parserField, slot, idMode);
        builder.property(field, parserField.getPropertyType(), storeAsFinal(parserKlass, parserField));
        linkedFields[index] = field;
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
        final HiddenField[] hiddenFieldNames;

        // Includes hidden fields
        final int instanceFields;
        final int staticFields;

        FieldCounter(ParserKlass parserKlass, JavaVersion version) {
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
            hiddenFieldNames = HiddenField.getHiddenFields(parserKlass.getType(), version);
            instanceFields = iFields + hiddenFieldNames.length;
            staticFields = sFields;
        }
    }

    private static class HiddenField {

        private final Symbol<Name> name;
        private final Symbol<Type> type;
        private final VersionRange versionRange;

        HiddenField(Symbol<Name> name) {
            this(name, Type.java_lang_Object, VersionRange.ALL);
        }

        HiddenField(Symbol<Name> name, Symbol<Type> type, VersionRange versionRange) {
            this.name = name;
            this.type = type;
            this.versionRange = versionRange;
        }

        private boolean appliesTo(JavaVersion version) {
            return versionRange.contains(version);
        }

        static final HiddenField[] EMPTY = new HiddenField[0];

        static HiddenField[] getHiddenFields(Symbol<Type> holder, JavaVersion version) {
            return applyFilter(getHiddenFieldsFull(holder), version);
        }

        private static HiddenField[] applyFilter(HiddenField[] hiddenFields, JavaVersion version) {
            int filtered = 0;
            for (HiddenField f : hiddenFields) {
                if (!f.appliesTo(version)) {
                    filtered++;
                }
            }
            if (filtered == 0) {
                return hiddenFields;
            }
            HiddenField[] result = new HiddenField[hiddenFields.length - filtered];
            int pos = 0;
            for (int i = 0; i < hiddenFields.length; i++) {
                HiddenField f = hiddenFields[i];
                if (f.appliesTo(version)) {
                    result[pos++] = f;
                }
            }
            return result;
        }

        private static HiddenField[] getHiddenFieldsFull(Symbol<Type> holder) {
            if (holder == Type.java_lang_invoke_MemberName) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_VMTARGET),
                                new HiddenField(Name.HIDDEN_VMINDEX)
                };
            }
            if (holder == Type.java_lang_reflect_Method) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS),
                                new HiddenField(Name.HIDDEN_METHOD_KEY)
                };
            }
            if (holder == Type.java_lang_reflect_Constructor) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS),
                                new HiddenField(Name.HIDDEN_CONSTRUCTOR_KEY)
                };
            }
            if (holder == Type.java_lang_reflect_Field) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS),
                                new HiddenField(Name.HIDDEN_FIELD_KEY)
                };
            }
            if (holder == Type.java_lang_ref_Reference) {
                return new HiddenField[]{
                                // All references (including strong) get an extra hidden field, this
                                // simplifies the code for weak/soft/phantom/final references.
                                new HiddenField(Name.HIDDEN_HOST_REFERENCE)
                };
            }
            if (holder == Type.java_lang_Throwable) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_FRAMES),
                                new HiddenField(Name.HIDDEN_EXCEPTION_WRAPPER)
                };
            }
            if (holder == Type.java_lang_Thread) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_INTERRUPTED, Type._boolean, VersionRange.lower(13)),
                                new HiddenField(Name.HIDDEN_HOST_THREAD),
                                new HiddenField(Name.HIDDEN_DEPRECATION_SUPPORT),

                                // Only used for j.l.management bookkeeping.
                                new HiddenField(Name.HIDDEN_THREAD_BLOCKED_OBJECT),
                                new HiddenField(Name.HIDDEN_THREAD_BLOCKED_COUNT),
                                new HiddenField(Name.HIDDEN_THREAD_WAITED_COUNT)
                };
            }
            if (holder == Type.java_lang_Class) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_SIGNERS),
                                new HiddenField(Name.HIDDEN_MIRROR_KLASS),
                                new HiddenField(Name.HIDDEN_PROTECTION_DOMAIN)
                };
            }
            if (holder == Type.java_lang_ClassLoader) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_CLASS_LOADER_REGISTRY)
                };
            }
            if (holder == Type.java_lang_Module) {
                return new HiddenField[]{
                                new HiddenField(Name.HIDDEN_MODULE_ENTRY)
                };
            }

            return EMPTY;
        }
    }
}
