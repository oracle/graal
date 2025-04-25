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
package com.oracle.svm.hosted.webimage.code;

import static com.oracle.svm.hosted.webimage.WebImageGenerator.COMPILE_QUEUE_SCOPE_NAME;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.COMPILED_METHODS;
import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.COMPILED_TYPES;

import java.util.Collections;
import java.util.Set;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.webimage.api.JSObject;

import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.logging.LoggableMetric;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.logging.LoggerScope;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MetricKey;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class WebImageCompileQueue extends CompileQueue {

    protected final Providers providers;

    public WebImageCompileQueue(FeatureHandler featureHandler, HostedUniverse hUniverse, RuntimeConfiguration runtimeConfiguration, DebugContext debug) {
        super(debug, featureHandler, hUniverse, runtimeConfiguration, false, Collections.emptyList());
        this.providers = runtimeConfiguration.getProviders();
    }

    public static void saveCompilationCounters(LoggerScope scope, @SuppressWarnings("unused") UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
        LoggerContext.currentContext().saveCounters(scope, COMPILED_TYPES, COMPILED_METHODS);
    }

    @Override
    protected boolean canBeUsedForInlining(Invoke invoke) {
        HostedMethod method = (HostedMethod) invoke.callTarget().targetMethod();
        if (method.isConstructor() && JSObject.class.isAssignableFrom(method.getDeclaringClass().getJavaClass())) {
            // The constructor of a JavaScript class must be not be inlined after the graph is
            // parsed.
            return false;
        }
        return super.canBeUsedForInlining(invoke);
    }

    @Override
    protected CompileTask createCompileTask(HostedMethod method, CompileReason reason) {
        return new WebImageCompileTask(method, reason);
    }

    @Override
    @SuppressWarnings("try")
    protected void compileAll() throws InterruptedException {
        try (LoggerScope loggerScope = LoggerContext.currentContext().scope(COMPILE_QUEUE_SCOPE_NAME, WebImageCompileQueue::saveCompilationCounters)) {
            super.compileAll();

            for (CompileTask task : compilations.values()) {
                LoggerContext.currentContext().mergeSavedCounters(((WebImageCompileTask) task).getMetrics());
            }
            Set<HostedMethod> methods = getCompilations().keySet();
            LoggerContext.counter(COMPILED_TYPES).add(methods.stream().map(ResolvedJavaMethod::getDeclaringClass).distinct().count());
            LoggerContext.counter(COMPILED_METHODS).add(methods.size());
        }
    }

    @Override
    protected Suites createRegularSuites() {
        return GraalConfiguration.hostedInstance().createSuites(HostedOptionValues.singleton(), true, null);
    }

    @Override
    protected Suites createDeoptTargetSuites() {
        return null;
    }

    @Override
    protected LIRSuites createLIRSuites() {
        return null;
    }

    @Override
    protected LIRSuites createDeoptTargetLIRSuites() {
        return null;
    }

    @Override
    protected void modifyRegularSuites(Suites suites) {
        // In Web Image, no suite modifications are necessary since it uses its own phase suites.
    }

    @Override
    protected void removeDeoptTargetOptimizations(Suites suites) {
        // In Web Image, no suite modifications are necessary since it uses its own phase suites.
    }

    @Override
    protected void removeDeoptTargetOptimizations(LIRSuites lirSuites) {
        // In Web Image, no suite modifications are necessary since it uses its own phase suites.
    }

    /**
     * WebImage-specific compile task that collects compilation metrics.
     */
    public class WebImageCompileTask extends CompileTask implements CompletionExecutor.DebugContextRunnable {
        public static final String COMPILE_TASK_SCOPE_NAME = "WebImage-Compilation";

        /**
         * Metrics that were logged in this compilation task.
         */
        public UnmodifiableEconomicMap<MetricKey, Number> metrics;

        public WebImageCompileTask(HostedMethod method, CompileReason reason) {
            super(method, reason);
        }

        @Override
        public DebugContext.Description getDescription() {
            return new DebugContext.Description(method, method.getName());
        }

        @Override
        @SuppressWarnings("try")
        public void run(DebugContext debug) {
            try (LoggerContext loggerContext = new LoggerContext.Builder(HostedOptionValues.singleton()).stream(WebImageOptions.compilerPrinter(HostedOptionValues.singleton())).deleteMetricFile(
                            false).onCloseHandler(this::saveHighTierCounters).build()) {
                try (LoggerScope scope = loggerContext.scope(COMPILE_TASK_SCOPE_NAME);
                                DebugContext.Scope s = debug.scope("(compilation)", method.compilationInfo.getCompilationGraph(), method, this)) {
                    super.run(debug);
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
            }
        }

        @SuppressWarnings("unused")
        private void saveHighTierCounters(LoggerScope scope, UnmodifiableEconomicMap<MetricKey, LoggableMetric> scopeMetrics) {
            this.metrics = scope.getExtractedMetrics();
        }

        public UnmodifiableEconomicMap<MetricKey, Number> getMetrics() {
            return metrics;
        }
    }

    /**
     * Temporary compile reason because Web Image always compiles all methods in the hosted
     * universe.
     */
    public static class WebImageReason extends CompileReason {
        public WebImageReason() {
            super(null);
        }

        @Override
        public String toString() {
            return "web-image-entry-point";
        }
    }
}
