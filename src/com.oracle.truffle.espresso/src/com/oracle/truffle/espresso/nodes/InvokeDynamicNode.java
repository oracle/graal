package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.CallSiteObject;

public class InvokeDynamicNode extends QuickNode {

    private final CallSiteObject cso;
    private final int paramCount;

    public InvokeDynamicNode(CallSiteObject cso) {
        this.paramCount = Signatures.parameterCount(cso.getSignature(), false);
        this.cso = cso;
    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        Object[] args = root.peekArguments(frame, top, false, cso.getSignature());
        int modif = -paramCount;
        cso.setArgs(args);
        return root.putKind(frame, top + modif, cso, JavaKind.Object) + modif;
    }
}
