package de.hpi.swa.trufflelsp;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;

@Registration(id = SourceSectionProvider.ID, services = SourceSectionProvider.class)
public class SourceSectionProvider extends TruffleInstrument {
    public static final String ID = "lsp-sourcesections";

    private List<Node> loadedNodes = new ArrayList<>();

    public List<Node> getLoadedNodes() {
        return new ArrayList<>(loadedNodes);
    }

    @Override
    protected void onCreate(Env env) {
        env.registerService(this);

        env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, new LoadSourceSectionListener() {

            public void onLoad(LoadSourceSectionEvent event) {
                System.out.println(event.getNode().getClass().getSimpleName() + " " + event.getSourceSection());
                loadedNodes.add(event.getNode());
            }
        }, true);
    }
}
