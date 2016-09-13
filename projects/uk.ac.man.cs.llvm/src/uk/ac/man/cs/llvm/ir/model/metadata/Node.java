package uk.ac.man.cs.llvm.ir.model.metadata;

import java.util.ArrayList;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class Node extends ArrayList<MetadataReference> implements MetadataNode {

    public Node() {
    }

    @Override
    public String toString() {
        return "Node " + super.toString();
    }
}
