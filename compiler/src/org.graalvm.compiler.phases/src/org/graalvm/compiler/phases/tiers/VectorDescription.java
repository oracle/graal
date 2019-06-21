package org.graalvm.compiler.phases.tiers;

import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;

public abstract class VectorDescription {

    /**
     * Returns the maximum number of elements with the given stamp that can be packed into a vector
     * on the current architecture.
     *
     * @param stamp
     * @return max vector width in elements
     */
    public int maxVectorWidth(Stamp stamp) {
        if (!(stamp instanceof PrimitiveStamp)) {
            return 1;
        } else {
            return maxVectorWidth((PrimitiveStamp) stamp);
        }
    }

    protected abstract int maxVectorWidth(PrimitiveStamp stamp);
}
