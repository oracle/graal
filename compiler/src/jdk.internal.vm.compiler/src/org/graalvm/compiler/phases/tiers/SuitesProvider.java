/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.tiers;

import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;

import jdk.vm.ci.code.Architecture;

/**
 * Main interface providing access to suites used for compilation.
 */

public interface SuitesProvider {

    /**
     * Get the default phase suites of this compiler. This will take {@code options} into account,
     * returning an appropriately constructed suite. The returned suite is immutable by default but
     * {@link Suites#copy} can be used to create a customized version.
     */
    Suites getDefaultSuites(OptionValues values, Architecture arch);

    /**
     * Get the default phase suite for creating new graphs.
     */
    PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite();

    /**
     * Get the default LIR phase suites of this compiler. This will take in account any options
     * enabled at the time of call, returning an appropriately constructed suite. The returned suite
     * is immutable by default but {@link LIRSuites#copy} can be used to create a customized
     * version.
     */
    LIRSuites getDefaultLIRSuites(OptionValues options);
}
