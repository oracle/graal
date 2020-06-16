/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.java;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl.Options;
import org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime;

final class HotSpotTruffleRuntime extends AbstractHotSpotTruffleRuntime {

    HotSpotTruffleRuntime() {
    }

    @Override
    public <T> T getOptions(Class<T> optionValuesType) {
        if (optionValuesType == OptionValues.class) {
            return optionValuesType.cast(HotSpotGraalOptionValues.defaultOptions());
        }
        return super.getOptions(optionValuesType);
    }

    @Override
    public <T> T convertOptions(Class<T> optionValuesType, Map<String, Object> map) {
        if (optionValuesType == OptionValues.class) {
            final EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
            final Iterable<OptionDescriptors> loader = OptionsParser.getOptionsLoader();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                final String optionName = e.getKey();
                final Object optionValue = e.getValue();
                OptionsParser.parseOption(optionName, optionValue, values, loader);
            }
            return optionValuesType.cast(new OptionValues(values));
        }
        return super.convertOptions(optionValuesType, map);
    }

    @Override
    public Map<String, Object> createInitialOptions() {
        final UnmodifiableMapCursor<OptionKey<?>, Object> optionValues = getOptions(OptionValues.class).getMap().getEntries();
        Map<String, Object> res = new HashMap<>();
        while (optionValues.advance()) {
            final OptionKey<?> key = optionValues.getKey();
            Object value = optionValues.getValue();
            res.put(key.getName(), value);
        }
        return res;
    }

    @Override
    protected String initLazyCompilerConfigurationName() {
        final OptionValues options = getOptions(OptionValues.class);
        String factoryName = Options.TruffleCompilerConfiguration.getValue(options);
        CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(factoryName, options);
        return compilerConfigurationFactory.getName();
    }

    @Override
    public TruffleCompiler newTruffleCompiler() {
        return HotSpotTruffleCompilerImpl.create(this);
    }

    @Override
    protected OutputStream getDefaultLogStream() {
        return TTY.out;
    }
}
