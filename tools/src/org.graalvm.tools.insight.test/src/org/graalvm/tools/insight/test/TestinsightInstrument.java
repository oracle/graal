/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import java.util.Collections;
import java.util.Map;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.tools.insight.Insight;

// @formatter:off
@TruffleInstrument.Registration(
    id = "count",
    name = "count",
    version = "testing",
    services = { Insight.SymbolProvider.class }
)
// @formatter:on
public class TestinsightInstrument extends TruffleInstrument {
    @Option(stability = OptionStability.STABLE, name = "", help = "Initial value of Insight's count variable", category = OptionCategory.INTERNAL) //
    static final OptionKey<Integer> COUNT = new OptionKey<>(-1);

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new TestinsightInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        Integer initialCount = env.getOptions().get(COUNT);
        if (initialCount != null && initialCount > 0) {
            env.registerService(new Insight.SymbolProvider() {
                @Override
                public Map<String, Object> symbolsWithValues() throws Exception {
                    return Collections.singletonMap("count", initialCount);
                }
            });
        } else {
            env.registerService(new Insight.SymbolProvider() {
                @Override
                public Map<String, ? extends Object> symbolsWithValues() throws Exception {
                    return Collections.emptyMap();
                }
            });
        }
    }
}
