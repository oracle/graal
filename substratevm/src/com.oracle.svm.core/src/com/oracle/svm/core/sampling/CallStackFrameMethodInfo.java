/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.sampling;

import java.util.HashMap;
import java.util.Map;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CallStackFrameMethodInfo {
    private final Map<Integer, String> sampledMethods = new HashMap<>();

    private int enterSamplingCodeMethodId = ENTER_SAMPLING_CODE_METHOD_ID_INTIAL;
    private static final int ENTER_SAMPLING_CODE_METHOD_ID_INTIAL = -1;
    private static final String enterSamplingCodeMethod = "Safepoint.enterSlowPathSafepointCheck";

    public void addMethodInfo(ResolvedJavaMethod method, int methodId) {
        sampledMethods.put(methodId, method.format("%H.%n"));
        if (enterSamplingCodeMethodId == ENTER_SAMPLING_CODE_METHOD_ID_INTIAL && method.format("%H.%n").contains(enterSamplingCodeMethod)) {
            enterSamplingCodeMethodId = methodId;
        }
    }

    public String methodFor(int methodId) {
        return sampledMethods.get(methodId);
    }

    public boolean isSamplingCodeEntry(int methodId) {
        return enterSamplingCodeMethodId == methodId;
    }

    public void setEnterSamplingCodeMethodId(int enterSamplingCodeMethodId) {
        this.enterSamplingCodeMethodId = enterSamplingCodeMethodId;
    }

    public int getEnterSamplingCodeMethodId() {
        return enterSamplingCodeMethodId;
    }
}
