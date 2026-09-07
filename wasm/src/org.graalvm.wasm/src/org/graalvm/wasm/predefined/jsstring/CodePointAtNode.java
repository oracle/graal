package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class CodePointAtNode extends WasmBuiltinRootNode {
    protected CodePointAtNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "codePointAt";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        var arg = args[0];
        if (!(arg instanceof TruffleString s)) throw new RuntimeException("Argument 0 was not a string");
        int byteIndex = ((int) args[1]) * 2;
        return TruffleString.CodePointAtByteIndexNode.create().execute(s, byteIndex, TruffleString.Encoding.UTF_16);
    }
}
