/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 *
 * @author sdedic
 */
public class PreRemoveCommand implements InstallerCommand {
    private CommandInput input;
    private Feedback feedback;

    @Override
    public Map<String, String> supportedOptions() {
        return Collections.emptyMap();
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.feedback = feedBack;
    }

    @Override
    public int execute() throws IOException {
        String compId;
        PreRemoveProcess pp = new PreRemoveProcess(input.getGraalHomePath(), input.getFileOperations(), feedback);
        while ((compId = input.nextParameter()) != null) {
            ComponentInfo info = input.getLocalRegistry().loadSingleComponent(compId.toLowerCase(), true);
            if (info != null) {
                pp.addComponentInfo(info);
            }
        }

        pp.run();
        return 0;
    }
}
