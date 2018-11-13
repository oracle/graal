package org.graalvm.tools.lsp.instrument;

import org.graalvm.tools.lsp.server.TruffleAdapter;

public interface TruffleAdapterProvider {

    public TruffleAdapter getTruffleAdapter();
}
