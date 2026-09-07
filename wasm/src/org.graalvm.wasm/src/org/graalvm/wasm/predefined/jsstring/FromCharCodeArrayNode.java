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
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class FromCharCodeArrayNode extends WasmBuiltinRootNode {

    @Child
    private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();
    private InteropLibrary interop;

    protected FromCharCodeArrayNode(WasmLanguage language, WasmModule module) {
        super(language, module);
        interop = InteropLibrary.getUncached();
    }

    @Override
    public String builtinNodeName() {
        return "fromCharCodeArray";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        var array = args[0];
        if (interop.isNull(array)) throw WasmException.create(Failure.NULL_REFERENCE);
        if (!(args[1] instanceof Integer start)) throw WasmException.create(Failure.TYPE_MISMATCH);
        if (!(args[2] instanceof Integer end)) throw WasmException.create(Failure.TYPE_MISMATCH);
        try {
            if (interop.hasArrayElements(array)) {
                StringBuilder result = new StringBuilder();
                long length = interop.getArraySize(array);
                if (Integer.compareUnsigned(start, end) > 0) throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                if (Long.compare(Integer.toUnsignedLong(end), length) > 0) throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
                for (int i = start; i < end; i++) {
                    result.append((char) interop.asInt(interop.readArrayElement(array, i)));
                }
                return fromJavaStringNode.execute(result.toString(), TruffleString.Encoding.UTF_16);
            }
            else {
                throw WasmException.create(Failure.TYPE_MISMATCH);
            }
        } catch (InvalidArrayIndexException e) {
            throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
        } catch (UnsupportedMessageException e) {
            throw WasmException.create(Failure.TYPE_MISMATCH);
        }
    }
}
