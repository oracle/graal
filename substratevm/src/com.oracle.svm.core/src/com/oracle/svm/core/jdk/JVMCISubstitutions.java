/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Map;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.services.Services;

final class IsNotLibgraal implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !SubstrateUtil.isBuildingLibgraal();
    }
}

/**
 * In libgraal the saved properties are initialized by copying them from the HotSpot heap.
 */
@TargetClass(value = Services.class, onlyWith = IsNotLibgraal.class)
final class Target_jdk_vm_ci_services_Services {
    @Delete //
    static Map<String, String> savedProperties;

    @Substitute
    public static Map<String, String> getSavedProperties() {
        return SystemPropertiesSupport.singleton().getSavedProperties();
    }
}

/**
 * Allow updating the value backing {@link AMD64#getLargestStorableKind}.
 */
@Platforms(Platform.AMD64.class)
@TargetClass(value = AMD64.class, onlyWith = IsNotLibgraal.class)
final class Target_jdk_vm_ci_amd64_AMD64 {
    @Alias AMD64Kind largestKind;

}

/** Dummy class to have a class with the file's name. */
public final class JVMCISubstitutions {
    @Platforms(Platform.AMD64.class)
    public static void updateLargestStorableKind(AMD64 architecture, AMD64Kind largestStorableKind) {
        Target_jdk_vm_ci_amd64_AMD64 arch = SubstrateUtil.cast(architecture, Target_jdk_vm_ci_amd64_AMD64.class);
        arch.largestKind = largestStorableKind;
    }
}
