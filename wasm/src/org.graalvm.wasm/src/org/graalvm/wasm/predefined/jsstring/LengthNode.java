package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class LengthNode extends WasmBuiltinRootNode {

    protected LengthNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "length";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var arg = WasmArguments.getArguments(frame.getArguments())[0];
        if (!(arg instanceof TruffleString s)) throw WasmException.create(Failure.TYPE_MISMATCH);
        return s.byteLength(TruffleString.Encoding.UTF_16)/2;
    }
}
