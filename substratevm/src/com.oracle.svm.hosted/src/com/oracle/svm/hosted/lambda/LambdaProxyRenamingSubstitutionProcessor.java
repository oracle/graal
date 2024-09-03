/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.lambda;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.BaseLayerType;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This substitution replaces all lambda proxy types with types that have a stable names. The name
 * is formed from the signature of the target method that the lambda is calling.
 * <p>
 * NOTE: there is a particular case in which names are not stable. If multiple lambda proxies have a
 * same target in a same class they are indistinguishable in bytecode. Then their stable names get
 * appended with a unique number for that class. To make this corner case truly stable, analysis
 * must be run in the single-threaded mode.
 */

public class LambdaProxyRenamingSubstitutionProcessor extends SubstitutionProcessor {

    private final ConcurrentHashMap<ResolvedJavaType, LambdaSubstitutionType> typeSubstitutions;
    private final Set<String> uniqueLambdaProxyNames;

    LambdaProxyRenamingSubstitutionProcessor() {
        this.typeSubstitutions = new ConcurrentHashMap<>();
        this.uniqueLambdaProxyNames = new HashSet<>();
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        if (LambdaUtils.isLambdaType(type) && !type.getClass().equals(LambdaSubstitutionType.class) && !(type.getClass().equals(BaseLayerType.class))) {
            return getSubstitution(type);
        } else {
            return type;
        }
    }

    private LambdaSubstitutionType getSubstitution(ResolvedJavaType original) {
        return typeSubstitutions.computeIfAbsent(original, (key) -> {
            String lambdaTargetName = LambdaUtils.findStableLambdaName(key);
            return new LambdaSubstitutionType(key, findUniqueLambdaProxyName(lambdaTargetName));
        });
    }

    /**
     * Finds a unique name for a lambda proxies with a same target originating from the same class.
     *
     * NOTE: the name truly stable only in a single threaded build.
     */
    private String findUniqueLambdaProxyName(String lambdaTargetName) {
        synchronized (uniqueLambdaProxyNames) {
            String stableNameBase = lambdaTargetName.substring(0, lambdaTargetName.length() - 1);
            String newStableName = stableNameBase + "0;";

            int i = 1;
            while (uniqueLambdaProxyNames.contains(newStableName)) {
                newStableName = stableNameBase + i + ";";
                i += 1;
            }
            uniqueLambdaProxyNames.add(newStableName);

            return newStableName;
        }
    }

    public boolean isNameAlwaysStable(String lambdaTargetName) {
        return !uniqueLambdaProxyNames.contains(lambdaTargetName.substring(0, lambdaTargetName.length() - 1) + "1;");
    }
}
