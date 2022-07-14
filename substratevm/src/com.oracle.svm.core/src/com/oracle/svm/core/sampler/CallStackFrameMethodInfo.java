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
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CallStackFrameMethodInfo {
    private static final int INITIAL_METHOD_ID = -1;

    private static final String ENTER_SAFEPOINT_METHOD_NAME = "Safepoint.enterSlowPathSafepointCheck";

    private static final String ENTER_SAFEPOINT_FROM_NATIVE_METHOD_NAME = "Safepoint.enterSlowPathTransitionFromNativeToNewStatus";

    private static final String ENTER_SAFEPOINT_CHECK_OBJECT_METHOD_NAME = "com.oracle.svm.enterprise.core.ae.enterSlowPathSafepointCheckObject";

    private final Map<Integer, String> sampledMethods = new HashMap<>();

    private int enterSafepointCheckId = INITIAL_METHOD_ID;

    private int enterSafepointFromNativeId = INITIAL_METHOD_ID;

    private int enterSafepointCheckObject = INITIAL_METHOD_ID;

    public void addMethodInfo(ResolvedJavaMethod method, int methodId) {
        sampledMethods.put(methodId, method.format("%H.%n"));
        // TODO BS make this contains be equals
        if (enterSafepointCheckId == INITIAL_METHOD_ID && method.format("%H.%n").contains(ENTER_SAFEPOINT_METHOD_NAME)) {
            enterSafepointCheckId = methodId;
        }
        if (enterSafepointFromNativeId == INITIAL_METHOD_ID && method.format("%H.%n").contains(ENTER_SAFEPOINT_FROM_NATIVE_METHOD_NAME)) {
            enterSafepointFromNativeId = methodId;
        }
        if (enterSafepointCheckObject == INITIAL_METHOD_ID && method.format("%H.%n").contains(ENTER_SAFEPOINT_CHECK_OBJECT_METHOD_NAME)) {
            enterSafepointCheckObject = methodId;
        }
    }

    public String methodFor(int methodId) {
        return sampledMethods.get(methodId);
    }

    public boolean isSamplingCodeEntry(int methodId) {
        return enterSafepointCheckId == methodId || enterSafepointFromNativeId == methodId || enterSafepointCheckObject == methodId;
    }

    public void setEnterSamplingCodeMethodId(int enterSafepointCheckId, int enterSafepointFromNativeId, int enterSafepointCheckObject) {
        this.enterSafepointCheckId = enterSafepointCheckId;
        this.enterSafepointFromNativeId = enterSafepointFromNativeId;
        this.enterSafepointCheckObject = enterSafepointCheckObject;
    }

    public int getEnterSamplingCodeMethodId() {
        return enterSafepointCheckId;
    }
}
