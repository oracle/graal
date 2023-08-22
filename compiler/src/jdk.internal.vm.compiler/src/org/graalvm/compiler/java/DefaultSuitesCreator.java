/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.java;

import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;

import jdk.vm.ci.code.Architecture;

public abstract class DefaultSuitesCreator extends SuitesProviderBase {

    private final CompilerConfiguration compilerConfiguration;

    @SuppressWarnings("this-escape")
    public DefaultSuitesCreator(CompilerConfiguration compilerConfiguration, Plugins plugins) {
        this(compilerConfiguration);
        this.defaultGraphBuilderSuite = createGraphBuilderSuite(plugins);
    }

    public DefaultSuitesCreator(CompilerConfiguration compilerConfiguration) {
        super();
        this.compilerConfiguration = compilerConfiguration;
    }

    @Override
    public Suites createSuites(OptionValues options, Architecture arch) {
        return Suites.createSuites(compilerConfiguration, options, arch);
    }

    protected PhaseSuite<HighTierContext> createGraphBuilderSuite(Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        suite.appendPhase(createGraphBuilderPhase(GraphBuilderConfiguration.getDefault(plugins)));
        return suite;
    }

    protected abstract GraphBuilderPhase createGraphBuilderPhase(GraphBuilderConfiguration graphBuilderConfiguration);

    @Override
    public LIRSuites createLIRSuites(OptionValues options) {
        return Suites.createLIRSuites(compilerConfiguration, options);
    }
}
