package com.oracle.objectfile.elf.dwarf;

// class used to ensure we keep only one copy of a String
// amd track it's locations in the string section should
// it need to be entered
public class StringEntry {
    private String string;
    private int offset;
    private boolean addToStrSection;

    StringEntry(String string) {
        this.string = string;
        this.offset = -1;
    }
    public String getString() {
        return string;
    }
    public int getOffset() {
        // offset must be set before this can be fetched
        assert offset >= 0;
        return offset;
    }
    public void setOffset(int offset) {
        assert this.offset < 0;
        assert offset >= 0;
        this.offset = offset;
    }
    public boolean isAddToStrSection() {
        return addToStrSection;
    }
    public void setAddToStrSection() {
        this.addToStrSection = true;
    }
    public boolean equals(Object object) {
        if (object == null || !(object instanceof StringEntry)) {
            return false;
        } else {
            StringEntry other = (StringEntry)object;
            return this == other || string.equals(other.string);
        }
    }
    public int hashCode() {
        return string.hashCode() + 37;
    }
    public String toString() {
        return string;
    }
    public boolean isEmpty() {
        return string.length() == 0;
    }
}
