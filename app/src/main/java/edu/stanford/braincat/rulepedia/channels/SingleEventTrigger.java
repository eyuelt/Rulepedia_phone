package edu.stanford.braincat.rulepedia.channels;

import java.util.Arrays;
import java.util.Collection;

import edu.stanford.braincat.rulepedia.events.EventSource;
import edu.stanford.braincat.rulepedia.model.Trigger;

/**
 * Created by gcampagn on 4/30/15.
 * <p>
 * Helper code for triggers that have only one associated event source.
 */
public abstract class SingleEventTrigger<K extends EventSource> implements Trigger {
    private K source;

    protected SingleEventTrigger() {
        source = null;
    }

    protected void setSource(K source) {
        this.source = source;
    }

    protected K getSource() {
        return source;
    }

    @Override
    public Collection<EventSource> getEventSources() {
        return Arrays.asList(new EventSource[]{source});
    }
}
