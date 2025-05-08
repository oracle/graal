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

package com.oracle.svm.core.dcmd;

import com.oracle.svm.core.jfr.Target_jdk_jfr_internal_dcmd_AbstractDCmd;
import com.oracle.svm.core.util.VMError;

/**
 * Base class for JFR-related diagnostic commands. Note that the JDK already implements those
 * diagnostic commands, so we wrap and reuse the JDK implementations.
 */
public abstract class AbstractJfrDCmd extends AbstractDCmd {
    public AbstractJfrDCmd(String name, String description, Impact impact) {
        super(name, description, impact);
    }

    @Override
    public String parseAndExecute(String input) {
        assert input.startsWith(getName());

        Target_jdk_jfr_internal_dcmd_AbstractDCmd cmd = createDCmd();
        String args = input.substring(getName().length());
        String[] result = cmd.execute("attach", args, ' ');
        return String.join(System.lineSeparator(), result);
    }

    @Override
    protected String execute(DCmdArguments args) throws Throwable {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    protected String getSyntaxAndExamples() {
        Target_jdk_jfr_internal_dcmd_AbstractDCmd cmd = createDCmd();
        String[] lines = cmd.getHelp();
        return String.join(System.lineSeparator(), lines);
    }

    protected abstract Target_jdk_jfr_internal_dcmd_AbstractDCmd createDCmd();
}
