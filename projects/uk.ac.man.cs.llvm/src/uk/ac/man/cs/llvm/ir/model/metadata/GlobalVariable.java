package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class GlobalVariable implements MetadataNode {

    protected MetadataReference context = MetadataBlock.voidRef;
    protected MetadataReference name = MetadataBlock.voidRef;
    protected MetadataReference displayName = MetadataBlock.voidRef;
    protected MetadataReference linkageName = MetadataBlock.voidRef;
    protected MetadataReference file = MetadataBlock.voidRef;
    protected long line;
    protected MetadataReference type = MetadataBlock.voidRef;
    protected boolean isLocalToCompileUnit;
    protected boolean isDefinedInCompileUnit;

    public GlobalVariable() {
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

    public MetadataReference getDisplayName() {
        return displayName;
    }

    public void setDisplayName(MetadataReference displayName) {
        this.displayName = displayName;
    }

    public MetadataReference getLinkageName() {
        return linkageName;
    }

    public void setLinkageName(MetadataReference linkageName) {
        this.linkageName = linkageName;
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

    public MetadataReference getType() {
        return type;
    }

    public void setType(MetadataReference type) {
        this.type = type;
    }

    public boolean isLocalToCompileUnit() {
        return isLocalToCompileUnit;
    }

    public void setLocalToCompileUnit(boolean isLocalToCompileUnit) {
        this.isLocalToCompileUnit = isLocalToCompileUnit;
    }

    public boolean isDefinedInCompileUnit() {
        return isDefinedInCompileUnit;
    }

    public void setDefinedInCompileUnit(boolean isDefinedInCompileUnit) {
        this.isDefinedInCompileUnit = isDefinedInCompileUnit;
    }

    @Override
    public String toString() {
        return "GlobalVariable [context=" + context + ", name=" + name + ", displayName=" + displayName + ", linkageName=" + linkageName + ", file=" + file + ", line=" + line + ", type=" + type +
                        ", isLocalToCompileUnit=" + isLocalToCompileUnit + ", isDefinedInCompileUnit=" + isDefinedInCompileUnit + "]";
    }
}
