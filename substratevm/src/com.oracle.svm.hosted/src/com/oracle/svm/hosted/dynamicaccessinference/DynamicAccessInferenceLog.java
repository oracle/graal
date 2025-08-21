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
package com.oracle.svm.hosted.dynamicaccessinference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Log folding information on build-time inferred dynamic access invocations.
 */
public final class DynamicAccessInferenceLog {

    private static final Object IGNORED_ARGUMENT_MARKER = new IgnoredArgumentValue();

    private final Queue<LogEntry> entries = new ConcurrentLinkedQueue<>();

    public static DynamicAccessInferenceLog singleton() {
        return ImageSingletons.lookup(DynamicAccessInferenceLog.class);
    }

    public static DynamicAccessInferenceLog singletonOrNull() {
        return ImageSingletons.contains(DynamicAccessInferenceLog.class) ? ImageSingletons.lookup(DynamicAccessInferenceLog.class) : null;
    }

    public static Object ignoreArgument() {
        return IGNORED_ARGUMENT_MARKER;
    }

    public void logFolding(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments, Object value) {
        logEntry(b, reason, () -> new FoldingLogEntry(b, targetMethod, targetReceiver, targetArguments, value));
    }

    public void logException(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments,
                    Class<? extends Throwable> exceptionClass) {
        logEntry(b, reason, () -> new ExceptionLogEntry(b, targetMethod, targetReceiver, targetArguments, exceptionClass));
    }

    public void logRegistration(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments) {
        logEntry(b, reason, () -> new RegistrationLogEntry(b, targetMethod, targetReceiver, targetArguments));
    }

    private void logEntry(GraphBuilderContext b, ParsingReason reason, Supplier<LogEntry> entrySupplier) {
        if (reason.duringAnalysis() && reason != ParsingReason.JITCompilation) {
            LogEntry entry = entrySupplier.get();
            /*
             * Using a reachability node avoids reporting for unreachable invocations, as well as
             * invocations that were potentially folded during the exploration phase of
             * InlineBeforeAnalysis (but not in the final graph).
             */
            b.add(ReachabilityRegistrationNode.create(() -> entries.add(entry), reason));
        }
    }

    Iterable<LogEntry> getEntries() {
        return entries;
    }

    abstract static class LogEntry {

        private final BytecodePosition callLocation;
        private final List<StackTraceElement> callStack;
        private final ResolvedJavaMethod targetMethod;
        private final Object targetReceiver;
        private final Object[] targetArguments;

        LogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments) {
            assert targetMethod.hasReceiver() == (targetReceiver != null) : "Inferred receiver does not match with target method signature";
            assert targetMethod.getSignature().getParameterCount(false) == targetArguments.length : "Inferred arguments do not match with target method signature";
            this.callLocation = new BytecodePosition(null, b.getMethod(), b.bci());
            this.callStack = getCallStack(b);
            this.targetMethod = targetMethod;
            this.targetReceiver = targetReceiver;
            this.targetArguments = targetArguments;
        }

        private static List<StackTraceElement> getCallStack(GraphBuilderContext b) {
            BytecodePosition inliningContext = b.getInliningChain();
            List<StackTraceElement> callStack = new ArrayList<>();
            if (inliningContext == null || !inliningContext.getMethod().equals(b.getMethod()) || inliningContext.getBCI() != b.bci()) {
                callStack.add(b.getMethod().asStackTraceElement(b.bci()));
            }
            while (inliningContext != null) {
                callStack.add(inliningContext.getMethod().asStackTraceElement(inliningContext.getBCI()));
                inliningContext = inliningContext.getCaller();
            }
            return callStack;
        }

        @Override
        public String toString() {
            String targetArgumentsString = Stream.of(targetArguments)
                            .map(arg -> arg instanceof Object[] ? Arrays.toString((Object[]) arg) : Objects.toString(arg)).collect(Collectors.joining(", "));

            if (targetReceiver != null) {
                return String.format("Call to %s reachable in %s with receiver %s and arguments (%s) was inferred",
                                targetMethod.format("%H.%n(%p)"), callStack.getFirst(), targetReceiver, targetArgumentsString);
            } else {
                return String.format("Call to %s reachable in %s with arguments (%s) was inferred",
                                targetMethod.format("%H.%n(%p)"), callStack.getFirst(), targetArgumentsString);
            }
        }

        public void toJson(JsonBuilder.ObjectBuilder builder) throws IOException {
            try (JsonBuilder.ArrayBuilder inliningContextBuilder = builder.append("inliningContext").array()) {
                for (StackTraceElement element : callStack) {
                    inliningContextBuilder.append(element);
                }
            }
            builder.append("targetMethod", targetMethod.format("%H.%n(%p)"));
            if (targetReceiver != null) {
                builder.append("targetCaller", targetReceiver);
            }
            try (JsonBuilder.ArrayBuilder argsBuilder = builder.append("targetArguments").array()) {
                for (Object arg : targetArguments) {
                    argsBuilder.append(arg instanceof Object[] ? Arrays.toString((Object[]) arg) : Objects.toString(arg));
                }
            }
        }

        public BytecodePosition getCallLocation() {
            return callLocation;
        }

        public ResolvedJavaMethod getTargetMethod() {
            return targetMethod;
        }

        public Object getReceiver() {
            return targetReceiver;
        }

        public Object[] getArguments() {
            return targetArguments;
        }
    }

    private static class FoldingLogEntry extends LogEntry {

        private final Object value;

        FoldingLogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetCaller, Object[] targetArguments, Object value) {
            super(b, targetMethod, targetCaller, targetArguments);
            this.value = value;
        }

        @Override
        public String toString() {
            return super.toString() + " as the constant " + value;
        }

        @Override
        public void toJson(JsonBuilder.ObjectBuilder builder) throws IOException {
            super.toJson(builder);
            builder.append("constantValue", value);
        }
    }

    private static class ExceptionLogEntry extends LogEntry {

        private final Class<? extends Throwable> exceptionClass;

        ExceptionLogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetCaller, Object[] targetArguments, Class<? extends Throwable> exceptionClass) {
            super(b, targetMethod, targetCaller, targetArguments);
            this.exceptionClass = exceptionClass;
        }

        @Override
        public String toString() {
            return super.toString() + " to throw " + exceptionClass.getName();
        }

        @Override
        public void toJson(JsonBuilder.ObjectBuilder builder) throws IOException {
            super.toJson(builder);
            builder.append("exception", exceptionClass.getName());
        }
    }

    private static class RegistrationLogEntry extends LogEntry {

        RegistrationLogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetCaller, Object[] targetArguments) {
            super(b, targetMethod, targetCaller, targetArguments);
        }

        @Override
        public String toString() {
            return super.toString() + " and registered for runtime usage";
        }

        @Override
        public void toJson(JsonBuilder.ObjectBuilder builder) throws IOException {
            super.toJson(builder);
        }
    }

    private static final class IgnoredArgumentValue {

        @Override
        public String toString() {
            return "<ignored>";
        }
    }
}

final class DynamicAccessInferenceLogFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(DynamicAccessInferenceLog.class, new DynamicAccessInferenceLog());
    }
}
