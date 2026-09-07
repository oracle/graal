package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringFactory;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class SubstringNode extends WasmBuiltinRootNode {

    @Child
    private TruffleString.SubstringByteIndexNode substringByteIndexNode = TruffleString.SubstringByteIndexNode.create();

    protected SubstringNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "substring";
    }


    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        TruffleString s = (TruffleString)args[0];
        int strlenbytes = s.byteLength(TruffleString.Encoding.UTF_16);
        int start = Math.max(((Number)args[1]).intValue()*2,0); // indices times 2 to convert from codepoint length to byte length
        int end = Math.min(Math.max(((Number)args[2]).intValue()*2,0),strlenbytes);
        if (start > end || start > strlenbytes) return "";
        return substringByteIndexNode.execute(s, start, end-start, TruffleString.Encoding.UTF_16, false);
    }
}
