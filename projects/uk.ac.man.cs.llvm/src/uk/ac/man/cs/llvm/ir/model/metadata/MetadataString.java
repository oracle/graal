package uk.ac.man.cs.llvm.ir.model.metadata;

public class MetadataString implements MetadataNode {
    protected final String s;

    public MetadataString(String s) {
        this.s = s;
    }

    public String getString() {
        return s;
    }

    @Override
    public String toString() {
        return "MetadataString [\"" + s + "\"]";
    }
}
