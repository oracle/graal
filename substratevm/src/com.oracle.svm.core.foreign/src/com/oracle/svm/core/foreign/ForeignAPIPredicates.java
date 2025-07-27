/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;

/**
 * Set of predicates used to control activation of substitutions (depending on method
 * {@link ForeignFunctionsRuntime#areFunctionCallsSupported()}) if FFM API support is enabled. In
 * case of the FFM API support is disabled entirely, substitutions in
 * {@link com.oracle.svm.core.jdk.ForeignDisabledSubstitutions} will be used.
 */
@SuppressWarnings("javadoc")
public final class ForeignAPIPredicates {
    public static final class Enabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.isForeignAPIEnabled();
        }
    }

    public static final class FunctionCallsSupported implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.isForeignAPIEnabled() && ForeignFunctionsRuntime.areFunctionCallsSupported();
        }
    }

    public static final class FunctionCallsUnsupported implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.isForeignAPIEnabled() && !ForeignFunctionsRuntime.areFunctionCallsSupported();
        }
    }

    public static final class SharedArenasEnabled implements BooleanSupplier {
        private static final String VECTOR_API_SUPPORT_OPTION_NAME = SubstrateOptionsParser.commandArgument(SubstrateOptions.VectorAPISupport, "-");
        private static final String SHARED_ARENA_SUPPORT_OPTION_NAME = SubstrateOptionsParser.commandArgument(SubstrateOptions.SharedArenaSupport, "-");

        @Platforms(Platform.HOSTED_ONLY.class)
        SharedArenasEnabled() {
        }

        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.isSharedArenaSupportEnabled();
        }

        public static RuntimeException vectorAPIUnsupported() {
            assert !SubstrateOptions.isSharedArenaSupportEnabled();
            throw VMError.unsupportedFeature("Support for Arena.ofShared is not available if Vector API support is enabled." +
                            "Either disable Vector API support using " + VECTOR_API_SUPPORT_OPTION_NAME +
                            " or replace usages of Arena.ofShared with Arena.ofAuto and disable shared arena support using " + SHARED_ARENA_SUPPORT_OPTION_NAME + ".");
        }
    }

    public static final class SharedArenasDisabled implements BooleanSupplier {
        private static final String SHARED_ARENA_SUPPORT_OPTION_NAME = SubstrateOptionsParser.commandArgument(SubstrateOptions.SharedArenaSupport, "+");

        @Platforms(Platform.HOSTED_ONLY.class)
        SharedArenasDisabled() {
        }

        @Override
        public boolean getAsBoolean() {
            return !SubstrateOptions.isSharedArenaSupportEnabled();
        }

        public static RuntimeException fail() {
            assert !SubstrateOptions.isSharedArenaSupportEnabled();
            throw VMError.unsupportedFeature("Support for Arena.ofShared is not active: enable with " + SHARED_ARENA_SUPPORT_OPTION_NAME);
        }
    }
}
