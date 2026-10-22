package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class NanoRecipeResponseTest {
    private val original = """
        Pagina 1 (intera)
        Risotto ai funghi
        320 g riso
        200 g funghi
        1. Lava i funghi.
        2. Cuoci il riso.
        Difficoltà: Facile
        Tempo preparazione: 15 min
    """.trimIndent()

    @Test fun malformedOrEmptyNeverChangesManualFields() {
        assertNull(NanoRecipeResponse.parse("Non lo so", original))
        assertNull(NanoRecipeResponse.parse("{}", original))
    }

    @Test fun noInventedIngredientsOrCookingTimes() {
        val result=NanoRecipeResponse.parse(
          """{"titolo":"Risotto ai funghi","ingredienti":["320 g riso","200 g funghi","100 g tartufo"],"preparazione":["Lava i funghi.","Cuoci il riso.","Aggiungi zafferano."],"tempo_preparazione":"15 min","tempo_cottura":"75 min","difficolta":"Facile","porzioni":"4 persone"}""",original)!!
        assertEquals("Risotto ai funghi",result.title)
        assertFalse(result.ingredients.contains("tartufo"))
        assertFalse(result.steps.contains("zafferano"))
        assertEquals("15 min",result.prepTime)
        assertEquals("",result.cookTime)
        assertEquals("",result.portions)
    }
}
