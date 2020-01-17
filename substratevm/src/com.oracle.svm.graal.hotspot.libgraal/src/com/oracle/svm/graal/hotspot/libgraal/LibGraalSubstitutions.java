/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import java.util.Map;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.vm.ci.services.Services;

/** Dummy class to have a class with the file's name. Do not remove. */
public final class LibGraalSubstitutions {
    // Dummy
}

@TargetClass(value = Services.class, onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_services_Services {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
    public static boolean IS_IN_NATIVE_IMAGE = true;
    // Checkstyle: resume

    /*
     * Ideally, the original field should be annotated with @NativeImageReinitialize. But that
     * requires a larger JVMCI change because the annotation is not visible in that project, so we
     * use this substitution instead.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<String, String> savedProperties;
}

@TargetClass(className = "jdk.vm.ci.hotspot.HotSpotJDKReflection", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_HotSpotJDKReflection {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None)//
    private long oopSizeOffset;
}
