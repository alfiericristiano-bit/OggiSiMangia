package it.oggisimangia.app

import java.net.InetAddress
import java.net.URI

/** Explicit recipe links may use any public HTTPS recipe site. */
object RecipeLink {
    fun isValid(value: String): Boolean = try {
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme.equals("https", true) && uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) && host.contains('.') &&
            !host.endsWith('.') && !host.endsWith(".local") && !host.endsWith(".localhost") &&
            host.matches(Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+")) &&
            !host.matches(Regex("[0-9.]+"))
    } catch (_: Exception) { false }

    /** Called on the worker thread before each request, including redirects. */
    fun requirePublic(value: String) {
        require(isValid(value))
        val addresses = InetAddress.getAllByName(URI(value).host)
        require(addresses.isNotEmpty() && addresses.none {
            it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                it.isSiteLocalAddress || it.isMulticastAddress ||
                (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc)
        })
    }
}
