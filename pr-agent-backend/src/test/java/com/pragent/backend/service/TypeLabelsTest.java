package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeLabelsTest {

    @Test
    void everyTypeHasADistinctPrefixedLabel() {
        long distinct = java.util.Arrays.stream(ChangeType.values())
                .map(t -> TypeLabels.of(t).name()).distinct().count();

        assertEquals(ChangeType.values().length, distinct);
        for (ChangeType type : ChangeType.values()) {
            assertTrue(TypeLabels.of(type).name().startsWith("type: "));
            assertEquals(6, TypeLabels.of(type).color().length());
        }
    }

    @Test
    void onlyOurOwnLabelsAreManaged() {
        assertTrue(TypeLabels.isManaged("type: feat"));
        assertFalse(TypeLabels.isManaged("bug"));
        assertFalse(TypeLabels.isManaged("type: question"));
    }

    @Test
    void upToDateMeansExactlyOneManagedLabelAndItIsTheRightOne() {
        assertTrue(TypeLabels.isUpToDate(List.of("priority", "type: feat"), ChangeType.FEAT));
        assertFalse(TypeLabels.isUpToDate(List.of("type: fix"), ChangeType.FEAT));
        assertFalse(TypeLabels.isUpToDate(List.of("type: feat", "type: fix"), ChangeType.FEAT));
        assertFalse(TypeLabels.isUpToDate(List.of(), ChangeType.FEAT));
    }

    @Test
    void mergeSwapsTheTypeLabelAndKeepsEverythingElse() {
        List<String> result = TypeLabels.withTypeLabel(List.of("priority", "type: fix", "needs-review"), ChangeType.FEAT);

        assertEquals(List.of("priority", "needs-review", "type: feat"), result);
    }

    @Test
    void mergeOnAnUnlabeledPrJustAddsTheLabel() {
        assertEquals(List.of("type: docs"), TypeLabels.withTypeLabel(List.of(), ChangeType.DOCS));
    }
}