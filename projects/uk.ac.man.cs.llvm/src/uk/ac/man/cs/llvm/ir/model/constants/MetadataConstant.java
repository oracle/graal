package uk.ac.man.cs.llvm.ir.model.constants;

import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.types.MetaType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class MetadataConstant implements Symbol {

    private final long value;

    public MetadataConstant(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public Type getType() {
        return MetaType.METADATA;
    }

}
