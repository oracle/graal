/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.jvmci.options.*;
import com.oracle.jvmci.options.DerivedOptionValue.OptionSupplier;

public class DefaultSuitesProvider implements SuitesProvider {

    private final DerivedOptionValue<Suites> defaultSuites;
    private final PhaseSuite<HighTierContext> defaultGraphBuilderSuite;
    private final DerivedOptionValue<LIRSuites> defaultLIRSuites;

    private class SuitesSupplier implements OptionSupplier<Suites> {

        private static final long serialVersionUID = 2677805381215454728L;

        public Suites get() {
            return createSuites();
        }

    }

    private class LIRSuitesSupplier implements OptionSupplier<LIRSuites> {

        private static final long serialVersionUID = 312070237227476252L;

        public LIRSuites get() {
            return createLIRSuites();
        }

    }

    public DefaultSuitesProvider(Plugins plugins) {
        this.defaultGraphBuilderSuite = createGraphBuilderSuite(plugins);
        this.defaultSuites = new DerivedOptionValue<>(new SuitesSupplier());
        this.defaultLIRSuites = new DerivedOptionValue<>(new LIRSuitesSupplier());
    }

    public Suites getDefaultSuites() {
        return defaultSuites.getValue();
    }

    public Suites createSuites() {
        return Suites.createDefaultSuites();
    }

    public PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        return defaultGraphBuilderSuite;
    }

    public PhaseSuite<HighTierContext> createGraphBuilderSuite(Plugins plugins) {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        suite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(plugins)));
        return suite;
    }

    public LIRSuites getDefaultLIRSuites() {
        return defaultLIRSuites.getValue();
    }

    public LIRSuites createLIRSuites() {
        return Suites.createDefaultLIRSuites();
    }

}
