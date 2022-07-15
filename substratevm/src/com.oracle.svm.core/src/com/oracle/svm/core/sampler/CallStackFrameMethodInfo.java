/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.sampler;

import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.thread.Safepoint;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CallStackFrameMethodInfo {

    protected static final int INITIAL_METHOD_ID = -1;

    private final Map<Integer, String> sampledMethods = new HashMap<>();
    private int enterSafepointCheckId = INITIAL_METHOD_ID;
    private int enterSafepointFromNativeId = INITIAL_METHOD_ID;

    public void addMethodInfo(ResolvedJavaMethod method, int methodId) {
        String formattedMethod = formatted(method);
        sampledMethods.put(methodId, formattedMethod);
        if (enterSafepointCheckId == INITIAL_METHOD_ID && formattedMethod.equals(formatted(Safepoint.ENTER_SLOW_PATH_SAFEPOINT_CHECK))) {
            enterSafepointCheckId = methodId;
        }
        if (enterSafepointFromNativeId == INITIAL_METHOD_ID && formattedMethod.equals(formatted(Safepoint.ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS))) {
            enterSafepointFromNativeId = methodId;
        }
    }

    protected static String formatted(ResolvedJavaMethod method) {
        return method.format("%H.%n");
    }

    protected static String formatted(SnippetRuntime.SubstrateForeignCallDescriptor descriptor) {
        return String.format("%s.%s",
                        descriptor.getDeclaringClass().getCanonicalName(),
                        descriptor.getName());
    }

    public String methodFor(int methodId) {
        return sampledMethods.get(methodId);
    }

    public boolean isSamplingCodeEntry(int methodId) {
        return enterSafepointCheckId == methodId || enterSafepointFromNativeId == methodId;
    }

    public void setEnterSamplingCodeMethodId(int i) {
        enterSafepointCheckId = i;
        enterSafepointFromNativeId = i;
    }
}
