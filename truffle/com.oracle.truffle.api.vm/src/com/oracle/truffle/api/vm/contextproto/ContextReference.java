package com.oracle.truffle.api.vm.contextproto;

public final class ContextReference<C> {

    private final ContextStoreProfile profile;
    private final int languageId;

    ContextReference(ContextStoreProfile profile, int languageId) {
        this.profile = profile;
        this.languageId = languageId; // index
    }

    @SuppressWarnings("unchecked")
    public C get() {
        return (C) profile.get().getContext(languageId);
    }

}
