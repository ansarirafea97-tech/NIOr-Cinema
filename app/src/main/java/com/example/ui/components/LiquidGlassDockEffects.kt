package com.example.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Hardware-accelerated optical shaders and RenderEffects for the Noir Cinema
 * Liquid Glass navigation dock and interactive press-and-hold magnifying lens.
 *
 * - API 33+ (Android 13+): Real-time backdrop blur chained with AGSL RuntimeShader
 *   perimeter refraction, chromatic dispersion, Fresnel specular catchlights, and
 *   convex spherical background magnification/distortion inside the floating lens.
 * - API 31–32 (Android 12/12L): Real-time backdrop RenderEffect blur + color boost
 *   paired with Compose scale magnification for the press-and-hold lens.
 * - API 26–30: Falls back cleanly to translucent layered glass + Compose scale magnification.
 */
object LiquidGlassDockEffects {

    private const val DOCK_REFRACTION_AGSL = """
        uniform shader childShader;
        uniform float2 resolution;
        uniform float cornerRadius;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
        }

        float2 gradRoundedBox(float2 p, float2 b, float r) {
            float eps = 1.5;
            float dx = sdRoundedBox(p + float2(eps, 0.0), b, r) - sdRoundedBox(p - float2(eps, 0.0), b, r);
            float dy = sdRoundedBox(p + float2(0.0, eps), b, r) - sdRoundedBox(p - float2(0.0, eps), b, r);
            float2 g = float2(dx, dy);
            return g / max(length(g), 0.0001);
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = resolution * 0.5;
            float2 p = fragCoord - halfSize;
            float r = min(cornerRadius, min(halfSize.x, halfSize.y));
            float d = sdRoundedBox(p, halfSize, r);
            float distFromEdge = max(-d, 0.0);

            float bezelWidth = min(r * 0.45, 14.0);
            float edgeMask = 1.0 - smoothstep(0.0, bezelWidth, distFromEdge);
            float2 n = gradRoundedBox(p, halfSize, r);

            // Restrict subtle glass rim optics strictly to TOP and BOTTOM horizontal edges (zero on left/right vertical edges)
            float topBottomMask = smoothstep(0.36, 0.80, abs(n.y));
            float tbEdge = edgeMask * topBottomMask;

            // Gentle, controlled glass bevel refraction (no text duplication or excessive distortion)
            float refractStrength = pow(tbEdge, 1.65) * 7.5;
            float2 baseCoord = fragCoord - float2(n.x * refractStrength * 0.25, n.y * refractStrength);
            float2 chromaOffset = float2(0.0, n.y) * (pow(tbEdge, 1.85) * 1.35);

            float2 maxCoord = max(resolution - float2(1.5), float2(1.5));
            float2 cR = clamp(baseCoord + chromaOffset, float2(0.5), maxCoord);
            float2 cG = clamp(baseCoord, float2(0.5), maxCoord);
            float2 cB = clamp(baseCoord - chromaOffset, float2(0.5), maxCoord);

            half rCol = childShader.eval(cR).r;
            half gCol = childShader.eval(cG).g;
            half bCol = childShader.eval(cB).b;
            half aCol = childShader.eval(cG).a;

            // Natural curved glass Fresnel highlight & soft inner meniscus depth along top and bottom edges
            float innerDepth = smoothstep(1.8, 4.5, distFromEdge) * (1.0 - smoothstep(4.5, 10.5, distFromEdge)) * topBottomMask;
            float topLight = smoothstep(0.36, 0.84, -n.y);
            float bottomLight = smoothstep(0.36, 0.84, n.y);

            // Natural brightness variation along the horizontal glass surface
            float xNorm = p.x / max(halfSize.x, 1.0);
            float topVariance = 0.72 + 0.28 * cos(xNorm * 1.65 - 0.35);
            float bottomVariance = 0.68 + 0.32 * cos(xNorm * 1.55 + 0.45);

            float rimLip = (1.0 - smoothstep(0.0, 2.8, distFromEdge)) * max(topLight * topVariance, bottomLight * bottomVariance * 0.78);
            float softSheen = (1.0 - smoothstep(0.0, 7.5, distFromEdge)) * max(topLight * topVariance * 0.12, bottomLight * bottomVariance * 0.09);

            half boost = half(rimLip * 0.22 + softSheen);
            half shade = half(innerDepth * 0.08);

            return half4(
                clamp(rCol - shade + boost, half(0.0), half(1.0)),
                clamp(gCol - shade + boost, half(0.0), half(1.0)),
                clamp(bCol - shade + boost, half(0.0), half(1.0)),
                aCol
            );
        }
    """

    /**
     * Single-pass hardware-accelerated GPU shader for the Player Volume, Brightness, and 2x Speed
     * Liquid Glass HUD popups (API 33+).
     * Executes multi-tap Gaussian backdrop sampling, saturation boost, convex dome refraction,
     * bevel chromatic dispersion, meniscus depth, and Fresnel specular reflections directly on
     * the GPU in one RenderEffect pass (eliminating intermediate CPU/multi-FBO blur passes).
     */
    private const val HUD_PILL_REFRACTION_AGSL = """
        uniform shader childShader;
        uniform float2 resolution;
        uniform float cornerRadius;
        uniform float lightPhase;
        uniform float blurRadius;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
        }

        float2 gradRoundedBox(float2 p, float2 b, float r) {
            float eps = 1.25;
            float dx = sdRoundedBox(p + float2(eps, 0.0), b, r) - sdRoundedBox(p - float2(eps, 0.0), b, r);
            float dy = sdRoundedBox(p + float2(0.0, eps), b, r) - sdRoundedBox(p - float2(0.0, eps), b, r);
            float2 g = float2(dx, dy);
            return g / max(length(g), 0.0001);
        }

        half4 sampleBackdropBlur(float2 coord, float2 maxCoord, float span) {
            float2 c0 = clamp(coord, float2(0.5), maxCoord);
            half4 acc = childShader.eval(c0) * half(0.18);

            float d1 = span * 0.38;
            acc += childShader.eval(clamp(coord + float2( d1,  d1), float2(0.5), maxCoord)) * half(0.105);
            acc += childShader.eval(clamp(coord + float2(-d1,  d1), float2(0.5), maxCoord)) * half(0.105);
            acc += childShader.eval(clamp(coord + float2( d1, -d1), float2(0.5), maxCoord)) * half(0.105);
            acc += childShader.eval(clamp(coord + float2(-d1, -d1), float2(0.5), maxCoord)) * half(0.105);

            float d2 = span * 0.92;
            acc += childShader.eval(clamp(coord + float2( d2, 0.0), float2(0.5), maxCoord)) * half(0.05);
            acc += childShader.eval(clamp(coord + float2(-d2, 0.0), float2(0.5), maxCoord)) * half(0.05);
            acc += childShader.eval(clamp(coord + float2(0.0,  d2), float2(0.5), maxCoord)) * half(0.05);
            acc += childShader.eval(clamp(coord + float2(0.0, -d2), float2(0.5), maxCoord)) * half(0.05);

            float d3 = span * 0.65;
            acc += childShader.eval(clamp(coord + float2( d3,  d3), float2(0.5), maxCoord)) * half(0.05);
            acc += childShader.eval(clamp(coord + float2(-d3,  d3), float2(0.5), maxCoord)) * half(0.05);
            acc += childShader.eval(clamp(coord + float2( d3, -d3), float2(0.5), maxCoord)) * half(0.05);
            acc += childShader.eval(clamp(coord + float2(-d3, -d3), float2(0.5), maxCoord)) * half(0.05);
            return acc;
        }

        half3 boostSaturation(half3 rgb) {
            half luma = dot(rgb, half3(0.2126, 0.7152, 0.0722));
            return clamp(mix(half3(luma), rgb, half(1.38)), half3(0.0), half3(1.0));
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = resolution * 0.5;
            float2 p = fragCoord - halfSize;
            float r = min(cornerRadius, min(halfSize.x, halfSize.y));
            float d = sdRoundedBox(p, halfSize, r);
            float distFromEdge = max(-d, 0.0);

            float bezelWidth = min(r * 0.52, 11.0);
            float activeBand = max(bezelWidth, 8.8);
            float2 normP = p / max(halfSize, float2(1.0));
            float domeCurve = 1.0 - 0.045 * (1.0 - clamp(dot(normP, normP), 0.0, 1.0));
            float2 maxCoord = max(resolution - float2(1.5), float2(1.5));
            float blurSpan = max(blurRadius * 1.85, 1.0);

            // Fast interior path: single GPU blur + dome refraction + saturation without SDF gradient math
            if (distFromEdge >= activeBand) {
                float2 baseCoord = clamp(halfSize + p * domeCurve, float2(0.5), maxCoord);
                half4 blurred = sampleBackdropBlur(baseCoord, maxCoord, blurSpan);
                return half4(boostSaturation(blurred.rgb), blurred.a);
            }

            float edgeMask = 1.0 - smoothstep(0.0, bezelWidth, distFromEdge);
            float2 n = gradRoundedBox(p, halfSize, r);

            // Focus optical bevel refraction & specular rim along upper and lower curved edges
            float topBottomMask = smoothstep(0.28, 0.76, abs(n.y));
            float tbEdge = edgeMask * topBottomMask;

            // Subtle convex glass dome curvature + controlled rim bevel refraction
            float refractStrength = pow(tbEdge, 1.60) * 5.8;
            float2 baseCoord = halfSize + p * domeCurve - float2(n.x * refractStrength * 0.30, n.y * refractStrength);
            float2 chromaOffset = float2(n.x * 0.35, n.y) * (pow(tbEdge, 1.80) * 1.25);

            float2 cR = clamp(baseCoord + chromaOffset, float2(0.5), maxCoord);
            float2 cG = clamp(baseCoord, float2(0.5), maxCoord);
            float2 cB = clamp(baseCoord - chromaOffset, float2(0.5), maxCoord);

            half4 centerCol = sampleBackdropBlur(cG, maxCoord, blurSpan);
            float dispWeight = clamp(tbEdge * 0.42, 0.0, 0.42);
            half rSample = (childShader.eval(clamp(cR + float2(0.0, blurSpan * 0.22), float2(0.5), maxCoord)).r +
                childShader.eval(clamp(cR - float2(0.0, blurSpan * 0.22), float2(0.5), maxCoord)).r) * half(0.5);
            half bSample = (childShader.eval(clamp(cB + float2(0.0, blurSpan * 0.22), float2(0.5), maxCoord)).b +
                childShader.eval(clamp(cB - float2(0.0, blurSpan * 0.22), float2(0.5), maxCoord)).b) * half(0.5);

            half3 refractedRgb = half3(
                mix(centerCol.r, rSample, half(dispWeight)),
                centerCol.g,
                mix(centerCol.b, bSample, half(dispWeight))
            );
            half3 satRgb = boostSaturation(refractedRgb);

            // Curved glass inner meniscus trough & naturally varying upper/lower Fresnel reflections
            float innerDepth = smoothstep(1.4, 3.8, distFromEdge) * (1.0 - smoothstep(3.8, 8.8, distFromEdge)) * topBottomMask;
            float topLight = smoothstep(0.30, 0.82, -n.y);
            float bottomLight = smoothstep(0.30, 0.82, n.y);

            float xNorm = p.x / max(halfSize.x, 1.0);
            float topVariance = 0.70 + 0.30 * cos(xNorm * 1.75 - 0.30 - lightPhase);
            float bottomVariance = 0.66 + 0.34 * cos(xNorm * 1.60 + 0.42 + lightPhase * 0.85);

            float rimLip = (1.0 - smoothstep(0.0, 2.4, distFromEdge)) * max(topLight * topVariance, bottomLight * bottomVariance * 0.82);
            float softSheen = (1.0 - smoothstep(0.0, 6.2, distFromEdge)) * max(topLight * topVariance * 0.14, bottomLight * bottomVariance * 0.10);

            half boost = half(rimLip * 0.24 + softSheen);
            half shade = half(innerDepth * 0.09);

            return half4(
                clamp(satRgb.r - shade + boost, half(0.0), half(1.0)),
                clamp(satRgb.g - shade + boost, half(0.0), half(1.0)),
                clamp(satRgb.b - shade + boost, half(0.0), half(1.0)),
                centerCol.a
            );
        }
    """

    /**
     * Applied exclusively to the live backdrop content inside the floating magnifying lens.
     * Magnifies the center cleanly (~1.48x) while curving/refracting background details around
     * the rounded glass droplet perimeter with subtle RGB chromatic dispersion and Fresnel caustics.
     * Dock icons & labels are rendered separately on top so they remain sharp and never clip.
     */
    private const val LENS_BACKGROUND_REFRACTION_AGSL = """
        uniform shader childShader;
        uniform float2 resolution;
        uniform float cornerRadius;
        uniform float2 pointerCoord;
        uniform float magnification;
        uniform float lensProgress;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
        }

        float2 gradRoundedBox(float2 p, float2 b, float r) {
            float eps = 1.5;
            float dx = sdRoundedBox(p + float2(eps, 0.0), b, r) - sdRoundedBox(p - float2(eps, 0.0), b, r);
            float dy = sdRoundedBox(p + float2(0.0, eps), b, r) - sdRoundedBox(p - float2(0.0, eps), b, r);
            float2 g = float2(dx, dy);
            return g / max(length(g), 0.0001);
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = resolution * 0.5;
            float2 p = fragCoord - halfSize;
            float r = min(cornerRadius, min(halfSize.x, halfSize.y));

            float strength = clamp(lensProgress, 0.0, 1.0);
            float mag = max(magnification, 1.0);

            // Optical focal point tracks the pointer coordinates inside the lens GraphicsLayer
            float2 clampedPointer = clamp(pointerCoord, halfSize * 0.25, resolution - halfSize * 0.25);
            float2 focus = mix(halfSize, clampedPointer, 0.45 * strength);
            float2 focalDelta = fragCoord - focus;

            // Normalized radial position inside the lens droplet
            float2 normP = p / max(halfSize, float2(1.0));
            float radialSq = clamp(dot(normP, normP), 0.0, 1.35);

            // Strong convex magnification around pointer focus + meniscus curve along outer glass wall
            float centerScale = 1.0 / mag;
            float rimCurve = smoothstep(0.22, 1.0, radialSq);
            float sampleScale = mix(centerScale, 0.34, rimCurve * 0.74 * strength);
            float2 sampleP = focalDelta * sampleScale;

            float d = sdRoundedBox(p, halfSize, r);
            float distFromEdge = max(-d, 0.0);
            float bezelWidth = min(r * 0.72, 30.0);
            float edgeMask = 1.0 - smoothstep(0.0, bezelWidth, distFromEdge);
            float2 n = gradRoundedBox(p, halfSize, r);

            float2 sampleCoord = focus + sampleP - n * (pow(edgeMask, 1.65) * 18.0 * strength);
            float2 chroma = n * (pow(edgeMask, 1.45) * 4.8 * strength);

            float2 maxCoord = max(resolution - float2(1.5), float2(1.5));
            float2 cR = clamp(sampleCoord + chroma, float2(0.5), maxCoord);
            float2 cG = clamp(sampleCoord, float2(0.5), maxCoord);
            float2 cB = clamp(sampleCoord - chroma, float2(0.5), maxCoord);

            half rCol = childShader.eval(cR).r;
            half gCol = childShader.eval(cG).g;
            half bCol = childShader.eval(cB).b;
            half aCol = childShader.eval(cG).a;

            // 3D curved glass meniscus: subtle dark refractive trough just inside the bright outer catchlight
            float innerTrough = smoothstep(2.5, 6.0, distFromEdge) * (1.0 - smoothstep(6.0, 14.0, distFromEdge));
            float topFresnel = clamp((-n.y * 0.82 - n.x * 0.25) * 0.5 + 0.5, 0.0, 1.0);
            float bottomCaustic = clamp((n.y * 0.85 + n.x * 0.15) * 0.5 + 0.5, 0.0, 1.0);
            float rim = (1.0 - smoothstep(0.0, 3.8, distFromEdge)) * mix(0.24, 1.0, max(topFresnel, bottomCaustic * 0.65));
            half highlight = half(rim * 0.36 * strength);
            half shade = half(innerTrough * 0.18 * strength);

            return half4(
                clamp(rCol - shade + highlight, half(0.0), half(1.0)),
                clamp(gCol - shade + highlight, half(0.0), half(1.0)),
                clamp(bCol - shade + highlight, half(0.0), half(1.0)),
                aCol
            );
        }
    """

    fun createDockBackdropEffect(
        blurRadiusPx: Float,
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float
    ): ComposeRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val safeBlur = blurRadiusPx.coerceAtLeast(1f)
        val blurEffect = RenderEffect.createBlurEffect(
            safeBlur,
            safeBlur,
            Shader.TileMode.CLAMP
        )
        val saturationMatrix = ColorMatrix().apply { setSaturation(1.38f) }
        val saturatedBlur = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(saturationMatrix),
            blurEffect
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && widthPx > 2f && heightPx > 2f) {
            val refracted = createRefractedDockEffectApi33(
                saturatedBlur = saturatedBlur,
                widthPx = widthPx,
                heightPx = heightPx,
                cornerRadiusPx = cornerRadiusPx
            )
            if (refracted != null) {
                return refracted.asComposeRenderEffect()
            }
        }
        return saturatedBlur.asComposeRenderEffect()
    }

    private val hudCacheLock = Any()
    private var cachedHudBlurKey: Int = -1
    private var cachedHudSaturatedBlur: Any? = null
    private val hudSaturationColorFilter: ColorMatrixColorFilter by lazy {
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.38f) })
    }
    private val hudPillBackdropCache = android.util.LruCache<Long, ComposeRenderEffect>(32)

    fun quantizeHudLightPhase(seed: Float): Float {
        return (Math.round(seed.coerceIn(0f, 1f) * 5f) / 5f) * 0.45f
    }

    private fun packHudPillCacheKey(
        blurRadiusPx: Float,
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        lightPhase: Float
    ): Long {
        val blurKey = Math.round(blurRadiusPx.coerceAtLeast(1f) * 2f).coerceIn(1, 511).toLong()
        val wKey = Math.round(widthPx.coerceAtLeast(1f)).coerceIn(1, 16383).toLong()
        val hKey = Math.round(heightPx.coerceAtLeast(1f)).coerceIn(1, 4095).toLong()
        val rKey = Math.round(cornerRadiusPx.coerceAtLeast(0f)).coerceIn(0, 2047).toLong()
        val phaseKey = (Math.round(lightPhase * 12f).coerceIn(-511, 511) + 512).toLong()
        return blurKey or
            (wKey shl 9) or
            (hKey shl 23) or
            (rKey shl 35) or
            (phaseKey shl 46)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getOrCreateHudSaturatedBlurApi31(blurRadiusPx: Float): RenderEffect {
        val safeBlur = blurRadiusPx.coerceAtLeast(1f)
        val blurKey = Math.round(safeBlur * 2f).coerceIn(1, 511)
        synchronized(hudCacheLock) {
            val existing = cachedHudSaturatedBlur as? RenderEffect
            if (existing != null && cachedHudBlurKey == blurKey) {
                return existing
            }
            val quantizedBlur = blurKey / 2f
            val blurEffect = RenderEffect.createBlurEffect(
                quantizedBlur,
                quantizedBlur,
                Shader.TileMode.CLAMP
            )
            val saturatedBlur = RenderEffect.createColorFilterEffect(
                hudSaturationColorFilter,
                blurEffect
            )
            cachedHudBlurKey = blurKey
            cachedHudSaturatedBlur = saturatedBlur
            return saturatedBlur
        }
    }

    fun prewarmHudPillEffects(
        blurRadiusPx: Float,
        defaultWidthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        createHudPillBackdropEffect(
            blurRadiusPx = blurRadiusPx,
            widthPx = defaultWidthPx,
            heightPx = heightPx,
            cornerRadiusPx = cornerRadiusPx,
            lightPhase = 0.65f
        )
        createHudPillBackdropEffect(
            blurRadiusPx = blurRadiusPx,
            widthPx = defaultWidthPx,
            heightPx = heightPx,
            cornerRadiusPx = cornerRadiusPx,
            lightPhase = quantizeHudLightPhase(0.5f)
        )
    }

    fun createHudPillBackdropEffect(
        blurRadiusPx: Float,
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        lightPhase: Float = 0f
    ): ComposeRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val cacheKey = packHudPillCacheKey(
            blurRadiusPx = blurRadiusPx,
            widthPx = widthPx,
            heightPx = heightPx,
            cornerRadiusPx = cornerRadiusPx,
            lightPhase = lightPhase
        )
        synchronized(hudCacheLock) {
            hudPillBackdropCache.get(cacheKey)?.let { return it }
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && widthPx > 2f && heightPx > 2f) {
            (createRefractedHudPillEffectApi33(
                blurRadiusPx = blurRadiusPx,
                widthPx = widthPx,
                heightPx = heightPx,
                cornerRadiusPx = cornerRadiusPx,
                lightPhase = lightPhase
            ) ?: getOrCreateHudSaturatedBlurApi31(blurRadiusPx)).asComposeRenderEffect()
        } else {
            getOrCreateHudSaturatedBlurApi31(blurRadiusPx).asComposeRenderEffect()
        }

        synchronized(hudCacheLock) {
            hudPillBackdropCache.put(cacheKey, result)
        }
        return result
    }

    fun createLensBackdropBlurEffect(blurRadiusPx: Float): ComposeRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val safeBlur = blurRadiusPx.coerceAtLeast(0.5f)
        val blurEffect = RenderEffect.createBlurEffect(
            safeBlur,
            safeBlur,
            Shader.TileMode.CLAMP
        )
        val saturationMatrix = ColorMatrix().apply { setSaturation(1.48f) }
        return RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(saturationMatrix),
            blurEffect
        ).asComposeRenderEffect()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createLensRuntimeShaderOrNull(): RuntimeShader? {
        return runCatching { RuntimeShader(LENS_BACKGROUND_REFRACTION_AGSL) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createLensBackgroundMagnifierEffect(
        shader: RuntimeShader,
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        pointerCoordX: Float,
        pointerCoordY: Float,
        magnification: Float,
        lensProgress: Float,
        blurRadiusPx: Float
    ): ComposeRenderEffect? {
        if (widthPx <= 2f || heightPx <= 2f) return null
        return runCatching {
            val safeBlur = blurRadiusPx.coerceAtLeast(0.5f)
            val blurEffect = RenderEffect.createBlurEffect(
                safeBlur,
                safeBlur,
                Shader.TileMode.CLAMP
            )
            val saturationMatrix = ColorMatrix().apply { setSaturation(1.48f) }
            val saturatedBlur = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(saturationMatrix),
                blurEffect
            )
            shader.setFloatUniform("resolution", widthPx, heightPx)
            shader.setFloatUniform("cornerRadius", cornerRadiusPx)
            shader.setFloatUniform("pointerCoord", pointerCoordX, pointerCoordY)
            shader.setFloatUniform("magnification", magnification)
            shader.setFloatUniform("lensProgress", lensProgress)
            val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "childShader")
            RenderEffect.createChainEffect(shaderEffect, saturatedBlur).asComposeRenderEffect()
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createRefractedDockEffectApi33(
        saturatedBlur: RenderEffect,
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float
    ): RenderEffect? {
        return runCatching {
            val shader = RuntimeShader(DOCK_REFRACTION_AGSL)
            shader.setFloatUniform("resolution", widthPx, heightPx)
            shader.setFloatUniform("cornerRadius", cornerRadiusPx)
            val refractionEffect = RenderEffect.createRuntimeShaderEffect(shader, "childShader")
            RenderEffect.createChainEffect(refractionEffect, saturatedBlur)
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createRefractedHudPillEffectApi33(
        blurRadiusPx: Float,
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        lightPhase: Float
    ): RenderEffect? {
        return runCatching {
            val shader = RuntimeShader(HUD_PILL_REFRACTION_AGSL)
            shader.setFloatUniform("resolution", widthPx, heightPx)
            shader.setFloatUniform("cornerRadius", cornerRadiusPx)
            shader.setFloatUniform("lightPhase", lightPhase)
            shader.setFloatUniform("blurRadius", blurRadiusPx.coerceAtLeast(1f))
            RenderEffect.createRuntimeShaderEffect(shader, "childShader")
        }.getOrNull()
    }
}
