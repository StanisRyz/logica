package com.stanisryz.logica.web

import com.stanisryz.logica.platform.SaveData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 45.12 regressions: Player-scoped unified saves never leak across Players, and the
 * versioned SaveData envelope round-trips through save/load without any change of state.
 */
class WebCloudSavesTest {
    private val storageA = mutableMapOf<String, String>()
    private val storageB = mutableMapOf<String, String>()

    private fun repository(
        key: String,
        storage: MutableMap<String, String>,
    ): LocalSaveRepository = LocalSaveRepository(key, { storage[key] }, { k, v -> storage[k] = v })

    private val economySection = byteArrayOf(1, 2, 3)
    private val storeSection = byteArrayOf(9, 8)

    @Test
    fun playerScopedSavesStayIsolatedAcrossPlayers() =
        runTest {
            val playerA = repository("logica_save_test_a", storageA)
            val playerB = repository("logica_save_test_b", storageB)

            assertTrue(
                playerA.save(SaveData(sections = mapOf(WebSaveSectionIds.ECONOMY to economySection))),
            )

            // Player B loads nothing — Player A's data is invisible in B's scope.
            assertNull(playerB.load())
            // Player A reloads exactly their own durable state.
            val restored = playerA.load()
            assertEquals(SaveData.CURRENT_VERSION, restored?.version)
            assertTrue(economySection.contentEquals(restored?.section(WebSaveSectionIds.ECONOMY)))
            assertNull(restored?.section(WebSaveSectionIds.STORE))
        }

    @Test
    fun saveDataRoundTripsThroughCodecAndManagerWithoutStateChange() =
        runTest {
            val economySectionAdapter =
                object : WebSaveSection {
                    override val id = WebSaveSectionIds.ECONOMY
                    var applied: ByteArray? = null

                    override fun export(): ByteArray = economySection

                    override fun apply(payload: ByteArray) {
                        applied = payload
                    }
                }
            val storeSectionAdapter =
                object : WebSaveSection {
                    override val id = WebSaveSectionIds.STORE
                    var applied: ByteArray? = null

                    override fun export(): ByteArray = storeSection

                    override fun apply(payload: ByteArray) {
                        applied = payload
                    }
                }
            val storage = mutableMapOf<String, String>()
            val manager =
                WebSaveManager(
                    listOf(economySectionAdapter, storeSectionAdapter),
                    repository("logica_save_test_rt", storage),
                )

            // Persist collects every exported section into one versioned payload.
            assertTrue(manager.persist())

            // The stored bytes decode back into an identical envelope.
            val encoded = storage.getValue("logica_save_test_rt")
            val decoded = WebSaveCodec.decode(WebBase64.decode(encoded)!!)!!
            assertEquals(SaveData.CURRENT_VERSION, decoded.version)
            assertEquals(setOf(WebSaveSectionIds.ECONOMY, WebSaveSectionIds.STORE), decoded.sections.keys)

            // Restore applies each section payload unchanged.
            assertTrue(manager.restore())
            assertEquals(economySection.toList(), (economySectionAdapter.applied ?: ByteArray(0)).toList())
            assertEquals(storeSection.toList(), (storeSectionAdapter.applied ?: ByteArray(0)).toList())
        }
}
