package com.oracle.svm.core.graal.amd64;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.core.amd64.AMD64AddressNode;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public class AMD64MaskedAddressNode extends AMD64AddressNode implements LIRLowerable {

    public static final NodeClass<AMD64MaskedAddressNode> TYPE = NodeClass.create(AMD64MaskedAddressNode.class);

    long mask;
    int shift;

    // NOTE: probably the base is not really helpful since it should always be the heap base node in my case
    public AMD64MaskedAddressNode(ValueNode base, ValueNode compressedIndex, long mask, int displacement, int shift) {
        super(TYPE, base, compressedIndex);
        this.displacement = displacement;
        this.mask = mask;
        this.shift = shift;

        // only the displacement from the address node here
        // compression node is still fine
    }


    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        // Getting the base value and reference
        AllocatableValue baseValue = tool.asAllocatable(gen.operand(base));
        AllocatableValue baseReference = LIRKind.derivedBaseFromValue(baseValue);

        // Getting the index value and reference
        AllocatableValue indexValue = tool.asAllocatable(gen.operand(index));
        AllocatableValue indexReference = LIRKind.derivedBaseFromValue(indexValue);

        // Creating the space for the results
        AllocatableValue result = tool.newVariable(LIRKind.reference(AMD64Kind.QWORD)); // FIXME get it properly

        // Creating the space to temporarily store the index before masking.
        AllocatableValue tempIndex = tool.newVariable(LIRKind.reference(AMD64Kind.QWORD));

        // Generating the first AMD64Node to hold the uncompressed index to be masked
        AMD64MaskAddressOp maskedValue = new AMD64MaskAddressOp(baseValue, indexReference, tempIndex, displacement, shift, mask, result); // FIXMe shift
        tool.append(maskedValue);

        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp(NodeView.DEFAULT)), baseValue, indexReference);
        gen.setResult(this, new AMD64AddressValue(kind, result, AllocatableValue.ILLEGAL, Stride.S1, 0));
    }

}
