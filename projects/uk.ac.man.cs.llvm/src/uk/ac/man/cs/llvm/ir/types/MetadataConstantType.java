package uk.ac.man.cs.llvm.ir.types;

public class MetadataConstantType implements Type {

    private final long value;

    public MetadataConstantType(long value) {
        this.value = value;
    }

    @Override
    public MetaType getType() {
        return MetaType.METADATA;
    }

    public long getValue() {
        return value;
    }

    @Override
    public int sizeof() {
        return getType().sizeof();
    }

    @Override
    public String toString() {
        return String.format("%s %d", getType(), value);
    }

}
