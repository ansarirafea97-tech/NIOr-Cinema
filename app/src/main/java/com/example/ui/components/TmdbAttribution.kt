package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Authentic TMDB 3-tier stacked logo matching official Infuse & TMDB branding.
 * - Row 1: "THE" + cyan pill bar
 * - Row 2: "MOVIE"
 * - Row 3: cyan pill bar + "DB"
 */
@Composable
fun TmdbOfficialLogo(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF01B4E4)
) {
    Column(
        modifier = modifier.testTag("tmdb_official_logo"),
        verticalArrangement = Arrangement.spacedBy(0.5.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            Text(
                text = "THE",
                color = color,
                fontSize = 5.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
                lineHeight = 6.sp
            )
            Box(
                modifier = Modifier
                    .width(9.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
        Text(
            text = "MOVIE",
            color = color,
            fontSize = 6.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp,
            lineHeight = 7.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(9.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
            Text(
                text = "DB",
                color = color,
                fontSize = 5.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
                lineHeight = 6.sp
            )
        }
    }
}

/**
 * Clean, compliant TMDB attribution disclaimer component.
 * Required by TMDB API terms of service.
 */
@Composable
fun TmdbAttribution(
    modifier: Modifier = Modifier
) {
    Text(
        text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("tmdb_attribution_text")
    )
}

