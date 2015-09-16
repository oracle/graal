/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.replacements;

import jdk.internal.jvmci.options.Option;
import jdk.internal.jvmci.options.OptionType;
import jdk.internal.jvmci.options.OptionValue;

/**
 * Options related to {@link InstanceOfSnippets}.
 *
 * Note: This must be a top level class to work around for <a
 * href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=477597">Eclipse bug 477597</a>.
 */
class InstanceOfSnippetsOptions {

    // @formatter:off
    @Option(help = "If the probability that a type check will hit one the profiled types (up to " +
                   "TypeCheckMaxHints) is below this value, the type check will be compiled without profiling info", type = OptionType.Expert)
    static final OptionValue<Double> TypeCheckMinProfileHitProbability = new OptionValue<>(0.5);

    @Option(help = "The maximum number of profiled types that will be used when compiling a profiled type check. " +
                    "Note that TypeCheckMinProfileHitProbability also influences whether profiling info is used in compiled type checks.", type = OptionType.Expert)
    static final OptionValue<Integer> TypeCheckMaxHints = new OptionValue<>(2);
    // @formatter:on
}
