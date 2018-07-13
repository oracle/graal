/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

public enum CompareOperator {
    FP_FALSE,
    FP_ORDERED_EQUAL,
    FP_ORDERED_GREATER_THAN,
    FP_ORDERED_GREATER_OR_EQUAL,
    FP_ORDERED_LESS_THAN,
    FP_ORDERED_LESS_OR_EQUAL,
    FP_ORDERED_NOT_EQUAL,
    FP_ORDERED,
    FP_UNORDERED,
    FP_UNORDERED_EQUAL,
    FP_UNORDERED_GREATER_THAN,
    FP_UNORDERED_GREATER_OR_EQUAL,
    FP_UNORDERED_LESS_THAN,
    FP_UNORDERED_LESS_OR_EQUAL,
    FP_UNORDERED_NOT_EQUAL,
    FP_TRUE,

    INT_EQUAL,
    INT_NOT_EQUAL,
    INT_UNSIGNED_GREATER_THAN,
    INT_UNSIGNED_GREATER_OR_EQUAL,
    INT_UNSIGNED_LESS_THAN,
    INT_UNSIGNED_LESS_OR_EQUAL,
    INT_SIGNED_GREATER_THAN,
    INT_SIGNED_GREATER_OR_EQUAL,
    INT_SIGNED_LESS_THAN,
    INT_SIGNED_LESS_OR_EQUAL;
}
