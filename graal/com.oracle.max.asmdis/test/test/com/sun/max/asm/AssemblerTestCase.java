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

import java.io.*;

import com.sun.max.asm.gen.*;
import com.sun.max.ide.*;
import com.sun.max.io.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;

/**
 * Base class for assembler tests that defines program options common to
 * all assembler test harnesses.
 */
public abstract class AssemblerTestCase extends MaxTestCase {

    protected final OptionSet options = new OptionSet();

    private final Option<String> templateOption = options.newStringOption("pattern", "",
            "specifies a pattern so that only templates with the matching patterns are tested");
    private final Option<Boolean> serialized = options.newBooleanOption("serial", false,
            "forces testing to be single threaded");
    private final Option<Integer> startSerialOption = options.newIntegerOption("start", 0,
            "specifies the first serial number to begin testing");
    private final Option<Integer> endSerialOption = options.newIntegerOption("end", Integer.MAX_VALUE,
            "specifies the last serial number to test");
    private final Option<Boolean> sourceOption = options.newBooleanOption("only-make-asm-source", false,
            "specifies that the testing framework should only create the assembler source files and should not run " +
            "any tests.");

    /**
     * Subclasses override this to modify a tester that is about to be {@linkplain #run() run}.
     * Typically, the modification is based on the values of any subclasses specific addition to {@link #options}.
     */
    protected void configure(AssemblyTester tester) {
    }

    public AssemblerTestCase() {
    }

    public AssemblerTestCase(String name) {
        super(name);
    }

    public final void run(AssemblyTester tester) {
        options.parseArguments(getProgramArguments());
        configure(tester);
        tester.setTemplatePattern(templateOption.getValue());
        if (sourceOption.getValue()) {
            final File sourceFile = new File(tester.assembly().isa().name().toLowerCase() + "-asmTest.s");
            try {
                final IndentWriter indentWriter = new IndentWriter(new PrintWriter(new BufferedWriter(new FileWriter(sourceFile))));
                tester.createExternalSource(startSerialOption.getValue(), endSerialOption.getValue(), indentWriter);
                indentWriter.close();
            } catch (IOException e) {
                throw ProgramError.unexpected("Could not open " + sourceFile + " for writing", e);
            }
        } else {
            tester.run(startSerialOption.getValue(), endSerialOption.getValue(), !serialized.getValue());
        }
    }
}
