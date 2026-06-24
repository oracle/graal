/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterFetchOpcode;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandler;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterThreadingExit;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * Shared resolver for bytecode-interpreter annotations.
 * <p>
 * The compiler-side annotations are defined by {@link BytecodeInterpreterDirectives}. Frontends
 * that expose an equivalent annotation API, such as Truffle, can register their resolved
 * annotation types with
 * {@link #registerAnnotationTypes(ResolvedJavaType, ResolvedJavaType, ResolvedJavaType, ResolvedJavaType)}
 * so shared bytecode-handler support can recognize those annotations without depending on the
 * frontend API classes.
 */
public final class BytecodeInterpreterAnnotations {

    private static final CopyOnWriteArrayList<ResolvedAnnotationSet> RESOLVED_ANNOTATION_SETS = new CopyOnWriteArrayList<>();

    private BytecodeInterpreterAnnotations() {
    }

    /**
     * Registers the annotations defined by {@link BytecodeInterpreterDirectives}.
     */
    public static void registerCompilerDirectives(MetaAccessProvider metaAccess) {
        registerCompilerDirectives(metaAccess, Function.identity());
    }

    /**
     * Registers the annotations defined by {@link BytecodeInterpreterDirectives} after applying
     * {@code typeMap} to each resolved annotation type.
     */
    public static void registerCompilerDirectives(MetaAccessProvider metaAccess, Function<ResolvedJavaType, ResolvedJavaType> typeMap) {
        registerAnnotationTypes(typeMap.apply(metaAccess.lookupJavaType(BytecodeInterpreterHandler.class)),
                        typeMap.apply(metaAccess.lookupJavaType(BytecodeInterpreterHandlerConfig.class)),
                        typeMap.apply(metaAccess.lookupJavaType(BytecodeInterpreterFetchOpcode.class)),
                        typeMap.apply(metaAccess.lookupJavaType(BytecodeInterpreterThreadingExit.class)));
    }

    /**
     * Registers an annotation set that follows the same contract as
     * {@link BytecodeInterpreterDirectives}.
     */
    public static synchronized void registerAnnotationTypes(ResolvedJavaType bytecodeInterpreterHandler, ResolvedJavaType bytecodeInterpreterHandlerConfig,
                    ResolvedJavaType bytecodeInterpreterFetchOpcode, ResolvedJavaType bytecodeInterpreterThreadingExit) {
        ResolvedAnnotationSet annotationSet = new ResolvedAnnotationSet(bytecodeInterpreterHandler, bytecodeInterpreterHandlerConfig, bytecodeInterpreterFetchOpcode,
                        bytecodeInterpreterThreadingExit);
        if (!RESOLVED_ANNOTATION_SETS.contains(annotationSet)) {
            RESOLVED_ANNOTATION_SETS.add(annotationSet);
        }
    }

    private static AnnotationValue findDeclaredAnnotation(Annotated annotated, Function<ResolvedAnnotationSet, ResolvedJavaType> typeSelector) {
        Map<ResolvedJavaType, AnnotationValue> values = AnnotationValueSupport.getDeclaredAnnotationValues(annotated);
        if (values.isEmpty()) {
            return null;
        }
        for (ResolvedAnnotationSet annotationSet : RESOLVED_ANNOTATION_SETS) {
            ResolvedJavaType annotationType = typeSelector.apply(annotationSet);
            if (annotationType == null) {
                continue;
            }
            AnnotationValue value = values.get(annotationType);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static boolean hasBytecodeInterpreterHandlerConfig(Annotated annotated) {
        return getBytecodeInterpreterHandlerConfig(annotated) != null;
    }

    public static AnnotationValue getBytecodeInterpreterHandler(Annotated annotated) {
        return findDeclaredAnnotation(annotated, ResolvedAnnotationSet::bytecodeInterpreterHandler);
    }

    public static AnnotationValue getBytecodeInterpreterHandlerConfig(Annotated annotated) {
        return findDeclaredAnnotation(annotated, ResolvedAnnotationSet::bytecodeInterpreterHandlerConfig);
    }

    public static AnnotationValue getBytecodeInterpreterFetchOpcode(Annotated annotated) {
        return findDeclaredAnnotation(annotated, ResolvedAnnotationSet::bytecodeInterpreterFetchOpcode);
    }

    public static AnnotationValue getBytecodeInterpreterThreadingExit(Annotated annotated) {
        return findDeclaredAnnotation(annotated, ResolvedAnnotationSet::bytecodeInterpreterThreadingExit);
    }

    public static ResolvedJavaMethod getUniqueFetchOpcodeMethod(ResolvedJavaType holder) {
        List<ResolvedJavaMethod> matches = Arrays.stream(holder.getDeclaredMethods(false)).filter(m -> getBytecodeInterpreterFetchOpcode(m) != null).toList();
        GraalError.guarantee(matches.size() == 1, "Expected exactly one method annotated with BytecodeInterpreterFetchOpcode, found %d", matches.size());
        return matches.getFirst();
    }

    public static ResolvedJavaMethod getUniqueThreadingExitMethod(ResolvedJavaType holder) {
        List<ResolvedJavaMethod> matches = Arrays.stream(holder.getDeclaredMethods(false)).filter(m -> getBytecodeInterpreterThreadingExit(m) != null).toList();
        GraalError.guarantee(matches.size() <= 1, "Expected at most one method annotated with BytecodeInterpreterThreadingExit, found %d", matches.size());
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private record ResolvedAnnotationSet(
                    ResolvedJavaType bytecodeInterpreterHandler,
                    ResolvedJavaType bytecodeInterpreterHandlerConfig,
                    ResolvedJavaType bytecodeInterpreterFetchOpcode,
                    ResolvedJavaType bytecodeInterpreterThreadingExit) {

        private ResolvedAnnotationSet {
            bytecodeInterpreterHandler = Objects.requireNonNull(bytecodeInterpreterHandler, "bytecodeInterpreterHandler");
            bytecodeInterpreterHandlerConfig = Objects.requireNonNull(bytecodeInterpreterHandlerConfig, "bytecodeInterpreterHandlerConfig");
            bytecodeInterpreterFetchOpcode = Objects.requireNonNull(bytecodeInterpreterFetchOpcode, "bytecodeInterpreterFetchOpcode");
        }
    }
}
