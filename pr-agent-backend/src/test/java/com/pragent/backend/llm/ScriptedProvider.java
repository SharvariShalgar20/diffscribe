package com.pragent.backend.llm;

import java.util.ArrayDeque;
import java.util.Deque;

/** Test double: returns or throws the scripted outcomes in order and counts calls. */
class ScriptedProvider implements LlmProvider {

    private final Deque<Object> outcomes = new ArrayDeque<>();
    int generateCalls;
    int checkCalls;
    RuntimeException checkFailure;

    ScriptedProvider thenReturn(String answer) {
        outcomes.add(answer);
        return this;
    }

    ScriptedProvider thenThrow(RuntimeException e) {
        outcomes.add(e);
        return this;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        generateCalls++;
        Object next = outcomes.poll();
        if (next == null) {
            throw new IllegalStateException("ScriptedProvider has no more outcomes");
        }
        if (next instanceof RuntimeException e) {
            throw e;
        }
        return (String) next;
    }

    @Override
    public void checkAvailableOrThrow() {
        checkCalls++;
        if (checkFailure != null) {
            throw checkFailure;
        }
    }
}