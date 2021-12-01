/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.exception;

public enum Failure {
    // TODO(mbovel): replace UNSPECIFIED_MALFORMED usages with appropriate errors.
    UNSPECIFIED_MALFORMED(Type.MALFORMED, "unspecified"),
    INTEGER_REPRESENTATION_TOO_LONG(Type.MALFORMED, "integer representation too long"),
    INTEGER_TOO_LONG(Type.MALFORMED, "integer too large"),
    UNEXPECTED_END(Type.MALFORMED, "unexpected end of section or function"),
    MALFORMED_VALUE_TYPE(Type.MALFORMED, "malformed value type"),
    INVALID_MAGIC_NUMBER(Type.MALFORMED, "magic header not detected"),
    INVALID_VERSION_NUMBER(Type.MALFORMED, "unknown binary version"),
    ZERO_FLAG_EXPECTED(Type.MALFORMED, "zero flag expected"),
    SECTION_SIZE_MISMATCH(Type.MALFORMED, "section size mismatch"),
    TOO_MANY_LOCALS(Type.MALFORMED, "too many locals"),
    FUNCTIONS_CODE_INCONSISTENT_LENGTHS(Type.MALFORMED, "function and code section have inconsistent lengths"),
    MALFORMED_UTF8(Type.MALFORMED, "malformed UTF-8 encoding"),
    MALFORMED_SECTION_ID(Type.MALFORMED, "malformed section id"),
    MALFORMED_MUTABILITY(Type.MALFORMED, "malformed mutability"),
    LENGTH_OUT_OF_BOUNDS(Type.MALFORMED, "length out of bounds"),
    // GraalWasm-specific:
    DUPLICATED_SECTION(Type.MALFORMED, "duplicated section"),
    INVALID_SECTION_ORDER(Type.MALFORMED, "invalid section order"),

    // TODO(mbovel): replace UNSPECIFIED_INVALID usages with appropriate errors.
    UNSPECIFIED_INVALID(Type.INVALID, "unspecified"),
    TYPE_MISMATCH(Type.INVALID, "type mismatch"),
    INVALID_RESULT_ARITY(Type.INVALID, "invalid result arity"),
    MULTIPLE_MEMORIES(Type.INVALID, "multiple memories"),
    MULTIPLE_TABLES(Type.INVALID, "multiple tables"),
    LOOP_INPUT(Type.INVALID, "non-empty loop input type"),
    UNKNOWN_LOCAL(Type.INVALID, "unknown local"),
    UNKNOWN_GLOBAL(Type.INVALID, "unknown global"),
    UNKNOWN_MEMORY(Type.INVALID, "unknown memory"),
    UNKNOWN_TABLE(Type.INVALID, "unknown table"),
    UNKNOWN_LABEL(Type.INVALID, "unknown label"),
    UNKNOWN_FUNCTION(Type.INVALID, "unknown function"),
    UNKNOWN_TYPE(Type.INVALID, "unknown type"),
    START_FUNCTION_RETURN_VALUE(Type.INVALID, "start function"),
    START_FUNCTION_ARGUMENTS(Type.INVALID, "start function"),
    LIMIT_MINIMUM_GREATER_THAN_MAXIMUM(Type.INVALID, "size minimum must not be greater than maximum"),
    DUPLICATE_EXPORT(Type.INVALID, "duplicate export name"),
    IMMUTABLE_GLOBAL_WRITE(Type.INVALID, "global is immutable"),
    CONSTANT_EXPRESSION_REQUIRED(Type.INVALID, "constant expression required"),
    LIMIT_EXCEEDED(Type.INVALID, "limit exceeded"),
    MEMORY_SIZE_LIMIT_EXCEEDED(Type.INVALID, "memory size must be at most 65536 pages (4GiB)"),
    ALIGNMENT_LARGER_THAN_NATURAL(Type.INVALID, "alignment must not be larger than natural"),
    UNEXPECTED_END_OF_BLOCK(Type.INVALID, "cannot exit unspecified block"),

    // GraalWasm-specific:
    MODULE_SIZE_LIMIT_EXCEEDED(Type.INVALID, "module size exceeds limit"),
    TYPE_COUNT_LIMIT_EXCEEDED(Type.INVALID, "type count exceeds limit"),
    FUNCTION_COUNT_LIMIT_EXCEEDED(Type.INVALID, "function count exceeds limit"),
    IMPORT_COUNT_LIMIT_EXCEEDED(Type.INVALID, "import count exceeds limit"),
    EXPORT_COUNT_LIMIT_EXCEEDED(Type.INVALID, "export count exceeds limit"),
    GLOBAL_COUNT_LIMIT_EXCEEDED(Type.INVALID, "global count exceeds limit"),
    DATA_SEGMENT_COUNT_LIMIT_EXCEEDED(Type.INVALID, "data segment count exceeds limit"),
    ELEMENT_SEGMENT_COUNT_LIMIT_EXCEEDED(Type.INVALID, "element segment count exceeds limit"),
    FUNCTION_SIZE_LIMIT_EXCEEDED(Type.INVALID, "function size exceeds limit"),
    PARAMETERS_COUNT_LIMIT_EXCEEDED(Type.INVALID, "parameters count exceeds limit"),
    RETURN_COUNT_LIMIT_EXCEEDED(Type.INVALID, "return values count exceeds limit"),

    // TODO(mbovel): replace UNSPECIFIED_UNLINKABLE usages with appropriate errors.
    UNSPECIFIED_UNLINKABLE(Type.UNLINKABLE, "unspecified"),
    UNKNOWN_IMPORT(Type.UNLINKABLE, "unknown import"),
    INCOMPATIBLE_IMPORT_TYPE(Type.UNLINKABLE, "incompatible import type"),
    ELEMENTS_SEGMENT_DOES_NOT_FIT(Type.UNLINKABLE, "elements segment does not fit"),
    DATA_SEGMENT_DOES_NOT_FIT(Type.UNLINKABLE, "data segment does not fit"),
    // GraalWasm-specific:
    INVALID_WASI_DIRECTORIES_MAPPING(Type.UNLINKABLE, "invalid wasi directories mapping"),

    // TODO(mbovel): replace UNSPECIFIED_TRAP usages with appropriate errors.
    UNSPECIFIED_TRAP(Type.TRAP, "unspecified"),
    INT_DIVIDE_BY_ZERO(Type.TRAP, "integer divide by zero"),
    INT_OVERFLOW(Type.TRAP, "integer overflow"),
    INVALID_CONVERSION_TO_INT(Type.TRAP, "invalid conversion to integer"),
    UNREACHABLE(Type.TRAP, "unreachable"),
    UNDEFINED_ELEMENT(Type.TRAP, "undefined element"),
    UNINITIALIZED_ELEMENT(Type.TRAP, "uninitialized element"),
    OUT_OF_BOUNDS_MEMORY_ACCESS(Type.TRAP, "out of bounds memory access"),
    INDIRECT_CALL_TYPE__MISMATCH(Type.TRAP, "indirect call type mismatch"),
    // GraalWasm-specific:
    TABLE_INSTANCE_SIZE_LIMIT_EXCEEDED(Type.TRAP, "table instance size exceeds limit"),
    MEMORY_INSTANCE_SIZE_LIMIT_EXCEEDED(Type.TRAP, "memory instance size exceeds limit"),

    CALL_STACK_EXHAUSTED(Type.EXHAUSTION, "call stack exhausted"),
    MEMORY_ALLOCATION_FAILED(Type.EXHAUSTION, "could not allocate memory"),

    // TODO(mbovel): replace UNSPECIFIED_INTERNAL usages with assertInternal/shouldNotReachHere.
    UNSPECIFIED_INTERNAL(Type.INTERNAL, "unspecified");

    public enum Type {
        TRAP("trap"),
        EXHAUSTION("exhaustion"),
        MALFORMED("malformed"),
        INVALID("invalid"),
        UNLINKABLE("unlinkable"),
        INTERNAL("internal");

        public final String name;

        Type(String name) {
            this.name = name;
        }
    }

    public final Type type;
    public final String name;

    Failure(Type type, String name) {
        this.type = type;
        this.name = name;
    }

}
