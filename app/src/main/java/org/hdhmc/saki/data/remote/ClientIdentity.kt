package org.hdhmc.saki.data.remote

import org.hdhmc.saki.BuildConfig

internal val HTTP_USER_AGENT: String =
    "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
