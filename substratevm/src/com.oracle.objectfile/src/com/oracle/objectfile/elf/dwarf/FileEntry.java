package com.oracle.objectfile.elf.dwarf;

public class FileEntry
{
    // the name of the associated file including path
    private String fileName;
    // the name of the associated file excluding path
    private String baseName;
    // the directory entry associated with this file entry
    DirEntry dirEntry;

    public FileEntry(String fileName, String baseName, DirEntry dirEntry)
    {
        this.fileName = fileName;
        this.baseName = baseName;
        this.dirEntry = dirEntry;
    }

    public String getFileName() {
        return fileName;
    }
    public String getBaseName() {
        return baseName;
    }
    String getDirName() {
        return (dirEntry != null ? dirEntry.getPath() : "");
    }
}
