package de.hpi.swa.trufflelsp;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@Registration(id = SourceSectionProvider.ID, services = SourceSectionProvider.class)
public class SourceSectionProvider extends TruffleInstrument {
    public static final String ID = "lsp-sourcesections";

    protected Map<String, Map<String, SourceWrapper>> loadedSources = new LinkedHashMap<>();

    public SourceWrapper getLoadedSource(String langId, String name) {
        return this.loadedSources.get(langId).get(name);
    }

    @Override
    protected void onCreate(Env env) {
        env.registerService(this);

        env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.ANY, new LoadSourceSectionListener() {

            public void onLoad(LoadSourceSectionEvent event) {
                String langId = event.getNode().getRootNode().getLanguageInfo().getId();
                System.out.println(event.getNode().getClass().getSimpleName() + " " + event.getSourceSection());
                SourceSection sourceSection = event.getSourceSection();
                Source source = sourceSection.getSource();
                String name = source.getName();
                Map<String, SourceWrapper> name2SourceWrapper = SourceSectionProvider.this.loadedSources.get(langId);
                if (!name2SourceWrapper.containsKey(name)) {
                    name2SourceWrapper.put(source.getName(), new SourceWrapper(source));
                }
                name2SourceWrapper.get(name).getNodes().add(event.getNode());
            }
        }, true);
    }

    public void remove(String langId, String name) {
        this.loadedSources.get(langId).remove(name);
    }

    public boolean containsKey(Object key) {
        return this.loadedSources.containsKey(key);
    }

    public void initLang(String langId) {
        if (!this.loadedSources.containsKey(langId)) {
            this.loadedSources.put(langId, new LinkedHashMap<>());
        }
    }
}
