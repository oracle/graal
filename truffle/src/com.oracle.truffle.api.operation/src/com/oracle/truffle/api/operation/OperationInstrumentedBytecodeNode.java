package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class OperationInstrumentedBytecodeNode extends OperationBytecodeNode {

    protected OperationInstrumentedBytecodeNode(int maxStack, int maxLocals, byte[] bc, Object[] consts, Node[] children, BuilderExceptionHandler[] handlers, ConditionProfile[] conditionProfiles) {
        super(maxStack, maxLocals, bc, consts, children, handlers, conditionProfiles);
    }

    @Override
    boolean isInstrumented() {
        return true;
    }
}
