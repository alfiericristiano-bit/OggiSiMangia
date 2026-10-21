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
    @Test fun sourceTimesPhotoAndNutritionAreKeptSeparate() {
        val result = RecipeReader.parse(JSONObject("""{
            "prepTime":"PT15M", "cookTime":"PT1H", "totalTime":"PT2H15M", "additionalTime":"PT1H",
            "recipeYield":"6 persone", "image":[{"@type":"ImageObject","url":"https://example.org/dish.jpg"}],
            "nutrition":{"calories":"320 kcal","proteinContent":"12 g"}
        }"""))
        assertEquals("15 min", result.prepTime)
        assertEquals("60 min", result.cookTime)
        assertEquals("135 min", result.totalTime)
        assertEquals("60 min", result.restTime)
        assertEquals("6 persone", result.portions)
        assertEquals("https://example.org/dish.jpg", result.imageUrl)
        assertTrue(result.nutrition.contains("320 kcal"))
    }
    @Test fun missingTotalsAndRestAreNotInferred() {
        val result = RecipeReader.parse(JSONObject("""{"prepTime":"PT10M", "cookTime":"PT20M"}"""))
        assertEquals("", result.totalTime)
        assertEquals("", result.restTime)
        assertEquals("", result.imageUrl)
    }
    @Test fun importedPageUsesPublishedTitleAndRelativePhoto() {
        val html = """<html><head><title>Pagina</title><script type="application/ld+json">
            {"@type":"Recipe", "name":"Pasta &amp; zucchine", "image":"/foto.jpg",
             "recipeIngredient":["200 g pasta"], "prepTime":"PT10M",
             "recipeInstructions":["Cuoci 8 minuti."]}
            </script></head></html>"""
        val result = RecipeReader.parseDocument(org.jsoup.Jsoup.parse(html), "https://ricette.example.org/pasta")
        assertEquals("Pasta & zucchine", result.title)
        assertEquals("https://ricette.example.org/foto.jpg", result.imageUrl)
        assertEquals("10 min", result.prepTime)
        assertTrue(result.original.contains("8 minuti"))
    }
    @Test fun unreadableRecipeKeepsPageTitleAndPhotoWithoutInventingData() {
        val result = RecipeReader.parseDocument(org.jsoup.Jsoup.parse(
            """<title>Torta</title><meta property="og:image" content="/torta.jpg">"""),
            "https://ricette.example.org/torta")
        assertEquals("Torta", result.title)
        assertEquals("https://ricette.example.org/torta.jpg", result.imageUrl)
        assertEquals("", result.ingredients)
        assertEquals("", result.totalTime)
        assertTrue(result.notice.contains("manualmente"))
    }
    @Test fun explicitLinkAcceptsOtherRecipeSitesButRejectsInvalidAddresses() {
        assertTrue(RecipeLink.isValid("https://ricette.example.org/torta?porzioni=4"))
        listOf("http://example.org/", "https://127.0.0.1/", "https://localhost/",
            "https://host.local/", "https://user:password@example.org/", "file:///tmp/a",
            "https://example.org:8080/", "testo senza link").forEach {
            assertFalse(it, RecipeLink.isValid(it))
        }
    }
}

