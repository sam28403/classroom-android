package cc.samlab.classroom.data

internal fun validCheckInResponse(httpStatus: Int, status: String): Boolean =
    (httpStatus == 201 && status in setOf("present", "late")) ||
        (httpStatus == 202 && status == "exception")
