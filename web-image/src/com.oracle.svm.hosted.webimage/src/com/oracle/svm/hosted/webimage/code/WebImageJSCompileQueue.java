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

import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.compiletasks.Long64PreparationTask;

import jdk.graal.compiler.debug.DebugContext;

public class WebImageJSCompileQueue extends WebImageCompileQueue {

    private final DebugContext debug;

    public WebImageJSCompileQueue(FeatureHandler featureHandler, HostedUniverse hUniverse, RuntimeConfiguration runtimeConfiguration, DebugContext debug) {
        super(featureHandler, hUniverse, runtimeConfiguration, debug);
        this.debug = debug;
    }

    private void prepareLong64Methods() throws InterruptedException {
        executor.init();
        universe.getMethods().stream().filter(method -> method.compilationInfo.getCompilationGraph() != null)
                        .forEach(method -> executor.execute(new Long64PreparationTask(method, providers, getCustomizedOptions(method, debug))));
        executor.start();
        executor.complete();
        executor.shutdown();
    }

    @Override
    protected void checkRestrictHeapAnnotations(DebugContext debugParam) {
        // No heap restriction checks required
    }

    @Override
    @SuppressWarnings("try")
    protected void compileAll() throws InterruptedException {
        try (StopTimer t = TimerCollection.createTimerAndStart("(prep. long64)")) {
            prepareLong64Methods();
        }

        super.compileAll();
    }

    @Override
    protected void checkUninterruptibleAnnotations() {
        // The @Uninterruptible annotations are currently meaningless in the JS backend.
    }

    @Override
    public void scheduleEntryPoints() {
        /*
         * Mark all methods with a graph as compiled because we do not yet call
         * ensureCalleesCompiled properly and can thus not only start from the entry points.
         */
        universe.getMethods().stream().filter(method -> method.compilationInfo.getCompilationGraph() != null).forEach(
                        method -> ensureCompiled(method, new WebImageReason()));
    }
}
