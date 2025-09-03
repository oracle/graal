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

import static jdk.vm.ci.aarch64.AArch64.CPUFeature.AES;
import static jdk.vm.ci.aarch64.AArch64.CPUFeature.ASIMD;
import static jdk.vm.ci.aarch64.AArch64.CPUFeature.CRC32;
import static jdk.vm.ci.aarch64.AArch64.CPUFeature.FP;
import static jdk.vm.ci.aarch64.AArch64.CPUFeature.LSE;
import static jdk.vm.ci.aarch64.AArch64.CPUFeature.PMULL;

import java.util.ArrayList;
import java.util.Collections;
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
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.StringUtil;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;

/**
 * AArch64 CPU types used to implement -march.
 * <p>
 * For reference, see <a href= "https://gcc.gnu.org/onlinedocs/gcc/AArch64-Options.html">gcc's
 * AArch64 Options</a>.
 */
public enum CPUTypeAArch64 implements CPUType {
    ARMV8_A("armv8-a", FP, ASIMD),
    ARMV8_1_A("armv8.1-a", ARMV8_A, CRC32, LSE),

    // Special symbols
    COMPATIBILITY(NativeImageOptions.MICRO_ARCHITECTURE_COMPATIBILITY, ARMV8_A),
    NATIVE(NativeImageOptions.MICRO_ARCHITECTURE_NATIVE, getNativeOrEmpty());

    private static final String AVAILABLE_FEATURE_MODIFIERS = StringUtil.joinSingleQuoted("aes", "lse", "fp", "simd");

    private static CPUFeature[] getNativeOrEmpty() {
        CPUFeature[] empty = new CPUFeature[0];
        if (GraalAccess.getOriginalTarget().arch instanceof AArch64 arch) {
            return arch.getFeatures().toArray(empty);
        } else {
            return empty;
        }
    }

    private final String name;
    private final CPUTypeAArch64 parent;
    private final EnumSet<CPUFeature> specificFeatures;

    CPUTypeAArch64(String cpuTypeName, CPUFeature... features) {
        this(cpuTypeName, null, features);
    }

    CPUTypeAArch64(String cpuTypeName, CPUTypeAArch64 cpuTypeParentOrNull, CPUFeature... features) {
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
    public CPUTypeAArch64 getParent() {
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

    public static String getDefaultName(boolean printFallbackWarning) {
        if (NATIVE.getFeatures().containsAll(ARMV8_1_A.getFeatures())) {
            return ARMV8_1_A.getName();
        } else {
            if (printFallbackWarning) {
                LogUtils.warning("The host machine does not support all features of '%s'. Falling back to '%s' for best compatibility.",
                                ARMV8_1_A.getName(), SubstrateOptionsParser.commandArgument(NativeImageOptions.MicroArchitecture, COMPATIBILITY.getName()));
            }
            return COMPATIBILITY.getName();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static EnumSet<CPUFeature> getSelectedFeatures() {
        String value = NativeImageOptions.MicroArchitecture.getValue();
        if (value == null) {
            value = getDefaultName(true);
        }
        return getCPUFeaturesForArch(value);
    }

    public static EnumSet<CPUFeature> getCPUFeaturesForArch(String marchValue) {
        String[] archParts = marchValue.split("\\+");
        CPUTypeAArch64 value = typeOf(archParts[0]);
        if (value == null) {
            throw UserError.abort("Unsupported architecture '%s'. Please adjust '%s'. On AArch64, only %s are available.",
                            marchValue,
                            SubstrateOptionsParser.commandArgument(NativeImageOptions.MicroArchitecture, marchValue),
                            StringUtil.joinSingleQuoted(CPUType.toNames(values())));
        }
        List<CPUFeature> features = new ArrayList<>(value.getFeatures());
        processFeatureModifiers(features, archParts);
        return EnumSet.copyOf(features);
    }

    public static void printFeatureModifiers() {
        System.out.printf("%nThe option also supports one or more feature modifiers via the form '-march=arch{+[no]feature}*'. " +
                        "Example: '%s+lse' enables Large System Extension instructions.%n" +
                        "The following feature modifiers are available: %s.%n",
                        ARMV8_1_A.getName(), AVAILABLE_FEATURE_MODIFIERS);
    }

    public static boolean nativeSupportsMoreFeaturesThanSelected() {
        EnumSet<CPUFeature> nativeFeatures = NATIVE.getFeatures();
        EnumSet<CPUFeature> selectedFeatures = getSelectedFeatures();
        return nativeFeatures.containsAll(selectedFeatures) && nativeFeatures.size() > selectedFeatures.size();
    }

    private static void processFeatureModifiers(List<CPUFeature> features, String[] archParts) {
        for (int i = 1; i < archParts.length; i++) {
            String part = archParts[i];
            List<CPUFeature> partFeatures = getFeatures(part);
            if (part.startsWith("no")) {
                features.removeAll(partFeatures);
            } else {
                features.addAll(partFeatures);
            }
        }
    }

    private static List<CPUFeature> getFeatures(String featureModifier) {
        return switch (featureModifier) {
            case "lse", "nolse" -> List.of(LSE);
            case "aes", "noaes" -> List.of(AES, PMULL);
            // fp and simd are required
            case "fp", "simd" -> Collections.emptyList();
            case "nofp", "nosimd" -> throw UserError.abort("The '%s' CPU feature is required by the Graal compiler and thus cannot be disabled.%s", featureModifier, getUserAction());
            default -> throw UserError.abort("Unsupported AArch64 feature modifier '%s'.%s Only %s are available.", featureModifier, getUserAction(), AVAILABLE_FEATURE_MODIFIERS);
        };
    }

    private static String getUserAction() {
        return String.format(" Please adjust '%s'.", SubstrateOptionsParser.commandArgument(NativeImageOptions.MicroArchitecture, NativeImageOptions.MicroArchitecture.getValue()));
    }

    private static CPUTypeAArch64 typeOf(String marchValue) {
        for (CPUTypeAArch64 value : values()) {
            if (value.name.equals(marchValue)) {
                return value;
            }
        }
        return null;
    }
}
