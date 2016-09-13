package uk.ac.man.cs.llvm.ir.model;

import java.util.ArrayList;
import java.util.List;

import uk.ac.man.cs.llvm.ir.model.metadata.MetadataNode;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class MetadataBlock {

    protected final List<MetadataNode> metadata = new ArrayList<>();

    protected int startIndex = 0;

    public MetadataBlock() {

    }

    public void setStartIndex(int index) {
        startIndex = index;
    }

    public void add(MetadataNode element) {
        metadata.add(element);
    }

    public MetadataNode get(int index) {
        return metadata.get(index - startIndex);
    }

    public MetadataReference getReference(int index) {
        if (index == 0)
            return voidRef;
        else
            return new Reference(index);
    }

    public MetadataReference getReference(long index) {
        return getReference((int) index);
    }

    public MetadataReference getReference(Type t) {
        int index = (int) ((IntegerConstantType) t).getValue(); // TODO
        return getReference(index);
    }

    /**
     * Based on the idea of Optional, but used for automatic forward reference lookup
     */
    public interface MetadataReference extends MetadataNode {
        public boolean isPresent();

        public MetadataNode get();
    }

    public static final VoidReference voidRef = new VoidReference();

    public static class VoidReference implements MetadataReference {

        private VoidReference() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public MetadataNode get() {
            // TODO: better exception
            throw new IndexOutOfBoundsException("That's an empty reference");
        }

        // @Override
        public int getIndex() {
            return -1;
        }

        @Override
        public String toString() {
            return "VoidReference";
        }
    }

    public class Reference implements MetadataReference {
        public final int index;

        private Reference(int index) {
            this.index = index;
        }

        @Override
        public boolean isPresent() {
            return metadata.size() > index;
        }

        @Override
        public MetadataNode get() {
            return metadata.get(index - startIndex);
        }

        // @Override
        public int getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return "!" + index;
        }
    }

}
