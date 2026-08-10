package com.rmm.app.ui.screen.journeys

internal data class NetworkMapPoint(val x: Float, val y: Float)

internal data class NetworkMapLineGeometry(
    val code: String,
    val startLabel: NetworkMapPoint,
    val endLabel: NetworkMapPoint,
    val stationCodes: List<String>,
)

internal object NetworkMapGeometry {
    const val WIDTH = 920f
    const val HEIGHT = 820f

    val stations: Map<String, NetworkMapPoint> = mapOf(
        "ST001" to NetworkMapPoint(50f, 650f),
        "ST002" to NetworkMapPoint(200f, 650f),
        "ST003" to NetworkMapPoint(125f, 600f),
        "ST004" to NetworkMapPoint(100f, 525f),
        "ST005" to NetworkMapPoint(200f, 550f),
        "ST006" to NetworkMapPoint(150f, 350f),
        "ST007" to NetworkMapPoint(150f, 450f),
        "ST008" to NetworkMapPoint(250f, 450f),
        "ST009" to NetworkMapPoint(150f, 250f),
        "ST010" to NetworkMapPoint(250f, 350f),
        "ST011" to NetworkMapPoint(250f, 150f),
        "ST012" to NetworkMapPoint(250f, 250f),
        "ST013" to NetworkMapPoint(350f, 650f),
        "ST014" to NetworkMapPoint(450f, 650f),
        "ST015" to NetworkMapPoint(650f, 650f),
        "ST016" to NetworkMapPoint(750f, 650f),
        "ST017" to NetworkMapPoint(850f, 650f),
        "ST018" to NetworkMapPoint(350f, 550f),
        "ST019" to NetworkMapPoint(450f, 550f),
        "ST020" to NetworkMapPoint(550f, 550f),
        "ST021" to NetworkMapPoint(650f, 550f),
        "ST022" to NetworkMapPoint(750f, 550f),
        "ST023" to NetworkMapPoint(850f, 550f),
        "ST024" to NetworkMapPoint(450f, 500f),
        "ST025" to NetworkMapPoint(500f, 500f),
        "ST026" to NetworkMapPoint(350f, 450f),
        "ST027" to NetworkMapPoint(450f, 450f),
        "ST028" to NetworkMapPoint(550f, 450f),
        "ST029" to NetworkMapPoint(650f, 450f),
        "ST030" to NetworkMapPoint(750f, 450f),
        "ST031" to NetworkMapPoint(850f, 450f),
        "ST032" to NetworkMapPoint(400f, 400f),
        "ST033" to NetworkMapPoint(550f, 350f),
        "ST034" to NetworkMapPoint(650f, 350f),
        "ST035" to NetworkMapPoint(650f, 250f),
        "ST036" to NetworkMapPoint(750f, 350f),
        "ST037" to NetworkMapPoint(350f, 350f),
        "ST038" to NetworkMapPoint(450f, 350f),
        "ST039" to NetworkMapPoint(550f, 250f),
        "ST040" to NetworkMapPoint(350f, 250f),
        "ST041" to NetworkMapPoint(450f, 250f),
        "ST042" to NetworkMapPoint(550f, 150f),
        "ST043" to NetworkMapPoint(350f, 150f),
        "ST044" to NetworkMapPoint(450f, 150f),
        "ST045" to NetworkMapPoint(350f, 50f),
        "ST046" to NetworkMapPoint(550f, 650f),
        "ST047" to NetworkMapPoint(750f, 750f),
        "ST048" to NetworkMapPoint(850f, 750f),
        "ST049" to NetworkMapPoint(200f, 750f),
        "ST050" to NetworkMapPoint(100f, 750f),
    )

    val lines = listOf(
        NetworkMapLineGeometry(
            "L1",
            NetworkMapPoint(331f, 15f),
            NetworkMapPoint(705f, 457f),
            listOf("ST045", "ST043", "ST011", "ST009", "ST010", "ST037", "ST038", "ST033", "ST028", "ST020", "ST015", "ST016", "ST017", "ST023", "ST030"),
        ),
        NetworkMapLineGeometry(
            "L2",
            NetworkMapPoint(2f, 623f),
            NetworkMapPoint(405f, 457f),
            listOf("ST001", "ST003", "ST005", "ST018", "ST019", "ST020", "ST021", "ST022", "ST023", "ST031", "ST036", "ST035", "ST039", "ST038", "ST027"),
        ),
        NetworkMapLineGeometry(
            "L3",
            NetworkMapPoint(180f, 762f),
            NetworkMapPoint(830f, 762f),
            listOf("ST049", "ST002", "ST003", "ST004", "ST007", "ST008", "ST026", "ST027", "ST028", "ST029", "ST022", "ST017", "ST048"),
        ),
        NetworkMapLineGeometry(
            "L4",
            NetworkMapPoint(2f, 653f),
            NetworkMapPoint(805f, 457f),
            listOf("ST001", "ST002", "ST013", "ST014", "ST019", "ST024", "ST027", "ST032", "ST037", "ST041", "ST042", "ST035", "ST034", "ST029", "ST030", "ST031"),
        ),
        NetworkMapLineGeometry(
            "L5",
            NetworkMapPoint(80f, 762f),
            NetworkMapPoint(732f, 762f),
            listOf("ST050", "ST002", "ST005", "ST008", "ST037", "ST040", "ST043", "ST044", "ST042", "ST039", "ST034", "ST030", "ST022", "ST016", "ST047"),
        ),
        NetworkMapLineGeometry(
            "L6",
            NetworkMapPoint(330f, 665f),
            NetworkMapPoint(530f, 662f),
            listOf("ST013", "ST005", "ST007", "ST006", "ST009", "ST012", "ST040", "ST041", "ST039", "ST033", "ST027", "ST025", "ST020", "ST046"),
        ),
    )
}
