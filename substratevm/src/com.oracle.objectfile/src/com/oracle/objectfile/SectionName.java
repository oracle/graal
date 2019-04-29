/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile;

import java.util.HashMap;
import java.util.Map;

import com.oracle.objectfile.ObjectFile.Format;

/**
 * Some common section names, and code to convert to/from their platform-dependent (or rather,
 * format-specific) string representations.
 */
public abstract class SectionName {

    private static class ProgbitsSectionName extends SectionName {

        ProgbitsSectionName(String name) {
            super(name);
        }

    }

    private static class NobitsSectionName extends SectionName {

        NobitsSectionName(String name) {
            super(name);
        }

    }

    /*
     * This was an enum, but in reality it needs to be extensible, so we fake an enum in class form.
     */
    // "standard" section names
    public static final SectionName DATA = new ProgbitsSectionName("data");
    public static final SectionName RODATA = new ProgbitsSectionName("rodata") {

        // On Mach-O, the read-only data goes in the __DATA,__const section.

        @Override
        public String getSegmentName(Format f) {
            final String result;
            if (f == Format.MACH_O) {
                result = "__DATA";
            } else {
                result = super.getSegmentName(f);
            }
            return result;
        }

        @Override
        public String getFormatDependentName(Format f) {
            final String result;
            if (f == Format.MACH_O) {
                result = "__const";
            } else {
                result = super.getFormatDependentName(f);
            }
            return result;
        }

    };
    public static final SectionName TEXT = new ProgbitsSectionName("text");
    public static final SectionName BSS = new NobitsSectionName("bss");
    public static final SectionName SVM_HEAP = new ProgbitsSectionName("svm_heap");
    // proprietary
    public static final SectionName APPLE_NAMES = new ProgbitsSectionName("apple_names");
    public static final SectionName APPLE_TYPES = new ProgbitsSectionName("apple_types");
    // not a typo!
    public static final SectionName APPLE_NAMESPACE = new ProgbitsSectionName("apple_namespac");
    public static final SectionName APPLE_OBJC = new ProgbitsSectionName("apple_objc");
    public static final SectionName LLVM_STACKMAPS = new ProgbitsSectionName("llvm_stackmaps");

    private static final SectionName[] myValues;

    static {
        myValues = new SectionName[]{DATA, RODATA, TEXT, BSS, APPLE_NAMES, APPLE_TYPES, APPLE_NAMESPACE, APPLE_OBJC, LLVM_STACKMAPS};
    }

    private static String getFormatPrefix(ObjectFile.Format f) {
        switch (f) {
            case ELF:
            case PECOFF:
                return ".";
            case MACH_O:
                return "__";
            default:
                throw new IllegalStateException("unsupported format: " + f);
        }
    }

    /**
     * Return a "suggested" segment name for sections of this name, given an object file format.
     * Some object file formats (Mach-O) require that all sections exist within a segment. Others
     * (ELF) impose no such requirement. For the latter formats, this will return null (noting that
     * ELF does not even have segment names). For the former, it will return some segment name. By
     * default, Mach-O segment names are the section name uppercased. Note that the value returned
     * by this function is designed to be passed to the (String, String, ...) overload of
     * new{UserDefined,Progbits,Nobits}Section in ObjectFile.
     *
     * @param f
     * @return a segment name, or null if none is necessary
     */
    public String getSegmentName(ObjectFile.Format f) {
        // default implementation
        switch (f) {
            case MACH_O:
                return getFormatDependentName(f).toUpperCase();
            default:
            case ELF:
                return null;
        }
    }

    protected static final Map<String, SectionName> NAMES_MAP = new HashMap<>();

    static {
        for (SectionName name : myValues) {
            /*
             * We indiscriminately stuff into the map: 1. the format-independent name; 2. the
             * format-dependent names, for all formats.
             */
            for (Format f : ObjectFile.Format.values()) {
                NAMES_MAP.put(name.getFormatDependentName(f), name);
            }
        }
    }

    // this is the string argument to our constructed values, above,
    // e.g. TEXT("text") <-- it's "text"
    protected final String platformIndependentName;

    public String getPlatformIndependentName() {
        return this.platformIndependentName;
    }

    protected SectionName(String n) {
        platformIndependentName = n;
    }

    public String getFormatDependentName(Format f) {
        return getFormatPrefix(f) + platformIndependentName;
    }

    public static SectionName from(String n) {
        final SectionName result = NAMES_MAP.get(n);
        return result;
    }

}
