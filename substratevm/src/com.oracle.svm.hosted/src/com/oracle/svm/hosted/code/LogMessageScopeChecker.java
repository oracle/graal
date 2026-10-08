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
package com.oracle.svm.hosted.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.UninterruptibleAnnotationUtils;
import com.oracle.svm.core.UninterruptibleGuestValue;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.hosted.RestrictHeapAccessGuestValue;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAnnotationAccess;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/// Enforces the caller contract for unified logging scopes before inlining can erase a call.
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
@AutomaticallyRegisteredImageSingleton
public final class LogMessageScopeChecker {
    /// Parsing runs in parallel, while deterministic diagnostics are emitted before compilation.
    private final Set<String> violations = new ConcurrentSkipListSet<>();

    LogMessageScopeChecker() {
    }

    /// Checks message-opening calls in `graph`, retaining the original caller after any inlining.
    public static void checkAfterParsing(StructuredGraph graph) {
        if (graph == null) {
            return;
        }
        for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod target = call.targetMethod();
            if (target.getName().equals("message") && target.getDeclaringClass().toClassName().equals("com.oracle.svm.core.logging.LogTagSet") &&
                            target.getSignature().getParameterCount(false) == 0) {
                ResolvedJavaMethod caller = call.invoke().stateAfter().getMethod();
                for (String violation : callerViolations(caller)) {
                    String position = call.getNodeSourcePosition() == null ? "" : System.lineSeparator() + "  at " + call.getNodeSourcePosition();
                    ImageSingletons.lookup(LogMessageScopeChecker.class).violations.add(caller.format("%H.%n(%p):%r") + ": " + violation + position);
                }
            }
        }
    }

    /// Returns violations of the message-opening contract declared by `caller`.
    public static List<String> callerViolations(ResolvedJavaMethod caller) {
        List<String> result = new ArrayList<>();
        if (!caller.getName().startsWith("log") || caller.getSignature().getReturnKind() != JavaKind.Void) {
            result.add("LogTagSet.message() callers must be void log* methods.");
        }
        AnnotationValue neverInline = GuestAnnotationAccess.getAnnotationValue(caller, NeverInline.class);
        if (neverInline == null || !LogTagSet.LOG_METHOD_REASON.equals(neverInline.getString("value"))) {
            result.add("LogTagSet.message() callers must declare @NeverInline(LogTagSet.LOG_METHOD_REASON).");
        }
        UninterruptibleGuestValue uninterruptible = UninterruptibleAnnotationUtils.getAnnotation(caller);
        if (uninterruptible == null || !LogTagSet.LOG_METHOD_REASON.equals(uninterruptible.reason()) || uninterruptible.mayBeInlined() ||
                        !uninterruptible.calleeMustBe() || uninterruptible.callerMustBe()) {
            result.add("LogTagSet.message() callers must declare @Uninterruptible(reason = LogTagSet.LOG_METHOD_REASON) with default options.");
        }
        RestrictHeapAccessGuestValue restriction = RestrictHeapAccessGuestValue.get(caller);
        if (restriction == null || restriction.access() != Access.NO_ALLOCATION || !LogTagSet.LOG_METHOD_REASON.equals(restriction.reason())) {
            result.add("LogTagSet.message() callers must declare @RestrictHeapAccess(access = NO_ALLOCATION, reason = LogTagSet.LOG_METHOD_REASON).");
        }
        return result;
    }

    /// Reports all violations collected during parsing, independently of the analysis engine.
    public static void checkBeforeCompilation() {
        Set<String> violations = ImageSingletons.lookup(LogMessageScopeChecker.class).violations;
        if (!violations.isEmpty()) {
            throw VMError.shouldNotReachHere("Found %d violations of the LogTagSet.message() caller contract:%n- %s", violations.size(),
                            String.join(System.lineSeparator() + "- ", violations));
        }
    }
}
