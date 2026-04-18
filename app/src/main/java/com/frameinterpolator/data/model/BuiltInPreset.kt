package com.frameinterpolator.data.model

enum class BuiltInPreset(
    val title: String,
    val description: String,
    val preferredFps: Int,
    val preferredHeight: Int?,
    val preferredQuality: ProcessingConfig.QualityPreset,
    val tag: String
) {
    SOCIAL_SMOOTH(
        title = "Sosyal 60",
        description = "Kısa format video ve hikâye paylaşımı için hızlı ve dengeli profil.",
        preferredFps = 60,
        preferredHeight = 1080,
        preferredQuality = ProcessingConfig.QualityPreset.MEDIUM,
        tag = "Reels"
    ),
    CINEMATIC(
        title = "Sinematik 60",
        description = "Daha temiz detay ve kontrollü export hızı için premium görünüm odaklı profil.",
        preferredFps = 60,
        preferredHeight = 1080,
        preferredQuality = ProcessingConfig.QualityPreset.HIGH,
        tag = "Film"
    ),
    GAMEPLAY_120(
        title = "Gameplay 120",
        description = "Aksiyon ve oyun kayıtlarında maksimum akıcılığa odaklanır.",
        preferredFps = 120,
        preferredHeight = null,
        preferredQuality = ProcessingConfig.QualityPreset.HIGH,
        tag = "Oyun"
    ),
    FAST_DELIVERY(
        title = "Hızlı Teslim",
        description = "Dosya boyutunu ve bekleme süresini makul tutan hafif teslim profili.",
        preferredFps = 30,
        preferredHeight = 720,
        preferredQuality = ProcessingConfig.QualityPreset.LOW,
        tag = "Hızlı"
    )
}
