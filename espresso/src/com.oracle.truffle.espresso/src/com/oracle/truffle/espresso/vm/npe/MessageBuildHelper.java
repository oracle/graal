/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm.npe;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.AASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ATHROW;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SIPUSH;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.MetaUtil;

final class MessageBuildHelper {

    private MessageBuildHelper() {
        // Disallow instantiation.
    }

    static final int MAX_DETAIL = 5;

    static final int INVALID_BYTECODE = -1;
    static final int EXPLICIT_NPE = -2;

    public static String buildCause(Analysis analysis, int bci) {
        int slot = getSlot(analysis, bci);
        if (slot == EXPLICIT_NPE) {
            // We don't want to return a message.
            return null;
        }
        if (slot == INVALID_BYTECODE) {
            // We encountered a bytecode that does not dereference a reference. This should be
            // impossible.
            return null;
        }
        StringBuilder sb = new StringBuilder();
        // Print string describing which action (bytecode) could not be
        // performed because of the null reference.
        appendFailedAction(analysis, sb, bci);
        // Print a description of what is null.
        appendCause(analysis, sb, bci, slot);
        return sb.toString();
    }

    private static boolean appendCause(Analysis analysis, StringBuilder sb, int bci, int slot) {
        if (appendCause(analysis, sb, bci, slot, MAX_DETAIL, false, " because \"")) {
            sb.append("\" is null");
            return true;
        }
        // Nothing was printed. End the sentence without the 'because'
        // subordinate sentence.
        return false;
    }

    private static boolean appendCause(Analysis analysis, StringBuilder sb, int currentBci, int slot,
                    int maxDetail,
                    boolean isInner, String prefix) {
        if (maxDetail <= 0) {
            return false;
        }
        SimulatedStack stack = analysis.stackAt(currentBci);
        if (stack == null) {
            return false;
        }
        StackObject obj = stack.top(slot);
        if (!obj.hasBci()) {
            return false;
        }
        int bci = obj.originBci();
        int opcode = analysis.bs.currentBC(bci);
        if ((maxDetail == MAX_DETAIL) &&
                        (prefix != null) &&
                        !Bytecodes.isInvoke(opcode)) {
            sb.append(prefix);
        }

        switch (opcode) {
            case ILOAD_0:
            case ALOAD_0:
                appendLocalVar(analysis, sb, bci, 0, !stack.isLocalSlotWritten(0));
                return true;

            case ILOAD_1:
            case ALOAD_1:
                appendLocalVar(analysis, sb, bci, 1, !stack.isLocalSlotWritten(1));
                return true;

            case ILOAD_2:
            case ALOAD_2:
                appendLocalVar(analysis, sb, bci, 2, !stack.isLocalSlotWritten(2));
                return true;

            case ILOAD_3:
            case ALOAD_3:
                appendLocalVar(analysis, sb, bci, 3, !stack.isLocalSlotWritten(3));
                return true;

            case ILOAD:
            case ALOAD: {
                int index = analysis.bs.readLocalIndex(bci);
                appendLocalVar(analysis, sb, bci, index, !stack.isLocalSlotWritten(index));
                return true;
            }

            case ACONST_NULL:
                sb.append("null");
                return true;
            case ICONST_M1:
                sb.append("-1");
                return true;
            case ICONST_0:
                sb.append("0");
                return true;
            case ICONST_1:
                sb.append("1");
                return true;
            case ICONST_2:
                sb.append("2");
                return true;
            case ICONST_3:
                sb.append("3");
                return true;
            case ICONST_4:
                sb.append("4");
                return true;
            case ICONST_5:
                sb.append("5");
                return true;
            case BIPUSH: {
                sb.append(analysis.bs.readByte(bci));
                return true;
            }
            case SIPUSH: {
                sb.append(analysis.bs.readShort(bci));
                return true;
            }
            case IALOAD:
            case AALOAD: {
                // Print the 'name' of the array. Go back to the bytecode that
                // pushed the array reference on the operand stack.
                if (!appendCause(analysis, sb, bci, 1, maxDetail - 1, isInner, null)) {
                    // Returned false. Max recursion depth was reached. Print dummy.
                    sb.append("<array>");
                }
                sb.append("[");
                // Print the index expression. Go back to the bytecode that
                // pushed the index on the operand stack.
                // inner_expr == true so we don't print unwanted strings
                // as "The return value of'". And don't decrement max_detail so we always
                // get a value here and only cancel out on the dereference.
                if (!appendCause(analysis, sb, bci, 0, maxDetail, true, null)) {
                    // Returned false. We don't print complex array index expressions. Print
                    // placeholder.
                    sb.append("...");
                }
                sb.append("]");
                return true;
            }

            case GETSTATIC: {
                appendStaticField(analysis, sb, bci);
                return true;
            }

            case GETFIELD: {
                // Print the sender. Go back to the bytecode that
                // pushed the sender on the operand stack.
                if (appendCause(analysis, sb, bci, 0, maxDetail - 1, isInner, null)) {
                    sb.append(".");
                }
                sb.append(analysis.getFieldName(bci));
                return true;
            }

            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE: {
                if (maxDetail == MAX_DETAIL && !isInner) {
                    sb.append(" because the return value of \"");
                }
                appendMethodCall(analysis, sb, bci);
                return true;
            }

            default:
                break;
        }
        return false;
    }

    private static void appendStaticField(Analysis analysis, StringBuilder sb, int bci) {
        ConstantPool pool = analysis.m.getConstantPool();
        int fieldIndex = analysis.bs.readCPI(bci);
        Symbol<Name> klassName = pool.memberClassName(fieldIndex);
        Symbol<Name> fieldName = pool.fieldName(fieldIndex);
        appendClassName(sb, klassName);
        sb.append(".").append(fieldName);

    }

    private static void appendMethodCall(Analysis analysis, StringBuilder sb, int bci) {
        ConstantPool pool = analysis.m.getConstantPool();

        int methodIndex = analysis.bs.readCPI(bci);
        Symbol<Name> klassName = pool.memberClassName(methodIndex);
        Symbol<Name> methodName = pool.methodName(methodIndex);
        Symbol<Signature> signature = pool.methodSignature(methodIndex);

        appendClassName(sb, klassName);
        sb.append(".").append(methodName).append("(");
        appendSignature(analysis.getSignatures(), sb, signature);
        sb.append(")");
    }

    private static void appendClassType(StringBuilder sb, Symbol<Type> classType) {
        String n = MetaUtil.internalNameToJava(classType.toString(), true, false);
        appendClassName(sb, n);
    }

    private static void appendClassName(StringBuilder sb, Symbol<Name> className) {
        String n = className.toString().replace("/", ".");
        appendClassName(sb, n);
    }

    private static void appendClassName(StringBuilder sb, String className) {
        String n = className;
        // Trims some well known classes.
        // Also handles array types (eg: j.l.Object[][] -> Object[][])
        if (n.startsWith("java.lang.Object") || n.startsWith("java.lang.String")) {
            n = n.substring("java.lang.".length());
        }
        sb.append(n);
    }

    private static void appendSignature(SignatureSymbols signatureSymbols, StringBuilder sb, Symbol<Signature> signature) {
        Symbol<Type>[] sig = signatureSymbols.parsed(signature);
        boolean first = true;
        for (int i = 0; i < SignatureSymbols.parameterCount(sig); i++) {
            Symbol<Type> type = sig[i];
            if (!first) {
                sb.append(", ");
            }
            appendClassType(sb, type);
            first = false;
        }
    }

    private static void appendLocalVar(Analysis analysis, StringBuilder sb, int bci, int slot, boolean isParameter) {
        Method m = analysis.m;
        LocalVariableTable table = m.getLocalVariableTable();
        if (table != LocalVariableTable.EMPTY_LVT) {
            Local local;
            try {
                local = table.getLocal(slot, bci);
            } catch (IllegalStateException e) {
                local = null;
            }
            if (local != null) {
                sb.append(local.getNameAsString());
                return;
            }
        }
        if (!isParameter) {
            sb.append("<local").append(slot).append(">");
            return;
        }

        // Try to handle some cases
        if (!m.isStatic() && (slot == 0)) {
            sb.append("this");
            return;
        }
        int currentSlot = m.isStatic() ? 0 : 1;
        int paramIndex = 1;
        Symbol<Type>[] sig = m.getParsedSignature();
        for (int i = 0; i < SignatureSymbols.parameterCount(sig); i++) {
            Symbol<Type> type = SignatureSymbols.parameterType(sig, i);
            int slots = TypeSymbols.slotCount(type);
            if ((slot >= currentSlot) && (slot < currentSlot + slots)) {
                sb.append("<parameter").append(paramIndex).append(">");
                return;
            }
            paramIndex += 1;
            currentSlot += slots;
        }

        // Best we can do.
        sb.append("<local").append(slot).append(">");
    }

    // The slot of the operand stack that contains the null reference.
    static int getSlot(Analysis analysis, int bci) {
        int opcode = analysis.bs.currentBC(bci);
        switch (opcode) {
            case GETFIELD:
            case ARRAYLENGTH:
            case ATHROW:
            case MONITORENTER:
            case MONITOREXIT:
                return 0;
            case IALOAD:
            case FALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case LALOAD:
            case DALOAD:
                return 1;
            case IASTORE:
            case FASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
                return 2;
            case LASTORE:
            case DASTORE:
                return 3;
            case PUTFIELD:
                return TypeSymbols.slotCount(analysis.getFieldType(bci));
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKEINTERFACE:
                Symbol<Name> name = analysis.getInvokeName(bci, opcode);
                // Assume the call of a constructor can never cause a NullPointerException
                // (which is true in Java). This is mainly used to avoid generating wrong
                // messages for NullPointerExceptions created explicitly by new in Java code.
                if (name == Names._init_) {
                    return EXPLICIT_NPE;
                } else {
                    return SignatureSymbols.slotsForParameters(analysis.getInvokeSignature(bci, opcode));
                }
        }
        return INVALID_BYTECODE;
    }

    static void appendFailedAction(Analysis analysis, StringBuilder sb, int bci) {
        int opcode = analysis.bs.currentBC(bci);
        switch (opcode) {
            case IALOAD:
                sb.append("Cannot load from int array");
                break;
            case FALOAD:
                sb.append("Cannot load from float array");
                break;
            case AALOAD:
                sb.append("Cannot load from object array");
                break;
            case BALOAD:
                sb.append("Cannot load from byte/boolean array");
                break;
            case CALOAD:
                sb.append("Cannot load from char array");
                break;
            case SALOAD:
                sb.append("Cannot load from short array");
                break;
            case LALOAD:
                sb.append("Cannot load from long array");
                break;
            case DALOAD:
                sb.append("Cannot load from double array");
                break;

            case IASTORE:
                sb.append("Cannot store to int array");
                break;
            case FASTORE:
                sb.append("Cannot store to float array");
                break;
            case AASTORE:
                sb.append("Cannot store to object array");
                break;
            case BASTORE:
                sb.append("Cannot store to byte/boolean array");
                break;
            case CASTORE:
                sb.append("Cannot store to char array");
                break;
            case SASTORE:
                sb.append("Cannot store to short array");
                break;
            case LASTORE:
                sb.append("Cannot store to long array");
                break;
            case DASTORE:
                sb.append("Cannot store to double array");
                break;

            case ARRAYLENGTH:
                sb.append("Cannot read the array length");
                break;
            case ATHROW:
                sb.append("Cannot throw exception");
                break;
            case MONITORENTER:
                sb.append("Cannot enter synchronized block");
                break;
            case MONITOREXIT:
                sb.append("Cannot exit synchronized block");
                break;
            case GETFIELD:
                sb.append("Cannot read field \"").append(analysis.getFieldName(bci)).append("\"");
                break;
            case PUTFIELD:
                sb.append("Cannot assign field \"").append(analysis.getFieldName(bci)).append("\"");
                break;
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKEINTERFACE:
                sb.append("Cannot invoke \"");
                appendMethodCall(analysis, sb, bci);
                sb.append("\"");
                break;
            default:
                throw EspressoError.shouldNotReachHere("Invalid bytecode for NPE encountered: " + Bytecodes.nameOf(opcode));
        }
    }
}
