package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

public class VerifierPrinter {
    public static void print(PrintStream out, LIR lir, BlockMap<List<RAVInstruction.Base>> instructions) {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);

            var blockHeaderSB = new StringBuilder();
            blockHeaderSB.append(block.toString()).append(": ");

            if (block.getPredecessorCount() > 0) {
                blockHeaderSB.append(" <- ");
                for (int i = 0; i < block.getPredecessorCount(); i++) {
                    blockHeaderSB.append(block.getPredecessorAt(i)).append(", ");
                }

                if (!blockHeaderSB.isEmpty()) {
                    blockHeaderSB.setLength(blockHeaderSB.length() - 2);
                }
            }

            if (block.getSuccessorCount() > 0) {
                blockHeaderSB.append(" -> ");
                for (int i = 0; i < block.getSuccessorCount(); i++) {
                    blockHeaderSB.append(block.getSuccessorAt(i)).append(", ");
                }

                if (block.getSuccessorCount() > 0 && !blockHeaderSB.isEmpty()) {
                    blockHeaderSB.setLength(blockHeaderSB.length() - 2);
                }
            }

            out.println(blockHeaderSB);
            for (var instruction : instructions.get(block)) {
                out.println("\t" + instruction.toString() + " | " + instruction.getLIRInstruction().toString());
            }
            out.println();
        }
    }
}
