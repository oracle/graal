package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;

public final class GetBindingsNode extends RootNode {
    public static final String EVAL_NAME = "<Bindings>";

    public GetBindingsNode(TruffleLanguage<?> language) {
        super(language);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return EspressoLanguage.getCurrentContext().getBindings();
    }
}
