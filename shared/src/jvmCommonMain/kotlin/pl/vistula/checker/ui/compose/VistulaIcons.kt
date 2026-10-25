package pl.vistula.checker.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Pure-Kotlin vector icons for Compose Multiplatform (no emoji icons). */
object VistulaIcons {
    val Search: ImageVector by lazy {
        ImageVector.Builder(name = "Search", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(20f, 20f); lineTo(15.5f, 15.5f)
                moveTo(17f, 10f)
                arcTo(7f, 7f, 0f, true, true, 3f, 10f)
                arcTo(7f, 7f, 0f, true, true, 17f, 10f)
                close()
            }
        }.build()
    }
    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 10.5f)
                lineTo(12f, 3f)
                lineTo(21f, 10.5f)
                lineTo(21f, 20f)
                lineTo(14.5f, 20f)
                lineTo(14.5f, 14f)
                lineTo(9.5f, 14f)
                lineTo(9.5f, 20f)
                lineTo(3f, 20f)
                close()
            }
        }.build()
    }

    val Schedule: ImageVector by lazy {
        ImageVector.Builder(
            name = "Schedule",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
                moveTo(4f, 9.5f)
                lineTo(20f, 9.5f)
                moveTo(8f, 3f)
                lineTo(8f, 6.5f)
                moveTo(16f, 3f)
                lineTo(16f, 6.5f)
            }
        }.build()
    }

    val Grades: ImageVector by lazy {
        ImageVector.Builder(
            name = "Grades",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 9f)
                lineTo(12f, 4f)
                lineTo(22f, 9f)
                lineTo(12f, 14f)
                close()
                moveTo(6f, 11.5f)
                lineTo(6f, 17f)
                lineTo(12f, 20f)
                lineTo(18f, 17f)
                lineTo(18f, 11.5f)
            }
        }.build()
    }

    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 16f)
                lineTo(6f, 16f)
                lineTo(7.5f, 14f)
                lineTo(7.5f, 10f)
                lineTo(10f, 6f)
                lineTo(14f, 6f)
                lineTo(16.5f, 10f)
                lineTo(16.5f, 14f)
                close()
                moveTo(10.5f, 19f)
                lineTo(13.5f, 19f)
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                moveTo(4f, 12f)
                lineTo(20f, 12f)
                moveTo(4f, 18f)
                lineTo(20f, 18f)
                moveTo(8f, 4f)
                lineTo(8f, 8f)
                moveTo(15f, 10f)
                lineTo(15f, 14f)
                moveTo(10f, 16f)
                lineTo(10f, 20f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 13f)
                lineTo(10f, 18f)
                lineTo(19f, 7f)
            }
        }.build()
    }

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "Warning",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(22f, 20f)
                lineTo(2f, 20f)
                close()
                moveTo(12f, 9f)
                lineTo(12f, 14f)
                moveTo(12f, 17f)
                lineTo(12.01f, 17f)
            }
        }.build()
    }
}

