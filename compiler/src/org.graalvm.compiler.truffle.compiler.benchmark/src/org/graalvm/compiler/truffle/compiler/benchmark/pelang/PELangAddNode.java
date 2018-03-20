package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("leftNode"), @NodeChild("rightNode")})
public abstract class PELangAddNode extends PELangExpressionNode {

    @Specialization
    public long add(long left, long right) {
        return left + right;
    }

    @Specialization
    public String add(String left, String right) {
        return left + right;
    }

}
