/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.bytecode;

import static jdk.graal.compiler.bytecode.Bytecodes.ALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.ANEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.ASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.BIPUSH;
import static jdk.graal.compiler.bytecode.Bytecodes.CHECKCAST;
import static jdk.graal.compiler.bytecode.Bytecodes.DLOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.DSTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.FLOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.FSTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.GETFIELD;
import static jdk.graal.compiler.bytecode.Bytecodes.GETSTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.GOTO;
import static jdk.graal.compiler.bytecode.Bytecodes.GOTO_W;
import static jdk.graal.compiler.bytecode.Bytecodes.IFEQ;
import static jdk.graal.compiler.bytecode.Bytecodes.IFGE;
import static jdk.graal.compiler.bytecode.Bytecodes.IFGT;
import static jdk.graal.compiler.bytecode.Bytecodes.IFLE;
import static jdk.graal.compiler.bytecode.Bytecodes.IFLT;
import static jdk.graal.compiler.bytecode.Bytecodes.IFNE;
import static jdk.graal.compiler.bytecode.Bytecodes.IFNONNULL;
import static jdk.graal.compiler.bytecode.Bytecodes.IFNULL;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ACMPEQ;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ACMPNE;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPEQ;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPGE;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPGT;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPLE;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPLT;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPNE;
import static jdk.graal.compiler.bytecode.Bytecodes.ILOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.INSTANCEOF;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;
import static jdk.graal.compiler.bytecode.Bytecodes.ISTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.JSR;
import static jdk.graal.compiler.bytecode.Bytecodes.JSR_W;
import static jdk.graal.compiler.bytecode.Bytecodes.LDC;
import static jdk.graal.compiler.bytecode.Bytecodes.LDC2_W;
import static jdk.graal.compiler.bytecode.Bytecodes.LDC_W;
import static jdk.graal.compiler.bytecode.Bytecodes.LLOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.LOOKUPSWITCH;
import static jdk.graal.compiler.bytecode.Bytecodes.LSTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.MULTIANEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.NEW;
import static jdk.graal.compiler.bytecode.Bytecodes.NEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.PUTFIELD;
import static jdk.graal.compiler.bytecode.Bytecodes.PUTSTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.RET;
import static jdk.graal.compiler.bytecode.Bytecodes.SIPUSH;
import static jdk.graal.compiler.bytecode.Bytecodes.TABLESWITCH;

import org.graalvm.collections.EconomicMap;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Utility for producing a {@code javap}-like disassembly of bytecode.
 */
public class BytecodeDisassembler {

    /**
     * Support for normalizing the values of constant pool index operands. Used to normalize
     * disassembly for methods whose classfile representation does not change but whose bytecode as
     * obtained via {@link ResolvedJavaMethod#getCode()} can be variable between JDK versions due to
     * changes in VM internal bytecode rewriting (e.g. JDK-8301996).
     */
    public interface CPIFunction {
        /**
         *
         * @param opcode a bytecode instruction opcode
         * @param cpi the constant pool index operand of an instruction whose opcode is
         *            {@code opcode}
         */
        default int apply(int opcode, int cpi) {
            return cpi;
        }

        /**
         * Identitiy function.
         */
        CPIFunction Identity = new CPIFunction() {
        };

        /**
         * Rewrites each CPI operand to a value denoting the order of the first time it is
         * encountered during disassembly.
         */
        static CPIFunction normalizer() {
            return new CPIFunction() {
                EconomicMap<Integer, Integer> normalized = EconomicMap.create();

                /**
                 * Returns a value for the {@code (opcode, cpi)} tuple that depends solely on when
                 * it is encountered with respective to all other such tuples in the disassembly of
                 * a method.
                 *
                 * @param opcode opcode "namespace" of cpi
                 * @param cpi the constant pool index operand of an instruction whose opcode is
                 *            {@code opcode}. Due to VM internal bytecode rewriting, this may not be
                 *            a class file constant pool index.
                 */
                @Override
                public int apply(int opcode, int cpi) {
                    int key = cpi << 8 | (opcode & 0xFF);
                    Integer res = normalized.get(key);
                    if (res == null) {
                        res = normalized.size();
                        normalized.put(key, res);
                    }
                    return res;
                }
            };
        }
    }

    private final boolean multiline;
    private final String newLine;
    private final boolean format;

    private final CPIFunction cpiFunction;

    /**
     * Creates a bytecode disassembler.
     *
     * @param multiline specifies if the disassembly for a single instruction can span multiple
     *            lines
     * @param newLine specifies the new line string to use. If null, all output is on one line.
     * @param format specifies if the disassembler should use {@link String#format} to do indenting
     *            and aligning
     */
    public BytecodeDisassembler(boolean multiline, String newLine, boolean format, CPIFunction cpiFunction) {
        this.multiline = multiline;
        this.newLine = newLine;
        this.format = format;
        this.cpiFunction = cpiFunction;
        if (multiline && newLine == null) {
            throw new IllegalArgumentException("multiline cannot be true when newLine is null");
        }
    }

    public BytecodeDisassembler(boolean multiline, String newLine) {
        this(multiline, newLine, true, CPIFunction.Identity);
    }

    public BytecodeDisassembler(boolean multiline) {
        this(multiline, System.lineSeparator(), true, CPIFunction.Identity);
    }

    public BytecodeDisassembler() {
        this(true, System.lineSeparator(), true, CPIFunction.Identity);
    }

    public static String disassembleOne(ResolvedJavaMethod method, int bci) {
        return new BytecodeDisassembler(false, null).disassemble(method, bci, bci);
    }

    /**
     * Disassembles the bytecode of a given method in a {@code javap}-like format.
     *
     * @return {@code null} if {@code method} has no bytecode (e.g., it is native or abstract)
     */
    public String disassemble(ResolvedJavaMethod method) {
        return disassemble(method, 0, Integer.MAX_VALUE);
    }

    /**
     * Disassembles the bytecode of a given method in a {@code javap}-like format.
     *
     * @return {@code null} if {@code method} has no bytecode (e.g., it is native or abstract)
     */
    public String disassemble(ResolvedJavaMethod method, int startBci, int endBci) {
        return disassemble(new ResolvedJavaMethodBytecode(method), startBci, endBci);
    }

    /**
     * Disassembles {@code code} in a {@code javap}-like format.
     */
    public String disassemble(Bytecode code) {
        return disassemble(code, 0, Integer.MAX_VALUE);
    }

    /**
     * Disassembles {@code code} in a {@code javap}-like format.
     */
    public String disassemble(Bytecode code, int startBci, int endBci) {
        if (code.getCode() == null) {
            return null;
        }
        ResolvedJavaMethod method = code.getMethod();
        ConstantPool cp = code.getConstantPool();
        BytecodeStream stream = new BytecodeStream(code.getCode());
        StringBuilder buf = new StringBuilder();
        int opcode = stream.currentBC();
        try {
            while (opcode != Bytecodes.END) {
                int bci = stream.currentBCI();
                if (bci >= startBci && bci <= endBci) {
                    String mnemonic = Bytecodes.nameOf(opcode);
                    if (format) {
                        buf.append(String.format("%4d: %-14s", bci, mnemonic));
                    } else {
                        buf.append(bci).append(' ').append(mnemonic);
                    }
                    if (stream.nextBCI() > bci + 1) {
                        if (!format) {
                            buf.append(' ');
                        }
                        decodeOperand(buf, stream, cp, method, bci, opcode);
                    }
                    if (newLine != null) {
                        buf.append(newLine);
                    }
                }
                stream.next();
                opcode = stream.currentBC();
            }
        } catch (Throwable e) {
            throw new RuntimeException(String.format("Error disassembling %s%nPartial disassembly:%n%s", method.format("%H.%n(%p)"), buf.toString()), e);
        }
        return buf.toString();
    }

    private void decodeOperand(StringBuilder buf, BytecodeStream stream, ConstantPool cp, ResolvedJavaMethod method, int bci, int opcode) {
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
                cpi = cpiFunction.apply(opcode, cpi);
                if (format) {
                    buf.append(String.format("#%-10d // %s", cpi, type.toJavaName()));
                } else {
                    buf.append(cpi).append(" // ").append(type.getName());
                }
                break;
            }
            case GETSTATIC      :
            case PUTSTATIC      :
            case GETFIELD       :
            case PUTFIELD       : {
                int cpi = stream.readCPI();
                JavaField field = cp.lookupField(cpi, method, opcode);
                cpi = cpiFunction.apply(opcode, cpi);
                if (format) {
                    String fieldDesc = field.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? field.format("%n:%T") : field.format("%H.%n:%T");
                    buf.append(String.format("#%-10d // %s", cpi, fieldDesc));
                } else {
                    buf.append(cpi)
                       .append(" // ")
                       .append(field.getDeclaringClass().getName())
                       .append('.')
                       .append(field.getName())
                       .append(':')
                       .append(field.getType().getName());
                }
                break;
            }
            case INVOKEVIRTUAL  :
            case INVOKESPECIAL  :
            case INVOKESTATIC   : {
                int cpi = stream.readCPI();
                JavaMethod callee = cp.lookupMethod(cpi, opcode);
                cpi = cpiFunction.apply(opcode, cpi);
                if (format) {
                    String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? callee.format("%n:(%P)%R") : callee.format("%H.%n:(%P)%R");
                    buf.append(String.format("#%-10d // %s", cpi, calleeDesc));
                } else {
                    buf.append(cpi)
                       .append(" // ")
                       .append(method.getDeclaringClass().getName())
                       .append('.')
                       .append(method.getName())
                       .append(method.getSignature().toMethodDescriptor());
                }
                break;
            }
            case INVOKEINTERFACE: {
                int cpi = stream.readCPI();
                JavaMethod callee = cp.lookupMethod(cpi, opcode);
                cpi = cpiFunction.apply(opcode, cpi);
                if (format) {
                    String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? callee.format("%n:(%P)%R") : callee.format("%H.%n:(%P)%R");
                    buf.append(String.format("#%-10s // %s", cpi + ", " + stream.readUByte(bci + 3), calleeDesc));
                } else {
                    buf.append(cpi)
                            .append(',')
                            .append(stream.readUByte(bci + 3))
                            .append(" // ")
                            .append(method.getDeclaringClass().getName())
                            .append('.')
                            .append(method.getName())
                            .append(method.getSignature().toMethodDescriptor());
                }
                break;
            }
            case INVOKEDYNAMIC: {
                int cpi = stream.readCPI4();
                JavaMethod callee = cp.lookupMethod(cpi, opcode);
                cpi = cpiFunction.apply(opcode, cpi);
                if (format) {
                    String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? callee.format("%n:(%P)%R") : callee.format("%H.%n:(%P)%R");
                    buf.append(String.format("#%-10d // %s", cpi, calleeDesc));
                } else {
                    buf.append(cpi)
                            .append(" // ")
                            .append(method.getDeclaringClass().getName())
                            .append('.')
                            .append(method.getName())
                            .append(method.getSignature().toMethodDescriptor());
                }
                break;
            }
            case LDC            :
            case LDC_W          :
            case LDC2_W         : {
                int cpi = stream.readCPI();
                Object constant = cp.lookupConstant(cpi, false);
                String desc = null;
                if (constant == null) {
                    desc = "<unresolved>";
                } else if (constant instanceof JavaConstant) {
                    JavaConstant c = ((JavaConstant) constant);
                    desc = c.toValueString();
                } else {
                    desc = constant.toString();
                }
                if (!multiline) {
                    desc = desc.replaceAll("\\n", "");
                }
                cpi = cpiFunction.apply(opcode, cpi);
                if (format) {
                    buf.append(String.format("#%-10d // %s", cpi, desc));
                } else {
                    buf.append(cpi).append(" // ").append(desc);
                }
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
                buf.append(stream.readLocalIndex());
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
                buf.append(stream.readBranchDest());
                break;
            }
            case LOOKUPSWITCH   :
            case TABLESWITCH    : {
                BytecodeSwitch bswitch = opcode == LOOKUPSWITCH ? new BytecodeLookupSwitch(stream, bci) : new BytecodeTableSwitch(stream, bci);
                if (multiline) {
                    buf.append("{ // ").append(bswitch.numberOfCases());
                    for (int i = 0; i < bswitch.numberOfCases(); i++) {
                        if (format) {
                            buf.append(String.format("%n           %7d: %d", bswitch.keyAt(i), bswitch.targetAt(i)));
                        } else {
                            buf.append(newLine)
                               .append("           ")
                               .append(bswitch.keyAt(i))
                               .append(':')
                               .append(bswitch.targetAt(i));
                        }
                    }
                    if (format) {
                        buf.append(String.format("%n           default: %d", bswitch.defaultTarget()));
                        buf.append(String.format("%n      }"));
                    } else {
                        buf.append(newLine)
                           .append("           default:")
                           .append(bswitch.defaultTarget())
                           .append(newLine).append('}');
                    }
                } else {
                    buf.append("[").append(bswitch.numberOfCases()).append("] {");
                    for (int i = 0; i < bswitch.numberOfCases(); i++) {
                        buf.append(bswitch.keyAt(i)).append(": ").append(bswitch.targetAt(i));
                        if (i != bswitch.numberOfCases() - 1) {
                            buf.append(", ");
                        }
                    }
                    buf.append("} default: ").append(bswitch.defaultTarget());
                }
                break;
            }
            case NEWARRAY       : {
                int typecode = stream.readLocalIndex();
                // Checkstyle: stop
                switch (typecode) {
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
                cpi = cpiFunction.apply(opcode, cpi);
                if (format) {
                    buf.append(String.format("#%-10s // %s", cpi + ", " + stream.readUByte(bci + 3), type.toJavaName()));
                } else {
                    buf.append(cpi).append(',').append(stream.readUByte(bci + 3)).append(type.getName());
                }
                break;
            }
        }
        // @formatter:on
    }

    public static JavaMethod getInvokedMethodAt(ResolvedJavaMethod method, int invokeBci) {
        if (method.getCode() == null) {
            return null;
        }
        ConstantPool cp = method.getConstantPool();
        BytecodeStream stream = new BytecodeStream(method.getCode());
        int opcode = stream.currentBC();
        while (opcode != Bytecodes.END) {
            int bci = stream.currentBCI();
            if (bci == invokeBci) {
                if (stream.nextBCI() > bci + 1) {
                    switch (opcode) {
                        case INVOKEVIRTUAL:
                        case INVOKESPECIAL:
                        case INVOKESTATIC:
                        case INVOKEINTERFACE: {
                            int cpi = stream.readCPI();
                            JavaMethod callee = cp.lookupMethod(cpi, opcode);
                            return callee;
                        }
                        case INVOKEDYNAMIC: {
                            int cpi = stream.readCPI4();
                            JavaMethod callee = cp.lookupMethod(cpi, opcode);
                            return callee;
                        }
                        default:
                            throw new InternalError(BytecodeDisassembler.disassembleOne(method, invokeBci));
                    }
                }
            }
            stream.next();
            opcode = stream.currentBC();
        }
        return null;
    }

    public static int getBytecodeAt(ResolvedJavaMethod method, int invokeBci) {
        if (method.getCode() == null) {
            return -1;
        }
        BytecodeStream stream = new BytecodeStream(method.getCode());
        int opcode = stream.currentBC();
        while (opcode != Bytecodes.END) {
            int bci = stream.currentBCI();
            if (bci == invokeBci) {
                return opcode;
            }
            stream.next();
            opcode = stream.currentBC();
        }
        return -1;
    }
}
