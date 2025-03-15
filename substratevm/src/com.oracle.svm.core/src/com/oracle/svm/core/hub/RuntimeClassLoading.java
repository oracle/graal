/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import static jdk.graal.compiler.options.OptionStability.EXPERIMENTAL;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.vm.ci.meta.ResolvedJavaType;

public class RuntimeClassLoading {
    public static final class Options {
        @Option(help = "Enable support for runtime class loading.", stability = EXPERIMENTAL) //
        public static final HostedOptionKey<Boolean> SupportRuntimeClassLoading = new HostedOptionKey<>(false, Options::validate) {

            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                if (newValue) {
                    /* requires open type world */
                    SubstrateOptions.ClosedTypeWorld.update(values, false);
                }
            }
        };

        private static void validate(HostedOptionKey<Boolean> optionKey) {
            if (optionKey.hasBeenSet() && optionKey.getValue() && SubstrateOptions.ClosedTypeWorld.getValue()) {
                throw UserError.invalidOptionValue(SupportRuntimeClassLoading, SupportRuntimeClassLoading.getValue(),
                                "Requires an open type world, Please use " + SubstrateOptionsParser.commandArgument(SubstrateOptions.ClosedTypeWorld, "-"));
            }
        }
    }

    @Fold
    public static boolean isSupported() {
        return Options.SupportRuntimeClassLoading.getValue();
    }

    public static ResolvedJavaType createInterpreterType(DynamicHub hub, ResolvedJavaType analysisType) {
        return ImageSingletons.lookup(CremaSupport.class).createInterpreterType(hub, analysisType);
    }
}
