package org.jenkinsci.plugins.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class EnvStepUnitTest {

    @Test
    void constructorAcceptsNull() {
        EnvStep s = new EnvStep(null);
        assertTrue(s.getOverrides().isEmpty());
    }

    @Test
    void constructorRejectsMalformedEntry() {
        assertThrows(IllegalArgumentException.class, () -> new EnvStep(Arrays.asList("BADPAIR")));
    }

    @Test
    void constructorTrimsStoredPair() {
        EnvStep s = new EnvStep(Arrays.asList("FOO = bar "));
        assertEquals(1, s.getOverrides().size());
        assertEquals("FOO = bar", s.getOverrides().get(0));
    }
}
