/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen.long64;

import java.lang.reflect.Method;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.js.JSStaticMethodDefinition;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.longemulation.Long64;

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

public class Long64Lowerer {

    public static final long JS_MAX_EXACT_INT53 = 9007199254740991L;
    public static final long JS_MIN_EXACT_INT53 = -9007199254740991L;
    private static final EconomicMap<HostedMethod, JSStaticMethodDefinition> staticJSMethodCache = EconomicMap.create();

    public static JSStaticMethodDefinition getJSStaticMethodDefinition(HostedMethod method) {
        if (staticJSMethodCache.containsKey(method)) {
            return staticJSMethodCache.get(method);
        } else {
            JSStaticMethodDefinition jsStaticMethodDefinition = new JSStaticMethodDefinition(method);
            staticJSMethodCache.put(method, jsStaticMethodDefinition);
            return jsStaticMethodDefinition;
        }
    }

    public static void lowerFromConstant(Constant c, JSCodeGenTool jsLTools) {
        assert c instanceof PrimitiveConstant : c;
        long longVal = ((PrimitiveConstant) c).asLong();
        Long64Provider long64Provider = jsLTools.getJSProviders().getLong64Provider();

        if (longVal == 0) {
            jsLTools.genStaticField(long64Provider.getField("LongZero"));
        } else if (Integer.MIN_VALUE <= longVal && longVal <= Integer.MAX_VALUE) {
            HostedMethod m = long64Provider.getMethod("fromInt", int.class);
            getJSStaticMethodDefinition(m).emitCall(jsLTools, Emitter.of((int) longVal));
        } else {
            int low = (int) longVal;
            int high = (int) (longVal >> 32);
            HostedMethod m = long64Provider.getMethod("fromTwoInt", int.class, int.class);
            getJSStaticMethodDefinition(m).emitCall(jsLTools, Emitter.of(low), Emitter.of(high));
        }
    }

    public static void lowerSubstitutedDeclaration(JSCodeGenTool jsLTools, long val) {
        lowerFromConstant(JavaConstant.forLong(val), jsLTools);
    }

    private static boolean fromNum(ShiftNode<?> n) {
        ValueNode y = n.getY();
        return y.getStackKind() != JavaKind.Long;
    }

    /**
     * Maps arithmetic operations to methods of {@link Long64}.
     *
     * @param n either a {@link ArithmeticOperation} or a {@link IntegerDivRemNode}
     * @return a {@link Method} of {@link Long64}
     */
    public static HostedMethod methodForArithmeticOperation(ValueNode n) {
        ArithmeticOpTable.Op op;
        ArithmeticOpTable arithmeticOpTable = IntegerStamp.OPS;
        if (n instanceof ArithmeticOperation) {
            op = ((ArithmeticOperation) n).getArithmeticOp();
        } else {
            assert n instanceof IntegerDivRemNode : n;
            op = switch (((IntegerDivRemNode) n).getOp()) {
                case DIV -> IntegerStamp.OPS.getDiv();
                case REM -> IntegerStamp.OPS.getRem();
            };
        }
        assert op != null;
        boolean fromNum = false;
        if (n instanceof ShiftNode) {
            fromNum = fromNum((ShiftNode<?>) n);
        }

        Long64Provider long64Provider = WebImageJSProviders.singleton().getLong64Provider();

        HostedMethod method;
        if (op.equals(arithmeticOpTable.getNeg())) {
            method = long64Provider.getMethod("negate", Long64.class);
        } else if (op.equals(arithmeticOpTable.getAdd())) {
            method = long64Provider.getMethod("add", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getSub())) {
            method = long64Provider.getMethod("sub", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getMul())) {
            method = long64Provider.getMethod("mul", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getMulHigh()) || op.equals(arithmeticOpTable.getUMulHigh())) {
            throw GraalError.unimplemented("Unsupported operation mul high in long emulation " + n); // ExcludeFromJacocoGeneratedReport
        } else if (op.equals(arithmeticOpTable.getDiv())) {
            method = long64Provider.getMethod("div", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getRem())) {
            method = long64Provider.getMethod("mod", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getNot())) {
            method = long64Provider.getMethod("not", Long64.class);
        } else if (op.equals(arithmeticOpTable.getAnd())) {
            method = long64Provider.getMethod("and", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getOr())) {
            method = long64Provider.getMethod("or", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getXor())) {
            method = long64Provider.getMethod("xor", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getMin())) {
            method = long64Provider.getMethod("min", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getMax())) {
            method = long64Provider.getMethod("max", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getUMin())) {
            method = long64Provider.getMethod("umin", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getUMax())) {
            method = long64Provider.getMethod("umax", Long64.class, Long64.class);
        } else if (op.equals(arithmeticOpTable.getShl())) {
            if (fromNum) {
                method = long64Provider.getMethod("slFromNum", Long64.class, int.class);
            } else {
                method = long64Provider.getMethod("sl", Long64.class, Long64.class);
            }
        } else if (op.equals(arithmeticOpTable.getShr())) {
            if (fromNum) {
                method = long64Provider.getMethod("srFromNum", Long64.class, int.class);
            } else {
                method = long64Provider.getMethod("sr", Long64.class, Long64.class);
            }
        } else if (op.equals(arithmeticOpTable.getUShr())) {
            if (fromNum) {
                method = long64Provider.getMethod("usrFromNum", Long64.class, int.class);
            } else {
                method = long64Provider.getMethod("usr", Long64.class, Long64.class);
            }
        } else if (op.equals(arithmeticOpTable.getAbs())) {
            method = long64Provider.getMethod("abs", Long64.class);
        } else if (op.equals(arithmeticOpTable.getZeroExtend())) {
            method = long64Provider.getMethod("fromZeroExtend", int.class);
        } else if (op.equals(arithmeticOpTable.getSignExtend())) {
            method = long64Provider.getMethod("fromInt", int.class);
        } else if (op.equals(arithmeticOpTable.getNarrow())) {
            method = long64Provider.getMethod("lowBits", Long64.class);
        } else if (op.equals(arithmeticOpTable.getFloatConvert(FloatConvert.L2D)) || op.equals(arithmeticOpTable.getFloatConvert(FloatConvert.L2F))) {
            method = long64Provider.getMethod("toNumber", Long64.class);
        } else if (op.equals(FloatStamp.OPS.getFloatConvert(FloatConvert.D2L)) || op.equals(FloatStamp.OPS.getFloatConvert(FloatConvert.F2L))) {
            method = long64Provider.getMethod("fromDouble", double.class);
        } else {
            throw GraalError.unimplemented("There is no long emulation method in Java for " + n + " with the operation " + op); // ExcludeFromJacocoGeneratedReport
        }
        return method;
    }

    /**
     * Maps the given {@link BinaryOpLogicNode} to the corresponding method of {@link Long64}.
     */
    public static HostedMethod getBinaryLogicMethod(BinaryOpLogicNode n) {
        Long64Provider long64Provider = WebImageJSProviders.singleton().getLong64Provider();
        return switch (n) {
            case CompareNode compareNode -> switch (compareNode.condition()) {
                case EQ -> long64Provider.getMethod("equal", Long64.class, Long64.class);
                case LT -> long64Provider.getMethod("lessThan", Long64.class, Long64.class);
                case BT -> long64Provider.getMethod("belowThan", Long64.class, Long64.class);
            };
            case IntegerTestNode integerTestNode -> long64Provider.getMethod("test", Long64.class, Long64.class);
            default -> throw GraalError.unimplemented("unhandled long64 binary operation logic node" + n); // ExcludeFromJacocoGeneratedReport
        };
    }

    public static void genBinaryArithmeticOperation(ValueNode node, ValueNode lOp, ValueNode rOp, JSCodeGenTool jsLTools) {
        genLong64MethodCall(methodForArithmeticOperation(node), jsLTools, Emitter.of(lOp), Emitter.of(rOp));
    }

    public static void genUnaryArithmeticOperation(UnaryNode node, ValueNode value, JSCodeGenTool jsLTools) {
        genUnaryArithmeticOperation(node, Emitter.of(value), jsLTools);
    }

    public static void genUnaryArithmeticOperation(UnaryNode node, IEmitter value, JSCodeGenTool jsLTools) {
        genLong64MethodCall(methodForArithmeticOperation(node), jsLTools, value);
    }

    public static void genBinaryLogicNode(BinaryOpLogicNode node, ValueNode lOp, ValueNode rOp, JSCodeGenTool jsLTools) {
        genLong64MethodCall(getBinaryLogicMethod(node), jsLTools, Emitter.of(lOp), Emitter.of(rOp));
    }

    private static void genLong64MethodCall(HostedMethod long64method, JSCodeGenTool jsLTools, IEmitter... args) {
        JSStaticMethodDefinition long64JSMethod = getJSStaticMethodDefinition(long64method);
        long64JSMethod.emitCall(jsLTools, args);
    }
}
