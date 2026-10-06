package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.catalog.CatalogContentVariant
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackError
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion

/**
 * Word plays in the interface language, with one shared level progression: level N of a difficulty is
 * the same level in every language, and its content comes from that language's frozen bucket —
 * Russian from `word_ru/` (Generator V5, the family-filtered answers), English from `word_en/` (V3),
 * Turkish from `word_tr/` (V4). The default `word/` buckets (V2) stay frozen for older app versions
 * and old results. A bucket whose generator version is not its language's fails cleanly.
 */
object WordCatalogContent {
    val RUSSIAN_VARIANT = CatalogContentVariant("ru")
    val ENGLISH_VARIANT = CatalogContentVariant("en")
    val TURKISH_VARIANT = CatalogContentVariant("tr")

    fun variant(language: WordLanguage): CatalogContentVariant =
        when (language) {
            WordLanguage.RUSSIAN -> RUSSIAN_VARIANT
            WordLanguage.ENGLISH -> ENGLISH_VARIANT
            WordLanguage.TURKISH -> TURKISH_VARIANT
        }

    fun generatorVersion(language: WordLanguage): GeneratorVersion =
        when (language) {
            WordLanguage.RUSSIAN -> GeneratorVersion(5)
            WordLanguage.ENGLISH -> GeneratorVersion(3)
            WordLanguage.TURKISH -> GeneratorVersion(4)
        }

    fun resolve(
        pack: CatalogLevelPack,
        levelId: CatalogLevelId,
        language: WordLanguage,
    ): CatalogLevelPackResult<CatalogLevelDefinition> =
        when (val resolved = pack.resolve(levelId, variant(language))) {
            is CatalogLevelPackResult.Failure -> resolved
            is CatalogLevelPackResult.Success ->
                if (resolved.value.generatorVersion == generatorVersion(language)) {
                    resolved
                } else {
                    CatalogLevelPackResult.Failure(
                        CatalogLevelPackError.CORRUPT_ASSET,
                        "Word ${language.name} bucket holds generator ${resolved.value.generatorVersion.value}.",
                    )
                }
        }
}
