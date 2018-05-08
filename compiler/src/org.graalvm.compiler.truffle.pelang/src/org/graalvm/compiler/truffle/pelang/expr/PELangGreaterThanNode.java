package org.graalvm.compiler.truffle.pelang.expr;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("leftNode"), @NodeChild("rightNode")})
public abstract class PELangGreaterThanNode extends PELangExpressionNode {

    @Specialization
    public long greaterThan(long left, long right) {
        if (left > right) {
            return 0L;
        } else {
            return 1L;
        }
    }

    @Specialization
    public long greaterThan(String left, String right) {
        if (left.compareTo(right) == 1) {
            return 0L;
        } else {
            return 1L;
        }
    }

}
