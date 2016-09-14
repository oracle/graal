package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class LexicalBlockFile implements MetadataNode {

    protected MetadataReference file = MetadataBlock.voidRef;

    public LexicalBlockFile() {
    }

    public MetadataReference getFile() {
        return file;
    }

    public void setFile(MetadataReference file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "LexicalBlockFile [file=" + file + "]";
    }

}
