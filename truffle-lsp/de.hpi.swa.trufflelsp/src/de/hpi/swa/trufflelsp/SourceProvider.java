package de.hpi.swa.trufflelsp;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class SourceProvider implements LoadSourceListener, LoadSourceSectionListener {
    protected Map<String, Map<String, SourceWrapper>> langId2loadedSources = new LinkedHashMap<>();

    public SourceWrapper getLoadedSource(String langId, String name) {
        Map<String, SourceWrapper> name2sources = this.langId2loadedSources.get(langId);
        return name2sources == null ? null : name2sources.get(name);
    }

    public void onLoad(LoadSourceEvent event) {
        // TODO(ds) this is only called for every new Source, i.e. if I add a character via the editor, this
        // is called, but when I remove this character, it is not called again, because the text of the
        // Source is then identical again to the previous Source
        Source source = event.getSource();
        initLang(source.getLanguage());
        Map<String, SourceWrapper> name2SourceWrapper = this.langId2loadedSources.get(source.getLanguage());
        name2SourceWrapper.put(source.getName(), new SourceWrapper(source));
    }

    public void onLoad(LoadSourceSectionEvent event) {
        String langId = event.getNode().getRootNode().getLanguageInfo().getId();
        System.out.println("\t" + event.getNode().getClass().getSimpleName() + " " + event.getSourceSection());
        SourceSection sourceSection = event.getSourceSection();
        Source source = sourceSection.getSource();
        String name = source.getName();
        Map<String, SourceWrapper> name2SourceWrapper = this.langId2loadedSources.get(langId);
        SourceWrapper sourceWrapper = name2SourceWrapper.get(name);
        if (sourceWrapper == null || !sourceWrapper.getSource().equals(source)) {
            // TODO(ds) need this because of the previous TODO
            sourceWrapper = new SourceWrapper(source);
            name2SourceWrapper.put(source.getName(), sourceWrapper);
        }
        sourceWrapper.getNodes().add(event.getNode());
    }

    private void initLang(String langId) {
        if (!this.langId2loadedSources.containsKey(langId)) {
            this.langId2loadedSources.put(langId, new LinkedHashMap<>());
        }
    }

    public void remove(String langId, String name) {
        if (this.langId2loadedSources.containsKey(langId) && this.langId2loadedSources.get(langId).containsKey(name)) {
            this.langId2loadedSources.get(langId).remove(name);
        }
    }

    public boolean containsKey(Object key) {
        return this.langId2loadedSources.containsKey(key);
    }
}
