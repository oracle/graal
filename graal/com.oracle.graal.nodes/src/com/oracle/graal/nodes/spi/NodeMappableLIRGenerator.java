package com.oracle.graal.nodes.spi;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

public interface NodeMappableLIRGenerator {

    Value operand(ValueNode object);

    Value setResult(ValueNode x, Value operand);

}