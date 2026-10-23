package com.pragent.backend.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChangeTypeTest {

    @Test
    void parsesCaseInsensitivelyAndTrims() {
        assertEquals(ChangeType.FEAT, ChangeType.fromValue("FEAT"));
        assertEquals(ChangeType.FEAT, ChangeType.fromValue("feat"));
        assertEquals(ChangeType.REFACTOR, ChangeType.fromValue("  Refactor "));
    }

    @Test
    void serializesToLowercase() {
        assertEquals("feat", ChangeType.FEAT.toValue());
        assertEquals("chore", ChangeType.CHORE.toValue());
    }

    @Test
    void everyValueSurvivesARoundTrip() {
        for (ChangeType type : ChangeType.values()) {
            assertEquals(type, ChangeType.fromValue(type.toValue()));
        }
    }

    @Test
    void unknownValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ChangeType.fromValue("banana"));
    }
}