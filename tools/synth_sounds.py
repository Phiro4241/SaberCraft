#!/usr/bin/env python3
"""
Synthesizes all SaberCraft sounds from scratch (no samples, no recordings).

Every sound is built from sine waves, a little band-limited noise and envelopes, so the mod
ships only its own audio. Run from the repository root:

    python3 tools/synth_sounds.py

Needs numpy and ffmpeg with libvorbis. Writes mono 44.1 kHz Ogg Vorbis files into
src/client/resources/assets/sabercraft/sounds/saber/ (default profile) and .../saber/dark/
(the deeper, rougher profile of the dark hilt), and the combat sounds of Phase 6 into
.../sounds/combat/. `--combat` writes only the combat sounds. Minecraft only attenuates mono
sounds by distance, so all files are mono.
"""
import os
import subprocess
import sys
import tempfile
import wave
from collections import namedtuple

import numpy as np

RATE = 44100
OUT = os.path.join("src", "client", "resources", "assets", "sabercraft", "sounds", "saber")

# Base pitch of the hum. All loop frequencies are multiples of 1 / HUM_SECONDS, so the loop
# repeats without a click.
HUM_SECONDS = 3.0
HUM_BASE = 92.0
# (harmonic, level) pairs. Odd harmonics give the buzzing, slightly hollow tone.
HUM_PARTIALS = [(1, 1.0), (2, 0.42), (3, 0.55), (4, 0.12), (5, 0.22), (7, 0.08), (9, 0.04)]

# A sound profile: base pitch, harmonic stack, distortion drive of the hum and "growl", a fast
# rough flutter of the volume. The default voice reproduces the Phase 4 sounds exactly.
Voice = namedtuple("Voice", "base partials drive growl")
DEFAULT_VOICE = Voice(HUM_BASE, HUM_PARTIALS, 2.4, 0.0)
# Dark hilt: lower (74 Hz is on the 1/3 Hz loop grid), stronger odd harmonics, more distortion
# and a growl at 11 Hz (also on the grid), so the hum sounds heavier and more menacing.
DARK_VOICE = Voice(74.0, [(1, 1.0), (2, 0.3), (3, 0.72), (4, 0.1), (5, 0.38), (7, 0.16), (9, 0.08), (11, 0.04)], 3.4, 0.22)


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


def hum_tone(t, base, wobble=None, partials=HUM_PARTIALS):
    """The hum's harmonic stack. `base` may be an array for pitch sweeps."""
    base = np.broadcast_to(base, t.shape)
    phase = 2 * np.pi * np.cumsum(base) / RATE
    if wobble is not None:
        phase = phase + wobble
    out = np.zeros_like(t)
    for harmonic, level in partials:
        out += level * np.sin(harmonic * phase + harmonic * 0.7)
    return out


def soft_clip(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)


def hum(rng, voice=DEFAULT_VOICE):
    t = t_axis(HUM_SECONDS)
    # Frequencies on the 1/3 Hz grid, so the loop has no seam.
    base = voice.base
    tone = np.zeros_like(t)
    for harmonic, level in voice.partials:
        tone += level * np.sin(2 * np.pi * base * harmonic * t + harmonic * 0.7)
    # A second, slightly detuned stack (+1/3 Hz) makes the tone beat slowly, like a real hum.
    for harmonic, level in voice.partials[:3]:
        tone += 0.35 * level * np.sin(2 * np.pi * (base * harmonic + 1.0 / HUM_SECONDS) * t)
    buzz = soft_clip(tone / 2.2, voice.drive)
    # Slow, irregular breathing of the volume (rates on the loop grid as well).
    am = 1.0 + 0.06 * np.sin(2 * np.pi * (2 / HUM_SECONDS) * t) + 0.04 * np.sin(2 * np.pi * (7 / HUM_SECONDS) * t + 1.1)
    if voice.growl:
        am = am * (1.0 + voice.growl * np.sin(2 * np.pi * (33 / HUM_SECONDS) * t) * (0.6 + 0.4 * np.sin(2 * np.pi * (4 / HUM_SECONDS) * t)))
    air = 0.05 * periodic_noise(HUM_SECONDS, 300, 2400, rng)
    return normalize((buzz * am + air), 0.5)


def ignite(rng, voice=DEFAULT_VOICE, seconds=1.1):
    t = t_axis(seconds)
    # Pitch rises from a low growl to the hum pitch, overshoots a little and settles.
    rise = 1 - np.exp(-t / 0.07)
    pitch = voice.base * (0.35 + 0.65 * rise) + voice.base * 0.08 * np.exp(-((t - 0.18) / 0.08) ** 2)
    tone = soft_clip(hum_tone(t, pitch, partials=voice.partials) / 2.0, voice.drive + 0.2)

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


def retract(rng, voice=DEFAULT_VOICE, seconds=0.85):
    t = t_axis(seconds)
    # Pitch drops quickly, like the energy draining into the hilt.
    fall = np.exp(-t / 0.28)
    pitch = voice.base * (0.25 + 0.85 * fall) + voice.base * 0.06 * np.exp(-((t - 0.04) / 0.05) ** 2)
    tone = soft_clip(hum_tone(t, pitch, partials=voice.partials) / 2.0, voice.drive - 0.2)
    env = np.clip(t / 0.02, 0, 1) * np.clip((seconds - t) / 0.5, 0, 1) ** 1.6
    swell = 1.0 + 0.4 * np.exp(-((t - 0.08) / 0.07) ** 2)
    hiss = bandpass_noise(seconds, 400, 4000, rng) * np.exp(-t / 0.12) * 0.3
    # Short "click" when the blade is gone.
    click_t = seconds - 0.23
    click = np.exp(-np.clip(t - click_t, 0, None) / 0.008) * (t >= click_t) * np.sin(2 * np.pi * 1800 * t) * 0.25
    out = tone * env * swell + hiss * env + click
    return normalize(out, 0.45)


def swing(rng, seconds, peak, bend, brightness, voice=DEFAULT_VOICE):
    """A pass of the blade: the hum swells and bends in pitch like a Doppler shift."""
    t = t_axis(seconds)
    pos = (t - peak) / (seconds * 0.22)
    env = np.exp(-pos ** 2) ** 0.9
    pitch = voice.base * (1.0 + bend * np.tanh(-pos * 1.3) * 0.5 + bend * 0.5 * np.exp(-pos ** 2))
    tone = soft_clip(hum_tone(t, pitch * 1.25, partials=voice.partials) / 1.6, voice.drive + 0.6)
    whoosh = bandpass_noise(seconds, 250, brightness, rng)
    out = tone * env + 0.35 * whoosh * env ** 1.5
    fade = np.clip(t / 0.01, 0, 1) * np.clip((seconds - t) / 0.05, 0, 1)
    return normalize(out * fade, 0.45)


def spin(rng, seconds=0.9):
    """A full turn of the blade: two Doppler swells close together on a long whoosh."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for peak, level in ((0.28, 0.8), (0.58, 1.0)):
        pos = (t - peak) / 0.11
        env = np.exp(-pos ** 2)
        pitch = HUM_BASE * (1.0 + 0.32 * np.tanh(-pos * 1.3) * 0.5 + 0.16 * env)
        out += level * soft_clip(hum_tone(t, pitch * 1.3) / 1.6, 3.0) * env
    whoosh_env = np.clip(t / 0.12, 0, 1) * np.clip((seconds - t) / 0.3, 0, 1)
    out += 0.4 * bandpass_noise(seconds, 300, 2800, rng) * whoosh_env
    return normalize(out * np.clip((seconds - t) / 0.05, 0, 1), 0.45)


def hit(rng, seconds=0.32):
    """The blade cuts into something: a sharp sizzle with a short burnt crackle."""
    t = t_axis(seconds)
    sizzle = bandpass_noise(seconds, 1500, 9000, rng) * np.exp(-t / 0.06)
    crackle = (rng.random(t.shape) < 0.04 * np.exp(-t / 0.12)) * rng.uniform(-1, 1, t.shape)
    thump = np.sin(2 * np.pi * 120 * t * (1 - 0.4 * t / seconds)) * np.exp(-t / 0.04)
    buzz = soft_clip(hum_tone(t, HUM_BASE * 1.6) / 1.5, 3.0) * np.exp(-t / 0.08)
    out = 0.7 * sizzle + 0.4 * crackle + 0.5 * thump + 0.45 * buzz
    return normalize(out * np.clip(t / 0.002, 0, 1), 0.5)


def scorch(rng, seconds=0.5):
    """The blade scrapes a wall: crackling sparks and a burning hiss."""
    t = t_axis(seconds)
    hiss = bandpass_noise(seconds, 2000, 8000, rng) * np.exp(-t / 0.16)
    crackle = (rng.random(t.shape) < 0.06 * np.exp(-t / 0.2)) * rng.uniform(-1, 1, t.shape)
    out = 0.5 * hiss + 0.6 * crackle
    return normalize(out * np.clip(t / 0.003, 0, 1) * np.clip((seconds - t) / 0.1, 0, 1), 0.4)


def guard(rng, seconds=0.45):
    """Raising the stance: a short, rising swell of the hum."""
    t = t_axis(seconds)
    pitch = HUM_BASE * (1.0 + 0.25 * (1 - np.exp(-t / 0.08)))
    env = np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.18)
    tone = soft_clip(hum_tone(t, pitch * 1.2) / 1.6, 3.0)
    air = bandpass_noise(seconds, 300, 2000, rng) * env
    return normalize(tone * env + 0.3 * air, 0.4)


def clash(rng, seconds=0.6, ring=1.0):
    """Two blades meet: a crack of energy and a metallic, buzzing ring."""
    t = t_axis(seconds)
    crack = bandpass_noise(seconds, 800, 10000, rng) * np.exp(-t / 0.025)
    buzz = soft_clip(hum_tone(t, HUM_BASE * 2.1) / 1.2, 4.0) * np.exp(-t / 0.12)
    tone = np.zeros_like(t)
    for freq, level in ((1210, 0.5), (1873, 0.35), (2667, 0.22)):
        tone += level * np.sin(2 * np.pi * freq * t) * np.exp(-t / (0.09 * ring))
    crackle = (rng.random(t.shape) < 0.03 * np.exp(-t / 0.1)) * rng.uniform(-1, 1, t.shape)
    out = 0.8 * crack + 0.6 * buzz + 0.5 * ring * tone + 0.3 * crackle
    return normalize(out * np.clip(t / 0.001, 0, 1) * np.clip((seconds - t) / 0.1, 0, 1), 0.55)


def push(rng, seconds=0.8):
    """The force push: a deep thump and a rush of air moving away."""
    t = t_axis(seconds)
    thump = np.sin(2 * np.pi * (70 - 30 * t) * t) * np.exp(-t / 0.12)
    rush_env = np.clip(t / 0.04, 0, 1) * np.exp(-t / 0.25)
    rush = bandpass_noise(seconds, 150, 1800, rng) * rush_env
    low = bandpass_noise(seconds, 30, 160, rng) * np.exp(-t / 0.2)
    out = 0.9 * thump + 0.6 * rush + 0.5 * low
    return normalize(out * np.clip((seconds - t) / 0.2, 0, 1), 0.55)


def dash(rng, seconds=0.4):
    """A quick lunge: a short rush of air."""
    t = t_axis(seconds)
    env = np.clip(t / 0.03, 0, 1) * np.exp(-t / 0.1)
    rush = bandpass_noise(seconds, 400, 3500, rng) * env
    return normalize(rush * np.clip((seconds - t) / 0.1, 0, 1), 0.4)


def force_grip(rng, seconds=1.6):
    """The force grip: a low, tense rumble that swells, with a faint rising whine."""
    t = t_axis(seconds)
    env = np.clip(t / 0.25, 0, 1) * np.clip((seconds - t) / 0.4, 0, 1)
    rumble = bandpass_noise(seconds, 25, 140, rng) * (0.7 + 0.3 * np.sin(2 * np.pi * 6 * t))
    whine = np.sin(2 * np.pi * (220 * t + 60 * t * t)) * 0.25 + np.sin(2 * np.pi * (331 * t + 90 * t * t)) * 0.12
    out = (0.9 * rumble + whine * np.clip(t / 0.8, 0, 1)) * env
    return normalize(soft_clip(out, 1.5), 0.45)


def force_pull(rng, seconds=0.7):
    """The force pull: a rush of air coming towards the listener, ending in a soft thump."""
    t = t_axis(seconds)
    swell = np.clip(t / 0.45, 0, 1) ** 2 * np.clip((seconds - t) / 0.08, 0, 1)
    rush = bandpass_noise(seconds, 200, 2600, rng) * swell
    thump = np.sin(2 * np.pi * 85 * t) * np.exp(-np.clip(t - 0.45, 0, None) / 0.08) * (t > 0.45)
    return normalize(0.8 * rush + 0.6 * thump, 0.5)


def force_jump(rng, seconds=0.6):
    """The force jump: a push off the ground and air rushing past upwards."""
    t = t_axis(seconds)
    thump = np.sin(2 * np.pi * (90 - 40 * t) * t) * np.exp(-t / 0.07)
    rush = bandpass_noise(seconds, 300, 3000, rng) * np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.22)
    return normalize((0.8 * thump + 0.6 * rush) * np.clip((seconds - t) / 0.15, 0, 1), 0.5)


def normalize(x, peak):
    """Scales to the given peak. Peaks stay well below 1.0, because in the game the hum, an
    ignition and a swing (or two sabers) can play at the same time and would clip otherwise."""
    return x / np.max(np.abs(x)) * peak


def write_ogg(name, samples, out=OUT):
    os.makedirs(out, exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")

    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(pcm.tobytes())

        target = os.path.join(out, name + ".ogg")
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", target],
            check=True,
        )
        print("wrote", target)


def combat():
    """The Phase 6 combat sounds, with their own random stream so the sounds above never change."""
    rng = np.random.default_rng(1977)
    out = os.path.join(os.path.dirname(OUT), "combat")
    write_ogg("spin", spin(rng), out)
    write_ogg("hit1", hit(rng), out)
    write_ogg("hit2", hit(rng, seconds=0.36), out)
    write_ogg("scorch", scorch(rng), out)
    write_ogg("guard", guard(rng), out)
    write_ogg("clash1", clash(rng), out)
    write_ogg("clash2", clash(rng, seconds=0.55, ring=0.8), out)
    write_ogg("parry", clash(rng, seconds=1.0, ring=2.2), out)
    write_ogg("push", push(rng), out)
    write_ogg("dash", dash(rng), out)
    force()


def force():
    """The force powers of Phase 7, with their own random stream."""
    rng = np.random.default_rng(7007)
    out = os.path.join(os.path.dirname(OUT), "combat")
    write_ogg("force_grip", force_grip(rng), out)
    write_ogg("force_pull", force_pull(rng), out)
    write_ogg("force_jump", force_jump(rng), out)


def main():
    if "--combat" in sys.argv:
        combat()
        return

    if "--force" in sys.argv:
        force()
        return

    rng = np.random.default_rng(4241)
    write_ogg("hum", hum(rng))
    write_ogg("ignite", ignite(rng))
    write_ogg("retract", retract(rng))
    # Several swing variants with different speed, bend and brightness, so swings do not repeat.
    variants = [(0.55, 0.22, 0.30, 2600), (0.7, 0.30, 0.38, 2200), (0.45, 0.18, 0.26, 3200), (0.62, 0.24, 0.45, 1800)]
    for index, (seconds, peak, bend, brightness) in enumerate(variants, start=1):
        write_ogg("swing%d" % index, swing(rng, seconds, peak, bend, brightness))

    # The dark profile has its own random stream, so the default sounds above stay unchanged.
    rng = np.random.default_rng(6661)
    dark = os.path.join(OUT, "dark")
    write_ogg("hum", hum(rng, DARK_VOICE), dark)
    write_ogg("ignite", ignite(rng, DARK_VOICE, seconds=1.3), dark)
    write_ogg("retract", retract(rng, DARK_VOICE, seconds=0.95), dark)
    for index, (seconds, peak, bend, brightness) in enumerate(variants, start=1):
        write_ogg("swing%d" % index, swing(rng, seconds * 1.1, peak * 1.1, bend * 0.85, brightness * 0.8, DARK_VOICE), dark)

    combat()


if __name__ == "__main__":
    main()
