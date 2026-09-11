package com.tlogger.core

/**
 * 一个只增不减的计数器。
 *
 * 为什么要单独做它：日志可能被好几个线程同时写，而统计本来是要拿去回答
 * "到底丢了多少条"的——**会计错账的计数器比没有计数器更糟**。
 * 实测就抓到过：4 条线程各打 500 条，记账报 1999。
 */
internal expect class Counter() {
    fun increment()
    fun value(): Int
}
