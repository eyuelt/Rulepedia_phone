package edu.stanford.braincat.rulepedia.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.braincat.rulepedia.events.EventSource;
import edu.stanford.braincat.rulepedia.exceptions.RuleExecutionException;
import edu.stanford.braincat.rulepedia.exceptions.UnknownObjectException;
import edu.stanford.braincat.rulepedia.model.Rule;
import edu.stanford.braincat.rulepedia.model.RuleDatabase;

/**
 * Created by gcampagn on 5/2/15.
 */
public class RuleExecutor extends Handler {
    private final Context context;
    private final Set<EventSource> eventSources;

    public RuleExecutor(Context ctx, Looper looper) {
        super(looper);
        context = ctx;
        eventSources = new HashSet<>();
    }

    public void installRule(final JSONObject jsonRule, final edu.stanford.braincat.rulepedia.service.Callback<Rule> callback) {
        post(new Runnable() {
            public void run() {
                doInstallRule(jsonRule, callback);
            }
        });
    }

    public void reloadRule(final String id, final edu.stanford.braincat.rulepedia.service.Callback<Rule> callback) {
        post(new Runnable() {
            @Override
            public void run() {
                doReloadRule(id, callback);
            }
        });
    }

    public void deleteRule(final String id, final edu.stanford.braincat.rulepedia.service.Callback<Boolean> callback) {
        post(new Runnable() {
            @Override
            public void run() {
                doDeleteRule(id, callback);
            }
        });
    }

    private void doEnableRule(Rule rule) throws UnknownObjectException {
        rule.resolve();

        boolean anyFailed = false;
        boolean anySuccess = false;
        Collection<EventSource> sources = rule.getEventSources();
        for (EventSource s : sources) {
            try {
                s.install(context, this);
                anySuccess = true;
            } catch (IOException e) {
                Log.e(RuleExecutorService.LOG_TAG, "Failed to install event source " + s.toString() + ": " + e.getMessage());
                anyFailed = true;
            }
        }
        if (!anyFailed)
            eventSources.addAll(sources);
        if (anySuccess)
            rule.setInstalled(true);
    }


    private void doDisableRule(Rule rule) throws UnknownObjectException {
        rule.resolve();

        boolean anyFailed = false;
        boolean anySuccess = false;
        Collection<EventSource> sources = rule.getEventSources();
        for (EventSource s : sources) {
            try {
                s.uninstall(context);
                anySuccess = true;
            } catch (IOException e) {
                Log.e(RuleExecutorService.LOG_TAG, "Failed to uninstall event source " + s.toString() + ": " + e.getMessage());
                anyFailed = true;
            }
        }
        if (!anyFailed)
            eventSources.removeAll(sources);
        if (anySuccess)
            rule.setInstalled(false);
    }

    private void doInstallRule(JSONObject jsonRule, edu.stanford.braincat.rulepedia.service.Callback<Rule> callback) {
        try {
            RuleDatabase db = RuleDatabase.get();
            Rule rule = db.addRule(jsonRule);
            // save eagerly to catch problems
            db.save(context);
            assert !rule.isInstalled();
            if (rule.isEnabled())
                doEnableRule(rule);
            callback.post(rule, null);
        } catch (Exception e) {
            Log.e(RuleExecutorService.LOG_TAG, "Failed to add rule to the database: " + e.getMessage());
            callback.post(null, e);
        }
    }

    private void doReloadRule(String id, edu.stanford.braincat.rulepedia.service.Callback<Rule> callback) {
        Rule rule = RuleDatabase.get().getRuleById(id);

        if (rule == null) {
            // perfectly legitimate, possible race condition
            Log.i(RuleExecutorService.LOG_TAG, "No rule with id " + id);
            callback.post(rule, null);
            return;
        }

        if (rule.isEnabled() == rule.isInstalled()) {
            callback.post(rule, null);
            return;
        }

        try {
            if (rule.isEnabled())
                doEnableRule(rule);
            else
                doDisableRule(rule);
            callback.post(rule, null);
        } catch(Exception e) {
            Log.e(RuleExecutorService.LOG_TAG, "Failed to reload rule: " + e.getMessage());
            callback.post(null, e);
        }
    }

    private void doDeleteRule(String id, edu.stanford.braincat.rulepedia.service.Callback<Boolean> callback) {
        RuleDatabase db = RuleDatabase.get();
        Rule rule = db.getRuleById(id);

        if (rule == null) {
            // perfectly legitimate, possible race condition
            Log.i(RuleExecutorService.LOG_TAG, "No rule with id " + id);
            callback.post(true, null);
            return;
        }

        db.removeRule(rule);

        if (!rule.isInstalled()) {
            callback.post(true, null);
            return;
        }

        try {
            doDisableRule(rule);
            callback.post(true, null);
        } catch (Exception e) {
            Log.e(RuleExecutorService.LOG_TAG, "Failed to reload rule: " + e.getMessage());
            callback.post(null, e);
        }
    }

    public void prepare() {
        for (Rule r : RuleDatabase.get().getAllRules()) {
            if (!r.isEnabled())
                continue;

            try {
                doEnableRule(r);
            } catch (UnknownObjectException e) {
                Log.i(RuleExecutorService.LOG_TAG, "Failed to bootstrap rule: " + e.getMessage());
            }
        }
    }

    public void destroy() {
        for (EventSource s : eventSources) {
            try {
                s.uninstall(context);
            } catch(IOException e) {
                Log.e(RuleExecutorService.LOG_TAG, "Failed to uninstall event source " + s.toString() + ": " + e.getMessage());
            }
        }
        eventSources.clear();

        for (Rule r : RuleDatabase.get().getAllRules())
            r.setInstalled(false);
    }

    @Override
    public void dispatchMessage(@NonNull Message msg) {
        // dispatch message to event sources
        super.dispatchMessage(msg);

        // recompute triggers based on the new state of the event sources
        updateTriggers();

        // dispatch any rule that now triggers true
        dispatchRules();

        // clear events and post any newly triggered message, if necessary
        updateEventSourceState();
    }

    private void updateTriggers() {
        for (Rule r : RuleDatabase.get().getAllRules()) {
            try {
                r.updateTrigger();
            } catch (RuleExecutionException e) {
                // FIXME: notify the user!
                Log.e(RuleExecutorService.LOG_TAG, "Failed to update the trigger for rule " + r.toHumanString() + ": " + e.getMessage());
            }
        }
    }

    private void dispatchRules() {
        for (Rule r : RuleDatabase.get().getAllRules()) {
            try {
                if (r.isFiring())
                    r.fire(context);
            } catch (RuleExecutionException e) {
                Log.e(RuleExecutorService.LOG_TAG, "Failed to run rule " + r.toHumanString() + ": " + e.getMessage());
            }
        }
    }

    private void updateEventSourceState() {
        for (EventSource s : eventSources)
            s.updateState();
    }
}
