package org.graalvm.compiler.truffle.pelang.expr;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("leftNode"), @NodeChild("rightNode")})
public abstract class PELangEqualsNode extends PELangExpressionNode {

    @Specialization
    public long equals(long left, long right) {
        if (left == right) {
            return 0L;
        } else {
            return 1L;
        }
    }

    @Specialization
    public long equals(String left, String right) {
        if (left.equals(right)) {
            return 0L;
        } else {
            return 1L;
        }
    }

}
