package org.graalvm.wasm.exception;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class WasmTailCallException extends ControlFlowException {
    public final CallTarget callTarget;
    public final Object[] arguments;

    public WasmTailCallException(CallTarget callTarget, Object[] arguments){
        this.callTarget = callTarget;
        this.arguments = arguments;
    }
}
