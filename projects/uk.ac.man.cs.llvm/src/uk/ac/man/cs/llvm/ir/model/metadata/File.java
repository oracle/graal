package uk.ac.man.cs.llvm.ir.model.metadata;

import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.MetadataBlock.MetadataReference;

public class File implements MetadataNode {

    protected MetadataReference file = MetadataBlock.voidRef;
    protected MetadataReference directory = MetadataBlock.voidRef;

    public File() {
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

    @Override
    public String toString() {
        return "File [file=" + file + ", directory=" + directory + "]";
    }

}
