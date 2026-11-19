package cc.samlab.classroom.data

import org.junit.Assert.*
import org.junit.Test

class AttendanceResultTest {
    @Test fun onlyContractStatusPairsAreAccepted() {
        assertTrue(validCheckInResponse(201, "present"))
        assertTrue(validCheckInResponse(201, "late"))
        assertTrue(validCheckInResponse(202, "exception"))
        assertFalse(validCheckInResponse(200, "present"))
        assertFalse(validCheckInResponse(201, "exception"))
        assertFalse(validCheckInResponse(202, "present"))
        assertFalse(validCheckInResponse(201, ""))
    }
}
