/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.dcmd;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.RuntimeCompilation;

/** Registers the infrastructure for diagnostic commands. */
@AutomaticallyRegisteredFeature
public class DCmdFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJCmdSupport();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        DCmdSupport dcmdSupport = new DCmdSupport();
        ImageSingletons.add(DCmdSupport.class, dcmdSupport);

        if (VMInspectionOptions.hasHeapDumpSupport()) {
            dcmdSupport.registerCommand(new GCHeapDumpDCmd());
        }

        dcmdSupport.registerCommand(new GCRunDCmd());

        if (VMInspectionOptions.hasJfrSupport()) {
            dcmdSupport.registerCommand(new JfrStartDCmd());
            dcmdSupport.registerCommand(new JfrStopDCmd());
            dcmdSupport.registerCommand(new JfrCheckDCmd());
            dcmdSupport.registerCommand(new JfrDumpDCmd());
        }

        dcmdSupport.registerCommand(new ThreadDumpToFileDCmd());
        dcmdSupport.registerCommand(new ThreadPrintDCmd());

        if (ImageSingletons.contains(JavaMainSupport.class)) {
            dcmdSupport.registerCommand(new VMCommandLineDCmd());
        }

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            dcmdSupport.registerCommand(new VMNativeMemoryDCmd());
        }

        if (RuntimeCompilation.isEnabled()) {
            dcmdSupport.registerCommand(new CompilerDumpCodeCacheDCmd());
        }

        dcmdSupport.registerCommand(new VMSystemPropertiesDCmd());
        dcmdSupport.registerCommand(new VMUptimeDmd());
        dcmdSupport.registerCommand(new VMVersionDmd());

        dcmdSupport.registerCommand(new HelpDCmd());
    }
}
