package uk.ac.man.cs.llvm.ir.model.metadata;

public class Kind implements MetadataNode {

    protected final long id;
    protected final String name;

    public Kind(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Kind [id=" + id + ", name=\"" + name + "\"]";
    }

}
