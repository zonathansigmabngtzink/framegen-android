package com.frameinterpolator.data.model

data class OutputSize(
    val width: Int,
    val height: Int,
    val label: String
)

data class ProcessingConfig(
    val targetFps: Int,
    val outputSize: OutputSize,
    val quality: QualityPreset,
    val interpolationMode: InterpolationMode = InterpolationMode.AI
) {
    enum class InterpolationMode(
        val label: String,
        val shortLabel: String,
        val description: String
    ) {
        AI(
            label = "Yapay Zeka",
            shortLabel = "AI",
            description = "RIFE modeliyle gerçek ara kare üretir. Daha yavaş ama daha güçlüdür."
        ),
        CLASSIC(
            label = "Hızlı",
            shortLabel = "Klasik",
            description = "FFmpeg minterpolate kullanır. Daha hızlıdır ama AI değildir."
        )
    }

    enum class QualityPreset(
        val crf: Int,
        val preset: String,
        val label: String,
        val description: String
    ) {
        LOW(26, "veryfast", "Düşük", "En hızlı export ve en küçük dosya boyutu."),
        MEDIUM(23, "medium", "Orta", "Kalite ve hız arasında dengeli seçim."),
        HIGH(20, "slow", "Yüksek", "Daha net görüntü ve daha büyük çıktı dosyası.")
    }
}
