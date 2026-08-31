package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.InMemoryProfitabilityConfigProvider
import com.ridedecider.app.data.accessibility.uber.RawUberTripOfferMapper
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityParser
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityProcessor
import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot
import com.ridedecider.app.data.accessibility.uber.UberOfferValidator
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [UberFullNodeDumper] y el control de cooldown de 5 segundos.
 */
class UberFullNodeDumperTest {

    @Test
    fun nullSnapshot_shouldReturnNullPlaceholder() {
        val result = UberFullNodeDumper.dump(null)
        assertTrue(result.dumpText.contains("[FULL_NODE_DUMP]"))
        assertTrue(result.dumpText.contains("nodes=0"))
        assertTrue(result.dumpText.contains("[FULL_NODE_DUMP_END]"))
    }

    @Test
    fun validTree_shouldOutputCompactFormatWithDepthAndAttributes() {
        val child = UberNodeSnapshot(
            className = "android.widget.TextView",
            text = "5,02 €",
            viewIdResourceName = "com.ubercab.driver:id/fare_text",
            boundsInScreen = "[100,200][300,400]",
            isVisibleToUser = true,
            isClickable = false
        )
        val button = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Aceptar",
            viewIdResourceName = "com.ubercab.driver:id/accept_button",
            boundsInScreen = "[50,500][500,600]",
            isVisibleToUser = true,
            isClickable = true
        )
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            boundsInScreen = "[0,0][1080,2400]",
            children = listOf(child, button)
        )

        val result = UberFullNodeDumper.dump(root)

        assertEquals(3, result.totalNodes)
        assertEquals(2, result.textNodesCount)
        assertEquals(2, result.resourceIdCount)
        assertEquals(3, result.visibleNodesCount)
        assertEquals(1, result.clickableNodesCount)

        // Comprobar formato exacto
        assertTrue(result.dumpText.contains("[FULL_NODE_DUMP]"))
        assertTrue(result.dumpText.contains("nodes=3"))
        assertTrue(result.dumpText.contains("NODE #1 depth=0"))
        assertTrue(result.dumpText.contains("class=android.widget.FrameLayout"))
        assertTrue(result.dumpText.contains("bounds=[0,0][1080,2400]"))

        assertTrue(result.dumpText.contains("NODE #2 depth=1"))
        assertTrue(result.dumpText.contains("text=\"5,02 €\""))
        assertTrue(result.dumpText.contains("id=\"com.ubercab.driver:id/fare_text\""))

        assertTrue(result.dumpText.contains("NODE #3 depth=1"))
        assertTrue(result.dumpText.contains("text=\"Aceptar\""))
        assertTrue(result.dumpText.contains("clickable=true"))

        assertTrue(result.dumpText.contains("[FULL_NODE_DUMP_END]"))
        assertTrue(result.dumpText.contains("textNodes=2"))
        assertTrue(result.dumpText.contains("clickableNodes=1"))
    }

    @Test
    fun processorEnforces5SecondCooldownForFullNodeDump() {
        val snapshot = UberNodeSnapshot(text = "UberX", className = "android.widget.TextView")
        val logger = InMemoryAccessibilityDiagnosticLogger()
        val processor = UberAccessibilityProcessor(
            parser = UberAccessibilityParser(),
            validator = UberOfferValidator(),
            mapper = RawUberTripOfferMapper(),
            evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
            configProvider = InMemoryProfitabilityConfigProvider(),
            diagnosticLogger = logger
        )

        // Evento 1 a t=1000ms -> ejecuta full dump
        processor.processSnapshot(snapshot, currentTime = 1000L)
        // Evento 2 a t=2000ms (1s después) -> NO debe ejecutar full dump
        processor.processSnapshot(snapshot, currentTime = 2000L)
        // Evento 3 a t=4000ms (3s después) -> NO debe ejecutar full dump
        processor.processSnapshot(snapshot, currentTime = 4000L)
        // Evento 4 a t=6001ms (>5s después) -> SÍ ejecuta full dump
        processor.processSnapshot(snapshot, currentTime = 6001L)

        assertEquals(2, logger.fullDumpLogs.size)
    }
}
