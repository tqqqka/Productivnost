package com.tihon.shtorka.core

/** Живые экземпляры служб — всё работает в одном процессе. */
object Hub {
    var service: com.tihon.shtorka.service.IslandService? = null
    var listener: com.tihon.shtorka.service.NotifListener? = null
}
