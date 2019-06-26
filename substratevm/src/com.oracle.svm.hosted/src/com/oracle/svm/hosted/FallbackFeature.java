/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.FallbackExecutor;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class FallbackFeature implements Feature {
    private static final String ABORT_MSG_PREFIX = "Aborting stand-alone image build";

    private final List<ReflectionInvocationCheck> reflectionInvocationChecks = new ArrayList<>();

    private final List<String> reflectionCalls = new ArrayList<>();
    private final List<String> resourceCalls = new ArrayList<>();
    private final List<String> jniCalls = new ArrayList<>();
    private final List<String> proxyCalls = new ArrayList<>();

    private static class AutoProxyInvoke {
        private final ResolvedJavaMethod method;
        private final int bci;

        AutoProxyInvoke(ResolvedJavaMethod method, int bci) {
            this.method = method;
            this.bci = bci;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AutoProxyInvoke that = (AutoProxyInvoke) o;
            return bci == that.bci && Objects.equals(method, that.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, bci);
        }
    }

    private final Set<AutoProxyInvoke> autoProxyInvokes = new HashSet<>();

    public void addAutoProxyInvoke(ResolvedJavaMethod method, int bci) {
        autoProxyInvokes.add(new AutoProxyInvoke(method, bci));
    }

    private boolean containsAutoProxyInvoke(ResolvedJavaMethod method, int bci) {
        return autoProxyInvokes.contains(new AutoProxyInvoke(method, bci));
    }

    private interface InvokeChecker {
        void check(ReflectionInvocationCheck check, InvokeTypeFlow invoke);
    }

    private static class ReflectionInvocationCheck {
        private final Method reflectionMethod;
        private final InvokeChecker checker;
        private AnalysisMethod trackedReflectionMethod;

        ReflectionInvocationCheck(Method reflectionMethod, InvokeChecker checker) {
            this.reflectionMethod = reflectionMethod;
            this.checker = checker;
            trackedReflectionMethod = null;
        }

        void trackMethod(AnalysisMetaAccess metaAccess) {
            trackedReflectionMethod = metaAccess.lookupJavaMethod(reflectionMethod);
            trackedReflectionMethod.startTrackInvocations();
        }

        void apply(InvokeTypeFlow invoke) {
            ClassLoader classLoader = getCallerMethod(invoke).getDeclaringClass().getJavaClass().getClassLoader();
            if (classLoader instanceof NativeImageClassLoader) {
                checker.check(this, invoke);
            }
        }

        String locationString(InvokeTypeFlow invoke) {
            AnalysisMethod caller = getCallerMethod(invoke);
            String callerLocation = caller.asStackTraceElement(invoke.getLocation().getBci()).toString();
            return trackedReflectionMethod.format("%H.%n") + " invoked at " + callerLocation;
        }
    }

    private static AnalysisMethod getCallerMethod(InvokeTypeFlow invoke) {
        return (AnalysisMethod) invoke.getSource().graph().method();
    }

    private void addCheck(Method reflectionMethod, InvokeChecker checker) {
        reflectionInvocationChecks.add(new ReflectionInvocationCheck(reflectionMethod, checker));
    }

    public FallbackFeature() {
        try {
            addCheck(Class.class.getMethod("forName", String.class), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("forName", String.class, boolean.class, ClassLoader.class), this::collectReflectionInvokes);

            addCheck(Class.class.getMethod("newInstance"), this::collectReflectionInvokes);

            addCheck(Class.class.getMethod("getMethod", String.class, Class[].class), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getDeclaredMethod", String.class, Class[].class), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getMethods"), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getDeclaredMethods"), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getEnclosingMethod"), this::collectReflectionInvokes);

            addCheck(Class.class.getMethod("getConstructor", Class[].class), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getDeclaredConstructor", Class[].class), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getConstructors"), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getDeclaredConstructors"), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getEnclosingConstructor"), this::collectReflectionInvokes);

            addCheck(Class.class.getMethod("getField", String.class), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getFields"), this::collectReflectionInvokes);
            addCheck(Class.class.getMethod("getDeclaredFields"), this::collectReflectionInvokes);

            addCheck(ClassLoader.class.getMethod("loadClass", String.class), this::collectReflectionInvokes);
            addCheck(ClassLoader.class.getMethod("getResource", String.class), this::collectResourceInvokes);
            addCheck(ClassLoader.class.getMethod("getSystemResource", String.class), this::collectResourceInvokes);
            addCheck(ClassLoader.class.getMethod("getResources", String.class), this::collectResourceInvokes);
            addCheck(ClassLoader.class.getMethod("getSystemResources", String.class), this::collectResourceInvokes);

            addCheck(Proxy.class.getMethod("getProxyClass", ClassLoader.class, Class[].class), this::collectProxyInvokes);
            addCheck(Proxy.class.getMethod("newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class), this::collectProxyInvokes);

            addCheck(System.class.getMethod("loadLibrary", String.class), this::collectJNIInvokes);
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere("Registering ReflectionInvocationChecks failed", e);
        }
    }

    private void collectReflectionInvokes(ReflectionInvocationCheck check, InvokeTypeFlow invoke) {
        reflectionCalls.add("Reflection method " + check.locationString(invoke));
    }

    private void collectResourceInvokes(ReflectionInvocationCheck check, InvokeTypeFlow invoke) {
        resourceCalls.add("Resource access method " + check.locationString(invoke));
    }

    private void collectJNIInvokes(ReflectionInvocationCheck check, InvokeTypeFlow invoke) {
        jniCalls.add("System method " + check.locationString(invoke));
    }

    private void collectProxyInvokes(ReflectionInvocationCheck check, InvokeTypeFlow invoke) {
        if (!containsAutoProxyInvoke(getCallerMethod(invoke), invoke.getLocation().getBci())) {
            proxyCalls.add("Dynamic proxy method " + check.locationString(invoke));
        }
    }

    static FallbackImageRequest reportFallback(String message) {
        return reportFallback(message, null);
    }

    static FallbackImageRequest reportFallback(String message, Throwable cause) {
        FallbackImageRequest request;
        if (cause instanceof UserError.UserException) {
            List<String> messages = new ArrayList<>();
            if (message != null) {
                messages.add(message);
            }
            ((UserError.UserException) cause).getMessages().forEach(messages::add);
            request = new FallbackImageRequest(messages);
            request.initCause(cause.getCause());
        } else {
            String fallbackMessage = ((message == null) && (cause != null)) ? cause.getMessage() : message;
            request = new FallbackImageRequest(fallbackMessage);
            request.initCause(cause);
        }
        throw request;
    }

    static UserError.UserException reportAsFallback(RuntimeException original) {
        if (SubstrateOptions.FallbackThreshold.getValue() == SubstrateOptions.NoFallback) {
            throw UserError.abort(original.getMessage(), original);
        }
        throw reportFallback(ABORT_MSG_PREFIX + ". " + original.getMessage(), original);
    }

    @SuppressWarnings("serial")
    public static final class FallbackImageRequest extends UserError.UserException {
        private FallbackImageRequest(String message) {
            super(message);
        }

        private FallbackImageRequest(Iterable<String> messages) {
            super(messages);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return FallbackExecutor.Options.FallbackExecutorMainClass.getValue() == null;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        if (SubstrateOptions.FallbackThreshold.getValue() == SubstrateOptions.ForceFallback) {
            reportFallback(ABORT_MSG_PREFIX + " due to native-image option --" + SubstrateOptions.OptionNameForceFallback);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        AnalysisMetaAccess metaAccess = access.getBigBang().getMetaAccess();
        for (ReflectionInvocationCheck check : reflectionInvocationChecks) {
            check.trackMethod(metaAccess);
        }
    }

    public FallbackImageRequest reflectionFallback = null;
    public FallbackImageRequest resourceFallback = null;
    public FallbackImageRequest jniFallback = null;
    public FallbackImageRequest proxyFallback = null;

    @Override
    public void afterAnalysis(AfterAnalysisAccess a) {
        if (SubstrateOptions.FallbackThreshold.getValue() == SubstrateOptions.NoFallback ||
                        NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue() ||
                        NativeImageOptions.AllowIncompleteClasspath.getValue() ||
                        SubstrateOptions.SharedLibrary.getValue()) {
            /*
             * Any of the above ensures we unconditionally allow stand-alone image to be generated.
             */
            return;
        }

        AfterAnalysisAccessImpl access = (AfterAnalysisAccessImpl) a;
        if (access.getBigBang().getUnsupportedFeatures().exist()) {
            /* If we detect use of unsupported features we trigger fallback image build. */
            String optionString = SubstrateOptionsParser.commandArgument(PointstoOptions.ReportUnsupportedFeaturesDuringAnalysis, "+");
            reportFallback(ABORT_MSG_PREFIX + " due to unsupported features (use " + optionString + " for report)");
        }

        for (ReflectionInvocationCheck check : reflectionInvocationChecks) {
            for (InvokeTypeFlow invoke : check.trackedReflectionMethod.getInvokeTypeFlows()) {
                check.apply(invoke);
            }
        }

        if (!reflectionCalls.isEmpty()) {
            reflectionCalls.add(ABORT_MSG_PREFIX + " due to reflection use without configuration.");
            reflectionFallback = new FallbackImageRequest(reflectionCalls);
        }
        if (!resourceCalls.isEmpty()) {
            resourceCalls.add(ABORT_MSG_PREFIX + " due to accessing resources without configuration.");
            resourceFallback = new FallbackImageRequest(resourceCalls);
        }
        if (!jniCalls.isEmpty()) {
            jniCalls.add(ABORT_MSG_PREFIX + " due to loading native libraries without configuration.");
            jniFallback = new FallbackImageRequest(jniCalls);
        }
        if (!proxyCalls.isEmpty()) {
            proxyCalls.add(ABORT_MSG_PREFIX + " due to dynamic proxy use without configuration.");
            proxyFallback = new FallbackImageRequest(proxyCalls);
        }
    }
}
