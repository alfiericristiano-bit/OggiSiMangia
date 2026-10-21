package it.oggisimangia.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RecipeReaderTest {
    @Test fun nestedSectionsPreserveQuantitiesAndOriginalSteps() {
        val result = RecipeReader.parse(JSONObject("""{
            "recipeYield": ["4 persone"], "totalTime": "PT1H10M",
            "recipeIngredient": ["200 g di riso", "2 uova"],
            "recipeInstructions": [{"@type":"HowToSection", "itemListElement":[
                {"@type":"HowToStep","text":"Scalda a 180 °C. Cuoci per 20 minuti."},
                {"@type":"HowToStep","text":"Servi caldo."}
            ]}]
        }"""))
        assertEquals("200 g di riso\n2 uova", result.ingredients)
        assertEquals("4 persone", result.portions)
        assertEquals("70 min", result.time)
        assertTrue(result.original.contains("Cuoci per 20 minuti."))
        assertTrue(result.summary.contains("180 °C"))
        assertTrue(result.summary.contains("20 minuti"))
        assertTrue(result.original.contains("2. Servi caldo."))
    }

    @Test fun unavailableDataDoesNotInventCookingInstructions() {
        val result = RecipeReader.parse(JSONObject("{}"))
        assertEquals("", result.ingredients)
        assertEquals("", result.summary)
        assertEquals("", result.original)
        assertEquals("", result.time)
        assertTrue(result.notice.contains("non disponibile"))
    }

    @Test fun plainTextInstructionsAndHtmlAreReadable() {
        val result = RecipeReader.parse(JSONObject("""{
            "recipeInstructions":"<p>Mescola 100 g di farina.</p>",
            "recipeYield":"2 persone"
        }"""))
        assertEquals("1. Mescola 100 g di farina.", result.original)
        assertEquals("2 persone", result.portions)
    }
}
