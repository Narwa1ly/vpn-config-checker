package app.vpncheck.data.subscription

/** Black lists = configs that bypass RKN black-list blocking; white lists = servers hosted in Russian white-listed subnets. */
enum class ListKind(val label: String, val shortLabel: String, val emoji: String) {
    BLACK("Чёрные списки", "чёрные", "🏴"),
    WHITE("Белые списки", "белые", "🏳️");
}

/** One subscription file from igareck/vpn-configs-for-russia. */
data class SubscriptionSource(
    val id: String,
    val fileName: String,
    val title: String,
    val description: String,
    val kind: ListKind,
    val enabledByDefault: Boolean = true,
) {
    fun urls(): List<String> = MIRRORS.map { it.replace("{file}", fileName) }

    companion object {
        /** Ordered by how often they are reachable from Russian networks. */
        val MIRRORS = listOf(
            "https://raw.githack.com/igareck/vpn-configs-for-russia/main/{file}",
            "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/main/{file}",
            "https://gitlab.com/igareck/vpn-configs-for-russia/-/raw/main/{file}",
            "https://codeberg.org/igareck/vpn-configs-for-russia/raw/branch/main/{file}",
            "https://bitbucket.org/igareck/vpn-configs-for-russia/raw/main/{file}",
            "https://gitea.com/igareck/vpn-configs-for-russia/raw/branch/main/{file}",
        )

        val ALL = listOf(
            SubscriptionSource(
                id = "black_vless_mobile",
                fileName = "BLACK_VLESS_RUS_mobile.txt",
                title = "Чёрные списки · Mobile",
                description = "VLESS/VMess, отобранные под мобильные сети",
                kind = ListKind.BLACK,
            ),
            SubscriptionSource(
                id = "black_vless",
                fileName = "BLACK_VLESS_RUS.txt",
                title = "Чёрные списки · VLESS",
                description = "Полный список VLESS (xhttp, reality)",
                kind = ListKind.BLACK,
            ),
            SubscriptionSource(
                id = "black_ss_all",
                fileName = "BLACK_SS+All_RUS.txt",
                title = "Чёрные списки · SS, Hy2, VMess, Trojan",
                description = "Смешанный список; Hysteria2 и insecure-конфиги проверяются через sing-box",
                kind = ListKind.BLACK,
            ),
            SubscriptionSource(
                id = "black_ss_weak_dpi",
                fileName = "BLACK_SS_WEAK_DPI_RUS.txt",
                title = "Чёрные списки · SS weak DPI",
                description = "Shadowsocks без обфускации для сетей со слабым DPI",
                kind = ListKind.BLACK,
                enabledByDefault = false,
            ),
            SubscriptionSource(
                id = "white_vless_mobile",
                fileName = "Vless-Reality-White-Lists-Rus-Mobile.txt",
                title = "Белые списки · CIDR Mobile",
                description = "Топ-150 из полной CIDR-подписки: серверы в белых подсетях российских хостеров",
                kind = ListKind.WHITE,
            ),
            SubscriptionSource(
                id = "white_cidr_all",
                fileName = "WHITE-CIDR-RU-all.txt",
                title = "Белые списки · CIDR полная",
                description = "Все известные белые подсети разных хостеров, VLESS",
                kind = ListKind.WHITE,
            ),
            SubscriptionSource(
                id = "white_cidr_checked",
                fileName = "WHITE-CIDR-RU-checked.txt",
                title = "Белые списки · VK, Yandex, CDNvideo, Beeline",
                description = "Только подсети этих российских хостеров",
                kind = ListKind.WHITE,
            ),
            SubscriptionSource(
                id = "white_sni_all",
                fileName = "WHITE-SNI-RU-all.txt",
                title = "Белые списки · SNI",
                description = "Обход только SNI-блокировок по фейковому домену; CIDR-блокировки не обходит. Бывает пустой",
                kind = ListKind.WHITE,
            ),
        )

        fun byId(id: String): SubscriptionSource? = ALL.firstOrNull { it.id == id }

        fun byKind(kind: ListKind): List<SubscriptionSource> = ALL.filter { it.kind == kind }

        fun kindsOf(sourceIds: Collection<String>): Set<ListKind> =
            sourceIds.mapNotNull { byId(it)?.kind }.toSet()
    }
}
