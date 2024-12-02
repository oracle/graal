/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jcmd;

import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SigQuitFeature;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.attach.AttachApiFeature;
import com.oracle.svm.core.attach.AttachApiSupport;
import com.oracle.svm.core.attach.AttachListenerThread;
import com.oracle.svm.core.dcmd.DCmd;
import com.oracle.svm.core.dcmd.DCmdFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

/**
 * jcmd can be used to send diagnostic command requests to a running JVM.
 *
 * Below is a rough high-level overview of how the interaction between jcmd and Native Image works:
 * <ul>
 * <li>In a Native Image startup hook (see {@link SigQuitFeature}), we check if an old socket file
 * exists and delete it if necessary. Then, we register a {@code SIGQUIT/SIGBREAK} signal
 * handler.</li>
 * <li>jcmd creates a {@code .attach_pid<pid>} file in a well-known directory and raises a
 * {@code SIGQUIT} signal.</li>
 * <li>Native Image handles the signal in a Java thread and initializes the {@link AttachApiSupport}
 * if the {@code .attach_pid<pid>} file is detected:
 * <ul>
 * <li>A domain socket is created and bound to the file {@code .java_pid<pid>}.</li>
 * <li>A dedicated {@link AttachListenerThread listener thread} is started that acts as a
 * single-threaded server. It waits for a client to connect, reads a request, executes it, and
 * returns the response to the client via the socket connection.</li>
 * </ul>
 * <li>Once jcmd detects the file for the domain socket, it can connect to the same socket and
 * communicate with Native Image. It may then request the execution of {@link DCmd diagnostic
 * commands}.</li>
 * </ul>
 */
@AutomaticallyRegisteredFeature
public class JCmdFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJCmdSupport();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(SigQuitFeature.class, AttachApiFeature.class, DCmdFeature.class);
    }
}
