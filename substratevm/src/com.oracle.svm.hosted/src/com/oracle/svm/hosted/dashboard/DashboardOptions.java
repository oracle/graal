/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dashboard;

import com.oracle.svm.core.option.HostedOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

class DashboardOptions {

    @Option(help = "Enable dashboard dumps to the specified file.", type = OptionType.Expert)//
    static final HostedOptionKey<String> DashboardDump = new HostedOptionKey<>(null);

    @Option(help = "In the dashboard dump, include all available information about the native image (this takes precedence over more specific flags).", type = OptionType.Expert)//
    static final HostedOptionKey<Boolean> DashboardAll = new HostedOptionKey<>(false);

    @Option(help = "In the dashboard dump, include the breakdown of the object sizes in the heap across different classes.", type = OptionType.Expert)//
    static final HostedOptionKey<Boolean> DashboardHeap = new HostedOptionKey<>(false);

    @Option(help = "In the dashboard dump, include the breakdown of the code size across different packages.", type = OptionType.Expert)//
    static final HostedOptionKey<Boolean> DashboardCode = new HostedOptionKey<>(false);

    @Option(help = "In the dashboard dump, include the information about the points-to analysis.", type = OptionType.Expert)//
    static final HostedOptionKey<Boolean> DashboardPointsTo = new HostedOptionKey<>(false);

    @Option(help = "Set dashboard to use pretty print.", type = OptionType.Expert)//
    static final HostedOptionKey<Boolean> DashboardPretty = new HostedOptionKey<>(false);
}
