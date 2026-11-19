package cc.samlab.classroom.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceReminderTest {
    @Test fun firstPollEstablishesBaselineAndLaterPollNotifiesOnlyNewTasks() {
        assertEquals(emptySet<Long>(), newAttendanceTasks(false, emptySet(), setOf(1, 2)))
        assertEquals(setOf(3L), newAttendanceTasks(true, setOf(1, 2), setOf(2, 3)))
        assertEquals(emptySet<Long>(), newAttendanceTasks(true, setOf(1, 2, 3), setOf(2, 3)))
    }
}
