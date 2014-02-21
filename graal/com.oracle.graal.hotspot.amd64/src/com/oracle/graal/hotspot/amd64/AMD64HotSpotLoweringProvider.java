/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.amd64.*;

public class AMD64HotSpotLoweringProvider extends HotSpotLoweringProvider {

    private AMD64ConvertSnippets.Templates convertSnippets;

    public AMD64HotSpotLoweringProvider(HotSpotGraalRuntime runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers) {
        super(runtime, metaAccess, foreignCalls, registers);
    }

    @Override
    public void initialize(HotSpotProviders providers, HotSpotVMConfig config) {
        convertSnippets = new AMD64ConvertSnippets.Templates(providers, providers.getCodeCache().getTarget());
        super.initialize(providers, config);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof FloatConvertNode) {
            convertSnippets.lower((FloatConvertNode) n, tool);
        } else {
            super.lower(n, tool);
        }
    }
}
