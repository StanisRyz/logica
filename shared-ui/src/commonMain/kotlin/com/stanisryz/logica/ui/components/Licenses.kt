package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.licenses_hide_text
import com.stanisryz.logica.shared.ui.generated.resources.licenses_intro
import com.stanisryz.logica.shared.ui.generated.resources.licenses_show_text
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * One third-party work the game ships or is built from: its name, its authors and copyright, its
 * licence, where it comes from, and the licence text the licence asks to keep, if any. Names and
 * licence texts stay in their original language.
 */
data class LicenseNotice(
    val name: String,
    val holder: String,
    val license: String,
    val url: String,
    /** A `files/licenses/` resource with the full text, for licences that ask to keep it. */
    val textFile: String? = null,
    /** Only in the Android build (its advertising and payment SDKs). */
    val androidOnly: Boolean = false,
)

private const val APACHE_2_0 = "files/licenses/apache-2.0.txt"

/**
 * Every notice the game shows, gathered from `datasets/` and `lexicon/` provenance, `tools/fonts/`,
 * and the build's dependencies. A source the project cannot verify is left out rather than guessed.
 */
val LICENSE_NOTICES: List<LicenseNotice> =
    listOf(
        LicenseNotice(
            name = "Rubik",
            holder = "Copyright 2015 The Rubik Project Authors",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/googlefonts/rubik",
            textFile = "files/licenses/ofl-1.1-rubik.txt",
        ),
        LicenseNotice(
            name = "Phosphor Icons",
            holder = "Copyright (c) 2020-2021 Phosphor Icons",
            license = "MIT License (Nonogram pictures)",
            url = "https://phosphoricons.com",
            textFile = "files/licenses/mit-phosphor.txt",
        ),
        LicenseNotice(
            name = "Material Symbols",
            holder = "Copyright Google LLC",
            license = "Apache License 2.0 (Nonogram pictures)",
            url = "https://github.com/google/material-design-icons",
            textFile = APACHE_2_0,
        ),
        LicenseNotice(
            name = "Open English WordNet 2023",
            holder = "Open English WordNet contributors",
            license = "CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/ (English word answers)",
            url = "https://en-word.net",
        ),
        LicenseNotice(
            name = "ENABLE word list",
            holder = "ENABLE2K",
            license = "Public domain (English allowed words)",
            url = "https://github.com/dolph/dictionary",
        ),
        LicenseNotice(
            name = "wordfreq",
            holder =
                "Copyright 2022 Robyn Speer; data from Google Books Ngrams, Leeds Internet Corpus, Wikipedia, " +
                    "ParaCrawl, OPUS OpenSubtitles, SUBTLEX, and other corpora",
            license =
                "Data: CC BY-SA 4.0 — https://creativecommons.org/licenses/by-sa/4.0/; code: Apache License 2.0 " +
                    "(word frequency ranking)",
            url = "https://github.com/rspeer/wordfreq",
            textFile = APACHE_2_0,
        ),
        LicenseNotice(
            name = "OpenCorpora (via pymorphy3-dicts-ru)",
            holder = "OpenCorpora contributors",
            license = "CC BY-SA 3.0 — https://creativecommons.org/licenses/by-sa/3.0/ (Russian words)",
            url = "http://opencorpora.org",
        ),
        LicenseNotice(
            name = "Zemberek-NLP and zemberek-python",
            holder = "Copyright 2018 Ahmet A. Akın, Mehmet D. Akın; Python port Copyright 2020 Loodos",
            license = "Apache License 2.0 (Turkish words)",
            url = "https://github.com/ahmetaa/zemberek-nlp",
            textFile = APACHE_2_0,
        ),
        LicenseNotice(
            name = "Sudoku Exchange Puzzle Bank",
            holder = "Grant McLean",
            license = "Public domain dedication (Sudoku puzzles)",
            url = "https://github.com/grantm/sudoku-exchange-puzzle-bank",
        ),
        LicenseNotice(
            name = "Kotlin, kotlinx, Compose Multiplatform, Jetpack Compose, AndroidX (Room, DataStore, Navigation)",
            holder = "JetBrains s.r.o., The Android Open Source Project, and contributors",
            license = "Apache License 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0",
            textFile = APACHE_2_0,
        ),
        LicenseNotice(
            name = "Yandex Mobile Ads SDK",
            holder = "YANDEX LLC",
            license = "Terms of Use of Yandex Advertising Network",
            url = "https://legal.yandex.com/partner_ch/",
            androidOnly = true,
        ),
    )

/** The notices one platform shows: the Web build carries no Android advertising or payment SDK. */
fun licenseNoticesFor(android: Boolean): List<LicenseNotice> = LICENSE_NOTICES.filter { android || !it.androidOnly }

/**
 * The shared «Licences» page: one card per work with its authors, licence, and source, and the full
 * licence text behind a tap where the licence asks to keep it. Nothing is fetched from the network.
 */
@Composable
fun LicensesContent(
    notices: List<LicenseNotice>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        contentPadding = PaddingValues(horizontal = LogicaSpacing.screenHorizontal, vertical = LogicaSpacing.screenVertical),
    ) {
        item {
            Text(
                stringResource(Res.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(notices, key = { it.name }) { notice -> LicenseCard(notice) }
    }
}

@Composable
private fun LicenseCard(notice: LicenseNotice) {
    var expanded by rememberSaveable(notice.name) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(LogicaSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
            Text(notice.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(notice.holder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(notice.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(notice.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            notice.textFile?.let { file ->
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) Res.string.licenses_hide_text else Res.string.licenses_show_text))
                }
                if (expanded) LicenseText(file)
            }
        }
    }
}

@Composable
private fun LicenseText(file: String) {
    val text by produceState("", file) { value = runCatching { reflowLicense(Res.readBytes(file).decodeToString()) }.getOrDefault("") }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The licence files keep their original fixed-width line breaks; on a phone those wrap raggedly, so
 * the lines of one paragraph are joined and only blank lines separate paragraphs.
 */
fun reflowLicense(text: String): String =
    text
        .replace("\r\n", "\n")
        .split(Regex("\n\\s*\n"))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
