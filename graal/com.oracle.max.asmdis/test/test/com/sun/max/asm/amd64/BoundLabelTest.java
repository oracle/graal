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
package test.com.sun.max.asm.amd64;

import static com.sun.max.asm.amd64.AMD64GeneralRegister64.*;

import java.io.*;
import java.util.*;

import junit.framework.*;

import com.sun.max.asm.*;
import com.sun.max.asm.amd64.complete.*;
import com.sun.max.asm.dis.amd64.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.amd64.*;
import com.sun.max.ide.*;

/**
 */
public class BoundLabelTest extends MaxTestCase {

    public BoundLabelTest() {
        super();

    }

    public BoundLabelTest(String name) {
        super(name);
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(BoundLabelTest.class.getName());
        suite.addTestSuite(BoundLabelTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(BoundLabelTest.class);
    }

    private static final int LABEL_DELTA = 10;

    private int insertInstructions(AMD64Assembler assembler, Label[] labels, int labelIndex) {
        int index = labelIndex;
        for (int i = 0; i < LABEL_DELTA; i++) {
            assembler.bindLabel(labels[index]);
            assembler.nop();
            index++;
        }
        return index;
    }

    private byte[] assemble(long startAddress, int labelDelta) throws IOException, AssemblyException {
        final AMD64Assembler assembler = new AMD64Assembler(startAddress);
        final List<AMD64Template> labelTemplates = AMD64Assembly.ASSEMBLY.labelTemplates();
        final Label[] labels = new Label[labelTemplates.size() + LABEL_DELTA];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
        }
        int labelIndex = 0;
        int bindIndex = 0;
        if (labelDelta < 0) {
            bindIndex = insertInstructions(assembler, labels, 0);
        } else {
            labelIndex = labelDelta;
        }
        for (AMD64Template template : labelTemplates) {
            assembler.bindLabel(labels[bindIndex]);
            final List<Argument> arguments = new ArrayList<Argument>(template.parameters().size());
            for (int parameterIndex = 0; parameterIndex < template.parameters().size(); parameterIndex++) {
                if (parameterIndex == template.labelParameterIndex()) {
                    arguments.set(parameterIndex, labels[labelIndex]);
                } else {
                    final Parameter parameter = template.parameters().get(parameterIndex);
                    final Iterator<? extends Argument> testArguments = parameter.getLegalTestArguments().iterator();
                    Argument argument = testArguments.next();
                    // skip AL, CL, DL, BL:
                    for (int i = 0; i < 4; i++) {
                        if (testArguments.hasNext()) {
                            argument = testArguments.next();
                        }
                    }
                    arguments.set(parameterIndex, argument);
                }
            }
            AMD64Assembly.ASSEMBLY.assemble(assembler, template, arguments);
            bindIndex++;
            labelIndex++;
        }
        if (labelDelta >= 0) {
            insertInstructions(assembler, labels, bindIndex);
        }
        for (int i = 0; i < labels.length; i++) {
            final Label label = labels[i];
            assert label.state() == Label.State.BOUND;
        }
        return assembler.toByteArray();
    }

    private void disassemble(long startAddress, byte[] bytes) throws IOException, AssemblyException {
        final AMD64Disassembler disassembler = new AMD64Disassembler(startAddress, null);
        final BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        disassembler.scanAndPrint(stream, System.out);
    }

    public void test_allLabelInstructions() throws IOException, AssemblyException {
        final long startAddress = 0x0L;

        byte[] bytes = assemble(startAddress, 0);
        disassemble(startAddress, bytes);

        bytes = assemble(startAddress, LABEL_DELTA);
        disassemble(startAddress, bytes);

        bytes = assemble(startAddress, -LABEL_DELTA);
        disassemble(startAddress, bytes);
    }

    public void test_effectOfVariableInstructionLengthOnLabel() throws IOException, AssemblyException {
        // Repeat with different assembled sizes of the 'jnz' instruction below:
        for (int n = 4; n < 2000; n += 128) {
            final long startAddress = 0x0L;
            final Label label = new Label();
            final Label target = new Label();
            final AMD64Assembler a = new AMD64Assembler(startAddress);
            a.nop();
            a.jnz(target);
            a.nop();
            a.nop();
            a.nop();
            a.nop();
            a.nop();
            a.nop();
            a.nop();
            a.nop();
            a.bindLabel(label);
            a.call(0);
            for (int i = 0; i < n; i++) {
                a.nop();
            }
            a.xor(RAX, RAX);
            a.bindLabel(target);
            a.xor(RBX, RBX);
            final byte[] bytes = a.toByteArray();
            assertTrue(bytes[label.position()] == (byte) 0xE8); // is there a call instruction at the label's address?
        }
    }

}
