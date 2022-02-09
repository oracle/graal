/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.java;

import static org.graalvm.compiler.bytecode.Bytecodes.AALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.AASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ACONST_NULL;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ANEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.ARETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ARRAYLENGTH;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ATHROW;
import static org.graalvm.compiler.bytecode.Bytecodes.BALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.BASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.BIPUSH;
import static org.graalvm.compiler.bytecode.Bytecodes.BREAKPOINT;
import static org.graalvm.compiler.bytecode.Bytecodes.CALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.CASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.CHECKCAST;
import static org.graalvm.compiler.bytecode.Bytecodes.D2F;
import static org.graalvm.compiler.bytecode.Bytecodes.D2I;
import static org.graalvm.compiler.bytecode.Bytecodes.D2L;
import static org.graalvm.compiler.bytecode.Bytecodes.DADD;
import static org.graalvm.compiler.bytecode.Bytecodes.DALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DCMPG;
import static org.graalvm.compiler.bytecode.Bytecodes.DCMPL;
import static org.graalvm.compiler.bytecode.Bytecodes.DCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.DNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.DREM;
import static org.graalvm.compiler.bytecode.Bytecodes.DRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2_X1;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2_X2;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP_X1;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP_X2;
import static org.graalvm.compiler.bytecode.Bytecodes.F2D;
import static org.graalvm.compiler.bytecode.Bytecodes.F2I;
import static org.graalvm.compiler.bytecode.Bytecodes.F2L;
import static org.graalvm.compiler.bytecode.Bytecodes.FADD;
import static org.graalvm.compiler.bytecode.Bytecodes.FALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FCMPG;
import static org.graalvm.compiler.bytecode.Bytecodes.FCMPL;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.FNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.FREM;
import static org.graalvm.compiler.bytecode.Bytecodes.FRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.GETFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.GETSTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO_W;
import static org.graalvm.compiler.bytecode.Bytecodes.I2B;
import static org.graalvm.compiler.bytecode.Bytecodes.I2C;
import static org.graalvm.compiler.bytecode.Bytecodes.I2D;
import static org.graalvm.compiler.bytecode.Bytecodes.I2F;
import static org.graalvm.compiler.bytecode.Bytecodes.I2L;
import static org.graalvm.compiler.bytecode.Bytecodes.I2S;
import static org.graalvm.compiler.bytecode.Bytecodes.IADD;
import static org.graalvm.compiler.bytecode.Bytecodes.IALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.IAND;
import static org.graalvm.compiler.bytecode.Bytecodes.IASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_4;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_5;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_M1;
import static org.graalvm.compiler.bytecode.Bytecodes.IDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.IFEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IFGE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFGT;
import static org.graalvm.compiler.bytecode.Bytecodes.IFLE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFLT;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNONNULL;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNULL;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ACMPEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ACMPNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPGE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPGT;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPLE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPLT;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IINC;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.IMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.INEG;
import static org.graalvm.compiler.bytecode.Bytecodes.INSTANCEOF;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;
import static org.graalvm.compiler.bytecode.Bytecodes.IOR;
import static org.graalvm.compiler.bytecode.Bytecodes.IREM;
import static org.graalvm.compiler.bytecode.Bytecodes.IRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ISHL;
import static org.graalvm.compiler.bytecode.Bytecodes.ISHR;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ISUB;
import static org.graalvm.compiler.bytecode.Bytecodes.IUSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.IXOR;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR_W;
import static org.graalvm.compiler.bytecode.Bytecodes.L2D;
import static org.graalvm.compiler.bytecode.Bytecodes.L2F;
import static org.graalvm.compiler.bytecode.Bytecodes.L2I;
import static org.graalvm.compiler.bytecode.Bytecodes.LADD;
import static org.graalvm.compiler.bytecode.Bytecodes.LALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LAND;
import static org.graalvm.compiler.bytecode.Bytecodes.LASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LCMP;
import static org.graalvm.compiler.bytecode.Bytecodes.LCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC2_W;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC_W;
import static org.graalvm.compiler.bytecode.Bytecodes.LDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.LNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.LOOKUPSWITCH;
import static org.graalvm.compiler.bytecode.Bytecodes.LOR;
import static org.graalvm.compiler.bytecode.Bytecodes.LREM;
import static org.graalvm.compiler.bytecode.Bytecodes.LRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.LSHL;
import static org.graalvm.compiler.bytecode.Bytecodes.LSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.LUSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.LXOR;
import static org.graalvm.compiler.bytecode.Bytecodes.MONITORENTER;
import static org.graalvm.compiler.bytecode.Bytecodes.MONITOREXIT;
import static org.graalvm.compiler.bytecode.Bytecodes.MULTIANEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.NEW;
import static org.graalvm.compiler.bytecode.Bytecodes.NEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.NOP;
import static org.graalvm.compiler.bytecode.Bytecodes.POP;
import static org.graalvm.compiler.bytecode.Bytecodes.POP2;
import static org.graalvm.compiler.bytecode.Bytecodes.PUTFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.PUTSTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.RET;
import static org.graalvm.compiler.bytecode.Bytecodes.RETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.SALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.SASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.SIPUSH;
import static org.graalvm.compiler.bytecode.Bytecodes.SWAP;
import static org.graalvm.compiler.bytecode.Bytecodes.TABLESWITCH;
import static org.graalvm.compiler.bytecode.Bytecodes.WIDE;
import static org.graalvm.compiler.core.common.GraalOptions.SupportJsrBytecodes;
import static org.graalvm.compiler.java.BciBlockMapping.Options.DuplicateIrreducibleLoops;
import static org.graalvm.compiler.java.BciBlockMapping.Options.MaxDuplicationFactor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeLookupSwitch;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.bytecode.BytecodeSwitch;
import org.graalvm.compiler.bytecode.BytecodeTableSwitch;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaMethod;

/**
 * Builds a mapping between bytecodes and basic blocks and builds a conservative control flow graph
 * (CFG). It makes one linear pass over the bytecodes to build the CFG where it detects block
 * headers and connects them.
 * <p>
 * It also creates exception dispatch blocks for exception handling. These blocks are between a
 * bytecode that might throw an exception, and the actual exception handler entries, and are later
 * used to create the type checks with the exception handler catch types. If a bytecode is covered
 * by an exception handler, this bytecode ends the basic block. This guarantees that a) control flow
 * cannot be transferred to an exception dispatch block in the middle of a block, and b) that every
 * block has at most one exception dispatch block (which is always the last entry in the successor
 * list).
 * <p>
 * If a bytecode is covered by multiple exception handlers, a chain of exception dispatch blocks is
 * created so that multiple exception handler types can be checked.
 * <p>
 * Note that exception unwinds, i.e., bytecodes that can throw an exception but the exception is not
 * handled in this method, do not end a basic block. Not modeling the exception unwind block reduces
 * the complexity of the CFG, and there is no algorithm yet where the exception unwind block would
 * matter.
 * <p>
 * The class also handles subroutines (jsr and ret bytecodes): subroutines are inlined by
 * duplicating the subroutine blocks. This is limited to simple, structured subroutines with a
 * maximum subroutine nesting of 4. Otherwise, a bailout is thrown.
 * <p>
 * Loops in the methods are detected. If a method contains an irreducible loop (a loop with more
 * than one entry), a bailout is thrown or block duplication is attempted to make the loop
 * reducible. This simplifies the compiler later on since only structured loops need to be
 * supported.
 * <p>
 * A data flow analysis computes the live local variables from the point of view of the interpreter.
 * The result is used later to prune frame states, i.e., remove local variable entries that are
 * guaranteed to be never used again (even in the case of deoptimization).
 * <p>
 * The algorithms and analysis in this class are conservative and do not use any assumptions or
 * profiling information.
 */
public class BciBlockMapping implements JavaMethodContext {
    public static class Options {
        @Option(help = "When enabled, some limited amount of duplication will be performed in order compile code containing irreducible loops.")//
        public static final OptionKey<Boolean> DuplicateIrreducibleLoops = new OptionKey<>(true);
        @Option(help = "Amount of block duplication to perform as factor of original number of blocks to handle irreducible loops before bailing out.", type = OptionType.Expert)//
        public static final OptionKey<Double> MaxDuplicationFactor = new OptionKey<>(2.0);
    }

    protected static final int UNASSIGNED_ID = -1;

    public static class BciBlock implements Cloneable {

        int id = UNASSIGNED_ID;
        final int startBci;
        private int endBci; // The bci of the last bytecode in the block
        private boolean isExceptionEntry;
        private boolean isLoopHeader;
        int loopId;
        List<BciBlock> successors;
        private int predecessorCount;

        private boolean visited;
        private boolean active;
        BitSet loops;
        JSRData jsrData;
        List<TraversalStep> loopIdChain;
        boolean duplicate;

        public static class JSRData implements Cloneable {
            public EconomicMap<JsrScope, BciBlock> jsrAlternatives;
            public JsrScope jsrScope = JsrScope.EMPTY_SCOPE;
            public BciBlock jsrSuccessor;
            public int jsrReturnBci;
            public BciBlock retSuccessor;
            public boolean endsWithRet = false;

            public JSRData copy() {
                try {
                    return (JSRData) this.clone();
                } catch (CloneNotSupportedException e) {
                    return null;
                }
            }
        }

        public BciBlock(int startBci) {
            this.startBci = startBci;
            this.successors = new ArrayList<>();
            this.loops = new BitSet();
        }

        protected BciBlock(int startBci, int endBci) {
            this(startBci);
            this.endBci = endBci;
        }

        public boolean bciUnique() {
            return jsrData == null && !duplicate;
        }

        public int getStartBci() {
            return startBci;
        }

        public int getEndBci() {
            return endBci;
        }

        public void setEndBci(int bci) {
            endBci = bci;
        }

        public BitSet getLoops() {
            return loops;
        }

        public BciBlock exceptionDispatchBlock() {
            if (successors.size() > 0 && successors.get(successors.size() - 1) instanceof ExceptionDispatchBlock) {
                return successors.get(successors.size() - 1);
            }
            return null;
        }

        public int getId() {
            return id;
        }

        public int getPredecessorCount() {
            return this.predecessorCount;
        }

        public int numNormalSuccessors() {
            if (exceptionDispatchBlock() != null) {
                return successors.size() - 1;
            }
            return successors.size();
        }

        public BciBlock copy() {
            try {
                BciBlock block = (BciBlock) super.clone();
                if (block.jsrData != null) {
                    block.jsrData = block.jsrData.copy();
                }
                block.successors = new ArrayList<>(successors);
                block.loops = (BitSet) block.loops.clone();
                return block;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        public BciBlock duplicate() {
            try {
                BciBlock block = (BciBlock) super.clone();
                if (block.jsrData != null) {
                    throw new PermanentBailoutException("Can not duplicate block with JSR data");
                }
                block.successors = new ArrayList<>(successors);
                block.loops = new BitSet();
                block.loopId = 0;
                block.id = UNASSIGNED_ID;
                block.isLoopHeader = false;
                block.visited = false;
                block.active = false;
                block.predecessorCount = 0;
                block.loopIdChain = null;
                block.duplicate = true;
                return block;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("B").append(getId());
            sb.append('[').append(startBci).append("..").append(endBci);
            if (isLoopHeader || isExceptionEntry || this instanceof ExceptionDispatchBlock) {
                sb.append(' ');
                if (isLoopHeader) {
                    sb.append('L');
                }
                if (isExceptionEntry) {
                    sb.append('!');
                } else if (this instanceof ExceptionDispatchBlock) {
                    sb.append("<!>");
                }
            }
            sb.append(']');
            if (duplicate) {
                sb.append(" (duplicate)");
            }
            return sb.toString();
        }

        public boolean isLoopHeader() {
            return isLoopHeader;
        }

        public boolean isExceptionEntry() {
            return isExceptionEntry;
        }

        public void setIsExceptionEntry() {
            isExceptionEntry = true;
        }

        public BciBlock getSuccessor(int index) {
            return successors.get(index);
        }

        public int getLoopId() {
            return loopId;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        private JSRData getOrCreateJSRData() {
            if (jsrData == null) {
                jsrData = new JSRData();
            }
            return jsrData;
        }

        void setEndsWithRet() {
            getOrCreateJSRData().endsWithRet = true;
        }

        public JsrScope getJsrScope() {
            if (this.jsrData == null) {
                return JsrScope.EMPTY_SCOPE;
            } else {
                return jsrData.jsrScope;
            }
        }

        public boolean endsWithRet() {
            if (this.jsrData == null) {
                return false;
            } else {
                return jsrData.endsWithRet;
            }
        }

        void setRetSuccessor(BciBlock bciBlock) {
            this.getOrCreateJSRData().retSuccessor = bciBlock;
        }

        public BciBlock getRetSuccessor() {
            if (this.jsrData == null) {
                return null;
            } else {
                return jsrData.retSuccessor;
            }
        }

        public BciBlock getJsrSuccessor() {
            if (this.jsrData == null) {
                return null;
            } else {
                return jsrData.jsrSuccessor;
            }
        }

        public int getJsrReturnBci() {
            if (this.jsrData == null) {
                return -1;
            } else {
                return jsrData.jsrReturnBci;
            }
        }

        public EconomicMap<JsrScope, BciBlock> getJsrAlternatives() {
            if (this.jsrData == null) {
                return null;
            } else {
                return jsrData.jsrAlternatives;
            }
        }

        public void initJsrAlternatives() {
            JSRData data = this.getOrCreateJSRData();
            if (data.jsrAlternatives == null) {
                data.jsrAlternatives = EconomicMap.create(Equivalence.DEFAULT);
            }
        }

        void setJsrScope(JsrScope nextScope) {
            this.getOrCreateJSRData().jsrScope = nextScope;
        }

        void setJsrSuccessor(BciBlock clone) {
            this.getOrCreateJSRData().jsrSuccessor = clone;
        }

        void setJsrReturnBci(int bci) {
            this.getOrCreateJSRData().jsrReturnBci = bci;
        }

        public int getSuccessorCount() {
            return successors.size();
        }

        public List<BciBlock> getSuccessors() {
            return successors;
        }

        void setId(int i) {
            this.id = i;
        }

        public void addSuccessor(BciBlock sux) {
            successors.add(sux);
            sux.predecessorCount++;
        }

        public void clearSucccessors() {
            for (BciBlock sux : successors) {
                sux.predecessorCount--;
            }
            successors.clear();
        }

        /**
         * A block is considered to be an instruction block if during parsing nodes the bytecodes
         * within the range [startBci, endBci] are generated when processing this block by
         * BytecodeProcess.processBlock.
         */
        public boolean isInstructionBlock() {
            return true;
        }

        public void getDebugProperties(Map<String, ? super Object> properties) {
            properties.put("assignedId", this.getId());
            properties.put("startBci", this.getStartBci());
            properties.put("endBci", this.getEndBci());
            properties.put("isExceptionEntry", this.isExceptionEntry());
            properties.put("isLoopHeader", this.isLoopHeader());
            properties.put("loopId", this.getLoopId());
            properties.put("loops", this.getLoops());
            properties.put("predecessorCount", this.getPredecessorCount());
            properties.put("active", this.active);
            properties.put("visited", this.visited);
            properties.put("duplicate", this.duplicate);
            // JSRData?
        }
    }

    public static class ExceptionDispatchBlock extends BciBlock {
        public final ExceptionHandler handler;
        public final int deoptBci;

        /**
         * Constructor for a normal dispatcher.
         */
        protected ExceptionDispatchBlock(ExceptionHandler handler, int deoptBci) {
            super(handler.getHandlerBCI(), handler.getHandlerBCI());
            this.deoptBci = deoptBci;
            this.handler = handler;
        }

        /**
         * Constructor for the method unwind dispatcher.
         */
        protected ExceptionDispatchBlock(int deoptBci) {
            super(deoptBci, deoptBci);
            this.deoptBci = deoptBci;
            this.handler = null;
        }

        @Override
        public void setEndBci(int bci) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public void setIsExceptionEntry() {
            throw GraalError.shouldNotReachHere("Dispatch block cannot be exception entry.");
        }

        @Override
        public boolean isInstructionBlock() {
            return false;
        }

        @Override
        public void getDebugProperties(Map<String, ? super Object> properties) {
            super.getDebugProperties(properties);
            properties.put("deoptBci", this.deoptBci);
            if (this.handler != null) {
                properties.put("catch type", this.handler.getCatchType());
            }
        }
    }

    private static class TraversalStep {
        private final TraversalStep pred;
        private final BciBlock block;
        private int currentSuccessorIndex;

        TraversalStep(TraversalStep pred, BciBlock block) {
            this.pred = pred;
            this.block = block;
            this.currentSuccessorIndex = 0;
        }

        TraversalStep(BciBlock block) {
            this(null, block);
        }

        @Override
        public String toString() {
            if (pred == null) {
                return "TraversalStep{block=" + block +
                                ", currentSuccessorIndex=" + currentSuccessorIndex +
                                '}';
            }
            return "TraversalStep{" +
                            "pred=" + pred +
                            ", block=" + block +
                            ", currentSuccessorIndex=" + currentSuccessorIndex +
                            '}';
        }
    }

    private static final class DuplicationTraversalStep extends TraversalStep {
        private final BciBlock loopHeader;
        private final EconomicMap<BciBlock, BciBlock> duplicationMap;

        DuplicationTraversalStep(TraversalStep pred, BciBlock block, BciBlock loopHeader) {
            super(pred, block);
            this.loopHeader = loopHeader;
            this.duplicationMap = EconomicMap.create();
        }

        DuplicationTraversalStep(DuplicationTraversalStep pred, BciBlock block) {
            super(pred, block);
            this.loopHeader = pred.loopHeader;
            this.duplicationMap = pred.duplicationMap;
        }

        @Override
        public String toString() {
            return super.toString() + " (duplicating " + loopHeader + ")";
        }
    }

    /**
     * The blocks found in this method, in reverse postorder.
     */
    private BciBlock[] blocks;
    protected BciBlock[] blockMap;
    public final Bytecode code;
    public boolean hasJsrBytecodes;

    protected final ExceptionHandler[] exceptionHandlers;
    protected BitSet[] bciExceptionHandlerIDs;
    private BciBlock startBlock;
    private BciBlock[] loopHeaders;

    private static final int LOOP_HEADER_MAX_CAPACITY = 1 << 12;
    private static final int LOOP_HEADER_INITIAL_CAPACITY = 4;

    protected int blocksNotYetAssignedId;
    private final DebugContext debug;
    private int postJsrBlockCount;
    private int newDuplicateBlocks;
    private int duplicateBlocks;

    /**
     * Creates a new BlockMap instance from {@code code}.
     */
    protected BciBlockMapping(Bytecode code, DebugContext debug) {
        this.code = code;
        this.debug = debug;
        this.exceptionHandlers = code.getExceptionHandlers();
        this.blockMap = new BciBlock[code.getCodeSize()];
    }

    public BciBlock[] getBlocks() {
        return this.blocks;
    }

    public BitSet getBciExceptionHandlerIDs(int bci) {
        assert bciExceptionHandlerIDs != null;
        return bciExceptionHandlerIDs[bci];
    }

    public BciBlock getHandlerBlock(int handlerID) {
        int handlerBci = exceptionHandlers[handlerID].getHandlerBCI();
        assert blockMap[handlerBci] != null;
        return blockMap[handlerBci];
    }

    public boolean bciUnique() {
        for (BciBlock block : this.blocks) {
            if (!block.bciUnique()) {
                return false;
            }
        }
        return true;
    }

    /**
     * After local liveness has been computed, some metadata no longer needs to be retained.
     */
    public void clearLivenessMetadata() {
        blockMap = null;
        bciExceptionHandlerIDs = null;
    }

    /**
     * Builds the block map and conservative CFG and numbers blocks.
     */
    public void build(BytecodeStream stream, OptionValues options, boolean splitExceptionRanges) {
        computeBciExceptionHandlerIDs(stream);
        makeExceptionEntries(splitExceptionRanges);
        iterateOverBytecodes(stream);
        startBlock = blockMap[0];
        if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
            debug.dump(DebugContext.INFO_LEVEL, this, code.getMethod().format("After iterateOverBytecodes %f %R %H.%n(%P)"));
        }
        if (hasJsrBytecodes) {
            if (!SupportJsrBytecodes.getValue(options)) {
                throw new JsrNotSupportedBailout("jsr/ret parsing disabled");
            }
            createJsrAlternatives(startBlock);
            if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
                debug.dump(DebugContext.INFO_LEVEL, this, code.getMethod().format("After createJsrAlternatives %f %R %H.%n(%P)"));
            }
        }
        postJsrBlockCount = blocksNotYetAssignedId;
        if (debug.isLogEnabled()) {
            this.log(blockMap, "Before BlockOrder");
        }
        computeBlockOrder();
        if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
            debug.dump(DebugContext.INFO_LEVEL, this, code.getMethod().format("After computeBlockOrder %f %R %H.%n(%P)"));
        }

        assert verify();

        if (debug.isLogEnabled()) {
            this.log(blockMap, "Before LivenessAnalysis");
        }
    }

    protected boolean verify() {
        for (BciBlock block : blocks) {
            assert blocks[block.getId()] == block;
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BciBlock sux = block.getSuccessor(i);
                if (sux instanceof ExceptionDispatchBlock) {
                    assert i == block.getSuccessorCount() - 1 : "Only one exception handler allowed, and it must be last in successors list";
                }
            }
        }

        return true;
    }

    /**
     * For each BCI corresponding to an instruction, compute which execution handlers it can be
     * directed to.
     */
    private void computeBciExceptionHandlerIDs(BytecodeStream stream) {
        bciExceptionHandlerIDs = new BitSet[code.getCodeSize()];
        /* Initialize BitSets for all bcis corresponding to bytecodes. */
        stream.setBCI(0);
        while (stream.currentBC() != Bytecodes.END) {
            int bci = stream.currentBCI();
            bciExceptionHandlerIDs[bci] = new BitSet();
            stream.next();
        }

        /* Process which handlers can be taken from each bci. */
        for (int handlerID = exceptionHandlers.length - 1; handlerID >= 0; handlerID--) {
            ExceptionHandler h = exceptionHandlers[handlerID];
            for (int bci = h.getStartBCI(); bci < h.getEndBCI(); bci++) {
                BitSet currentIDs = bciExceptionHandlerIDs[bci];
                if (currentIDs == null) {
                    /* No instruction for this bci. */
                    continue;
                }
                if (h.isCatchAll()) {
                    /*
                     * Discard all information about prior exception handlers, since they can never
                     * be reached.
                     */
                    currentIDs.clear();
                }
                currentIDs.set(handlerID);
            }
        }
    }

    /**
     * Exception ranges don't have to match up with actual bytecodes so walk through the blockMap to
     * find the real bytecode starts.
     */
    private int findConcreteBci(int bci) {
        assert bciExceptionHandlerIDs != null;
        int current = bci;
        while (current < bciExceptionHandlerIDs.length) {
            if (bciExceptionHandlerIDs[current] != null) {
                return current;
            }
            current++;
        }
        return bciExceptionHandlerIDs.length;
    }

    /**
     * Wrapper around makeBlock. This serves as a hook for subclasses.
     */
    protected BciBlock startNewBlock(int bci) {
        return makeBlock(bci);
    }

    /**
     * Makes exception entries and splits blocks at exception handlers if requested.
     *
     * @return blocks that were requested to be the start of new blocks.
     */
    protected Set<BciBlock> makeExceptionEntries(boolean splitRanges) {
        Set<BciBlock> requestedBlockStarts = new HashSet<>();
        // start basic blocks at all exception handler blocks and mark them as exception entries
        for (int i = 0; i < exceptionHandlers.length; i++) {
            ExceptionHandler h = exceptionHandlers[i];
            BciBlock xhandler = startNewBlock(h.getHandlerBCI());
            xhandler.setIsExceptionEntry();
            requestedBlockStarts.add(xhandler);

            /*
             * Split blocks at handler boundaries to help improve local liveness precision when
             * asynchronous exceptions can occur.
             */
            if (splitRanges) {
                int startBci = findConcreteBci(h.getStartBCI());
                assert startBci < bciExceptionHandlerIDs.length;
                requestedBlockStarts.add(startNewBlock(startBci));
                int endBci = findConcreteBci(h.getEndBCI());
                if (endBci < bciExceptionHandlerIDs.length) {
                    requestedBlockStarts.add(startNewBlock(endBci));
                }
            }
        }
        return requestedBlockStarts;
    }

    /**
     * Check whether this bci should be the start of a new block.
     */
    protected boolean isStartOfNewBlock(BciBlock current, int bci) {
        /*
         * A new block must be created if either there is not a block currently being processed this
         * bci can be appended to (current == null) or if this bci is has an explicit predecessor
         * from another block (blockMap[bci] != null).
         */
        return current == null || blockMap[bci] != null;
    }

    /**
     * Retrieve the instruction block corresponding to this bci. The criteria for being an
     * instruction block is defined at BlockMap.isInstructionBlock.
     */
    protected BciBlock getInstructionBlock(int bci) {
        assert blockMap[bci].isInstructionBlock();
        return blockMap[bci];
    }

    private void iterateOverBytecodes(BytecodeStream stream) {
        // iterate over the bytecodes top to bottom.
        // mark the entrypoints of basic blocks and build lists of successors for
        // all bytecodes that end basic blocks (i.e. goto, ifs, switches, throw, jsr, returns, ret)
        BciBlock current = null;
        stream.setBCI(0);
        while (stream.currentBC() != Bytecodes.END) {
            int bci = stream.currentBCI();

            if (isStartOfNewBlock(current, bci)) {
                BciBlock b = makeBlock(bci);
                if (current != null) {
                    addSuccessor(current.getEndBci(), b);
                }
                current = b;
            }
            blockMap[bci] = current;
            current = getInstructionBlock(bci);
            current.setEndBci(bci);

            switch (stream.currentBC()) {
                case IRETURN: // fall through
                case LRETURN: // fall through
                case FRETURN: // fall through
                case DRETURN: // fall through
                case ARETURN: // fall through
                case RETURN: {
                    current = null;
                    break;
                }
                case ATHROW: {
                    current = null;
                    ExceptionDispatchBlock handler = handleExceptions(bci, true, false);
                    if (handler != null) {
                        addSuccessor(bci, handler);
                    }
                    break;
                }
                case IFEQ:      // fall through
                case IFNE:      // fall through
                case IFLT:      // fall through
                case IFGE:      // fall through
                case IFGT:      // fall through
                case IFLE:      // fall through
                case IF_ICMPEQ: // fall through
                case IF_ICMPNE: // fall through
                case IF_ICMPLT: // fall through
                case IF_ICMPGE: // fall through
                case IF_ICMPGT: // fall through
                case IF_ICMPLE: // fall through
                case IF_ACMPEQ: // fall through
                case IF_ACMPNE: // fall through
                case IFNULL:    // fall through
                case IFNONNULL: {
                    current = null;
                    addSuccessor(bci, makeBlock(stream.readBranchDest()));
                    addSuccessor(bci, makeBlock(stream.nextBCI()));
                    break;
                }
                case GOTO:
                case GOTO_W: {
                    current = null;
                    addSuccessor(bci, makeBlock(stream.readBranchDest()));
                    break;
                }
                case TABLESWITCH: {
                    current = null;
                    addSwitchSuccessors(bci, new BytecodeTableSwitch(stream, bci));
                    break;
                }
                case LOOKUPSWITCH: {
                    current = null;
                    addSwitchSuccessors(bci, new BytecodeLookupSwitch(stream, bci));
                    break;
                }
                case JSR:
                case JSR_W: {
                    hasJsrBytecodes = true;
                    int target = stream.readBranchDest();
                    if (target == 0) {
                        throw new JsrNotSupportedBailout("jsr target bci 0 not allowed");
                    }
                    BciBlock b1 = makeBlock(target);
                    current.setJsrSuccessor(b1);
                    current.setJsrReturnBci(stream.nextBCI());
                    current = null;
                    addSuccessor(bci, b1);
                    break;
                }
                case RET: {
                    current.setEndsWithRet();
                    current = null;
                    break;
                }
                case INVOKEINTERFACE:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEVIRTUAL:
                case INVOKEDYNAMIC: {
                    current = null;
                    addInvokeNormalSuccessor(bci, makeBlock(stream.nextBCI()));
                    ExceptionDispatchBlock handler = handleExceptions(bci, true, true);
                    if (handler != null) {
                        addSuccessor(bci, handler);
                    }
                    break;
                }
                case IDIV:
                case IREM:
                case LDIV:
                case LREM:
                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                case IALOAD:
                case LALOAD:
                case FALOAD:
                case DALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                case ARRAYLENGTH:
                case CHECKCAST:
                case INSTANCEOF:
                case NEW:
                case NEWARRAY:
                case ANEWARRAY:
                case MULTIANEWARRAY:
                case PUTSTATIC:
                case GETSTATIC:
                case PUTFIELD:
                case GETFIELD:
                case LDC:
                case LDC_W:
                case LDC2_W:
                case MONITORENTER: {
                    /*
                     * All bytecodes that can trigger lazy class initialization via a
                     * ClassInitializationPlugin (allocations, static field access) must be listed
                     * because the class initializer is allowed to throw an exception, which
                     * requires proper exception handling.
                     */
                    ExceptionDispatchBlock handler = handleExceptions(bci, true, false);
                    if (handler != null) {
                        current = null;
                        addSuccessor(bci, makeBlock(stream.nextBCI()));
                        addSuccessor(bci, handler);
                    }
                    break;
                }

                case NOP:
                case ACONST_NULL:
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case LCONST_0:
                case LCONST_1:
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                case DCONST_0:
                case DCONST_1:
                case BIPUSH:
                case SIPUSH:
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                case ALOAD:
                case ILOAD_0:
                case ILOAD_1:
                case ILOAD_2:
                case ILOAD_3:
                case LLOAD_0:
                case LLOAD_1:
                case LLOAD_2:
                case LLOAD_3:
                case FLOAD_0:
                case FLOAD_1:
                case FLOAD_2:
                case FLOAD_3:
                case DLOAD_0:
                case DLOAD_1:
                case DLOAD_2:
                case DLOAD_3:
                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3:
                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE:
                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3:
                case LSTORE_0:
                case LSTORE_1:
                case LSTORE_2:
                case LSTORE_3:
                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3:
                case DSTORE_0:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3:
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3:
                case POP:
                case POP2:
                case DUP:
                case DUP_X1:
                case DUP_X2:
                case DUP2:
                case DUP2_X1:
                case DUP2_X2:
                case SWAP:
                case IADD:
                case LADD:
                case FADD:
                case DADD:
                case ISUB:
                case LSUB:
                case FSUB:
                case DSUB:
                case IMUL:
                case LMUL:
                case FMUL:
                case DMUL:
                case FDIV:
                case DDIV:
                case FREM:
                case DREM:
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG:
                case ISHL:
                case LSHL:
                case ISHR:
                case LSHR:
                case IUSHR:
                case LUSHR:
                case IAND:
                case LAND:
                case IOR:
                case LOR:
                case IXOR:
                case LXOR:
                case IINC:
                case I2L:
                case I2F:
                case I2D:
                case L2I:
                case L2F:
                case L2D:
                case F2I:
                case F2L:
                case F2D:
                case D2I:
                case D2L:
                case D2F:
                case I2B:
                case I2C:
                case I2S:
                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                case MONITOREXIT:
                    // All stack manipulation, comparison, conversion and arithmetic operators
                    // except for idiv and irem can't throw exceptions so the don't need to connect
                    // exception edges. MONITOREXIT can't throw exceptions in the context of
                    // compiled code because of the structured locking requirement in the parser.
                    break;

                case WIDE:
                case BREAKPOINT:
                default:
                    throw new GraalError("Unhandled bytecode");
            }
            stream.next();
        }
    }

    /**
     * A hook for subclasses to insert additional blocks around a newly created BciBlock.
     */
    @SuppressWarnings("unused")
    protected BciBlock processNewBciBlock(int bci, BciBlock newBlock) {
        /* By default, no additional processing is needed. */
        return newBlock;
    }

    private BciBlock makeBlock(int startBci) {
        BciBlock oldBlock = blockMap[startBci];
        if (oldBlock == null) {
            BciBlock newBlock = new BciBlock(startBci);
            blocksNotYetAssignedId++;
            blockMap[startBci] = newBlock;
            return processNewBciBlock(startBci, newBlock);

        } else if (oldBlock.startBci != startBci) {
            /*
             * Backward branch into the middle of an already processed block. Split prior block and
             * add the correct fall-through successor.
             */
            assert oldBlock.isInstructionBlock();
            BciBlock newBlock = new BciBlock(startBci);
            blocksNotYetAssignedId++;
            newBlock.setEndBci(oldBlock.getEndBci());
            for (BciBlock oldSuccessor : oldBlock.getSuccessors()) {
                newBlock.addSuccessor(oldSuccessor);
            }

            oldBlock.setEndBci(startBci - 1);
            oldBlock.clearSucccessors();
            oldBlock.addSuccessor(newBlock);

            for (int i = startBci; i <= newBlock.getEndBci(); i++) {
                blockMap[i] = newBlock;
            }
            return newBlock;

        } else {
            return oldBlock;
        }
    }

    private void addSwitchSuccessors(int predBci, BytecodeSwitch bswitch) {
        // adds distinct targets to the successor list
        Collection<Integer> targets = new TreeSet<>();
        for (int i = 0; i < bswitch.numberOfCases(); i++) {
            targets.add(bswitch.targetAt(i));
        }
        targets.add(bswitch.defaultTarget());
        for (int targetBci : targets) {
            addSuccessor(predBci, makeBlock(targetBci));
        }
    }

    private void addSuccessor(int predBci, BciBlock sux) {
        BciBlock predecessor = getInstructionBlock(predBci);
        if (sux.isExceptionEntry()) {
            throw new PermanentBailoutException("Exception handler can be reached by both normal and exceptional control flow");
        }
        predecessor.addSuccessor(sux);
    }

    /**
     * Logic for adding an the "normal" invoke successor link.
     */
    protected void addInvokeNormalSuccessor(int invokeBci, BciBlock sux) {
        addSuccessor(invokeBci, sux);
    }

    private final ArrayList<BciBlock> jsrVisited = new ArrayList<>();

    private void createJsrAlternatives(BciBlock block) {
        jsrVisited.add(block);
        JsrScope scope = block.getJsrScope();

        if (block.endsWithRet()) {
            block.setRetSuccessor(blockMap[scope.nextReturnAddress()]);
            block.addSuccessor(block.getRetSuccessor());
            assert block.getRetSuccessor() != block.getJsrSuccessor();
        }
        debug.log("JSR alternatives block %s  sux %s  jsrSux %s  retSux %s  jsrScope %s", block, block.getSuccessors(), block.getJsrSuccessor(), block.getRetSuccessor(), block.getJsrScope());

        if (block.getJsrSuccessor() != null || !scope.isEmpty()) {
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BciBlock successor = block.getSuccessor(i);
                JsrScope nextScope = scope;
                if (successor == block.getJsrSuccessor()) {
                    nextScope = scope.push(block.getJsrReturnBci(), successor);
                }
                if (successor == block.getRetSuccessor()) {
                    nextScope = scope.pop();
                }
                if (!successor.getJsrScope().isPrefixOf(nextScope)) {
                    throw new JsrNotSupportedBailout("unstructured control flow  (" + successor.getJsrScope() + " " + nextScope + ")");
                }
                if (!nextScope.isEmpty()) {
                    BciBlock clone;
                    if (successor.getJsrAlternatives() != null && successor.getJsrAlternatives().containsKey(nextScope)) {
                        clone = successor.getJsrAlternatives().get(nextScope);
                    } else {
                        successor.initJsrAlternatives();
                        clone = successor.copy();
                        blocksNotYetAssignedId++;
                        clone.setJsrScope(nextScope);
                        successor.getJsrAlternatives().put(nextScope, clone);
                    }
                    block.getSuccessors().set(i, clone);
                    if (successor == block.getJsrSuccessor()) {
                        block.setJsrSuccessor(clone);
                    }
                    if (successor == block.getRetSuccessor()) {
                        block.setRetSuccessor(clone);
                    }
                }
            }
        }
        for (BciBlock successor : block.getSuccessors()) {
            if (!jsrVisited.contains(successor) && shouldFollowEdge(successor, scope)) {
                createJsrAlternatives(successor);
            }
        }
    }

    private static boolean shouldFollowEdge(BciBlock successor, JsrScope scope) {
        if (successor instanceof ExceptionDispatchBlock && scope.getJsrEntryBlock() != null) {
            ExceptionDispatchBlock exceptionDispatchBlock = (ExceptionDispatchBlock) successor;
            int bci = scope.getJsrEntryBlock().startBci;
            if (exceptionDispatchBlock.handler.getStartBCI() < bci && bci < exceptionDispatchBlock.handler.getEndBCI()) {
                // Handler covers start of JSR block and the bci before that => don't follow edge.
                return false;
            }
        }

        return true;
    }

    /**
     * A hook for subclasses to insert additional blocks around a newly created
     * ExceptionDispatchBlock.
     */
    @SuppressWarnings("unused")
    protected ExceptionDispatchBlock processNewExceptionDispatchBlock(int bci, boolean isInvoke, ExceptionDispatchBlock handler) {
        /* By default, no additional processing is needed. */
        return handler;
    }

    protected ExceptionDispatchBlock handleExceptions(int bci, boolean processNewBlock, boolean isInvoke) {
        ExceptionDispatchBlock lastHandler = null;
        int dispatchBlocks = 0;

        BitSet handlerIDs = getBciExceptionHandlerIDs(bci);
        assert handlerIDs != null : "missing handlers for bci";
        for (int handlerID = handlerIDs.length(); (handlerID = handlerIDs.previousSetBit(handlerID - 1)) >= 0;) {
            if (handlerIDs.get(handlerID)) {
                /*
                 * We do not reuse exception dispatch blocks, because nested exception handlers
                 * might have problems reasoning about the correct frame state.
                 */
                ExceptionDispatchBlock curHandler = new ExceptionDispatchBlock(exceptionHandlers[handlerID], bci);
                dispatchBlocks++;
                curHandler.addSuccessor(getHandlerBlock(handlerID));
                if (lastHandler != null) {
                    curHandler.addSuccessor(lastHandler);
                }
                lastHandler = curHandler;
            }
        }
        blocksNotYetAssignedId += dispatchBlocks;
        if (processNewBlock) {
            return processNewExceptionDispatchBlock(bci, isInvoke, lastHandler);
        } else {
            return lastHandler;
        }
    }

    private void computeBlockOrder() {
        int maxBlocks = blocksNotYetAssignedId;
        this.blocks = new BciBlock[blocksNotYetAssignedId];
        computeBlockOrder(blockMap[0]);
        int duplicatedBlocks = newDuplicateBlocks + duplicateBlocks;
        if (duplicatedBlocks > 0) {
            debug.log(DebugContext.INFO_LEVEL, "Duplicated %d blocks. Original block count: %d", duplicatedBlocks, postJsrBlockCount);
        }

        // Purge null entries for unreached blocks and sort blocks such that loop bodies are always
        // consecutively in the array.
        int blockCount = maxBlocks - blocksNotYetAssignedId + 1 + duplicateBlocks;
        BciBlock[] newBlocks = new BciBlock[blockCount];
        int next = 0;
        for (int i = 0; i < blocks.length; ++i) {
            BciBlock b = blocks[i];
            if (b != null) {
                b.setId(next);
                newBlocks[next++] = b;
                if (b.isLoopHeader) {
                    next = handleLoopHeader(newBlocks, next, i, b);
                }
            }
        }
        assert next == newBlocks.length - 1;

        // Add unwind block.
        ExceptionDispatchBlock unwindBlock = new ExceptionDispatchBlock(BytecodeFrame.AFTER_EXCEPTION_BCI);
        unwindBlock.setId(newBlocks.length - 1);
        newBlocks[newBlocks.length - 1] = unwindBlock;

        blocks = newBlocks;
    }

    private int handleLoopHeader(BciBlock[] newBlocks, int nextStart, int i, BciBlock loopHeader) {
        int next = nextStart;
        for (int j = i + 1; j < blocks.length; ++j) {
            BciBlock other = blocks[j];
            if (other != null && other.loops.get(loopHeader.loopId)) {
                other.setId(next);
                newBlocks[next++] = other;
                blocks[j] = null;
                if (other.isLoopHeader) {
                    next = handleLoopHeader(newBlocks, next, j, other);
                }
            }
        }
        return next;
    }

    public void log(BciBlock[] blockArray, String name) {
        if (debug.isLogEnabled()) {
            debug.log("%sBlockMap %s: %n%s", debug.getCurrentScopeName(), name, toString(blockArray, loopHeaders));
        }
    }

    public static String toString(BciBlock[] blockMap, BciBlock[] loopHeadersMap) {
        if (blockMap == null) {
            return "no blockmap";
        }
        StringBuilder sb = new StringBuilder();
        Map<BciBlock, Integer> debugIds = new HashMap<>();
        int[] nextDebugId = new int[]{-2};
        ToIntFunction<BciBlock> getId = b -> {
            int id = b.getId();
            if (id < 0) {
                id = debugIds.computeIfAbsent(b, bb -> nextDebugId[0]--);
            }
            return id;
        };
        for (BciBlock b : blockMap) {
            if (b == null) {
                continue;
            }
            sb.append("B").append(getId.applyAsInt(b)).append("[").append(b.startBci).append("..").append(b.getEndBci()).append("]");
            if (b.isLoopHeader) {
                sb.append(" LoopHeader");
            }
            if (b.isExceptionEntry()) {
                sb.append(" ExceptionEntry");
            }
            if (b instanceof ExceptionDispatchBlock) {
                sb.append(" ExceptionDispatch");
            }
            if (!b.successors.isEmpty()) {
                sb.append(" Successors=[");
                for (BciBlock s : b.getSuccessors()) {
                    if (sb.charAt(sb.length() - 1) != '[') {
                        sb.append(", ");
                    }
                    sb.append("B").append(getId.applyAsInt(s));
                }
                sb.append("]");
            }
            if (!b.loops.isEmpty() && loopHeadersMap != null) {
                sb.append(" Loops=[");
                for (int pos = -1; (pos = b.loops.nextSetBit(pos + 1)) >= 0;) {
                    if (sb.charAt(sb.length() - 1) != '[') {
                        sb.append(", ");
                    }
                    sb.append("B").append(getId.applyAsInt(loopHeadersMap[pos]));
                }
                sb.append("]");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(blocks, loopHeaders);
    }

    /**
     * Get the header block for a loop index.
     */
    public BciBlock getLoopHeader(int index) {
        return loopHeaders[index];
    }

    /**
     * The next available loop number.
     */
    private int nextLoop;

    /**
     * Returns the smallest power of 2, strictly greater than value.
     */
    private static int nextPowerOfTwo(int value) {
        assert value >= 0;
        return 1 << (32 - Integer.numberOfLeadingZeros(value));
    }

    private void makeLoopHeader(BciBlock block) {
        assert !block.isLoopHeader;
        block.isLoopHeader = true;
        if (nextLoop >= LOOP_HEADER_MAX_CAPACITY) {
            // This is an artificial restriction, a sanity check to avoid feeding the compiler an
            // unreasonable number of loops.
            throw new PermanentBailoutException("Too many loops in method");
        }
        block.loops.set(nextLoop);
        debug.log("makeLoopHeader(%s) -> %s", block, block.loops);
        if (loopHeaders == null) {
            loopHeaders = new BciBlock[Math.max(nextPowerOfTwo(nextLoop), LOOP_HEADER_INITIAL_CAPACITY)];
        } else if (nextLoop >= loopHeaders.length) {
            int newLength = nextPowerOfTwo(nextLoop);
            loopHeaders = Arrays.copyOf(loopHeaders, newLength);
        }
        loopHeaders[nextLoop] = block;
        block.loopId = nextLoop;
        nextLoop++;
    }

    private void propagateLoopBits(TraversalStep step, BitSet loopBits) {
        TraversalStep s = step;
        while (s != null) {
            // Original condition: if (s.block.loops & loopBits == loopBits) break;
            // Rewritten in a more efficient way: if (loopBits & ~s.block.loops == 0) break;
            BitSet missingLoops = (BitSet) loopBits.clone();
            missingLoops.andNot(s.block.loops);
            if (missingLoops.isEmpty()) {
                break;
            }
            s.block.loops.or(missingLoops);
            if (s.block.loopIdChain != null) {
                for (TraversalStep chain : s.block.loopIdChain) {
                    propagateLoopBits(chain, loopBits);
                }
            }
            s = s.pred;
        }
    }

    /**
     * Computes a block pre-order, finds and marks loops.
     *
     * <p>
     * This uses a depth-first traversal of the blocks. In order to detect irreducible loops, blocks
     * are marked as belonging to a block as soon as that is known. This is done by keeping a linked
     * list of the currently "active" blocks (the path from entry to the current block). To be able
     * to do this marking correctly when in the case of nested loops, merge points (including loop
     * headers) remember the path from their predecessor (see
     * {@link #propagateLoopBits(TraversalStep, BitSet)}).
     * <p>
     * Since loops are marked eagerly, forward entries into an existing loop without going through
     * the loop header (i.e., irreducible loops) can be detected easily. In this case, if
     * {@link Options#DuplicateIrreducibleLoops} is enabled, the traversal starts to duplicate
     * blocks until it either exits the loop or reaches the header. Since this is a depth-first
     * traversal and the loop header is not active, we know that the loop and its inner-loops were
     * until then reducible.
     * <p>
     * This is not recursive to avoid stack overflow issues.
     */
    private void computeBlockOrder(BciBlock initialBlock) {
        ArrayDeque<TraversalStep> workStack = new ArrayDeque<>();
        workStack.push(new TraversalStep(initialBlock));
        while (!workStack.isEmpty()) {
            TraversalStep step = workStack.peek();
            BciBlock block = step.block;
            if (step.currentSuccessorIndex == 0) {
                block.visited = true;
                block.active = true;
            }
            if (step.currentSuccessorIndex < block.successors.size()) {
                BciBlock successor = block.getSuccessors().get(step.currentSuccessorIndex);
                if (step instanceof DuplicationTraversalStep) {
                    DuplicationTraversalStep duplicationStep = (DuplicationTraversalStep) step;
                    BciBlock targetHeader = duplicationStep.loopHeader;
                    if (successor != targetHeader && successor.loops.get(targetHeader.loopId)) {
                        // neither the target header nor an exit: duplicate or merge with duplicate
                        BciBlock duplicate = duplicationStep.duplicationMap.get(successor);
                        if (duplicate == null) {
                            duplicate = successor.duplicate();
                            newDuplicateBlocks++;
                            duplicationStep.duplicationMap.put(successor, duplicate);
                        }
                        successor = duplicate;
                        successor.predecessorCount++;
                        block.successors.set(step.currentSuccessorIndex, successor);
                    } else {
                        debug.dump(DebugContext.DETAILED_LEVEL, this, "Exiting duplication @ %s", successor);
                        debug.log("Exiting duplication @ %s", successor);
                        successor.predecessorCount++;
                    }
                }
                if (successor.visited) {
                    BitSet loopBits;
                    boolean duplicationStarted = false;
                    if (successor.active) {
                        // Reached block via backward branch.
                        if (!successor.isLoopHeader) {
                            makeLoopHeader(successor);
                        }
                        loopBits = (BitSet) successor.loops.clone();
                    } else {
                        // re-reaching control-flow through new path.
                        // Find loop bits
                        loopBits = (BitSet) successor.loops.clone();
                        if (successor.isLoopHeader) {
                            // this is a forward edge
                            loopBits.clear(successor.loopId);
                        }
                        // Check if we are re-entering a loop in an irreducible way
                        BitSet checkBits = loopBits;
                        int outermostInactiveLoopId = -1;
                        for (int pos = -1; (pos = checkBits.nextSetBit(pos + 1)) >= 0;) {
                            int id = pos;
                            if (!loopHeaders[id].active) {
                                if (!Options.DuplicateIrreducibleLoops.getValue(debug.getOptions())) {
                                    throw new PermanentBailoutException("Irreducible");
                                } else if (outermostInactiveLoopId == -1 || !loopHeaders[id].loops.get(outermostInactiveLoopId)) {
                                    outermostInactiveLoopId = id;
                                }
                            }
                        }
                        if (outermostInactiveLoopId != -1) {
                            assert !(step instanceof DuplicationTraversalStep);
                            // we need to duplicate until we can merge with this loop's header
                            successor.predecessorCount--;
                            BciBlock duplicate = successor.duplicate();
                            duplicate.predecessorCount++;
                            block.successors.set(step.currentSuccessorIndex, duplicate);
                            DuplicationTraversalStep duplicationStep = new DuplicationTraversalStep(step, duplicate, loopHeaders[outermostInactiveLoopId]);
                            workStack.push(duplicationStep);
                            debug.log("Starting duplication @ %s", duplicate);
                            debug.dump(DebugContext.DETAILED_LEVEL, this, "Starting duplication @ %s", duplicate);
                            duplicationStep.duplicationMap.put(successor, duplicate);
                            newDuplicateBlocks++;
                            duplicationStarted = true;
                        }
                    }
                    if (!duplicationStarted) {
                        propagateLoopBits(step, loopBits);
                        if (successor.loopIdChain == null) {
                            successor.loopIdChain = new ArrayList<>(2);
                        }
                        successor.loopIdChain.add(step);
                        debug.dump(DebugContext.DETAILED_LEVEL, this, "After re-reaching %s", successor);
                    }
                } else if (step instanceof DuplicationTraversalStep) {
                    workStack.push(new DuplicationTraversalStep((DuplicationTraversalStep) step, successor));
                } else {
                    workStack.push(new TraversalStep(step, successor));
                }
                step.currentSuccessorIndex++;
            } else {
                // We processed all the successors of this block.
                block.active = false;
                assert checkBlocks(blocksNotYetAssignedId, block);
                blocksNotYetAssignedId--;
                if (blocksNotYetAssignedId < 0) {
                    // this should only happen if duplication is active
                    OptionValues options = debug.getOptions();
                    assert DuplicateIrreducibleLoops.getValue(options);
                    duplicateBlocks += newDuplicateBlocks;
                    double factor = MaxDuplicationFactor.getValue(options);
                    if (duplicateBlocks > postJsrBlockCount * factor) {
                        throw new PermanentBailoutException("Non-reducible loop requires too much duplication. " +
                                        "Setting " + MaxDuplicationFactor.getName() + " to a value higher than " + factor + " may resolve this.");
                    }
                    // there are new duplicate blocks, re-number
                    debug.log(DebugContext.INFO_LEVEL, "Re-numbering blocks to make room for duplicates (old length: %d; new blocks: %d)", blocks.length, newDuplicateBlocks);
                    BciBlock[] newBlocks = new BciBlock[blocks.length + newDuplicateBlocks];
                    for (int i = 0; i < blocks.length; i++) {
                        newBlocks[i + newDuplicateBlocks] = blocks[i];
                        assert blocks[i].id == UNASSIGNED_ID;
                    }
                    blocksNotYetAssignedId += newDuplicateBlocks;
                    assert blocksNotYetAssignedId >= 0;
                    newDuplicateBlocks = 0;
                    blocks = newBlocks;
                }
                blocks[blocksNotYetAssignedId] = block;
                debug.log("computeBlockOrder(%s) -> %s", block, block.loops);
                debug.dump(DebugContext.DETAILED_LEVEL, this, "After adding %s", block);
                workStack.pop();
            }
        }
        BitSet loops = (BitSet) initialBlock.loops.clone();
        if (initialBlock.isLoopHeader) {
            loops.clear(initialBlock.loopId);
        }
        GraalError.guarantee(loops.isEmpty(), "Irreducible loops should already have been detected to duplicated");
    }

    private boolean checkBlocks(int start, BciBlock inserting) {
        for (int i = 0; i < start; i++) {
            assert blocks[i] == null;
        }
        EconomicSet<BciBlock> seen = EconomicSet.create(blocks.length - start);
        for (int i = start; i < blocks.length; i++) {
            assert blocks[i] != null;
            assert seen.add(blocks[i]);
        }
        assert !seen.contains(inserting) : "Trying to add " + inserting + " again";
        return true;
    }

    public static BciBlockMapping create(BytecodeStream stream, Bytecode code, OptionValues options, DebugContext debug, boolean hasAsyncExceptions) {
        BciBlockMapping map = new BciBlockMapping(code, debug);
        buildMap(stream, code, options, debug, map, hasAsyncExceptions);
        return map;
    }

    @SuppressWarnings("try")
    protected static void buildMap(BytecodeStream stream, Bytecode code, OptionValues options, DebugContext debug, BciBlockMapping map, boolean splitExceptionRanges) {
        try (Scope scope = debug.scope("BciBlockMapping", map)) {
            map.build(stream, options, splitExceptionRanges);
            if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
                debug.dump(DebugContext.INFO_LEVEL, map, code.getMethod().format("After block building %f %R %H.%n(%P)"));
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    public BciBlock[] getLoopHeaders() {
        return loopHeaders;
    }

    public BciBlock getStartBlock() {
        return startBlock;
    }

    public ExceptionDispatchBlock getUnwindBlock() {
        return (ExceptionDispatchBlock) blocks[blocks.length - 1];
    }

    public int getLoopCount() {
        return nextLoop;
    }

    public int getBlockCount() {
        return blocks.length;
    }

    @Override
    public JavaMethod asJavaMethod() {
        return code.getMethod();
    }
}
