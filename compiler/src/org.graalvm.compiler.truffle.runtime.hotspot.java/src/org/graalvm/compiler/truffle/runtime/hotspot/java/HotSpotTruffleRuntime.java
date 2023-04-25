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

import org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerEnvironment;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilerImpl.Options;
import org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

final class HotSpotTruffleRuntime extends AbstractHotSpotTruffleRuntime {

    HotSpotTruffleRuntime() {
    }

    @Override
    public <T> T getGraalOptions(Class<T> optionValuesType) {
        if (optionValuesType == OptionValues.class) {
            return optionValuesType.cast(HotSpotGraalOptionValues.defaultOptions());
        }
        return super.getGraalOptions(optionValuesType);
    }

    @Override
    protected String initLazyCompilerConfigurationName() {
        final OptionValues options = getGraalOptions(OptionValues.class);
        String factoryName = Options.TruffleCompilerConfiguration.getValue(options);
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(factoryName, options, runtime);
        return compilerConfigurationFactory.getName();
    }

    @Override
    public Object createCompilerEnvironment() {
        /*
         * For libgraal and SVM we can initialize the compiler environment eagerly. However, for
         * HotSpot without libgraal we need to make sure we lazily load all the compiler classes,
         * hence we lazily initialize it when the first compiler gets initialized.
         */
        return new HotSpotTruffleCompilerEnvironment(this);
    }

    @Override
    public synchronized TruffleCompiler newTruffleCompiler() {
        return HotSpotTruffleCompilerImpl.create(this);
    }
}
