package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class IngredientFilterTest {
    @Test fun allRequestedIngredientsAreRequired() {
        assertTrue(IngredientFilter.matches("2 UOVA\n100 g riso\n1 zucchina", "uova, riso"))
        assertFalse(IngredientFilter.matches("2 UOVA\n100 g farina", "zucchine, uova, riso"))
        assertFalse(IngredientFilter.matches("200 g risotto", "riso"))
    }
    @Test fun blankFilterAndAlternativeSeparatorsWork() {
        assertTrue(IngredientFilter.matches("", " , ; \n"))
        assertTrue(IngredientFilter.matches("2 uova\n200 g di riso", " uova ;\n riso "))
        assertFalse(IngredientFilter.matches("", "uova"))
    }
}
