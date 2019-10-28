package com.oracle.truffle.espresso.jdwp.api;

public interface LineNumberTableRef {

    EntryRef[] getEntries();

    interface EntryRef {

        int getBCI();

        int getLineNumber();
    }
}
