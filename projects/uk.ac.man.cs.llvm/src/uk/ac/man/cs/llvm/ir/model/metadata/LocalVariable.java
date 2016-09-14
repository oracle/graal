package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class LocalVariable implements MetadataNode {

    protected MetadataReference context = MetadataBlock.voidRef;
    protected MetadataReference name = MetadataBlock.voidRef;
    protected MetadataReference file = MetadataBlock.voidRef;
    protected long line;
    protected long arg;
    protected MetadataReference type = MetadataBlock.voidRef;
    protected long flags;

    public LocalVariable() {
    }

    public MetadataReference getContext() {
        return context;
    }

    public void setContext(MetadataReference context) {
        this.context = context;
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

    public long getArg() {
        return arg;
    }

    public void setArg(long arg) {
        this.arg = arg;
    }

    public MetadataReference getType() {
        return type;
    }

    public void setType(MetadataReference type) {
        this.type = type;
    }

    public long getFlags() {
        return flags;
    }

    public void setFlags(long flags) {
        this.flags = flags;
    }

    @Override
    public String toString() {
        return "LocalVariable [context=" + context + ", name=" + name + ", file=" + file + ", line=" + line + ", arg=" + arg + ", type=" + type + ", flags=" + flags + "]";
    }
}
