package com.oracle.objectfile.elf.dwarf;

import java.util.HashMap;
import java.util.Iterator;

// class which reduces incoming strings to unique
// instances and also marks strings which need
// to be written to the debug_str section
public class StringTable implements Iterable<StringEntry> {

    private final HashMap<String, StringEntry> table;

    public StringTable() {
        this.table = new HashMap<String, StringEntry>();
    }

    public String uniqueString(String string) {
        return ensureString(string, false);
    }

    public String uniqueDebugString(String string) {
        return ensureString(string, true);
    }

    private String ensureString(String string, boolean addToStrSection) {
        StringEntry stringEntry = table.get(string);
        if (stringEntry == null) {
            stringEntry = new StringEntry(string);
            table.put(string, stringEntry);
        }
        if (addToStrSection && !stringEntry.isAddToStrSection()) {
            stringEntry.setAddToStrSection();
        }
        return stringEntry.getString();
    }

    public int debugStringIndex(String string) {
        StringEntry stringEntry = table.get(string);
        assert stringEntry != null;
        if (stringEntry == null) {
            return -1;
        }
        return stringEntry.getOffset();
    }
    public Iterator<StringEntry> iterator() {
        return table.values().iterator();
    }
}
