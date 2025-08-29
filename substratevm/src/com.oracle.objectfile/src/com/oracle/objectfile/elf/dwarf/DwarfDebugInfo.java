/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.objectfile.elf.dwarf;

import java.nio.ByteOrder;

import org.graalvm.collections.EconomicMap;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.DebugInfoBase;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.dwarf.constants.DwarfLanguage;

/**
 * A class that models the debug info in an organization that facilitates generation of the required
 * DWARF sections. It groups common data and behaviours for use by the various subclasses of class
 * DwarfSectionImpl that take responsibility for generating content for a specific section type.
 */
public class DwarfDebugInfo extends DebugInfoBase {

    public static final String HEAP_BEGIN_NAME = "__svm_heap_begin";

    /*
     * Define all the abbrev section codes we need for our DIEs.
     */
    public enum AbbrevCode {
        /* null marker which must come first as its ordinal has to equal zero */
        NULL,
        /* Level 0 DIEs. */
        CLASS_CONSTANT_UNIT,
        CLASS_UNIT_1,
        CLASS_UNIT_2,
        CLASS_UNIT_3,
        TYPE_UNIT,
        /* Level 1 DIEs. */
        PRIMITIVE_TYPE,
        VOID_TYPE,
        OBJECT_HEADER,
        CLASS_CONSTANT,
        NAMESPACE,
        CLASS_LAYOUT_TU,
        CLASS_LAYOUT_CU,
        CLASS_LAYOUT_ARRAY,
        CLASS_LAYOUT_OPAQUE,
        TYPE_POINTER_SIG,
        TYPE_POINTER,
        FOREIGN_TYPEDEF,
        FOREIGN_STRUCT,
        METHOD_LOCATION,
        STATIC_FIELD_LOCATION,
        ARRAY_LAYOUT,
        INTERFACE_LAYOUT,
        COMPRESSED_LAYOUT,
        /* Level 2 DIEs. */
        METHOD_DECLARATION,
        METHOD_DECLARATION_INLINE,
        METHOD_DECLARATION_STATIC,
        METHOD_DECLARATION_INLINE_STATIC,
        METHOD_DECLARATION_SKELETON,
        FIELD_DECLARATION_1,
        FIELD_DECLARATION_2,
        FIELD_DECLARATION_3,
        FIELD_DECLARATION_4,
        STRUCT_FIELD_SIG,
        STRUCT_FIELD,
        ARRAY_DATA_TYPE_1,
        ARRAY_DATA_TYPE_2,
        ARRAY_SUBRANGE,
        SUPER_REFERENCE,
        INTERFACE_IMPLEMENTOR,
        /* Level 2+K DIEs (where inline depth K >= 0) */
        INLINED_SUBROUTINE,
        ABSTRACT_INLINE_METHOD,
        /* Level 3 DIEs. */
        METHOD_PARAMETER_DECLARATION_1,
        METHOD_PARAMETER_DECLARATION_2,
        METHOD_PARAMETER_DECLARATION_3,
        METHOD_PARAMETER_DECLARATION_4,
        METHOD_PARAMETER_DECLARATION_5,
        METHOD_LOCAL_DECLARATION_1,
        METHOD_LOCAL_DECLARATION_2,
        METHOD_PARAMETER_LOCATION_1,
        METHOD_PARAMETER_LOCATION_2,
        METHOD_LOCAL_LOCATION_1,
        METHOD_LOCAL_LOCATION_2,
    }

    /**
     * This field defines the value used for the DW_AT_language attribute of compile units.
     *
     */
    public static final DwarfLanguage LANG_ENCODING = DwarfLanguage.DW_LANG_Java;

    /* Register constants for AArch64. */
    public static final byte rheapbase_aarch64 = (byte) 27;
    public static final byte rthread_aarch64 = (byte) 28;
    /* Register constants for x86. */
    public static final byte rheapbase_x86 = (byte) 14;
    public static final byte rthread_x86 = (byte) 15;

    /* Full byte/word values. */
    private final DwarfStrSectionImpl dwarfStrSection;
    private final DwarfLineStrSectionImpl dwarfLineStrSection;
    private final DwarfAbbrevSectionImpl dwarfAbbrevSection;
    private final DwarfInfoSectionImpl dwarfInfoSection;
    private final DwarfLocSectionImpl dwarfLocSection;
    private final DwarfARangesSectionImpl dwarfARangesSection;
    private final DwarfRangesSectionImpl dwarfRangesSection;
    private final DwarfLineSectionImpl dwarfLineSection;
    private final DwarfFrameSectionImpl dwarfFameSection;
    public final ELFMachine elfMachine;
    /**
     * Register used to hold the heap base.
     */
    private final byte heapbaseRegister;
    /**
     * Register used to hold the current thread.
     */
    private final byte threadRegister;

    /**
     * A collection of properties associated with each generated type record indexed by type name.
     * n.b. this collection includes entries for the structure types used to define the object and
     * array headers which do not have an associated TypeEntry.
     */
    private final EconomicMap<TypeEntry, DwarfClassProperties> classPropertiesIndex = EconomicMap.create();

    /**
     * A collection of method properties associated with each generated method record.
     */
    private final EconomicMap<MethodEntry, DwarfMethodProperties> methodPropertiesIndex = EconomicMap.create();

    /**
     * A collection of field properties associated with generated field record.
     */
    private final EconomicMap<FieldEntry, DwarfFieldProperties> fieldPropertiesIndex = EconomicMap.create();

    /**
     * A collection of local variable properties associated with an inlined subrange.
     */
    private final EconomicMap<Range, DwarfRangeProperties> rangePropertiesIndex = EconomicMap.create();

    @SuppressWarnings("this-escape")
    public DwarfDebugInfo(ELFMachine elfMachine, ByteOrder byteOrder) {
        super(byteOrder);
        this.elfMachine = elfMachine;
        dwarfStrSection = new DwarfStrSectionImpl(this);
        dwarfLineStrSection = new DwarfLineStrSectionImpl(this);
        dwarfAbbrevSection = new DwarfAbbrevSectionImpl(this);
        dwarfInfoSection = new DwarfInfoSectionImpl(this);
        dwarfLocSection = new DwarfLocSectionImpl(this);
        dwarfARangesSection = new DwarfARangesSectionImpl(this);
        dwarfRangesSection = new DwarfRangesSectionImpl(this);
        dwarfLineSection = new DwarfLineSectionImpl(this);

        if (elfMachine == ELFMachine.AArch64) {
            dwarfFameSection = new DwarfFrameSectionImplAArch64(this);
            this.heapbaseRegister = rheapbase_aarch64;
            this.threadRegister = rthread_aarch64;
        } else {
            dwarfFameSection = new DwarfFrameSectionImplX86_64(this);
            this.heapbaseRegister = rheapbase_x86;
            this.threadRegister = rthread_x86;
        }
    }

    public DwarfStrSectionImpl getStrSectionImpl() {
        return dwarfStrSection;
    }

    public DwarfLineStrSectionImpl getLineStrSectionImpl() {
        return dwarfLineStrSection;
    }

    public DwarfAbbrevSectionImpl getAbbrevSectionImpl() {
        return dwarfAbbrevSection;
    }

    public DwarfFrameSectionImpl getFrameSectionImpl() {
        return dwarfFameSection;
    }

    public DwarfInfoSectionImpl getInfoSectionImpl() {
        return dwarfInfoSection;
    }

    public DwarfLocSectionImpl getLocSectionImpl() {
        return dwarfLocSection;
    }

    public DwarfARangesSectionImpl getARangesSectionImpl() {
        return dwarfARangesSection;
    }

    public DwarfRangesSectionImpl getRangesSectionImpl() {
        return dwarfRangesSection;
    }

    public DwarfLineSectionImpl getLineSectionImpl() {
        return dwarfLineSection;
    }

    public byte getHeapbaseRegister() {
        return heapbaseRegister;
    }

    public byte getThreadRegister() {
        return threadRegister;
    }

    /**
     * A class used to associate extra properties with an instance class type.
     */

    static class DwarfClassProperties {
        /**
         * Index of the class entry's compile unit in the debug_info section.
         */
        private int cuIndex;
        /**
         * Index of the class entry's code ranges data in the debug_rnglists section.
         */
        private int codeRangesIndex;
        /**
         * Index of the class entry's code location data in the debug_loclists section.
         */
        private int locationListIndex;
        /**
         * Index of the class entry's line data in the debug_line section.
         */
        private int lineIndex;
        /**
         * Size of the class entry's prologue in the debug_line section.
         */
        private int linePrologueSize;

        DwarfClassProperties() {
            this.cuIndex = -1;
            this.codeRangesIndex = -1;
            this.locationListIndex = 0;
            this.lineIndex = -1;
            this.linePrologueSize = -1;
        }
    }

    /**
     * A class used to associate properties with a specific method.
     */
    static class DwarfMethodProperties {
        /**
         * The index in the info section at which the method's declaration resides.
         */
        private int methodDeclarationIndex;

        /**
         * Per class properties of a method. This contains properties for the top level compiled
         * method and abstract inline methods.
         */
        private EconomicMap<ClassEntry, DwarfMethodPerClassProperties> perClassPropertiesMap;

        DwarfMethodProperties() {
            methodDeclarationIndex = -1;
            // We might not need it if there are no local entries and the method is not inlined
            perClassPropertiesMap = null;
        }

        public DwarfMethodPerClassProperties lookupPerClassProperties(ClassEntry classEntry) {
            if (perClassPropertiesMap == null) {
                perClassPropertiesMap = EconomicMap.create();
            }

            DwarfMethodPerClassProperties methodPerClassProperties = perClassPropertiesMap.get(classEntry);
            if (methodPerClassProperties == null) {
                methodPerClassProperties = new DwarfMethodPerClassProperties();
                perClassPropertiesMap.put(classEntry, methodPerClassProperties);
            }
            return methodPerClassProperties;
        }
    }

    static class DwarfMethodPerClassProperties {
        /**
         * Per class map that identifies the info declarations for a top level method declaration or
         * an abstract inline method declaration.
         */
        private final EconomicMap<LocalEntry, Integer> localEntryMap;

        /**
         * Per class index that identifies the info declaration for an abstract inline method.
         */
        private int abstractInlineMethodIndex;

        DwarfMethodPerClassProperties() {
            localEntryMap = EconomicMap.create();
            abstractInlineMethodIndex = -1;
        }
    }

    static class DwarfFieldProperties {
        /**
         * Offset of the field declaration for this field.
         */
        private int fieldDeclarationIndex;

        DwarfFieldProperties() {
            fieldDeclarationIndex = -1;
        }
    }

    static class DwarfRangeProperties {
        /**
         * Per range map that identifies the location declarations of a local entry in the loc
         * section.
         */
        private final EconomicMap<LocalEntry, Integer> localEntryMap;

        DwarfRangeProperties() {
            localEntryMap = EconomicMap.create();
        }
    }

    private DwarfClassProperties addClassProperties(StructureTypeEntry entry) {
        assert classPropertiesIndex.get(entry) == null;
        DwarfClassProperties classProperties = new DwarfClassProperties();
        classPropertiesIndex.put(entry, classProperties);
        return classProperties;
    }

    private DwarfMethodProperties addMethodProperties(MethodEntry methodEntry) {
        assert methodPropertiesIndex.get(methodEntry) == null;
        DwarfMethodProperties methodProperties = new DwarfMethodProperties();
        methodPropertiesIndex.put(methodEntry, methodProperties);
        return methodProperties;
    }

    private DwarfFieldProperties addFieldProperties(FieldEntry fieldEntry) {
        assert fieldPropertiesIndex.get(fieldEntry) == null;
        DwarfFieldProperties fieldProperties = new DwarfFieldProperties();
        fieldPropertiesIndex.put(fieldEntry, fieldProperties);
        return fieldProperties;
    }

    private DwarfRangeProperties addRangeProperties(Range range) {
        assert rangePropertiesIndex.get(range) == null;
        DwarfRangeProperties rangeProperties = new DwarfRangeProperties();
        rangePropertiesIndex.put(range, rangeProperties);
        return rangeProperties;
    }

    private DwarfClassProperties lookupClassProperties(StructureTypeEntry entry) {
        DwarfClassProperties classProperties = classPropertiesIndex.get(entry);
        if (classProperties == null) {
            classProperties = addClassProperties(entry);
        }
        return classProperties;
    }

    private DwarfMethodProperties lookupMethodProperties(MethodEntry methodEntry) {
        DwarfMethodProperties methodProperties = methodPropertiesIndex.get(methodEntry);
        if (methodProperties == null) {
            methodProperties = addMethodProperties(methodEntry);
        }
        return methodProperties;
    }

    private DwarfFieldProperties lookupFieldProperties(FieldEntry fieldEntry) {
        DwarfFieldProperties fieldProperties = fieldPropertiesIndex.get(fieldEntry);
        if (fieldProperties == null) {
            fieldProperties = addFieldProperties(fieldEntry);
        }
        return fieldProperties;
    }

    private DwarfRangeProperties lookupRangeProperties(Range range) {
        DwarfRangeProperties rangeProperties = rangePropertiesIndex.get(range);
        if (rangeProperties == null) {
            rangeProperties = addRangeProperties(range);
        }
        return rangeProperties;
    }

    public void setCUIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.cuIndex == -1 || classProperties.cuIndex == idx;
        classProperties.cuIndex = idx;
    }

    public int getCUIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.cuIndex >= 0;
        return classProperties.cuIndex;
    }

    public void setCodeRangesIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.codeRangesIndex == -1 || classProperties.codeRangesIndex == idx;
        classProperties.codeRangesIndex = idx;
    }

    public int getCodeRangesIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.codeRangesIndex >= 0;
        return classProperties.codeRangesIndex;
    }

    public void setLocationListIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.locationListIndex == 0 || classProperties.locationListIndex == idx;
        classProperties.locationListIndex = idx;
    }

    public int getLocationListIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.locationListIndex >= 0;
        return classProperties.locationListIndex;
    }

    public void setLineIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.lineIndex == -1 || classProperties.lineIndex == idx;
        classProperties.lineIndex = idx;
    }

    public int getLineIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.lineIndex >= 0;
        return classProperties.lineIndex;
    }

    public void setLinePrologueSize(ClassEntry classEntry, int size) {
        assert size >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.linePrologueSize == -1 || classProperties.linePrologueSize == size;
        classProperties.linePrologueSize = size;
    }

    public int getLinePrologueSize(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.linePrologueSize >= 0;
        return classProperties.linePrologueSize;
    }

    public void setFieldDeclarationIndex(FieldEntry fieldEntry, int pos) {
        DwarfFieldProperties fieldProperties = lookupFieldProperties(fieldEntry);
        assert fieldProperties.fieldDeclarationIndex == -1 || fieldProperties.fieldDeclarationIndex == pos;
        fieldProperties.fieldDeclarationIndex = pos;
    }

    public int getFieldDeclarationIndex(FieldEntry fieldEntry) {
        DwarfFieldProperties fieldProperties = lookupFieldProperties(fieldEntry);
        return fieldProperties.fieldDeclarationIndex;
    }

    public void setMethodDeclarationIndex(MethodEntry methodEntry, int pos) {
        DwarfMethodProperties methodProperties = lookupMethodProperties(methodEntry);
        assert methodProperties.methodDeclarationIndex == -1 || methodProperties.methodDeclarationIndex == pos : "bad declaration index";
        methodProperties.methodDeclarationIndex = pos;
    }

    public int getMethodDeclarationIndex(MethodEntry methodEntry) {
        DwarfMethodProperties methodProperties = lookupMethodProperties(methodEntry);
        assert methodProperties.methodDeclarationIndex >= 0 : "unset declaration index";
        return methodProperties.methodDeclarationIndex;
    }

    public void setAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry, int pos) {
        DwarfMethodPerClassProperties methodPerClassProperties = lookupMethodProperties(methodEntry).lookupPerClassProperties(classEntry);
        assert methodPerClassProperties.abstractInlineMethodIndex == -1 || methodPerClassProperties.abstractInlineMethodIndex == pos;
        methodPerClassProperties.abstractInlineMethodIndex = pos;
    }

    public int getAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry) {
        DwarfMethodPerClassProperties methodPerClassProperties = lookupMethodProperties(methodEntry).lookupPerClassProperties(classEntry);
        assert methodPerClassProperties.abstractInlineMethodIndex >= -1;
        return methodPerClassProperties.abstractInlineMethodIndex;
    }

    public void setMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, LocalEntry localEntry, int index) {
        DwarfMethodPerClassProperties methodPerClassProperties = lookupMethodProperties(methodEntry).lookupPerClassProperties(classEntry);
        Integer oldIndex = methodPerClassProperties.localEntryMap.put(localEntry, index);
        assert oldIndex == null || oldIndex == index;
    }

    public int getMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, LocalEntry localEntry) {
        DwarfMethodPerClassProperties methodPerClassProperties = lookupMethodProperties(methodEntry).lookupPerClassProperties(classEntry);
        assert methodPerClassProperties != null : "get of non-existent local index";
        Integer index = methodPerClassProperties.localEntryMap.get(localEntry);
        assert index != null : "get of local index before it was set";
        return index;
    }

    public void setRangeLocalIndex(Range range, LocalEntry localEntry, int index) {
        DwarfRangeProperties rangeProperties = lookupRangeProperties(range);
        Integer oldIndex = rangeProperties.localEntryMap.put(localEntry, index);
        assert oldIndex == null || oldIndex == index;
    }

    public int getRangeLocalIndex(Range range, LocalEntry localEntry) {
        DwarfRangeProperties rangeProperties = rangePropertiesIndex.get(range);
        assert rangeProperties != null : "get of non-existent local index";
        Integer index = rangeProperties.localEntryMap.get(localEntry);
        assert index != null : "get of local index before it was set";
        return index;
    }
}
