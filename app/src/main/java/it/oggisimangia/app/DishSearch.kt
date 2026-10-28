package it.oggisimangia.app

/** Catalogo per una ricerca guidata indipendente dalla ricerca per ingredienti. */
object DishSearch {
    enum class Mode { INGREDIENTS, DISH, SURPRISE }
    enum class IngredientRule { PREFERRED, REQUIRED }
    data class Category(val name: String, val subcategories: List<String>)
    data class Request(
        val mode: Mode,
        val category: String? = null,
        val subcategory: String? = null,
        val baseIngredient: String = "",
        val extraIngredients: List<String> = emptyList(),
        val ingredientRule: IngredientRule = IngredientRule.PREFERRED
    )
    data class Plan(
        val queries: List<String>,
        val requiredIngredients: String,
        val preferredIngredients: String,
        val category: String?,
        val subcategory: String?
    )
    val categories = listOf(
        Category("Primi piatti", listOf("Pasta", "Riso e risotti", "Ravioli e tortellini", "Gnocchi", "Lasagne e pasta al forno", "Zuppe e minestre", "Cous cous e cereali")),
        Category("Secondi piatti", listOf("Carne bianca", "Carne rossa", "Pesce", "Crostacei e molluschi", "Uova", "Formaggi", "Legumi", "Vegetariani e vegani")),
        Category("Antipasti", listOf("Freddi", "Caldi", "Bruschette e crostini", "Torte salate", "Di mare")),
        Category("Contorni", listOf("Verdure cotte", "Verdure crude", "Patate", "Funghi", "Insalate", "Legumi")),
        Category("Piatti unici", listOf("Pizza e focacce", "Insalatone", "Bowl", "Sformati", "Piatti etnici")),
        Category("Dolci", listOf("Torte", "Crostate", "Biscotti", "Dolci al cucchiaio", "Gelati e semifreddi")),
        Category("Pane e lievitati", listOf("Pane", "Focacce", "Grissini")),
        Category("Salse e condimenti", listOf("Sughi", "Pesti", "Salse")),
        Category("Bevande", listOf("Frullati", "Analcolici", "Calde"))
    )
    private val expansions = mapOf(
        "Pasta" to listOf("pasta", "spaghetti", "penne", "fusilli", "tagliatelle"),
        "Riso e risotti" to listOf("risotto", "riso"),
        "Ravioli e tortellini" to listOf("ravioli", "tortellini"),
        "Carne bianca" to listOf("pollo", "tacchino", "coniglio"),
        "Carne rossa" to listOf("manzo", "vitello", "maiale"),
        "Pesce" to listOf("pesce", "salmone", "merluzzo", "orata"),
        "Funghi" to listOf("funghi", "porcini", "champignon")
    )
    fun plan(request: Request): Plan {
        val ingredients = (listOf(request.baseIngredient) + request.extraIngredients)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
        if (request.mode == Mode.INGREDIENTS) {
            require(ingredients.isNotEmpty()) { "Inserisci almeno un ingrediente" }
            return Plan(RecipeSearchQueries.variants(ingredients.joinToString(", ")),
                ingredients.joinToString(", "), "", null, null)
        }
        val cat = request.category?.takeIf { c -> categories.any { it.name == c } }
        val sub = request.subcategory?.takeIf { s -> categories.firstOrNull { it.name == cat }?.subcategories?.contains(s) == true }
        val seeds = (expansions[sub] ?: listOfNotNull(sub)).ifEmpty { listOfNotNull(cat) }
        val querySeeds = if (request.mode == Mode.SURPRISE && seeds.isEmpty()) {
            listOf("ricette facili", "piatti veloci", "idee per cena")
        } else seeds
        val queries = querySeeds.flatMap { seed ->
            listOf(listOf(seed, ingredients.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" "),
                seed)
        }.distinct().take(12)
        return Plan(queries,
            if (request.ingredientRule == IngredientRule.REQUIRED) ingredients.joinToString(", ") else "",
            if (request.ingredientRule == IngredientRule.PREFERRED) ingredients.joinToString(", ") else "",
            cat, sub)
    }
}
