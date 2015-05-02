package edu.stanford.braincat.rulepedia.channels.sms;

import edu.stanford.braincat.rulepedia.exceptions.RuleExecutionException;
import edu.stanford.braincat.rulepedia.exceptions.TriggerValueTypeException;
import edu.stanford.braincat.rulepedia.model.Action;
import edu.stanford.braincat.rulepedia.model.ObjectPool;
import edu.stanford.braincat.rulepedia.model.Trigger;
import edu.stanford.braincat.rulepedia.model.Value;

/**
 * Created by gcampagn on 5/1/15.
 */
public class SMSSendMessageAction implements Action {
    private final SMSChannel channel;
    private final Value destination;
    private final Value message;

    public SMSSendMessageAction(SMSChannel channel, Value destination, Value message) {
        this.channel = channel;
        this.destination = destination;
        this.message = message;
    }

    @Override
    public void typeCheck(Trigger trigger) throws TriggerValueTypeException {
        destination.typeCheck(trigger, Value.Contact.class);
        message.typeCheck(trigger, Value.Text.class);
    }

    @Override
    public void execute(ObjectPool pool, Trigger trigger) throws RuleExecutionException {
    }

    @Override
    public String toHumanString() {
        return "send a message to " + destination;
    }
}