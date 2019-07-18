package com.oracle.truffle.st;

import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import java.util.HashSet;

/**
 * A listener for new {@link SourceSection}s being loaded.
 *
 * Because we {@link #enable(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
 * attached} an instance of this listener, each time a new {@link SourceSection} of interest is
 * loaded, we are notified in the
 * {@link #onLoad(com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent) } method.
 */
class GatherSourceSectionsListener implements LoadSourceSectionListener {

    private SimpleCoverageInstrument simpleCoverageInstrument;

    public GatherSourceSectionsListener(SimpleCoverageInstrument simpleCoverageInstrument) {
        this.simpleCoverageInstrument = simpleCoverageInstrument;
    }

    /**
     * Notification that a new {@link LoadSourceSectionEvent} has occurred.
     *
     * @param event information about the event. We use this information to keep our
     *            {@link #sourceToNotYetCoveredSections set of not-yet-covered}
     *            {@link SourceSection}s up to date.
     */
    @Override
    public void onLoad(LoadSourceSectionEvent event) {
        final SourceSection sourceSection = event.getSourceSection();
        final Source source = sourceSection.getSource();
        // TODO: This should not be necesery becuase of the filter. Bug!
        if (!source.isInternal()) {
            synchronized (simpleCoverageInstrument.sourceToNotYetCoveredSections) {
                simpleCoverageInstrument.sourceToNotYetCoveredSections.computeIfAbsent(source, (Source s) -> {
                    return new HashSet<>();
                }).add(sourceSection);
            }
        }
    }
}
