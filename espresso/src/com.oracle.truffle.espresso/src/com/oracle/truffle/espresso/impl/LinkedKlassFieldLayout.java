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

import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_HIDDEN;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_VOLATILE;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.api.staticobject.StaticShape.Builder;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange;
import com.oracle.truffle.espresso.classfile.ParserField;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject.StaticObjectFactory;

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

    LinkedKlassFieldLayout(EspressoLanguage language, ParserKlass parserKlass, LinkedKlass superKlass) {
        StaticShape.Builder instanceBuilder = StaticShape.newBuilder(language);
        StaticShape.Builder staticBuilder = StaticShape.newBuilder(language);

        FieldsInfo fieldsInfo = FieldsInfo.create(parserKlass, language);
        int nextInstanceFieldIndex = 0;
        int nextStaticFieldIndex = 0;
        int nextInstanceFieldSlot = superKlass == null ? 0 : superKlass.getFieldTableLength();
        int nextStaticFieldSlot = 0;

        staticFields = new LinkedField[fieldsInfo.staticFields];
        instanceFields = new LinkedField[fieldsInfo.instanceFields];

        LinkedField.IdMode idMode = getIdMode(parserKlass);

        for (ParserField parserField : parserKlass.getFields()) {
            if (parserField.isStatic()) {
                createAndRegisterLinkedField(parserKlass, parserField, nextStaticFieldSlot++, nextStaticFieldIndex++, idMode, staticBuilder, staticFields);
            } else {
                createAndRegisterLinkedField(parserKlass, parserField, nextInstanceFieldSlot++, nextInstanceFieldIndex++, idMode, instanceBuilder, instanceFields);
            }
        }

        for (HiddenField hiddenField : fieldsInfo.hiddenFields) {
            if (hiddenField.predicate.test(language)) {
                ParserField hiddenParserField = new ParserField(ACC_HIDDEN | hiddenField.additionalFlags, hiddenField.name, hiddenField.type, Attribute.EMPTY_ARRAY);
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
        builder.property(field, LinkedField.getPropertyType(parserField), storeAsFinal(parserKlass, parserField));
        linkedFields[index] = field;
    }

    private static boolean storeAsFinal(ParserKlass klass, ParserField field) {
        Symbol<Type> klassType = klass.getType();
        Symbol<Name> fieldName = field.getName();
        // The Graal compiler folds final fields, with some exceptions (e.g., `System.out`). If the
        // value of one of these fields is stored as final, the corresponding set method has no
        // effect on already compiled methods that folded the read of the field value during
        // compilation.
        if (klassType == Types.java_lang_System && (fieldName == Names.in || fieldName == Names.out || fieldName == Names.err)) {
            return false;
        }
        // We're updating the class modifiers during redefinition if they change, so don't allow the
        // compiler to fold the reads.
        if (klassType == Types.java_lang_Class && fieldName == Names.modifiers) {
            return false;
        }
        return field.isFinal();
    }

    /**
     * Describes fields defined by a class file plus extra fields to be injected.
     *
     * @param hiddenFields the fields to be injected
     * @param instanceFields number of instance fields (including hidden fields)
     * @param staticFields number of static fields
     */
    private record FieldsInfo(HiddenField[] hiddenFields, int instanceFields, int staticFields) {

        static FieldsInfo create(ParserKlass parserKlass, EspressoLanguage language) {
            int iFields = 0;
            int sFields = 0;
            for (ParserField f : parserKlass.getFields()) {
                if (f.isStatic()) {
                    sFields++;
                } else {
                    iFields++;
                }
            }
            HiddenField[] hidden = HiddenField.getHiddenFields(parserKlass.getType(), language);
            return new FieldsInfo(hidden, iFields + hidden.length, sFields);
        }
    }

    /// Represents a field of a class that is not defined in a class file.
    /// The fixed set of available injected fields are defined in the [#REGISTRY]. The actual set
    /// of injected fields is determined by filtering the registry with an [EspressoLanguage] based
    /// [#predicate] (e.g. [`Thread.interrupted`][Names#HIDDEN_interrupted] is only
    /// relevant for Java <= 13).
    ///
    /// @param predicate determines if the field exists for a given [EspressoLanguage] instance.
    record HiddenField(Symbol<Name> name,
                    Symbol<Type> type,
                    Predicate<EspressoLanguage> predicate,
                    int additionalFlags) {

        private static final Predicate<EspressoLanguage> NO_PREDICATE = l -> true;
        private static final int NO_ADDITIONAL_FLAGS = 0;
        private static final HiddenField[] EMPTY = new HiddenField[0];
        private static final Map<Symbol<Type>, HiddenField[]> REGISTRY = Map.ofEntries(
                        entry(Types.java_lang_Object,
                                        new HiddenField(Names.HIDDEN_systemHashCode, Types._int, EspressoLanguage::canSetCustomIdentityHashCode, ACC_VOLATILE)),
                        entry(Types.java_lang_invoke_MemberName,
                                        new HiddenField(Names.HIDDEN_vmTarget),
                                        new HiddenField(Names.HIDDEN_vmIndex)),
                        entry(Types.java_lang_invoke_ResolvedMethodName,
                                        new HiddenField(Names.HIDDEN_vmMethod, Types.java_lang_Object, VersionRange.VERSION_22_OR_HIGHER, NO_ADDITIONAL_FLAGS)),
                        entry(Types.java_lang_reflect_Method,
                                        new HiddenField(Names.HIDDEN_runtimeVisibleTypeAnnotations),
                                        new HiddenField(Names.HIDDEN_vmMethod)),
                        entry(Types.java_lang_reflect_Constructor,
                                        new HiddenField(Names.HIDDEN_runtimeVisibleTypeAnnotations),
                                        new HiddenField(Names.HIDDEN_vmMethod)),
                        entry(Types.java_lang_reflect_Field,
                                        new HiddenField(Names.HIDDEN_runtimeVisibleTypeAnnotations),
                                        new HiddenField(Names.HIDDEN_vmField)),
                        // All references (including strong) get an extra hidden field, this
                        // simplifies the code for weak/soft/phantom/final references.
                        entry(Types.java_lang_ref_Reference,
                                        new HiddenField(Names.HIDDEN_hostReference)),
                        entry(Types.java_lang_Throwable,
                                        new HiddenField(Names.HIDDEN_frames),
                                        new HiddenField(Names.HIDDEN_exceptionWrapper)),
                        entry(Types.java_lang_Thread,
                                        new HiddenField(Names.HIDDEN_interrupted, Types._boolean, VersionRange.lower(13), NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_interruptedEvent, Types.java_lang_Object, EspressoLanguage::needsInterruptedEvent, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_hostThread),
                                        new HiddenField(Names.HIDDEN_espressoManaged, Types._boolean, VersionRange.ALL, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_toNativeLock, Types.java_lang_Object, VersionRange.ALL, ACC_FINAL),
                                        new HiddenField(Names.HIDDEN_deprecationSupport),
                                        new HiddenField(Names.HIDDEN_unparkSignals, Types._int, VersionRange.ALL, ACC_VOLATILE),
                                        new HiddenField(Names.HIDDEN_parkLock, Types.java_lang_Object, VersionRange.ALL, ACC_FINAL),
                                        new HiddenField(Names.HIDDEN_scopedValueCache),

                                        // Only used for j.l.management bookkeeping.
                                        new HiddenField(Names.HIDDEN_pendingMonitor),
                                        new HiddenField(Names.HIDDEN_waitingMonitor),
                                        new HiddenField(Names.HIDDEN_blockedCount),
                                        new HiddenField(Names.HIDDEN_waitedCount),
                                        new HiddenField(Names.HIDDEN_depthFirstNumber)),
                        entry(Types.java_lang_Class,
                                        new HiddenField(Names.HIDDEN_signers),
                                        new HiddenField(Names.HIDDEN_klass, ACC_FINAL),
                                        new HiddenField(Names.HIDDEN_protectionDomain),
                                        new HiddenField(Names.HIDDEN_jvmciIndy, Types.java_lang_Object, EspressoLanguage::isJVMCIEnabled, NO_ADDITIONAL_FLAGS)),
                        entry(Types.java_lang_ClassLoader,
                                        new HiddenField(Names.HIDDEN_registry)),
                        entry(Types.java_lang_Module,
                                        new HiddenField(Names.HIDDEN_entry)),
                        entry(Types.java_util_regex_Pattern,
                                        new HiddenField(Names.HIDDEN_match, Types.java_lang_Object, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_fullMatch, Types.java_lang_Object, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_search, Types.java_lang_Object, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_status, Types._int, EspressoLanguage::useTRegex, ACC_VOLATILE)),
                        entry(Types.java_util_regex_Matcher,
                                        new HiddenField(Names.HIDDEN_tstring, Types.java_lang_Object, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_textSync, Types.java_lang_Object, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_patternSync, Types.java_lang_Object, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_oldLastBackup, Types._int, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_modCountBackup, Types._int, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_transparentBoundsBackup, Types._boolean, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_anchoringBoundsBackup, Types._boolean, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_fromBackup, Types._int, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_toBackup, Types._int, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_searchFromBackup, Types._int, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS),
                                        new HiddenField(Names.HIDDEN_matchingModeBackup, Types.java_lang_Object, EspressoLanguage::useTRegex, NO_ADDITIONAL_FLAGS)),
                        entry(Types.com_oracle_truffle_espresso_polyglot_TypeLiteral,
                                        new HiddenField(Names.HIDDEN_internalType)),
                        entry(Types.org_graalvm_continuations_ContinuationImpl,
                                        new HiddenField(Names.HIDDEN_frameRecord)),
                        entry(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType,
                                        new HiddenField(Names.HIDDEN_vmKlass)),
                        entry(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaField,
                                        new HiddenField(Names.HIDDEN_vmField)),
                        entry(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaMethod,
                                        new HiddenField(Names.HIDDEN_vmMethod)),
                        entry(Types.com_oracle_truffle_espresso_jvmci_meta_EspressoObjectConstant,
                                        new HiddenField(Names.HIDDEN_object)),
                        entry(Types.sun_nio_fs_TrufflePath,
                                        new HiddenField(Names.HIDDEN_file, Types.java_lang_Object, EspressoLanguage::useEspressoLibs, ACC_FINAL)),
                        entry(Types.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignDirectoryStream,
                                        new HiddenField(Names.HIDDEN_hostReference, Types.java_lang_Object, EspressoLanguage::useEspressoLibs, ACC_FINAL)),
                        entry(Types.sun_nio_fs_TruffleFilteredDirectoryStream$ForeignIterator,
                                        new HiddenField(Names.HIDDEN_hostReference, Types.java_lang_Object, EspressoLanguage::useEspressoLibs, ACC_FINAL)),
                        entry(Types.java_util_zip_CRC32,
                                        new HiddenField(Names.HIDDEN_value, Types.java_lang_Object, EspressoLanguage::useEspressoLibs, ACC_FINAL)));

        private static Map.Entry<Symbol<Type>, HiddenField[]> entry(Symbol<Type> declaringClass, HiddenField... fields) {
            return Map.entry(declaringClass, fields);
        }

        HiddenField(Symbol<Name> name) {
            this(name, Types.java_lang_Object, NO_PREDICATE, NO_ADDITIONAL_FLAGS);
        }

        HiddenField(Symbol<Name> name, int additionalFlags) {
            this(name, Types.java_lang_Object, NO_PREDICATE, additionalFlags);
        }

        HiddenField(Symbol<Name> name, Symbol<Type> type, VersionRange versionRange, int additionalFlags) {
            this(name, type, toPredicate(versionRange), additionalFlags);
        }

        private boolean appliesTo(EspressoLanguage language) {
            return predicate.test(language);
        }

        static HiddenField[] getHiddenFields(Symbol<Type> holder, EspressoLanguage language) {
            return applyFilter(getHiddenFieldsFull(holder), language);
        }

        private static HiddenField[] applyFilter(HiddenField[] hiddenFields, EspressoLanguage language) {
            int filtered = 0;
            for (HiddenField f : hiddenFields) {
                if (!f.appliesTo(language)) {
                    filtered++;
                }
            }
            if (filtered == 0) {
                return hiddenFields;
            }
            HiddenField[] result = new HiddenField[hiddenFields.length - filtered];
            int pos = 0;
            for (HiddenField f : hiddenFields) {
                if (f.appliesTo(language)) {
                    result[pos++] = f;
                }
            }
            return result;
        }

        private static HiddenField[] getHiddenFieldsFull(Symbol<Type> holder) {
            return REGISTRY.getOrDefault(holder, EMPTY);
        }

        private static Predicate<EspressoLanguage> toPredicate(VersionRange range) {
            return d -> range.contains(d.getJavaVersion());
        }
    }
}
