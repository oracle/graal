package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.HeapIsolationException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.array.WasmInt16Array;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class FromCharCodeArrayNode extends WasmBuiltinRootNode {
    protected FromCharCodeArrayNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public String builtinNodeName() {
        return "fromCharCodeArray";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        //var wasmarray = (WasmInt16Array) args[0];
        var arg = WasmArguments.getArguments(frame.getArguments())[0];
        var interop = InteropLibrary.getUncached();
        try {
            if (interop.isNull(arg)) throw new RuntimeException("Array expected, got null");
            StringBuilder result = new StringBuilder();
            if (interop.hasArrayElements(arg)) {
                long length = interop.getArraySize(arg);
                for (int i = 0; i < length; i++) {
                    result.append((char) interop.asInt(interop.readArrayElement(arg, i)));
                }
                return TruffleString.fromJavaStringUncached(result.toString(), TruffleString.Encoding.UTF_16);
            }
            else if (interop.isHostObject(arg)) {
                var host = interop.asHostObject(arg);
                if (!(host instanceof Integer[] array)) throw new RuntimeException("Array expected");
                for (int integer : array) {
                    result.append((char) integer);
                }
                return TruffleString.fromJavaStringUncached(result.toString(), TruffleString.Encoding.UTF_16);
            }
            else {
                throw new RuntimeException("Array expected");
            }
        } catch (UnsupportedMessageException | HeapIsolationException | InvalidArrayIndexException e) {
            throw new RuntimeException(e);
        }
    }
}
