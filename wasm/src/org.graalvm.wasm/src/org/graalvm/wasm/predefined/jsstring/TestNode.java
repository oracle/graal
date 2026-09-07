package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class TestNode extends WasmBuiltinRootNode {
    protected TestNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "test";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        return WasmArguments.getArguments(frame.getArguments())[0] instanceof TruffleString ? 1 : 0;
    }
}
