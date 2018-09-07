package de.hpi.swa.trufflelsp.instrument;

import de.hpi.swa.trufflelsp.server.TruffleAdapter;

public interface TruffleAdapterProvider {

    public TruffleAdapter getTruffleAdapter();
}
