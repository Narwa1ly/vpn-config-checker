package app.vpncheck.core.engine

import app.vpncheck.core.parser.CoreType
import app.vpncheck.core.parser.LinkParser
import app.vpncheck.data.subscription.SubscriptionParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Not a real test: when VPNCHECK_DUMP_LINKS (path to a subscription .txt) and VPNCHECK_DUMP_OUT
 * (directory) are set, writes one core JSON per link so the configs can be exercised against
 * desktop builds of Xray-core / sing-box. Skipped otherwise.
 */
class ConfigDumpTool {
    @Test
    fun dump() {
        val linksPath = System.getenv("VPNCHECK_DUMP_LINKS")
        val outPath = System.getenv("VPNCHECK_DUMP_OUT")
        assumeTrue(linksPath != null && outPath != null)
        val out = File(outPath!!).apply { mkdirs() }
        val links = SubscriptionParser.extractLinks(File(linksPath!!).readText())
        var port = System.getenv("VPNCHECK_DUMP_PORT_BASE")?.toIntOrNull() ?: 31000
        val index = StringBuilder()
        for (link in links) {
            val parsed = LinkParser.parse(link)
            if (parsed.isFailure) {
                index.append("PARSE_FAIL\t${parsed.exceptionOrNull()?.message}\t${link.take(80)}\n")
                continue
            }
            val cfg = parsed.getOrThrow()
            if (cfg.unsupportedReason() != null) { index.append("UNSUPPORTED\t${cfg.unsupportedReason()}\t${link.take(80)}\n"); continue }
            val json = when (cfg.core) {
                CoreType.XRAY -> XrayConfigBuilder.build(cfg, port)
                CoreType.SINGBOX -> SingBoxConfigBuilder.build(cfg, port)
            }
            val name = "${cfg.id.take(10)}.json"
            File(out, name).writeText(json.toString(2))
            index.append("${cfg.core}\t$port\t$name\t${cfg.protocol}\t${cfg.transport}\t${cfg.security}\t${cfg.host}:${cfg.port}\t${cfg.name}\n")
            port++
        }
        File(out, "index.tsv").writeText(index.toString())
    }
}
