package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class EqualsNode extends WasmBuiltinRootNode {
    protected EqualsNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "equals";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        var s0 = args[0];
        var s1 = args[1];
        var interop = InteropLibrary.getUncached();
        if (!interop.isNull(s0) && !(s0 instanceof TruffleString)) throw new RuntimeException("Argument 0 was not a string");
        if (!interop.isNull(s1) && !(s1 instanceof TruffleString)) throw new RuntimeException("Argument 1 was not a string");
        if (interop.isNull(s0)) return interop.isNull(s1) ? 1 : 0;
        return s0.equals(s1) ? 1 : 0;
    }
}
