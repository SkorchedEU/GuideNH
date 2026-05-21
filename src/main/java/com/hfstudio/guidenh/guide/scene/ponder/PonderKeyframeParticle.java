package com.hfstudio.guidenh.guide.scene.ponder;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * A particle effect entry triggered when a Ponder keyframe becomes active during forward playback.
 */
public class PonderKeyframeParticle {

    public static final int MAX_COUNT = 256;
    public static final int MAX_LIFETIME_TICKS = 200;
    public static final float MAX_POWER = 12f;
    public static final float MAX_SIZE = 4f;

    @Nullable
    private String preset;
    @Nullable
    private String name;
    @Nullable
    private String particle;
    @Nullable
    private String kind;
    @Nullable
    private Integer count;
    @Nullable
    private Integer time;
    @Nullable
    private Integer lifetime;
    @Nullable
    private Float power;
    private float x;
    private float y;
    private float z;
    @Nullable
    private Float vx;
    @Nullable
    private Float vy;
    @Nullable
    private Float vz;
    @Nullable
    private Float motionX;
    @Nullable
    private Float motionY;
    @Nullable
    private Float motionZ;
    @Nullable
    private Float size;

    @Nullable
    public String getPreset() {
        return preset;
    }

    public boolean isExplosionPreset() {
        return "explosion".equals(normalize(preset));
    }

    @Nullable
    public String getParticleName() {
        String normalized = normalize(name);
        if (normalized != null) {
            return normalized;
        }
        normalized = normalize(particle);
        if (normalized != null) {
            return normalized;
        }
        return normalize(kind);
    }

    public int getCount(int defaultValue) {
        return clampInt(count != null ? count : defaultValue, 1, MAX_COUNT);
    }

    public int getLifetimeTicks(int defaultValue) {
        if (lifetime != null) {
            return clampInt(lifetime, 1, MAX_LIFETIME_TICKS);
        }
        if (time != null) {
            return clampInt(time, 1, MAX_LIFETIME_TICKS);
        }
        return clampInt(defaultValue, 1, MAX_LIFETIME_TICKS);
    }

    public float getPower(float defaultValue) {
        return clampFloat(power != null ? power : defaultValue, 0.1f, MAX_POWER);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public float getVelocityX() {
        return motionX != null ? motionX : vx != null ? vx : 0f;
    }

    public float getVelocityY() {
        return motionY != null ? motionY : vy != null ? vy : 0f;
    }

    public float getVelocityZ() {
        return motionZ != null ? motionZ : vz != null ? vz : 0f;
    }

    public float getSize(float defaultValue) {
        return clampFloat(size != null ? size : defaultValue, 0.01f, MAX_SIZE);
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
