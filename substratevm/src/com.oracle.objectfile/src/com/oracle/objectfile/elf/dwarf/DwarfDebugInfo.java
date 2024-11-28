/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
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
    enum AbbrevCode {
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
        CLASS_LAYOUT_1,
        CLASS_LAYOUT_2,
        CLASS_LAYOUT_3,
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
        METHOD_DECLARATION_STATIC,
        METHOD_DECLARATION_SKELETON,
        FIELD_DECLARATION_1,
        FIELD_DECLARATION_2,
        FIELD_DECLARATION_3,
        FIELD_DECLARATION_4,
        HEADER_FIELD,
        ARRAY_ELEMENT_FIELD,
        ARRAY_DATA_TYPE_1,
        ARRAY_DATA_TYPE_2,
        ARRAY_SUBRANGE,
        SUPER_REFERENCE,
        INTERFACE_IMPLEMENTOR,
        /* Level 2+K DIEs (where inline depth K >= 0) */
        INLINED_SUBROUTINE,
        INLINED_SUBROUTINE_WITH_CHILDREN,
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

    /*
     * A prefix used to label indirect types used to ensure gdb performs oop reference --> raw
     * address translation
     */
    public static final String COMPRESSED_PREFIX = "_z_.";
    /*
     * A prefix used for type signature generation to generate unique type signatures for type
     * layout type units
     */
    public static final String LAYOUT_PREFIX = "_layout_.";
    /*
     * The name of the type for header field hub which needs special case processing to remove tag
     * bits
     */
    public static final String HUB_TYPE_NAME = "java.lang.Class";
    /* Full byte/word values. */
    private final DwarfStrSectionImpl dwarfStrSection;
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
     * A collection of local variable properties associated with an inlined subrange.
     */
    private final EconomicMap<Range, DwarfLocalProperties> rangeLocalPropertiesIndex = EconomicMap.create();

    @SuppressWarnings("this-escape")
    public DwarfDebugInfo(ELFMachine elfMachine, ByteOrder byteOrder) {
        super(byteOrder);
        this.elfMachine = elfMachine;
        dwarfStrSection = new DwarfStrSectionImpl(this);
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
         * The type entry with which these properties are associated.
         */
        private final StructureTypeEntry typeEntry;
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
        /**
         * Map from field names to info section index for the field declaration.
         */
        private EconomicMap<String, Integer> fieldDeclarationIndex;

        public StructureTypeEntry getTypeEntry() {
            return typeEntry;
        }

        DwarfClassProperties(StructureTypeEntry typeEntry) {
            this.typeEntry = typeEntry;
            this.cuIndex = -1;
            this.codeRangesIndex = -1;
            this.locationListIndex = 0;
            this.lineIndex = -1;
            this.linePrologueSize = -1;
            fieldDeclarationIndex = null;
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
         * Per class map that identifies the info declarations for a top level method declaration or
         * an abstract inline method declaration.
         */
        private EconomicMap<ClassEntry, DwarfLocalProperties> localPropertiesMap;

        /**
         * Per class map that identifies the info declaration for an abstract inline method.
         */
        private EconomicMap<ClassEntry, Integer> abstractInlineMethodIndex;

        DwarfMethodProperties() {
            methodDeclarationIndex = -1;
            localPropertiesMap = null;
            abstractInlineMethodIndex = null;
        }

        public int getMethodDeclarationIndex() {
            assert methodDeclarationIndex >= 0 : "unset declaration index";
            return methodDeclarationIndex;
        }

        public void setMethodDeclarationIndex(int pos) {
            assert methodDeclarationIndex == -1 || methodDeclarationIndex == pos : "bad declaration index";
            methodDeclarationIndex = pos;
        }

        public DwarfLocalProperties getLocalProperties(ClassEntry classEntry) {
            if (localPropertiesMap == null) {
                localPropertiesMap = EconomicMap.create();
            }
            DwarfLocalProperties localProperties = localPropertiesMap.get(classEntry);
            if (localProperties == null) {
                localProperties = new DwarfLocalProperties();
                localPropertiesMap.put(classEntry, localProperties);
            }
            return localProperties;
        }

        public void setAbstractInlineMethodIndex(ClassEntry classEntry, int pos) {
            if (abstractInlineMethodIndex == null) {
                abstractInlineMethodIndex = EconomicMap.create();
            }
            // replace but check it did not change
            Integer val = abstractInlineMethodIndex.put(classEntry, pos);
            assert val == null || val == pos;
        }

        public int getAbstractInlineMethodIndex(ClassEntry classEntry) {
            // should be set before we get here but an NPE will guard that
            return abstractInlineMethodIndex.get(classEntry);
        }
    }

    private DwarfClassProperties addClassProperties(StructureTypeEntry entry) {
        DwarfClassProperties classProperties = new DwarfClassProperties(entry);
        this.classPropertiesIndex.put(entry, classProperties);
        return classProperties;
    }

    private DwarfMethodProperties addMethodProperties(MethodEntry methodEntry) {
        assert methodPropertiesIndex.get(methodEntry) == null;
        DwarfMethodProperties methodProperties = new DwarfMethodProperties();
        this.methodPropertiesIndex.put(methodEntry, methodProperties);
        return methodProperties;
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

    public void setCUIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.cuIndex == -1 || classProperties.cuIndex == idx;
        classProperties.cuIndex = idx;
    }

    public int getCUIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.cuIndex >= 0;
        return classProperties.cuIndex;
    }

    public void setCodeRangesIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.codeRangesIndex == -1 || classProperties.codeRangesIndex == idx;
        classProperties.codeRangesIndex = idx;
    }

    public int getCodeRangesIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.codeRangesIndex >= 0;
        return classProperties.codeRangesIndex;
    }

    public void setLocationListIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.locationListIndex == 0 || classProperties.locationListIndex == idx;
        classProperties.locationListIndex = idx;
    }

    public int getLocationListIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        return classProperties.locationListIndex;
    }

    public void setLineIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.lineIndex == -1 || classProperties.lineIndex == idx;
        classProperties.lineIndex = idx;
    }

    public int getLineIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.lineIndex >= 0;
        return classProperties.lineIndex;
    }

    public void setLinePrologueSize(ClassEntry classEntry, int size) {
        assert size >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.linePrologueSize == -1 || classProperties.linePrologueSize == size;
        classProperties.linePrologueSize = size;
    }

    public int getLinePrologueSize(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.linePrologueSize >= 0;
        return classProperties.linePrologueSize;
    }

    public void setFieldDeclarationIndex(StructureTypeEntry entry, String fieldName, int pos) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(entry);
        assert classProperties.getTypeEntry() == entry;
        EconomicMap<String, Integer> fieldDeclarationIndex = classProperties.fieldDeclarationIndex;
        if (fieldDeclarationIndex == null) {
            classProperties.fieldDeclarationIndex = fieldDeclarationIndex = EconomicMap.create();
        }
        if (fieldDeclarationIndex.get(fieldName) != null) {
            assert fieldDeclarationIndex.get(fieldName) == pos : entry.getTypeName() + fieldName;
        } else {
            fieldDeclarationIndex.put(fieldName, pos);
        }
    }

    public int getFieldDeclarationIndex(StructureTypeEntry entry, String fieldName) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(entry);
        assert classProperties.getTypeEntry() == entry;
        EconomicMap<String, Integer> fieldDeclarationIndex = classProperties.fieldDeclarationIndex;
        assert fieldDeclarationIndex != null : fieldName;
        assert fieldDeclarationIndex.get(fieldName) != null : entry.getTypeName() + fieldName;
        return fieldDeclarationIndex.get(fieldName);
    }

    public void setMethodDeclarationIndex(MethodEntry methodEntry, int pos) {
        DwarfMethodProperties methodProperties = lookupMethodProperties(methodEntry);
        methodProperties.setMethodDeclarationIndex(pos);
    }

    public int getMethodDeclarationIndex(MethodEntry methodEntry) {
        DwarfMethodProperties methodProperties = lookupMethodProperties(methodEntry);
        return methodProperties.getMethodDeclarationIndex();
    }

    /**
     * A class used to associate properties with a specific param or local whether top level or
     * inline.
     */

    static final class DwarfLocalProperties {
        private EconomicMap<DebugLocalInfo, Integer> locals;

        private DwarfLocalProperties() {
            locals = EconomicMap.create();
        }

        int getIndex(DebugLocalInfo localInfo) {
            return locals.get(localInfo);
        }

        void setIndex(DebugLocalInfo localInfo, int index) {
            if (locals.get(localInfo) != null) {
                assert locals.get(localInfo) == index;
            } else {
                locals.put(localInfo, index);
            }
        }
    }

    public void setAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry, int pos) {
        lookupMethodProperties(methodEntry).setAbstractInlineMethodIndex(classEntry, pos);
    }

    public int getAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry) {
        return lookupMethodProperties(methodEntry).getAbstractInlineMethodIndex(classEntry);
    }

    private DwarfLocalProperties addRangeLocalProperties(Range range) {
        DwarfLocalProperties localProperties = new DwarfLocalProperties();
        rangeLocalPropertiesIndex.put(range, localProperties);
        return localProperties;
    }

    public DwarfLocalProperties lookupLocalProperties(ClassEntry classEntry, MethodEntry methodEntry) {
        return lookupMethodProperties(methodEntry).getLocalProperties(classEntry);
    }

    public void setMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugLocalInfo localInfo, int index) {
        DwarfLocalProperties localProperties = lookupLocalProperties(classEntry, methodEntry);
        localProperties.setIndex(localInfo, index);
    }

    public int getMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugLocalInfo localInfo) {
        DwarfLocalProperties localProperties = lookupLocalProperties(classEntry, methodEntry);
        assert localProperties != null : "get of non-existent local index";
        int index = localProperties.getIndex(localInfo);
        assert index >= 0 : "get of local index before it was set";
        return index;
    }

    public void setRangeLocalIndex(Range range, DebugLocalInfo localInfo, int index) {
        DwarfLocalProperties rangeProperties = rangeLocalPropertiesIndex.get(range);
        if (rangeProperties == null) {
            rangeProperties = addRangeLocalProperties(range);
        }
        rangeProperties.setIndex(localInfo, index);
    }

    public int getRangeLocalIndex(Range range, DebugLocalInfo localinfo) {
        DwarfLocalProperties rangeProperties = rangeLocalPropertiesIndex.get(range);
        assert rangeProperties != null : "get of non-existent local index";
        int index = rangeProperties.getIndex(localinfo);
        assert index >= 0 : "get of local index before it was set";
        return index;
    }
}
