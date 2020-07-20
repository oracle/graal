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
package com.oracle.svm.agent.restrict;

import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOrNull;

import org.graalvm.compiler.phases.common.LazyValue;

import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.LazyValueUtils;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

class AbstractAccessVerifier {
    protected final AccessAdvisor accessAdvisor;

    AbstractAccessVerifier(AccessAdvisor advisor) {
        this.accessAdvisor = advisor;
    }

    protected boolean shouldApproveWithoutChecks(JNIEnvironment env, JNIObjectHandle queriedClass, JNIObjectHandle callerClass) {
        return shouldApproveWithoutChecks(lazyClassNameOrNull(env, queriedClass), lazyClassNameOrNull(env, callerClass));
    }

    protected boolean shouldApproveWithoutChecks(LazyValue<String> queriedClassName, LazyValue<String> callerClassName) {
        return accessAdvisor.shouldIgnore(queriedClassName, callerClassName);
    }

    protected static LazyValue<String> lazyClassNameOrNull(JNIEnvironment env, JNIObjectHandle clazz) {
        return LazyValueUtils.lazyGet(() -> getClassNameOrNull(env, clazz));
    }
}
