package uk.ac.man.cs.llvm.ir.model.metadata;

public class Subrange implements MetadataNode {

    protected long lowBound;
    protected long size;

    public Subrange() {
    }

    public long getLowBound() {
        return lowBound;
    }

    public void setLowBound(long lowBound) {
        this.lowBound = lowBound;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "Subrange [lowBound=" + lowBound + ", size=" + size + "]";
    }

}
