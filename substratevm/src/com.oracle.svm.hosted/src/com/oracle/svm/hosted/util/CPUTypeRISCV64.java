/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.util;

import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.A;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.D;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.F;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.I;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.M;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.V;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.util.StringUtil;

import jdk.vm.ci.riscv64.RISCV64;
import jdk.vm.ci.riscv64.RISCV64.CPUFeature;

/**
 * RISCV64 CPU types used to implement -march.
 * <p>
 * For reference, see <a href= "https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Options.html">gcc's RISC-V
 * Options</a> and <a href= "https://riscv.org/technical/specifications/">the chapter 27 of RISC-V
 * user-level ISA specification</a>.
 */
public enum CPUTypeRISCV64 implements CPUType {
    /*
     * It is possible to choose almost any subset of features (some features depends on others like
     * D on F. Native Image needs at least I, M, A, F, D and C, so subset without those features are
     * not included here.
     */
    RV64IMAFDC("rv64imafdc", I, M, A, F, D),
    RV64GC("rv64gc", RV64IMAFDC),
    RV64IMAFDCV("rv64imafdcv", RV64IMAFDC, V),
    RV64GCV("rv64gcv", RV64IMAFDCV),

    // Special symbols
    COMPATIBILITY(NativeImageOptions.MICRO_ARCHITECTURE_COMPATIBILITY, RV64GC),
    NATIVE(NativeImageOptions.MICRO_ARCHITECTURE_NATIVE, getNativeOrEmpty());

    private static CPUFeature[] getNativeOrEmpty() {
        CPUFeature[] empty = new CPUFeature[0];
        if (GraalAccess.getOriginalTarget().arch instanceof RISCV64 arch) {
            return arch.getFeatures().toArray(empty);
        } else {
            return empty;
        }
    }

    private final String name;
    private final CPUTypeRISCV64 parent;
    private final EnumSet<CPUFeature> specificFeatures;

    CPUTypeRISCV64(String cpuTypeName, CPUFeature... features) {
        this(cpuTypeName, null, features);
    }

    CPUTypeRISCV64(String cpuTypeName, CPUTypeRISCV64 cpuTypeParentOrNull, CPUFeature... features) {
        name = cpuTypeName;
        parent = cpuTypeParentOrNull;
        specificFeatures = features.length > 0 ? EnumSet.copyOf(List.of(features)) : EnumSet.noneOf(CPUFeature.class);
        assert parent == null || parent.getFeatures().stream().noneMatch(specificFeatures::contains) : "duplicate features detected but not allowed";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CPUTypeRISCV64 getParent() {
        return parent;
    }

    @Override
    public String getSpecificFeaturesString() {
        return specificFeatures.stream().map(Enum::name).collect(Collectors.joining(" + "));
    }

    public EnumSet<CPUFeature> getFeatures() {
        if (parent == null) {
            return specificFeatures;
        } else {
            return EnumSet.copyOf(Stream.concat(parent.getFeatures().stream(), specificFeatures.stream()).toList());
        }
    }

    public static String getDefaultName() {
        return RV64GC.getName();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static EnumSet<CPUFeature> getSelectedFeatures() {
        String value = NativeImageOptions.MicroArchitecture.getValue();
        if (value == null) {
            value = getDefaultName();
        }
        return getCPUFeaturesForArch(value);
    }

    public static EnumSet<CPUFeature> getCPUFeaturesForArch(String marchValue) {
        CPUTypeRISCV64 value = typeOf(marchValue);
        if (value == null) {
            throw UserError.abort("Unsupported architecture '%s'. Please adjust '%s'. On RISCV64, only %s are available.",
                            marchValue,
                            SubstrateOptionsParser.commandArgument(NativeImageOptions.MicroArchitecture, marchValue),
                            StringUtil.joinSingleQuoted(CPUType.toNames(values())));
        }
        return value.getFeatures();
    }

    public static boolean nativeSupportsMoreFeaturesThanSelected() {
        EnumSet<CPUFeature> nativeFeatures = NATIVE.getFeatures();
        EnumSet<CPUFeature> selectedFeatures = getSelectedFeatures();
        return nativeFeatures.containsAll(selectedFeatures) && nativeFeatures.size() > selectedFeatures.size();
    }

    private static CPUTypeRISCV64 typeOf(String marchValue) {
        for (CPUTypeRISCV64 value : values()) {
            if (value.name.equals(marchValue)) {
                return value;
            }
        }
        return null;
    }
}
