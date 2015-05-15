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
package com.oracle.graal.hotspot;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.*;

/**
 * Filters certain method substitutions based on whether there is underlying hardware support for
 * them.
 */
public class HotSpotReplacementsImpl extends ReplacementsImpl {

    private final HotSpotVMConfig config;

    public HotSpotReplacementsImpl(Providers providers, SnippetReflectionProvider snippetReflection, HotSpotVMConfig config, TargetDescription target) {
        super(providers, snippetReflection, target);
        this.config = config;
    }

    @Override
    protected boolean hasGenericInvocationPluginAnnotation(ResolvedJavaMethod method) {
        return method.getAnnotation(HotSpotOperation.class) != null || super.hasGenericInvocationPluginAnnotation(method);
    }

    @Override
    protected ResolvedJavaMethod registerMethodSubstitution(ClassReplacements cr, Executable originalMethod, Method substituteMethod) {
        final Class<?> substituteClass = substituteMethod.getDeclaringClass();
        if (substituteClass == IntegerSubstitutions.class || substituteClass == LongSubstitutions.class) {
            if (substituteMethod.getName().equals("numberOfLeadingZeros")) {
                if (config.useCountLeadingZerosInstruction) {
                    return null;
                }
            } else if (substituteMethod.getName().equals("numberOfTrailingZeros")) {
                if (config.useCountTrailingZerosInstruction) {
                    return null;
                }
            }
        }
        return super.registerMethodSubstitution(cr, originalMethod, substituteMethod);
    }
}
