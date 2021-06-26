package com.malpa.przypadek

import com.google.cloud.translate.Translate
import com.google.cloud.translate.TranslateOptions
import kotlinx.serialization.Serializable
import org.http4k.core.*
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.core.Response
import org.http4k.server.Undertow
import org.http4k.server.asServer
import java.util.*
import kotlin.random.Random

@Serializable
data class CaseResponse(
    val english: String,
    val polish: String,
)

object Words {

    private val englishWords: Set<String> =
        this.javaClass.getResource("/english-words.txt").readText().lines().map { it.trim() }.toSet()

    private val ankiNouns: Array<String> =
        this.javaClass.getResource("/nouns.txt").readText()
            .lines()
            .flatMap { it.split("\t") }
            .filter { it.all { c -> c.isLetter() } }
            .map { it.lowercase() }
            .toSet()
            .toTypedArray()

    private val validNouns: Array<String> = ankiNouns.filter { englishWords.contains(it) }.toTypedArray()

    private val adjectives: List<String> = listOf(
        "blue",
        "green",
        "yellow",
        "ugly",
        "dirty",
        "perfect",
        "modern",
        "old",
        "new",
        "big",
        "small",
        "wooden",
        "round",
        "expensive",
        "cheap",
        "nice",
        "brown",
        "colourful",
        "boring",
        "grey",
        "positive",
        "shiny",
        "wet",
        "dry",
        "straight",
        "bad",
        "wise",
        "tall",
        "short",
        "huge",
        "golden",
        "pink",
        "important",
        "loud",
        "intelligent",
        "high",
        "white",
        "black",
        "violet",
        "fat",
        "thin",
        "soft",
        "hard",
        "metal",
        "happy",
        "cheerful",
        "sad",
        "hungry",
        "thirsty",
        "sick",
        "loving",
        "english",
        "polish",
    )

    fun randomPairing(): String =
        "${adjectives[Random.nextInt(adjectives.size)]} ${validNouns[Random.nextInt(validNouns.size)]}"
}

enum class Case(val template: (Plurality, String) -> String) {
    NARZEDNIK({ pl, w -> "I am ${article(pl, w)}${pl.apply(w)}" }),
    BIERNIK({ pl, w -> "I have ${article(pl, w)}${pl.apply(w)}" }),
    DOPELNIACZ({ pl, w -> "I do not have ${article(pl, w)}${pl.apply(w)}" });

    fun apply(plurality: Plurality, pair: String): String = template(plurality, pair)

    companion object {
        fun article(plurality: Plurality, word: String) = when (plurality) {
            Plurality.PLURAL -> ""
            else -> if ("aeiou".any { word[0] == it }) "an " else "a "
        }

        fun random(): Case = values().let { it[Random.nextInt(it.size)] }
    }
}

enum class Plurality {
    PLURAL, SINGULAR;

    fun apply(pair: String) = when (this) {
        PLURAL -> "${pair}s"
        SINGULAR -> pair
    }

    companion object {
        fun random(): Plurality = values().let { it[Random.nextInt(it.size)] }
    }
}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val translate: Translate = TranslateOptions.getDefaultInstance().service
        val lensResponse = Body.auto<CaseResponse>().toLens()

        val app: (Request) -> Response = { _: Request ->
            val pair = Words.randomPairing()
            val case = Case.random()
            val plurality = Plurality.random()

            val english = case.apply(plurality, pair)

            val translation = translate.translate(
                english,
                Translate.TranslateOption.sourceLanguage("en"),
                Translate.TranslateOption.targetLanguage("pl")
            )

            val result =
                CaseResponse(english = english, polish = translation.translatedText.replaceFirstChar { it.uppercase() })

            lensResponse.inject(result, Response(Status.OK).header("Access-Control-Allow-Origin", "*"))
        }

        app.asServer(Undertow(9001)).start().block()
    }
}