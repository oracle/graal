package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class CompareNode extends WasmBuiltinRootNode {

    @Child
    private TruffleString.CompareCharsUTF16Node compareCharsUTF16Node = TruffleString.CompareCharsUTF16Node.create();

    protected CompareNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "compare";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        TruffleString first = (TruffleString) args[0];
        TruffleString second = (TruffleString) args[1];
        int comp = compareCharsUTF16Node.execute(first,second);
        return comp == 0 ? 0 : (comp < 0 ? -1 : 1);
    }
}
