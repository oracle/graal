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
package com.oracle.svm.hosted.snippets;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.phases.IntrinsifyMethodHandlesInvocationPlugin;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class IntrinsificationPluginRegistry {

    public static class CallSiteDescriptor {
        private final AnalysisMethod[] caller;
        private final int[] bci;
        private final int length;

        public CallSiteDescriptor(List<Pair<ResolvedJavaMethod, Integer>> callingContext) {
            this.length = callingContext.size();
            this.caller = new AnalysisMethod[length];
            this.bci = new int[length];
            int i = 0;
            for (Pair<ResolvedJavaMethod, Integer> pair : callingContext) {
                this.caller[i] = toAnalysisMethod(pair.getLeft());
                this.bci[i] = pair.getRight();
                i++;
            }
        }

        public AnalysisMethod[] getCaller() {
            return caller;
        }

        public int[] getBci() {
            return bci;
        }

        public int getLength() {
            return length;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CallSiteDescriptor) {
                CallSiteDescriptor other = (CallSiteDescriptor) obj;
                return Arrays.equals(this.bci, other.bci) && Arrays.equals(this.caller, other.caller);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(caller) ^ java.util.Arrays.hashCode(bci);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < caller.length; i++) {
                sb.append(caller[i].format("%h.%n(%p)")).append("@").append(bci[i]).append(System.lineSeparator());
            }
            return sb.toString();
        }

        private static AnalysisMethod toAnalysisMethod(ResolvedJavaMethod method) {
            if (method instanceof HostedMethod) {
                return ((HostedMethod) method).wrapped;
            } else {
                VMError.guarantee(method instanceof AnalysisMethod);
                return (AnalysisMethod) method;
            }
        }
    }

    private static final Object NULL_MARKER = new Object();
    /**
     * Contains all the elements intrinsified during analysis. Only these elements will be
     * intrinsified during compilation. We cannot intrinsify an element during compilation if it was
     * not intrinsified during analysis since it can lead to compiling code that was not seen during
     * analysis.
     */
    private final ConcurrentHashMap<CallSiteDescriptor, Object> globalAnalysisElements = new ConcurrentHashMap<>();
    public final Set<AnalysisMethod> methodsWithIntrinsification = ConcurrentHashMap.newKeySet();
    public final ThreadLocal<ConcurrentHashMap<CallSiteDescriptor, Object>> threadLocalRegistry = new ThreadLocal<>();

    public AutoCloseable startThreadLocalReflectionRegistry() {
        return new AutoCloseable() {
            {
                ImageSingletons.lookup(ReflectionPlugins.ReflectionPluginRegistry.class).threadLocalRegistry.set(new ConcurrentHashMap<>());
            }

            @Override
            public void close() {
                ImageSingletons.lookup(ReflectionPlugins.ReflectionPluginRegistry.class).threadLocalRegistry.remove();
            }
        };
    }

    public AutoCloseable startThreadLocalIntrinsificationRegistry() {
        return new AutoCloseable() {
            {
                ImageSingletons.lookup(IntrinsifyMethodHandlesInvocationPlugin.IntrinsificationRegistry.class).threadLocalRegistry.set(new ConcurrentHashMap<>());
            }

            @Override
            public void close() {
                ImageSingletons.lookup(IntrinsifyMethodHandlesInvocationPlugin.IntrinsificationRegistry.class).threadLocalRegistry.remove();
            }
        };
    }

    private ConcurrentHashMap<CallSiteDescriptor, Object> getAnalysisElements() {
        return threadLocalRegistry.get() == null ? globalAnalysisElements : threadLocalRegistry.get();
    }

    public void add(List<Pair<ResolvedJavaMethod, Integer>> callingContext, Object element) {
        Object nonNullElement = element != null ? element : NULL_MARKER;
        Object previous = getAnalysisElements().putIfAbsent(new CallSiteDescriptor(callingContext), nonNullElement);
        VMError.guarantee(previous == null || previous == nonNullElement, "Newly intrinsified element is different than the previous");

        /* save information that method has intrinsification */
        methodsWithIntrinsification.add((AnalysisMethod) callingContext.get(0).getLeft());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(List<Pair<ResolvedJavaMethod, Integer>> callingContext) {
        Object nonNullElement = getAnalysisElements().get(new CallSiteDescriptor(callingContext));
        return nonNullElement != NULL_MARKER ? (T) nonNullElement : null;
    }

    public boolean hasIntrinsifications(AnalysisMethod method) {
        return methodsWithIntrinsification.contains(method);
    }
}
