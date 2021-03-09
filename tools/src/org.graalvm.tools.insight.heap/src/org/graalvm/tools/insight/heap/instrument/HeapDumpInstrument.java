/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.heap.instrument;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.tools.insight.Insight;

@TruffleInstrument.Registration(id = "heap", internal = false, services = Insight.SymbolProvider.class)
public final class HeapDumpInstrument extends TruffleInstrument {
    @Option(stability = OptionStability.STABLE, name = "dump", help = "Output file to ", category = OptionCategory.EXPERT) //
    static final OptionKey<String> DUMP = new OptionKey<>("");

    @Override
    protected void onCreate(Env env) {
        if (DUMP.hasBeenSet(env.getOptions())) {
            env.registerService(new HeapObject(env, DUMP.getValue(env.getOptions())));
        } else {
            env.registerService(new HeapObject(env, null));
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new HeapDumpInstrumentOptionDescriptors();
    }

}
