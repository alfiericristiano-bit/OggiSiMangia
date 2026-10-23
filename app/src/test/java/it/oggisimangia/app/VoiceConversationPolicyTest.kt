package it.oggisimangia.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceConversationPolicyTest {
    @Test fun explicitStopStopsEvenWithoutConfirmation() {
        assertEquals(VoiceConversationPolicy.Decision.STOP,
            VoiceConversationPolicy.classify("Fermati!", false))
        assertEquals(VoiceConversationPolicy.Decision.STOP,
            VoiceConversationPolicy.classify("chiudi la conversazione", true))
    }

    @Test fun confirmationOnlyCountsForPendingWriteAndWholeUtterance() {
        assertEquals(VoiceConversationPolicy.Decision.CONFIRM,
            VoiceConversationPolicy.classify("Sì, procedi", true))
        assertEquals(VoiceConversationPolicy.Decision.REJECT,
            VoiceConversationPolicy.classify("No", true))
        assertEquals(VoiceConversationPolicy.Decision.COMMAND,
            VoiceConversationPolicy.classify("Conferma", false))
        assertEquals(VoiceConversationPolicy.Decision.COMMAND,
            VoiceConversationPolicy.classify("Conferma la ricetta e elimina l'archivio", true))
    }

    @Test fun commandsContainingStopAreNotImplicitlyStopped() {
        assertEquals(VoiceConversationPolicy.Decision.COMMAND,
            VoiceConversationPolicy.classify("Come fermati nella ricetta?", false))
    }
}
