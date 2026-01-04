/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.hotspot.replacements.ObjectCloneSnippets;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider.Extensions;
import jdk.graal.compiler.hotspot.stubs.ForeignCallSnippets;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.options.OptionValues;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public interface HotSpotLoweringProvider extends LoweringProvider {

    void initialize(OptionValues options, Iterable<DebugDumpHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config);

    ObjectCloneSnippets.Templates getObjectCloneSnippets();

    ForeignCallSnippets.Templates getForeignCallSnippets();

    void initializeExtensions(OptionValues options, Iterable<DebugDumpHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    Iterable<Extensions> iterableExtensions);

    GraalHotSpotVMConfig getVMConfig();
}
