package com.oracle.graal.java;

import static com.oracle.graal.bytecode.Bytecodes.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

public class BytecodeParseHelper<T extends KindInterface> {

    private AbstractFrameStateBuilder<T> frameState;

    public BytecodeParseHelper(AbstractFrameStateBuilder<T> frameState) {
        this.frameState = frameState;
    }

    public void setCurrentFrameState(AbstractFrameStateBuilder<T> frameState) {
        this.frameState = frameState;
    }

    public void loadLocal(int index, Kind kind) {
        frameState.push(kind, frameState.loadLocal(index));
    }

    public void storeLocal(Kind kind, int index) {
        T value;
        if (kind == Kind.Object) {
            value = frameState.xpop();
            // astore and astore_<n> may be used to store a returnAddress (jsr)
            assert value.getKind() == Kind.Object || value.getKind() == Kind.Int;
        } else {
            value = frameState.pop(kind);
        }
        frameState.storeLocal(index, value);
    }

    public void stackOp(int opcode) {
        switch (opcode) {
            case POP: {
                frameState.xpop();
                break;
            }
            case POP2: {
                frameState.xpop();
                frameState.xpop();
                break;
            }
            case DUP: {
                T w = frameState.xpop();
                frameState.xpush(w);
                frameState.xpush(w);
                break;
            }
            case DUP_X1: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP_X2: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                T w3 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X1: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                T w3 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X2: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                T w3 = frameState.xpop();
                T w4 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w4);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case SWAP: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                break;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
