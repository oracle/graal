package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class CompareNode extends WasmBuiltinRootNode {
    protected CompareNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "compare";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        // TODO: confirm java compareTo method is the same as js `<` operator
        var args = WasmArguments.getArguments(frame.getArguments());
        TruffleString first = (TruffleString) args[0];
        TruffleString second = (TruffleString) args[1];
        int comp = TruffleString.CompareCharsUTF16Node.create().execute(first,second);
        // int comp = first.compareTo(second);
        return comp == 0 ? 0 : (comp < 0 ? -1 : 1);
    }
}
