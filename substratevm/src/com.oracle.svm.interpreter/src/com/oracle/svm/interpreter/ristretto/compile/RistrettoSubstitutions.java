/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import static com.oracle.svm.interpreter.ristretto.RistrettoFeature.RistrettoEnabled;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.replacements.ReplacementsImpl;

@TargetClass(value = ReplacementsImpl.class, onlyWith = RistrettoEnabled.class)
final class Target_jdk_graal_compiler_replacements_ReplacementsImpl {

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = RistrettoGraphBuilderPluginTransformer.class) //
    private GraphBuilderConfiguration.Plugins graphBuilderPlugins;

    private static final class RistrettoGraphBuilderPluginTransformer implements FieldValueTransformer {

        @Override
        public Object transform(Object receiver, Object originalValue) {
            GraphBuilderConfiguration.Plugins runtimeParseGraphBuilderPlugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
            RistrettoGraphBuilderPlugins.setRuntimeGraphBuilderPlugins(runtimeParseGraphBuilderPlugins);
            runtimeParseGraphBuilderPlugins.getInvocationPlugins().closeRegistration();
            return runtimeParseGraphBuilderPlugins;
        }

    }
}

public class RistrettoSubstitutions {
    // Dummy
}
