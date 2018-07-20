package de.hpi.swa.trufflelsp.instrument;

import de.hpi.swa.trufflelsp.TruffleAdapter;

public interface TruffleAdapterProvider {

    public TruffleAdapter geTruffleAdapter();
}
