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
package com.oracle.truffle.llvm.parser.model.enums;

public enum Linkage {

    EXTERNAL("external", 0L),
    WEAK("weak", 1L, 16L),
    APPENDING("appending", 2L),
    INTERNAL("internal", 3L),
    LINKONCE("linkonce", 4L, 18L),
    DLL_IMPORT("dllimport", 5L),
    DLL_EXPORT("dllexport", 6L),
    EXTERN_WEAK("extern_weak", 7L),
    COMMON("common", 8L),
    PRIVATE("private", 9L),
    WEAK_ODR("weak_odr", 10L, 17L),
    LINK_ONCE_ODR("linkonce_odr", 11L, 19L),
    AVAILABLE_EXTERNALLY("available_externally", 12L),
    LINKER_PRIVATE("linker_private", 13L),
    LINKER_PRIVATE_WEAK("linker_private_weak", 14L),
    LINK_ONCE_ODR_AUTO_HIDE("linkonce_odr_auto_hide", 15L);

    private static final Linkage[] VALUES = values();

    private final String irString;

    private final long[] encodedValue;

    Linkage(String irString, long... encodedValue) {
        this.irString = irString;
        this.encodedValue = encodedValue;
    }

    private long[] getEncodedValue() {
        return encodedValue;
    }

    public static Linkage decode(long value) {
        for (Linkage linkage : VALUES) {
            for (long l : linkage.getEncodedValue()) {
                if (l == value) {
                    return linkage;
                }
            }
        }
        return EXTERNAL;
    }

    public String getIrString() {
        return irString;
    }

    public static boolean isExported(Linkage linkage, Visibility visibility) {
        switch (linkage) {
            case AVAILABLE_EXTERNALLY:
            case DLL_IMPORT:
                return true;
            case EXTERNAL:
            case DLL_EXPORT:
            case WEAK:
            case APPENDING:
            case LINKONCE:
            case EXTERN_WEAK:
            case COMMON:
            case WEAK_ODR:
            case LINK_ONCE_ODR:
                return visibility == Visibility.DEFAULT || visibility == Visibility.PROTECTED;
            case INTERNAL:
            case PRIVATE:
            case LINKER_PRIVATE:
            case LINKER_PRIVATE_WEAK:
            case LINK_ONCE_ODR_AUTO_HIDE:
                return false;
            default:
                throw new IllegalStateException("Unknown linkage: " + linkage);
        }
    }

    public static boolean isOverridable(Linkage linkage, Visibility visibility) {
        switch (linkage) {
            case AVAILABLE_EXTERNALLY:
            case DLL_IMPORT:
                return true;
            case EXTERNAL:
            case DLL_EXPORT:
            case WEAK:
            case APPENDING:
            case LINKONCE:
            case EXTERN_WEAK:
            case COMMON:
            case WEAK_ODR:
            case LINK_ONCE_ODR:
                return visibility == Visibility.DEFAULT;
            case INTERNAL:
            case PRIVATE:
            case LINKER_PRIVATE:
            case LINKER_PRIVATE_WEAK:
            case LINK_ONCE_ODR_AUTO_HIDE:
                return false;
            default:
                throw new IllegalStateException("Unknown linkage: " + linkage);
        }
    }

    public static boolean isExternal(Linkage linkage) {
        switch (linkage) {
            case AVAILABLE_EXTERNALLY:
            case DLL_IMPORT:
                return true;
            case EXTERNAL:
            case EXTERN_WEAK:
            case WEAK:
            case APPENDING:
            case LINKONCE:
            case DLL_EXPORT:
            case COMMON:
            case WEAK_ODR:
            case LINK_ONCE_ODR:
            case INTERNAL:
            case PRIVATE:
            case LINKER_PRIVATE:
            case LINKER_PRIVATE_WEAK:
            case LINK_ONCE_ODR_AUTO_HIDE:
                return false;
            default:
                throw new IllegalStateException("Unknown linkage: " + linkage);
        }
    }
}
