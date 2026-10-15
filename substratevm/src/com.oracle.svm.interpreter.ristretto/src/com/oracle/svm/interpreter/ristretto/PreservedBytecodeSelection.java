/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;

import jdk.graal.compiler.debug.MethodFilter;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Signature;

/** Parses preservation filters and enumerates exact candidates without registering analysis roots. */
@Platforms(Platform.HOSTED_ONLY.class)
final class PreservedBytecodeSelection {
    private final MethodFilter filter;
    private final String commaSeparatedPatterns;

    private PreservedBytecodeSelection(MethodFilter filter, String commaSeparatedPatterns) {
        this.filter = filter;
        this.commaSeparatedPatterns = commaSeparatedPatterns;
    }

    static PreservedBytecodeSelection parse(String patterns) {
        try {
            return new PreservedBytecodeSelection(MethodFilter.parse(patterns), patterns);
        } catch (IllegalArgumentException e) {
            throw UserError.abort(e, "Invalid value '%s' for option '%s': %s", patterns,
                            RistrettoOptions.JITPreserveBytecodesForRuntime.getName(), e.getMessage());
        }
    }

    boolean matches(AnalysisMethod method) {
        return filter.matches(method);
    }

    /**
     * Resolves exact entries, applies exclusions to each candidate, and rejects unresolved or
     * ambiguous selections.
     */
    void forEachExactMethod(FeatureImpl.BeforeAnalysisAccessImpl access, Consumer<AnalysisMethod> consumer) {
        forEachExactMethod(access, RistrettoOptions.JITPreserveBytecodesForRuntime.getName(), consumer);
    }

    void forEachExactMethod(FeatureImpl.BeforeAnalysisAccessImpl access, String selectionSource, Consumer<AnalysisMethod> consumer) {
        for (String rawPattern : commaSeparatedPatterns.split(",")) {
            ExactMethodFilterEntry entry = ExactMethodFilterEntry.parse(rawPattern);
            if (entry == null) {
                continue;
            }
            Class<?> declaringClass = access.findClassByName(entry.className());
            if (declaringClass == null) {
                if (!entry.className().contains(".")) {
                    // A simple class name can also match classes in any package via MethodFilter.
                    continue;
                }
                throw UserError.abort("Could not resolve class '%s' selected by %s.", entry.className(), selectionSource);
            }
            AnalysisType declaringType = access.getMetaAccess().lookupJavaType(declaringClass);
            Set<List<AnalysisType>> resolvedParameterLists = new HashSet<>();
            boolean positiveMatched = false;
            AnalysisMethod[] candidates = switch (entry.methodName()) {
                case "<init>" -> declaringType.getDeclaredConstructors(false);
                case "<clinit>" -> declaringType.getClassInitializer() == null ? AnalysisMethod.EMPTY_ARRAY : new AnalysisMethod[]{declaringType.getClassInitializer()};
                default -> declaringType.getDeclaredMethods(false);
            };
            for (AnalysisMethod method : candidates) {
                if (!entry.matches(method)) {
                    continue;
                }
                positiveMatched = true;
                if (filter.matches(method)) {
                    /* Return-type variants, including covariant bridges, share one parameter list. */
                    resolvedParameterLists.add(method.getSignature().toParameterList(null));
                    consumer.accept(method);
                }
            }
            if (!positiveMatched) {
                throw UserError.abort("Could not resolve method '%s' selected by %s.", rawPattern.trim(), selectionSource);
            } else if (resolvedParameterLists.size() > 1 && entry.hasParameters()) {
                throw UserError.abort("Method '%s' selected by %s is ambiguous. Use fully qualified parameter names.", rawPattern.trim(), selectionSource);
            }
        }
    }

    /**
     * Exact method filter used to enumerate candidates before analysis root registration.
     *
     * The general analysis filter still handles the full option syntax for methods that analysis has
     * already reached. This entry accepts only concrete {@code Class.method} patterns (with or
     * without a package), optionally followed by a semicolon-separated parameter list, because analysis roots can be
     * registered up front only when the declaring class and method can be resolved without scanning
     * every method in the universe.
     */
    private record ExactMethodFilterEntry(String className, String methodName, List<String> parameters) {
        /**
         * Parses one exact preserved-bytecode pattern, returning {@code null} for negated, globbed,
         * empty, malformed, or class-unqualified entries that must wait for the normal filter pass.
         */
        private static ExactMethodFilterEntry parse(String rawPattern) {
            String pattern = rawPattern.trim();
            if (pattern.isEmpty() || pattern.startsWith("~") || containsGlob(pattern)) {
                return null;
            }

            List<String> parameters = null;
            int signatureStart = pattern.indexOf('(');
            if (signatureStart != -1) {
                if (!pattern.endsWith(")")) {
                    return null;
                }
                String signature = pattern.substring(signatureStart + 1, pattern.length() - 1).trim();
                parameters = signature.isEmpty() ? List.of() : Arrays.stream(signature.split(";", -1)).map(String::trim).toList();
                if (parameters.stream().anyMatch(String::isBlank)) {
                    return null;
                }
                pattern = pattern.substring(0, signatureStart);
            }

            int separator = pattern.lastIndexOf('.');
            if (separator <= 0 || separator == pattern.length() - 1) {
                return null;
            }
            String className = pattern.substring(0, separator);
            return new ExactMethodFilterEntry(className, pattern.substring(separator + 1), parameters);
        }

        /**
         * Returns whether the pattern needs wildcard matching and therefore cannot be resolved during
         * root pre-registration.
         */
        private static boolean containsGlob(String pattern) {
            return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
        }

        /**
         * Matches a concrete declared method before the full {@link MethodFilter} decides whether
         * that candidate remains selected after negative entries are applied.
         */
        private boolean matches(AnalysisMethod method) {
            return method.getName().equals(methodName) && matchesParameters(method);
        }

        /**
         * Matches the optional exact parameter list against a candidate method.
         */
        private boolean matchesParameters(AnalysisMethod method) {
            if (parameters == null) {
                return true;
            }
            Signature signature = method.getSignature();
            if (signature.getParameterCount(false) != parameters.size()) {
                return false;
            }
            for (int i = 0; i < parameters.size(); i++) {
                if (!matchesParameter(signature.getParameterType(i, null), parameters.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasParameters() {
            return parameters != null;
        }

        /**
         * Accepts a fully qualified, simple, or nested-unqualified parameter type name, including
         * the complete package-unqualified binary name of a nested class.
         */
        private static boolean matchesParameter(JavaType actualParameter, String expectedParameter) {
            String javaName = actualParameter.toJavaName();
            return expectedParameter.equals(javaName) || expectedParameter.equals(unqualifiedName(javaName)) || javaName.endsWith("$" + expectedParameter) ||
                            (expectedParameter.indexOf('.') < 0 && expectedParameter.indexOf('$') >= 0 &&
                                            expectedParameter.equals(javaName.substring(javaName.lastIndexOf('.') + 1)));
        }

        /**
         * Extracts the simple type name used by legacy option values.
         */
        private static String unqualifiedName(String javaName) {
            int lastPackageSeparator = javaName.lastIndexOf('.');
            int lastNestedClassSeparator = javaName.lastIndexOf('$');
            return javaName.substring(Math.max(lastPackageSeparator, lastNestedClassSeparator) + 1);
        }
    }
}
