package com.attendance.server.services

/** Only explicit, authored validation messages may be returned to an unauthenticated client. */
class AuthRequestException(val code: String, message: String, val status: Int = 400) : IllegalArgumentException(message)
