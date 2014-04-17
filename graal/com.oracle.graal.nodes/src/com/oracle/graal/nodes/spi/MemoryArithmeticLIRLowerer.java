/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nodes.spi;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.extended.*;

/**
 * This interface can be used to generate LIR for arithmetic operations where one of the operations
 * is load (@see ArithmeticLIRLowerable).
 */
public interface MemoryArithmeticLIRLowerer {

    Value setResult(ValueNode x, Value operand);

    Value emitAddMemory(ValueNode x, ValueNode y, Access access);

    Value emitMulMemory(ValueNode x, ValueNode y, Access access);

    Value emitSubMemory(ValueNode x, ValueNode y, Access access);

    Value emitDivMemory(ValueNode x, ValueNode y, Access access);

    Value emitRemMemory(ValueNode x, ValueNode y, Access access);

    Value emitXorMemory(ValueNode x, ValueNode y, Access access);

    Value emitOrMemory(ValueNode x, ValueNode y, Access access);

    Value emitAndMemory(ValueNode x, ValueNode y, Access access);

    Value emitReinterpretMemory(Stamp stamp, Access access);

    Value emitSignExtendMemory(Access access, int fromBits, int toBits);

    Value emitNarrowMemory(int resultBits, Access access);

    Value emitZeroExtendMemory(int inputBits, int resultBits, Access access);

    Value emitFloatConvertMemory(FloatConvert op, Access access);

    boolean memoryPeephole(Access valueNode, MemoryArithmeticLIRLowerable operation, List<ValueNode> deferred);

    boolean emitIfMemory(IfNode ifNode, Access access);

}
