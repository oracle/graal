#
# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

from typing import Iterable

import sys
import os
import re
import pdb

import gdb
import gdb.types
import gdb.printing
import gdb.unwinder
from gdb.FrameDecorator import FrameDecorator

if sys.version_info.major < 3:
    pyversion = '.'.join(str(v) for v in sys.version_info[:3])
    message = (
            'Cannot load SubstrateVM debugging assistance for GDB from ' + os.path.basename(__file__)
            + ': it requires at least Python 3.x. You are running GDB with Python ' + pyversion
            + ' from ' + sys.executable + '.'
    )
    raise AssertionError(message)

if int(gdb.VERSION.split('.')[0]) < 13:
    message = (
            'Cannot load SubstrateVM debugging assistance for GDB from ' + os.path.basename(__file__)
            + ': it requires at least GDB 13.x. You are running GDB ' + gdb.VERSION + '.'
    )
    raise AssertionError(message)

# check for a symbol that exists only in native image debug info
if gdb.lookup_global_symbol("com.oracle.svm.core.Isolates", gdb.SYMBOL_TYPES_DOMAIN) is None:
    message = (
            'Cannot load SubstrateVM debugging assistance without a loaded java native image or native shared library,'
            + 'the script requires java types for initialization.'
    )
    raise AssertionError(message)


def trace(msg: str) -> None:
    if svm_debug_tracing.tracefile:
        svm_debug_tracing.tracefile.write(f'trace: {msg}\n'.encode(encoding='utf-8', errors='strict'))
        svm_debug_tracing.tracefile.flush()


def adr(obj: gdb.Value) -> int:
    # use null as fallback if we cannot find the address value
    adr_val = 0
    if obj.type.code == gdb.TYPE_CODE_PTR:
        if int(obj) != 0:
            adr_val = int(obj.dereference().address)
    elif obj.address is not None:
        adr_val = int(obj.address)
    return adr_val


def try_or_else(success, failure, *exceptions):
    try:
        return success()
    except exceptions or Exception:
        return failure() if callable(failure) else failure


class Function:
    """A more complete representation of gdb function symbols."""
    def __init__(self, static: bool, name: str, gdb_sym: gdb.Symbol):
        self.static = static
        self.name = name
        self.sym = gdb_sym


class SVMUtil:
    pretty_printer_name = "SubstrateVM"

    hub_field_name = "hub"
    compressed_ref_prefix = '_z_.'

    use_heap_base = try_or_else(lambda: bool(gdb.parse_and_eval('(bool)__svm_use_heap_base')), True, gdb.error)
    compressed_shift = try_or_else(lambda: int(gdb.parse_and_eval('(int)__svm_compressed_shift')), 0, gdb.error)
    oop_tags_mask = try_or_else(lambda: int(gdb.parse_and_eval('(int)__svm_oop_tags_mask')), 0, gdb.error)
    object_alignment = try_or_else(lambda: int(gdb.parse_and_eval('(int)__svm_object_alignment')), 0, gdb.error)

    string_type = gdb.lookup_type("java.lang.String")
    enum_type = gdb.lookup_type("java.lang.Enum")
    object_type = gdb.lookup_type("java.lang.Object")
    hub_type = gdb.lookup_type("java.lang.Class")
    null = gdb.Value(0).cast(object_type.pointer())
    classloader_type = gdb.lookup_type("java.lang.ClassLoader")
    wrapper_types = [gdb.lookup_type(f'java.lang.{x}') for x in
                     ["Byte", "Short", "Integer", "Long", "Float", "Double", "Boolean", "Character"]
                     if gdb.lookup_global_symbol(f'java.lang.{x}', gdb.SYMBOL_TYPES_DOMAIN) is not None]

    pretty_print_objfiles = set()

    current_print_depth = 0
    parents = dict()
    selfref_cycles = set()

    hlreps = dict()
    deopt_stub_adr = 0

    @classmethod
    def get_architecture(cls) -> str:
        try:
            arch_name = gdb.selected_frame().architecture().name()
        except gdb.error:
            # no frame available
            arch_name = ""

        if "x86-64" in arch_name:
            return "amd64"
        elif "aarch64" in arch_name:
            return "arm64"
        else:
            return arch_name

    @classmethod
    def get_isolate_thread(cls) -> gdb.Value:
        arch = cls.get_architecture()
        if arch == "amd64":
            return gdb.selected_frame().read_register('r15')
        elif arch == "arm64":
            return gdb.selected_frame().read_register('r28')
        else:
            return cls.null

    @classmethod
    def get_heap_base(cls) -> gdb.Value:
        arch = cls.get_architecture()
        if arch == "amd64":
            return gdb.selected_frame().read_register('r14')
        elif arch == "arm64":
            return gdb.selected_frame().read_register('r29')
        return cls.null

    @classmethod
    def is_null(cls, obj: gdb.Value) -> bool:
        return adr(obj) == 0 or (cls.use_heap_base and adr(obj) == int(cls.get_heap_base()))

    @classmethod
    def get_uncompressed_type(cls, t: gdb.Type) -> gdb.Type:
        # compressed types only exist for java type which are either struct or union
        if t.code != gdb.TYPE_CODE_STRUCT and t.code != gdb.TYPE_CODE_UNION:
            return t
        result = cls.get_base_class(t) if cls.is_compressed(t) else t
        trace(f'<SVMUtil> - get_uncompressed_type({t}) = {result}')
        return result

    @classmethod
    def get_compressed_type(cls, t: gdb.Type) -> gdb.Type:
        t = cls.get_basic_type(t)
        # compressed types only exist for java types which are either struct or union
        # do not compress types that already have the compressed prefix
        if not cls.is_java_type(t) or cls.is_compressed(t):
            return t

        type_name = t.name
        # java types only contain '::' if there is a classloader namespace
        if '::' in type_name:
            loader_namespace, _, type_name = type_name.partition('::')
            type_name = loader_namespace + '::' + cls.compressed_ref_prefix + type_name
        else:
            type_name = cls.compressed_ref_prefix + type_name

        trace(f'<SVMUtil> - get_compressed_type({t}) = {type_name}')
        return gdb.lookup_type(type_name)

    @classmethod
    def get_compressed_adr(cls, obj: gdb.Value) -> int:
        # use compressed ref if available - only compute it if necessary
        if obj.type.code == gdb.TYPE_CODE_PTR and cls.is_compressed(obj.type):
            return int(obj)

        absolute_adr = adr(obj)
        if absolute_adr == 0:
            return absolute_adr

        # recreate correct address for compressed oops
        # For an explanation of the conversion rules see com.oracle.svm.core.heap.ReferenceAccess
        is_hub = cls.get_rtt(obj) == cls.hub_type
        oop_compressed_shift = cls.compressed_shift
        oop_tag_shift = int.bit_count(cls.oop_tags_mask)
        oop_align_shift = int.bit_count(cls.object_alignment - 1)
        compressed_adr = absolute_adr
        if cls.use_heap_base:
            compressed_adr -= int(SVMUtil.get_heap_base())
            if is_hub:
                if oop_compressed_shift == 0:
                    oop_compressed_shift = oop_align_shift
                compressed_adr = compressed_adr << oop_tag_shift
            compressed_adr = compressed_adr >> oop_compressed_shift

        return compressed_adr

    @classmethod
    def get_unqualified_type_name(cls, qualified_type_name: str) -> str:
        result = qualified_type_name.split('.')[-1]
        result = result.split('$')[-1]
        trace(f'<SVMUtil> - get_unqualified_type_name({qualified_type_name}) = {result}')
        return result

    @classmethod
    def is_compressed(cls, t: gdb.Type) -> bool:
        type_name = cls.get_basic_type(t).name
        if type_name is None:
            # fallback to the GDB type printer for t
            type_name = str(t)
        # compressed types from a different classLoader have the format <loader_name>::_z_.<type_name>
        result = type_name.startswith(cls.compressed_ref_prefix) or ('::' + cls.compressed_ref_prefix) in type_name
        trace(f'<SVMUtil> - is_compressed({type_name}) = {result}')
        return result

    @classmethod
    def adr_str(cls, obj: gdb.Value) -> str:
        if not svm_print_address.absolute_adr and cls.is_compressed(obj.type):
            result = f' @z({hex(cls.get_compressed_adr(obj))})'
        else:
            result = f' @({hex(adr(obj))})'
        trace(f'<SVMUtil> - adr_str({hex(adr(obj))}) = {result}')
        return result

    @classmethod
    def prompt_hook(cls, current_prompt: str = None):
        cls.current_print_depth = 0
        cls.parents.clear()
        cls.selfref_cycles.clear()
        SVMCommandPrint.cache.clear()

    @classmethod
    def is_selfref(cls, obj: gdb.Value) -> bool:
        result = (svm_check_selfref.value and
                  not cls.is_primitive(obj.type) and
                  adr(obj) in cls.selfref_cycles)
        trace(f'<SVMUtil> - is_selfref({hex(adr(obj))}) = {result}')
        return result

    @classmethod
    def add_selfref(cls, parent: gdb.Value, child: gdb.Value) -> gdb.Value:
        # filter out null references and primitives
        if (child.type.code == gdb.TYPE_CODE_PTR and cls.is_null(child)) or cls.is_primitive(child.type):
            return child

        child_adr = adr(child)
        parent_adr = adr(parent)
        trace(f'<SVMUtil> - add_selfref(parent={hex(parent_adr)}, child={hex(child_adr)})')
        if svm_check_selfref.value and cls.is_reachable(child_adr, parent_adr):
            trace(f' <add selfref {child_adr}>')
            cls.selfref_cycles.add(child_adr)
        else:
            trace(f' <add {hex(child_adr)} --> {hex(parent_adr)}>')
            if child_adr in cls.parents:
                cls.parents[child_adr].append(parent_adr)
            else:
                cls.parents[child_adr] = [parent_adr]
        return child

    @classmethod
    # checks if node this is reachable from node other (this node is parent of other node)
    def is_reachable(cls, this: hex, other: hex) -> bool:
        test_nodes = [other]
        trace(f'<SVMUtil> - is_reachable(this={this}, other={other}')
        while True:
            if len(test_nodes) == 0:
                return False
            if any(this == node for node in test_nodes):
                return True
            # create a flat list of all ancestors of each tested node
            test_nodes = [parent for node in test_nodes for parent in cls.parents.get(node, [])]

    @classmethod
    def get_java_string(cls, obj: gdb.Value, gdb_output_string: bool = False) -> str:
        if cls.is_null(obj):
            return ""

        trace(f'<SVMUtil> - get_java_string({hex(adr(obj))})')
        coder = cls.get_int_field(obj, 'coder', None)
        if coder is None:
            codec = 'utf-16'  # Java 8 has a char[] with utf-16
            bytes_per_char = 2
        else:
            trace(f'<SVMUtil> - get_java_string: coder = {coder}')
            # From Java 9 on, value is byte[] with latin_1 or utf-16_le
            codec = {
                0: 'latin_1',
                1: 'utf-16_le',
            }.get(coder)
            bytes_per_char = 1

        value = cls.get_obj_field(obj, 'value')
        if cls.is_null(value):
            return ""

        value_content = cls.get_obj_field(value, 'data')
        value_length = cls.get_int_field(value, 'len')
        if cls.is_null(value_content) or value_length == 0:
            return ""

        string_data = bytearray()
        for index in range(min(svm_print_string_limit.value,
                               value_length) if gdb_output_string and svm_print_string_limit.value >= 0 else value_length):
            mask = (1 << 8 * bytes_per_char) - 1
            code_unit = int(value_content[index] & mask)
            code_unit_as_bytes = code_unit.to_bytes(bytes_per_char, byteorder='little')
            string_data.extend(code_unit_as_bytes)
        result = string_data.decode(codec).replace("\x00", r"\0")
        if gdb_output_string and 0 < svm_print_string_limit.value < value_length:
            result += "..."

        trace(f'<SVMUtil> - get_java_string({hex(adr(obj))}) = {result}')
        return result

    @classmethod
    def get_obj_field(cls, obj: gdb.Value, field_name: str, default: gdb.Value | None = null) -> gdb.Value | None:
        try:
            return obj[field_name]
        except gdb.error:
            return default

    @classmethod
    def get_int_field(cls, obj: gdb.Value, field_name: str, default: int | None = 0) -> int | None:
        field = cls.get_obj_field(obj, field_name)
        try:
            return int(field)
        except (gdb.error, TypeError):  # TypeError if field is None already
            return default

    @classmethod
    def get_classloader_namespace(cls, obj: gdb.Value) -> str:
        try:
            hub = cls.get_obj_field(obj, cls.hub_field_name)
            if cls.is_null(hub):
                return ""

            hub_companion = cls.get_obj_field(hub, 'companion')
            if cls.is_null(hub_companion):
                return ""

            loader = cls.get_obj_field(hub_companion, 'classLoader')
            if cls.is_null(loader):
                return ""
            loader = cls.cast_to(loader, cls.classloader_type)

            loader_name = cls.get_obj_field(loader, 'nameAndId')
            if cls.is_null(loader_name):
                return ""

            loader_namespace = cls.get_java_string(loader_name)
            trace(f'<SVMUtil> - get_classloader_namespace loader_namespace: {loader_namespace}')
            # replicate steps in 'com.oracle.svm.hosted.image.NativeImageBFDNameProvider::uniqueShortLoaderName'
            # for recreating the loader name stored in the DWARF debuginfo
            loader_namespace = cls.get_unqualified_type_name(loader_namespace)
            loader_namespace = loader_namespace.replace(' @', '_').replace("'", '').replace('"', '')
            return loader_namespace
        except gdb.error:
            pass  # ignore gdb errors here and try to continue with no classLoader
        return ""

    @classmethod
    def get_rtt(cls, obj: gdb.Value) -> gdb.Type:
        static_type = cls.get_basic_type(obj.type)

        # check for interfaces and cast them to Object to make the hub accessible
        if cls.get_uncompressed_type(cls.get_basic_type(obj.type)).code == gdb.TYPE_CODE_UNION:
            obj = cls.cast_to(obj, cls.object_type)

        hub = cls.get_obj_field(obj, cls.hub_field_name)
        if cls.is_null(hub):
            return static_type

        name_field = cls.get_obj_field(hub, 'name')
        if cls.is_null(name_field):
            return static_type

        rtt_name = cls.get_java_string(name_field)
        if rtt_name.startswith('['):
            array_dimension = rtt_name.count('[')
            if array_dimension > 0:
                rtt_name = rtt_name[array_dimension:]
            if rtt_name[0] == 'L':
                classname_end = rtt_name.find(';')
                rtt_name = rtt_name[1:classname_end]
            else:
                rtt_name = {
                    'Z': 'boolean',
                    'B': 'byte',
                    'C': 'char',
                    'D': 'double',
                    'F': 'float',
                    'I': 'int',
                    'J': 'long',
                    'S': 'short',
                }.get(rtt_name, rtt_name)
            for _ in range(array_dimension):
                rtt_name += '[]'

        loader_namespace = cls.get_classloader_namespace(obj)
        if loader_namespace != "":
            try:
                # try to apply loader namespace
                rtt = gdb.lookup_type(loader_namespace + '::' + rtt_name)
            except gdb.error:
                rtt = gdb.lookup_type(rtt_name)  # found a loader namespace that is ignored (e.g. 'app')
        else:
            rtt = gdb.lookup_type(rtt_name)

        if cls.is_compressed(obj.type) and not cls.is_compressed(rtt):
            rtt = cls.get_compressed_type(rtt)

        trace(f'<SVMUtil> - get_rtt({hex(adr(obj))}) = {rtt_name}')
        return rtt

    @classmethod
    def cast_to(cls, obj: gdb.Value, t: gdb.Type) -> gdb.Value:
        if t is None:
            return obj

        # get objects address, take care of compressed oops
        if cls.is_compressed(t):
            obj_adr = cls.get_compressed_adr(obj)
        else:
            obj_adr = adr(obj)

        trace(f'<SVMUtil> - cast_to({hex(adr(obj))}, {t})')
        if t.code != gdb.TYPE_CODE_PTR:
            t = t.pointer()

        trace(f'<SVMUtil> - cast_to({hex(adr(obj))}, {t}) returned')
        # just use the raw pointer value and cast it instead the obj
        # casting the obj directly results in issues with compressed oops
        return obj if t == obj.type else gdb.Value(obj_adr).cast(t)

    @classmethod
    def get_symbol_adr(cls, symbol: str) -> int:
        trace(f'<SVMUtil> - get_symbol_adr({symbol})')
        return gdb.parse_and_eval(symbol).address

    @classmethod
    def execout(cls, cmd: str) -> str:
        trace(f'<SVMUtil> - execout({cmd})')
        return gdb.execute(cmd, False, True)

    @classmethod
    def get_basic_type(cls, t: gdb.Type) -> gdb.Type:
        trace(f'<SVMUtil> - get_basic_type({t})')
        while t.code == gdb.TYPE_CODE_PTR:
            t = t.target()
        return t

    @classmethod
    def is_primitive(cls, t: gdb.Type) -> bool:
        result = cls.get_basic_type(t).is_scalar
        trace(f'<SVMUtil> - is_primitive({t}) = {result}')
        return result

    @classmethod
    def is_primitive_wrapper(cls, t: gdb.Type) -> bool:
        result = t in cls.wrapper_types
        trace(f'<SVMUtil> - is_primitive_wrapper({t}) = {result}')
        return result

    @classmethod
    def is_enum_type(cls, t: gdb.Type) -> bool:
        return cls.get_base_class(t) == cls.enum_type

    @classmethod
    def get_base_class(cls, t: gdb.Type) -> gdb.Type:
        return t if t == cls.object_type else \
            next((f.type for f in t.fields() if f.is_base_class), cls.object_type)

    @classmethod
    def find_shared_types(cls, type_list: list[gdb.Type], t: gdb.Type) -> list[gdb.Type]:
        if len(type_list) == 0:
            while t != cls.object_type:
                type_list += [t]
                t = cls.get_base_class(t)
            return type_list
        else:
            while t != cls.object_type:
                if t in type_list:
                    return type_list[type_list.index(t):]
                t = cls.get_base_class(t)
            return [cls.object_type]

    @classmethod
    def get_all_fields(cls, t: gdb.Type, include_static: bool) -> list[gdb.Field]:
        t = cls.get_basic_type(t)
        while t.code == gdb.TYPE_CODE_TYPEDEF:
            t = t.target()
            t = cls.get_basic_type(t)
        if t.code != gdb.TYPE_CODE_STRUCT and t.code != gdb.TYPE_CODE_UNION:
            return []
        for f in t.fields():
            if not include_static:
                try:
                    f.bitpos  # bitpos attribute is not available for static fields
                except AttributeError:  # use bitpos access exception to skip static fields
                    continue
            if f.is_base_class:
                yield from cls.get_all_fields(f.type, include_static)
            else:
                yield f

    @classmethod
    def get_all_member_functions(cls, t: gdb.Type, include_static: bool, include_constructor: bool) -> set[Function]:
        syms = set()
        try:
            basic_type = cls.get_basic_type(t)
            type_name = basic_type.name
            members = SVMUtil.execout(f"ptype '{type_name}'")
            for member in members.split('\n'):
                parts = member.strip().split(' ')
                is_static = parts[0] == 'static'
                if not include_static and is_static:
                    continue
                for part in parts:
                    if '(' in part:
                        func_name = part[:part.find('(')]
                        if include_constructor or func_name != cls.get_unqualified_type_name(type_name):
                            sym = gdb.lookup_global_symbol(f"{type_name}::{func_name}")
                            # check if symbol exists and is a function
                            if sym is not None and sym.type.code == gdb.TYPE_CODE_FUNC:
                                syms.add(Function(is_static, func_name, sym))
                        break
            for f in basic_type.fields():
                if f.is_base_class:
                    syms = syms.union(cls.get_all_member_functions(f.type, include_static, include_constructor))
        except Exception as ex:
            trace(f'<SVMUtil> - get_all_member_function_names({t}) exception: {ex}')
        return syms

    @classmethod
    def is_java_type(cls, t: gdb.Type) -> bool:
        t = cls.get_uncompressed_type(cls.get_basic_type(t))
        # Check for existing ".class" symbol (which exists for every java type in a native image)
        # a common java class is represented by a struct, interfaces are represented by a union
        # only structs contain a "hub" field, thus just checking for a hub field would not be enough
        result = ((t.code == gdb.TYPE_CODE_STRUCT or t.code == gdb.TYPE_CODE_UNION) and
                  gdb.lookup_global_symbol(t.name + '.class', gdb.SYMBOL_VAR_DOMAIN) is not None)

        trace(f'<SVMUtil> - is_java_obj({t}) = {result}')
        return result


class SVMPPString:
    def __init__(self, obj: gdb.Value, java: bool = True):
        trace(f'<SVMPPString> - __init__({hex(adr(obj))})')
        self.__obj = obj
        self.__java = java

    def to_string(self) -> str | gdb.Value:
        trace('<SVMPPString> - to_string')
        if self.__java:
            try:
                value = '"' + SVMUtil.get_java_string(self.__obj, True) + '"'
            except gdb.error:
                return SVMPPConst(None)
        else:
            value = str(self.__obj)
            value = value[value.index('"'):]
        if svm_print_address.with_adr:
            value += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPString> - to_string = {value}')
        return value


class SVMPPArray:
    def __init__(self, obj: gdb.Value, java_array: bool = True):
        trace(f'<SVMPPArray> - __init__(obj={obj.type} @ {hex(adr(obj))}, java_array={java_array})')
        if java_array:
            self.__length = SVMUtil.get_int_field(obj, 'len')
            self.__array = SVMUtil.get_obj_field(obj, 'data', None)
            if SVMUtil.is_null(self.__array):
                self.__array = None
        else:
            self.__length = obj.type.range()[-1] + 1
            self.__array = obj
        self.__obj = obj
        self.__java_array = java_array
        self.__skip_children = SVMUtil.is_selfref(obj) or 0 <= svm_print_depth_limit.value <= SVMUtil.current_print_depth
        if not self.__skip_children:
            SVMUtil.current_print_depth += 1

    def display_hint(self) -> str:
        trace('<SVMPPArray> - display_hint = array')
        return 'array'

    def to_string(self) -> str | gdb.Value:
        trace('<SVMPPArray> - to_string')
        if self.__java_array:
            rtt = SVMUtil.get_rtt(self.__obj)
            value = str(SVMUtil.get_uncompressed_type(rtt))
            value = value.replace('[]', f'[{self.__length}]')
        else:
            value = str(self.__obj.type)
        if self.__skip_children:
            value += ' = {...}'
        if svm_print_address.with_adr:
            value += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPArray> - to_string = {value}')
        return value

    def __iter__(self):
        trace('<SVMPPArray> - __iter__')
        if self.__array is not None:
            for i in range(self.__length):
                yield self.__array[i]

    def children(self) -> Iterable[object]:
        trace('<SVMPPArray> - children')
        if self.__skip_children:
            return
        for index, elem in enumerate(self):
            # apply custom limit only for java arrays
            if self.__java_array and 0 <= svm_print_element_limit.value <= index:
                yield str(index), '...'
                return
            trace(f'<SVMPPArray> - children[{index}]')
            yield str(index), SVMUtil.add_selfref(self.__obj, elem)
        SVMUtil.current_print_depth -= 1


class SVMPPClass:
    def __init__(self, obj: gdb.Value, java_class: bool = True):
        trace(f'<SVMPPClass> - __init__({obj.type} @ {hex(adr(obj))})')
        self.__obj = obj
        self.__java_class = java_class
        self.__skip_children = SVMUtil.is_selfref(obj) or 0 <= svm_print_depth_limit.value <= SVMUtil.current_print_depth
        if not self.__skip_children:
            SVMUtil.current_print_depth += 1

    def __getitem__(self, key: str) -> gdb.Value:
        trace(f'<SVMPPClass> - __getitem__({key})')
        item = SVMUtil.get_obj_field(self.__obj, key, None)
        if item is None:
            return None
        pp_item = gdb.default_visualizer(item)
        return item if pp_item is None else pp_item

    def to_string(self) -> str | gdb.Value:
        trace('<SVMPPClass> - to_string')
        try:
            if self.__java_class:
                rtt = SVMUtil.get_rtt(self.__obj)
                result = SVMUtil.get_uncompressed_type(rtt).name
            else:
                result = "object" if self.__obj.type.name is None else self.__obj.type.name
            if self.__skip_children:
                result += ' = {...}'
            if svm_print_address.with_adr:
                result += SVMUtil.adr_str(self.__obj)
            trace(f'<SVMPPClass> - to_string = {result}')
            return result
        except gdb.error as ex:
            trace(f"<SVMPPClass> - to_string error - SVMPPClass: {ex}")
            return 'object'

    def children(self) -> Iterable[object]:
        trace('<SVMPPClass> - children (class field iterator)')
        if self.__skip_children:
            return
        fields = [str(f.name) for f in SVMUtil.get_all_fields(self.__obj.type, svm_print_static_fields.value) if f.name != SVMUtil.hub_field_name]
        for index, f in enumerate(fields):
            trace(f'<SVMPPClass> - children: field "{f}"')
            # apply custom limit only for java objects
            if self.__java_class and 0 <= svm_print_field_limit.value <= index:
                yield f, '...'
                return
            yield f, SVMUtil.add_selfref(self.__obj, self.__obj[f])
        SVMUtil.current_print_depth -= 1


class SVMPPEnum:
    def __init__(self, obj: gdb.Value):
        trace(f'<SVMPPEnum> - __init__({hex(adr(obj))})')
        self.__obj = obj
        self.__name = SVMUtil.get_obj_field(self.__obj, 'name', "")
        self.__ordinal = SVMUtil.get_int_field(self.__obj, 'ordinal', None)

    def to_string(self) -> str | gdb.Value:
        result = SVMUtil.get_java_string(self.__name) + f"({self.__ordinal})"
        if svm_print_address.with_adr:
            result += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPEnum> - to_string = {result}')
        return result


class SVMPPBoxedPrimitive:
    def __init__(self, obj: gdb.Value):
        trace(f'<SVMPPBoxedPrimitive> - __init__({obj.type} @ {hex(adr(obj))})')
        self.__obj = obj
        self.__value = SVMUtil.get_obj_field(self.__obj, 'value', obj.type.name)

    def to_string(self) -> str | gdb.Value:
        result = str(self.__value)
        if svm_print_address.with_adr:
            result += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPBoxedPrimitive> - to_string = {result}')
        return result


class SVMPPConst:
    def __init__(self, val: str | None):
        trace('<SVMPPConst> - __init__')
        self.__val = val

    def to_string(self) -> str | gdb.Value:
        result = "null" if self.__val is None else self.__val
        trace(f'<SVMPPConst> - to_string = {result}')
        return result


class SVMPrettyPrinter(gdb.printing.PrettyPrinter):
    def __init__(self):
        super().__init__(SVMUtil.pretty_printer_name)

    def __call__(self, obj: gdb.Value):
        trace(f'<SVMPrettyPrinter> - __call__({obj.type} @ {hex(adr(obj))})')

        if not SVMUtil.is_primitive(obj.type) and SVMUtil.is_java_type(obj.type):
            # Filter out references to the null literal
            if SVMUtil.is_null(obj):
                return SVMPPConst(None)

            rtt = SVMUtil.get_rtt(obj)
            uncompressed_rtt = SVMUtil.get_uncompressed_type(rtt)
            obj = SVMUtil.cast_to(obj, rtt)

            # filter for primitive wrappers
            if SVMUtil.is_primitive_wrapper(uncompressed_rtt):
                return SVMPPBoxedPrimitive(obj)

            # filter for strings
            if uncompressed_rtt == SVMUtil.string_type:
                return SVMPPString(obj)

            # filter for arrays
            if uncompressed_rtt.name.endswith("[]"):
                return SVMPPArray(obj)

            # filter for enum values
            if SVMUtil.is_enum_type(uncompressed_rtt):
                return SVMPPEnum(obj)

            # Any other Class ...
            if svm_use_hlrep.value:
                pp = make_high_level_object(obj, uncompressed_rtt.name)
            else:
                pp = SVMPPClass(obj)
            return pp

        # no complex java type -> handle foreign types for selfref checks
        elif obj.type.code == gdb.TYPE_CODE_PTR and obj.type.target().code != gdb.TYPE_CODE_VOID:
            # Filter out references to the null literal
            if SVMUtil.is_null(obj):
                return SVMPPConst(None)
            return self.__call__(obj.dereference())
        elif obj.type.code == gdb.TYPE_CODE_ARRAY:
            return SVMPPArray(obj, False)
        elif obj.type.code == gdb.TYPE_CODE_TYPEDEF:
            # try to expand foreign c structs
            try:
                obj = obj.dereference()
                return self.__call__(obj)
            except gdb.error as err:
                return None
        elif obj.type.code == gdb.TYPE_CODE_STRUCT:
            return SVMPPClass(obj, False)
        elif SVMUtil.is_primitive(obj.type):
            if obj.type.name == "char" and obj.type.sizeof == 2:
                return SVMPPConst(repr(chr(obj)))
            elif obj.type.name == "byte":
                return SVMPPConst(str(int(obj)))
            else:
                return None
        else:
            return None


def HLRep(original_class):
    try:
        SVMUtil.hlreps[original_class.target_type] = original_class
    except Exception as ex:
        trace(f'<@HLRep registration exception: {ex}>')
    return original_class


@HLRep
class ArrayList:
    target_type = 'java.util.ArrayList'

    def __init__(self, obj: gdb.Value):
        trace(f'<ArrayList> - __init__({obj.type} @ {hex(adr(obj))})')
        self.__size = SVMUtil.get_int_field(obj, 'size')
        element_data = SVMUtil.get_obj_field(obj, 'elementData')
        if SVMUtil.is_null(element_data):
            self.__data = None
        else:
            self.__data = SVMUtil.get_obj_field(element_data, 'data', None)
            if self.__data is not None and SVMUtil.is_null(self.__data):
                self.__data = None
        self.__obj = obj
        self.__skip_children = SVMUtil.is_selfref(obj) or 0 <= svm_print_depth_limit.value <= SVMUtil.current_print_depth
        if not self.__skip_children:
            SVMUtil.current_print_depth += 1

    def to_string(self) -> str | gdb.Value:
        trace('<ArrayList> - to_string')
        res = 'java.util.ArrayList'
        if svm_infer_generics.value != 0:
            elem_type = self.infer_generic_types()
            if elem_type is not None:
                res += f'<{elem_type}>'
        res += f'({self.__size})'
        if self.__skip_children:
            res += ' = {...}'
        if svm_print_address.with_adr:
            res += SVMUtil.adr_str(self.__obj)
        trace(f'<ArrayList> - to_string = {res}')
        return res

    def infer_generic_types(self) -> str:
        elem_type: list[gdb.Type] = []

        for i, elem in enumerate(self, 1):
            if not SVMUtil.is_null(elem):  # check for null values
                elem_type = SVMUtil.find_shared_types(elem_type, SVMUtil.get_rtt(elem))
            if (len(elem_type) > 0 and elem_type[0] == SVMUtil.object_type) or (0 <= svm_infer_generics.value <= i):
                break

        return None if len(elem_type) == 0 else SVMUtil.get_unqualified_type_name(elem_type[0].name)

    def display_hint(self) -> str:
        trace('<ArrayList> - display_hint = array')
        return 'array'

    def __iter__(self) -> gdb.Value:
        trace('<ArrayList> - __iter__')
        if self.__data is not None:
            for i in range(self.__size):
                yield self.__data[i]

    def children(self) -> Iterable[object]:
        trace(f'<ArrayList> - children({self.__obj.type} @ {hex(adr(self.__obj))})')
        if self.__skip_children:
            return
        for index, elem in enumerate(self):
            if 0 <= svm_print_element_limit.value <= index:
                yield str(index), '...'
                return
            trace(f'<ArrayList> - children({self.__obj.type} @ {hex(adr(self.__obj))})[{index}]')
            yield str(index), SVMUtil.add_selfref(self.__obj, elem)
        SVMUtil.current_print_depth -= 1


@HLRep
class HashMap:
    target_type = 'java.util.HashMap'

    def __init__(self, obj: gdb.Value):
        trace(f'<HashMap> - __init__({obj.type} @ {hex(adr(obj))})')

        self.__size = SVMUtil.get_int_field(obj, 'size')
        table = SVMUtil.get_obj_field(obj, 'table')
        if SVMUtil.is_null(table):
            self.__data = None
            self.__table_len = 0
        else:
            self.__data = SVMUtil.get_obj_field(table, 'data', None)
            if self.__data is not None and SVMUtil.is_null(self.__data):
                self.__data = None
            self.__table_len = SVMUtil.get_int_field(table, 'len')

        self.__obj = obj
        self.__skip_children = SVMUtil.is_selfref(obj) or 0 <= svm_print_depth_limit.value <= SVMUtil.current_print_depth
        if not self.__skip_children:
            SVMUtil.current_print_depth += 1

    def to_string(self) -> str | gdb.Value:
        trace('<HashMap> - to_string')
        res = 'java.util.HashMap'
        if svm_infer_generics.value != 0:
            key_type, value_type = self.infer_generic_types()
            res += f"<{key_type}, {value_type}>"
        res += f'({self.__size})'
        if self.__skip_children:
            res += ' = {...}'
        if svm_print_address.with_adr:
            res += SVMUtil.adr_str(self.__obj)
        trace(f'<HashMap> - to_string = {res}')
        return res

    def infer_generic_types(self) -> (str, str):
        key_type: list[gdb.Type] = []
        value_type: list[gdb.Type] = []

        for i, kv in enumerate(self, 1):
            key, value = kv
            # if len(*_type) = 1 we could just infer the type java.lang.Object, ignore null values
            if not SVMUtil.is_null(key) and (len(key_type) == 0 or key_type[0] != SVMUtil.object_type):
                key_type = SVMUtil.find_shared_types(key_type, SVMUtil.get_rtt(key))
            if not SVMUtil.is_null(value) and (len(value_type) == 0 or value_type[0] != SVMUtil.object_type):
                value_type = SVMUtil.find_shared_types(value_type, SVMUtil.get_rtt(value))
            if (0 <= svm_infer_generics.value <= i) or (len(key_type) > 0 and key_type[0] == SVMUtil.object_type and
                                                       len(value_type) > 0 and value_type[0] == SVMUtil.object_type):
                break

        key_type_name = '?' if len(key_type) == 0 else SVMUtil.get_unqualified_type_name(key_type[0].name)
        value_type_name = '?' if len(value_type) == 0 else SVMUtil.get_unqualified_type_name(value_type[0].name)

        return key_type_name, value_type_name

    def display_hint(self) -> str:
        trace('<HashMap> - display_hint = map')
        return "map"

    def __iter__(self) -> (gdb.Value, gdb.Value):
        trace('<HashMap> - __iter__')
        for i in range(self.__table_len):
            obj = self.__data[i]
            while not SVMUtil.is_null(obj):
                key = SVMUtil.get_obj_field(obj, 'key')
                value = SVMUtil.get_obj_field(obj, 'value')
                yield key, value
                obj = SVMUtil.get_obj_field(obj, 'next')

    def children(self) -> Iterable[object]:
        trace(f'<HashMap> - children({self.__obj.type} @ {hex(adr(self.__obj))})')
        if self.__skip_children:
            return
        for index, (key, value) in enumerate(self):
            if 0 <= svm_print_element_limit.value <= index:
                yield str(index), '...'
                return
            trace(f'<HashMap> - children({self.__obj.type} @ {hex(adr(self.__obj))})[{index}]')
            yield f"key{index}", SVMUtil.add_selfref(self.__obj, key)
            yield f"value{index}", SVMUtil.add_selfref(self.__obj, value)
        SVMUtil.current_print_depth -= 1


def make_high_level_object(obj: gdb.Value, rtt_name: str) -> gdb.Value:
    try:
        trace(f'try makeHighLevelObject for {rtt_name}')
        hl_rep_class = SVMUtil.hlreps[rtt_name]
        return hl_rep_class(obj)
    except Exception as ex:
        trace(f'<makeHighLevelObject> exception: {ex}')
    return SVMPPClass(obj)


class SVMPrintParam(gdb.Parameter):
    """Use this command to enable/disable SVM pretty printing."""
    set_doc = "Enable/Disable SVM pretty printer."
    show_doc = "Show if SVM pretty printer are enabled/disabled."

    def __init__(self, initial: bool = True):
        super().__init__('svm-print', gdb.COMMAND_DATA, gdb.PARAM_BOOLEAN)
        self.value = initial  # default enabled

    def get_set_string(self):
        return SVMUtil.execout(f"{'enable' if self.value else 'disable'} pretty-printer .* {SVMUtil.pretty_printer_name}")


class SVMPrintStringLimit(gdb.Parameter):
    """Use this command to limit the number of characters in a string shown during pretty printing.
    Does only limit java strings. To limit c strings use 'set print characters'."""
    set_doc = "Set character limit for java strings."
    show_doc = "Show character limit for java strings."

    def __init__(self, initial: int = 200):
        super().__init__('svm-print-string-limit', gdb.COMMAND_DATA, gdb.PARAM_ZUINTEGER_UNLIMITED)
        self.value = initial


class SVMPrintElementLimit(gdb.Parameter):
    """Use this command to limit the number of elements in an array/collection shown during SVM pretty printing.
    Does only limit java arrays and java some java collections. To limit other arrays use 'set print elements'.
    However, 'print elements' also limits the amount of elements for java arrays and java collections.
    If GDBs element limit is below the SVM element limit, printing will be limited by gdb."""
    set_doc = "Set element limit for arrays and collections."
    show_doc = "Show element limit for array and collections."

    def __init__(self, initial: int = 10):
        super().__init__('svm-print-element-limit', gdb.COMMAND_DATA, gdb.PARAM_ZUINTEGER_UNLIMITED)
        self.value = initial

    def get_set_string(self):
        gdb_limit = gdb.parameter("print elements")
        if gdb_limit >= 0 and (self.value > gdb_limit or self.value == -1):
            return f"""The number of elements printed will be limited by GDBs 'print elements' which is {gdb_limit}.
            To increase this limit use 'set print elements <limit>'"""
        else:
            return ""


class SVMPrintFieldLimit(gdb.Parameter):
    """Use this command to limit the number of fields in a java object shown during SVM pretty printing.
    Does only limit java objects. To limit other objects use 'set print elements'.
    However, 'print elements' also limits the amount of fields for java objects.
    If GDBs element limit is below the field limit, field printing will be limited by gdb."""
    set_doc = "Set field limit for objects."
    show_doc = "Show field limit for objects."

    def __init__(self, initial: int = 50):
        super().__init__('svm-print-field-limit', gdb.COMMAND_DATA, gdb.PARAM_ZUINTEGER_UNLIMITED)
        self.value = initial

    def get_set_string(self):
        gdb_limit = gdb.parameter("print elements")
        if gdb_limit >= 0 and (self.value > gdb_limit or self.value == -1):
            return f"""The number of fields printed will be limited by GDBs 'print elements' which is {gdb_limit}.
            To increase this limit use 'set print elements <limit>'"""
        else:
            return ""


class SVMPrintDepthLimit(gdb.Parameter):
    """Use this command to limit the depth at which objects are printed by the SVM pretty printer.
    Does only affect objects that are handled by the SVM pretty printer, similar to selfref checks.
    However, 'print max-depth' also limits the svm print depth.
    If GDBs max-depth limit is below the svm depth limit, printing will be limited by gdb."""
    set_doc = "Set depth limit for svm objects."
    show_doc = "Show depth limit for svm objects."

    def __init__(self, initial: int = 1):
        super().__init__('svm-print-depth-limit', gdb.COMMAND_DATA, gdb.PARAM_ZUINTEGER_UNLIMITED)
        self.value = initial

    def get_set_string(self):
        gdb_limit = gdb.parameter("print max-depth")
        if gdb_limit >= 0 and (self.value > gdb_limit or self.value == -1):
            return f"""The print depth will be limited by GDBs 'print max-depth' which is {gdb_limit}.
            To increase this limit use 'set print max-depth <limit>'"""
        else:
            return ""


class SVMUseHLRepParam(gdb.Parameter):
    """Use this command to enable/disable SVM high level representations.
    Supported high level representations: ArrayList, HashMap"""
    set_doc = "Enable/Disable pretty printing of high level representations."
    show_doc = "Show if SVM pretty printer are enabled/disabled."

    def __init__(self, initial: bool = True):
        super().__init__('svm-use-hlrep', gdb.COMMAND_DATA, gdb.PARAM_BOOLEAN)
        self.value = initial


class SVMInferGenericsParam(gdb.Parameter):
    """Use this command to set the limit of elements used to infer types for collections with generic type parameters.
    (0 for no Inference, -1 for Inference over all elements)"""
    set_doc = "Set limit of elements used for inferring generic type parameters."
    show_doc = "Show limit of elements used for inferring generic type parameters."

    def __init__(self, initial: int = 10):
        super().__init__('svm-infer-generics', gdb.COMMAND_DATA, gdb.PARAM_ZUINTEGER_UNLIMITED)
        self.value = initial


class SVMPrintAddressParam(gdb.Parameter):
    """Use this command to enable/disable additionally printing the addresses."""
    set_doc = "Set additional printing of addresses."
    show_doc = "Show additional printing of addresses."

    def __init__(self, initial: str = 'disable'):
        super().__init__('svm-print-address', gdb.COMMAND_DATA, gdb.PARAM_ENUM, ['on', 'enable', 'absolute', 'disable', 'off'])
        self.with_adr = False
        self.absolute_adr = False
        self.value = initial
        self.set_flags()

    def set_flags(self):
        if self.value == 'disable' or self.value == 'off':
            self.with_adr = False
        elif self.value == 'absolute':
            self.absolute_adr = True
            self.with_adr = True
        else:
            self.absolute_adr = False
            self.with_adr = True

    def get_set_string(self):
        self.set_flags()
        return ""


class SVMCheckSelfrefParam(gdb.Parameter):
    """Use this command to enable/disable cycle detection for pretty printing."""
    set_doc = "Set selfref check."
    show_doc = "Show selfref check."

    def __init__(self, initial: bool = True):
        super().__init__('svm-selfref-check', gdb.COMMAND_DATA, gdb.PARAM_BOOLEAN)
        self.value = initial

    def get_set_string(self):
        # make sure selfrefs are cleared after changing this setting (to avoid unexpected behavior)
        SVMUtil.selfref_cycles.clear()
        return ""


class SVMPrintStaticFieldsParam(gdb.Parameter):
    """Use this command to enable/disable printing of static field members."""
    set_doc = "Set print static fields."
    show_doc = "Show print static fields."

    def __init__(self, initial: bool = False):
        super().__init__('svm-print-static-fields', gdb.COMMAND_DATA, gdb.PARAM_BOOLEAN)
        self.value = initial


class SVMCompleteStaticVariablesParam(gdb.Parameter):
    """Use this command to enable/disable printing of static field members."""
    set_doc = "Set complete static variables."
    show_doc = "Show complete static variables."

    def __init__(self, initial: bool = False):
        super().__init__('svm-complete-static-variables', gdb.COMMAND_DATA, gdb.PARAM_BOOLEAN)
        self.value = initial


class SVMDebugTraceParam(gdb.Parameter):
    """Use this command to enable/disable debug tracing for gdb-debughelpers.py.
    Appends debug logs to the file 'gdb-debughelpers.trace.out' in the current working directory
    (creates the file if it does not exist)."""
    set_doc = "Set debug tracing."
    show_doc = "Show debug tracing."

    def __init__(self, initial: bool = False):
        super().__init__('svm-debug-tracing', gdb.COMMAND_SUPPORT, gdb.PARAM_BOOLEAN)
        self.value = initial
        self.tracefile = open('gdb-debughelpers.trace.out', 'ab', 0) if initial else None

    def get_set_string(self):
        if self.value and self.tracefile is None:
            self.tracefile = open('gdb-debughelpers.trace.out', 'ab', 0)
        elif not self.value and self.tracefile is not None:
            self.tracefile.close()
            self.tracefile = None
        return ""


def load_param(name: str, param_class):
    try:
        return param_class(globals()[name].value)
    except (KeyError, AttributeError):
        return param_class()


svm_print = load_param('svm_print', SVMPrintParam)
svm_print_string_limit = load_param('svm_print_string_limit', SVMPrintStringLimit)
svm_print_element_limit = load_param('svm_print_element_limit', SVMPrintElementLimit)
svm_print_field_limit = load_param('svm_print_field_limit', SVMPrintFieldLimit)
svm_print_depth_limit = load_param('svm_print_depth_limit', SVMPrintDepthLimit)
svm_use_hlrep = load_param('svm_use_hlrep', SVMUseHLRepParam)
svm_infer_generics = load_param('svm_infer_generics', SVMInferGenericsParam)
svm_print_address = load_param('svm_print_address', SVMPrintAddressParam)
svm_check_selfref = load_param('svm_check_selfref', SVMCheckSelfrefParam)
svm_print_static_fields = load_param('svm_print_static_fields', SVMPrintStaticFieldsParam)
svm_complete_static_variables = load_param('svm_complete_static_variables', SVMCompleteStaticVariablesParam)
svm_debug_tracing = load_param('svm_debug_tracing', SVMDebugTraceParam)


class SVMCommandDebugPrettyPrinting(gdb.Command):
    """Use this command to start debugging pretty printing."""

    def __init__(self):
        super().__init__('pdb', gdb.COMMAND_DATA)

    def complete(self, text: str, word: str) -> list[str] | int:
        return gdb.COMPLETE_EXPRESSION

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandDebugPrettyPrinting> - invoke({arg})')
        command = "gdb.execute('print {}')".format(arg.replace("'", "\\'"))
        pdb.run(command)


SVMCommandDebugPrettyPrinting()


class SVMCommandPrint(gdb.Command):
    """Use this command for printing with awareness for java values.
    This command shadows the alias 'p' for GDBs built-in print command if SVM pretty printing is enabled.
    If the expression contains a java value, it is evaluated as such, otherwise GDBs default print command is used"""

    class Token:
        def __init__(self, kind: str = "", val: str = "", start: int = 0, end: int = 0):
            self.kind = kind
            self.val = val
            self.start = start
            self.end = end

    class AutoComplete(RuntimeError):
        def __init__(self, complete: list[str] | int):
            self.complete = complete

    cache = dict()
    scanner = None
    expr = ""
    t: Token = Token()
    la: Token = Token()
    sym: str = ""

    def __init__(self):
        super().__init__('p', gdb.COMMAND_DATA)

    @staticmethod
    def cast_to_rtt(obj: gdb.Value, obj_str: str) -> tuple[gdb.Value, str]:
        static_type = SVMUtil.get_basic_type(obj.type)
        rtt = SVMUtil.get_rtt(obj)
        obj = SVMUtil.cast_to(obj, rtt)
        if static_type.name == rtt.name:
            return obj, obj_str
        else:
            obj_adr = SVMUtil.get_compressed_adr(obj) if SVMUtil.is_compressed(rtt) else adr(obj)
            return obj, f"(('{rtt.name}' *)({obj_adr}))"

    # Define the token specifications
    token_specification = [
        ('IDENT', r'\$?[a-zA-Z_][a-zA-Z0-9_]*'),  # identifier (convenience variables may contain $)
        ('QIDENT', r"'\$?[a-zA-Z_][a-zA-Z0-9_.:]*'"),  # quoted identifier
        ('FA', r'\.'),  # field access
        ('LBRACK', r'\['),  # opening bracket
        ('RBRACK', r'\]'),  # closing bracket
        ('LPAREN', r'\('),  # opening parentheses
        ('RPAREN', r'\)'),  # closing parentheses
        ('COMMA', r','),  # comma
        ('SKIP', r'\s+'),  # skip over whitespaces
        ('OTHER', r'.'),  # any other character, will be handled by gdb
    ]
    token_regex = '|'.join(f'(?P<{kind}>{regex})' for (kind, regex) in token_specification)

    def tokenize(self, expr: str) -> Iterable[Token]:
        for match in re.finditer(self.token_regex, expr):
            kind = match.lastgroup
            val = match.group()
            if kind == 'SKIP':
                # skip whitespaces
                continue
            yield self.Token(kind, val, match.start(), match.end())

    def setup_scanner(self, expr: str) -> None:
        self.scanner = iter(self.tokenize(expr))
        self.t = self.Token()
        self.la = self.Token()
        self.sym = ""
        self.expr = expr

    def scan(self):
        self.t = self.la
        self.la = next(self.scanner, self.Token())
        self.sym = self.la.kind

    def check(self, expected: str):
        if self.sym == expected:
            self.scan()
        else:
            raise RuntimeError(f"{expected} expected after {self.expr[:self.t.end]} but got {self.sym}")

    def parse(self, completion: bool = False) -> str:
        self.scan()
        if self.sym == "" and completion:
            raise self.AutoComplete(gdb.COMPLETE_EXPRESSION)
        expr = self.expression(completion)
        self.check("")
        return expr

    def expression(self, completion: bool = False) -> str:
        expr = ""
        while self.sym != "":
            if self.sym == "IDENT":
                expr += self.object(completion)
            else:
                # ignore everything that does not start with an identifier
                self.scan()
                expr += self.t.val
        return expr

    def object(self, completion: bool = False) -> str:
        self.scan()
        if self.sym == "" and completion:
            raise self.AutoComplete(gdb.COMPLETE_EXPRESSION)

        obj_str = self.t.val
        if obj_str in self.cache:
            obj, obj_str = self.cache[obj_str]
        else:
            try:
                obj = gdb.parse_and_eval(obj_str)
            except gdb.error:
                # could not parse obj_str as obj -> let gdb deal with it later
                return self.t.val
            base_obj_str = obj_str
            if not SVMUtil.is_primitive(obj.type) and SVMUtil.is_java_type(obj.type):
                obj, obj_str = SVMCommandPrint.cast_to_rtt(obj, obj_str)
            self.cache[base_obj_str] = (obj, obj_str)

        while self.sym == "FA" or self.sym == "LPAREN" or self.sym == "LBRACK":
            if self.sym == "FA":
                self.scan()
                if not completion:
                    self.check("IDENT")
                else:
                    # handle auto-completion after field access
                    fields = SVMUtil.get_all_fields(obj.type, svm_complete_static_variables.value)
                    funcs = SVMUtil.get_all_member_functions(obj.type, svm_complete_static_variables.value, False)
                    field_names = set(f.name for f in fields)
                    func_names = set(f.name for f in funcs)
                    complete_set = field_names.union(func_names)
                    if self.sym == "":
                        raise self.AutoComplete(list(complete_set))
                    self.check("IDENT")
                    if self.sym == "":
                        raise self.AutoComplete([c for c in complete_set if c.startswith(self.t.val)])
                obj = obj[self.t.val]
                obj_str += "." + self.t.val
                base_obj_str = obj_str
                if not SVMUtil.is_primitive(obj.type) and SVMUtil.is_java_type(obj.type):
                    obj, obj_str = SVMCommandPrint.cast_to_rtt(obj, obj_str)
                self.cache[base_obj_str] = (obj, obj_str)
            elif self.sym == "LPAREN":
                if obj.type.code != gdb.TYPE_CODE_METHOD:
                    raise RuntimeError(f"Method object expected at: {self.expr[:self.t.end]}")
                self.scan()
                param_str = self.params(completion)
                self.check("RPAREN")
                this, _, func_name = obj_str.rpartition('.')
                if this in self.cache:
                    this_obj, this = self.cache[this]
                else:
                    this_obj = gdb.parse_and_eval(this)
                if this_obj.type.code == gdb.TYPE_CODE_PTR:
                    obj_str = f"{this}->{func_name}"
                obj_str += f"({param_str})"
                obj = gdb.parse_and_eval(obj_str)
                base_obj_str = obj_str
                if not SVMUtil.is_primitive(obj.type) and SVMUtil.is_java_type(obj.type):
                    obj, obj_str = SVMCommandPrint.cast_to_rtt(obj, obj_str)
                self.cache[base_obj_str] = (obj, obj_str)
            elif self.sym == "LBRACK":
                if (not obj.type.is_array_like) and not isinstance(gdb.default_visualizer(obj), SVMPPArray):
                    raise RuntimeError(f"Array object expected at: {self.expr[:self.t.end]}")
                self.scan()
                i_obj_str = self.array_index(completion)
                if self.sym == "" and completion:
                    # handle autocompletion for array index
                    if SVMUtil.is_java_type(obj.type) and (i_obj_str == '' or i_obj_str.isnumeric()):
                        index = 0 if i_obj_str == '' else int(i_obj_str)
                        length = SVMUtil.get_int_field(obj, 'len')
                        complete = []
                        if index < length:
                            complete.append(f'{index}]')
                            if index + 1 < length:
                                complete.append(f'{index + 1}]')
                                if index + 2 < length:
                                    complete.append(f'{length - 1}]')
                        raise self.AutoComplete(complete)
                    else:
                        raise self.AutoComplete(gdb.COMPLETE_EXPRESSION)
                if i_obj_str in self.cache:
                    i_obj, i_obj_str = self.cache[i_obj_str]
                else:
                    i_obj = gdb.parse_and_eval(i_obj_str)
                self.check('RBRACK')
                if SVMUtil.is_java_type(obj.type):
                    obj_str += ".data"
                    obj = SVMUtil.get_obj_field(obj, 'data', obj)
                if isinstance(gdb.default_visualizer(i_obj), SVMPPBoxedPrimitive) or SVMUtil.is_primitive(i_obj.type):
                    if isinstance(gdb.default_visualizer(i_obj), SVMPPBoxedPrimitive):
                        index = SVMUtil.get_int_field(i_obj, 'value')
                    else:
                        index = int(i_obj)
                    obj_str += f"[{index}]"
                    obj = obj[index]
                    base_obj_str = obj_str
                    if not SVMUtil.is_primitive(obj.type) and SVMUtil.is_java_type(obj.type):
                        obj, obj_str = SVMCommandPrint.cast_to_rtt(obj, obj_str)
                    self.cache[base_obj_str] = (obj, obj_str)
                else:
                    # let gdb figure out what to do
                    obj_str += f"[{i_obj_str}]"
                    if obj_str in self.cache:
                        obj, obj_str = self.cache[obj_str]
                    else:
                        obj = gdb.parse_and_eval(obj_str)
                        base_obj_str = obj_str
                        if not SVMUtil.is_primitive(obj.type) and SVMUtil.is_java_type(obj.type):
                            obj, obj_str = SVMCommandPrint.cast_to_rtt(obj, obj_str)
                        self.cache[base_obj_str] = (obj, obj_str)

        if isinstance(gdb.default_visualizer(obj), SVMPPBoxedPrimitive):
            obj_str += ".value"

        return obj_str

    def params(self, completion: bool = False) -> str:
        param_str = ""
        while self.sym != "RPAREN" and self.sym != "":
            obj_str = ""
            while self.sym != "RPAREN" and self.sym != "COMMA" and self.sym != "":
                if self.sym == "IDENT":
                    obj_str += self.object(completion)
                else:
                    self.scan()
                    obj_str += self.t.val

            obj = gdb.parse_and_eval(obj_str)  # check if gdb can handle the current param
            if SVMUtil.is_java_type(obj.type) and SVMUtil.is_compressed(obj.type):
                # uncompress compressed java params
                obj_str = f"(('{SVMUtil.get_uncompressed_type(SVMUtil.get_basic_type(obj.type)).name}' *)({adr(obj)}))"
            param_str += obj_str
            if self.sym == "COMMA":
                self.scan()
                param_str += self.t.val
        if self.sym == "" and completion:
            # handle autocompletion for params
            if self.t.kind == "LPAREN" or self.t.kind == "COMMA":  # no open object access
                raise self.AutoComplete(gdb.COMPLETE_EXPRESSION)
            else:
                raise self.AutoComplete(gdb.COMPLETE_NONE)
        return param_str

    def array_index(self, completion: bool = False) -> str:
        i_obj_str = ""
        while self.sym != "RBRACK" and self.sym != "":
            if self.sym == "IDENT":
                i_obj_str += self.object(completion)
            else:
                self.scan()
                i_obj_str += self.t.val
        return i_obj_str

    def complete(self, text: str, word: str) -> list[str]:
        if not svm_print.value:
            return gdb.COMPLETE_EXPRESSION

        self.setup_scanner(text)
        try:
            self.parse(completion=True)
        except self.AutoComplete as ac:
            trace(f"<SVMCommandPrint> - complete({text}, {word}) -- autocomplete result: {ac.complete}")
            return ac.complete
        trace(f"<SVMCommandPrint> - complete({text}, {word}) -- no completion possible")
        return gdb.COMPLETE_NONE

    def invoke(self, arg: str, from_tty: bool) -> None:
        if not svm_print.value:
            gdb.execute(f"print {arg}")
            return

        output_format = ""
        if arg.startswith('/'):
            output_format, _, arg = arg.partition(' ')
        self.setup_scanner(arg)
        expr = self.parse()
        trace(f"<SVMCommandPrint> - invoke({arg}) -- parsed arg: {expr}")
        # handle print call as if it was a new prompt
        SVMUtil.prompt_hook()
        # let gdb evaluate the modified expression
        gdb.execute(f"print{output_format} {expr}", False, False)


SVMCommandPrint()


class SVMCommandBreak(gdb.Command):
    def __init__(self):
        super().__init__('b', gdb.COMMAND_BREAKPOINTS, gdb.COMPLETE_LOCATION)

    def invoke(self, arg: str, from_tty: bool) -> None:
        args = gdb.string_to_argv(arg) + ['']  # add an empty arg to avoid IndexError if arg is empty
        # first argument can either be line number, symbol name, empty, or if condition
        # # -> :: to make breakpoints work with IntelliJ function name notation
        args[0] = args[0].replace('#', '::')
        # let gdb execute the full break command with the updated symbol name
        gdb.execute(f"break {''.join(args)}", False, False)
        trace(f"<SVMCommandBreak> - invoke({arg}) -- invoked: break {''.join(args)}")


SVMCommandBreak()


class SVMFrameUnwinder(gdb.unwinder.Unwinder):
    AMD64_RBP = 6
    AMD64_RSP = 7
    AMD64_RIP = 16

    def __init__(self):
        super().__init__('SubstrateVM FrameUnwinder')
        self.stack_type = gdb.lookup_type('long')
        self.deopt_frame_type = gdb.lookup_type('com.oracle.svm.core.deopt.DeoptimizedFrame')

    def __call__(self, pending_frame):
        if SVMUtil.deopt_stub_adr == 0:
            # find deopt stub after its properly loaded
            SVMUtil.deopt_stub_adr = gdb.lookup_global_symbol('com.oracle.svm.core.deopt.Deoptimizer::deoptStub',
                                                              gdb.SYMBOL_VAR_DOMAIN).value().address

        try:
            rsp = pending_frame.read_register('sp')
            rip = pending_frame.read_register('pc')
            if int(rip) == SVMUtil.deopt_stub_adr:
                deopt_frame_stack_slot = rsp.cast(self.stack_type.pointer()).dereference()
                deopt_frame = deopt_frame_stack_slot.cast(self.deopt_frame_type.pointer())
                source_frame_size = deopt_frame['sourceTotalFrameSize']
                # Now find the register-values for the caller frame
                unwind_info = pending_frame.create_unwind_info(gdb.unwinder.FrameId(rsp, rip))
                caller_rsp = rsp + int(source_frame_size)
                unwind_info.add_saved_register(self.AMD64_RSP, gdb.Value(caller_rsp))
                caller_rip = gdb.Value(caller_rsp - 8).cast(self.stack_type.pointer()).dereference()
                unwind_info.add_saved_register(self.AMD64_RIP, gdb.Value(caller_rip))
                return unwind_info
        except Exception as e:
            print(e)
            # Fallback to default frame unwinding via debug_frame (dwarf)

        return None


class SVMFrameFilter():
    def __init__(self):
        self.name = "SubstrateVM FrameFilter"
        self.priority = 100
        self.enabled = True

    def filter(self, frame_iter):
        for frame in frame_iter:
            frame = frame.inferior_frame()
            if SVMUtil.deopt_stub_adr and frame.pc() == SVMUtil.deopt_stub_adr:
                yield SVMFrameDeopt(frame)
            else:
                yield SVMFrame(frame)


class SVMFrame(FrameDecorator):
    def function(self):
        frame = self.inferior_frame()
        if not frame.name():
            return 'Unknown Frame at ' + hex(int(frame.read_register('sp')))
        func_name = str(frame.name().split('(')[0])
        if frame.type() == gdb.INLINE_FRAME:
            func_name = '<-- ' + func_name

        filename = self.filename()
        if filename:
            line = self.line()
            if line is None:
                line = 0
            eclipse_filename = '(' + os.path.basename(filename) + ':' + str(line) + ')'
        else:
            eclipse_filename = ''

        return func_name + eclipse_filename


class SVMFrameDeopt(SVMFrame):
    def function(self):
        return '[DEOPT FRAMES ...]'

    def frame_args(self):
        return None

    def frame_locals(self):
        return None


try:
    svminitfile = os.path.expandvars('${SVMGDBINITFILE}')
    exec(open(svminitfile).read())
    trace(f'successfully processed svminitfile: {svminitfile}')
except Exception as e:
    trace(f'<exception in svminitfile execution: {e}>')

try:
    gdb.prompt_hook = SVMUtil.prompt_hook
    svm_objfile = gdb.current_objfile()
    # Only if we have an objfile and an SVM specific symbol we consider this an SVM objfile
    if svm_objfile and svm_objfile.lookup_global_symbol("com.oracle.svm.core.Isolates", gdb.SYMBOL_TYPES_DOMAIN):
        gdb.printing.register_pretty_printer(svm_objfile, SVMPrettyPrinter(), True)

        # deopt stub points to the wrong address at first -> set dummy value to fill later (0 from SVMUtil)
        deopt_stub_available = gdb.lookup_global_symbol('com.oracle.svm.core.deopt.Deoptimizer::deoptStub',
                                                        gdb.SYMBOL_VAR_DOMAIN)

        if deopt_stub_available:
            SVMUtil.frame_unwinder = SVMFrameUnwinder()
            gdb.unwinder.register_unwinder(svm_objfile, SVMUtil.frame_unwinder)

        SVMUtil.frame_filter = SVMFrameFilter()
        svm_objfile.frame_filters[SVMUtil.frame_filter.name] = SVMUtil.frame_filter
    else:
        print(f'Warning: Load {os.path.basename(__file__)} only in the context of an SVM objfile')
        # fallback (e.g. if loaded manually -> look through all objfiles and attach pretty printer)
        for of in gdb.objfiles():
            if of.lookup_global_symbol("com.oracle.svm.core.Isolates", gdb.SYMBOL_TYPES_DOMAIN):
                gdb.printing.register_pretty_printer(of, SVMPrettyPrinter(), True)

    # save and restore SVM pretty printer for reloaded objfiles (e.g. shared libraries)
    def new_objectfile(new_objfile_event):
        objfile = new_objfile_event.new_objfile
        if objfile.filename in SVMUtil.pretty_print_objfiles:
            gdb.printing.register_pretty_printer(objfile, SVMPrettyPrinter(), True)

    def free_objectfile(free_objfile_event):
        objfile = free_objfile_event.objfile
        if any(pp.name == SVMUtil.pretty_printer_name for pp in objfile.pretty_printers):
            SVMUtil.pretty_print_objfiles.add(objfile.filename)

    gdb.events.new_objfile.connect(new_objectfile)
    gdb.events.free_objfile.connect(free_objectfile)


except Exception as e:
    print(f'<exception in gdb-debughelpers initialization: {e}>')
