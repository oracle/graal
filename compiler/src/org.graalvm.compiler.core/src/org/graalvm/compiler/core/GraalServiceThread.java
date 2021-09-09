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
package org.graalvm.compiler.core;

import jdk.vm.ci.services.Services;

/**
 * This is a utility class for Threads started by the compiler itself. In certain execution
 * environments extra work must be done for these threads to execute correctly and this class
 * provides hooks for this work.
 */
public class GraalServiceThread extends Thread {
    private final Runnable runnable;

    public GraalServiceThread(String name, Runnable runnable) {
        super(name);
        this.runnable = runnable;
    }

    @Override
    public final void run() {
        try {
            beforeRun();
        } catch (InternalError t) {
            // There was a problem attaching this thread to the libgraal peer runtime.
            // Not much that can be done apart from terminating the thread.
            onAttachError(t);
            return;
        }
        try {
            runnable.run();
        } finally {
            afterRun();
        }
    }

    /**
     * Notifies of an error on attaching this thread to the libgraal peer runtime.
     *
     * The default implementation of this method is to print the stack trace for {@code error} if
     * the {@code GraalServiceThread.verbose} system property is {@code "true"}.
     *
     * @param error the error
     */
    protected void onAttachError(InternalError error) {
        if (Boolean.parseBoolean(Services.getSavedProperties().getOrDefault("GraalServiceThread.verbose", "false"))) {
            error.printStackTrace();
        }
    }

    /**
     * Substituted by {@code com.oracle.svm.graal.hotspot.libgraal.
     * Target_org_graalvm_compiler_core_GraalServiceThread} to attach to the peer runtime if
     * required.
     */
    private void afterRun() {
    }

    /**
     * Substituted by {@code com.oracle.svm.graal.hotspot.libgraal.
     * Target_org_graalvm_compiler_core_GraalServiceThread} to attach to the peer runtime if
     * required.
     */
    private void beforeRun() {
    }
}
