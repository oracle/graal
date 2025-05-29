/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.imagelayer.HostedDynamicLayerInfo;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredFeature
public class HostedMethodNameFactory implements InternalFeature {
    public static final class Options {
        @Option(help = "Log unique names which do not match across layers. This is an experimental option which will be removed.") //
        public static final HostedOptionKey<Boolean> LogUniqueNameInconsistencies = new HostedOptionKey<>(false);
    }

    private Map<String, Integer> methodNameCount = new ConcurrentHashMap<>();
    private Set<String> uniqueShortNames = ConcurrentHashMap.newKeySet();
    private final boolean buildingExtensionLayer = ImageLayerBuildingSupport.buildingExtensionLayer();
    private final boolean logUniqueNameInconsistencies = Options.LogUniqueNameInconsistencies.getValue();
    private Set<String> reservedUniqueShortNames;

    public record MethodNameInfo(String name, String uniqueShortName) {
    }

    public interface NameGenerator {
        MethodNameInfo generateMethodNameInfo(int collisionCount);

        String generateUniqueName(String name);
    }

    public static HostedMethodNameFactory singleton() {
        return ImageSingletons.lookup(HostedMethodNameFactory.class);
    }

    MethodNameInfo createNames(NameGenerator generator, AnalysisMethod aMethod) {
        MethodNameInfo result = buildingExtensionLayer ? HostedDynamicLayerInfo.loadMethodNameInfo(aMethod) : null;
        if (result != null) {
            assert reservedUniqueShortNames.contains(result.uniqueShortName()) : result;
            if (logUniqueNameInconsistencies) {
                boolean consistentNames = generator.generateUniqueName(result.name()).equals(result.uniqueShortName);
                if (!consistentNames) {
                    LogUtils.warning("Unique names are inconsistent for %s", aMethod.getQualifiedName());
                }
            }

            boolean added = uniqueShortNames.add(result.uniqueShortName());
            if (added) {
                /*
                 * Currently it is possible for the same method id to be assigned to multiple
                 * AnalysisMethods. However, only one is assigned this name.
                 */
                return result;
            }
        }

        MethodNameInfo initialName = generator.generateMethodNameInfo(0);
        result = initialName;

        do {
            int collisionCount = methodNameCount.merge(initialName.uniqueShortName(), 0, (oldValue, value) -> oldValue + 1);
            if (collisionCount != 0) {
                result = generator.generateMethodNameInfo(collisionCount);
            }
            /*
             * Redo if the short name is reserved.
             */
        } while (buildingExtensionLayer && reservedUniqueShortNames.contains(result.uniqueShortName()));

        boolean added = uniqueShortNames.add(result.uniqueShortName());
        VMError.guarantee(added, "failed to generate uniqueShortName for HostedMethod: %s", result.uniqueShortName());

        return result;
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        reservedUniqueShortNames = buildingExtensionLayer ? HostedDynamicLayerInfo.singleton().getReservedNames() : null;
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        methodNameCount = null;
        uniqueShortNames = null;
        reservedUniqueShortNames = null;
    }
}
