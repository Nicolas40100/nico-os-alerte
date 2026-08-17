package fr.nico.scouterdirect

import java.text.Normalizer
import java.util.Locale

data class SearchSpec(
    val token: Long,
    val display: String,
    val candidates: Set<String>,
)

object SearchLogic {
    private val aliases: Map<String, Set<String>> = mapOf(
        "chaussure" to setOf("shoe", "sneaker", "trainer", "footwear"),
        "chaussures" to setOf("shoe", "sneaker", "trainer", "footwear"),
        "basket" to setOf("shoe", "sneaker", "trainer"),
        "baskets" to setOf("shoe", "sneaker", "trainer"),
        "botte" to setOf("boot"), "bottes" to setOf("boot"),
        "pantoufle" to setOf("slipper"), "pantoufles" to setOf("slipper"),
        "imprimante" to setOf("printer"),
        "cafetiere" to setOf("coffee maker", "coffee machine", "coffeemaker"),
        "machine a cafe" to setOf("coffee maker", "coffee machine", "coffeemaker"),
        "telecommande" to setOf("remote control", "remote controller", "remote"),
        "bouteille" to setOf("bottle"), "tasse" to setOf("cup", "mug"), "mug" to setOf("mug", "cup"),
        "verre" to setOf("glass", "drinking glass"), "bol" to setOf("bowl"), "assiette" to setOf("plate"),
        "fourchette" to setOf("fork"), "cuillere" to setOf("spoon"), "couteau" to setOf("knife"),
        "casserole" to setOf("pot", "saucepan"), "poele" to setOf("frying pan", "pan"),
        "theiere" to setOf("teapot"), "bouilloire" to setOf("kettle"), "grille pain" to setOf("toaster"),
        "mixeur" to setOf("blender", "mixer"), "micro ondes" to setOf("microwave"), "four" to setOf("oven"),
        "plante" to setOf("plant"), "lampe" to setOf("lamp"), "ampoule" to setOf("light bulb", "bulb"),
        "cle" to setOf("key"), "cles" to setOf("key"),
        "portefeuille" to setOf("wallet"), "porte monnaie" to setOf("coin purse", "wallet"),
        "sac" to setOf("bag", "handbag"), "sac a main" to setOf("handbag", "purse", "bag"),
        "telephone" to setOf("cell phone", "mobile phone", "smartphone", "phone"),
        "portable" to setOf("cell phone", "mobile phone", "smartphone", "phone"),
        "tablette" to setOf("tablet", "tablet computer"),
        "livre" to setOf("book"), "magazine" to setOf("magazine"), "bd" to setOf("comic book", "comic"),
        "disque" to setOf("record", "vinyl record", "disc"), "vinyle" to setOf("vinyl record", "record"),
        "cd" to setOf("compact disc", "cd"), "dvd" to setOf("dvd"), "cassette" to setOf("cassette tape", "tape"),
        "chaise" to setOf("chair"), "table" to setOf("table"), "fauteuil" to setOf("armchair", "chair"),
        "canape" to setOf("sofa", "couch"), "lit" to setOf("bed"), "armoire" to setOf("wardrobe", "cabinet"),
        "commode" to setOf("dresser", "chest of drawers"), "miroir" to setOf("mirror"),
        "horloge" to setOf("clock"), "reveil" to setOf("alarm clock", "clock"),
        "cadre" to setOf("picture frame", "frame"), "tableau" to setOf("painting", "picture"),
        "vase" to setOf("vase"), "tapis" to setOf("rug", "carpet"), "rideau" to setOf("curtain"),
        "coussin" to setOf("cushion", "pillow"), "couverture" to setOf("blanket"),
        "ordinateur" to setOf("computer", "laptop"), "ordinateur portable" to setOf("laptop", "notebook computer"),
        "ecran" to setOf("monitor", "screen", "display"), "clavier" to setOf("keyboard"), "souris" to setOf("mouse"),
        "casque" to setOf("headphones", "headset", "helmet"), "ecouteurs" to setOf("earphones", "earbuds", "headphones"),
        "enceinte" to setOf("speaker", "loudspeaker"), "radio" to setOf("radio"),
        "tele" to setOf("television", "tv"), "television" to setOf("television", "tv"),
        "appareil photo" to setOf("camera", "digital camera"), "camera" to setOf("camera"),
        "console" to setOf("game console", "console"), "manette" to setOf("game controller", "controller", "gamepad"),
        "chargeur" to setOf("charger", "power adapter"), "cable" to setOf("cable", "cord"),
        "lunettes" to setOf("glasses", "eyeglasses", "sunglasses"), "montre" to setOf("watch"),
        "bague" to setOf("ring"), "bracelet" to setOf("bracelet"), "collier" to setOf("necklace"),
        "chapeau" to setOf("hat"), "casquette" to setOf("cap", "baseball cap"),
        "chemise" to setOf("shirt"), "t shirt" to setOf("t-shirt", "shirt"), "pull" to setOf("sweater", "jumper"),
        "pantalon" to setOf("pants", "trousers"), "robe" to setOf("dress"), "veste" to setOf("jacket"), "manteau" to setOf("coat"),
        "ceinture" to setOf("belt"),
        "aspirateur" to setOf("vacuum cleaner", "vacuum"), "ventilateur" to setOf("fan"),
        "poubelle" to setOf("trash can", "garbage can", "bin"),
        "velo" to setOf("bicycle", "bike"), "moto" to setOf("motorcycle", "motorbike"), "voiture" to setOf("car", "automobile"),
        "ballon" to setOf("ball"), "raquette" to setOf("racket", "racquet"), "skate" to setOf("skateboard"),
        "peluche" to setOf("stuffed animal", "plush toy", "teddy bear"), "poupee" to setOf("doll"),
        "figurine" to setOf("figurine", "action figure", "toy figure"), "jouet" to setOf("toy"),
        "marteau" to setOf("hammer"), "tournevis" to setOf("screwdriver"), "pince" to setOf("pliers"),
        "perceuse" to setOf("drill"), "scie" to setOf("saw"),
        "boite" to setOf("box", "container"), "carton" to setOf("box", "cardboard box"),
        "serviette" to setOf("towel"), "statue" to setOf("statue"), "sculpture" to setOf("sculpture"),
    )

    fun buildSpec(token: Long, raw: String): SearchSpec? {
        val display = raw.trim()
        if (display.isEmpty()) return null
        val normalized = normalize(display)
        val candidates = linkedSetOf<String>()

        aliases[normalized]?.let { candidates += it }
        if (candidates.isEmpty()) {
            for (word in normalized.split(' ')) {
                aliases[word]?.let {
                    candidates += it
                    break
                }
            }
        }

        // English input keeps working, and an unknown French word is never silently discarded.
        candidates += canonical(normalized)
        return SearchSpec(token = token, display = display, candidates = candidates.map(::canonical).toSet())
    }

    fun matches(label: String, spec: SearchSpec): Boolean {
        val labelCanonical = canonical(label)
        val labelTokens = labelCanonical.split(' ').filter { it.isNotBlank() }.map(::singular).toSet()

        for (candidateRaw in spec.candidates) {
            val candidate = canonical(candidateRaw)
            if (labelCanonical == candidate) return true

            val candidateTokens = candidate.split(' ').filter { it.isNotBlank() }.map(::singular).toSet()
            if (candidateTokens.isNotEmpty() && candidateTokens.all { it in labelTokens }) return true

            if (candidateTokens.size == 1 && labelTokens.any { it == candidateTokens.first() }) return true
        }
        return false
    }

    fun normalize(value: String): String {
        val noAccents = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .replace('-', ' ')
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun canonical(value: String): String {
        val n = normalize(value)
        return when (n) {
            "shoes", "sneaker", "sneakers", "trainer", "trainers", "footwear" -> "shoe"
            "phone", "smartphone", "cellphone", "mobile phone" -> "cell phone"
            "remote", "remote controller", "remote commander" -> "remote control"
            "tv", "television set" -> "television"
            "couch" -> "sofa"
            "bike" -> "bicycle"
            "motorbike" -> "motorcycle"
            else -> n
        }
    }

    private fun singular(token: String): String {
        return when {
            token == "glasses" -> "glass"
            token == "shoes" -> "shoe"
            token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
            token.endsWith("ses") && token.length > 4 -> token.dropLast(2)
            token.endsWith("s") && token.length > 4 && !token.endsWith("ss") -> token.dropLast(1)
            else -> token
        }
    }
}
