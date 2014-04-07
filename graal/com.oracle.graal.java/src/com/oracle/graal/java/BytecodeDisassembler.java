/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.bytecode.Bytecodes.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;

/**
 * Utility for producing a {@code javap}-like disassembly of bytecode.
 */
public class BytecodeDisassembler implements BytecodeDisassemblerProvider {

    /**
     * Specifies if the disassembly for a single instruction can span multiple lines.
     */
    private final boolean multiline;

    public BytecodeDisassembler(boolean multiline) {
        this.multiline = multiline;
    }

    public BytecodeDisassembler() {
        this(true);
    }

    /**
     * Disassembles the bytecode of a given method in a {@code javap}-like format.
     * 
     * @return {@code null} if {@code method} has no bytecode (e.g., it is native or abstract)
     */
    public String disassemble(ResolvedJavaMethod method) {
        if (method.getCode() == null) {
            return null;
        }
        ConstantPool cp = method.getConstantPool();
        BytecodeStream stream = new BytecodeStream(method.getCode());
        StringBuilder buf = new StringBuilder();
        int opcode = stream.currentBC();
        while (opcode != Bytecodes.END) {
            int bci = stream.currentBCI();
            String mnemonic = Bytecodes.nameOf(opcode);
            buf.append(String.format("%4d: %-14s", bci, mnemonic));
            if (stream.nextBCI() > bci + 1) {
                // @formatter:off
                switch (opcode) {
                    case BIPUSH         : buf.append(stream.readByte()); break;
                    case SIPUSH         : buf.append(stream.readShort()); break;
                    case NEW            :
                    case CHECKCAST      :
                    case INSTANCEOF     :
                    case ANEWARRAY      : {
                        int cpi = stream.readCPI();
                        JavaType type = cp.lookupType(cpi, opcode);
                        buf.append(String.format("#%-10d // %s", cpi, MetaUtil.toJavaName(type)));
                        break;
                    }
                    case GETSTATIC      :
                    case PUTSTATIC      :
                    case GETFIELD       :
                    case PUTFIELD       : {
                        int cpi = stream.readCPI();
                        JavaField field = cp.lookupField(cpi, opcode);
                        String fieldDesc = field.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? MetaUtil.format("%n:%T", field) : MetaUtil.format("%H.%n:%T", field);
                        buf.append(String.format("#%-10d // %s", cpi, fieldDesc));
                        break;
                    }
                    case INVOKEVIRTUAL  :
                    case INVOKESPECIAL  :
                    case INVOKESTATIC   : {
                        int cpi = stream.readCPI();
                        JavaMethod callee = cp.lookupMethod(cpi, opcode);
                        String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? MetaUtil.format("%n:(%P)%R", callee) : MetaUtil.format("%H.%n:(%P)%R", callee);
                        buf.append(String.format("#%-10d // %s", cpi, calleeDesc));
                        break;
                    }
                    case INVOKEINTERFACE: {
                        int cpi = stream.readCPI();
                        JavaMethod callee = cp.lookupMethod(cpi, opcode);
                        String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? MetaUtil.format("%n:(%P)%R", callee) : MetaUtil.format("%H.%n:(%P)%R", callee);
                        buf.append(String.format("#%-10s // %s", cpi + ", " + stream.readUByte(bci + 3), calleeDesc));
                        break;
                    }
                    case INVOKEDYNAMIC: {
                        int cpi = stream.readCPI4();
                        JavaMethod callee = cp.lookupMethod(cpi, opcode);
                        String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? MetaUtil.format("%n:(%P)%R", callee) : MetaUtil.format("%H.%n:(%P)%R", callee);
                        buf.append(String.format("#%-10d // %s", cpi, calleeDesc));
                        break;
                    }
                    case LDC            :
                    case LDC_W          :
                    case LDC2_W         : {
                        int cpi = stream.readCPI();
                        Object constant = cp.lookupConstant(cpi);
                        String desc = null;
                        if (constant instanceof Constant) {
                            Constant c = ((Constant) constant);
                            desc = c.toValueString();
                        } else {
                            desc = constant.toString();
                        }
                        if (!multiline) {
                            desc = desc.replaceAll("\\n", "");
                        }
                        buf.append(String.format("#%-10d // %s", cpi, desc));
                        break;
                    }
                    case RET            :
                    case ILOAD          :
                    case LLOAD          :
                    case FLOAD          :
                    case DLOAD          :
                    case ALOAD          :
                    case ISTORE         :
                    case LSTORE         :
                    case FSTORE         :
                    case DSTORE         :
                    case ASTORE         : {
                        buf.append(String.format("%d", stream.readLocalIndex()));
                        break;
                    }
                    case IFEQ           :
                    case IFNE           :
                    case IFLT           :
                    case IFGE           :
                    case IFGT           :
                    case IFLE           :
                    case IF_ICMPEQ      :
                    case IF_ICMPNE      :
                    case IF_ICMPLT      :
                    case IF_ICMPGE      :
                    case IF_ICMPGT      :
                    case IF_ICMPLE      :
                    case IF_ACMPEQ      :
                    case IF_ACMPNE      :
                    case GOTO           :
                    case JSR            :
                    case IFNULL         :
                    case IFNONNULL      :
                    case GOTO_W         :
                    case JSR_W          : {
                        buf.append(String.format("%d", stream.readBranchDest()));
                        break;
                    }
                    case LOOKUPSWITCH   :
                    case TABLESWITCH    : {
                        BytecodeSwitch bswitch = opcode == LOOKUPSWITCH ? new BytecodeLookupSwitch(stream, bci) : new BytecodeTableSwitch(stream, bci);
                        if (multiline) {
                            buf.append("{ // " + bswitch.numberOfCases());
                            for (int i = 0; i < bswitch.numberOfCases(); i++) {
                                buf.append(String.format("%n           %7d: %d", bswitch.keyAt(i), bswitch.targetAt(i)));
                            }
                            buf.append(String.format("%n           default: %d", bswitch.defaultTarget()));
                            buf.append(String.format("%n      }"));
                        } else {
                            buf.append("[" + bswitch.numberOfCases()).append("] {");
                            for (int i = 0; i < bswitch.numberOfCases(); i++) {
                                buf.append(String.format("%d: %d", bswitch.keyAt(i), bswitch.targetAt(i)));
                                if (i != bswitch.numberOfCases() - 1) {
                                    buf.append(", ");
                                }
                            }
                            buf.append(String.format("} default: %d", bswitch.defaultTarget()));
                        }
                        break;
                    }
                    case NEWARRAY       : {
                        int code = stream.readLocalIndex();
                        // Checkstyle: stop
                        switch (code) {
                            case 4:  buf.append("boolean"); break;
                            case 5:  buf.append("char"); break;
                            case 6:  buf.append("float"); break;
                            case 7:  buf.append("double"); break;
                            case 8:  buf.append("byte"); break;
                            case 9:  buf.append("short"); break;
                            case 10: buf.append("int"); break;
                            case 11: buf.append("long"); break;
                        }
                        // Checkstyle: resume

                        break;
                    }
                    case MULTIANEWARRAY : {
                        int cpi = stream.readCPI();
                        JavaType type = cp.lookupType(cpi, opcode);
                        buf.append(String.format("#%-10s // %s", cpi + ", " + stream.readUByte(bci + 3), MetaUtil.toJavaName(type)));
                        break;
                    }
                }
                // @formatter:on
            }
            buf.append(String.format("%n"));
            stream.next();
            opcode = stream.currentBC();
        }
        return buf.toString();
    }
}
