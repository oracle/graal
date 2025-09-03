/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr;

import com.oracle.svm.core.os.RawFileOperationSupport;
import org.graalvm.nativeimage.ImageSingletons;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * JFR emergency dumps are snapshots generated when the VM shuts down due to unexpected
 * circumstances such as OOME or VM crash. Currently, only dumping on OOME is supported. Emergency
 * dumps are a best effort attempt to persist in-flight data and consolidate data in the on-disk JFR
 * chunk repository into a snapshot. This process is allocation free.
 */
public interface JfrEmergencyDumpSupport {
    @Fold
    static boolean isPresent() {
        return ImageSingletons.contains(JfrEmergencyDumpSupport.class);
    }

    @Fold
    static JfrEmergencyDumpSupport singleton() {
        return ImageSingletons.lookup(JfrEmergencyDumpSupport.class);
    }

    void initialize();

    void setRepositoryLocation(String dirText);

    void setDumpPath(String dumpPathText);

    String getDumpPath();

    RawFileOperationSupport.RawFileDescriptor chunkPath();

    void onVmError();

    void teardown();
}
