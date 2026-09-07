package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class ConcatNode extends WasmBuiltinRootNode {
    protected ConcatNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "concat";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        var arg0 = args[0];
        var arg1 = args[1];
        if (!(arg0 instanceof TruffleString s0)) throw new RuntimeException("Argument 0 was not a string");
        if (!(arg1 instanceof TruffleString s1)) throw new RuntimeException("Argument 1 was not a string");
        return s0.concatUncached(s1, TruffleString.Encoding.UTF_16, false);
    }
}
