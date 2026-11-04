package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The label this tool manages for each change type, and how to merge it into a PR's labels. */
final class TypeLabels {

    record Label(String name, String color, String description) {}

    private TypeLabels() {}

    static Label of(ChangeType type) {
        return switch (type) {
            case FEAT -> new Label("type: feat", "a2eeef", "New functionality");
            case FIX -> new Label("type: fix", "d73a4a", "Bug fix");
            case REFACTOR -> new Label("type: refactor", "fbca04", "Code restructuring, same behavior");
            case DOCS -> new Label("type: docs", "0075ca", "Documentation only");
            case TEST -> new Label("type: test", "bfd4f2", "Tests only");
            case CHORE -> new Label("type: chore", "ededed", "Build, tooling or dependency changes");
        };
    }

    private static Set<String> managedNames() {
        return Arrays.stream(ChangeType.values()).map(t -> of(t).name()).collect(Collectors.toSet());
    }

    static boolean isManaged(String labelName) {
        return managedNames().contains(labelName);
    }

    /** True when the PR already has exactly the right type label and no other managed one. */
    static boolean isUpToDate(List<String> current, ChangeType type) {
        long managedPresent = current.stream().filter(TypeLabels::isManaged).count();
        return managedPresent == 1 && current.contains(of(type).name());
    }

    /** The PR's labels with any old type label removed and the new one added. Other labels are kept. */
    static List<String> withTypeLabel(List<String> current, ChangeType type) {
        List<String> result = new ArrayList<>();
        for (String label : current) {
            if (!isManaged(label)) {
                result.add(label);
            }
        }
        result.add(of(type).name());
        return result;
    }
}