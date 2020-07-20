/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.restrict;

import static com.oracle.svm.configure.trace.LazyValueUtils.lazyNull;
import static com.oracle.svm.jvmtiagentbase.Support.fromJniString;

import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class ResourceAccessVerifier extends AbstractAccessVerifier {
    private final ResourceConfiguration configuration;

    public ResourceAccessVerifier(ResourceConfiguration configuration, AccessAdvisor advisor) {
        super(advisor);
        this.configuration = configuration;
    }

    public boolean verifyGetResources(JNIEnvironment env, JNIObjectHandle name, JNIObjectHandle callerClass) {
        return verifyGetResources0(env, name, callerClass);
    }

    public boolean verifyGetSystemResources(JNIEnvironment env, JNIObjectHandle name, JNIObjectHandle callerClass) {
        return verifyGetResources0(env, name, callerClass);
    }

    private boolean verifyGetResources0(JNIEnvironment env, JNIObjectHandle name, @SuppressWarnings("unused") JNIObjectHandle callerClass) {
        // NOTE: like BreakpointInterceptor, we currently don't exclude caller classes.
        String resource = fromJniString(env, name);
        return configuration.anyResourceMatches(resource);
    }

    public boolean verifyGetBundle(JNIEnvironment env, JNIObjectHandle baseName, @SuppressWarnings("unused") JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(lazyNull(), lazyClassNameOrNull(env, callerClass))) {
            return true;
        }
        String bundleName = fromJniString(env, baseName);
        return configuration.anyBundleMatches(bundleName);
    }
}
