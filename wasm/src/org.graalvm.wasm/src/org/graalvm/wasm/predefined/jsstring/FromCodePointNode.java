package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class FromCodePointNode extends WasmBuiltinRootNode {

    @Child
    private TruffleString.FromCodePointNode fromCodePointNode = TruffleString.FromCodePointNode.create();

    protected FromCodePointNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "fromCodePoint";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        int codePoint = ((Number)args[0]).intValue();
        // if (codePoint > 0x10FFFF) throw new RuntimeException(String.format("RangeError: %d is not a valid code point", codePoint));
        return fromCodePointNode.execute(codePoint, TruffleString.Encoding.UTF_16, true);
    }
}
