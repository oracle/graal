/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.hosted.server.NativeImageBuildServer;

/**
 * Interface for executing SVM image building inside the SVM image build server (
 * {@link NativeImageBuildServer}).
 */
public interface ImageBuildTask {

    /**
     * Main function for remote image building which is invoked on every image building request sent
     * to the server.
     *
     * API NOTE: Standard error and standard output for image building will be reported remotely
     * only if printed within this method. After {@code build} finishes, the static state of the JDK
     * and {@link NativeImageBuildServer} must not have pointers to classes loaded by
     * {@code compilationClassLoader}.
     *
     * @param args arguments passed with the request to the SVM image builder
     * @param compilationClassLoader the classloader used for this image building task
     * @return exit status of compilation
     * @see NativeImageBuildServer
     */
    int build(String[] args, NativeImageClassLoader compilationClassLoader);

    /**
     * Requests interruption of the image build.
     */
    void interruptBuild();

}
