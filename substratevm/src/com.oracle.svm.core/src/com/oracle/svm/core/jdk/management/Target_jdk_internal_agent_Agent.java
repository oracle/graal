/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.jdk.management;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(className = "jdk.internal.agent.Agent", onlyWith = ManagementAgentModule.IsPresent.class)
public final class Target_jdk_internal_agent_Agent {

    /**
     * This method is substituted to avoid throwing an exception if java.home is null. If a config
     * file is not specified via com.sun.management.config.file=ConfigFilePath, AND java.home is not
     * set, then this method doesn't bother trying to read from config file.
     *
     * This method is mostly copied (aside from a single line change) from jdk19. Commit hash
     * 967a28c3d85fdde6d5eb48aa0edd8f7597772469.
     */
    @Substitute
    @TargetElement(onlyWith = JmxServerIncluded.class)
    public static void readConfiguration(String fname, Properties p) {
        if (fname == null) {
            // if file is not specified, don't bother trying to read from a management.properties
            // file.
            return;
        }
        final File configFile = new File(fname);
        if (!configFile.exists()) {
            ManagementAgentModule.agentError(ManagementAgentModule.CONFIG_FILE_NOT_FOUND, fname);
        }

        InputStream in = null;
        try {
            in = new FileInputStream(configFile);
            p.load(in);
        } catch (FileNotFoundException e) {
            ManagementAgentModule.agentError(ManagementAgentModule.CONFIG_FILE_OPEN_FAILED, e.getMessage());
        } catch (IOException e) {
            ManagementAgentModule.agentError(ManagementAgentModule.CONFIG_FILE_OPEN_FAILED, e.getMessage());
        } catch (SecurityException e) {
            ManagementAgentModule.agentError(ManagementAgentModule.CONFIG_FILE_ACCESS_DENIED, fname);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    ManagementAgentModule.agentError(ManagementAgentModule.CONFIG_FILE_CLOSE_FAILED, fname);
                }
            }
        }
    }
}
