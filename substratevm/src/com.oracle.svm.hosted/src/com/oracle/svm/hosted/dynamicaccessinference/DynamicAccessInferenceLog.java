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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;

import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DynamicAccessInferenceLog {

    public static DynamicAccessInferenceLog singleton() {
        return ImageSingletons.lookup(DynamicAccessInferenceLog.class);
    }

    private Queue<LogEntry> entries = new ConcurrentLinkedQueue<>();
    private boolean isSealed = false;

    public void logConstant(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments, Object value) {
        logEntry(b, reason, () -> new ConstantLogEntry(b, targetMethod, targetReceiver, targetArguments, value));
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
            assert !isSealed : "Logging attempt when log is already sealed";
            LogEntry entry = entrySupplier.get();
            b.add(ReachabilityRegistrationNode.create(() -> entries.add(entry), reason));
        }
    }

    Queue<LogEntry> getEntries() {
        return entries;
    }

    public boolean isSealed() {
        return isSealed;
    }

    public void seal() {
        isSealed = true;
        entries = null;
    }

    abstract static class LogEntry {

        final Pair<ResolvedJavaMethod, Integer> callLocation;
        final List<StackTraceElement> callStack;
        final ResolvedJavaMethod targetMethod;
        final Object targetReceiver;
        final Object[] targetArguments;

        LogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments) {
            assert targetMethod.hasReceiver() == (targetReceiver != null) : "Inferred receiver does not match with target method signature";
            assert targetMethod.getSignature().getParameterCount(false) == targetArguments.length : "Inferred arguments do not match with target method signature";
            this.callLocation = Pair.create(b.getMethod(), b.bci());
            this.callStack = b.getInliningCallStack(true);
            this.targetMethod = targetMethod;
            this.targetReceiver = targetReceiver;
            this.targetArguments = targetArguments;
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
            try (JsonBuilder.ArrayBuilder foldContextBuilder = builder.append("foldContext").array()) {
                for (StackTraceElement element : callStack) {
                    foldContextBuilder.append(element);
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
    }

    static class ConstantLogEntry extends LogEntry {

        final Object value;

        ConstantLogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetCaller, Object[] targetArguments, Object value) {
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

    static class ExceptionLogEntry extends LogEntry {

        final Class<? extends Throwable> exceptionClass;

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

    static class RegistrationLogEntry extends LogEntry {

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

    private static final Object IGNORED_ARGUMENT_MARKER = new IgnoredArgumentValue();

    public static Object ignoreArgument() {
        return IGNORED_ARGUMENT_MARKER;
    }

    private static final class IgnoredArgumentValue {

        @Override
        public String toString() {
            return "<ignored>";
        }
    }
}
