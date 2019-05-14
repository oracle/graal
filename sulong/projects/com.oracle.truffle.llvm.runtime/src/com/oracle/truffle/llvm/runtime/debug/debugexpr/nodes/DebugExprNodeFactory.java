package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprSymbolTable.TabObj;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceBasicType.Kind;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

public class DebugExprNodeFactory {
    private static DebugExprNodeFactory instance;

    public static DebugExprNodeFactory Get() {
        if (instance == null)
            instance = new DebugExprNodeFactory();
        return instance;
    }

    private DebugExprNodeFactory() {
    }

    public DebugExprOperandNode createTabNode(TabObj tabObj) {
        return new DebugExprOperandNode(tabObj.type, tabObj.value);
    }

    public DebugExprOperandNode createIntNode(int value) {
        return new DebugExprOperandNode("int", value);
    }

    public DebugExprOperandNode createFloatNode(float value) {
        return new DebugExprOperandNode("float", value);
    }

    public DebugExprOperandNode createStringNode(String value) {
        return new DebugExprOperandNode("String", value);
    }

    public DebugExprOperandNode createAddNode(DebugExprOperandNode left, DebugExprOperandNode right, LLVMContext context) {
        // LLVMExpressionNode n =
        // context.getNodeFactory().createArithmeticOp(ArithmeticOperation.ADD,
        // Type.getIntegerType(4), left, right);
        if (left.type.toString().contentEquals("int") && right.type.toString().contentEquals("int")) {
            int sum = Integer.parseInt(left.value.toString()) + Integer.parseInt(right.value.toString());
            return new DebugExprOperandNode(left.type, context.getNodeFactory().createSimpleConstantNoArray(sum, Type.getIntegerType(32)));
        }
        return left;
    }

    public DebugExprOperandNode createSubNode(DebugExprOperandNode left, DebugExprOperandNode right) {
        return left;
    }

    public DebugExprOperandNode createMulNode(DebugExprOperandNode left, DebugExprOperandNode right) {
        return left;
    }

    public DebugExprOperandNode createDivNode(DebugExprOperandNode left, DebugExprOperandNode right) {
        return left;
    }

    public DebugExprOperandNode createRemNode(DebugExprOperandNode left, DebugExprOperandNode right) {
        return left;
    }

    public DebugExprOperandNode createLogAndNode(DebugExprOperandNode left, DebugExprOperandNode right) {
        return left;
    }

}
