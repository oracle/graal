package org.graalvm.compiler.replacements.aarch64;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.phases.Phase;

/**
 * AArch64-specific phase which substitutes certain read nodes
 * with arch-specific variants in order to allow merging of
 * zero and sign extension into the read operation
 */
public class AArch64ReadReplacementPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph)
    {
        for (Node node : graph.getNodes()) {
            // don't process nodes we just added
            if (node instanceof AArch64ReadNode) {
                continue;
            }
            if (node instanceof ReadNode) {
                ReadNode readNode = (ReadNode) node;
                if (readNode.getUsageCount() == 1) {
                    Node usage = readNode.getUsageAt(0);
                    if (usage instanceof ZeroExtendNode || usage instanceof SignExtendNode) {
                        AArch64ReadNode.replace(readNode);
                    }
                }
            }
        }
    }
}
