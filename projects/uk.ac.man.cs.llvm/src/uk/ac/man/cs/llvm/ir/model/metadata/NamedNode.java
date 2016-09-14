package uk.ac.man.cs.llvm.ir.model.metadata;

import java.util.ArrayList;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class NamedNode extends ArrayList<MetadataReference> implements MetadataNode {

    public NamedNode() {
    }

    @Override
    public String toString() {
        return "NamedNode " + super.toString();
    }
}