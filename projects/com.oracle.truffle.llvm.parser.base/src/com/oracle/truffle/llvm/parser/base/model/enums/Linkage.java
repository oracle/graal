/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.base.model.enums;

public enum Linkage {

    EXTERNAL("external"),
    WEAK("weak"),
    APPENDING("appending"),
    INTERNAL("internal"),
    LINKONCE("linkonce"),
    DLL_IMPORT("dllimport"),
    DLL_EXPORT("dllexport"),
    EXTERN_WEAK("extern_weak"),
    COMMON("common"),
    PRIVATE("private"),
    WEAK_ODR("weak_odr"),
    LINK_ONCE_ODR("linkonce_odr"),
    AVAILABLE_EXTERNALLY("available_externally"),
    LINKER_PRIVATE("linker_private"),
    LINKER_PRIVATE_WEAK("linker_private_weak"),
    LINK_ONCE_ODR_AUTO_HIDE("linkonce_odr_auto_hide"),
    UNKNOWN(""); // TODO: required by LLVM IR Parser, should be removed when no longer needed

    private final String irString;

    Linkage(String irString) {
        this.irString = irString;
    }

    public static Linkage decode(long value) {
        return values()[(int) value];
    }

    public String getIrString() {
        return irString;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
