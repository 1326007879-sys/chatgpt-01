package io.github.chatgpt01.petai.reminders

import org.junit.Assert.*
import org.junit.Test

class ReminderSpeechPolicyTest {
    @Test fun automaticPlaybackRespectsLockSilentAndDoNotDisturb() {
        assertTrue(ReminderSpeechPolicy.allowed(true,false,true,true))
        assertFalse(ReminderSpeechPolicy.allowed(true,true,true,true))
        assertFalse(ReminderSpeechPolicy.allowed(true,false,false,true))
        assertFalse(ReminderSpeechPolicy.allowed(true,false,true,false))
    }
    @Test fun manualPlaybackStillRequiresUnlockedDevice() {
        assertTrue(ReminderSpeechPolicy.allowed(false,false,false,false))
        assertFalse(ReminderSpeechPolicy.allowed(false,true,true,true))
    }
}
