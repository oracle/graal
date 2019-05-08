package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.impl.ParserMethod;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.sun.org.apache.bcel.internal.classfile.ClassFormatException;

import java.util.ArrayList;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.AASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ATHROW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BREAKPOINT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2B;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2C;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2S;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IINC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCMP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC2_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NOP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.WIDE;
import static com.oracle.truffle.espresso.meta.JavaKind.Double;
import static com.oracle.truffle.espresso.meta.JavaKind.Float;
import static com.oracle.truffle.espresso.meta.JavaKind.Int;
import static com.oracle.truffle.espresso.meta.JavaKind.Long;
import static com.oracle.truffle.espresso.meta.JavaKind.Object;
import static com.oracle.truffle.espresso.meta.JavaKind.Void;

public class MethodVerifier {
    private final int maxStack;
//    private final int maxLocals;
//    private final int codeLength;
    private final BytecodeStream code;
    private final ConstantPool pool;
    private final boolean[] verified;

    public MethodVerifier(int maxStack/*, int maxLocals, int codeLength*/, byte[] code, ConstantPool pool) {
        this.maxStack = maxStack;
//        this.maxLocals = maxLocals;
//        this.codeLength = codeLength;
        this.code = new BytecodeStream(code);
        this.pool = pool;
        this.verified = new boolean[code.length];
    }

    public static boolean verify(ParserMethod m, ConstantPool pool) {
        CodeAttribute codeAttribute = (CodeAttribute)m.getAttribute(CodeAttribute.NAME);
        if (codeAttribute == null) {
            return true;
        }
        return new MethodVerifier(codeAttribute.getMaxStack(), codeAttribute.getCode(), pool).verify();
    }

    private boolean verify() {
        int nextBCI = 0;
        Stack stack = new Stack(maxStack);
        while (!verified[nextBCI]) {
            nextBCI = verify(nextBCI, stack);
        }
        return true;
    }

    private boolean branch(int BCI, Stack stack) {
        if (verified[BCI]) {
            return true;
        }

        Stack newStack = stack.copy();
        int nextBCI = BCI;
        while (!verified[nextBCI]) {
            nextBCI = verify(nextBCI, newStack);
        }
        return true;
    }

    private static JavaKind fromTag(ConstantPool.Tag tag) {
        switch (tag) {
            case INTEGER: return Int;
            case FLOAT: return Float;
            case LONG: return Long;
            case DOUBLE: return Double;
            case CLASS:
            case STRING:
            case METHODHANDLE:
            case METHODTYPE: return Object;
            default:
                throw new VerifyError("invalid CP load: " + tag);
        }
    }

    private int verify(int BCI, Stack stack) {
        verified[BCI] = true;
        int curOpcode = code.opcode(BCI);
//        System.err.println(Bytecodes.nameOf(curOpcode));
        // @formatter:off
        // Checkstyle: stop
        switch (curOpcode) {
            case NOP: break;
            case ACONST_NULL: stack.pushObj(); break;

            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5: stack.pushInt(); break;

            case LCONST_0:
            case LCONST_1: stack.pushLong(); break;

            case FCONST_0:
            case FCONST_1:
            case FCONST_2: stack.pushFloat(); break;

            case DCONST_0:
            case DCONST_1: stack.pushDouble(); break;

            case BIPUSH: stack.pushInt(); break;
            case SIPUSH: stack.pushInt(); break;

            case LDC:
            case LDC_W:
            case LDC2_W:
                stack.push(fromTag(pool.at(code.readCPI(BCI)).tag()));
                break;

            case ILOAD: stack.pushInt(); break;
            case LLOAD: stack.pushLong(); break;
            case FLOAD: stack.pushFloat(); break;
            case DLOAD: stack.pushDouble(); break;
            case ALOAD: stack.pushObj(); break;

            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3: stack.pushInt(); break;
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3: stack.pushLong(); break;
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3: stack.pushFloat(); break;
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3: stack.pushDouble(); break;
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3: stack.pushObj(); break;

            case IALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;
            case LALOAD: stack.popInt(); stack.popObj(); stack.pushLong(); break;
            case FALOAD: stack.popInt(); stack.popObj(); stack.pushFloat(); break;
            case DALOAD: stack.popInt(); stack.popObj(); stack.pushDouble(); break;
            case AALOAD: stack.popInt(); stack.popObj(); stack.pushObj(); break;
            case BALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;
            case CALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;
            case SALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;

            case ISTORE: stack.popInt(); break;
            case LSTORE: stack.popLong(); break;
            case FSTORE: stack.popFloat(); break;
            case DSTORE: stack.popDouble(); break;
            case ASTORE: stack.popObj(); break;

            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3: stack.popInt(); break;
            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3: stack.popLong(); break;
            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3: stack.popFloat(); break;
            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3: stack.popDouble(); break;
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3: stack.popObj(); break;

            case IASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;
            case LASTORE: stack.popLong(); stack.popInt(); stack.popObj(); break;
            case FASTORE: stack.popFloat(); stack.popInt(); stack.popObj(); break;
            case DASTORE: stack.popDouble(); stack.popInt(); stack.popObj(); break;
            case AASTORE: stack.popObj(); stack.popInt(); stack.popObj(); break;
            case BASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;
            case CASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;
            case SASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;

            case POP: stack.pop(); break;
            case POP2: stack.pop2(); break;

            // TODO(peterssen): Stack shuffling is expensive.
            case DUP: stack.dup(); break;
            case DUP_X1: stack.dupx1(); break;
            case DUP_X2: stack.dupx2(); break;
            case DUP2: stack.dup2(); break;
            case DUP2_X1: stack.dup2x1(); break;
            case DUP2_X2: stack.dup2x2(); break;
            case SWAP: stack.swap(); break;

            case IADD: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LADD: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FADD: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DADD: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case ISUB: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LSUB: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FSUB: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DSUB: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case IMUL: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LMUL: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FMUL: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DMUL: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case IDIV: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LDIV: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FDIV: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DDIV: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case IREM: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LREM: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FREM: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DREM: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case INEG:  stack.popInt(); stack.pushInt(); break;
            case LNEG:  stack.popLong(); stack.pushLong();break;
            case FNEG:  stack.popFloat(); stack.pushFloat();break;
            case DNEG:  stack.popDouble(); stack.pushDouble();break;

            case ISHL: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LSHL: stack.popInt(); stack.popLong(); stack.pushLong();break;
            case ISHR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LSHR: stack.popInt(); stack.popLong(); stack.pushLong();break;
            case IUSHR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LUSHR: stack.popInt(); stack.popLong(); stack.pushLong();break;

            case IAND: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LAND: stack.popLong(); stack.popLong(); stack.pushLong();break;

            case IOR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LOR: stack.popLong(); stack.popLong(); stack.pushLong();break;

            case IXOR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LXOR: stack.popLong(); stack.popLong(); stack.pushLong();break;

            case IINC: break;

            case I2L: stack.popInt(); stack.pushLong(); break;
            case I2F: stack.popInt(); stack.pushFloat(); break;
            case I2D: stack.popInt(); stack.pushDouble(); break;

            case L2I: stack.popLong(); stack.pushInt(); break;
            case L2F: stack.popLong(); stack.pushFloat(); break;
            case L2D: stack.popLong(); stack.pushDouble(); break;

            case F2I: stack.popFloat(); stack.pushInt(); break;
            case F2L: stack.popFloat(); stack.pushLong();break;
            case F2D: stack.popFloat(); stack.pushDouble();break;

            case D2I: stack.popDouble(); stack.pushInt();break;
            case D2L: stack.popDouble(); stack.pushLong();break;
            case D2F: stack.popDouble(); stack.pushFloat();break;

            case I2B: stack.popInt(); stack.pushInt();break;
            case I2C: stack.popInt(); stack.pushInt();break;
            case I2S: stack.popInt(); stack.pushInt();break;

            case LCMP: stack.popLong(); stack.popLong(); stack.pushInt();break;
            case FCMPL:
            case FCMPG: stack.popFloat(); stack.popFloat(); stack.pushInt();break;
            case DCMPL:
            case DCMPG: stack.popDouble(); stack.popDouble(); stack.pushInt();break;

            case IFEQ: // fall through
            case IFNE: // fall through
            case IFLT: // fall through
            case IFGE: // fall through
            case IFGT: // fall through
            case IFLE:
                stack.popInt();
                branch(code.readBranchDest(BCI), stack);
                break;
            case IF_ICMPEQ: // fall through
            case IF_ICMPNE: // fall through
            case IF_ICMPLT: // fall through
            case IF_ICMPGE: // fall through
            case IF_ICMPGT: // fall through
            case IF_ICMPLE:
                stack.popInt(); stack.popInt();
                branch(code.readBranchDest(BCI), stack);
                break;
            case IF_ACMPEQ: // fall through
            case IF_ACMPNE:
                stack.popObj(); stack.popObj();
                branch(code.readBranchDest(BCI), stack);
                break;

            case GOTO:
            case GOTO_W:
                branch(code.readBranchDest(BCI), stack);
                return BCI;
            case IFNULL: // fall through
            case IFNONNULL:
                stack.popObj();
                branch(code.readBranchDest(BCI), stack);
                break;
            case JSR: // fall through
            case JSR_W: {
                stack.pushObj();
                branch(code.readBranchDest(BCI), stack);
                break;
            }
            case RET: {
                break;
            }

            // @formatter:on
            // Checkstyle: resume
            case TABLESWITCH: {
                stack.popInt();
                BytecodeTableSwitch switchHelper = code.getBytecodeTableSwitch();
                int low = switchHelper.lowKey(BCI);
                int high = switchHelper.highKey(BCI);
                for (int i = low; i < high; i++) {
                    branch(switchHelper.targetAt(BCI, i - low), stack);
                }
                return switchHelper.defaultTarget(BCI);
            }
            case LOOKUPSWITCH: {
                stack.popInt();
                BytecodeLookupSwitch switchHelper = code.getBytecodeLookupSwitch();
                int low = 0;
                int high = switchHelper.numberOfCases(BCI);
                for (int i = low; i < high; i++) {
                    branch(BCI + switchHelper.offsetAt(BCI, i), stack);
                }
                return switchHelper.defaultTarget(BCI);
            }
            // @formatter:off
            // Checkstyle: stop
            case IRETURN: stack.popInt(); return BCI;
            case LRETURN: stack.popLong(); return BCI;
            case FRETURN: stack.popFloat(); return BCI;
            case DRETURN: stack.popDouble(); return BCI;
            case ARETURN: stack.popObj(); return BCI;
            case RETURN: return BCI;

            case GETSTATIC:
            case GETFIELD:
            {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof FieldRefConstant)) {
                    throw new VerifyError();
                }
                JavaKind kind = charToKind(((FieldRefConstant) pc).getType(pool).toString().charAt(0));
                if (curOpcode == GETFIELD) {
                    stack.popObj();
                }
                stack.push(kind);
                break;
            }
            case PUTSTATIC:
            case PUTFIELD: {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof FieldRefConstant)) {
                    throw new VerifyError();
                }
//                JavaKind kind = JavaKind.fromTypeString(((FieldRefConstant) pc).getType(pool).toString());
                JavaKind kind = charToKind(((FieldRefConstant) pc).getType(pool).toString().charAt(0));
                stack.pop(kind);
                if (curOpcode == PUTFIELD) {
                    stack.popObj();
                }
                break;
            }

            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE: {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof MethodRefConstant)) {
                    throw new VerifyError();
                }
                JavaKind[] parsedSig = parseSig((((MethodRefConstant) pc).getSignature(pool)).toString());
                if (parsedSig.length == 0) {
                    throw new VerifyError();
                }
                for (int i = parsedSig.length - 2; i>=0; i--) {
                    stack.pop(parsedSig[i]);
                }
                if (curOpcode != INVOKESTATIC) {
                    stack.popObj();
                }
                JavaKind returnKind = parsedSig[parsedSig.length - 1];
                if (!(returnKind == Void)) {
                    stack.push(returnKind);
                }
                break;
            }

            case NEW: stack.pushObj(); break;
            case NEWARRAY: stack.popInt(); stack.pushObj(); break;
            case ANEWARRAY: stack.popInt(); stack.pushObj(); break;
            case ARRAYLENGTH: stack.popObj(); stack.pushInt(); break;

            case ATHROW: stack.popObj(); return BCI;

            case CHECKCAST: stack.popObj(); stack.pushObj(); break;
            case INSTANCEOF: stack.popObj(); stack.pushInt(); break;

            case MONITORENTER: stack.popObj(); break;
            case MONITOREXIT: stack.popObj(); break;

            case WIDE: break;
            case MULTIANEWARRAY:
                int dim = code.readUByte(BCI + 3);
                for (int i = 0; i<dim; i++) {
                    stack.popInt();
                }
                stack.pushObj();
                break;

            case BREAKPOINT: break;

            case INVOKEDYNAMIC: {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof InvokeDynamicConstant)) {
                    throw new VerifyError();
                }
                JavaKind[] parsedSig = parseSig(((InvokeDynamicConstant) pc).getSignature(pool).toString());
                if (parsedSig.length == 0) {
                    throw new VerifyError();
                }
                for (int i = parsedSig.length - 2; i >= 0; i--) {
                    stack.pop(parsedSig[i]);
                }
                JavaKind returnKind = parsedSig[parsedSig.length - 1];
                if (returnKind != Void) {
                    stack.push(returnKind);
                }
                break;
            }
            case QUICK: break;
            default:
        // @formatter:on
        // Checkstyle: resume
        }
        return code.nextBCI(BCI);
    }

    private static JavaKind[] parseSig(String sig) {
        ArrayList<JavaKind> res = new ArrayList<>();
        int i= 0;
        while (i < sig.length()) {
            switch (sig.charAt(i)) {
                case '(':
                case ')':
                    i++; break;
                case '[':
                    res.add(Object);
                    while (sig.charAt(i) == '[') {
                        i++;
                    }
                    if (sig.charAt(i) != 'L') {
                        i++;
                    } else {
                        i = sig.indexOf(';', i) + 1;
                    }
                    break;
                case 'L':
                    res.add(Object);
                    i = sig.indexOf(';', i) + 1;
                    break;
                case 'Z':
                case 'C':
                case 'B':
                case 'S':
                case 'I':
                    res.add(Int); i++; break;
                case 'F':
                    res.add(Float); i++; break;
                case 'D':
                    res.add(Double); i++; break;
                case 'J':
                    res.add(Long); i++; break;
                case 'V':
                    res.add(Void); i++; break;
                default:
                    throw new VerifyError();
            }
        }
        return res.toArray(new JavaKind[0]);
    }

    private static JavaKind charToKind(char c) {
        switch (c) {
            case '(':
            case ')':
                throw new VerifyError();
            case '[':
            case 'L':
                return Object;
            case 'Z':
            case 'C':
            case 'B':
            case 'S':
            case 'I':
                return Int;
            case 'F':
                return Float;
            case 'D':
                return Double;
            case 'J':
                return Long;
            case 'V':
                return Void;
            default:
                throw new VerifyError();
        }
    }

    private class Stack {
        private final JavaKind[] stack;
        private int top;

        private Stack(Stack copy) {
            this.stack = new JavaKind[copy.stack.length];
            this.top = copy.top;
            System.arraycopy(copy.stack, 0, this.stack, 0, top);
        }

        Stack(int maxStack) {
            this.stack = new JavaKind[maxStack];
            this.top = 0;
        }

        Stack copy() {
            return new Stack(this);
        }

        void pushInt() {
            stack[top++] = Int;
        }
        void pushObj() {
            stack[top++] = Object;
        }
        void pushFloat() {
            stack[top++] = Float;
        }
        void pushDouble() {
            stack[top++] = Double;
        }
        void pushLong() {
            stack[top++] = Long;
        }

        void push(JavaKind kind) {
            stack[top++] = kind;
        }

        void popInt() {
            if (!(stack[--top] == Int)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Int);
            }
        }
        void popObj() {
            if (!(stack[--top] == Object)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Object);
            }
        }
        void popFloat() {
            if (!(stack[--top] == Float)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Float);
            }
        }
        void popDouble() {
            if (!(stack[--top] == Double)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Double);
            }
        }
        void popLong() {
            if (!(stack[--top] == Long)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Long);
            }
        }
        void popAny() {
            if (stack[--top] == null) {
                throw new VerifyError(stack[top] + " on stack, required: any");
            }
        }
        void pop(JavaKind k) {
            if (!(stack[--top] == k)) {
                throw new VerifyError(stack[top] + " on stack, required: " + k);
            }
        }

        void dup() {
            if (isType2(stack[top-1])) {
                throw new ClassFormatException("type 2 operand for dup.");
            }
            stack[top] = stack[top-1];
            top++;
        }

        void pop() {
            JavaKind v1 = stack[top-1];
            if (isType2(v1)) {
                throw new ClassFormatException("type 2 operand for pop.");
            }
            top--;
        }

        void pop2() {
            JavaKind v1 = stack[top-1];
            if (isType2(v1)) {
                top--;
                return;
            }
            JavaKind v2 = stack[top -2];
            if (isType2(v2)) {
                throw new ClassFormatException("type 2 second operand for pop2.");
            }
            top = top - 2;
        }

        void dupx1() {
            JavaKind v1 = stack[top-1];
            if (isType2(v1) || isType2(stack[top-2])) {
                throw new ClassFormatException("type 2 operand for dupx1.");
            }
            System.arraycopy(stack, top-2, stack, top-1, 2);
            top++;
            stack[top-3] = v1;
        }

        void dupx2() {
            JavaKind v1 = stack[top-1];
            if (isType2(v1)) {
                throw new ClassFormatException("type 2 first operand for dupx2.");
            }
            JavaKind v2 = stack[top-2];
            if (isType2(v2)) {
                System.arraycopy(stack, top-2, stack, top-1, 2);
                top++;
                stack[top-3] = v1;
            } else {
                if (isType2(stack[top -3])) {
                    throw new ClassFormatException("type 2 third operand for dupx2.");
                }
                System.arraycopy(stack, top-3, stack, top-2, 3);
                top++;
                stack[top-4] = v1;
            }
        }

        void dup2() {
            JavaKind v1 = stack[top -1];
            if (isType2(v1)) {
                stack[top] = v1;
                top++;
            } else {
                if (isType2(stack[top-1])) {
                    throw new ClassFormatException("type 2 second operand for dup2.");
                }
                System.arraycopy(stack, top -2, stack, top, 2);
                top = top + 2;
            }
        }

        void dup2x1() {
            JavaKind v1 = stack[top-1];
            JavaKind v2 = stack[top-2];
            if (isType2(v2)) {
                throw new ClassFormatException("type 2 second operand for dup2x1");
            }
            if (isType2(v1)) {
                System.arraycopy(stack, top-2, stack, top-1, 2);
                top++;
                stack[top-3] = v1;
                return;
            }
            if (isType2(stack[top-3])) {
                throw new ClassFormatException("type 2 third operand for dup2x1.");
            }
            System.arraycopy(stack, top-3, stack, top, 3);
            top = top + 2;
            stack[top - 5] = v2;
            stack[top - 4] = v1;
        }

        void dup2x2() {
            JavaKind v1 = stack[top-1];
            JavaKind v2 = stack[top-2];
            boolean b1 = isType2(v1);
            boolean b2 = isType2(v2);

            if (b1 && b2) {
                System.arraycopy(stack, top-2, stack, top-1, 2);
                stack[top-2] = v1;
                top++;
                return;
            }
            JavaKind v3 = stack[top-3];
            boolean b3 = isType2(v3);
            if (!b1 && !b2 && b3) {
                System.arraycopy(stack, top-3, stack, top, 3);
                stack[top - 3] = v2;
                stack[top - 2] = v1;
                top = top + 2;
                return;
            }
            if (b1 && !b2 && !b3) {
                System.arraycopy(stack, top-3, stack, top-2, 3);
                stack[top-3] = v1;
                top++;
                return;
            }
            JavaKind v4 = stack[top-4];
            boolean b4 = isType2(v4);
            if (!b1 && !b2 && !b3 && !b4) {
                System.arraycopy(stack, top-4, stack, top - 2, 4);
                stack[top - 4] = v2;
                stack[top - 3] = v1;
                top = top + 2;
                return;
            }
            throw new ClassFormatException("Seriously, what are you doing with dup2x2 ?");

        }

        void swap() {
            JavaKind v1 = stack[top-1];
            JavaKind v2 = stack[top-2];
            boolean b1 = isType2(v1);
            boolean b2 = isType2(v2);
            if (!b1 && !b2) {
                stack[top - 1] = v2;
                stack[top - 2] = v1;
                return;
            }
            throw new ClassFormatException("Type 2 operand for SWAP");
        }

        private boolean isType2(JavaKind k) {
            return k == Long || k == Double;
        }
    }

}
