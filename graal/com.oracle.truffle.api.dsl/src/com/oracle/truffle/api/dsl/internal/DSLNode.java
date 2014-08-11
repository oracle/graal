package com.oracle.truffle.api.dsl.internal;

import com.oracle.truffle.api.nodes.*;

/**
 * This is NOT public API. Do not use directly. This code may change without notice.
 */
public interface DSLNode {

    DSLMetadata getMetadata0();

    void adoptChildren0(Node other, Node next);

    void updateTypes0(Class<?>[] types);

    Node getNext0();

}
