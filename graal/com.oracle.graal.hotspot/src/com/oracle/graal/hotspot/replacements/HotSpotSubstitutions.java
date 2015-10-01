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

import static com.oracle.graal.compiler.common.GraalOptions.UseGraalQueries;
import jdk.internal.jvmci.code.TargetDescription;
import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.service.ServiceProvider;
import sun.reflect.ConstantPool;
import sun.reflect.Reflection;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.debug.query.DelimitationAPI;
import com.oracle.graal.debug.query.GraalQueryAPI;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.nodes.spi.ReplacementsProvider;

@ServiceProvider(ReplacementsProvider.class)
public class HotSpotSubstitutions implements ReplacementsProvider {

    @Override
    public void registerReplacements(MetaAccessProvider metaAccess, LoweringProvider loweringProvider, SnippetReflectionProvider snippetReflection, Replacements replacements, TargetDescription target) {
        replacements.registerSubstitutions(System.class, SystemSubstitutions.class);
        replacements.registerSubstitutions(Thread.class, ThreadSubstitutions.class);
        replacements.registerSubstitutions(Reflection.class, ReflectionSubstitutions.class);
        replacements.registerSubstitutions(ConstantPool.class, ConstantPoolSubstitutions.class);
        if (UseGraalQueries.getValue()) {
            replacements.registerSubstitutions(GraalQueryAPI.class, GraalQueryAPISubstitutions.class);
            replacements.registerSubstitutions(DelimitationAPI.class, DelimitationAPISubstitutions.class);
        }
    }
}
