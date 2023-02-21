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

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.DebugInfoBase;

import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.elf.ELFMachine;
import org.graalvm.collections.EconomicMap;

/**
 * A class that models the debug info in an organization that facilitates generation of the required
 * DWARF sections. It groups common data and behaviours for use by the various subclasses of class
 * DwarfSectionImpl that take responsibility for generating content for a specific section type.
 */
public class DwarfDebugInfo extends DebugInfoBase {

    /*
     * Names of the different ELF sections we create or reference in reverse dependency order.
     */
    public static final String TEXT_SECTION_NAME = ".text";
    public static final String HEAP_BEGIN_NAME = "__svm_heap_begin";
    public static final String DW_STR_SECTION_NAME = ".debug_str";
    public static final String DW_LINE_SECTION_NAME = ".debug_line";
    public static final String DW_FRAME_SECTION_NAME = ".debug_frame";
    public static final String DW_ABBREV_SECTION_NAME = ".debug_abbrev";
    public static final String DW_INFO_SECTION_NAME = ".debug_info";
    public static final String DW_LOC_SECTION_NAME = ".debug_loc";
    public static final String DW_ARANGES_SECTION_NAME = ".debug_aranges";

    /**
     * Currently generated debug info relies on DWARF spec version 4.
     */
    public static final short DW_VERSION_2 = 2;
    public static final short DW_VERSION_4 = 4;

    /*
     * Define all the abbrev section codes we need for our DIEs.
     */
    @SuppressWarnings("unused") public static final int DW_ABBREV_CODE_null = 0;
    /* Level 0 DIEs. */
    public static final int DW_ABBREV_CODE_builtin_unit = 1;
    public static final int DW_ABBREV_CODE_class_unit1 = 2;
    public static final int DW_ABBREV_CODE_class_unit2 = 3;
    public static final int DW_ABBREV_CODE_array_unit = 4;
    /* Level 1 DIEs. */
    public static final int DW_ABBREV_CODE_primitive_type = 5;
    public static final int DW_ABBREV_CODE_void_type = 6;
    public static final int DW_ABBREV_CODE_object_header = 7;
    public static final int DW_ABBREV_CODE_namespace = 8;
    public static final int DW_ABBREV_CODE_class_layout1 = 9;
    public static final int DW_ABBREV_CODE_class_layout2 = 10;
    public static final int DW_ABBREV_CODE_class_pointer = 11;
    public static final int DW_ABBREV_CODE_method_location = 12;
    public static final int DW_ABBREV_CODE_abstract_inline_method = 13;
    public static final int DW_ABBREV_CODE_static_field_location = 14;
    public static final int DW_ABBREV_CODE_array_layout = 15;
    public static final int DW_ABBREV_CODE_array_pointer = 16;
    public static final int DW_ABBREV_CODE_interface_layout = 17;
    public static final int DW_ABBREV_CODE_interface_pointer = 18;
    public static final int DW_ABBREV_CODE_indirect_layout = 19;
    public static final int DW_ABBREV_CODE_indirect_pointer = 20;
    /* Level 2 DIEs. */
    public static final int DW_ABBREV_CODE_method_declaration = 21;
    public static final int DW_ABBREV_CODE_method_declaration_static = 22;
    public static final int DW_ABBREV_CODE_field_declaration1 = 23;
    public static final int DW_ABBREV_CODE_field_declaration2 = 24;
    public static final int DW_ABBREV_CODE_field_declaration3 = 25;
    public static final int DW_ABBREV_CODE_field_declaration4 = 26;
    public static final int DW_ABBREV_CODE_class_constant = 42;
    public static final int DW_ABBREV_CODE_header_field = 27;
    public static final int DW_ABBREV_CODE_array_data_type = 28;
    public static final int DW_ABBREV_CODE_super_reference = 29;
    public static final int DW_ABBREV_CODE_interface_implementor = 30;
    /* Level 2+K DIEs (where inline depth K >= 0) */
    public static final int DW_ABBREV_CODE_inlined_subroutine = 31;
    public static final int DW_ABBREV_CODE_inlined_subroutine_with_children = 32;
    /* Level 2 DIEs. */
    public static final int DW_ABBREV_CODE_method_parameter_declaration1 = 33;
    public static final int DW_ABBREV_CODE_method_parameter_declaration2 = 34;
    public static final int DW_ABBREV_CODE_method_parameter_declaration3 = 35;
    public static final int DW_ABBREV_CODE_method_local_declaration1 = 36;
    public static final int DW_ABBREV_CODE_method_local_declaration2 = 37;
    /* Level 3 DIEs. */
    public static final int DW_ABBREV_CODE_method_parameter_location1 = 38;
    public static final int DW_ABBREV_CODE_method_parameter_location2 = 39;
    public static final int DW_ABBREV_CODE_method_local_location1 = 40;
    public static final int DW_ABBREV_CODE_method_local_location2 = 41;

    /*
     * Define all the Dwarf tags we need for our DIEs.
     */
    public static final int DW_TAG_array_type = 0x01;
    public static final int DW_TAG_class_type = 0x02;
    public static final int DW_TAG_formal_parameter = 0x05;
    public static final int DW_TAG_member = 0x0d;
    public static final int DW_TAG_pointer_type = 0x0f;
    public static final int DW_TAG_compile_unit = 0x11;
    public static final int DW_TAG_structure_type = 0x13;
    public static final int DW_TAG_union_type = 0x17;
    public static final int DW_TAG_inheritance = 0x1c;
    public static final int DW_TAG_base_type = 0x24;
    public static final int DW_TAG_constant = 0x27;
    public static final int DW_TAG_subprogram = 0x2e;
    public static final int DW_TAG_variable = 0x34;
    public static final int DW_TAG_namespace = 0x39;
    public static final int DW_TAG_unspecified_type = 0x3b;
    public static final int DW_TAG_inlined_subroutine = 0x1d;

    /*
     * Define all the Dwarf attributes we need for our DIEs.
     */
    public static final int DW_AT_null = 0x0;
    public static final int DW_AT_location = 0x02;
    public static final int DW_AT_name = 0x3;
    public static final int DW_AT_byte_size = 0x0b;
    public static final int DW_AT_bit_size = 0x0d;
    public static final int DW_AT_stmt_list = 0x10;
    public static final int DW_AT_low_pc = 0x11;
    public static final int DW_AT_hi_pc = 0x12;
    public static final int DW_AT_language = 0x13;
    public static final int DW_AT_comp_dir = 0x1b;
    public static final int DW_AT_containing_type = 0x1d;
    public static final int DW_AT_inline = 0x20;
    public static final int DW_AT_abstract_origin = 0x31;
    public static final int DW_AT_accessibility = 0x32;
    public static final int DW_AT_artificial = 0x34;
    public static final int DW_AT_data_member_location = 0x38;
    @SuppressWarnings("unused") public static final int DW_AT_decl_column = 0x39;
    public static final int DW_AT_decl_file = 0x3a;
    @SuppressWarnings("unused") public static final int DW_AT_decl_line = 0x3b;
    public static final int DW_AT_declaration = 0x3c;
    public static final int DW_AT_encoding = 0x3e;
    public static final int DW_AT_external = 0x3f;
    @SuppressWarnings("unused") public static final int DW_AT_return_addr = 0x2a;
    @SuppressWarnings("unused") public static final int DW_AT_frame_base = 0x40;
    public static final int DW_AT_specification = 0x47;
    public static final int DW_AT_type = 0x49;
    public static final int DW_AT_data_location = 0x50;
    public static final int DW_AT_use_UTF8 = 0x53;
    public static final int DW_AT_call_file = 0x58;
    public static final int DW_AT_call_line = 0x59;
    public static final int DW_AT_object_pointer = 0x64;
    public static final int DW_AT_linkage_name = 0x6e;

    /*
     * Define all the Dwarf attribute forms we need for our DIEs.
     */
    public static final int DW_FORM_null = 0x0;
    public static final int DW_FORM_addr = 0x1;
    public static final int DW_FORM_data2 = 0x05;
    public static final int DW_FORM_data4 = 0x6;
    @SuppressWarnings("unused") public static final int DW_FORM_data8 = 0x7;
    @SuppressWarnings("unused") private static final int DW_FORM_string = 0x8;
    @SuppressWarnings("unused") public static final int DW_FORM_block1 = 0x0a;
    public static final int DW_FORM_ref_addr = 0x10;
    @SuppressWarnings("unused") public static final int DW_FORM_ref1 = 0x11;
    @SuppressWarnings("unused") public static final int DW_FORM_ref2 = 0x12;
    @SuppressWarnings("unused") public static final int DW_FORM_ref4 = 0x13;
    @SuppressWarnings("unused") public static final int DW_FORM_ref8 = 0x14;
    public static final int DW_FORM_sec_offset = 0x17;
    public static final int DW_FORM_data1 = 0x0b;
    public static final int DW_FORM_flag = 0xc;
    public static final int DW_FORM_strp = 0xe;
    public static final int DW_FORM_expr_loc = 0x18;

    /*
     * Define specific attribute values for given attribute or form types.
     */
    /*
     * DIE header has_children attribute values.
     */
    public static final byte DW_CHILDREN_no = 0;
    public static final byte DW_CHILDREN_yes = 1;
    /*
     * DW_FORM_flag attribute values.
     */
    @SuppressWarnings("unused") public static final byte DW_FLAG_false = 0;
    public static final byte DW_FLAG_true = 1;
    /*
     * Value for DW_AT_language attribute with form DATA1.
     */
    public static final byte DW_LANG_Java = 0xb;
    /**
     * This field defines the value used for the DW_AT_language attribute of compile units.
     *
     */
    public static final byte LANG_ENCODING = DW_LANG_Java;
    /*
     * Values for {@link DW_AT_inline} attribute with form DATA1.
     */
    @SuppressWarnings("unused") public static final byte DW_INL_not_inlined = 0;
    public static final byte DW_INL_inlined = 1;
    @SuppressWarnings("unused") public static final byte DW_INL_declared_not_inlined = 2;
    @SuppressWarnings("unused") public static final byte DW_INL_declared_inlined = 3;

    /*
     * DW_AT_Accessibility attribute values.
     *
     * These are not needed until we make functions members.
     */
    @SuppressWarnings("unused") public static final byte DW_ACCESS_public = 1;
    @SuppressWarnings("unused") public static final byte DW_ACCESS_protected = 2;
    @SuppressWarnings("unused") public static final byte DW_ACCESS_private = 3;

    /*
     * DW_AT_encoding attribute values
     */
    public static final byte DW_ATE_address = 0x1;
    public static final byte DW_ATE_boolean = 0x2;
    public static final byte DW_ATE_float = 0x4;
    public static final byte DW_ATE_signed = 0x5;
    public static final byte DW_ATE_signed_char = 0x6;
    public static final byte DW_ATE_unsigned = 0x7;

    /*
     * CIE and FDE entries.
     */

    /* Full byte/word values. */
    public static final int DW_CFA_CIE_id = -1;
    @SuppressWarnings("unused") public static final int DW_CFA_FDE_id = 0;

    public static final byte DW_CFA_CIE_version = 1;

    /* Values encoded in high 2 bits. */
    public static final byte DW_CFA_advance_loc = 0x1;
    public static final byte DW_CFA_offset = 0x2;
    public static final byte DW_CFA_restore = 0x3;

    /* Values encoded in low 6 bits. */
    public static final byte DW_CFA_nop = 0x0;
    @SuppressWarnings("unused") public static final byte DW_CFA_set_loc1 = 0x1;
    public static final byte DW_CFA_advance_loc1 = 0x2;
    public static final byte DW_CFA_advance_loc2 = 0x3;
    public static final byte DW_CFA_advance_loc4 = 0x4;
    @SuppressWarnings("unused") public static final byte DW_CFA_offset_extended = 0x5;
    @SuppressWarnings("unused") public static final byte DW_CFA_restore_extended = 0x6;
    @SuppressWarnings("unused") public static final byte DW_CFA_undefined = 0x7;
    @SuppressWarnings("unused") public static final byte DW_CFA_same_value = 0x8;
    public static final byte DW_CFA_register = 0x9;
    public static final byte DW_CFA_def_cfa = 0xc;
    @SuppressWarnings("unused") public static final byte DW_CFA_def_cfa_register = 0xd;
    public static final byte DW_CFA_def_cfa_offset = 0xe;

    /*
     * Values used to build DWARF expressions and locations
     */
    public static final byte DW_OP_addr = 0x03;
    @SuppressWarnings("unused") public static final byte DW_OP_deref = 0x06;
    public static final byte DW_OP_dup = 0x12;
    public static final byte DW_OP_and = 0x1a;
    public static final byte DW_OP_not = 0x20;
    public static final byte DW_OP_plus = 0x22;
    public static final byte DW_OP_shl = 0x24;
    public static final byte DW_OP_shr = 0x25;
    public static final byte DW_OP_bra = 0x28;
    public static final byte DW_OP_eq = 0x29;
    public static final byte DW_OP_lit0 = 0x30;
    public static final byte DW_OP_reg0 = 0x50;
    public static final byte DW_OP_breg0 = 0x70;
    public static final byte DW_OP_regx = (byte) 0x90;
    public static final byte DW_OP_bregx = (byte) 0x92;
    public static final byte DW_OP_push_object_address = (byte) 0x97;
    public static final byte DW_OP_implicit_value = (byte) 0x9e;
    public static final byte DW_OP_stack_value = (byte) 0x9f;

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
    /*
     * The name of the type for header field hub which needs special case processing to remove tag
     * bits
     */
    public static final String HUB_TYPE_NAME = "java.lang.Class";

    private DwarfStrSectionImpl dwarfStrSection;
    private DwarfAbbrevSectionImpl dwarfAbbrevSection;
    private DwarfInfoSectionImpl dwarfInfoSection;
    private DwarfLocSectionImpl dwarfLocSection;
    private DwarfARangesSectionImpl dwarfARangesSection;
    private DwarfLineSectionImpl dwarfLineSection;
    private DwarfFrameSectionImpl dwarfFameSection;
    public final ELFMachine elfMachine;
    /**
     * Register used to hold the heap base.
     */
    private byte heapbaseRegister;
    /**
     * Register used to hold the current thread.
     */
    private byte threadRegister;

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
     * A collection of local variable properties associated with a generated method record,
     * potentially including a method which is abstract (hence why it is not indexed off the primary
     * range).
     */

    private final EconomicMap<MethodEntry, DwarfLocalProperties> methodLocalPropertiesIndex = EconomicMap.create();

    /**
     * A collection of local variable properties associated with an inlined subrange.
     */
    private final EconomicMap<Range, DwarfLocalProperties> rangeLocalPropertiesIndex = EconomicMap.create();

    public DwarfDebugInfo(ELFMachine elfMachine, ByteOrder byteOrder) {
        super(byteOrder);
        this.elfMachine = elfMachine;
        dwarfStrSection = new DwarfStrSectionImpl(this);
        dwarfAbbrevSection = new DwarfAbbrevSectionImpl(this);
        dwarfInfoSection = new DwarfInfoSectionImpl(this);
        dwarfLocSection = new DwarfLocSectionImpl(this);
        dwarfARangesSection = new DwarfARangesSectionImpl(this);
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
         * Index of debug_info section compilation unit for this class.
         */
        private int cuIndex;
        /**
         * index of debug_info section compilation unit for deopt target methods.
         */
        private int deoptCUIndex;
        /**
         * Index of the class entry's class_layout DIE in the debug_info section.
         */
        private int layoutIndex;
        /**
         * Index of the class entry's indirect layout DIE in the debug_info section.
         */
        private int indirectLayoutIndex;
        /**
         * Index into debug_line section for associated compilation unit.
         */
        private int lineIndex;
        /**
         * Size of line number info prologue region for associated compilation unit.
         */
        private int linePrologueSize;
        /**
         * Total size of line number info region for associated compilation unit.
         */
        private int lineSectionSize;
        /**
         * Map from field names to info section index for the field declaration.
         */
        private EconomicMap<String, Integer> fieldDeclarationIndex;
        /**
         * Map from method entries to associated abstract inline method index.
         */
        private EconomicMap<MethodEntry, Integer> abstractInlineMethodIndex;

        /**
         * Map from method entries to associated method local properties index.
         */
        private EconomicMap<MethodEntry, DwarfLocalProperties> methodLocalPropertiesIndex;

        DwarfClassProperties(StructureTypeEntry entry) {
            super(entry);
            this.cuIndex = -1;
            this.deoptCUIndex = -1;
            this.layoutIndex = -1;
            this.indirectLayoutIndex = -1;
            this.lineIndex = -1;
            this.linePrologueSize = -1;
            this.lineSectionSize = -1;
            fieldDeclarationIndex = null;
            abstractInlineMethodIndex = null;
            methodLocalPropertiesIndex = null;
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
         * The index in the info section at which the method's abstract inline declaration resides.
         */
        private int abstractInlineMethodIndex;

        DwarfMethodProperties() {
            methodDeclarationIndex = -1;
            abstractInlineMethodIndex = -1;
        }

        public int getMethodDeclarationIndex() {
            assert methodDeclarationIndex >= 0 : "unset declaration index";
            return methodDeclarationIndex;
        }

        public int getAbstractInlineMethodIndex() {
            assert abstractInlineMethodIndex >= 0 : "unset inline index";
            return abstractInlineMethodIndex;
        }

        public void setMethodDeclarationIndex(int pos) {
            assert methodDeclarationIndex == -1 || methodDeclarationIndex == pos : "bad declaration index";
            methodDeclarationIndex = pos;
        }

        public void setAbstractInlineMethodIndex(int pos) {
            assert abstractInlineMethodIndex == -1 || abstractInlineMethodIndex == pos : "bad inline index";
            abstractInlineMethodIndex = pos;
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

    private DwarfMethodProperties lookupMethodProperties(MethodEntry methodEntry) {
        DwarfMethodProperties methodProperties = methodPropertiesIndex.get(methodEntry);
        if (methodProperties == null) {
            methodProperties = addMethodProperties(methodEntry);
        }
        return methodProperties;
    }

    void setTypeIndex(TypeEntry typeEntry, int idx) {
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

    void setCUIndex(ClassEntry classEntry, int idx) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.cuIndex == -1 || classProperties.cuIndex == idx;
        classProperties.cuIndex = idx;
    }

    int getCUIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.cuIndex >= 0;
        return classProperties.cuIndex;
    }

    void setDeoptCUIndex(ClassEntry classEntry, int idx) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert (classProperties.deoptCUIndex == -1 || classProperties.deoptCUIndex == idx);
        classProperties.deoptCUIndex = idx;
    }

    int getDeoptCUIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.deoptCUIndex >= 0;
        return classProperties.deoptCUIndex;
    }

    void setLayoutIndex(ClassEntry classEntry, int idx) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.layoutIndex == -1 || classProperties.layoutIndex == idx;
        classProperties.layoutIndex = idx;
    }

    int getLayoutIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.layoutIndex >= 0;
        return classProperties.layoutIndex;
    }

    void setIndirectLayoutIndex(ClassEntry classEntry, int idx) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.indirectLayoutIndex == -1 || classProperties.indirectLayoutIndex == idx;
        classProperties.indirectLayoutIndex = idx;
    }

    int getIndirectLayoutIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.indirectLayoutIndex >= 0;
        return classProperties.indirectLayoutIndex;
    }

    void setLineIndex(ClassEntry classEntry, int idx) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert (classProperties.lineIndex == -1 || classProperties.lineIndex == idx);
        classProperties.lineIndex = idx;
    }

    public int getLineIndex(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        /* line index may be fetched without being set */
        assert classProperties.lineIndex >= -1;
        return classProperties.lineIndex;
    }

    public void setLinePrologueSize(ClassEntry classEntry, int prologueSize) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert (classProperties.linePrologueSize == -1 || classProperties.linePrologueSize == prologueSize);
        classProperties.linePrologueSize = prologueSize;
    }

    public int getLinePrologueSize(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.linePrologueSize >= 0;
        return classProperties.linePrologueSize;
    }

    public void setLineSectionSize(ClassEntry classEntry, int totalSize) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert (classProperties.lineSectionSize == -1 || classProperties.lineSectionSize == totalSize);
        classProperties.lineSectionSize = totalSize;
    }

    public int getLineSectionSize(ClassEntry classEntry) {
        DwarfClassProperties classProperties;
        classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        assert classProperties.lineSectionSize >= 0;
        return classProperties.lineSectionSize;
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

    public void setAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry, int pos) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        EconomicMap<MethodEntry, Integer> abstractInlineMethodIndex = classProperties.abstractInlineMethodIndex;
        if (abstractInlineMethodIndex == null) {
            classProperties.abstractInlineMethodIndex = abstractInlineMethodIndex = EconomicMap.create();
        }
        if (abstractInlineMethodIndex.get(methodEntry) != null) {
            assert abstractInlineMethodIndex.get(methodEntry) == pos : classEntry.getTypeName() + " & " + methodEntry.getSymbolName();
        } else {
            abstractInlineMethodIndex.put(methodEntry, pos);
        }
    }

    public int getAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry) {
        DwarfClassProperties classProperties = lookupClassProperties(classEntry);
        assert classProperties.getTypeEntry() == classEntry;
        EconomicMap<MethodEntry, Integer> abstractInlineMethodIndex = classProperties.abstractInlineMethodIndex;
        assert abstractInlineMethodIndex != null : classEntry.getTypeName() + " & " + methodEntry.getSymbolName();
        assert abstractInlineMethodIndex.get(methodEntry) != null : classEntry.getTypeName() + " & " + methodEntry.getSymbolName();
        return abstractInlineMethodIndex.get(methodEntry);
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

    private DwarfLocalProperties addRangeLocalProperties(Range range) {
        DwarfLocalProperties localProperties = new DwarfLocalProperties();
        rangeLocalPropertiesIndex.put(range, localProperties);
        return localProperties;
    }

    public DwarfLocalProperties lookupLocalProperties(ClassEntry classEntry, MethodEntry methodEntry) {
        EconomicMap<MethodEntry, DwarfLocalProperties> map;
        if (classEntry == null) {
            map = methodLocalPropertiesIndex;
        } else {
            DwarfClassProperties classProperties = lookupClassProperties(classEntry);
            assert classProperties != null;
            map = classProperties.methodLocalPropertiesIndex;
            if (map == null) {
                map = classProperties.methodLocalPropertiesIndex = EconomicMap.create();
            }
        }
        DwarfLocalProperties localProperties = map.get(methodEntry);
        if (localProperties == null) {
            localProperties = new DwarfLocalProperties();
            map.put(methodEntry, localProperties);
        }
        return localProperties;
    }

    public void setMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugLocalInfo localInfo, int index) {
        DwarfLocalProperties localProperties = lookupLocalProperties(classEntry, methodEntry);
        localProperties.setIndex(localInfo, index);
    }

    public int getMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugLocalInfo localinfo) {
        DwarfLocalProperties localProperties = lookupLocalProperties(classEntry, methodEntry);
        assert localProperties != null : "get of non-existent local index";
        int index = localProperties.getIndex(localinfo);
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
