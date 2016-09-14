package uk.ac.man.cs.llvm.ir.model.metadata;

public class Name implements MetadataNode {
    protected final String name;

    public Name(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Name [\"" + name + "\"]";
    }
}
