package co.touchlab.skie.plugin.generator.internal.analytics.system

import co.touchlab.skie.plugin.analytics.producer.AnalyticsProducer
import co.touchlab.skie.util.Command

object SysctlAnalyticsProducer : AnalyticsProducer {

    override val name: String = "sys"

    override fun produce(): ByteArray =
        Command(
            "sh",
            "-c",
            """sysctl -ae | grep -v kern.hostname""",
        )
            .execute()
            .outputLines
            .joinToString(System.lineSeparator())
            .toByteArray()
}
