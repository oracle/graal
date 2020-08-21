/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import com.oracle.objectfile.LayoutDecision;
import org.graalvm.compiler.debug.DebugContext;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_data_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_layout;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_pointer;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_typedef;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_array_unit;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_builtin_unit;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_layout;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_pointer;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_typedef;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_unit1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_class_unit2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration3;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_field_declaration4;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_header_field;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_implementor;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_layout;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_pointer;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_interface_typedef;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_declaration1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_declaration2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_location;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration3;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_object_header;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_primitive_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_static_field_location;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_super_reference;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_void_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ARANGES_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_accessibility;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_artificial;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_bit_size;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_byte_size;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_comp_dir;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_containing_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_data_member_location;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_decl_file;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_declaration;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_encoding;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_external;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_hi_pc;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_language;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_location;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_low_pc;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_name;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_null;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_specification;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_stmt_list;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_AT_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CHILDREN_no;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CHILDREN_yes;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_addr;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_data1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_data2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_data4;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_expr_loc;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_flag;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_null;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_ref_addr;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FORM_strp;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_array_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_base_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_class_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_compile_unit;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_formal_parameter;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_inheritance;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_member;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_pointer_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_structure_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_subprogram;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_typedef;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_union_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_unspecified_type;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_TAG_variable;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.Dw_AT_object_pointer;

/**
 * Section generator for debug_abbrev section.
 */
public class DwarfAbbrevSectionImpl extends DwarfSectionImpl {

    public DwarfAbbrevSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_ABBREV_SECTION_NAME;
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();
        /*
         * An abbrev table contains abbrev entries for one or more CUs. the table includes a
         * sequence of abbrev entries each of which defines a specific DIE layout employed to
         * describe some DIE in a CU. a table is terminated by a null entry.
         *
         * A null entry consists of just a 0 abbrev code.
         *
         * <ul>
         *
         * <li><code>LEB128 abbrev_code; ...... == 0</code>
         *
         * </ul>
         *
         * Non-null entries have the following format.
         *
         * <ul>
         *
         * <li><code>LEB128 abbrev_code; ......unique noncode for this layout != 0</code>
         *
         * <li><code>LEB128 tag; .............. defines the type of the DIE (class, subprogram, var
         * etc)</code>
         *
         * <li><code>uint8 has_chldren; ....... is the DIE followed by child DIEs or a sibling
         * DIE</code>
         *
         * <li><code>attribute_spec* .......... zero or more attributes</code>
         *
         * <li><code>null_attribute_spec ...... terminator</code>
         *
         * </ul>
         *
         * An attribute_spec consists of an attribute name and form
         *
         * <ul>
         *
         * <li><code>LEB128 attr_name; ........ 0 for the null attribute name</code>
         *
         * <li><code>LEB128 attr_form; ........ 0 for the null attribute form</code>
         *
         * </ul>
         *
         * For the moment we only use one abbrev table for all CUs. It contains the following DIES:
         *
         * <ul>
         *
         * <li>Level 0 DIEs
         *
         * <li><code>code = null, TAG = null<code> - empty terminator
         *
         * <li><code>code = builtin_unit, TAG = compile_unit<code> - Java primitive and header type
         * compile unit
         *
         * <li><code>code = class_unit1/2, tag = compile_unit<code> - Java instance type compile
         * unit
         *
         * <li><code>code = array_unit, tag = compile_unit<code> - Java array type compile unit
         *
         * </ul>
         *
         * <ul>
         *
         * <li>Level 1 DIES
         *
         * <li><code>code = primitive_type, tag = base_type<code> - Java primitive type (non-void)
         *
         * <li><code>code = void_type, tag = unspecified_type<code> - Java void type
         *
         * <li><code>code = object_header, tag = structure_type<code> - Java object header
         *
         * <li><code>code = class_layout, tag = class_type, parent = class_unit<code> - Java
         * instance type structure definition
         *
         * <li><code>code = class_pointer, tag = pointer_type, parent = class_unit<code> - Java
         * instance ref type
         *
         * <li><code>code = class_typedef, tag = typedef, parent = class_unit<code> - Java instance
         * ref typedef
         *
         * <li><code>code = method_location, tag = subprogram , parent = class_unit<code> - Java
         * method code definition (i.e. location of code)
         *
         * <li><code>code = static_field_location, tag = variable, parent = class_unit<code> - Java
         * static field definition (i.e. location of data)
         *
         * <li><code>code = array_layout, tag = structure_type, parent = array_unit<code> - Java
         * array type structure definition
         *
         * <li><code>code = array_pointer, tag = pointer_type, parent = array_unit<code> - Java
         * array ref type
         *
         * <li><code>code = array_typedef, tag = typedef, parent = array_unit<code> - Java array ref
         * typedef
         *
         * <li><code>code = interface_layout, tag = union_type, parent = class_unit<code> - Java
         * array type structure definition
         *
         * <li><code>code = interface_pointer, tag = pointer_type, parent = class_unit<code> - Java
         * interface ref type
         *
         * <li><code>code = interface_typedef, tag = typedef, parent = class_unit<code> - Java array
         * ref typedef
         *
         * </ul>
         *
         * <ul>
         *
         * <li> Level 2 DIEs
         *
         * <li><code>code = header_field, tag = member, parent = object_header<code> - object/array
         * header field
         *
         * <li><code>code == method_declaration1/2, tag == subprogram, parent = class_layout</code>
         *
         * <li><code>code = field_declaration1/2/3/4, tag = member, parent =
         * object_header/class_layout<code> - object header or instance field declaration (i.e.
         * specification of properties)
         *
         * <li><code>code == super_reference, tag == inheritance, parent =
         * class_layout/array_layout</code> - reference to super class layout or to appropriate
         * header struct for {code java.lang.Object} or arrays.
         *
         * <li><code>code == interface_implementor, tag == member, parent = interface_layout</code>
         * - union member typed using class layout of a given implementing class
         *
         * </ul>
         *
         * <li> Level 2/3 DIEs
         *
         * <li><code>code == method_parameter_declaration1/2/3, tag == formal_parameter, parent =
         * method_declaration1/2, method_location</code> - details of method parameters
         *
         * Details of each specific DIE contents are as follows:
         *
         * Primitive Types: For each non-void Java primitive type there is a level 0 DIE defining a
         * base type
         *
         * <ul>
         *
         * <li><code>abbrev_code == primitive_type, tag == DW_TAG_base_type, no_children</code>
         *
         * <li><code>DW_AT_byte_size : ... DW_FORM_data1</code> (or data2 ???)
         *
         * <li><code>DW_AT_bit_size : ... DW_FORM_data1</code> (or data2 ???)
         *
         * <li><code>DW_AT_encoding : .... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_name : ........ DW_FORM_strp</code>
         *
         * </ul>
         *
         * <ul>
         *
         * The void type is defined as an unspecified type
         * 
         * <li><code>abbrev_code == void_type, tag == DW_TAG_unspecified_type, no_children</code>
         *
         * <li><code>DW_AT_name : ........ DW_FORM_strp</code>
         *
         * </ul>
         *
         * Header: There is a level 0 DIE defining structure types used to define the various types
         * of header structure embedded at the start of every instance or array. All instances embed
         * the same object header. Array headers embed the object header as a parent type, allowing
         * an array to be viewed as a type of object. Multiple array headers structures are defined
         * to allow for the possibility of different amounts of padding required between the array
         * header fields and the array elements that are allocate at the end of the header. Child
         * DIEs are employed to define the name, type and layout of fields in each header.
         *
         * <ul>
         *
         * <li><code>abbrev_code == object_header, tag == DW_TAG_structure_type, has_children</code>
         *
         * <li><code>DW_AT_name : ......... DW_FORM_strp</code> "oop"
         *
         * <li><code>DW_AT_byte_size : ... DW_FORM_data1</code> "oop"
         *
         * </ul>
         *
         * Header Data: A level 1 DIE of type member is used to describe the fields of both object
         * and array headers. This includes the type tag and other tag bits in all objects, the
         * length field in all arrays and any padding bytes needed to complete the layout.
         *
         * <ul>
         *
         * <li><code>abbrev_code = header_field, tag == DW_TAG_member, no_children</code>
         *
         * <li><code>Dw_AT_name : ................... DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_type : ................... DW_FORM_ref_addr</code>
         *
         * <li><code>Dw_AT_data_member_location : ... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_accessibility : .......... DW_FORM_data1</code>
         *
         * </ul>
         *
         * Instance Classes: For each class there is a level 0 DIE defining the class compilation
         * unit
         *
         * <ul>
         *
         * <li><code>abbrev_code == class_unit1/2, tag == DW_TAG_compilation_unit,
         * has_children</code>
         *
         * <li><code>DW_AT_language : ... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_name : ....... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_comp_dir : ... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_low_pc : ..... DW_FORM_address</code> ??? omit this so method entries do
         * not need to occupy full range
         *
         * <li><code>DW_AT_hi_pc : ...... DW_FORM_address</code> ??? omit this so method entries do
         * not need to occupy full range
         *
         * <li><code>DW_AT_stmt_list : .. DW_FORM_data4</code> n.b only for <code>abbrev-code ==
         * class_unit1</code>
         *
         * </ul>
         *
         * Instance Class Structure: Each class_unit DIE contains a series of level 1 DIEs. The
         * first one describes the class layout:
         *
         * <ul>
         *
         * <li><code>abbrev_code == class_layout, tag == DW_TAG_class_type, has_children</code>
         *
         * <li><code>Dw_AT_name : ........ DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1/2</code> ??? how many bytes do we really
         * need?
         *
         * <li><code>Dw_AT_decl_file : ... DW_FORM_data1/2</code> ??? how many bytes do we really
         * need?
         *
         * <li><code>Dw_AT_decl_line : ... DW_FORM_data1/2</code> ??? how many bytes do we really
         * need?
         *
         * <li><code>Dw_AT_containing_type : ..... DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * Instance Class members: The level 1 class_layout DIE includes a level 2 child for each of
         * the class's methods and fields. The first type declares a method but omits details of the
         * location of the code that implements the method. The second type declares an instance or
         * static field. The class_layout DIE also contains an level 2 DIE specifying the type from
         * which it inherits superclass structure. In the case of class Object structure is
         * inherited from the object header structure type.
         *
         * n.b. Code implementation details for each method are provided in an auxiliary level 1
         * method_location DIE that follows the class_unit DIE. Instance field declarations need no
         * auxiliary level 1 DIE as all relevant details, including size and offset in the instance,
         * are specified in the level 2 field declaration DIE. Static field locations are provided
         * in an auxiliary level 1 DIE (with tag variable) that follows the class_unit DIE.
         *
         * <ul>
         *
         * <li><code>abbrev_code == method_declaration1/2, tag == DW_TAG_subprogram,
         * has_children</code>
         *
         * <li><code>DW_AT_external : .......... DW_FORM_flag</code> ??? for all?
         *
         * <li><code>Dw_AT_name : .............. DW_FORM_strp</code>
         *
         * <li><code>DW_AT_decl_file : ......... DW_FORM_data1/2</code> ??? how many bytes
         *
         * <li><code>DW_AT_decl_line : ......... DW_FORM_data1/2<code> ??? how many bytes
         *
         * <li><code>Dw_AT_linkage_name : ...... DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_type : .............. DW_FORM_ref_addr</code> (optional!!)
         *
         * <li><code>DW_AT_artificial : ........ DW_FORM_flag</code>
         *
         * <li><code>DW_AT_accessibility : ..... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_declaration : ....... DW_FORM_flag</code>
         *
         * <li><code>Dw_AT_object_pointer : .... DW_FORM_ref_addr<code> (only for
         * method_declaration1 points to param 0 DIE)
         *
         * <li><code>DW_AT_virtuality : ........ DW_FORM_data1<code> (for override methods)
         *
         * <li><code>DW_AT_containing_type : ... DW_FORM_ref_addr</code> (for override methods)
         *
         * </ul>
         *
         * <ul>
         *
         * <li><code>abbrev_code == field_declaration1/2/3/4, tag == DW_TAG_member,
         * no_children</code>
         *
         * <li><code>Dw_AT_name : ................... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_decl_file : .............. DW_FORM_data1/2</code> (only for
         * field_declaration2/4)
         *
         * <li><code>DW_AT_decl_line : .............. DW_FORM_data1/2</code> (only for
         * field_declaration2/4)
         *
         * <li><code>Dw_AT_type : ................... DW_FORM_ref_addr</code>
         *
         * <li><code>Dw_AT_data_member_location : ... DW_FORM_data1/2</code> (only for
         * field_declaration1/2 instance) ??? how many bytes?
         *
         * <li><code>Dw_AT_artificial : ............. DW_FORM_flag</code> ?? do we need this?
         *
         * <li><code>Dw_AT_accessibility : .......... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_external : ............... DW_FORM_flag</code> (only for
         * field_declaration3/4 static)
         *
         * <li><code>Dw_AT_declaration : ............ DW_FORM_flag</code> (only for
         * field_declaration3/4 static)
         *
         * </ul>
         *
         * <ul>
         *
         * <li><code>abbrev_code == super_reference, tag == DW_TAG_inheritance, no_children</code>
         *
         * <li><code>Dw_AT_type : ................... DW_FORM_ref_addr</code>
         *
         * <li><code>Dw_AT_data_member_location : ... DW_FORM_data1/2</code>
         *
         * <li><code>Dw_AT_accessibility :........... DW_FORM_data1</code>
         *
         * </ul>
         *
         * Method Parameters: Level 2 method_declaration DIEs may include level 3 DIEs that describe
         * their parameters
         *
         * <ul>
         *
         * <li><code>abbrev_code == method_parameter_declaration1/2/3, tag ==
         * DW_TAG_formal_parameter, no_children</code>
         *
         * <li><code>Dw_AT_name : ... DW_FORM_strp</code> (may be empty string)
         *
         * <li><code>Dw_AT_file : ... DW_FORM_data1/2</code> (optional only for
         * method_parameter_declaration2)
         *
         * <li><code>Dw_AT_line : ... DW_FORM_data1/2</code> (optional only for
         * method_parameter_declaration2)
         *
         * <li><code>Dw_AT_type : ... DW_FORM_ref_addr</code>
         *
         * <li><code>Dw_AT_artificial : ... DW_FORM_flag</code> (optional only for
         * method_parameter_declaration1 $this, $access)
         *
         * <li><code>Dw_AT_location(list???) : ... DW_FORM_exprloc</code>
         *
         * </ul>
         *
         * Instance Class Reference Types: A level 1 class_layout DIE is followed by a DIE defining
         * a pointer to the class and a second DIE that defines a typedef for that pointer using the
         * Java class name as the typedef name. This reflects the fact that a Java object reference
         * is actually implemented as a pointer.
         *
         * n.b. the name used in the class_layout DIE is not the Java class name. It is derived by
         * appending '_' to the Java class name (preceding the package prefix). So this means that,
         * for example, the Java type java.lang.Object appears to gdb to be defined as follows
         *
         * <code>typedef struct _java.lang.Object { ... } *java.lang.Object;</code>
         *
         * <ul>
         *
         * <li><code>abbrev_code == class_pointer, tag == DW_TAG_pointer_type, no_children</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_type : ........ DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * <ul>
         *
         * <li><code>abbrev_code == class_typedef, tag == DW_TAG_typedef, no_children</code>
         *
         * <li><code>Dw_AT_name : ... DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_type : ........ DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * Method Code Locations: For each method within a class there is a corresponding level 1
         * DIE providing details of the location of the compiled code for the method. This DIE
         * should inherit attributes from the method_definition DIE referenced from its
         * specification attribute without the need to repeat them, including attributes specified
         * in child DIEs of the method_definition. However, it is actually necessary to replicate
         * the method_parameter DIEs as children of this DIE because gdb does not carry these
         * attributes across from the specification DIE.
         *
         * <ul>
         *
         * <li><code>abbrev_code == DW_ABBREV_CODE_method_location, tag == DW_TAG_subprogram,
         * has_children</code>
         *
         * <li><code>DW_AT_low_pc : .......... DW_FORM_addr</code>
         *
         * <li><code>DW_AT_hi_pc : ........... DW_FORM_addr</code> (or data8???)
         *
         * <li><code>DW_AT_external : ........ DW_FORM_flag</code>
         *
         * <li><code>DW_AT_specification : ... DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * Static Field Locations: For each static field within the class there is a level 1 DIE
         * providing details of the static field location
         *
         * <ul>
         *
         * <li><code>abbrev_code == static_field_location, tag == DW_TAG_variable,
         * no_children</code>
         *
         * <li><code>DW_AT_specification : ... DW_FORM_ref_addr</code>
         *
         * <li><code>DW_AT_linkage_name : .... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_location : ........ DW_FORM_exprloc</code>
         *
         * </ul>
         *
         * Arrays: For each array type there is a level 0 DIE defining the array compilation unit
         *
         * <ul>
         *
         * <li><code>abbrev_code == array_unit, tag == DW_TAG_compilation_unit, has_children</code>
         *
         * <li><code>DW_AT_language : ... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_name : ....... DW_FORM_strp</code> ??? what name???
         *
         * </ul>
         *
         * Array Structure: Each array_unit DIE contains three level 1 DIEs. The first one describes
         * the array layout:
         *
         * <ul>
         *
         * <li><code>abbrev_code == array_layout, tag == DW_TAG_class_type, has_children</code>
         *
         * <li><code>Dw_AT_name : ........ DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1/2</code> size up to base of embedded array
         * elements?
         *
         * </ul>
         *
         * The second DIE defines the array reference type as a pointer to the underlying structure
         * type
         *
         * <ul>
         *
         * <li><code>abbrev_code == array_pointer, tag == DW_TAG_pointer_type, no_children</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_type : ........ DW_FORM_ref_addr</code>
         *
         * The third DIE defines the array type name as a typedef for the pointer type
         *
         * <ul>
         *
         * <li><code>abbrev_code == array_typedef, tag == DW_TAG_typedef, no_children</code>
         *
         * <li><code>Dw_AT_name : ....... DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_type : ........ DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * n.b. the name used in the array_layout DIE is not the Java array name. It is derived by
         * appending '_' to the Java array name (preceding the package prefix). So this means that,
         * for example, the Java type java.lang.String[] appears to gdb to be defined as follows
         *
         * <code>typedef struct _java.lang.String[] { ... } *java.lang.String[];</code>
         *
         * Array members: The level 1 array_layout DIE includes level 2 child DIEs with tag member
         * that describe the layout of the array. header_field DIEs are used to declare members of
         * the array header, including the zero length array data member tat dfollows other header
         * fields. An auxiliary array_data_type DIE with tag array_type also occurs as a child DIE
         * defining the type for the array data member.
         *
         * <ul>
         *
         * <li><code>abbrev_code == array_data_type, tag == DW_TAG_array_type, no_children</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_type : ........ DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * Interfaces: For each interface there is a level 0 class_unit DIE defining the interface
         * compilation unit.
         *
         * Interface Layout and Reference Types: The level 0 class_unit DIE for an interface is
         * followed by a level 1 DIE defining the interface layout as a union of all the layouts for
         * the classes which implement the interface. Two more level 1 DIEs define the a pointer to
         * this layout type and a typedef that names the interface pointer type using the Java
         * interface name.
         *
         * n.b. the name used in the interface_layout DIE is not the Java interface name. It is
         * derived by appending '_' to the Java class name (preceding the package prefix). So this
         * means that, for example, the Java interface java.lang.CharSequence appears to gdb to be
         * defined as follows
         *
         * <code>typedef union _java.lang.CharSequence { ... } *java.lang.CharSequence; </code>
         *
         * <ul>
         *
         * <li><code>abbrev_code == interface_layout, tag == union_type, has_children</code>
         *
         * <li><code>Dw_AT_name : ....... DW_FORM_strp</code>
         *
         * </ul>
         *
         * <ul>
         *
         * <li><code>abbrev_code == interface_pointer, tag == pointer_type, has_children</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_TYPE : ....... DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * The union type embeds level 2 DIEs with tag member. There is a member for each
         * implementing class, typed using the layout.
         *
         * <ul>
         *
         * <li><code>abbrev_code == interface_implementor, tag == member, no_children</code>
         *
         * <li><code>Dw_AT_name : ................... DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_type : ................... DW_FORM_ref_addr</code>
         *
         * <li><code>Dw_AT_accessibility : .......... DW_FORM_data1</code>
         *
         * </ul>
         *
         * The member name is constructed by appending an '_' to the Java* name of the implementing
         * class. So, this means that, for example, the Java interface java.lang.CharSequence will
         * include members for String, StringBuffer etc as follows
         *
         * <code>typedef union _java.lang.CharSequence { _java.lang.String _java.lang.String;
         * _java.lang.StringBuffer _java.lang.StringBuffer; ... } *java.lang.CharSequence;</code>
         *
         */

        int pos = 0;
        pos = writeAbbrevs(null, null, pos);

        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        pos = writeAbbrevs(context, buffer, pos);

        assert pos == size;
    }

    public int writeAbbrevs(DebugContext context, byte[] buffer, int pos) {
        pos = writeBuiltInUnitAbbrev(context, buffer, pos);
        pos = writeClassUnitAbbrevs(context, buffer, pos);
        pos = writeArrayUnitAbbrev(context, buffer, pos);

        pos = writePrimitiveTypeAbbrev(context, buffer, pos);
        pos = writeVoidTypeAbbrev(context, buffer, pos);
        pos = writeObjectHeaderAbbrev(context, buffer, pos);

        pos = writeClassLayoutAbbrev(context, buffer, pos);
        pos = writeClassReferenceAbbrev(context, buffer, pos);
        pos = writeMethodDeclarationAbbrevs(context, buffer, pos);
        pos = writeFieldDeclarationAbbrevs(context, buffer, pos);
        pos = writeArrayLayoutAbbrev(context, buffer, pos);
        pos = writeArrayReferenceAbbrev(context, buffer, pos);
        pos = writeInterfaceLayoutAbbrev(context, buffer, pos);
        pos = writeInterfaceReferenceAbbrev(context, buffer, pos);

        pos = writeHeaderFieldAbbrev(context, buffer, pos);
        pos = writeArrayDataTypeAbbrev(context, buffer, pos);
        pos = writeMethodLocationAbbrev(context, buffer, pos);
        pos = writeStaticFieldLocationAbbrev(context, buffer, pos);
        pos = writeSuperReferenceAbbrev(context, buffer, pos);
        pos = writeInterfaceImplementorAbbrev(context, buffer, pos);

        pos = writeParameterDeclarationAbbrevs(context, buffer, pos);
        return pos;
    }

    private int writeAttrType(long code, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putSLEB(code, scratch, 0);
        } else {
            return putSLEB(code, buffer, pos);
        }
    }

    private int writeAttrForm(long code, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putSLEB(code, scratch, 0);
        } else {
            return putSLEB(code, buffer, pos);
        }
    }

    private int writeBuiltInUnitAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DW_ABBREV_CODE_builtin_unit, buffer, pos);
        pos = writeTag(DW_TAG_compile_unit, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_language, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeClassUnitAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        // class compile unit no line info
        pos = writeClassUnitAbbrev(context, DW_ABBREV_CODE_class_unit1, buffer, pos);
        // class compile unit with line info
        pos = writeClassUnitAbbrev(context, DW_ABBREV_CODE_class_unit2, buffer, pos);
        return pos;
    }

    private int writeClassUnitAbbrev(DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DW_TAG_compile_unit, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_language, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_comp_dir, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_class_unit1) {
            pos = writeAttrType(DW_AT_stmt_list, buffer, pos);
            pos = writeAttrForm(DW_FORM_data4, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayUnitAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_array_unit, buffer, pos);
        pos = writeTag(DW_TAG_compile_unit, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_language, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writePrimitiveTypeAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DW_ABBREV_CODE_primitive_type, buffer, pos);
        pos = writeTag(DW_TAG_base_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_bit_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_encoding, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeVoidTypeAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DW_ABBREV_CODE_void_type, buffer, pos);
        pos = writeTag(DW_TAG_unspecified_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeObjectHeaderAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DW_ABBREV_CODE_object_header, buffer, pos);
        pos = writeTag(DW_TAG_structure_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeClassLayoutAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_class_layout, buffer, pos);
        pos = writeTag(DW_TAG_class_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        pos = writeAttrType(DW_AT_decl_file, buffer, pos);
        pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        // at present we definitely don't have line numbers
        // pos = writeAttrType(DW_AT_decl_line, buffer, pos);
        // pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        // n.b. the containing_type attribute is not strict DWARF but gdb expects it
        // we also add an inheritance member with the same info
        pos = writeAttrType(DW_AT_containing_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeClassReferenceAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        // first the basic pointer type for a pointer to the class struct type
        pos = writeAbbrevCode(DW_ABBREV_CODE_class_pointer, buffer, pos);
        pos = writeTag(DW_TAG_pointer_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);

        // we wll also need a typedef to advertise the class name as the pointer type
        pos = writeAbbrevCode(DW_ABBREV_CODE_class_typedef, buffer, pos);
        pos = writeTag(DW_TAG_typedef, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeMethodDeclarationAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeMethodDeclarationAbbrev(context, DW_ABBREV_CODE_method_declaration1, buffer, pos);
        pos = writeMethodDeclarationAbbrev(context, DW_ABBREV_CODE_method_declaration2, buffer, pos);
        return pos;
    }

    private int writeMethodDeclarationAbbrev(DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DW_TAG_subprogram, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_external, buffer, pos);
        pos = writeAttrForm(DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_decl_file, buffer, pos);
        pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        // pos = writeAttrType(DW_AT_decl_line, buffer, pos);
        // pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        // pos = writeAttrType(Dw_AT_linkage_name, buffer, pos); // not in DWARF2
        // pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DW_AT_artificial, buffer, pos);
        pos = writeAttrForm(DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_declaration, buffer, pos);
        pos = writeAttrForm(DW_FORM_flag, buffer, pos);
        // pos = writeAttrType(DW_AT_virtuality, buffer, pos);
        // pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_containing_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_method_declaration1) {
            pos = writeAttrType(Dw_AT_object_pointer, buffer, pos);
            pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeFieldDeclarationAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        // instance field no line and file
        pos = writeFieldDeclarationAbbrev(context, DW_ABBREV_CODE_field_declaration1, buffer, pos);
        // instance field with line and file
        pos = writeFieldDeclarationAbbrev(context, DW_ABBREV_CODE_field_declaration2, buffer, pos);
        // static field no line and file
        pos = writeFieldDeclarationAbbrev(context, DW_ABBREV_CODE_field_declaration3, buffer, pos);
        // static field with line and file
        pos = writeFieldDeclarationAbbrev(context, DW_ABBREV_CODE_field_declaration4, buffer, pos);
        return pos;
    }

    private int writeFieldDeclarationAbbrev(DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DW_TAG_member, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        // we may not have a file and line for a field
        if (abbrevCode == DW_ABBREV_CODE_field_declaration2 || abbrevCode == DW_ABBREV_CODE_field_declaration4) {
            pos = writeAttrType(DW_AT_decl_file, buffer, pos);
            pos = writeAttrForm(DW_FORM_data2, buffer, pos);
            // at present we definitely don't have line numbers
            // pos = writeAttrType(DW_AT_decl_line, buffer, pos);
            // pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        }
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_field_declaration1 || abbrevCode == DW_ABBREV_CODE_field_declaration2) {
            // instance fields have a member offset relocated relative to the heap base register
            pos = writeAttrType(DW_AT_data_member_location, buffer, pos);
            pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        }
        pos = writeAttrType(DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        // static fields are only declared here and are external
        if (abbrevCode == DW_ABBREV_CODE_field_declaration3 || abbrevCode == DW_ABBREV_CODE_field_declaration4) {
            pos = writeAttrType(DW_AT_external, buffer, pos);
            pos = writeAttrForm(DW_FORM_flag, buffer, pos);
            pos = writeAttrType(DW_AT_declaration, buffer, pos);
            pos = writeAttrForm(DW_FORM_flag, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayLayoutAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_array_layout, buffer, pos);
        pos = writeTag(DW_TAG_structure_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayReferenceAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_array_pointer, buffer, pos);
        pos = writeTag(DW_TAG_pointer_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);

        // we wll also need a typedef to advertise the array name as the pointer type
        pos = writeAbbrevCode(DW_ABBREV_CODE_array_typedef, buffer, pos);
        pos = writeTag(DW_TAG_typedef, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeInterfaceLayoutAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_interface_layout, buffer, pos);
        pos = writeTag(DW_TAG_union_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeInterfaceReferenceAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_interface_pointer, buffer, pos);
        pos = writeTag(DW_TAG_pointer_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);

        // we wll also need a typedef to advertise the interface name as the pointer type
        pos = writeAbbrevCode(DW_ABBREV_CODE_interface_typedef, buffer, pos);
        pos = writeTag(DW_TAG_typedef, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeInterfaceImplementorAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_interface_implementor, buffer, pos);
        pos = writeTag(DW_TAG_member, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeNarrowLayoutAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        return pos;
    }

    private int writeHeaderFieldAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_header_field, buffer, pos);
        pos = writeTag(DW_TAG_member, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_name, buffer, pos);
        pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DW_AT_data_member_location, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayDataTypeAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_array_data_type, buffer, pos);
        pos = writeTag(DW_TAG_array_type, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeMethodLocationAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DW_ABBREV_CODE_method_location, buffer, pos);
        pos = writeTag(DW_TAG_subprogram, buffer, pos);
        pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DW_AT_external, buffer, pos);
        pos = writeAttrForm(DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DW_AT_specification, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeStaticFieldLocationAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_static_field_location, buffer, pos);
        pos = writeTag(DW_TAG_variable, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_specification, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        // pos = writeAttrType(DW_AT_linkage_name, buffer, pos);
        // pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DW_AT_location, buffer, pos);
        pos = writeAttrForm(DW_FORM_expr_loc, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeSuperReferenceAbbrev(DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DW_ABBREV_CODE_super_reference, buffer, pos);
        pos = writeTag(DW_TAG_inheritance, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DW_AT_data_member_location, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos); // = offset? in which segment though?
        pos = writeAttrType(DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DW_FORM_data1, buffer, pos); // = offset? in which segment though?
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeParameterDeclarationAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeParameterDeclarationAbbrev(context, DW_ABBREV_CODE_method_parameter_declaration1, buffer, pos);
        pos = writeParameterDeclarationAbbrev(context, DW_ABBREV_CODE_method_parameter_declaration2, buffer, pos);
        pos = writeParameterDeclarationAbbrev(context, DW_ABBREV_CODE_method_parameter_declaration3, buffer, pos);
        return pos;
    }

    private int writeParameterDeclarationAbbrev(DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DW_TAG_formal_parameter, buffer, pos);
        pos = writeFlag(DW_CHILDREN_no, buffer, pos);
        // pos = writeAttrType(DW_AT_name, buffer, pos);
        // pos = writeAttrForm(DW_FORM_strp, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_method_parameter_declaration2) {
            // don't always have a file name and line numbers are not yet available
            pos = writeAttrType(DW_AT_decl_file, buffer, pos);
            pos = writeAttrForm(DW_FORM_data2, buffer, pos);
            // pos = writeAttrType(DW_AT_decl_line, buffer, pos);
            // pos = writeAttrForm(DW_FORM_data2, buffer, pos);
        }
        pos = writeAttrType(DW_AT_type, buffer, pos);
        pos = writeAttrForm(DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_method_parameter_declaration1) {
            // only this parameter is artificial and it has no line
            pos = writeAttrType(DW_AT_artificial, buffer, pos);
            pos = writeAttrForm(DW_FORM_flag, buffer, pos);
        }
        // don't yet have locations for method parameters
        // not even at the start of the method
        // pos = writeAttrType(DW_AT_location, buffer, pos);
        // pos = writeAttrForm(DW_FORM_data4, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DW_AT_null, buffer, pos);
        pos = writeAttrForm(DW_FORM_null, buffer, pos);
        return pos;
    }

    /**
     * The debug_abbrev section depends on debug_aranges section.
     */
    private static final String TARGET_SECTION_NAME = DW_ARANGES_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.SIZE
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
