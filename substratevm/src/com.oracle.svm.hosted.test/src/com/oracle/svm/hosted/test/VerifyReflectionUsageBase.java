/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.core.test.VerifyPhase;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Base class for {@link VerifyReflectionUsage} to hide all the implementation details.
 */
public abstract class VerifyReflectionUsageBase extends VerifyPhase<CoreProviders> {

    /**
     * Report modes supported by this verification. It can be set via the
     * {@link #MODE_PROPERTY_NAME} property.
     */
    protected enum Mode {
        /**
         * Verify reflection usage and print human-readable error messages on failure.
         */
        DEFAULT,

        /**
         * Like {@code DEFAULT} but format error message so it can be copy-pasted into the exclude
         * list.
         */
        PRINT_EXCLUDE_LIST,

        /**
         * Verify that all entries on the exclude list are actually needed. It might only work
         * correctly if a {@linkplain #DEFAULT verification run} succeeded.
         */
        CHECK_EXCLUDE_LIST
    }

    private static final String MODE_PROPERTY_NAME = "svm.invariants.VerifyReflectionUsage.mode";
    protected static final Mode MODE = Mode.valueOf(System.getProperty(MODE_PROPERTY_NAME, Mode.DEFAULT.name()).toUpperCase(Locale.ROOT));
    private final List<ExcludeEntry> allExcludes;
    /*
     * CheckGraalInvariants verifies graphs in parallel, so a single verifier instance must track
     * processed methods and used excludes without mutating shared HashSet state concurrently.
     */
    private final Set<String> processedClasses = ConcurrentHashMap.newKeySet();
    private final Set<String> processedMethods = ConcurrentHashMap.newKeySet();
    private final Set<String> processedPackages = ConcurrentHashMap.newKeySet();
    private final Set<ExcludeEntry> usedExcludes = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("this-escape")
    protected VerifyReflectionUsageBase(List<List<? extends ExcludeEntry>> allExcludeLists) {
        this.allExcludes = allExcludeLists.stream().flatMap(Collection::stream).collect(Collectors.toUnmodifiableList());

        String context = "(fix exclude list in %s.java)".formatted(ClassUtil.getUnqualifiedName(VerifyReflectionUsage.class));
        allExcludeLists.forEach(e -> VerifyReflectionUsageBase.ensureSorted(e, context));
    }

    @Override
    public void finish() {
        if (MODE == Mode.CHECK_EXCLUDE_LIST) {
            Set<ExcludeEntry> excludes = getUnusedRelevantExcludes();
            if (!excludes.isEmpty()) {
                throw new VerificationError("The following exclude list entries are unused:\n  %s\nPlease delete the entries from the exclude list in %s.java",
                                excludes.stream().sorted(ExcludeEntry::compareToIgnoreCase).map(Object::toString).collect(Collectors.joining("\n  ")),
                                ClassUtil.getUnqualifiedName(getClass()));
            }
        }
    }

    protected static void ensureSorted(List<? extends ExcludeEntry> excludedClasses, Object context) {
        var it = excludedClasses.iterator();
        if (it.hasNext()) {
            ExcludeEntry prev = it.next();
            while (it.hasNext()) {
                ExcludeEntry curr = it.next();
                if (prev.compareToIgnoreCase(curr) > 0) {
                    throw new AssertionError(String.format(Locale.ROOT,
                                    "all exclude lists must be sorted lexicographically, but '%s' > '%s' %s",
                                    prev, curr, context));
                }
                prev = curr;
            }
        }
    }

    private static String createMethodValue(String className, String methodName) {
        return className + "#" + methodName;
    }

    protected static UnhandledExcludeEntry method(String className, String methodName) {
        return new UnhandledExcludeEntry(createMethodValue(className, methodName), METHOD_ENTRY_MATCHER);
    }

    protected static JustifiedExcludeEntry method(String className, String methodName, String justification) {
        return new JustifiedExcludeEntry(createMethodValue(className, methodName), justification, METHOD_ENTRY_MATCHER);
    }

    protected static UnhandledExcludeEntry clazz(String className) {
        return new UnhandledExcludeEntry(className, CLASS_ENTRY_MATCHER);
    }

    protected static UnhandledExcludeEntry clazzIf(String className, boolean condition) {
        if (condition) {
            return new UnhandledExcludeEntry(className, CLASS_ENTRY_MATCHER);
        }
        return null;
    }

    protected static JustifiedExcludeEntry clazz(String className, String justification) {
        return new JustifiedExcludeEntry(className, justification, CLASS_ENTRY_MATCHER);
    }

    protected static UnhandledExcludeEntry pkg(String packageName) {
        return new UnhandledExcludeEntry(packageName, PACKAGE_ENTRY_MATCHER);
    }

    protected static JustifiedExcludeEntry pkg(String packageName, String justification) {
        return new JustifiedExcludeEntry(packageName, justification, PACKAGE_ENTRY_MATCHER);
    }

    /**
     * Records a method whose graph was processed during the current verification run.
     * <p>
     * In {@link Mode#CHECK_EXCLUDE_LIST}, this lets the verifier distinguish entries that were
     * actually observable on the current class path from entries that were simply absent.
     */
    protected final void recordProcessedMethod(ResolvedJavaMethod method) {
        processedClasses.add(method.getDeclaringClass().toClassName());
        processedMethods.add(createMethodValue(method.getDeclaringClass().toClassName(), method.getName()));
        processedPackages.add(JVMCIReflectionUtil.getPackageName(method.getDeclaringClass()));
    }

    /**
     * Gets the exclude entries that are relevant to the current verification run.
     * <p>
     * An entry is relevant only if a processed graph referenced the corresponding class, method, or
     * package.
     */
    protected final Set<ExcludeEntry> getRelevantExcludes() {
        return allExcludes.stream().filter(e -> e.isRelevantForCurrentRun(this)).collect(Collectors.toSet());
    }

    /**
     * Gets the relevant exclude entries that were not exercised by any processed graph in the
     * current verification run.
     */
    protected final Set<ExcludeEntry> getUnusedRelevantExcludes() {
        Set<ExcludeEntry> excludes = new HashSet<>(getRelevantExcludes());
        excludes.removeAll(usedExcludes);
        return excludes;
    }

    protected boolean isExcluded(ResolvedJavaMethod method) {
        var contained = find(this.allExcludes, method);
        if (contained != null) {
            usedExcludes.add(contained);
            return true;
        }
        return false;
    }

    private static ExcludeEntry find(List<? extends ExcludeEntry> excludedClasses, ResolvedJavaMethod method) {
        for (ExcludeEntry e : excludedClasses) {
            if (e.matches(method)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    private interface EntryMatcher {
        String prefix();

        boolean matches(ResolvedJavaMethod method, String value);

        boolean isRelevantForCurrentRun(VerifyReflectionUsageBase verifier, String value);
    }

    private static final EntryMatcher METHOD_ENTRY_MATCHER = new EntryMatcher() {
        @Override
        public String prefix() {
            return "method";
        }

        @Override
        public boolean matches(ResolvedJavaMethod method, String value) {
            return value.equals(createMethodValue(method.getDeclaringClass().toClassName(), method.getName()));
        }

        @Override
        public boolean isRelevantForCurrentRun(VerifyReflectionUsageBase verifier, String value) {
            return verifier.processedMethods.contains(value);
        }
    };
    private static final EntryMatcher CLASS_ENTRY_MATCHER = new EntryMatcher() {
        @Override
        public String prefix() {
            return "class";
        }

        @Override
        public boolean matches(ResolvedJavaMethod method, String value) {
            return value.equals(method.getDeclaringClass().toClassName());
        }

        @Override
        public boolean isRelevantForCurrentRun(VerifyReflectionUsageBase verifier, String value) {
            return verifier.processedClasses.contains(value);
        }
    };
    private static final EntryMatcher PACKAGE_ENTRY_MATCHER = new EntryMatcher() {
        @Override
        public String prefix() {
            return "package";
        }

        @Override
        public boolean matches(ResolvedJavaMethod method, String value) {
            return JVMCIReflectionUtil.getPackageName(method.getDeclaringClass()).startsWith(value);
        }

        @Override
        public boolean isRelevantForCurrentRun(VerifyReflectionUsageBase verifier, String value) {
            return verifier.processedPackages.stream().anyMatch(pkg -> pkg.startsWith(value));
        }
    };

    protected abstract static class ExcludeEntry {
        protected final String value;
        protected final EntryMatcher matcher;

        private ExcludeEntry(String value, EntryMatcher matcher) {
            this.value = value;
            this.matcher = matcher;
        }

        public int compareToIgnoreCase(ExcludeEntry curr) {
            return value.compareToIgnoreCase(curr.value);
        }

        public boolean matches(ResolvedJavaMethod method) {
            return matcher.matches(method, value);
        }

        public boolean isRelevantForCurrentRun(VerifyReflectionUsageBase verifier) {
            return matcher.isRelevantForCurrentRun(verifier, value);
        }

        @Override
        public abstract String toString();
    }

    protected static final class UnhandledExcludeEntry extends ExcludeEntry {
        private UnhandledExcludeEntry(String value, EntryMatcher matcher) {
            super(value, matcher);
        }

        @Override
        public String toString() {
            return "%s: %s (unhandled)".formatted(matcher.prefix(), value);
        }
    }

    protected static final class JustifiedExcludeEntry extends ExcludeEntry {
        private final String justification;

        private JustifiedExcludeEntry(String value, String justification, EntryMatcher matcher) {
            super(value, matcher);
            GraalError.guarantee(justification != null && !justification.isEmpty(), "justification must not be null or empty");
            this.justification = justification;
        }

        @Override
        public String toString() {
            return "%s: %s (justified: %s)".formatted(matcher.prefix(), value, justification);
        }
    }

    protected static boolean isRuntimeOnly(StructuredGraph graph, MetaAccessProvider metaAccess) {
        ResolvedJavaMethod caller = graph.method();
        ResolvedJavaType declaringClass = caller.getDeclaringClass();
        if (AnnotationUtil.isAnnotationPresent(declaringClass, TargetClass.class) || AnnotationUtil.isAnnotationPresent(declaringClass, Substitute.class)) {
            // Substitutions are runtime code which is allowed to use reflection
            return true;
        }
        ResolvedJavaType substrateUtilType = metaAccess.lookupJavaType(SubstrateUtil.class);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            ResolvedJavaType calleeDeclaringClass = callee.getDeclaringClass();
            if (substrateUtilType.equals(calleeDeclaringClass) && "guaranteeRuntimeOnly".equals(callee.getName())) {
                // method is marked as "runtime-only" - ignoring
                verifyTopLevel(t);
                return true;
            }
        }
        return false;
    }

    private static void verifyTopLevel(MethodCallTargetNode t) {
        for (FixedNode fixedNode : GraphUtil.predecessorIterable((FixedNode) t.invoke().asFixedNode().predecessor())) {
            if (fixedNode.equals(fixedNode.graph().start())) {
                return;
            }
            if (fixedNode instanceof AbstractMergeNode || fixedNode instanceof ControlSplitNode) {
                ResolvedJavaMethod callee = t.targetMethod();
                throw new VerificationError(t.invoke(), "Call to %s must be at the beginning of the method",
                                callee.format("%H.%n(%p)"));
            }
        }
        throw GraalError.shouldNotReachHere("No start node?");
    }

}
