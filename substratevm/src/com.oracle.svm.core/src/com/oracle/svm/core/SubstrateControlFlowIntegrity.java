/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.LayerVerifiedOption;
import com.oracle.svm.shared.option.LayerVerifiedOption.Kind;
import com.oracle.svm.shared.option.LayerVerifiedOption.Severity;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.vm.ci.code.Register;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class SubstrateControlFlowIntegrity {

    public static class Options {
        @LayerVerifiedOption(kind = Kind.Changed, severity = Severity.Error)//
        @Option(help = """
                        Options for configuring control flow integrity (CFI) enforcement.
                        NONE               No CFI enforcement
                        HW                 CFI enforcement leveraging hardware support. Currently only on aarch64: enforcing backward-edge CFI using pointer authentication codes (PAC).
                        SW                 CFI enforcement entirely in software.
                        SW_NONATIVE        CFI enforcement in software, except for transitions to/from native code, which are not validated.""", type = OptionType.User, stability = OptionStability.EXPERIMENTAL)//
        public static final HostedOptionKey<CFIOptions> CFI = new HostedOptionKey<>(CFIOptions.NONE);
    }

    public enum CFIOptions {
        NONE,
        HW,
        SW,
        SW_NONATIVE
    }

    public static void validateConfiguration(CFIOptions cfiMode, boolean supportedOperatingSystem, CFIOptions... supportedModes) {
        validateConfiguration(cfiMode, SubstrateOptions.useLLVMBackend(), supportedOperatingSystem, supportedModes);
    }

    static void validateConfiguration(CFIOptions cfiMode, boolean llvmBackend, boolean supportedOperatingSystem, CFIOptions... supportedModes) {
        if (cfiMode == CFIOptions.NONE) {
            return;
        }

        boolean supportedMode = false;
        for (CFIOptions mode : supportedModes) {
            supportedMode |= cfiMode == mode;
        }
        UserError.guarantee(supportedMode, "CFI mode '%s' is not supported on this target architecture.", cfiMode);
        UserError.guarantee(!llvmBackend, "CFI mode '%s' is not supported with the LLVM backend.", cfiMode);
        UserError.guarantee(supportedOperatingSystem, "CFI mode '%s' is not supported on this operating system.", cfiMode);
    }

    public CFIOptions getCFIMode() {
        return CFIOptions.NONE;
    }

    /**
     * Returns the architecture-specific register used to carry an indirect branch target while
     * software CFI validates it. Returns and runtime-generated trampolines load their target into
     * this register before the validated jump. Calls declare the register as temporary, and
     * runtime calling-convention support excludes it from callee-saved and bytecode-handler
     * argument registers, so its value is never expected to survive a call.
     *
     * This method is only valid when software CFI is enabled. Implementations that support
     * software CFI must override it with a register suitable for this purpose.
     */
    public Register getCFITargetRegister() {
        throw VMError.shouldNotReachHere("No CFI Target Register is available");
    }

    public boolean continuationsSupported() {
        return true;
    }

    @Fold
    public static SubstrateControlFlowIntegrity singleton() {
        return ImageSingletons.lookup(SubstrateControlFlowIntegrity.class);
    }

    @Fold
    public static boolean enabled() {
        var cfiMode = singleton().getCFIMode();
        return cfiMode != CFIOptions.NONE;
    }

    @Fold
    public static boolean useSoftwareCFI() {
        var cfiMode = singleton().getCFIMode();
        return cfiMode == CFIOptions.SW || cfiMode == CFIOptions.SW_NONATIVE;
    }
}
