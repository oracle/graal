/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.criutils;

import java.io.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiAddress.Scale;
import com.sun.cri.ci.CiValue.Formatter;
import com.sun.cri.ri.*;

/**
 * Utility for printing compilation related data structures at various compilation phases.
 * The output format is such that it can then be fed to the
 * <a href="https://c1visualizer.dev.java.net/">C1 Visualizer</a>.
 */
public class CompilationPrinter {
    public static final String COLUMN_END = " <|@";
    public static final String HOVER_START = "<@";
    public static final String HOVER_SEP = "|@";
    public static final String HOVER_END = ">@";

    private static OutputStream globalOut;

    /**
     * Gets a global output stream on a file in the current working directory.
     * This stream is first opened if necessary. The name of the file
     * is {@code "compilations-" + System.currentTimeMillis() + ".cfg"}.
     *
     * @return the global output stream or {@code null} if there was an error opening the file for writing
     */
    public static synchronized OutputStream globalOut() {
        if (globalOut == null) {
            File file = new File("compilations-" + System.currentTimeMillis() + ".cfg");
            try {
                globalOut = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                TTY.println("WARNING: Could not open " + file.getAbsolutePath());
            }
        }
        return globalOut;
    }

    protected final LogStream out;

    /**
     * Creates a control flow graph printer.
     *
     * @param os where the output generated via this printer will be sent
     */
    public CompilationPrinter(OutputStream os) {
        out = new LogStream(os);
    }

    /**
     * Flushes all buffered output to the underlying output stream.
     */
    public void flush() {
        out.flush();
    }

    protected void begin(String string) {
        out.println("begin_" + string);
        out.adjustIndentation(2);
    }

    protected void end(String string) {
        out.adjustIndentation(-2);
        out.println("end_" + string);
    }

    /**
     * Prints a compilation timestamp for a given method.
     *
     * @param method the method for which a timestamp will be printed
     */
    public void printCompilation(RiMethod method) {
        begin("compilation");
        out.print("name \" ").print(CiUtil.format("%H::%n", method, true)).println('"');
        out.print("method \"").print(CiUtil.format("%f %r %H.%n(%p)", method, true)).println('"');
        out.print("date ").println(System.currentTimeMillis());
        end("compilation");
    }

    /**
     * Formats a given {@linkplain FrameState JVM frame state} as a multi line string.
     */
    protected String debugInfoToString(CiCodePos codePos, CiBitMap registerRefMap, CiBitMap frameRefMap, OperandFormatter fmt, CiArchitecture arch) {
        StringBuilder sb = new StringBuilder();

        if (registerRefMap != null) {
            sb.append("reg-ref-map:");
            for (int reg = registerRefMap.nextSetBit(0); reg >= 0; reg = registerRefMap.nextSetBit(reg + 1)) {
                sb.append(' ').append(arch == null ? "reg" + reg : arch.registers[reg]);
            }
            sb.append("\n");
        }

        if (frameRefMap != null) {
            sb.append("frame-ref-map:");
            for (int reg = frameRefMap.nextSetBit(0); reg >= 0; reg = frameRefMap.nextSetBit(reg + 1)) {
                sb.append(' ').append(CiStackSlot.get(CiKind.Object, reg));
            }
            sb.append("\n");
        }

        if (codePos != null) {
            do {
                sb.append(CiUtil.toLocation(codePos.method, codePos.bci));
                sb.append('\n');
                if (codePos instanceof CiFrame) {
                    CiFrame frame = (CiFrame) codePos;
                    if (frame.numStack > 0) {
                        sb.append("stack: ");
                        for (int i = 0; i < frame.numStack; i++) {
                            sb.append(valueToString(frame.getStackValue(i), fmt)).append(' ');
                        }
                        sb.append("\n");
                    }

                    if (frame.numLocks > 0) {
                        sb.append("locks: ");
                        for (int i = 0; i < frame.numLocks; ++i) {
                            sb.append(valueToString(frame.getLockValue(i), fmt)).append(' ');
                        }
                        sb.append("\n");
                    }

                    sb.append("locals: ");
                    for (int i = 0; i < frame.numLocals; i++) {
                        sb.append(valueToString(frame.getLocalValue(i), fmt)).append(' ');
                    }
                    sb.append("\n");
                }
                codePos = codePos.caller;
            } while (codePos != null);
        }
        return sb.toString();
    }


    protected String valueToString(CiValue value, Formatter operandFmt) {
        if (value == null) {
            return "-";
        }
        return operandFmt.format(value);
    }


    /**
     * Formats LIR operands as expected by the C1 Visualizer.
     */
    public static class OperandFormatter extends Formatter {
        /**
         * The textual delimiters used for an operand depend on the context in which it is being
         * printed. When printed as part of a frame state or as the result operand in a HIR node listing,
         * it is enclosed in double-quotes (i.e. {@code "}'s).
         */
        public final boolean asStateOrHIROperandResult;

        public OperandFormatter(boolean asStateOrHIROperandResult) {
            this.asStateOrHIROperandResult = asStateOrHIROperandResult;
        }

        @Override
        public String format(CiValue operand) {
            if (operand.isLegal()) {
                String op;
                if (operand.isVariableOrRegister() || operand.isStackSlot()) {
                    op = operand.name();
                } else if (operand.isConstant()) {
                    CiConstant constant = (CiConstant) operand;
                    op = operand.kind.javaName + ":" + operand.kind.format(constant.boxedValue());
                } else if (operand.isAddress()) {
                    CiAddress address = (CiAddress) operand;
                    op = "Base:" + format(address.base);
                    if (!address.index.isIllegal()) {
                        op += " Index:" + format(address.index);
                    }
                    if (address.scale != Scale.Times1) {
                        op += " * " + address.scale.value;
                    }
                    op += " Disp:" + address.displacement;
                } else {
                    assert operand.isIllegal();
                    op = "-";
                }
                if (operand.kind != CiKind.Illegal) {
                    op += "|" + operand.kind.typeChar;
                }
                if (asStateOrHIROperandResult) {
                    op = " \"" + op.replace('"', '\'') + "\" ";
                }
                return op;
            }
            return "";
        }
    }

    public void printMachineCode(String code, String label) {
        if (code.length() == 0) {
            return;
        }
        if (label != null) {
            begin("cfg");
            out.print("name \"").print(label).println('"');
            end("cfg");
        }
        begin("nmethod");
        out.print(code);
        out.println(" <|@");
        end("nmethod");
    }

    public void printBytecodes(String code) {
        if (code.length() == 0) {
            return;
        }
        begin("bytecodes");
        out.print(code);
        out.println(" <|@");
        end("bytecodes");
    }
}
