package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class BasicType implements MetadataNode {
    // http://llvm.org/releases/3.2/docs/SourceLevelDebugging.html#format_basic_type

    protected MetadataReference name = MetadataBlock.voidRef;
    protected MetadataReference file = MetadataBlock.voidRef;
    protected long line;
    protected long size;
    protected long align;
    protected long offset;
    protected long flags;
    protected long encoding; // DWARF type encoding

    public BasicType() {
    }

    public MetadataReference getName() {
        return name;
    }

    public void setName(MetadataReference name) {
        this.name = name;
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

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getAlign() {
        return align;
    }

    public void setAlign(long align) {
        this.align = align;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getFlags() {
        return flags;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    public long getEncoding() {
        return encoding;
    }

    public void setEncoding(long encoding) {
        this.encoding = encoding;
    }

    @Override
    public String toString() {
        return "BasicType [name=" + name + ", file=" + file + ", line=" + line + ", size=" + size + ", align=" + align + ", offset=" + offset + ", flags=" + flags + ", encoding=" + encoding + "]";
    }

}
