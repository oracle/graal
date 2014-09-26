/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Method substitutions that are VM-independent.
 */
@ServiceProvider(ReplacementsProvider.class)
public class GraalMethodSubstitutions implements ReplacementsProvider {

    public void registerReplacements(MetaAccessProvider metaAccess, LoweringProvider loweringProvider, SnippetReflectionProvider snippetReflection, Replacements replacements, TargetDescription target) {
        BoxingSubstitutions.registerReplacements(replacements);
        if (Intrinsify.getValue()) {
            replacements.registerSubstitutions(Array.class, ArraySubstitutions.class);
            replacements.registerSubstitutions(Math.class, MathSubstitutionsX86.class);
            replacements.registerSubstitutions(Double.class, DoubleSubstitutions.class);
            replacements.registerSubstitutions(Float.class, FloatSubstitutions.class);
            replacements.registerSubstitutions(Long.class, LongSubstitutions.class);
            replacements.registerSubstitutions(Integer.class, IntegerSubstitutions.class);
            replacements.registerSubstitutions(Character.class, CharacterSubstitutions.class);
            replacements.registerSubstitutions(Short.class, ShortSubstitutions.class);
            replacements.registerSubstitutions(UnsignedMath.class, UnsignedMathSubstitutions.class);
            replacements.registerSubstitutions(Edges.class, EdgesSubstitutions.class);
        }
    }
}
