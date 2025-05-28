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
package com.oracle.svm.hosted.strictconstantanalysis;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.util.LogUtils;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutomaticallyRegisteredFeature
public class InferredDynamicAccessLoggingFeature implements InternalFeature {

    static class Options {
        @Option(help = "Specify the .json log file location for inferred dynamic accesses.")//
        static final HostedOptionKey<String> InferredDynamicAccessLog = new HostedOptionKey<>(null);
    }

    private static Queue<LogEntry> log = new ConcurrentLinkedQueue<>();

    public static void logConstant(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments, Object value) {
        logEntry(b, reason, () -> new ConstantLogEntry(b, targetMethod, targetReceiver, targetArguments, value));
    }

    public static void logException(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments,
                    Class<? extends Throwable> exceptionClass) {
        logEntry(b, reason, () -> new ExceptionLogEntry(b, targetMethod, targetReceiver, targetArguments, exceptionClass));
    }

    public static void logRegistration(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments) {
        logEntry(b, reason, () -> new RegistreationLogEntry(b, targetMethod, targetReceiver, targetArguments));
    }

    private static void logEntry(GraphBuilderContext b, ParsingReason reason, Supplier<LogEntry> entrySupplier) {
        if (reason.duringAnalysis() && reason != ParsingReason.JITCompilation && isEnabled()) {
            VMError.guarantee(log != null, "Logging attempt when log has been sealed");
            LogEntry entry = entrySupplier.get();
            b.add(ReachabilityRegistrationNode.create(() -> log.add(entry), reason));
        }
    }

    private static boolean isEnabled() {
        return Options.InferredDynamicAccessLog.getValue() != null || shouldWarnForNonStrictFolding();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isEnabled();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        String logLocation = Options.InferredDynamicAccessLog.getValue();
        if (logLocation != null) {
            dump(logLocation);
        }
        if (shouldWarnForNonStrictFolding()) {
            warnForNonStrictFolding();
        }
        /* The log is no longer used after this, so we can clean it up. */
        cleanLog();
    }

    private static void cleanLog() {
        log = null;
    }

    private static void dump(String location) {
        try (JsonWriter out = new JsonPrettyWriter(Path.of(location))) {
            try (JsonBuilder.ArrayBuilder arrayBuilder = out.arrayBuilder()) {
                for (LogEntry entry : log) {
                    try (JsonBuilder.ObjectBuilder objectBuilder = arrayBuilder.nextEntry().object()) {
                        entry.toJson(objectBuilder);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean shouldWarnForNonStrictFolding() {
        return StrictConstantAnalysisFeature.Options.StrictConstantAnalysis.getValue() == StrictConstantAnalysisFeature.Options.Mode.Warn;
    }

    private static void warnForNonStrictFolding() {
        ConstantExpressionRegistry registry = ImageSingletons.lookup(ConstantExpressionRegistry.class);
        List<LogEntry> unsafeFoldingEntries = log.stream().filter(entry -> !registryContainsConstantOperands(registry, entry)).toList();
        if (!unsafeFoldingEntries.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("The following method invocations have been inferred outside of the strict constant expression mode:").append(System.lineSeparator());
            for (int i = 0; i < unsafeFoldingEntries.size(); i++) {
                sb.append((i + 1)).append(". ").append(unsafeFoldingEntries.get(i));
                if (i != unsafeFoldingEntries.size() - 1) {
                    sb.append(System.lineSeparator());
                }
            }
            LogUtils.warning(sb.toString());
        }
    }

    private static boolean registryContainsConstantOperands(ConstantExpressionRegistry registry, LogEntry entry) {
        Pair<ResolvedJavaMethod, Integer> callLocation = entry.callLocation;
        if (entry.targetMethod.hasReceiver()) {
            Optional<Object> receiver = registry.getReceiver(callLocation.getLeft(), callLocation.getRight(), entry.targetMethod);
            if (entry.targetReceiver != ignoredArgument() && receiver.isEmpty()) {
                return false;
            }
        }
        for (int i = 0; i < entry.targetArguments.length; i++) {
            Optional<Object> argument = registry.getArgument(callLocation.getLeft(), callLocation.getRight(), entry.targetMethod, i);
            if (entry.targetArguments[i] != ignoredArgument() && argument.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static final Object IGNORED_ARGUMENT_MARKER = new IgnoredArgumentValue();

    public static Object ignoredArgument() {
        return IGNORED_ARGUMENT_MARKER;
    }

    private static final class IgnoredArgumentValue {

        @Override
        public String toString() {
            return "<ignored>";
        }
    }

    private abstract static class LogEntry {

        private final Pair<ResolvedJavaMethod, Integer> callLocation;
        private final List<StackTraceElement> callStack;
        private final ResolvedJavaMethod targetMethod;
        private final Object targetReceiver;
        private final Object[] targetArguments;

        LogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetReceiver, Object[] targetArguments) {
            VMError.guarantee(targetMethod.hasReceiver() == (targetReceiver != null), "Inferred receiver does not match with target method signature");
            VMError.guarantee(targetMethod.getSignature().getParameterCount(false) == targetArguments.length, "Inferred arguments do not match with target method signature");
            this.callLocation = Pair.create(b.getMethod(), b.bci());
            this.callStack = b.getCallStack();
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

    private static class ConstantLogEntry extends LogEntry {

        private final Object value;

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

    private static class RegistreationLogEntry extends LogEntry {

        RegistreationLogEntry(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object targetCaller, Object[] targetArguments) {
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
}
