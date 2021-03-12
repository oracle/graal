package com.oracle.truffle.espresso.redefinition.plugins.api;

import java.util.Set;

public interface RedefinitionAction {
    Set<byte[]> onChange();
}
