/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.core.Instrumentation;
import org.graalvm.compiler.core.phases.CommunityCompilerConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

/**
 * Factory for creating the default configuration for the community edition of Graal.
 */
@ServiceProvider(CompilerConfigurationFactory.class)
public class CommunityCompilerConfigurationFactory extends CompilerConfigurationFactory {

    public static final String NAME = "community";

    /**
     * Must be greater than {@link EconomyCompilerConfigurationFactory#AUTO_SELECTION_PRIORITY}.
     */
    public static final int AUTO_SELECTION_PRIORITY = 2;

    public CommunityCompilerConfigurationFactory() {
        super(NAME, AUTO_SELECTION_PRIORITY);
        assert AUTO_SELECTION_PRIORITY > EconomyCompilerConfigurationFactory.AUTO_SELECTION_PRIORITY;
    }

    @Override
    public CompilerConfiguration createCompilerConfiguration() {
        return new CommunityCompilerConfiguration();
    }

    @Override
    public Instrumentation createInstrumentation(OptionValues options) {
        return new DefaultInstrumentation();
    }
}
