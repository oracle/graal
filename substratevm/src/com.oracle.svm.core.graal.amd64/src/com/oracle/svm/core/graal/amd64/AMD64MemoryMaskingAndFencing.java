/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.genscavenge.AddressRangeCommittedMemoryProvider;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValidationSupport;
import com.oracle.svm.core.option.RuntimeOptionValidationSupport.RuntimeOptionValidation;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;

/**
 * This class along with {@link AMD64AddressLoweringAndMaskingByNodePhase} implements a mitigation
 * for speculative execution attacks.
 * <p>
 * The mitigation is available for runtime compiled code when running on svm, with isolates support
 * enabled, on AMD64.
 * </p>
 * <p>
 * When the mitigation is enabled, each memory access that can potentially be used as part of a
 * spectre gadget, is either masked or fenced. In the first case, by leveraging the contiguous
 * address space in which the isolate heap is allocated and using x86_64 addressing mode, we can
 * always represent addresses in the form: [heapbase + offset]. Then we can enforce that the offset
 * is bounded by the size of the reserved address space of the Isolate by applying a logical mask
 * (and offset, MASK). In the second case, which is a fallback for cases in which we are referencing
 * memory outside the isolate heap, we emit memory barriers before the memory access to avoid
 * speculative reads. The mitigation won't act on addresses that make direct use of a reserved
 * register (except for r14, which stores the heap base) as well as addresses that reference memory
 * as a static offset from a reserved register.
 * </p>
 * Example of memory accesses that do require masking:
 * <ul>
 * <li>[reg1 + reg2 * scale + immediate] where reg2 * scale as well as the immediate are optional;
 * </li>
 * <li>[reserved reg + reg * scale + immediate] where the immediate is optional.</li>
 * </ul>
 * Example of memory accesses that do not require masking:
 * <ul>
 * <li>[reserved reg + immediate] where the immediate is optional.</li>
 * </ul>
 *
 * <p>
 * The mitigation implementation is divided in two parts. The first is a phase in the low-tier,
 * {@link AMD64AddressLoweringAndMaskingByNodePhase}, responsible for lowering OffsetAddressNodes
 * into their masked (or fenced) counterpart ({@link AMD64MaskedAddressNode}). The second part is
 * implemented at the assembler level, specifically in {@link SubstrateAMD64MacroAssembler} which
 * allows interception of all the op codes using an AMD64Address as source operand. Thus, we can
 * emit a barrier for all the addresses that were created after the lowering phase. At this stage,
 * we cannot apply masking anymore, however the amount of addresses created during the emission
 * phase is limited, thus performance is not hindered.
 * </p>
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class AMD64MemoryMaskingAndFencing {
    public static boolean isEnabled() {
        return Options.MemoryMaskingAndFencing.getValue() && ImageInfo.inImageRuntimeCode() && RuntimeCompilation.isEnabled();
    }

    public static class Options {
        @Option(help = "AMD64 only spectre mitigation for runtime compiled code (masking or fencing before accesses).", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL) //
        public static final RuntimeOptionKey<Boolean> MemoryMaskingAndFencing = new RuntimeOptionKey<>(false, Options::validateOptionAtBuildTime,
                        RelevantForCompilationIsolates);

        @Platforms(Platform.HOSTED_ONLY.class)
        private static void validateOptionAtBuildTime(RuntimeOptionKey<Boolean> optionKey) {
            /*
             * Checking if "hasBeenSet" allows us to catch any usage at build time and fail if the
             * option is used on unsupported platforms. Setting the options will make it reachable,
             * also on non-supported platforms.
             */
            if (optionKey.hasBeenSet() && !Platform.includedIn(Platform.AMD64.class)) {
                throw UserError.invalidOptionValue(optionKey, optionKey.getValue(), "The option is only available on AMD64");
            }

            if (optionKey.getValue()) {
                if (SubstrateOptions.useG1GC()) {
                    throw UserError.invalidOptionValue(optionKey, optionKey.getValue(), "The option is not supported when using G1");
                } else if (!SubstrateOptions.SpawnIsolates.getValue()) {
                    throw UserError.invalidOptionValue(optionKey, optionKey.getValue(), "The option is only available when isolate support is enabled.");
                }
            }
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        static void registerRuntimeOptionValidation() {
            RuntimeOptionValidationSupport.singleton().register(new RuntimeOptionValidation<>(runtimeOptionKey -> {
                if (runtimeOptionKey.getValue()) {
                    if (SubstrateOptions.useG1GC()) {
                        throw new IllegalArgumentException("Option " + runtimeOptionKey.getName() + " is not supported when using G1");
                    } else if (!SubstrateOptions.SpawnIsolates.getValue()) {
                        throw new IllegalArgumentException("Option " + runtimeOptionKey.getName() + " is only available when isolate support is enabled.");
                    } else if (!Platform.includedIn(Platform.AMD64.class)) {
                        throw new IllegalArgumentException("Option " + runtimeOptionKey.getName() + " is only available on AMD64");
                    }
                }
            }, Options.MemoryMaskingAndFencing));
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.AMD64.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
class AMD64MemoryMaskingAndFencingFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            AMD64MemoryMaskingAndFencing.Options.registerRuntimeOptionValidation();
        }
    }

    @Override
    public void registerGraalPhases(Providers providers, Suites suites, boolean hosted, boolean fallback) {
        if (hosted) {
            /*
             * The mitigation is for runtime compilation only, we don't need to register the phases
             * for host compilation.
             */
            return;
        } else if (!SubstrateOptions.SpawnIsolates.getValue()) {
            return;
        } else if (SubstrateOptions.useG1GC()) {
            /*
             * The mitigation is not compatible with G1. Also, when only 1 isolate is used (G1 case)
             * the mitigation is not needed.
             */
            return;
        }

        // The isolate heap should be a contiguous memory region
        VMError.guarantee(ImageSingletons.lookup(CommittedMemoryProvider.class) instanceof AddressRangeCommittedMemoryProvider);

        /*
         * For runtime compilation we need to register the proper lowering phase that supports the
         * mitigation.
         */
        CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
        suites.getLowTier().replacePhase(AddressLoweringByNodePhase.class, new AMD64AddressLoweringAndMaskingByNodePhase(new SubstrateAMD64AddressLowering(compressEncoding)));
    }
}
