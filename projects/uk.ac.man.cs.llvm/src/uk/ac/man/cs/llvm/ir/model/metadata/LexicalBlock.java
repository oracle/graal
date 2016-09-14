package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class LexicalBlock implements MetadataNode {

    protected MetadataReference file = MetadataBlock.voidRef;
    protected long line;
    protected long column;

    public LexicalBlock() {
    }

    public MetadataReference getFile() {
        return file;
    }

    public void setFile(MetadataReference file) {
        this.file = file;
    }

    public long getLine() {
        return line;
    }

    public void setLine(long line) {
        this.line = line;
    }

    public long getColumn() {
        return column;
    }

    public void setColumn(long column) {
        this.column = column;
    }

    @Override
    public String toString() {
        return "LexicalBlock [file=" + file + ", line=" + line + ", column=" + column + "]";
    }
}
