/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging;

import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.LayerVerifiedOption;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;

public final class SubstrateGuestOptions {

    @Option(help = "Initialize the VM and run startup hooks.")//
    public static final HostedOptionKey<Boolean> InitializeVM = new HostedOptionKey<>(true);

    @Option(help = "The size of each thread stack at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> StackSize = new RuntimeOptionKey<>(0L);

    @LayerVerifiedOption(kind = LayerVerifiedOption.Kind.Changed, severity = LayerVerifiedOption.Severity.Error)//
    @Option(help = "Prefix that is added to the names of entry point methods.")//
    public static final HostedOptionKey<String> EntryPointNamePrefix = new HostedOptionKey<>("");

    @LayerVerifiedOption(kind = LayerVerifiedOption.Kind.Changed, severity = LayerVerifiedOption.Severity.Error)//
    @Option(help = "Prefix that is added to the names of API functions.", stability = OptionStability.STABLE)//
    public static final HostedOptionKey<String> APIFunctionPrefix = new HostedOptionKey<>("graal_");

    /**
     * Determines if the installation of important signal handlers should be tried during early
     * isolate startup.
     */
    @GuestFold
    public static boolean installSignalHandlersEarly() {
        return InitializeVM.getValue();
    }

    private SubstrateGuestOptions() {
    }
}
