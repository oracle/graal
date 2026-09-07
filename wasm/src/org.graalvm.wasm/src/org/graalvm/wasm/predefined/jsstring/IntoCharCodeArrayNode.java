package org.graalvm.wasm.predefined.jsstring;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.array.WasmInt16Array;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

public class IntoCharCodeArrayNode extends WasmBuiltinRootNode {

    @Child
    private TruffleString.ReadCharUTF16Node readCharUTF16Node = TruffleString.ReadCharUTF16Node.create();
    private InteropLibrary interop;

    protected IntoCharCodeArrayNode(WasmLanguage language, WasmModule module) {
        super(language, module);
        interop = InteropLibrary.getUncached();
    }

    @Override
    public String builtinNodeName() {
        return "intoCharCodeArray";
    }

    @Override
    public Object executeWithInstance(VirtualFrame frame, WasmInstance instance) {
        var args = WasmArguments.getArguments(frame.getArguments());
        if (!(args[0] instanceof TruffleString s)) throw WasmException.create(Failure.TYPE_MISMATCH);
        var array = args[1];
        if (interop.isNull(array)) throw WasmException.create(Failure.NULL_REFERENCE);
        long start = Integer.toUnsignedLong(((Number)args[2]).intValue());
        int strLength = s.byteLength(TruffleString.Encoding.UTF_16)/2;
        try {
            long arrLength = interop.getArraySize(array);
            if (start + strLength > arrLength) throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
            for (int i = 0; i < strLength; i++) {
                short charCode = (short) readCharUTF16Node.execute(s,i);
                interop.writeArrayElement(array, start+i, charCode);
            }
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            throw WasmException.create(Failure.TYPE_MISMATCH);
        } catch (InvalidArrayIndexException e) {
            throw WasmException.create(Failure.OUT_OF_BOUNDS_ARRAY_ACCESS);
        }
        return strLength;
    }
}
