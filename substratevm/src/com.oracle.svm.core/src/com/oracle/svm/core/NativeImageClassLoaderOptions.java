/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.options.Option;

import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;

public class NativeImageClassLoaderOptions {
    public static final String AddExportsAndOpensFormat = "<module>/<package>=<target-module>(,<target-module>)*";
    public static final String AddReadsFormat = "<module>=<target-module>(,<target-module>)*";

    @APIOption(name = "add-exports", extra = true, launcherOption = true, valueSeparator = {APIOption.WHITESPACE_SEPARATOR, '='})//
    @Option(help = "Value " + AddExportsAndOpensFormat + " updates <module> to export <package> to <target-module>, regardless of module declaration." +
                    " <target-module> can be ALL-UNNAMED to export to all unnamed modules.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AddExports = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @APIOption(name = "add-opens", extra = true, launcherOption = true, valueSeparator = {APIOption.WHITESPACE_SEPARATOR, '='})//
    @Option(help = "Value " + AddExportsAndOpensFormat + " updates <module> to open <package> to <target-module>, regardless of module declaration.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AddOpens = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @APIOption(name = "add-reads", extra = true, valueSeparator = {APIOption.WHITESPACE_SEPARATOR, '='})//
    @Option(help = "Value " + AddReadsFormat + " updates <module> to read <target-module>, regardless of module declaration." +
                    " <target-module> can be ALL-UNNAMED to read all unnamed modules.")//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AddReads = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @APIOption(name = "list-modules")//
    @Option(help = "List observable modules and exit.")//
    public static final HostedOptionKey<Boolean> ListModules = new HostedOptionKey<>(false);
}
