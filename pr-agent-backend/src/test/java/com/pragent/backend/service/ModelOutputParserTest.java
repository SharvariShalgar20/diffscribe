package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelOutputParserTest {

    private static final String GOOD = """
            TYPE: feat
            TITLE: add version endpoint
            WHAT: Adds a GET /api/version endpoint.
            WHY: Not evident from the diff.
            TEST: Call the endpoint and check the version string.
            """;

    @Test
    void parsesAWellFormedAnswer() {
        ModelOutputParser.Parsed p = ModelOutputParser.parse(GOOD);

        assertEquals(ChangeType.FEAT, p.type());
        assertEquals("add version endpoint", p.summary());
        assertEquals("Adds a GET /api/version endpoint.", p.what());
        assertEquals("Not evident from the diff.", p.why());
        assertEquals("Call the endpoint and check the version string.", p.howToTest());
    }

    @Test
    void toleratesPreambleAndMarkdownLabels() {
        String raw = """
                Sure, here you go:
                **TYPE**: feat
                **TITLE**: Add version endpoint.
                **WHAT**: Adds an endpoint.
                **WHY**: Not evident from the diff.
                **TEST**: Call it.
                """;

        ModelOutputParser.Parsed p = ModelOutputParser.parse(raw);

        assertEquals(ChangeType.FEAT, p.type());
        assertEquals("feat: add version endpoint", ModelOutputParser.formatTitle(p.type(), p.summary()));
    }

    @Test
    void ignoresCodeFences() {
        ModelOutputParser.Parsed p = ModelOutputParser.parse("```\n" + GOOD + "```");

        assertEquals(ChangeType.FEAT, p.type());
    }

    @Test
    void stripsATypePrefixTheModelAddedToTheTitle() {
        String raw = GOOD.replace("TITLE: add version endpoint", "TITLE: Feat: Add version endpoint.");

        ModelOutputParser.Parsed p = ModelOutputParser.parse(raw);

        assertEquals("feat: add version endpoint", ModelOutputParser.formatTitle(p.type(), p.summary()));
    }

    @Test
    void acceptsFeatureAsFeat() {
        ModelOutputParser.Parsed p = ModelOutputParser.parse(GOOD.replace("TYPE: feat", "TYPE: Feature"));

        assertEquals(ChangeType.FEAT, p.type());
    }

    @Test
    void rejectsAnUnknownType() {
        assertThrows(ModelOutputException.class,
                () -> ModelOutputParser.parse(GOOD.replace("TYPE: feat", "TYPE: banana")));
    }

    @Test
    void missingWhyFallsBackToNotEvident() {
        String raw = GOOD.replace("WHY: Not evident from the diff.\n", "");

        assertEquals(ModelOutputParser.WHY_FALLBACK, ModelOutputParser.parse(raw).why());
    }

    @Test
    void missingWhatIsRejected() {
        String raw = GOOD.replace("WHAT: Adds a GET /api/version endpoint.\n", "");

        assertThrows(ModelOutputException.class, () -> ModelOutputParser.parse(raw));
    }

    @Test
    void multiLineFieldsKeepTheirLines() {
        String raw = GOOD.replace("WHAT: Adds a GET /api/version endpoint.",
                "WHAT: First line.\nSecond line.");

        assertEquals("First line.\nSecond line.", ModelOutputParser.parse(raw).what());
    }

    @Test
    void headingsInsideAFieldAreDropped() {
        String raw = GOOD.replace("WHAT: Adds a GET /api/version endpoint.",
                "WHAT: Adds a thing.\n## Extra heading\nMore detail.");

        assertEquals("Adds a thing.\nMore detail.", ModelOutputParser.parse(raw).what());
    }

    @Test
    void rejectsEmptyAndRamblingAnswers() {
        assertThrows(ModelOutputException.class, () -> ModelOutputParser.parse("   "));
        assertThrows(ModelOutputException.class, () -> ModelOutputParser.parse(null));
        assertThrows(ModelOutputException.class, () -> ModelOutputParser.parse("I think this is a nice change."));
    }

    @Test
    void longTitlesAreCutOnAWordBoundary() {
        String longSummary = "word ".repeat(30).strip();

        String title = ModelOutputParser.formatTitle(ChangeType.FEAT, longSummary);

        assertTrue(title.length() <= ModelOutputParser.MAX_TITLE_LENGTH, title);
        assertTrue(title.startsWith("feat: word"));
        assertFalse(title.endsWith(" "));
    }

    @Test
    void descriptionHasExactlyTheThreeHeadingsInOrder() {
        String description = ModelOutputParser.formatDescription(ModelOutputParser.parse(GOOD));

        int what = description.indexOf("## What changed");
        int why = description.indexOf("## Why");
        int test = description.indexOf("## How to test");
        assertTrue(what >= 0 && what < why && why < test, description);
        assertEquals(3, description.split("(?m)^## ", -1).length - 1);
    }
}