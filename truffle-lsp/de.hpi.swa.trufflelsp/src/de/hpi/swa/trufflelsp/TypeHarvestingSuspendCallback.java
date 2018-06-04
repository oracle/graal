package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;

public class TypeHarvestingSuspendCallback implements SuspendedCallback {

    private DebugValue evalResult = null;

    public DebugValue getEvalResult() {
        return evalResult;
    }

    public void onSuspend(SuspendedEvent event) {
// this.evalResult = event.getReturnValue();
// if (this.evalResult.isReadable() && this.evalResult.get() != null) {
//
// } else {
        this.evalResult = event.getTopStackFrame().eval(event.getSourceSection().getCharacters().toString());
// }
    }

}
