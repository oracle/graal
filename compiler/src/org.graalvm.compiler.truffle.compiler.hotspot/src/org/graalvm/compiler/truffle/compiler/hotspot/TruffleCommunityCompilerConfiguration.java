/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import org.graalvm.compiler.core.phases.CommunityCompilerConfiguration;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.phases.TruffleHostInliningPhase;
import org.graalvm.compiler.truffle.compiler.phases.TruffleInjectImmutableFrameFieldsPhase;

public final class TruffleCommunityCompilerConfiguration extends CommunityCompilerConfiguration {

    @Override
    public HighTier createHighTier(OptionValues options) {
        HighTier highTier = super.createHighTier(options);
        TruffleHostInliningPhase.install(highTier, options);
        TruffleInjectImmutableFrameFieldsPhase.install(highTier, options);
        return highTier;
    }

    @Override
    public void registerGraphBuilderPlugins(Plugins plugins, OptionValues options) {
        super.registerGraphBuilderPlugins(plugins, options);
        TruffleHostInliningPhase.installInlineInvokePlugin(plugins, options);
    }

}
