package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.value.Parity;

public final class ParityDomain extends FiniteAbstractDomain<Parity> {
    public ParityDomain() {
        super(Parity.BOT, AbstractValueKind.BOT);
    }

    public ParityDomain(Parity parity) {
        super(parity, parity == Parity.BOT ? AbstractValueKind.BOT : parity == Parity.TOP ? AbstractValueKind.TOP : AbstractValueKind.VAL);
    }

    public ParityDomain(ParityDomain ParityDomain) {
        super(ParityDomain.getState(), ParityDomain.getKind());
    }

    @Override
    public ParityDomain copyOf() {
        return new ParityDomain(this);
    }

}
