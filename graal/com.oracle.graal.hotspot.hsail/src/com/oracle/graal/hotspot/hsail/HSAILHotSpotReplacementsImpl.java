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
package com.oracle.graal.hotspot.hsail;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.*;

/**
 * The substitutions and snippets supported by HSAIL.
 */
public class HSAILHotSpotReplacementsImpl extends ReplacementsImpl {

    private final Replacements host;

    public HSAILHotSpotReplacementsImpl(Providers providers, Assumptions assumptions, TargetDescription target, Replacements host) {
        super(providers, assumptions, target);
        this.host = host;
    }

    @Override
    protected ResolvedJavaMethod registerMethodSubstitution(Member originalMethod, Method substituteMethod) {
        // TODO: decide if we want to override this in any way
        return super.registerMethodSubstitution(originalMethod, substituteMethod);
    }

    @Override
    public Class<? extends FixedWithNextNode> getMacroSubstitution(ResolvedJavaMethod method) {
        Class<? extends FixedWithNextNode> klass = super.getMacroSubstitution(method);
        if (klass == null) {
            // eventually we want to only defer certain macro substitutions to the host, but for now
            // we will do everything
            return host.getMacroSubstitution(method);
        }
        return klass;
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method) {
        // Must work in cooperation with HSAILHotSpotLoweringProvider
        return host.getSnippet(method);
    }

    @Override
    public StructuredGraph getMethodSubstitution(ResolvedJavaMethod original) {
        StructuredGraph m = super.getMethodSubstitution(original);
        if (m == null) {
            // eventually we want to only defer certain substitutions to the host, but for now we
            // will defer everything
            return host.getMethodSubstitution(original);
        }
        return m;
    }

}
