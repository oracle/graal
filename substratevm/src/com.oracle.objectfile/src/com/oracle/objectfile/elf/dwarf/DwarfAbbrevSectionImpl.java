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

import com.oracle.objectfile.LayoutDecision;
import org.graalvm.compiler.debug.DebugContext;

/**
 * Section generator for debug_abbrev section.
 */
public class DwarfAbbrevSectionImpl extends DwarfSectionImpl {

    public DwarfAbbrevSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DwarfDebugInfo.DW_ABBREV_SECTION_NAME;
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
         * <li><code>code = class_unit1/2/3, tag = compile_unit<code> - Java instance type compile
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
         * <li><code>code = method_location, tag = subprogram , parent = class_unit<code> - Java
         * method code definition (i.e. location of code)
         *
         * <li><code>code = abstract_inline_method, tag = subprogram , parent = class_unit<code> -
         * Java abstract inline method (i.e. proxy for method definition referenced by concrete
         * inline instance)
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
         * <li><code>code = interface_layout, tag = union_type, parent = class_unit<code> - Java
         * array type structure definition
         *
         * <li><code>code = interface_pointer, tag = pointer_type, parent = class_unit<code> - Java
         * interface ref type
         *
         * <li><code>code = indirect_layout, tag = class_type, parent = class_unit, array_unit,
         * interface_unit<code> - wrapper layout attaches address rewriting logic to the layout
         * types that it wraps using a data_location attribute
         *
         * <li><code>code = indirect_pointer, tag = pointer_type, parent = class_unit, array_unit,
         * interface_unit<code> - indirect ref type used to type indirect oops that encode the
         * address of an object, whether by adding tag bits or representing the address as an offset
         * from some base address. these are used to type object references stored in static and
         * instance fields. They are not needed when typing local vars and parameters held in
         * registers or on the stack as they appear as raw addresses.
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
         * <li><code>code == super_reference, tag == inheritance, parent = class_layout,
         * array_layout</code> - reference to super class layout or to appropriate header struct for
         * {code java.lang.Object} or arrays.
         *
         * <li><code>code == interface_implementor, tag == member, parent = interface_layout</code>
         * - union member typed using class layout of a given implementing class
         *
         * <li><code>code = inlined_subroutine/inlined_subroutine_with_children, tag = subprogram,
         * parent = method_location/inlined_subroutine_with_children<code> - provides range and
         * abstract origin for a concrete inline method
         *
         * </ul>
         *
         * <li> Level 2/3 DIEs
         *
         * <li><code>code == method_parameter_declaration1/2/3, tag == formal_parameter, parent =
         * method_declaration1/2, method_location, abstract_inline_method</code> - details of method
         * parameters
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
         * unit. low_pc and hi_pc are only included if the class has compiled methods i.e. for
         * variants 1 and 2. stmt_list is is only included if the class has an associated source
         * file and may therefore have line info i.e. for variant 1.
         *
         * <ul>
         *
         * <li><code>abbrev_code == class_unit1/2/3, tag == DW_TAG_compilation_unit,
         * has_children</code>
         *
         * <li><code>DW_AT_language : ... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_name : ....... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_comp_dir : ... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_low_pc : ..... DW_FORM_address</code> n.b only for <code>abbrev-code ==
         * class_unit1/2</code>
         *
         * <li><code>DW_AT_hi_pc : ...... DW_FORM_address</code> n.b only for <code>abbrev-code ==
         * class_unit1/2</code>
         *
         * <li><code>DW_AT_use_UTF8 : ... DW_FORM_flag</code>
         *
         * <li><code>DW_AT_stmt_list : .. DW_FORM_sec_offset</code> n.b only for <code>abbrev-code
         * == class_unit1</code>
         *
         * </ul>
         *
         * Instance Class Structure: Each class_unit DIE contains a series of level 1 DIEs. The
         * first one describes the class layout. The normal layout does not include a data_location
         * attribute. However, an alternative layout, including that extra attribute, is provided to
         * ensure that tag bits can be removed from pointers to instances of java.lang.Class. This
         * alternative layout is only needed when a heapbase register is not in use and fields hold
         * raw oops. If a heapbase register is in use and fields hold indirect oops then the masking
         * logic for* class pointer tags is included in the data_location attribute attached to the
         * indirect layout record (see below)
         *
         * <ul>
         *
         * <li><code>abbrev_code == class_layout1/class_layout2, tag == DW_TAG_class_type,
         * has_children</code>
         *
         * <li><code>Dw_AT_name : ........ DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1/2</code>
         *
         * <li><code>Dw_AT_decl_file : ... DW_FORM_data1/2</code>
         *
         * <li><code>Dw_AT_decl_line : ... DW_FORM_data1/2</code>
         *
         * <li><code>Dw_AT_data_location : ... DW_FORM_expr_loc</code> n.b. only for class_layout2
         *
         * </ul>
         *
         * Instance Class members: A level 1 class_layout DIE includes a level 2 child for each of
         * the class's methods and fields. The first type declares a method but omits details of the
         * location of the code that implements the method. The second type declares an instance or
         * static field. A class_layout DIE also contains an level 2 DIE specifying the type from
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
         * <li><code>DW_AT_external : .......... DW_FORM_flag</code>
         *
         * <li><code>Dw_AT_name : .............. DW_FORM_strp</code>
         *
         * <li><code>DW_AT_decl_file : ......... DW_FORM_data1/2</code>
         *
         * <li><code>DW_AT_decl_line : ......... DW_FORM_data1/2<code>
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
         * <li><code>Dw_AT_object_pointer : .... DW_FORM_ref_addr<code> n.b. only for
         * method_declaration1, points to param 0 DIE
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
         * <li><code>DW_AT_decl_file : .............. DW_FORM_data1/2</code> n.b. only for
         * field_declaration2/4
         *
         * <li><code>DW_AT_decl_line : .............. DW_FORM_data1/2</code> n.b. only for
         * field_declaration2/4
         *
         * <li><code>Dw_AT_type : ................... DW_FORM_ref_addr</code>
         *
         * <li><code>Dw_AT_data_member_location : ... DW_FORM_data1/2</code> (n.b. nly for
         * field_declaration1/2 instance
         *
         * <li><code>Dw_AT_artificial : ............. DW_FORM_flag</code>
         *
         * <li><code>Dw_AT_accessibility : .......... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_external : ............... DW_FORM_flag</code> (n.b. only for
         * field_declaration3/4 static
         *
         * <li><code>Dw_AT_declaration : ............ DW_FORM_flag</code> n.b. only for
         * field_declaration3/4 static
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
         * <li><code>Dw_AT_file : ... DW_FORM_data1/2</code> n.b. only for
         * method_parameter_declaration2
         *
         * <li><code>Dw_AT_line : ... DW_FORM_data1/2</code> n.b. only for
         * method_parameter_declaration2
         *
         * <li><code>Dw_AT_type : ... DW_FORM_ref_addr</code>
         *
         * <li><code>Dw_AT_artificial : ... DW_FORM_flag</code> n.b. only for
         * method_parameter_declaration1 used for this and access vars
         *
         * </ul>
         *
         * Indirect Instance Class Structure: The level 1 class layout DIE may be followed by a
         * level 1 indirect_layout DIE that wraps the class layout as a super class. The wrapper
         * type supplies a data_location attribute, allowing indirect pointers to the class (see
         * next item) to be translated to raw addresses. The name of the indirect type is
         * constructed by prefixing the class name with DwarfDebufInfo.INDIRECT_PREFIX. This DIE has
         * only one child DIE with type super_reference (see above). The latter references the class
         * layout DIE as a super, effectively embedding the standard layout type in the indirect
         * layout. The size of the indirect layout is the same as the size of the class layout.
         *
         * <ul>
         *
         * <li><code>abbrev_code == indirect_layout, tag == DW_TAG_class_type, has_children</code>
         *
         * <li><code>Dw_AT_name : ........ DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1/2</code>
         *
         * <li><code>Dw_AT_data_location : ... DW_FORM_expr_loc</code>
         *
         * </ul>
         *
         * Instance Class Reference Types: The level 1 class_layout and indirect_layout DIEs are
         * followed by DIEs defining pointers to the respective class layouts. A class_pointer DIE
         * defines a pointer type for the class_layout type and is used to type pointers which
         * directly address an instance. It is used to type local and parameter var references
         * whether located in a register or on the stack. It is followed by an indirect_pointer DIE
         * which defines a pointer type for the class's indirect_layout type. This is used to type
         * references to instances of the class located in a static or instance field. These
         * references require address translation by masking off tag bits and rebasing from an
         * offset to a raw address. The logic for this translation is encoded in a data_location
         * attribute of the indirect_layout DIE.
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
         * <li><code>abbrev_code == indirect_pointer, tag == DW_TAG_pointer_type, no_children</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_type : ........ DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * n.b. the name used in the class_layout DIE is the Java class name. This is deliberately
         * inconsistent with the Java naming where the name refers to the pointer type. In
         * consequence when gdb displays Java types and signatures oop reference appear as pointer
         * types. So, for example the Java String class looks like
         *
         * <ul>
         *
         * <li><code>class java.lang.String : public java.lang.Object {<code>
         *
         * <li><code> private:</code>
         *
         * <li><code> byte[] value;</code>
         *
         * <li><code>...</code>
         *
         * <li><code>public:</code>
         *
         * <li><code>...</code>
         *
         * <li><code> java.lang.String *concat(java.lang.String *);</code>
         *
         * <li><code>...</code>
         *
         * </ul>
         *
         * Method Code Locations: For each method within a class there will normally be a
         * corresponding level 1 DIE providing details of the location of the compiled code for the
         * method. This DIE should inherit attributes from the method_definition DIE referenced from
         * its specification attribute without the need to repeat them, including attributes
         * specified in child DIEs of the method_definition. However, it is actually necessary to
         * replicate the method_parameter DIEs as children of this DIE because gdb does not carry
         * these attributes across from the specification DIE.
         *
         * Note that for methods which only occur as inlined code rather than as a top-level
         * compiles method the method location DIE will be omitted
         *
         * <ul>
         *
         * <li><code>abbrev_code == DW_ABBREV_CODE_method_location, tag == DW_TAG_subprogram,
         * has_children</code>
         *
         * <li><code>DW_AT_low_pc : .......... DW_FORM_addr</code>
         *
         * <li><code>DW_AT_hi_pc : ........... DW_FORM_addr</code>
         *
         * <li><code>DW_AT_external : ........ DW_FORM_flag</code>
         *
         * <li><code>DW_AT_specification : ... DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * Abstract Inline Methods: For any method which has been inlined into another compiled
         * method there will be a corresponding level 1 DIE that identifies the method declaration
         * and serves as the target reference for concrete inlined method DIEs. This DIE should
         * inherit attributes from the method_definition DIE referenced from its specification
         * attribute without the need to repeat them, including attributes specified in child DIEs
         * of the method_definition. However, it is actually necessary to replicate the
         * method_parameter DIEs as children of this DIE because gdb does not carry these attributes
         * across from the specification DIE.
         *
         * Note that an abstract inline method DIE is generated in the compile unit of the class
         * which declares the inlined method whereas a concrete inlined method DIE is generated in
         * the compile unit of the class which declares method into which code has been inlined.
         *
         * <ul>
         *
         * <li><code>abbrev_code == DW_ABBREV_CODE_abstract_inline_method, tag == DW_TAG_subprogram,
         * has_children</code>
         *
         * <li><code>DW_AT_inline : .......... DW_FORM_data1</code>
         *
         * <li><code>DW_AT_external : ........ DW_FORM_flag</code>
         *
         * <li><code>DW_AT_specification : ... DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * Concrete Inlined Methods: Concrete inlined methods are nested as a tree of children under
         * the method_location DIE for the method into which they have been inlined. Each inlined
         * method DIE defines an address range that is a subrange of its parent DIE. A
         * method_location DIE occurs at depth 1 in a compile unit (class_unit). So, this means that
         * for any method which has been inlined into a compiled method at depth K in the inline
         * frame stack there will be a corresponding level 2+K DIE that identifies the method that
         * was inlined (by referencing the corresponding abstract inline method DIE) and locates the
         * call point by citing the file index and line number of its caller. So, if compiled method
         * M inlines a call to m1 at source position f0:l0, m1 inlines a call to method m2 at source
         * position f1:l1 and m2 inlines a call to m3 at source position f2:l2 then there will be a
         * level 2 DIE for the inline code range derived from m1 referencing the abstract entry for
         * m1 with f0 and l0 as file and line, a level 3 DIE for the inline code range derived from
         * m2 referencing the abstract entry for m2 with f1 and l1 as file and line and a level 3
         * DIE for the inline code range derived from m3 referencing the abstract entry for m3 with
         * f2 and l2 as file and line.
         *
         * Note that a concrete inlined method DIE is generated in the compile unit of the class
         * which declares the method into which code has been inlined whereas an abstract inlined
         * method DIE is generated in the compile unit of the class which declares of the inlined
         * method.
         *
         * <ul>
         *
         * <li><code>abbrev_code == DW_ABBREV_CODE_inlined_subroutine, tag == DW_TAG_subprogram,
         * no_children</code>
         *
         * <li><code>abbrev_code == DW_ABBREV_CODE_inlined_subroutine_with_children, tag ==
         * DW_TAG_subprogram, has_children</code>
         *
         * <li><code>DW_AT_abstract_origin : ... DW_FORM_ref_addr</code>
         *
         * <li><code>DW_AT_low_pc : ............ DW_FORM_addr</code>
         *
         * <li><code>DW_AT_hi_pc : ............. DW_FORM_addr</code>
         *
         * <li><code>DW_AT_call_file : ......... DW_FORM_data4</code>
         *
         * <li><code>DW_AT_call_line : ......... DW_FORM_data4</code>
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
         * <li><code>DW_AT_location : ........ DW_FORM_expr_loc</code>
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
         * <li><code>DW_AT_name : ....... DW_FORM_strp</code>
         *
         * </ul>
         *
         * Array Structure: Each array_unit DIE contains four level 1 DIEs. The first one describes
         * the array layout. It has only one child, a super_reference DIE (see above) that
         * references the appropriate array header type for an obnject aray or primitive array of
         * the relevant primitive type). The size of the array layout is the same as the size of the
         * array header.
         *
         * <ul>
         *
         * <li><code>abbrev_code == array_layout, tag == DW_TAG_class_type, has_children</code>
         *
         * <li><code>Dw_AT_name : ........ DW_FORM_strp</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1/2</code>
         *
         * </ul>
         *
         * The immediately following DIE is an indirect_layout (see above) that wraps the array
         * layout as its super type (just as with class layouts). The wrapper type supplies a
         * data_location attribute, allowing indirect pointers to the array to be translated to raw
         * addresses. The name of the indirect array type is constructed by prefixing the array name
         * with INDIRECT_PREFIX. This DIE has only one child DIE with type super_reference (see
         * above). The latter references the array layout DIE, effectively embedding the standard
         * array layout type in the indirect layout. The size of the indirect layout is the same as
         * the size of the array layout.
         *
         * The third and fourth DIEs define array reference types as a pointers to the underlying
         * structure layout types. As with classes, there is an array_pointer type for raw address
         * references used to type local and param vars and an indirect_pointer type (see above) for
         * array references stored in static and instance fields.
         *
         * <ul>
         *
         * <li><code>abbrev_code == array_pointer, tag == DW_TAG_pointer_type, no_children</code>
         *
         * <li><code>Dw_AT_byte_size : ... DW_FORM_data1</code>
         *
         * <li><code>Dw_AT_type : ........ DW_FORM_ref_addr</code>
         *
         * </ul>
         *
         * n.b. the name used in the array_layout DIE is the Java array name. This is deliberately
         * inconsistent with the Java naming where the name refers to the pointer type. As with
         * normal objects an array reference in a Java signature appears as a pointer to an array
         * layout when printed by gdb.
         *
         * Array members: The level 1 array_layout DIE includes level 2 child DIEs with tag member
         * that describe the layout of the array. header_field DIEs are used to declare members of
         * the array header, including the zero length array data member that follows other header
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
         * the classes which implement the interface. The size of the interface layout is the
         * maximum of the sizes for the implementing classes.
         *
         * <ul>
         *
         * <li><code>abbrev_code == interface_layout, tag == union_type, has_children</code>
         *
         * <li><code>Dw_AT_name : ....... DW_FORM_strp</code>
         *
         * <li><code>DW_AT_location : ... DW_FORM_expr_loc</code>
         *
         * </ul>
         *
         * A second level 1 DIE provides an indirect_layout that wraps the interface layout as its
         * super type (just as with class layouts). The wrapper type supplies a data_location
         * attribute, allowing indirect pointers to the interface to be translated to raw addresses.
         * The name of the indirect interface type is constructed by prefixing the interface name
         * with INDIRECT_PREFIX. This DIE has only one child DIE with type super_reference (see
         * above). The latter references the interface layout DIE, effectively embedding the
         * standard interface layout type in the indirect layout. The size of the indirect layout is
         * the same as the size of the interface layout.
         *
         * The third and fourth DIEs define interface reference types as a pointers to the
         * underlying structure layout types. As with classes, there is an interface_pointer type
         * for raw address references used to type local and param vars and an indirect_pointer type
         * (see above) for interface references stored in static and instance fields.
         *
         * A second level 1 defines a pointer to this layout type.
         *
         * n.b. the name used in the interface_layout DIE is the Java array name. This is
         * deliberately inconsistent with the Java naming where the name refers to the pointer type.
         * As with normal objects an interface reference in a Java signature appears as a pointer to
         * an interface layout when printed by gdb.
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
         * <code>union java.lang.CharSequence { java.lang.String _java.lang.String;
         * java.lang.StringBuffer _java.lang.StringBuffer; ... };</code>
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

    public int writeAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeBuiltInUnitAbbrev(context, buffer, pos);
        pos = writeClassUnitAbbrevs(context, buffer, pos);
        pos = writeArrayUnitAbbrev(context, buffer, pos);

        pos = writePrimitiveTypeAbbrev(context, buffer, pos);
        pos = writeVoidTypeAbbrev(context, buffer, pos);
        pos = writeObjectHeaderAbbrev(context, buffer, pos);

        pos = writeClassLayoutAbbrevs(context, buffer, pos);
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
        pos = writeAbstractInlineMethodAbbrev(context, buffer, pos);
        pos = writeStaticFieldLocationAbbrev(context, buffer, pos);
        pos = writeSuperReferenceAbbrev(context, buffer, pos);
        pos = writeInterfaceImplementorAbbrev(context, buffer, pos);

        pos = writeInlinedSubroutineAbbrev(buffer, pos, false);
        pos = writeInlinedSubroutineAbbrev(buffer, pos, true);

        /*
         * if we address rebasing is required then then we need to use indirect layout types
         * supplied with a suitable data_location attribute and indirect pointer types to ensure
         * that gdb converts offsets embedded in static or instance fields to raw pointers.
         * Transformed addresses are typed using pointers to the underlying layout.
         *
         * if address rebasing is not required then we a data_location attribute on the layout type
         * will ensure that address tag bits are removed.
         */
        if (dwarfSections.useHeapBase()) {
            pos = writeIndirectLayoutAbbrev(context, buffer, pos);
            pos = writeIndirectReferenceAbbrev(context, buffer, pos);
        }

        pos = writeParameterDeclarationAbbrevs(context, buffer, pos);

        /* write a null abbrev to terminate the sequence */
        pos = writeNullAbbrev(context, buffer, pos);
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

    private int writeBuiltInUnitAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_builtin_unit, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_compile_unit, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_language, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeClassUnitAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        /* class compile unit with compiled methods and line info */
        pos = writeClassUnitAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_class_unit1, buffer, pos);
        /* class compile unit with compiled methods but without line info */
        pos = writeClassUnitAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_class_unit2, buffer, pos);
        /* class compile unit without compiled methods and without line info */
        pos = writeClassUnitAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_class_unit3, buffer, pos);
        return pos;
    }

    private int writeClassUnitAbbrev(@SuppressWarnings("unused") DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_compile_unit, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_language, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_use_UTF8, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_comp_dir, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_class_unit1 || abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_class_unit2) {
            pos = writeAttrType(DwarfDebugInfo.DW_AT_low_pc, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_addr, buffer, pos);
            pos = writeAttrType(DwarfDebugInfo.DW_AT_hi_pc, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_addr, buffer, pos);
        }
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_class_unit1) {
            pos = writeAttrType(DwarfDebugInfo.DW_AT_stmt_list, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_sec_offset, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayUnitAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_array_unit, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_compile_unit, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_language, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writePrimitiveTypeAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_primitive_type, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_base_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_bit_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_encoding, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeVoidTypeAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_void_type, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_unspecified_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeObjectHeaderAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_object_header, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_structure_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeClassLayoutAbbrevs(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeClassLayoutAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_class_layout1, buffer, pos);
        if (!dwarfSections.useHeapBase()) {
            pos = writeClassLayoutAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_class_layout2, buffer, pos);
        }
        return pos;
    }

    private int writeClassLayoutAbbrev(@SuppressWarnings("unused") DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_class_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_file, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        /*-
         * At present we definitely don't have line numbers.
           pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_line, buffer, pos);
           pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
         */
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_class_layout2) {
            pos = writeAttrType(DwarfDebugInfo.DW_AT_data_location, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_expr_loc, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);

        return pos;
    }

    private int writeClassReferenceAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        /* First the basic pointer type for a pointer to the class struct type. */
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_class_pointer, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_pointer_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeMethodDeclarationAbbrevs(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeMethodDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_method_declaration, buffer, pos);
        pos = writeMethodDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_method_declaration_static, buffer, pos);
        return pos;
    }

    private int writeMethodDeclarationAbbrev(@SuppressWarnings("unused") DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_subprogram, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_external, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_file, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        /* We don't (yet?) have a proper start line for the method itself */
        // pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_line, buffer, pos);
        // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        /* This probably needs to use the symbol name */
        // pos = writeAttrType(DwarfDebugInfo.DW_AT_linkage_name, buffer, pos);
        // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_artificial, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_declaration, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        /* This is not in DWARF2 */
        // pos = writeAttrType(DwarfDebugInfo.DW_AT_virtuality, buffer, pos);
        // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_containing_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_declaration) {
            pos = writeAttrType(DwarfDebugInfo.DW_AT_object_pointer, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeFieldDeclarationAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        /* An instance field no line and file. */
        pos = writeFieldDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_field_declaration1, buffer, pos);
        /* An instance field with line and file. */
        pos = writeFieldDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_field_declaration2, buffer, pos);
        /* A static field no line and file. */
        pos = writeFieldDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_field_declaration3, buffer, pos);
        /* A static field with line and file. */
        pos = writeFieldDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_field_declaration4, buffer, pos);
        return pos;
    }

    private int writeFieldDeclarationAbbrev(@SuppressWarnings("unused") DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_member, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        /* We may not have a file and line for a field. */
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_field_declaration2 || abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_field_declaration4) {
            pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_file, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
            /* At present we definitely don't have line numbers. */
            // pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_line, buffer, pos);
            // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        }
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_field_declaration1 || abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_field_declaration2) {
            /* Instance fields have a member offset relocated relative to the heap base register. */
            pos = writeAttrType(DwarfDebugInfo.DW_AT_data_member_location, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        }
        pos = writeAttrType(DwarfDebugInfo.DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        /* Static fields are only declared here and are external. */
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_field_declaration3 || abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_field_declaration4) {
            pos = writeAttrType(DwarfDebugInfo.DW_AT_external, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
            pos = writeAttrType(DwarfDebugInfo.DW_AT_declaration, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        }
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayLayoutAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_array_layout, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_class_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayReferenceAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_array_pointer, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_pointer_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeInterfaceLayoutAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_interface_layout, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_union_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeInterfaceReferenceAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_interface_pointer, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_pointer_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeInterfaceImplementorAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_interface_implementor, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_member, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeHeaderFieldAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_header_field, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_member, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_data_member_location, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeArrayDataTypeAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_array_data_type, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_array_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeMethodLocationAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_method_location, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_subprogram, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_external, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_specification, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeAbstractInlineMethodAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_abstract_inline_method, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_subprogram, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_inline, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_external, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_specification, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeStaticFieldLocationAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_static_field_location, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_variable, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_specification, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /* Do we have a symbol name to use here? */
        // pos = writeAttrType(DwarfDebugInfo.DW_AT_linkage_name, buffer, pos);
        // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_location, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_expr_loc, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeSuperReferenceAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_super_reference, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_inheritance, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_data_member_location, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_accessibility, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeIndirectLayoutAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        /*
         * oops are not necessarily raw addresses. they may contains pointer bits or be offsets from
         * a base register. An indirect layout wraps a standard layout adding a data_location that
         * translates indirect an oop to a raw address. It is used as the base for an indirect
         * pointer type that is used to type values that need translation to a raw address i.e.
         * values stored in static and instance fields.
         */
        /* the type ofr an indirect layout that includes address translation info */
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_indirect_layout, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_class_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_yes, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        /* Add a data location expression to rebase oop pointers stored as offsets. */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_data_location, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_expr_loc, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);

        return pos;
    }

    private int writeIndirectReferenceAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;

        /* The type for a pointer to the indirect layout type. */
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_indirect_pointer, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_pointer_type, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_byte_size, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data1, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeParameterDeclarationAbbrevs(DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeParameterDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1, buffer, pos);
        pos = writeParameterDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration2, buffer, pos);
        pos = writeParameterDeclarationAbbrev(context, DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration3, buffer, pos);
        return pos;
    }

    private int writeParameterDeclarationAbbrev(@SuppressWarnings("unused") DebugContext context, int abbrevCode, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_formal_parameter, buffer, pos);
        pos = writeFlag(DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        /* We don't yet have parameter names. */
        // pos = writeAttrType(DwarfDebugInfo.DW_AT_name, buffer, pos);
        // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_strp, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration2) {
            /* Line numbers for parameter declarations are not (yet?) available. */
            pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_file, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
            // pos = writeAttrType(DwarfDebugInfo.DW_AT_decl_line, buffer, pos);
            // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data2, buffer, pos);
        }
        pos = writeAttrType(DwarfDebugInfo.DW_AT_type, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        if (abbrevCode == DwarfDebugInfo.DW_ABBREV_CODE_method_parameter_declaration1) {
            /* Only this parameter is artificial and it has no line. */
            pos = writeAttrType(DwarfDebugInfo.DW_AT_artificial, buffer, pos);
            pos = writeAttrForm(DwarfDebugInfo.DW_FORM_flag, buffer, pos);
        }
        /*-
          * We don't yet have locations for method parameters,
          * not even at the start of the method.
          */
        // pos = writeAttrType(DwarfDebugInfo.DW_AT_location, buffer, pos);
        // pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data4, buffer, pos);
        /*
         * Now terminate.
         */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    private int writeNullAbbrev(@SuppressWarnings("unused") DebugContext context, byte[] buffer, int p) {
        int pos = p;
        pos = writeAbbrevCode(DwarfDebugInfo.DW_ABBREV_CODE_null, buffer, pos);
        return pos;
    }

    private int writeInlinedSubroutineAbbrev(byte[] buffer, int p, boolean withChildren) {
        int pos = p;
        pos = writeAbbrevCode(withChildren ? DwarfDebugInfo.DW_ABBREV_CODE_inlined_subroutine_with_children : DwarfDebugInfo.DW_ABBREV_CODE_inlined_subroutine, buffer, pos);
        pos = writeTag(DwarfDebugInfo.DW_TAG_inlined_subroutine, buffer, pos);
        pos = writeFlag(withChildren ? DwarfDebugInfo.DW_CHILDREN_yes : DwarfDebugInfo.DW_CHILDREN_no, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_abstract_origin, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_ref_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_low_pc, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_hi_pc, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_addr, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_call_file, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data4, buffer, pos);
        pos = writeAttrType(DwarfDebugInfo.DW_AT_call_line, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_data4, buffer, pos);
        /* Now terminate. */
        pos = writeAttrType(DwarfDebugInfo.DW_AT_null, buffer, pos);
        pos = writeAttrForm(DwarfDebugInfo.DW_FORM_null, buffer, pos);
        return pos;
    }

    /**
     * The debug_abbrev section depends on debug_aranges section.
     */
    private static final String TARGET_SECTION_NAME = DwarfDebugInfo.DW_ARANGES_SECTION_NAME;

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
