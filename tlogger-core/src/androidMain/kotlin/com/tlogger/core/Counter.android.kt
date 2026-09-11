package com.tlogger.core

import java.util.concurrent.atomic.AtomicInteger

internal actual class Counter actual constructor() {
    private val value = AtomicInteger(0)

    actual fun increment() {
        value.incrementAndGet()
    }

    actual fun value(): Int = value.get()
}
