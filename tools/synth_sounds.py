#!/usr/bin/env python3
"""
Synthesizes all SaberCraft sounds from scratch (no samples, no recordings).

Every sound is built from sine waves, a little band-limited noise and envelopes, so the mod
ships only its own audio. Run from the repository root:

    python3 tools/synth_sounds.py

Needs numpy and ffmpeg with libvorbis. Writes mono 44.1 kHz Ogg Vorbis files into
src/client/resources/assets/sabercraft/sounds/saber/. Minecraft only attenuates mono sounds
by distance, so all files are mono.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join("src", "client", "resources", "assets", "sabercraft", "sounds", "saber")

# Base pitch of the hum. All loop frequencies are multiples of 1 / HUM_SECONDS, so the loop
# repeats without a click.
HUM_SECONDS = 3.0
HUM_BASE = 92.0
# (harmonic, level) pairs. Odd harmonics give the buzzing, slightly hollow tone.
HUM_PARTIALS = [(1, 1.0), (2, 0.42), (3, 0.55), (4, 0.12), (5, 0.22), (7, 0.08), (9, 0.04)]


def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def periodic_noise(seconds, low, high, rng):
    """Band-limited noise that loops seamlessly, built from random phases in the frequency domain."""
    n = int(seconds * RATE)
    spectrum = np.zeros(n // 2 + 1, dtype=complex)
    freqs = np.fft.rfftfreq(n, 1.0 / RATE)
    band = (freqs >= low) & (freqs <= high)
    spectrum[band] = np.exp(2j * np.pi * rng.random(band.sum()))
    noise = np.fft.irfft(spectrum, n)
    return noise / np.max(np.abs(noise))


def bandpass_noise(seconds, low, high, rng):
    return periodic_noise(seconds, low, high, rng)


def hum_tone(t, base, wobble=None):
    """The hum's harmonic stack. `base` may be an array for pitch sweeps."""
    base = np.broadcast_to(base, t.shape)
    phase = 2 * np.pi * np.cumsum(base) / RATE
    if wobble is not None:
        phase = phase + wobble
    out = np.zeros_like(t)
    for harmonic, level in HUM_PARTIALS:
        out += level * np.sin(harmonic * phase + harmonic * 0.7)
    return out


def soft_clip(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)


def hum(rng):
    t = t_axis(HUM_SECONDS)
    # Frequencies on the 1/3 Hz grid, so the loop has no seam.
    base = HUM_BASE
    tone = np.zeros_like(t)
    for harmonic, level in HUM_PARTIALS:
        tone += level * np.sin(2 * np.pi * base * harmonic * t + harmonic * 0.7)
    # A second, slightly detuned stack (+1/3 Hz) makes the tone beat slowly, like a real hum.
    for harmonic, level in HUM_PARTIALS[:3]:
        tone += 0.35 * level * np.sin(2 * np.pi * (base * harmonic + 1.0 / HUM_SECONDS) * t)
    buzz = soft_clip(tone / 2.2, 2.4)
    # Slow, irregular breathing of the volume (rates on the loop grid as well).
    am = 1.0 + 0.06 * np.sin(2 * np.pi * (2 / HUM_SECONDS) * t) + 0.04 * np.sin(2 * np.pi * (7 / HUM_SECONDS) * t + 1.1)
    air = 0.05 * periodic_noise(HUM_SECONDS, 300, 2400, rng)
    return normalize((buzz * am + air), 0.5)


def ignite(rng):
    seconds = 1.1
    t = t_axis(seconds)
    # Pitch rises from a low growl to the hum pitch, overshoots a little and settles.
    rise = 1 - np.exp(-t / 0.07)
    pitch = HUM_BASE * (0.35 + 0.65 * rise) + HUM_BASE * 0.08 * np.exp(-((t - 0.18) / 0.08) ** 2)
    tone = soft_clip(hum_tone(t, pitch) / 2.0, 2.6)

    # The "snap": a short burst of bright noise and a crackle at the very start.
    snap_env = np.exp(-t / 0.035)
    snap = bandpass_noise(seconds, 900, 9000, rng) * snap_env
    crackle = (rng.random(t.shape) < 0.02 * np.exp(-t / 0.15)) * rng.uniform(-1, 1, t.shape)
    # A rising whoosh of filtered noise, the energy rushing up the blade.
    whoosh_env = np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.22)
    whoosh = bandpass_noise(seconds, 200, 3000, rng) * whoosh_env

    attack = np.clip(t / 0.012, 0, 1)
    swell = 1.0 + 0.6 * np.exp(-((t - 0.16) / 0.12) ** 2)
    # Fades out while the looping hum fades in in the game, so the two cross-fade.
    tail = np.clip((seconds - t) / 0.45, 0, 1)
    out = tone * attack * swell * tail + 0.55 * snap + 0.35 * crackle + 0.45 * whoosh * tail
    return normalize(out, 0.5)


def retract(rng):
    seconds = 0.85
    t = t_axis(seconds)
    # Pitch drops quickly, like the energy draining into the hilt.
    fall = np.exp(-t / 0.28)
    pitch = HUM_BASE * (0.25 + 0.85 * fall) + HUM_BASE * 0.06 * np.exp(-((t - 0.04) / 0.05) ** 2)
    tone = soft_clip(hum_tone(t, pitch) / 2.0, 2.2)
    env = np.clip(t / 0.02, 0, 1) * np.clip((seconds - t) / 0.5, 0, 1) ** 1.6
    swell = 1.0 + 0.4 * np.exp(-((t - 0.08) / 0.07) ** 2)
    hiss = bandpass_noise(seconds, 400, 4000, rng) * np.exp(-t / 0.12) * 0.3
    # Short "click" when the blade is gone.
    click_t = 0.62
    click = np.exp(-np.clip(t - click_t, 0, None) / 0.008) * (t >= click_t) * np.sin(2 * np.pi * 1800 * t) * 0.25
    out = tone * env * swell + hiss * env + click
    return normalize(out, 0.45)


def swing(rng, seconds, peak, bend, brightness):
    """A pass of the blade: the hum swells and bends in pitch like a Doppler shift."""
    t = t_axis(seconds)
    pos = (t - peak) / (seconds * 0.22)
    env = np.exp(-pos ** 2) ** 0.9
    pitch = HUM_BASE * (1.0 + bend * np.tanh(-pos * 1.3) * 0.5 + bend * 0.5 * np.exp(-pos ** 2))
    tone = soft_clip(hum_tone(t, pitch * 1.25) / 1.6, 3.0)
    whoosh = bandpass_noise(seconds, 250, brightness, rng)
    out = tone * env + 0.35 * whoosh * env ** 1.5
    fade = np.clip(t / 0.01, 0, 1) * np.clip((seconds - t) / 0.05, 0, 1)
    return normalize(out * fade, 0.45)


def normalize(x, peak):
    """Scales to the given peak. Peaks stay well below 1.0, because in the game the hum, an
    ignition and a swing (or two sabers) can play at the same time and would clip otherwise."""
    return x / np.max(np.abs(x)) * peak


def write_ogg(name, samples):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")

    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(pcm.tobytes())

        target = os.path.join(OUT, name + ".ogg")
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", target],
            check=True,
        )
        print("wrote", target)


def main():
    rng = np.random.default_rng(4241)
    write_ogg("hum", hum(rng))
    write_ogg("ignite", ignite(rng))
    write_ogg("retract", retract(rng))
    # Several swing variants with different speed, bend and brightness, so swings do not repeat.
    variants = [(0.55, 0.22, 0.30, 2600), (0.7, 0.30, 0.38, 2200), (0.45, 0.18, 0.26, 3200), (0.62, 0.24, 0.45, 1800)]
    for index, (seconds, peak, bend, brightness) in enumerate(variants, start=1):
        write_ogg("swing%d" % index, swing(rng, seconds, peak, bend, brightness))


if __name__ == "__main__":
    main()
