/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

/**
 * Options related to HotSpot snippets in this package.
 */
public class HotspotSnippetsOptions {
    // @formatter:off
    @Option(help = "Use a VM runtime call to load and clear the exception object from the thread at the start of a compiled exception handler.", type = OptionType.Debug)
    public static final OptionKey<Boolean> LoadExceptionObjectInVM = new OptionKey<>(false);

    @Option(help = "Enable profiling of allocation sites.", type = OptionType.Debug)
    public static final OptionKey<Boolean> ProfileAllocations = new OptionKey<>(false);

    @Option(help = """
                   Control the naming and granularity of the counters when using ProfileAllocations.
                   The accepted values are:
                           AllocatingMethod - a counter per method
                            InstanceOrArray - one counter for all instance allocations and
                                              one counter for all array allocations
                              AllocatedType - one counter per allocated type
                     AllocatedTypesInMethod - one counter per allocated type, per method""", type = OptionType.Debug)
    public static final EnumOptionKey<HotSpotAllocationSnippets.ProfileContext> ProfileAllocationsContext = new EnumOptionKey<>(HotSpotAllocationSnippets.ProfileContext.AllocatingMethod);

    @Option(help = "Enable profiling of monitor operations.", type = OptionType.Debug)
    public static final OptionKey<Boolean> ProfileMonitors = new OptionKey<>(false);

    @Option(help = "Trace monitor operations on objects whose type contains this substring.", type = OptionType.Debug)
    public static final OptionKey<String> TraceMonitorsTypeFilter = new OptionKey<>(null);

    @Option(help = "Trace monitor operations in methods whose fully qualified name contains this substring.", type = OptionType.Debug)
    public static final OptionKey<String> TraceMonitorsMethodFilter = new OptionKey<>(null);

    @Option(help = "Emit extra code to dynamically check monitor operations are balanced.", type = OptionType.Debug)
    public static final OptionKey<Boolean> VerifyBalancedMonitors = new OptionKey<>(false);
    //@formatter:on
}
