package org.graalvm.continuations;

import java.io.IOException;
import java.io.ObjectOutput;

/**
 * Marker class for "continuation-related objects" that needs special coordination with non-jdk
 * serialization frameworks.
 */
abstract class ContinuationExternalSerializable {
    ContinuationExternalSerializable() {
    }

    abstract void writeObjectExternal(ObjectOutput out) throws IOException;
}
