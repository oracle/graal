package org.graalvm.tools.lsp.test.instrument;

import org.graalvm.tools.lsp.server.TruffleAdapter;

public interface TruffleAdapterProvider {

    TruffleAdapter getTruffleAdapter();
}
