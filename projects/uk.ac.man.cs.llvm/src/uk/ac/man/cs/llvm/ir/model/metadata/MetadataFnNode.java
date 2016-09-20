package uk.ac.man.cs.llvm.ir.model.metadata;

public class MetadataFnNode implements MetadataBaseNode {

    private final int value;

    public MetadataFnNode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "MetadataFnNode [" + value + "]";
    }

}
