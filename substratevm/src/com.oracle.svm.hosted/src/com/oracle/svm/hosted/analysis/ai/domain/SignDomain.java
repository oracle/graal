package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.value.AbstractValueKind;
import com.oracle.svm.hosted.analysis.ai.domain.value.Sign;

public final class SignDomain extends FiniteDomain<Sign> {

    public SignDomain() {
        super(Sign.BOT, AbstractValueKind.BOT);
    }

    public SignDomain(Sign sign) {
        super(sign, sign == Sign.BOT ? AbstractValueKind.BOT : sign == Sign.TOP ? AbstractValueKind.TOP : AbstractValueKind.VAL);
    }

    public SignDomain(SignDomain signDomain) {
        super(signDomain.getState(), signDomain.getKind());
    }

    @Override
    public SignDomain copyOf() {
        return new SignDomain(this);
    }

    // TODO: add arithmetical operations
}
