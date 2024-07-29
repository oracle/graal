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

package com.oracle.objectfile.runtime.dwarf;

import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.runtime.RuntimeDebugInfoBase;
import com.oracle.objectfile.runtime.RuntimeDebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.runtime.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.runtime.debugentry.MethodEntry;
import com.oracle.objectfile.runtime.debugentry.StructureTypeEntry;
import com.oracle.objectfile.runtime.debugentry.TypeEntry;
import com.oracle.objectfile.runtime.debugentry.range.Range;
import com.oracle.objectfile.runtime.dwarf.constants.DwarfLanguage;
import org.graalvm.collections.EconomicMap;

import java.nio.ByteOrder;
import java.util.HashMap;

/**
 * A class that models the debug info in an organization that facilitates generation of the required
 * DWARF sections. It groups common data and behaviours for use by the various subclasses of class
 * DwarfSectionImpl that take responsibility for generating content for a specific section type.
 */
public class RuntimeDwarfDebugInfo extends RuntimeDebugInfoBase {
    /*
     * Define all the abbrev section codes we need for our DIEs.
     */
    enum AbbrevCode {
        /* null marker which must come first as its ordinal has to equal zero */
        NULL,
        /* Level 0 DIEs. */
        METHOD_UNIT,
        /* Level 1 DIEs. */
        PRIMITIVE_TYPE,
        TYPEDEF,
        TYPEDEF_POINTER,
        METHOD_LOCATION,
        METHOD_LOCATION_STATIC,
        ABSTRACT_INLINE_METHOD,
        /* Level 2 DIEs. */
        /* Level 2+K DIEs (where inline depth K >= 0) */
        INLINED_SUBROUTINE,
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
    private final RuntimeDwarfStrSectionImpl dwarfStrSection;
    private final RuntimeDwarfAbbrevSectionImpl dwarfAbbrevSection;
    private final RuntimeDwarfInfoSectionImpl dwarfInfoSection;
    private final RuntimeDwarfLocSectionImpl dwarfLocSection;
    private final RuntimeDwarfLineSectionImpl dwarfLineSection;
    private final RuntimeDwarfFrameSectionImpl dwarfFameSection;
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
    private final EconomicMap<TypeEntry, DwarfTypeProperties> typePropertiesIndex = EconomicMap.create();

    /**
     * A collection of method properties associated with each generated method record.
     */
    private final EconomicMap<MethodEntry, DwarfMethodProperties> methodPropertiesIndex = EconomicMap.create();

    /**
     * A collection of local variable properties associated with an inlined subrange.
     */
    private final EconomicMap<Range, DwarfLocalProperties> rangeLocalPropertiesIndex = EconomicMap.create();

    @SuppressWarnings("this-escape")
    public RuntimeDwarfDebugInfo(ELFMachine elfMachine, ByteOrder byteOrder) {
        super(byteOrder);
        this.elfMachine = elfMachine;
        dwarfStrSection = new RuntimeDwarfStrSectionImpl(this);
        dwarfAbbrevSection = new RuntimeDwarfAbbrevSectionImpl(this);
        dwarfInfoSection = new RuntimeDwarfInfoSectionImpl(this);
        dwarfLocSection = new RuntimeDwarfLocSectionImpl(this);
        dwarfLineSection = new RuntimeDwarfLineSectionImpl(this);

        if (elfMachine == ELFMachine.AArch64) {
            dwarfFameSection = new RuntimeDwarfFrameSectionImplAArch64(this);
            this.heapbaseRegister = rheapbase_aarch64;
            this.threadRegister = rthread_aarch64;
        } else {
            dwarfFameSection = new RuntimeDwarfFrameSectionImplX86_64(this);
            this.heapbaseRegister = rheapbase_x86;
            this.threadRegister = rthread_x86;
        }
    }

    public RuntimeDwarfStrSectionImpl getStrSectionImpl() {
        return dwarfStrSection;
    }

    public RuntimeDwarfAbbrevSectionImpl getAbbrevSectionImpl() {
        return dwarfAbbrevSection;
    }

    public RuntimeDwarfFrameSectionImpl getFrameSectionImpl() {
        return dwarfFameSection;
    }

    public RuntimeDwarfInfoSectionImpl getInfoSectionImpl() {
        return dwarfInfoSection;
    }

    public RuntimeDwarfLocSectionImpl getLocSectionImpl() {
        return dwarfLocSection;
    }

    public RuntimeDwarfLineSectionImpl getLineSectionImpl() {
        return dwarfLineSection;
    }

    public byte getHeapbaseRegister() {
        return heapbaseRegister;
    }

    public byte getThreadRegister() {
        return threadRegister;
    }

    /**
     * A class used to associate properties with a specific type, the most important one being its
     * index in the info section.
     */
    static class DwarfTypeProperties {
        /**
         * Index in debug_info section of type declaration for this class.
         */
        private int typeInfoIndex;
        /**
         * Index in debug_info section of indirect type declaration for this class.
         *
         * this is normally just the same as the index of the normal type declaration, however, when
         * oops are stored in static and instance fields as offsets from the heapbase register gdb
         * needs to be told how to convert these oops to raw addresses and this requires attaching a
         * data_location address translation expression to an indirect type that wraps the object
         * layout type. so, with that encoding this field will identify the wrapper type whenever
         * the original type is an object, interface or array layout. primitive types and header
         * types do not need translating.
         */
        private int indirectTypeInfoIndex;
        /**
         * The type entry with which these properties are associated.
         */
        private final TypeEntry typeEntry;

        public int getTypeInfoIndex() {
            return typeInfoIndex;
        }

        public void setTypeInfoIndex(int typeInfoIndex) {
            this.typeInfoIndex = typeInfoIndex;
        }

        public int getIndirectTypeInfoIndex() {
            return indirectTypeInfoIndex;
        }

        public void setIndirectTypeInfoIndex(int typeInfoIndex) {
            this.indirectTypeInfoIndex = typeInfoIndex;
        }

        public TypeEntry getTypeEntry() {
            return typeEntry;
        }

        DwarfTypeProperties(TypeEntry typeEntry) {
            this.typeEntry = typeEntry;
            this.typeInfoIndex = -1;
            this.indirectTypeInfoIndex = -1;
        }

    }

    /**
     * A class used to associate extra properties with an instance class type.
     */

    static class DwarfClassProperties extends DwarfTypeProperties {
        /**
         * Index of the class entry's compile unit in the debug_info section.
         */
        private int cuIndex;
        /**
         * Index of the class entry's class_layout DIE in the debug_info section.
         */
        private int layoutIndex;
        /**
         * Index of the class entry's indirect layout DIE in the debug_info section.
         */
        private int indirectLayoutIndex;
        /**
         * Index of the class entry's code ranges data in the debug_ranges section.
         */
        private int codeRangesIndex;
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

        DwarfClassProperties(StructureTypeEntry entry) {
            super(entry);
            this.cuIndex = -1;
            this.layoutIndex = -1;
            this.indirectLayoutIndex = -1;
            this.codeRangesIndex = -1;
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
        private HashMap<CompiledMethodEntry, DwarfLocalProperties> localPropertiesMap;

        /**
         * Per class map that identifies the info declaration for an abstract inline method.
         */
        private HashMap<CompiledMethodEntry, Integer> abstractInlineMethodIndex;

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

        public DwarfLocalProperties getLocalProperties(CompiledMethodEntry compiledEntry) {
            if (localPropertiesMap == null) {
                localPropertiesMap = new HashMap<>();
            }
            DwarfLocalProperties localProperties = localPropertiesMap.get(compiledEntry);
            if (localProperties == null) {
                localProperties = new DwarfLocalProperties();
                localPropertiesMap.put(compiledEntry, localProperties);
            }
            return localProperties;
        }

        public void setAbstractInlineMethodIndex(CompiledMethodEntry compiledEntry, int pos) {
            if (abstractInlineMethodIndex == null) {
                abstractInlineMethodIndex = new HashMap<>();
            }
            // replace but check it did not change
            Integer val = abstractInlineMethodIndex.put(compiledEntry, pos);
            assert val == null || val == pos;
        }

        public int getAbstractInlineMethodIndex(CompiledMethodEntry compiledEntry) {
            // should be set before we get here but an NPE will guard that
            return abstractInlineMethodIndex.get(compiledEntry);
        }
    }

    private DwarfTypeProperties addTypeProperties(TypeEntry typeEntry) {
        assert typeEntry != null;
        assert !typeEntry.isClass();
        assert typePropertiesIndex.get(typeEntry) == null;
        DwarfTypeProperties typeProperties = new DwarfTypeProperties(typeEntry);
        this.typePropertiesIndex.put(typeEntry, typeProperties);
        return typeProperties;
    }

    private DwarfClassProperties addClassProperties(StructureTypeEntry entry) {
        assert typePropertiesIndex.get(entry) == null;
        DwarfClassProperties classProperties = new DwarfClassProperties(entry);
        this.typePropertiesIndex.put(entry, classProperties);
        return classProperties;
    }

    private DwarfMethodProperties addMethodProperties(MethodEntry methodEntry) {
        assert methodPropertiesIndex.get(methodEntry) == null;
        DwarfMethodProperties methodProperties = new DwarfMethodProperties();
        this.methodPropertiesIndex.put(methodEntry, methodProperties);
        return methodProperties;
    }

    private DwarfTypeProperties lookupTypeProperties(TypeEntry typeEntry) {
        DwarfTypeProperties typeProperties = typePropertiesIndex.get(typeEntry);
        if (typeProperties == null) {
            typeProperties = addTypeProperties(typeEntry);
        }
        return typeProperties;
    }

    private DwarfClassProperties lookupClassProperties(StructureTypeEntry entry) {
        DwarfTypeProperties typeProperties = typePropertiesIndex.get(entry);
        assert typeProperties == null || typeProperties instanceof DwarfClassProperties;
        DwarfClassProperties classProperties = (DwarfClassProperties) typeProperties;
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

    void setTypeIndex(TypeEntry typeEntry, int idx) {
        assert idx >= 0;
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        assert typeProperties.getTypeInfoIndex() == -1 || typeProperties.getTypeInfoIndex() == idx;
        typeProperties.setTypeInfoIndex(idx);
    }

    int getTypeIndex(TypeEntry typeEntry) {
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        return getTypeIndex(typeProperties);
    }

    int getTypeIndex(DwarfTypeProperties typeProperties) {
        assert typeProperties.getTypeInfoIndex() >= 0;
        return typeProperties.getTypeInfoIndex();
    }

    void setIndirectTypeIndex(TypeEntry typeEntry, int idx) {
        assert idx >= 0;
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        assert typeProperties.getIndirectTypeInfoIndex() == -1 || typeProperties.getIndirectTypeInfoIndex() == idx;
        typeProperties.setIndirectTypeInfoIndex(idx);
    }

    int getIndirectTypeIndex(TypeEntry typeEntry) {
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        return getIndirectTypeIndex(typeProperties);
    }

    int getIndirectTypeIndex(DwarfTypeProperties typeProperties) {
        assert typeProperties.getIndirectTypeInfoIndex() >= 0;
        return typeProperties.getIndirectTypeInfoIndex();
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

    public void setAbstractInlineMethodIndex(CompiledMethodEntry compiledEntry, MethodEntry methodEntry, int pos) {
        lookupMethodProperties(methodEntry).setAbstractInlineMethodIndex(compiledEntry, pos);
    }

    public int getAbstractInlineMethodIndex(CompiledMethodEntry compiledEntry, MethodEntry methodEntry) {
        return lookupMethodProperties(methodEntry).getAbstractInlineMethodIndex(compiledEntry);
    }

    private DwarfLocalProperties addRangeLocalProperties(Range range) {
        DwarfLocalProperties localProperties = new DwarfLocalProperties();
        rangeLocalPropertiesIndex.put(range, localProperties);
        return localProperties;
    }

    public DwarfLocalProperties lookupLocalProperties(CompiledMethodEntry compiledEntry, MethodEntry methodEntry) {
        return lookupMethodProperties(methodEntry).getLocalProperties(compiledEntry);
    }

    public void setMethodLocalIndex(CompiledMethodEntry compiledEntry, MethodEntry methodEntry, DebugLocalInfo localInfo, int index) {
        DwarfLocalProperties localProperties = lookupLocalProperties(compiledEntry, methodEntry);
        localProperties.setIndex(localInfo, index);
    }

    public int getMethodLocalIndex(CompiledMethodEntry compiledEntry, MethodEntry methodEntry, DebugLocalInfo localInfo) {
        DwarfLocalProperties localProperties = lookupLocalProperties(compiledEntry, methodEntry);
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
