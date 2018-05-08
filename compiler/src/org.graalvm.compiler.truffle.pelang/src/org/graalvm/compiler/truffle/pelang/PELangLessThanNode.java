package org.graalvm.compiler.truffle.pelang;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("leftNode"), @NodeChild("rightNode")})
public abstract class PELangLessThanNode extends PELangExpressionNode {

    @Specialization
    public long lessThan(long left, long right) {
        if (left < right) {
            return 0L;
        } else {
            return 1L;
        }
    }

    @Specialization
    public long lessThan(String left, String right) {
        if (left.compareTo(right) == -1) {
            return 0L;
        } else {
            return 1L;
        }
    }

}
