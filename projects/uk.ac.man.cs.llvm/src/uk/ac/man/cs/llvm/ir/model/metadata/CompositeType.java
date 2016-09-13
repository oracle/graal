package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class CompositeType implements MetadataNode {

    protected MetadataReference context = MetadataBlock.voidRef;
    protected MetadataReference name = MetadataBlock.voidRef;
    protected MetadataReference file = MetadataBlock.voidRef;
    protected long line;
    protected long size;
    protected long align;
    protected long offset;
    protected long flags;
    protected MetadataReference derivedFrom = MetadataBlock.voidRef;
    protected MetadataReference memberDescriptors = MetadataBlock.voidRef;
    protected long runtimeLanguage;

    public CompositeType() {
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

    public MetadataReference getDerivedFrom() {
        return derivedFrom;
    }

    public void setDerivedFrom(MetadataReference derivedFrom) {
        this.derivedFrom = derivedFrom;
    }

    public MetadataReference getMemberDescriptors() {
        return memberDescriptors;
    }

    public void setMemberDescriptors(MetadataReference memberDescriptors) {
        this.memberDescriptors = memberDescriptors;
    }

    public long getRuntimeLanguage() {
        return runtimeLanguage;
    }

    public void setRuntimeLanguage(long runtimeLanguage) {
        this.runtimeLanguage = runtimeLanguage;
    }

    @Override
    public String toString() {
        return "CompositeType [context=" + context + ", name=" + name + ", file=" + file + ", line=" + line + ", size=" + size + ", align=" + align + ", offset=" + offset + ", flags=" + flags +
                        ", derivedFrom=" + derivedFrom + ", memberDescriptors=" + memberDescriptors + ", runtimeLanguage=" + runtimeLanguage + "]";
    }

}
