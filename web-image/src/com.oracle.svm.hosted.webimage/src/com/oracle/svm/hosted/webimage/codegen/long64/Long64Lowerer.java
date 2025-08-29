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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.js.JSStaticMethodDefinition;
import com.oracle.svm.util.ReflectionUtil;
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
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class Long64Lowerer {

    public static final long JS_MAX_EXACT_INT53 = 9007199254740991L;
    public static final long JS_MIN_EXACT_INT53 = -9007199254740991L;
    private static final EconomicMap<Method, JSStaticMethodDefinition> staticJSMethodCache = EconomicMap.create();

    public static JSStaticMethodDefinition getJSStaticMethodDefinition(Method method, JSCodeGenTool jsLTools) {
        if (staticJSMethodCache.containsKey(method)) {
            return staticJSMethodCache.get(method);
        } else {
            ResolvedJavaMethod m = jsLTools.getProviders().getMetaAccess().lookupJavaMethod(method);
            JSStaticMethodDefinition jsStaticMethodDefinition = new JSStaticMethodDefinition(m);
            staticJSMethodCache.put(method, jsStaticMethodDefinition);
            return jsStaticMethodDefinition;
        }
    }

    public static void lowerFromConstant(Constant c, JSCodeGenTool jsLTools) {
        assert c instanceof PrimitiveConstant : c;
        long longVal = ((PrimitiveConstant) c).asLong();

        if (longVal == 0) {
            Field longZeroField = ReflectionUtil.lookupField(Long64.class, "LongZero");
            ResolvedJavaField resolvedField = jsLTools.getProviders().getMetaAccess().lookupJavaField(longZeroField);
            jsLTools.genStaticField(resolvedField);
        } else if (Integer.MIN_VALUE <= longVal && longVal <= Integer.MAX_VALUE) {
            Method m = ReflectionUtil.lookupMethod(Long64.class, "fromInt", int.class);
            getJSStaticMethodDefinition(m, jsLTools).emitCall(jsLTools, Emitter.of((int) longVal));
        } else {
            int low = (int) longVal;
            int high = (int) (longVal >> 32);
            Method m = ReflectionUtil.lookupMethod(Long64.class, "fromTwoInt", int.class, int.class);
            getJSStaticMethodDefinition(m, jsLTools).emitCall(jsLTools, Emitter.of(low), Emitter.of(high));
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
    public static Method methodForArithmeticOperation(ValueNode n) {
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
        Class<Long64> long64Class = Long64.class;
        Method method;
        try {
            if (op.equals(arithmeticOpTable.getNeg())) {
                method = long64Class.getMethod("negate", Long64.class);
            } else if (op.equals(arithmeticOpTable.getAdd())) {
                method = long64Class.getMethod("add", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getSub())) {
                method = long64Class.getMethod("sub", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getMul())) {
                method = long64Class.getMethod("mul", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getMulHigh()) || op.equals(arithmeticOpTable.getUMulHigh())) {
                throw GraalError.unimplemented("Unsupported operation mul high in long emulation " + n); // ExcludeFromJacocoGeneratedReport
            } else if (op.equals(arithmeticOpTable.getDiv())) {
                method = long64Class.getMethod("div", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getRem())) {
                method = long64Class.getMethod("mod", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getNot())) {
                method = long64Class.getMethod("not", Long64.class);
            } else if (op.equals(arithmeticOpTable.getAnd())) {
                method = long64Class.getMethod("and", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getOr())) {
                method = long64Class.getMethod("or", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getXor())) {
                method = long64Class.getMethod("xor", Long64.class, Long64.class);
            } else if (op.equals(arithmeticOpTable.getShl())) {
                if (fromNum) {
                    method = long64Class.getMethod("slFromNum", Long64.class, int.class);
                } else {
                    method = long64Class.getMethod("sl", Long64.class, Long64.class);
                }
            } else if (op.equals(arithmeticOpTable.getShr())) {
                if (fromNum) {
                    method = long64Class.getMethod("srFromNum", Long64.class, int.class);
                } else {
                    method = long64Class.getMethod("sr", Long64.class, Long64.class);
                }
            } else if (op.equals(arithmeticOpTable.getUShr())) {
                if (fromNum) {
                    method = long64Class.getMethod("usrFromNum", Long64.class, int.class);
                } else {
                    method = long64Class.getMethod("usr", Long64.class, Long64.class);
                }
            } else if (op.equals(arithmeticOpTable.getAbs())) {
                method = long64Class.getMethod("abs", Long64.class);
            } else if (op.equals(arithmeticOpTable.getZeroExtend())) {
                method = long64Class.getMethod("fromZeroExtend", int.class);
            } else if (op.equals(arithmeticOpTable.getSignExtend())) {
                method = long64Class.getMethod("fromInt", int.class);
            } else if (op.equals(arithmeticOpTable.getNarrow())) {
                method = long64Class.getMethod("lowBits", Long64.class);
            } else if (op.equals(arithmeticOpTable.getFloatConvert(FloatConvert.L2D)) || op.equals(arithmeticOpTable.getFloatConvert(FloatConvert.L2F))) {
                method = long64Class.getMethod("toNumber", Long64.class);
            } else if (op.equals(FloatStamp.OPS.getFloatConvert(FloatConvert.D2L)) || op.equals(FloatStamp.OPS.getFloatConvert(FloatConvert.F2L))) {
                method = long64Class.getMethod("fromDouble", double.class);
            } else {
                throw GraalError.unimplemented("There is no long emulation method in Java for " + n + " with the operation " + op); // ExcludeFromJacocoGeneratedReport
            }
        } catch (NoSuchMethodException e) {
            throw GraalError.shouldNotReachHere(e); // ExcludeFromJacocoGeneratedReport
        }
        return method;
    }

    /**
     * Maps the given {@link BinaryOpLogicNode} to the corresponding method of {@link Long64}.
     */
    public static Method getBinaryLogicMethod(BinaryOpLogicNode n) {
        try {
            if (n instanceof CompareNode) {
                switch (((CompareNode) n).condition()) {
                    case EQ:
                        return Long64.class.getMethod("equal", Long64.class, Long64.class);
                    case LT:
                        return Long64.class.getMethod("lessThan", Long64.class, Long64.class);
                    case BT:
                        return Long64.class.getMethod("belowThan", Long64.class, Long64.class);
                }
            } else if (n instanceof IntegerTestNode) {
                return Long64.class.getMethod("test", Long64.class, Long64.class);
            }
        } catch (NoSuchMethodException e) {
            throw GraalError.shouldNotReachHere(e); // ExcludeFromJacocoGeneratedReport
        }
        throw GraalError.unimplemented("unhandled long64 binary operation logic node" + n); // ExcludeFromJacocoGeneratedReport
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

    private static void genLong64MethodCall(Method long64method, JSCodeGenTool jsLTools, IEmitter... args) {
        JSStaticMethodDefinition long64JSMethod = getJSStaticMethodDefinition(long64method, jsLTools);
        long64JSMethod.emitCall(jsLTools, args);
    }
}
