package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class CompileUnit implements MetadataNode {

    protected long context;
    protected long language;
    protected MetadataReference file = MetadataBlock.voidRef;
    protected MetadataReference directory = MetadataBlock.voidRef;
    protected MetadataReference producer = MetadataBlock.voidRef;
    protected boolean isDeprecatedField;
    protected boolean isOptimized;
    protected MetadataReference flags = MetadataBlock.voidRef;
    protected long runtimeVersion;
    protected MetadataReference enumType = MetadataBlock.voidRef;
    protected MetadataReference retainedTypes = MetadataBlock.voidRef;
    protected MetadataReference subprograms = MetadataBlock.voidRef;
    protected MetadataReference globalVariables = MetadataBlock.voidRef;

    public CompileUnit() {
    }

    public long getContext() {
        return context;
    }

    public void setContext(long context) {
        this.context = context;
    }

    public long getLanguage() {
        return language;
    }

    public void setLanguage(long language) {
        this.language = language;
    }

    public MetadataReference getFile() {
        return file;
    }

    public void setFile(MetadataReference file) {
        this.file = file;
    }

    public MetadataReference getDirectory() {
        return directory;
    }

    public void setDirectory(MetadataReference directory) {
        this.directory = directory;
    }

    public MetadataReference getProducer() {
        return producer;
    }

    public void setProducer(MetadataReference producer) {
        this.producer = producer;
    }

    public boolean isDeprecatedField() {
        return isDeprecatedField;
    }

    public void setDeprecatedField(boolean isDeprecatedField) {
        this.isDeprecatedField = isDeprecatedField;
    }

    public boolean isOptimized() {
        return isOptimized;
    }

    public void setOptimized(boolean isOptimized) {
        this.isOptimized = isOptimized;
    }

    public MetadataReference getFlags() {
        return flags;
    }

    public void setFlags(MetadataReference flags) {
        this.flags = flags;
    }

    public long getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(long runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public MetadataNode getEnumType() {
        return enumType;
    }

    public void setEnumType(MetadataReference enumType) {
        this.enumType = enumType;
    }

    public MetadataReference getRetainedTypes() {
        return retainedTypes;
    }

    public void setRetainedTypes(MetadataReference retainedTypes) {
        this.retainedTypes = retainedTypes;
    }

    public MetadataReference getSubprograms() {
        return subprograms;
    }

    public void setSubprograms(MetadataReference subprograms) {
        this.subprograms = subprograms;
    }

    public MetadataReference getGlobalVariables() {
        return globalVariables;
    }

    public void setGlobalVariables(MetadataReference globalVariables) {
        this.globalVariables = globalVariables;
    }

    @Override
    public String toString() {
        return "CompileUnit [context=" + context + ", language=" + language + ", file=" + file + ", directory=" + directory + ", producer=" + producer + ", isDeprecatedField=" + isDeprecatedField +
                        ", isOptimized=" + isOptimized + ", flags=" + flags + ", runtimeVersion=" + runtimeVersion + ", enumType=" + enumType + ", retainedTypes=" + retainedTypes + ", subprograms=" +
                        subprograms + ", globalVariables=" + globalVariables + "]";
    }
}
