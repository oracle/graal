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
package com.oracle.graal.salver;

import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionKey;

public final class SalverOptions {

    //@formatter:off
    @Option(help = "Enable dumps via Salver trace events.", type = OptionType.Debug)
    public static final OptionKey<Boolean> Salver = new OptionKey<>(false);

    @Option(help = "Network address (Salver).", type = OptionType.Debug)
    public static final OptionKey<String> SalverAddress = new OptionKey<>("127.0.0.1");

    @Option(help = "Network port (Salver).", type = OptionType.Debug)
    public static final OptionKey<Integer> SalverPort = new OptionKey<>(2343);

    @Option(help = "Dump to files as opposed to sending them over the network (Salver).", type = OptionType.Debug)
    public static final OptionKey<Boolean> SalverToFile = new OptionKey<>(false);
    //@formatter:on
}
