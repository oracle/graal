/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.Optional;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * System lookups allow to search for symbols in a fixed set of OS-dependent, standard, "curated"
 * libraries. The provided libraries are not really defined in the documentation, so the best we can
 * do is load the exact same libraries as HotSpot.
 */
@TargetClass(className = "jdk.internal.foreign.SystemLookup", onlyWith = ForeignAPIPredicates.Enabled.class)
public final class Target_jdk_internal_foreign_SystemLookup {
    // Checkstyle: stop

    /*
     * This field must be cleared because on Windows, it references a closure which contains a
     * native memory segment.
     */
    @Alias //
    @RecomputeFieldValue(isFinal = true, kind = Kind.Reset) //
    static SymbolLookup SYSTEM_LOOKUP;

    @SuppressWarnings("static-method")
    @Substitute
    public Optional<MemorySegment> find(String name) {
        return RuntimeSystemLookup.INSTANCE.find(name);
    }
    // Checkstyle: resume
}

/*
 * IMPORTANT: If the substitution target (i.e. enum
 * 'jdk.internal.foreign.SystemLookup$WindowsFallbackSymbols') changes, ensure that the enum values
 * are still in sync with 'com.oracle.svm.native.libchelper/src/syslookup.c'.
 */
@TargetClass(className = "jdk.internal.foreign.SystemLookup", innerClass = "WindowsFallbackSymbols", onlyWith = ForeignAPIPredicates.Enabled.class)
final class Target_jdk_internal_foreign_SystemLookup_WindowsFallbackSymbols {
}
