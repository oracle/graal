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

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic commands should only be registered at build time and are effectively singletons
 * managed by this class. The Attach-API uses this class directly, which then dispatches the
 * appropriate diagnostic command to handle the request.
 */
public class DcmdSupport {
    private List<Dcmd> dcmds;

    @Platforms(HOSTED_ONLY.class)
    public DcmdSupport() {
        dcmds = new ArrayList<>();
    }

    Dcmd getDcmd(String cmdName) {
        for (Dcmd dcmd : dcmds) {
            if (dcmd.getName().equals(cmdName)) {
                return dcmd;
            }
        }
        return null;
    }

    /** Should be called by relevant features that want to be accessed via diagnostic commands. */
    @Platforms(HOSTED_ONLY.class)
    public void registerDcmd(Dcmd dcmd) {
        dcmds.add(dcmd);
    }

    String[] getRegisteredCommands() {
        String[] commands = new String[dcmds.size()];
        for (int i = 0; i < dcmds.size(); i++) {
            commands[i] = dcmds.get(i).getName();
        }
        return commands;
    }

    /**
     * This method is to be used at runtime by the Attach-API. It connects the Attach-API with the
     * DCMD infrastructure.
     */
    public String parseAndExecute(String arguments) throws DcmdParseException {
        String[] argumentsSplit = arguments.split(" ");
        assert argumentsSplit.length > 0;
        String cmdName = argumentsSplit[0];

        Dcmd dcmd = getDcmd(cmdName);

        if (dcmd == null) {
            return "The requested command is not supported.";
        }

        return dcmd.parseAndExecute(argumentsSplit);
    }

}
