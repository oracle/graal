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
package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.core.Instrumentation;
import jdk.graal.compiler.core.phases.CommunityCompilerConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.serviceprovider.ServiceProvider;

/**
 * Factory for creating the default configuration for the community edition of Graal.
 */
@ServiceProvider(CompilerConfigurationFactory.class)
public class CommunityCompilerConfigurationFactory extends CompilerConfigurationFactory {

    public static final String NAME = "community";

    public static final String INFO = "Graal Community compiler";

    /**
     * Must be greater than {@link EconomyCompilerConfigurationFactory#AUTO_SELECTION_PRIORITY}.
     */
    public static final int AUTO_SELECTION_PRIORITY = 3;

    public CommunityCompilerConfigurationFactory() {
        this(AUTO_SELECTION_PRIORITY, INFO);
    }

    protected CommunityCompilerConfigurationFactory(int priority, String info) {
        super(NAME, info, priority);
        assert priority > EconomyCompilerConfigurationFactory.AUTO_SELECTION_PRIORITY : priority;
    }

    @Override
    public CompilerConfiguration createCompilerConfiguration() {
        return new CommunityCompilerConfiguration();
    }

    @Override
    public final Instrumentation createInstrumentation(OptionValues options) {
        return new DefaultInstrumentation();
    }
}
