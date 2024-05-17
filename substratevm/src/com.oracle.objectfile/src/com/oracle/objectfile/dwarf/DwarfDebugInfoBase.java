/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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

package com.oracle.objectfile.dwarf;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.DebugInfoBase;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.dwarf.constants.DwarfLanguage;
import com.oracle.objectfile.dwarf.constants.DwarfSectionNameBase;
import org.graalvm.collections.EconomicMap;

import java.nio.ByteOrder;

public abstract class DwarfDebugInfoBase extends DebugInfoBase {
    public static final String HEAP_BEGIN_NAME = "__svm_heap_begin";
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
    public static final String INDIRECT_PREFIX = "_z_.";
    /**
     * A collection of properties associated with each generated type record indexed by type name.
     * n.b. this collection includes entries for the structure types used to define the object and
     * array headers which do not have an associated TypeEntry.
     */
    protected final EconomicMap<TypeEntry, DwarfTypeProperties> typePropertiesIndex = EconomicMap.create();
    /**
     * A collection of method properties associated with each generated method record.
     */
    protected final EconomicMap<MethodEntry, DwarfMethodProperties> methodPropertiesIndex = EconomicMap.create();
    /**
     * A collection of local variable properties associated with an inlined subrange.
     */
    protected final EconomicMap<Range, DwarfLocalProperties> rangeLocalPropertiesIndex = EconomicMap.create();

    public DwarfDebugInfoBase(ByteOrder byteOrder) {
        super(byteOrder);
    }

    public int getIndirectLayoutIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.indirectLayoutIndex >= 0;
        return classProperties.indirectLayoutIndex;
    }

    public int getCodeRangesIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.codeRangesIndex >= 0;
        return classProperties.codeRangesIndex;
    }

    public void setCodeRangesIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.codeRangesIndex == -1 || classProperties.codeRangesIndex == idx;
        classProperties.codeRangesIndex = idx;
    }

    protected DwarfMethodProperties lookupMethodProperties(MethodEntry methodEntry) {
        DwarfMethodProperties methodProperties = methodPropertiesIndex.get(methodEntry);
        if (methodProperties == null) {
            methodProperties = addMethodProperties(methodEntry);
        }
        return methodProperties;
    }

    public DwarfLocalProperties lookupLocalProperties(ClassEntry classEntry, MethodEntry methodEntry) {
        return lookupMethodProperties(methodEntry).getLocalProperties(classEntry);
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

    protected DwarfLocalProperties addRangeLocalProperties(Range range) {
        DwarfLocalProperties localProperties = new DwarfLocalProperties();
        rangeLocalPropertiesIndex.put(range, localProperties);
        return localProperties;
    }

    public void setTypeIndex(TypeEntry typeEntry, int idx) {
        assert idx >= 0;
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        assert typeProperties.getTypeInfoIndex() == -1 || typeProperties.getTypeInfoIndex() == idx;
        typeProperties.setTypeInfoIndex(idx);
    }

    public int getTypeIndex(TypeEntry typeEntry) {
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        return getTypeIndex(typeProperties);
    }

    int getTypeIndex(DwarfTypeProperties typeProperties) {
        assert typeProperties.getTypeInfoIndex() >= 0;
        return typeProperties.getTypeInfoIndex();
    }

    public void setIndirectTypeIndex(TypeEntry typeEntry, int idx) {
        assert idx >= 0;
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        assert typeProperties.getIndirectTypeInfoIndex() == -1 || typeProperties.getIndirectTypeInfoIndex() == idx;
        typeProperties.setIndirectTypeInfoIndex(idx);
    }

    public void setLayoutIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0 || idx == -1;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.layoutIndex == -1 || classProperties.layoutIndex == idx;
        classProperties.layoutIndex = idx;
    }

    public void setIndirectLayoutIndex(ClassEntry classEntry, int idx) {
        // The layout index of a POINTER type is set to the type index of its referent.
        // If the pointer type is generated before its referent that means it can be set
        // with value -1 (unset) on the first sizing pass. The indirect layout will
        // be reset to a positive offset on the second pass before it is used to write
        // the referent of the pointer type. Hence the condition in the following assert.
        assert idx >= 0 || idx == -1;
        // Note however, that this possibility needs to be finessed when writing
        // a foreign struct ADDRESS field of POINTER type (i.e. an embedded field).
        // If the struct is generated before the POINTER type then the layout index will
        // still be -1 during the second write pass when the field type needs to be
        // written. This possibility is handled by typing the field using the typeIdx
        // of the referent. the latter is guaranteed to have been set during the first pass.

        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.indirectLayoutIndex == -1 || classProperties.indirectLayoutIndex == idx;
        classProperties.indirectLayoutIndex = idx;
    }

    public void setLineIndex(ClassEntry classEntry, int idx) {
        assert idx >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.lineIndex == -1 || classProperties.lineIndex == idx;
        classProperties.lineIndex = idx;
    }

    public void setLinePrologueSize(ClassEntry classEntry, int size) {
        assert size >= 0;
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.linePrologueSize == -1 || classProperties.linePrologueSize == size;
        classProperties.linePrologueSize = size;
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

    public void setMethodDeclarationIndex(MethodEntry methodEntry, int pos) {
        DwarfMethodProperties methodProperties = lookupMethodProperties(methodEntry);
        methodProperties.setMethodDeclarationIndex(pos);
    }

    public void setAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry, int pos) {
        lookupMethodProperties(methodEntry).setAbstractInlineMethodIndex(classEntry, pos);
    }

    public void setMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugInfoProvider.DebugLocalInfo localInfo, int index) {
        DwarfLocalProperties localProperties = lookupLocalProperties(classEntry, methodEntry);
        localProperties.setIndex(localInfo, index);
    }

    public void setRangeLocalIndex(Range range, DebugInfoProvider.DebugLocalInfo localInfo, int index) {
        DwarfLocalProperties rangeProperties = rangeLocalPropertiesIndex.get(range);
        if (rangeProperties == null) {
            rangeProperties = addRangeLocalProperties(range);
        }
        rangeProperties.setIndex(localInfo, index);
    }

    public int getLayoutIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.layoutIndex >= 0;
        return classProperties.layoutIndex;
    }

    public int getLineIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.lineIndex >= 0;
        return classProperties.lineIndex;
    }

    public int getLinePrologueSize(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.linePrologueSize >= 0;
        return classProperties.linePrologueSize;
    }

    public int getMethodDeclarationIndex(MethodEntry methodEntry) {
        DwarfMethodProperties methodProperties = lookupMethodProperties(methodEntry);
        return methodProperties.getMethodDeclarationIndex();
    }

    public int getMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugInfoProvider.DebugLocalInfo localInfo) {
        DwarfLocalProperties localProperties = lookupLocalProperties(classEntry, methodEntry);
        assert localProperties != null : "get of non-existent local index";
        int index = localProperties.getIndex(localInfo);
        assert index >= 0 : "get of local index before it was set";
        return index;
    }

    public int getRangeLocalIndex(Range range, DebugInfoProvider.DebugLocalInfo localinfo) {
        DwarfLocalProperties rangeProperties = rangeLocalPropertiesIndex.get(range);
        assert rangeProperties != null : "get of non-existent local index";
        int index = rangeProperties.getIndex(localinfo);
        assert index >= 0 : "get of local index before it was set";
        return index;
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

    public int getAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry) {
        return lookupMethodProperties(methodEntry).getAbstractInlineMethodIndex(classEntry);
    }

    /*
     * Define all the abbrev section codes we need for our DIEs.
     */
    public enum AbbrevCode {
        /* null marker which must come first as its ordinal has to equal zero */
        NULL,
        /* Level 0 DIEs. */
        BUILTIN_UNIT,
        CLASS_UNIT_1,
        CLASS_UNIT_2,
        /* Level 1 DIEs. */
        PRIMITIVE_TYPE,
        VOID_TYPE,
        OBJECT_HEADER,
        NAMESPACE,
        CLASS_LAYOUT_1,
        CLASS_LAYOUT_2,
        CLASS_POINTER,
        FOREIGN_POINTER,
        FOREIGN_TYPEDEF,
        FOREIGN_STRUCT,
        METHOD_LOCATION,
        STATIC_FIELD_LOCATION,
        ARRAY_LAYOUT,
        ARRAY_POINTER,
        INTERFACE_LAYOUT,
        INTERFACE_POINTER,
        INDIRECT_LAYOUT,
        INDIRECT_POINTER,
        /* Level 2 DIEs. */
        METHOD_DECLARATION,
        METHOD_DECLARATION_STATIC,
        FIELD_DECLARATION_1,
        FIELD_DECLARATION_2,
        FIELD_DECLARATION_3,
        FIELD_DECLARATION_4,
        CLASS_CONSTANT,
        HEADER_FIELD,
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
        METHOD_LOCAL_DECLARATION_1,
        METHOD_LOCAL_DECLARATION_2,
        METHOD_PARAMETER_LOCATION_1,
        METHOD_PARAMETER_LOCATION_2,
        METHOD_LOCAL_LOCATION_1,
        METHOD_LOCAL_LOCATION_2,
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
         * <p>
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
        private EconomicMap<ClassEntry, DwarfDebugInfo.DwarfLocalProperties> localPropertiesMap;

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

    /**
     * A class used to associate properties with a specific param or local whether top level or
     * inline.
     */

    static final class DwarfLocalProperties {
        private EconomicMap<DebugInfoProvider.DebugLocalInfo, Integer> locals;

        private DwarfLocalProperties() {
            locals = EconomicMap.create();
        }

        int getIndex(DebugInfoProvider.DebugLocalInfo localInfo) {
            return locals.get(localInfo);
        }

        void setIndex(DebugInfoProvider.DebugLocalInfo localInfo, int index) {
            if (locals.get(localInfo) != null) {
                assert locals.get(localInfo) == index;
            } else {
                locals.put(localInfo, index);
            }
        }
    }

    public int getIndirectTypeIndex(TypeEntry typeEntry) {
        DwarfTypeProperties typeProperties = lookupTypeProperties(typeEntry);
        return getIndirectTypeIndex(typeProperties);
    }

    int getIndirectTypeIndex(DwarfTypeProperties typeProperties) {
        assert typeProperties.getIndirectTypeInfoIndex() >= 0;
        return typeProperties.getIndirectTypeInfoIndex();
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

    protected DwarfTypeProperties lookupTypeProperties(TypeEntry typeEntry) {
        if (typeEntry instanceof ClassEntry) {
            return lookupClassProperties((ClassEntry) typeEntry);
        } else {
            DwarfTypeProperties typeProperties = typePropertiesIndex.get(typeEntry);
            if (typeProperties == null) {
                typeProperties = addTypeProperties(typeEntry);
            }
            return typeProperties;
        }
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

    private DwarfTypeProperties addTypeProperties(TypeEntry typeEntry) {
        assert typeEntry != null;
        assert !typeEntry.isClass();
        assert typePropertiesIndex.get(typeEntry) == null;
        DwarfTypeProperties typeProperties = new DwarfTypeProperties(typeEntry);
        this.typePropertiesIndex.put(typeEntry, typeProperties);
        return typeProperties;
    }

    public abstract byte getHeapbaseRegister();

    public abstract boolean isAarch64();

    public abstract boolean isAMD64();

    public abstract DwarfSectionNameBase textSectionName();

    public abstract DwarfSectionNameBase lineSectionName();

    public abstract DwarfSectionNameBase strSectionName();

    public abstract DwarfSectionNameBase locSectionName();

    public abstract DwarfSectionNameBase rangesSectionName();

    public abstract DwarfSectionNameBase arangesSectionName();

    public abstract DwarfSectionNameBase frameSectionName();

    public abstract DwarfSectionNameBase abbrevSectionName();

    public abstract DwarfSectionNameBase infoSectionName();

    public boolean layoutDependsOnVaddr() {
        return false;
    }

    public boolean isDebugSectionName(String name) {
        return name.startsWith(".debug");
    }

    public long relocatableLong(long l) {
        return 0L;
    }

    public int relocatableInt(int l) {
        return 0;
    }

}
