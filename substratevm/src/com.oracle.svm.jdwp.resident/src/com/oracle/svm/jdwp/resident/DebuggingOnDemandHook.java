/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.jdwp.bridge.DebugOptions;

public class DebuggingOnDemandHook implements RuntimeSupport.Hook {

    static final String ADDITIONAL_JDWP_OPTIONS_ENV_VARIABLE = "_NATIVE_JDWP_OPTIONS";

    @Override
    public void execute(boolean isFirstIsolate) {
        // TODO(peterssen): GR-55057 Support attaching the JDWP debugger to any isolate, not only
        // the first.
        if (isFirstIsolate) {

            String jdwpOptions = JDWPOptions.JDWPOptions.getValue();
            if (jdwpOptions == null) {
                // Debugger not requested.
                return;
            }

            // Options can be added externally via this environment variable. Anything contained in
            // it will get a comma prepended to it (if needed), then it will be added to the end of
            // the JDWP options.
            // This mimics the _JAVA_JDWP_OPTIONS env. variable used in HotSpot.
            // Note that a different variable name is used here to avoid collisions with options
            // meant for the HotSpot JDWP agent.
            String nativeJDWPOptions = System.getenv(ADDITIONAL_JDWP_OPTIONS_ENV_VARIABLE);
            DebugOptions.Options options = null;
            try {
                options = DebugOptions.parse(jdwpOptions, nativeJDWPOptions, true, JDWPOptions.JDWPTrace.getValue());
            } catch (IllegalArgumentException e) {
                String errorMessage = MetadataUtil.fmt("ERROR: JDWP option syntax error: %s %s=%s",
                                jdwpOptions,
                                ADDITIONAL_JDWP_OPTIONS_ENV_VARIABLE,
                                nativeJDWPOptions);
                System.err.println(errorMessage);
                System.exit(1);
            }

            // TODO(peterssen): GR-55061 Add support for starting JDWP debugging on signal.
            // Debug on signal (USR2) allowing to attach to a running executable,
            // but it's not clear to me how this is supposed to work and what's the tooling
            // involved.
            // Signal.handle(new Signal("USR2"), new DebuggingOnDemandHandler(options));
            new DebuggingOnDemandHandler(options).spawnJDWPThread();
        } else {
            VMError.shouldNotReachHere("debugging only works in main isolate, for now");
        }
    }
}
