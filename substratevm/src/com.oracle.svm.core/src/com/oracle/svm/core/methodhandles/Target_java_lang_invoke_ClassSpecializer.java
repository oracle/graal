/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.methodhandles;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.lang.invoke.ClassSpecializer")
final class Target_java_lang_invoke_ClassSpecializer {
    @Alias
    native Target_java_lang_invoke_ClassSpecializer_SpeciesData findSpecies(Object ll);
}

@TargetClass(className = "java.lang.invoke.ClassSpecializer", innerClass = "SpeciesData")
final class Target_java_lang_invoke_ClassSpecializer_SpeciesData {
    @Alias
    native Object key();

    @Alias
    protected native String deriveClassName();

    @Alias
    protected native boolean isResolved();
}

@TargetClass(className = "java.lang.invoke.ClassSpecializer", innerClass = "Factory")
final class Target_java_lang_invoke_ClassSpecializer_Factory {
    @Alias
    protected native void linkSpeciesDataToCode(Target_java_lang_invoke_ClassSpecializer_SpeciesData speciesData, Class<?> speciesCode);

    /*
     * Avoid generating signature-specific classes at runtime.
     */
    @Substitute
    Target_java_lang_invoke_ClassSpecializer_SpeciesData loadSpecies(Target_java_lang_invoke_ClassSpecializer_SpeciesData speciesData) {
        String className = speciesData.deriveClassName();
        assert (className.indexOf('/') < 0) : className;
        final Class<?> speciesCode;
        // Not pregenerated, generate the class
        try {
            /* All species are the same class */
            speciesCode = Target_java_lang_invoke_BoundMethodHandle.class;
            if (Target_java_lang_invoke_MethodHandleStatics.TRACE_RESOLVE) {
                // Used by jlink species pregeneration plugin, see
                // jdk.tools.jlink.internal.plugins.GenerateJLIClassesPlugin
                System.out.println("[SPECIES_RESOLVE] " + className + " (generated)");
            }
            // This operation causes a lot of churn:
            linkSpeciesDataToCode(speciesData, speciesCode);
        } catch (Error ex) {
            if (Target_java_lang_invoke_MethodHandleStatics.TRACE_RESOLVE) {
                System.out.println("[SPECIES_RESOLVE] " + className + " (Error #2)");
            }
            // We can get here if there is a race condition loading a class.
            // Or maybe we are out of resources. Back out of the CHM.get and retry.
            throw ex;
        }

        if (!speciesData.isResolved()) {
            throw new InternalError("Bad species class linkage for " + className + ": " + speciesData);
        }
        return speciesData;
    }
}

@TargetClass(className = "java.lang.invoke.MethodHandleStatics")
final class Target_java_lang_invoke_MethodHandleStatics {
    // Checkstyle: stop
    @Alias static boolean TRACE_RESOLVE;
    // Checkstyle: resume
}
