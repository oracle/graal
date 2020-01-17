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

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class IntrinsificationPluginRegistry {

    static final class CallSiteDescriptor {
        private final AnalysisMethod method;
        private final int bci;

        private CallSiteDescriptor(ResolvedJavaMethod method, int bci) {
            this.method = toAnalysisMethod(method);
            this.bci = bci;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CallSiteDescriptor) {
                CallSiteDescriptor other = (CallSiteDescriptor) obj;
                return other.bci == this.bci && other.method.equals(this.method);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return method.hashCode() ^ bci;
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
    private final ConcurrentHashMap<CallSiteDescriptor, Object> analysisElements = new ConcurrentHashMap<>();

    public void add(ResolvedJavaMethod method, int bci, Object element) {
        Object nonNullElement = element != null ? element : NULL_MARKER;
        Object previous = analysisElements.put(new CallSiteDescriptor(method, bci), nonNullElement);

        /*
         * New elements can only be added when the intrinsification is executed during the analysis.
         * If an intrinsified element was already registered that's an error.
         */
        VMError.guarantee(previous == null, "Detected previously intrinsified element");
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ResolvedJavaMethod method, int bci) {
        Object nonNullElement = analysisElements.get(new CallSiteDescriptor(method, bci));
        return nonNullElement != NULL_MARKER ? (T) nonNullElement : null;
    }
}
