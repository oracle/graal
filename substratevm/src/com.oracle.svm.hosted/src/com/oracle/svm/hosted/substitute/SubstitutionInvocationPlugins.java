/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@Platforms(Platform.HOSTED_ONLY.class)
public class SubstitutionInvocationPlugins extends InvocationPlugins {

    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
    private EconomicMap<String, Integer> missingIntrinsicMetrics;

    public SubstitutionInvocationPlugins(AnnotationSubstitutionProcessor annotationSubstitutionProcessor) {
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;
        this.missingIntrinsicMetrics = null;
    }

    @Override
    protected void register(Type declaringClass, InvocationPlugin plugin, boolean allowOverwrite) {
        Type targetClass;
        if (declaringClass instanceof Class<?> annotatedClass) {
            targetClass = annotationSubstitutionProcessor.getTargetClass(annotatedClass);
            if (targetClass != declaringClass) {
                /* Found a target class. Check if it is included. */
                Executable annotatedMethod = plugin.name.equals("<init>") ? resolveConstructor(annotatedClass, plugin) : resolveMethod(annotatedClass, plugin);
                String originalName = annotationSubstitutionProcessor.findOriginalElementName(annotatedMethod, (Class<?>) targetClass);
                if (originalName == null) {
                    /*
                     * If the name is null, the element should not be substituted. Thus, we should
                     * also not register the invocation plugin.
                     */
                    return;
                }
                if (!originalName.equals(plugin.name)) {
                    throw VMError.unimplemented(String.format("""
                                    InvocationPlugins cannot yet deal with substitution methods that set the target name via the @TargetElement(name = ...) property.
                                    Annotated method "%s" vs target method "%s".""", plugin.name, originalName));
                }
            }
        } else {
            targetClass = declaringClass;
        }
        super.register(targetClass, plugin, allowOverwrite);
    }

    @Override
    public void notifyNoPlugin(ResolvedJavaMethod targetMethod, OptionValues options) {
        if (Options.WarnMissingIntrinsic.getValue(options)) {
            for (Class<?> annotationType : AnnotationAccess.getAnnotationTypes(targetMethod)) {
                if (ClassUtil.getUnqualifiedName(annotationType).contains("IntrinsicCandidate")) {
                    String method = String.format("%s.%s%s", targetMethod.getDeclaringClass().toJavaName().replace('.', '/'), targetMethod.getName(),
                                    targetMethod.getSignature().toMethodDescriptor());
                    synchronized (this) {
                        if (missingIntrinsicMetrics == null) {
                            missingIntrinsicMetrics = EconomicMap.create();
                            try {
                                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                    if (missingIntrinsicMetrics.size() > 0) {
                                        System.out.format("[Warning] Missing intrinsics found: %d%n", missingIntrinsicMetrics.size());
                                        List<Pair<String, Integer>> data = new ArrayList<>();
                                        final MapCursor<String, Integer> cursor = missingIntrinsicMetrics.getEntries();
                                        while (cursor.advance()) {
                                            data.add(Pair.create(cursor.getKey(), cursor.getValue()));
                                        }
                                        data.stream().sorted(Comparator.comparing(Pair::getRight, Comparator.reverseOrder())).forEach(
                                                        pair -> System.out.format("        - %d occurrences during parsing: %s%n", pair.getRight(), pair.getLeft()));
                                    }
                                }));
                            } catch (IllegalStateException e) {
                                // shutdown in progress, no need to register the hook
                            }
                        }
                        if (missingIntrinsicMetrics.containsKey(method)) {
                            missingIntrinsicMetrics.put(method, missingIntrinsicMetrics.get(method) + 1);
                        } else {
                            System.out.format("[Warning] Missing intrinsic %s found during parsing.%n", method);
                            missingIntrinsicMetrics.put(method, 1);
                        }
                    }
                    break;
                }
            }
        }
    }
}
