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

import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.util.Digest;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This substitution replaces all lambda proxy types with types that have a stable names. The name
 * is formed from the lambda capture site.
 * <p>
 * NOTE: if multiple lambda proxies have the same capture-site hash, a unique number is appended for
 * that class. To make this conflict case truly stable, analysis must be run in the single-threaded
 * mode.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class LambdaProxyRenamingSubstitutionProcessor extends SubstitutionProcessor {

    private final ConcurrentHashMap<ResolvedJavaType, LambdaSubstitutionType> typeSubstitutions;
    private final EconomicSet<String> uniqueLambdaProxyNames;

    LambdaProxyRenamingSubstitutionProcessor() {
        this.typeSubstitutions = new ConcurrentHashMap<>();
        this.uniqueLambdaProxyNames = EconomicSet.create();
    }

    public static LambdaProxyRenamingSubstitutionProcessor singleton() {
        if (!ImageSingletons.contains(LambdaProxyRenamingSubstitutionProcessor.class)) {
            throw VMError.shouldNotReachHere("Lambda proxy renaming accessed before LambdaProxyRenamingSubstitutionProcessor was installed during setup");
        }
        return ImageSingletons.lookup(LambdaProxyRenamingSubstitutionProcessor.class);
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
            String captureSite = LambdaParser.findLambdaCaptureSite(OriginalClassProvider.getJavaClass(key));
            String lambdaTargetName = withCaptureSite(key.getName(), captureSite);
            return new LambdaSubstitutionType(key, findUniqueLambdaProxyName(lambdaTargetName));
        });
    }

    private static String withCaptureSite(String lambdaTargetName, String captureSite) {
        int startIndexOfLambdaName = lambdaTargetName.indexOf(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING);
        int endIndexOfLambdaName = lambdaTargetName.indexOf(';', startIndexOfLambdaName);
        String siteSpecificSignature = Digest.digestAsHex(captureSite);
        return lambdaTargetName.substring(0, startIndexOfLambdaName) + LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING + LambdaUtils.ADDRESS_PREFIX + siteSpecificSignature +
                        lambdaTargetName.substring(endIndexOfLambdaName);
    }

    /**
     * Finds a unique name for lambda proxies with the same base name. To create unique names when
     * conflicts are found the naming scheme uses a counter: first a 0 is added to the end of the
     * generated lambda name, and it is incremented until a unique name is found. This also means
     * that a conflicting name is truly stable only in a single threaded build, i.e., when conflicts
     * are discovered in the same order. Since the conflict counter doesn't have an upper limit the
     * generated lambda name length can vary but the base hash is always 32 hex digits long.
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

    /**
     * Find the base name by subtracting the counter injected by
     * {@link #findUniqueLambdaProxyName(String)}. Note that the counter can be on multiple digits
     * but the base hash is always 32 hex digits long.
     */
    private static String getBaseName(String lambdaTargetName) {
        int startIndexOfHash = lambdaTargetName.indexOf(LambdaUtils.ADDRESS_PREFIX) + LambdaUtils.ADDRESS_PREFIX.length();
        return lambdaTargetName.substring(0, startIndexOfHash + 32);
    }

    /**
     * Check if this lambda's name conflicted with any other entries, i.e., if the conflict counter
     * ever got to 1.
     */
    public boolean isNameAlwaysStable(String lambdaTargetName) {
        String baseName = getBaseName(lambdaTargetName);
        return !uniqueLambdaProxyNames.contains(baseName + "1;");
    }
}
