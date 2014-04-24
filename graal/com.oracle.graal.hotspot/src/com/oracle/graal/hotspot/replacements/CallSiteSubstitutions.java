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
package com.oracle.graal.hotspot.replacements;

import java.lang.invoke.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.nodes.spi.*;

@ServiceProvider(ReplacementsProvider.class)
public class CallSiteSubstitutions implements ReplacementsProvider {

    @Override
    public void registerReplacements(MetaAccessProvider metaAccess, LoweringProvider loweringProvider, SnippetReflectionProvider snippetReflection, Replacements replacements, TargetDescription target) {
        replacements.registerSubstitutions(ConstantCallSiteSubstitutions.class);
        replacements.registerSubstitutions(MutableCallSiteSubstitutions.class);
        replacements.registerSubstitutions(VolatileCallSiteSubstitutions.class);
    }

    @ClassSubstitution(ConstantCallSite.class)
    private static class ConstantCallSiteSubstitutions {

        @MacroSubstitution(isStatic = false, macro = CallSiteTargetNode.class)
        public static native MethodHandle getTarget(ConstantCallSite callSite);
    }

    @ClassSubstitution(MutableCallSite.class)
    private static class MutableCallSiteSubstitutions {

        @MacroSubstitution(isStatic = false, macro = CallSiteTargetNode.class)
        public static native MethodHandle getTarget(MutableCallSite callSite);
    }

    @ClassSubstitution(VolatileCallSite.class)
    private static class VolatileCallSiteSubstitutions {

        @MacroSubstitution(isStatic = false, macro = CallSiteTargetNode.class)
        public static native MethodHandle getTarget(VolatileCallSite callSite);
    }
}
