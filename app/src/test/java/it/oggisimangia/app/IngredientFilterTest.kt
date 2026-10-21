package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class IngredientFilterTest {
    @Test fun anyRequestedIngredientIsEnough() {
        assertTrue(IngredientFilter.matches("2 UOVA\n100 g di farina", "zucchine, uova, riso"))
        assertFalse(IngredientFilter.matches("100 g di farina", "zucchine, uova, riso"))
    }
    @Test fun blankFilterAndAlternativeSeparatorsWork() {
        assertTrue(IngredientFilter.matches("", " , ; \n"))
        assertTrue(IngredientFilter.matches("200 g di riso", " uova ;\n riso "))
        assertFalse(IngredientFilter.matches("", "uova"))
    }
}
