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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jfr.Target_jdk_jfr_internal_dcmd_AbstractDCmd;
import com.oracle.svm.core.util.BasedOnJDKFile;

public class JfrDumpDCmd extends AbstractJfrDCmd {
    @Platforms(Platform.HOSTED_ONLY.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/jfr/dcmd/jfrDcmds.hpp#L81-L83")
    public JfrDumpDCmd() {
        super("JFR.dump", "Copies contents of a JFR recording to file. Either the name or the recording id must be specified.", Impact.Medium);
    }

    @Override
    protected Target_jdk_jfr_internal_dcmd_AbstractDCmd createDCmd() {
        return SubstrateUtil.cast(new Target_jdk_jfr_internal_dcmd_DCmdDump(), Target_jdk_jfr_internal_dcmd_AbstractDCmd.class);
    }
}

@TargetClass(className = "jdk.jfr.internal.dcmd.DCmdDump")
final class Target_jdk_jfr_internal_dcmd_DCmdDump {
    @Alias
    Target_jdk_jfr_internal_dcmd_DCmdDump() {
    }
}
