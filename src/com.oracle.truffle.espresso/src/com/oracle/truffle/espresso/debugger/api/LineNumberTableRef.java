package com.oracle.truffle.espresso.debugger.api;

public interface LineNumberTableRef {

    EntryRef[] getEntries();

    interface EntryRef {

        int getBCI();

        int getLineNumber();
    }
}
