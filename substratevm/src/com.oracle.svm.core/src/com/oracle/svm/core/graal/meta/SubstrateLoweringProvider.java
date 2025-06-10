/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import java.util.Map;

import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;

import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public interface SubstrateLoweringProvider extends LoweringProvider {

    void setConfiguration(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers);

    Map<Class<? extends Node>, NodeLoweringProvider<?>> getLowerings();

    Stamp loadStamp(Stamp stamp, JavaKind kind);

    static LoweringProvider createForHosted(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return GraalConfiguration.hostedInstance().createLoweringProvider(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider);
    }

    static LoweringProvider createForRuntime(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return GraalConfiguration.runtimeInstance().createLoweringProvider(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider);
    }

    @Override
    default boolean supportsImplicitNullChecks() {
        return false;
    }
}
