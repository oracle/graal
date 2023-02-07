package com.oracle.svm.core.jfr;

import org.graalvm.word.SignedWord;
public class JfrMetadata {
    private volatile long currentMetadataId;
    private volatile byte[] metadataDescriptor;
    private volatile boolean isDirty;
    private SignedWord metadataPosition;
    public JfrMetadata(byte[] bytes){
        metadataDescriptor = bytes;
        currentMetadataId = 0;
        isDirty = false;
    }

    public void setDescriptor(byte[] bytes) {
        metadataDescriptor = bytes;
        currentMetadataId++;
        isDirty = true;
    }

    public byte[] getDescriptorAndClearDirtyFlag() {
        isDirty = false;
        return metadataDescriptor;
    }
    public boolean isDirty() {
        return isDirty;
    }

    public void setMetadataPosition(SignedWord metadataPosition) {
        this.metadataPosition = metadataPosition;
    }

    public SignedWord getMetadataPosition() {
        return metadataPosition;
    }

    public long getCurrentMetadataId(){
        return currentMetadataId;
    }
}
