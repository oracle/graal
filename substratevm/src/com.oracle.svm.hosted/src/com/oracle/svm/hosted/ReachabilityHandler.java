/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

public abstract class ReachabilityHandler {

    public abstract void registerMethodOverrideReachabilityHandler(BeforeAnalysisAccessImpl access, BiConsumer<DuringAnalysisAccess, Executable> callback, Executable baseMethod);

    public abstract void registerSubtypeReachabilityHandler(BeforeAnalysisAccessImpl access, BiConsumer<DuringAnalysisAccess, Class<?>> callback, Class<?> baseClass);

    public final void registerClassInitializerReachabilityHandler(BeforeAnalysisAccessImpl access, Consumer<DuringAnalysisAccess> callback, Class<?> clazz) {
        /*
         * In our current static analysis implementations, there is no difference between the
         * reachability of a class and the reachability of its class initializer.
         */
        registerReachabilityHandler(access, callback, new Object[]{clazz});
    }

    public abstract void registerReachabilityHandler(BeforeAnalysisAccessImpl access, Consumer<DuringAnalysisAccess> callback, Object[] triggers);
}
