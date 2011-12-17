/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package test.com.sun.max.asm;

import com.sun.max.asm.gen.*;
import com.sun.max.program.option.*;

/**
 * Base class for assembler tests that use an external assembler which may
 * be executed remotely on another machine by using the '-remote=user@host'
 * program option.
 */
public abstract class ExternalAssemblerTestCase extends AssemblerTestCase {

    private final Option<String> removeUserOption = options.newStringOption("remote", null,
            "execute commands via an ssh connection with supplied user@hostname");
    private final Option<String> removePathOption = options.newStringOption("remote-asm-path", null,
            "specifies an absolute path to the directory containing an assembler executable");

    public ExternalAssemblerTestCase() {
        super();
    }

    public ExternalAssemblerTestCase(String name) {
        super(name);
    }

    @Override
    protected void configure(AssemblyTester tester) {
        tester.setRemoteUserAndHost(removeUserOption.getValue());
        if (removePathOption.getValue() != null) {
            tester.setRemoteAssemblerPath(removePathOption.getValue());
        }
    }
}
