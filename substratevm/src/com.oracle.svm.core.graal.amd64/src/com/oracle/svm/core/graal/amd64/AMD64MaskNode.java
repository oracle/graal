package com.oracle.svm.core.graal.amd64;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class AMD64MaskNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<AMD64MaskNode> TYPE = NodeClass.create(AMD64MaskNode.class);

    int displacement;
    @Input
    ValueNode compressedAddress;
    long mask;
    @Input
    ValueNode baseNode;

    int shift;

    public AMD64MaskNode(Stamp stamp, long mask, int displacement, ValueNode compressedAddress, ValueNode baseNode, int shift) {
        super(TYPE, stamp);
        this.displacement = displacement;
        this.mask = mask;
        this.compressedAddress = compressedAddress;
        this.baseNode = baseNode;
        this.shift = shift;

        // only the displacement from the address node here
        // compression node is still fine
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap) {
        ArithmeticLIRGeneratorTool gen = nodeValueMap.getLIRGeneratorTool().getArithmetic();
        LIRGeneratorTool tool = nodeValueMap.getLIRGeneratorTool();

        // Generating the first AMD64Node to hold the uncompressed index to be masked
        AllocatableValue result = tool.newVariable(LIRKind.reference(AMD64Kind.QWORD)); // FIXME get it properly
        AllocatableValue tempIndex = tool.newVariable(LIRKind.reference(AMD64Kind.QWORD));
        AMD64MaskAddressOp maskedValue = new AMD64MaskAddressOp(nodeValueMap.operand(baseNode), nodeValueMap.operand(compressedAddress), tempIndex, displacement, this.shift, mask, result); // FIXMe shift
        tool.append(maskedValue);
        nodeValueMap.setResult(this, result);

//        AllocatableValue baseValue = tool.asAllocatable(nodeValueMap.operand(this.baseNode));
//        AllocatableValue indexValue = tool.asAllocatable(nodeValueMap.operand(compressionNode.getValue()));
//        Value shiftConst = new ConstantValue(tool.getValueKind(JavaKind.Long), JavaConstant.forLong(compressionNode.getEncoding().getShift()));
//        Value shiftedIndex = gen.emitShl(indexValue, shiftConst);
//        Value dispConst = new ConstantValue(tool.getValueKind(JavaKind.Long), JavaConstant.forLong(this.displacement));
//        Value dispIndex = gen.emitAdd(shiftedIndex, dispConst, false); // TODO: checks if the flags needs adkustment
//        Value maskConst = new ConstantValue(tool.getValueKind(JavaKind.Long), JavaConstant.forLong(this.mask));
//        Value extendedDisp = gen.emitSignExtend(dispIndex, 32, 64);
//        AllocatableValue maskedIndex = tool.asAllocatable(gen.emitAnd(extendedDisp, maskConst));
//
//        LIRKind kind = LIRKind.combine(indexValue);
//        nodeValueMap.setResult(this, new AMD64AddressValue(kind, baseValue, maskedIndex, Stride.S1, 0));


    }

}
