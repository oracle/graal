package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class Subprogram implements MetadataNode {

    protected MetadataReference name = MetadataBlock.voidRef;
    protected MetadataReference linkageName = MetadataBlock.voidRef;
    protected MetadataReference file = MetadataBlock.voidRef;
    protected long line;
    protected MetadataReference type = MetadataBlock.voidRef;
    protected boolean isLocalToUnit;
    protected boolean isDefinition;
    protected long scopeLine;
    protected MetadataReference containingType = MetadataBlock.voidRef;
    // long virtuallity = args[11];
    // long virtualIndex = args[12];
    protected MetadataReference flags = MetadataBlock.voidRef;
    protected boolean isOptimized;
    // long templateParams = args[15];
    // long declaration = args[16];
    // long variables = args[17];

    public Subprogram() {
    }

    public MetadataReference getName() {
        return name;
    }

    public void setName(MetadataReference name) {
        this.name = name;
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

    public boolean isLocalToUnit() {
        return isLocalToUnit;
    }

    public void setLocalToUnit(boolean isLocalToUnit) {
        this.isLocalToUnit = isLocalToUnit;
    }

    public boolean isDefinition() {
        return isDefinition;
    }

    public void setDefinition(boolean isDefinition) {
        this.isDefinition = isDefinition;
    }

    public long getScopeLine() {
        return scopeLine;
    }

    public void setScopeLine(long scopeLine) {
        this.scopeLine = scopeLine;
    }

    public MetadataReference getContainingType() {
        return containingType;
    }

    public void setContainingType(MetadataReference containingType) {
        this.containingType = containingType;
    }

    public MetadataReference getFlags() {
        return flags;
    }

    public void setFlags(MetadataReference flags) {
        this.flags = flags;
    }

    public boolean isOptimized() {
        return isOptimized;
    }

    public void setOptimized(boolean isOptimized) {
        this.isOptimized = isOptimized;
    }

    @Override
    public String toString() {
        return "Subprogram [name=" + name + ", linkageName=" + linkageName + ", file=" + file + ", line=" + line + ", type=" + type + ", isLocalToUnit=" + isLocalToUnit + ", isDefinition=" +
                        isDefinition + ", scopeLine=" + scopeLine + ", containingType=" + containingType + ", flags=" + flags + ", isOptimized=" + isOptimized + "]";
    }

}
