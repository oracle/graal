package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.espresso.meta.Meta;

public interface LinkedNode {
    Meta.Method getOriginalMethod();
}
