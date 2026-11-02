#!/usr/bin/env python3
"""
Synthesizes all SaberCraft sounds from scratch (no samples, no recordings).

Every sound is built from sine waves, a little band-limited noise and envelopes, so the mod
ships only its own audio. Run from the repository root:

    python3 tools/synth_sounds.py

Needs numpy and ffmpeg with libvorbis. Writes mono 44.1 kHz Ogg Vorbis files into
src/client/resources/assets/sabercraft/sounds/saber/ (default profile) and .../saber/dark/
(the deeper, rougher profile of the dark hilt), and the combat sounds of Phase 6 into
.../sounds/combat/. `--combat` writes only the combat sounds, `--blaster` only the blaster sounds of Phase 10
(.../sounds/blaster/), `--ambient` only the planet ambiences of Phase 10 (.../sounds/ambient/),
`--phase11-blaster` the new blaster sounds of Phase 11, `--droids` the droid voices of Phase 11
(.../sounds/droid/: beeps, whistles, servos and wordless robot murmurs, no speech), `--atmosphere` the draw and holster sounds and the menu sweep of Phase 11 G, `--ships` the
starship engine, laser cannon, crash and S-foil sounds of Phase 11 (.../sounds/ship/), `--imperial-ships` the TIE
Fighter and TIE Interceptor engines and cannons, hull hits and the explosion of Phase 12, `--phase13-ships` the calmer engines and
cannons of every ship, the Falcon and the Star Destroyer, impacts, landings and the energy sounds of Phase 13 (it rewrites the
engine and laser files of the two options before), `--phase14` the Falcon's own drive, thrust layers, boost and brake,
hyperspace, the big explosion, alarm, ramp, turbolift and interior sounds of Phase 14 (it rewrites falcon_engine). Minecraft only attenuates mono
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


def blaster_shot(rng, seconds, start, end, ring, body, noise, decay):
    """A blaster shot (Phase 10): a fast falling chirp, like a struck steel wire, with a short
    burst of noise at the start and a metallic ring. start/end: chirp frequencies in Hz, ring:
    level of the ringing partials, body: level of a low thump, noise: level of the crack, decay:
    how fast it fades (larger = shorter)."""
    t = t_axis(seconds)
    # Exponential frequency fall; the phase is the integral of the frequency.
    k = np.log(end / start) / seconds
    phase = 2 * np.pi * start * (np.exp(k * t) - 1) / k
    chirp = np.sin(phase) + 0.35 * np.sin(2.01 * phase) + ring * 0.25 * np.sin(3.3 * phase)
    env = np.exp(-decay * t) * (1 - np.exp(-t * 900))
    crack = bandpass_noise(seconds, 1800, 9000, rng) * np.exp(-t * 90) * noise
    thump = np.sin(2 * np.pi * 90 * t) * np.exp(-t * 30) * body
    return normalize(soft_clip(chirp * env + crack + thump, 1.6), 0.8)


def blaster_impact(rng, seconds=0.35):
    t = t_axis(seconds)
    sizzle = bandpass_noise(seconds, 900, 7000, rng) * np.exp(-t * 14)
    pop = np.sin(2 * np.pi * 180 * t * (1 - t)) * np.exp(-t * 40)
    return normalize(sizzle * 0.7 + pop, 0.6)


def blaster_overheat(rng, seconds=1.2):
    t = t_axis(seconds)
    hiss = bandpass_noise(seconds, 2500, 9000, rng) * np.exp(-t * 2.5) * (1 - np.exp(-t * 60))
    whine = np.sin(2 * np.pi * (900 - 500 * t) * t) * np.exp(-t * 4) * 0.4
    return normalize(hiss + whine, 0.6)


def blasters():
    """The blaster sounds of Phase 10, with their own random stream. Each type sounds different."""
    rng = np.random.default_rng(1138)
    out = os.path.join(os.path.dirname(OUT), "blaster")
    write_ogg("e11", blaster_shot(rng, 0.42, 2400, 180, 0.6, 0.3, 0.5, 9), out)
    write_ogg("dl44", blaster_shot(rng, 0.6, 1700, 110, 0.9, 0.8, 0.7, 6), out)
    write_ogg("a280", blaster_shot(rng, 0.5, 3200, 240, 0.4, 0.25, 0.4, 8), out)
    write_ogg("repeater", blaster_shot(rng, 0.32, 1500, 160, 0.5, 0.9, 0.8, 12), out)
    write_ogg("rebel", blaster_shot(rng, 0.4, 2800, 300, 0.7, 0.2, 0.45, 10), out)
    write_ogg("ezra", blaster_shot(rng, 0.38, 1400, 600, 1.2, 0.1, 0.3, 11), out)
    write_ogg("impact", blaster_impact(rng), out)
    write_ogg("overheat", blaster_overheat(rng), out)


def bowcaster_shot(rng, seconds=0.75):
    """The bowcaster (Phase 11): a heavy twang of the bow arms, a deep thump and a short crack."""
    t = t_axis(seconds)
    k = np.log(70 / 900) / seconds
    phase = 2 * np.pi * 900 * (np.exp(k * t) - 1) / k
    twang = (np.sin(phase) + 0.5 * np.sin(1.5 * phase) + 0.3 * np.sin(2.7 * phase)) * np.exp(-t * 5)
    thump = np.sin(2 * np.pi * 62 * t) * np.exp(-t * 14) * 1.1
    crack = bandpass_noise(seconds, 1200, 7000, rng) * np.exp(-t * 60) * 0.8
    return normalize(soft_clip(twang * (1 - np.exp(-t * 700)) + thump + crack, 1.8), 0.85)


def dry_click(rng, seconds=0.18):
    """No ammunition (Phase 11): a dry mechanical double click and a falling beep."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for start in (0.0, 0.05):
        n = int(start * RATE)
        tt = t[: len(t) - n]
        out[n:] += bandpass_noise(seconds, 2500, 9000, rng)[: len(tt)] * np.exp(-tt * 400)
    beep = np.sin(2 * np.pi * (1300 - 2500 * t) * t) * np.exp(-t * 18) * (t > 0.09) * 0.25
    return normalize(out + beep, 0.5)


def vent(rng, seconds=1.4):
    """Cooling vents opening (Phase 11): a rising hiss with a few metallic ticks."""
    t = t_axis(seconds)
    hiss = bandpass_noise(seconds, 3000, 11000, rng) * np.minimum(1, t * 8) * np.exp(-t * 1.8)
    ticks = np.zeros_like(t)
    for start in rng.uniform(0.2, 1.1, 5):
        n = int(start * RATE)
        m = min(len(t) - n, int(0.02 * RATE))
        ticks[n:n + m] += np.sin(2 * np.pi * 4200 * t[:m]) * np.exp(-t[:m] * 300) * 0.3
    return normalize(hiss + ticks, 0.5)


def menu_select(rng, seconds=0.35):
    """Picking a blaster in the menu (Phase 11): a short power-up chirp."""
    t = t_axis(seconds)
    k = np.log(1800 / 400) / seconds
    phase = 2 * np.pi * 400 * (np.exp(k * t) - 1) / k
    tone = (np.sin(phase) + 0.3 * np.sin(2 * phase)) * np.exp(-t * 7) * (1 - np.exp(-t * 300))
    return normalize(tone, 0.45)


def phase11_blasters():
    """The new blaster sounds of Phase 11, with their own random stream (Phase 10 sounds stay as they are)."""
    rng = np.random.default_rng(1111)
    out = os.path.join(os.path.dirname(OUT), "blaster")
    write_ogg("dc15a", blaster_shot(rng, 0.5, 2900, 220, 0.5, 0.35, 0.45, 8), out)
    write_ogg("se14c", blaster_shot(rng, 0.34, 3400, 420, 0.5, 0.15, 0.35, 13), out)
    write_ogg("bowcaster", bowcaster_shot(rng), out)
    write_ogg("empty", dry_click(rng), out)
    write_ogg("vent", vent(rng), out)
    write_ogg("select", menu_select(rng), out)


def finisher(rng, seconds=1.6):
    """The final blow (Phase 11): a deep, swelling hum, a bright energy crack and a long, ringing
    decay with a soft rush of air, like the last strike of a duel in slow motion."""
    t = t_axis(seconds)
    swell = soft_clip(hum_tone(t, HUM_BASE * (0.8 + 0.2 * np.exp(-t / 0.4))) / 1.4, 3.0) * np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.7)
    crack = bandpass_noise(seconds, 600, 9000, rng) * np.exp(-t / 0.03)
    boom = np.sin(2 * np.pi * 55 * t * (1 - 0.3 * t / seconds)) * np.exp(-t / 0.35)
    ring = np.zeros_like(t)
    for freq, level in ((880, 0.35), (1318, 0.25), (1760, 0.15)):
        ring += level * np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.5)
    air = bandpass_noise(seconds, 200, 1500, rng) * np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 2
    out = 0.6 * swell + 0.7 * crack + 0.8 * boom + 0.4 * ring + 0.25 * air
    return normalize(out * np.clip((seconds - t) / 0.3, 0, 1), 0.6)


def phase11_combat():
    """The combat sounds of Phase 11, with their own random stream."""
    rng = np.random.default_rng(1112)
    write_ogg("finisher", finisher(rng))


# Droid voices (Phase 11) --------------------------------------------------------------------------
# Every droid "talks" in its own synthesized way: astromechs in quick sine beeps and whistles that
# glide in pitch, BB-8 rounder and softer, Chopper in raspy grumbles, the mouse droid in squeaks,
# the probe droid in radio static over a drone, and the humanoid droids (C-3PO, B1, K-2SO) in a
# wordless, vowel-like murmur over a servo whirr. Nothing is sampled and nothing says words.
DROID_OUT = os.path.join("src", "client", "resources", "assets", "sabercraft", "sounds", "droid")


def glide_tone(f0, f1, seconds, shape="sine", vibrato=0.0, vib_rate=18.0, curve=1.0):
    t = t_axis(seconds)
    x = (t / max(seconds, 1e-6)) ** curve
    freq = f0 + (f1 - f0) * x
    freq = freq * (1.0 + vibrato * np.sin(2 * np.pi * vib_rate * t))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    if shape == "triangle":
        tone = 2.0 / np.pi * np.arcsin(np.sin(phase))
    elif shape == "square":
        tone = np.tanh(4.0 * np.sin(phase))
    else:
        tone = np.sin(phase)
    attack = np.clip(t / 0.006, 0, 1)
    release = np.clip((seconds - t) / 0.012, 0, 1)
    return tone * attack * release


def sequence(parts, gap=0.02):
    """Joins sounds with short pauses."""
    out = []
    pause = np.zeros(int(gap * RATE))
    for part in parts:
        out.append(part)
        out.append(pause)
    return np.concatenate(out)


def astromech(rng, count, low, high, shape="sine", whistle=False, soft=1.0):
    parts = []
    for i in range(count):
        seconds = rng.uniform(0.05, 0.13)
        f0 = rng.uniform(low, high)
        f1 = f0 * rng.choice([0.6, 0.8, 1.0, 1.25, 1.6])
        parts.append(glide_tone(f0, f1, seconds, shape, vibrato=rng.choice([0.0, 0.0, 0.03]), vib_rate=rng.uniform(14, 30)) * rng.uniform(0.6, 1.0))
    if whistle:
        f0 = rng.uniform(low, high)
        parts.append(glide_tone(f0, f0 * 1.9, 0.35, shape, vibrato=0.02, vib_rate=9, curve=2.0))
    x = sequence(parts, gap=rng.uniform(0.01, 0.04))
    if soft < 1.0:
        # Rounder: a gentle low-pass by averaging.
        k = int(8 / soft)
        x = np.convolve(x, np.ones(k) / k, mode="same")
    return x


def grumble(rng, seconds, base):
    t = t_axis(seconds)
    pitch = base * (1.0 + 0.25 * np.sin(2 * np.pi * rng.uniform(2, 4) * t)) * (1.0 - 0.3 * t / seconds)
    phase = 2 * np.pi * np.cumsum(pitch) / RATE
    buzz = np.tanh(3.0 * np.sin(phase)) + 0.3 * np.sin(2 * phase)
    rasp = bandpass_noise(seconds, 300, 2500, rng) * 0.35
    env = np.clip(t / 0.02, 0, 1) * np.clip((seconds - t) / 0.06, 0, 1) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * rng.uniform(5, 8) * t)))
    return (buzz + rasp) * env


def murmur(rng, syllables, base, spread, formants, rasp=0.0, servo=0.3):
    """A wordless, vowel-like murmur: a buzz through two moving formants, syllable by syllable,
    over a quiet servo whirr. Intonation, not words."""
    parts = []
    for i in range(syllables):
        seconds = rng.uniform(0.09, 0.2)
        t = t_axis(seconds)
        f0 = base * rng.uniform(1.0 - spread, 1.0 + spread)
        f1 = f0 * rng.uniform(0.85, 1.2)
        freq = f0 + (f1 - f0) * t / seconds
        phase = 2 * np.pi * np.cumsum(freq) / RATE
        voice = np.zeros_like(t)
        for h in range(1, 14):
            hf = freq * h
            weight = 0.0
            for centre, width in formants[rng.integers(len(formants))]:
                weight += np.exp(-((hf - centre) / width) ** 2)
            voice += weight * np.sin(h * phase) / h ** 0.3
        if rasp:
            voice += rasp * bandpass_noise(seconds, 800, 4000, rng)
        env = np.sin(np.pi * t / seconds) ** 0.6
        parts.append(voice * env)
    x = sequence(parts, gap=rng.uniform(0.02, 0.05))
    t = t_axis(len(x) / RATE)
    whirr = np.sin(2 * np.pi * (180 + 40 * np.sin(2 * np.pi * 3 * t)) * t) * servo * 0.2
    return x + whirr


VOWELS = [[(700, 160), (1200, 200)], [(400, 120), (2000, 260)], [(300, 100), (900, 180)], [(550, 140), (1700, 220)]]


def servo_step(rng, seconds=0.16):
    t = t_axis(seconds)
    whine = np.sin(2 * np.pi * (520 + 300 * t / seconds) * t) * np.exp(-t / 0.06)
    tick = bandpass_noise(seconds, 2000, 8000, rng) * np.exp(-t / 0.008)
    return normalize(0.6 * whine + 0.5 * tick, 0.5)


def static(rng, seconds):
    t = t_axis(seconds)
    noise = bandpass_noise(seconds, 400, 5000, rng)
    gate = (np.sin(2 * np.pi * rng.uniform(9, 15) * t) > rng.uniform(-0.2, 0.4)).astype(float)
    gate = np.convolve(gate, np.ones(200) / 200, mode="same")
    tones = glide_tone(rng.uniform(900, 1400), rng.uniform(500, 900), seconds, "square") * 0.25
    drone = np.sin(2 * np.pi * 70 * t) * 0.3 + np.sin(2 * np.pi * 140 * t) * 0.15
    return noise * gate * 0.6 + tones * gate + drone * np.clip(t / 0.1, 0, 1) * np.clip((seconds - t) / 0.1, 0, 1)


def zap(rng, seconds=0.5):
    t = t_axis(seconds)
    crackle = bandpass_noise(seconds, 1500, 12000, rng) * (rng.random(len(t)) > 0.7) * np.exp(-t / 0.2)
    arc = np.sin(2 * np.pi * 60 * t) * np.sin(2 * np.pi * 3000 * t) * np.exp(-t / 0.15)
    return crackle + 0.6 * arc


def lowpass(x, cutoff):
    """A gentle one-pole low-pass (6 dB per octave): takes the edge off bright beeps."""
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def soft_beeps(rng, count, low, high, whistle=False, shape="sine", cutoff=2600.0):
    """Phase 12: the softer astromech voice. Fewer, lower and rounder beeps than in Phase 11: a
    sine with a little warmth (second harmonic), slow attack and release so nothing clicks or
    pierces, short pauses that feel like phrasing, and a low-pass on top."""
    parts = []
    for i in range(count):
        seconds = rng.uniform(0.07, 0.16)
        f0 = rng.uniform(low, high)
        f1 = f0 * rng.choice([0.75, 0.9, 1.0, 1.12, 1.3])
        t = t_axis(seconds)
        freq = f0 + (f1 - f0) * (t / seconds) ** 1.5
        phase = 2 * np.pi * np.cumsum(freq) / RATE
        tone = np.sin(phase) + 0.18 * np.sin(2 * phase) if shape == "sine" else 2.0 / np.pi * np.arcsin(np.sin(phase))
        env = np.clip(t / 0.018, 0, 1) * np.clip((seconds - t) / 0.045, 0, 1)
        parts.append(tone * env * rng.uniform(0.55, 0.9))
    if whistle:
        seconds = 0.32
        t = t_axis(seconds)
        f0 = rng.uniform(low, high) * 0.9
        freq = f0 * (1.0 + 0.45 * (t / seconds) ** 2)
        phase = 2 * np.pi * np.cumsum(freq) / RATE
        env = np.clip(t / 0.04, 0, 1) * np.clip((seconds - t) / 0.08, 0, 1)
        parts.append(np.sin(phase) * env * 0.7)
    x = sequence(parts, gap=rng.uniform(0.04, 0.08))
    return lowpass(x, cutoff)


def droid_voice(name, kind, rng):
    """Phase 12: calmer voices. The idle chatter (ambient) is short and quiet, a reaction
    (interact) a little longer, the ability the clearest, so every action is still heard."""
    if name == "r2d2":
        if kind == "interact":
            return soft_beeps(rng, 4, 700, 1700, whistle=True)
        if kind == "ability":
            return soft_beeps(rng, 7, 800, 2000)
        return soft_beeps(rng, rng.integers(2, 4), 650, 1500, whistle=rng.random() < 0.3)
    if name == "bb8":
        if kind == "interact":
            return soft_beeps(rng, 4, 500, 1300, whistle=True, cutoff=1800)
        if kind == "ability":
            return soft_beeps(rng, 6, 600, 1500, cutoff=1800)
        return soft_beeps(rng, rng.integers(2, 4), 450, 1200, cutoff=1800)
    if name == "chopper":
        if kind == "ability":
            return lowpass(np.concatenate([zap(rng) * 0.7, grumble(rng, 0.5, 130)]), 3000)
        return lowpass(grumble(rng, rng.uniform(0.35, 0.6), rng.uniform(100, 150)), 2200)
    if name == "mouse":
        n = 2 if kind == "ambient" else 4
        return soft_beeps(rng, n, 1000, 1900, shape="triangle", cutoff=2200)
    if name == "probe":
        return lowpass(static(rng, 0.8 if kind == "ability" else rng.uniform(0.45, 0.7)), 2400)
    if name == "c3po":
        return lowpass(murmur(rng, 5 if kind != "ambient" else 3, 190, 0.18, VOWELS, servo=0.15), 3500)
    if name == "b1":
        return lowpass(murmur(rng, 2 if kind != "ability" else 3, 150, 0.08, VOWELS[:2], rasp=0.15, servo=0.3), 3000)
    if name == "k2so":
        return lowpass(murmur(rng, 3 if kind != "ambient" else 2, 105, 0.06, VOWELS[2:], servo=0.25), 3000)
    raise ValueError(name)


def soft_servo_step(rng, seconds=0.12):
    """Phase 12: a soft, low servo tick for each step instead of the bright whine of Phase 11."""
    t = t_axis(seconds)
    whine = np.sin(2 * np.pi * (300 + 120 * t / seconds) * t) * np.exp(-t / 0.045)
    tick = bandpass_noise(seconds, 600, 2500, rng) * np.exp(-t / 0.006)
    return normalize(lowpass(0.7 * whine + 0.3 * tick, 2000), 0.35)


DROID_NAMES = ["r2d2", "c3po", "bb8", "chopper", "mouse", "probe", "b1", "k2so"]
# Peak level of each kind: idle chatter quiet, reactions a little louder, abilities clearest.
DROID_PEAKS = {"ambient": 0.3, "interact": 0.4, "ability": 0.5}


def droids():
    """The droid voices (Phase 11, calmer since Phase 12), each droid with its own random stream.
    Three idle variants, so the rare chatter does not repeat."""
    for index, name in enumerate(DROID_NAMES):
        rng = np.random.default_rng(2400 + index)
        for kind in ("ambient1", "ambient2", "ambient3", "interact", "ability"):
            base = kind.rstrip("123")
            x = droid_voice(name, base, rng)
            write_ogg(f"{name}_{kind}", normalize(x, DROID_PEAKS[base]), DROID_OUT)
    write_ogg("step", soft_servo_step(np.random.default_rng(2499)), DROID_OUT)


# Starships (Phase 11) ----------------------------------------------------------------------------
SHIP_OUT = os.path.join(os.path.dirname(OUT), "ship")
ENGINE_SECONDS = 2.0


def ship_engine(rng):
    """The X-Wing engines: a loop of a low roar, a rushing band of noise and a faint whine. Every
    frequency is on the 1 / ENGINE_SECONDS grid, so it loops without a seam; the game raises its
    pitch and volume with the speed."""
    t = t_axis(ENGINE_SECONDS)
    roar = periodic_noise(ENGINE_SECONDS, 40, 260, rng)
    rush = periodic_noise(ENGINE_SECONDS, 600, 2600, rng) * 0.35
    whine = (np.sin(2 * np.pi * 440 * t) + 0.5 * np.sin(2 * np.pi * 662 * t)) * 0.06
    throb = 0.85 + 0.15 * np.sin(2 * np.pi * 6 * t)
    return normalize(soft_clip((roar + rush) * throb + whine, 1.4), 0.5)


def ship_laser(rng, seconds=0.5):
    """A starship laser cannon: deeper and heavier than a hand blaster, with a booming body."""
    shot = blaster_shot(rng, seconds, 1300, 70, 0.8, 1.1, 0.9, 7)
    t = t_axis(seconds)
    boom = np.sin(2 * np.pi * 55 * t) * np.exp(-t * 12)
    return normalize(soft_clip(shot + boom * 0.7, 1.5), 0.85)


def ship_crash(rng, seconds=1.1):
    """Flying into something: a metallic bang, scraping and settling debris."""
    t = t_axis(seconds)
    bang = bandpass_noise(seconds, 80, 2400, rng) * np.exp(-t * 9)
    clang = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, d in ((173, 6), (311, 8), (587, 11))) * 0.35
    scrape = bandpass_noise(seconds, 1500, 6000, rng) * np.exp(-t * 4) * 0.25 * (t > 0.08)
    return normalize(soft_clip(bang + clang + scrape, 2.0), 0.8)


def ship_foils(rng, seconds=0.9):
    """The S-foils opening or closing: servo whir with a clunk at the end."""
    t = t_axis(seconds)
    whir = np.sin(2 * np.pi * (180 + 160 * t) * t) * 0.4 + bandpass_noise(seconds, 300, 1800, rng) * 0.3
    whir *= np.clip(t / 0.05, 0, 1) * np.clip((0.7 - t) / 0.1, 0, 1)
    clunk = bandpass_noise(seconds, 60, 900, rng) * np.exp(-np.maximum(t - 0.7, 0) * 30) * (t > 0.7)
    return normalize(whir + clunk, 0.55)


def tie_howl(rng, base, formants, noise_level, brightness):
    """Phase 12: the howl of a TIE's twin ion engines, built from scratch: a buzzing tone whose
    harmonics are shaped by vowel-like resonances (the "scream"), a slow wobble of its pitch, and a
    rushing band of noise beating against it. Everything runs whole times per ENGINE_SECONDS, so
    the loop has no seam; the game raises pitch and volume with the speed."""
    t = t_axis(ENGINE_SECONDS)
    # base * ENGINE_SECONDS is whole, the wobble (2 Hz) is on the loop grid: the phase closes.
    freq = base * (1.0 + 0.035 * np.sin(2 * np.pi * 2.0 * t))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    tone = np.zeros_like(t)
    for h in range(1, 30):
        f = base * h
        weight = sum(level * np.exp(-((f - centre) / width) ** 2) for centre, width, level in formants)
        tone += (weight + 0.04 / h) * np.sin(h * phase)
    rush = periodic_noise(ENGINE_SECONDS, 500, 1000 + 2500 * brightness, rng) * noise_level
    beat = 0.8 + 0.2 * np.sin(2 * np.pi * 24.0 * t)
    roar = periodic_noise(ENGINE_SECONDS, 50, 220, rng) * 0.35
    return normalize(soft_clip(tone * beat + rush + roar, 1.6), 0.5)


def tie_laser(rng, seconds, start):
    """Phase 12: a TIE cannon, brighter and snappier than the X-Wing's, with a short hard crack."""
    shot = blaster_shot(rng, seconds, start, 140, 0.6, 0.6, 1.0, 9)
    t = t_axis(seconds)
    zap = np.sin(2 * np.pi * 2400 * t * (1.0 - 0.6 * t / seconds)) * np.exp(-t * 40) * 0.25
    return normalize(soft_clip(shot + zap, 1.5), 0.8)


def ship_hit(rng, seconds=0.45):
    """Phase 12: a ship takes a hit: a metal clang over crackling sparks."""
    t = t_axis(seconds)
    clang = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * d) for f, d in ((240, 14), (415, 18), (733, 24))) * 0.5
    sparks = bandpass_noise(seconds, 2500, 9000, rng) * (rng.random(len(t)) > 0.6) * np.exp(-t * 16) * 0.6
    thud = bandpass_noise(seconds, 60, 400, rng) * np.exp(-t * 30)
    return normalize(soft_clip(clang + sparks + thud, 1.8), 0.7)


def ship_explode(rng, seconds=2.4):
    """Phase 12: a ship is destroyed: a deep blast, a rolling rumble and falling debris."""
    t = t_axis(seconds)
    blast = bandpass_noise(seconds, 30, 1800, rng) * np.exp(-t * 5)
    rumble = bandpass_noise(seconds, 25, 160, rng) * np.exp(-t * 1.6) * 0.8
    crackle = bandpass_noise(seconds, 1500, 7000, rng) * (rng.random(len(t)) > 0.85) * np.exp(-t * 2.5) * 0.4
    debris = sum(np.sin(2 * np.pi * f * t) * np.exp(-np.maximum(t - d, 0) * 20) * (t > d)
                 for f, d in ((520, 0.6), (690, 0.9), (410, 1.3), (880, 1.6))) * 0.12
    attack = np.clip(t / 0.004, 0, 1)
    return normalize(soft_clip((blast + rumble + crackle + debris) * attack, 2.2), 0.9)


def ships():
    """The starship sounds of Phase 11, with their own random stream."""
    rng = np.random.default_rng(1977_11)
    write_ogg("xwing_engine", ship_engine(rng), SHIP_OUT)
    write_ogg("xwing_laser1", ship_laser(rng), SHIP_OUT)
    write_ogg("xwing_laser2", ship_laser(rng, 0.55), SHIP_OUT)
    write_ogg("crash", ship_crash(rng), SHIP_OUT)
    write_ogg("foils", ship_foils(rng), SHIP_OUT)


def imperial_ships():
    """Phase 12: the TIE Fighter and TIE Interceptor, hits and the explosion, own random stream."""
    rng = np.random.default_rng(1977_12)
    write_ogg("tie_fighter_engine", tie_howl(rng, 152.0, [(820, 260, 1.0), (1250, 300, 0.7), (2600, 500, 0.25)], 0.45, 0.6), SHIP_OUT)
    write_ogg("tie_interceptor_engine", tie_howl(rng, 176.0, [(950, 280, 1.0), (1500, 320, 0.8), (3000, 600, 0.35)], 0.55, 0.9), SHIP_OUT)
    write_ogg("tie_laser1", tie_laser(rng, 0.4, 1700), SHIP_OUT)
    write_ogg("tie_laser2", tie_laser(rng, 0.42, 1850), SHIP_OUT)
    write_ogg("interceptor_laser1", tie_laser(rng, 0.32, 2100), SHIP_OUT)
    write_ogg("interceptor_laser2", tie_laser(rng, 0.34, 2250), SHIP_OUT)
    write_ogg("hit1", ship_hit(rng), SHIP_OUT)
    write_ogg("hit2", ship_hit(rng, 0.5), SHIP_OUT)
    write_ogg("explode", ship_explode(rng), SHIP_OUT)


# Phase 13: calmer starships ------------------------------------------------------------------------
# Longer engine loops (4 s, so the repetition is far less noticeable), softer and darker: every
# engine goes through a gentle low-pass, the hissing noise bands are lower, and the peaks are
# lower than before. The game varies volume and pitch with the flight (ShipEngineSound). Shots are
# shorter with a softer crack, so a held trigger does not pile up into a wall of noise. All built
# from scratch, nothing recorded or taken from films.
LOOP13 = 4.0


def loop_lowpass(x, cutoff):
    """Low-pass for a loop: filters the loop twice in a row and keeps the second pass, so the
    filter has settled at the seam and the loop stays seamless."""
    return lowpass(np.concatenate([x, x]), cutoff)[len(x):]


def calm_engine(rng, roar_band, rush_band, rush_level, tones, throb_hz, cutoff, peak):
    """A soft engine loop: a low roar, a quiet rushing band, a few steady tones and a slow throb.
    Every frequency is on the 1 / LOOP13 grid, so it loops without a seam."""
    t = t_axis(LOOP13)
    roar = periodic_noise(LOOP13, roar_band[0], roar_band[1], rng)
    rush = periodic_noise(LOOP13, rush_band[0], rush_band[1], rng) * rush_level
    tone = sum(level * np.sin(2 * np.pi * f * t + i) for i, (f, level) in enumerate(tones))
    throb = 0.9 + 0.1 * np.sin(2 * np.pi * throb_hz * t) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.25 * t))
    mix = soft_clip((roar + rush) * throb + tone, 1.2)
    return normalize(loop_lowpass(mix, cutoff), peak)


def calm_tie(rng, base, formants, noise_level, cutoff):
    """The TIE howl of Phase 12, toned down: fewer high harmonics, less noise, a low-pass."""
    t = t_axis(LOOP13)
    freq = base * (1.0 + 0.03 * np.sin(2 * np.pi * 1.0 * t))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    tone = np.zeros_like(t)
    for h in range(1, 18):
        f = base * h
        weight = sum(level * np.exp(-((f - centre) / width) ** 2) for centre, width, level in formants)
        tone += (weight + 0.03 / h) * np.sin(h * phase)
    rush = periodic_noise(LOOP13, 400, 1800, rng) * noise_level
    roar = periodic_noise(LOOP13, 45, 200, rng) * 0.5
    beat = 0.88 + 0.12 * np.sin(2 * np.pi * 12.0 * t)
    return normalize(loop_lowpass(soft_clip(tone * beat + rush + roar, 1.3), cutoff), 0.4)


def soft_laser(rng, seconds, start, end, body, crack, cutoff, peak=0.7):
    """A ship cannon shot: a quick falling tone with a low thump and a short, filtered crack."""
    t = t_axis(seconds)
    k = np.log(end / start) / seconds
    phase = 2 * np.pi * start * (np.exp(k * t) - 1) / k
    chirp = (np.sin(phase) + 0.3 * np.sin(2.0 * phase)) * np.exp(-t * 11) * (1 - np.exp(-t * 700))
    thump = np.sin(2 * np.pi * 70 * t * (1 - 0.4 * t)) * np.exp(-t * 22) * body
    snap = bandpass_noise(seconds, 900, 5000, rng) * np.exp(-t * 70) * crack
    tail = bandpass_noise(seconds, 120, 900, rng) * np.exp(-t * 9) * 0.15
    fade = np.clip((seconds - t) / 0.06, 0, 1)
    return normalize(lowpass(soft_clip(chirp + thump + snap + tail, 1.3), cutoff) * fade, peak)


def turbolaser(rng, seconds=0.9):
    """The Star Destroyer's heavy cannon: a deep boom with a long falling tone and a rolling tail."""
    t = t_axis(seconds)
    k = np.log(90 / 520) / seconds
    phase = 2 * np.pi * 520 * (np.exp(k * t) - 1) / k
    tone = (np.sin(phase) + 0.4 * np.sin(1.5 * phase)) * np.exp(-t * 4.5) * (1 - np.exp(-t * 300))
    boom = np.sin(2 * np.pi * 42 * t) * np.exp(-t * 6) * 1.2
    roll = bandpass_noise(seconds, 40, 400, rng) * np.exp(-t * 3.5) * 0.5
    snap = bandpass_noise(seconds, 700, 3500, rng) * np.exp(-t * 40) * 0.35
    fade = np.clip((seconds - t) / 0.12, 0, 1)
    return normalize(lowpass(soft_clip(tone + boom + roll + snap, 1.6), 3500) * fade, 0.8)


def laser_impact(rng, seconds=0.45, heavy=False):
    """A ship bolt hits something: a short burst, crackle and a little debris, no big explosion."""
    t = t_axis(seconds)
    burst = bandpass_noise(seconds, 80 if heavy else 150, 2200, rng) * np.exp(-t * (9 if heavy else 16))
    sizzle = bandpass_noise(seconds, 1500, 6000, rng) * np.exp(-t * 12) * 0.3
    pop = np.sin(2 * np.pi * (60 if heavy else 110) * t) * np.exp(-t * 18) * 0.8
    debris = sum(np.sin(2 * np.pi * f * t) * np.exp(-np.maximum(t - d, 0) * 30) * (t > d)
                 for f, d in ((640, 0.12), (870, 0.2), (520, 0.28))) * 0.06
    return normalize(lowpass(soft_clip(burst + sizzle + pop + debris, 1.6), 4000), 0.7)


def small_blast(rng, seconds=1.2):
    """Something was destroyed by a ship bolt: a compact explosion, smaller than a ship's."""
    t = t_axis(seconds)
    blast = bandpass_noise(seconds, 40, 1500, rng) * np.exp(-t * 6)
    rumble = bandpass_noise(seconds, 30, 160, rng) * np.exp(-t * 2.8) * 0.6
    crackle = bandpass_noise(seconds, 1500, 6000, rng) * (rng.random(len(t)) > 0.9) * np.exp(-t * 4) * 0.3
    attack = np.clip(t / 0.004, 0, 1)
    return normalize(lowpass(soft_clip((blast + rumble + crackle) * attack, 1.8), 5000), 0.8)


def thud(rng, seconds, low, decay, scrape):
    """A ship touching down: a low thud, the creak of the landing gear and a little scraping."""
    t = t_axis(seconds)
    body = bandpass_noise(seconds, 30, low, rng) * np.exp(-t * decay)
    knock = np.sin(2 * np.pi * 55 * t) * np.exp(-t * decay * 1.5)
    creak = np.sin(2 * np.pi * (240 - 80 * t) * t) * np.exp(-t * 7) * 0.12
    grit = bandpass_noise(seconds, 1200, 4500, rng) * np.exp(-t * 6) * scrape * (t > 0.03)
    return normalize(lowpass(soft_clip(body + knock + creak + grit, 1.5), 3000), 0.75)


def power(rng, up, seconds=1.4):
    """The engines failing (a falling, sputtering whine) or charging back up (a rising hum)."""
    t = t_axis(seconds)
    f = (90 + 260 * (t / seconds) ** 1.5) if up else (360 - 280 * (t / seconds) ** 0.7)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    tone = np.sin(phase) + 0.4 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    if up:
        env = np.clip(t / 0.4, 0, 1) * np.clip((seconds - t) / 0.3, 0, 1)
    else:
        stutter = (np.sin(2 * np.pi * (6 + 10 * t) * t) > -0.3).astype(float)
        env = np.exp(-t * 1.8) * (0.4 + 0.6 * stutter) * np.clip(t / 0.02, 0, 1)
    hiss = bandpass_noise(seconds, 300, 1800, rng) * 0.12 * env
    return normalize(lowpass(tone * env + hiss, 2500), 0.55)


def flyby(rng, seconds, low, high, peak):
    """A ship rushing past: noise that swells and falls in pitch (a Doppler sweep), then fades."""
    t = t_axis(seconds)
    centre = seconds * 0.4
    swell = np.exp(-((t - centre) / (seconds * 0.22)) ** 2)
    noise = bandpass_noise(seconds, low, high, rng)
    tone_f = 300 * np.where(t < centre, 1.15, 0.8)
    tone = np.sin(2 * np.pi * np.cumsum(tone_f) / RATE) * 0.2
    return normalize(lowpass((noise + tone) * swell, high), peak)


def phase13_ships():
    """Phase 13: the reworked ship engines and cannons, the Falcon and the Star Destroyer, impacts,
    landings and the energy sounds. Replaces the engine and laser files of Phases 11 and 12."""
    rng = np.random.default_rng(1977_13)
    write_ogg("xwing_engine", calm_engine(rng, (35, 220), (500, 1600), 0.18, [(220.0, 0.04), (330.0, 0.025)], 3.0, 1600, 0.42), SHIP_OUT)
    write_ogg("tie_fighter_engine", calm_tie(rng, 150.0, [(780, 240, 1.0), (1150, 280, 0.55), (2200, 400, 0.12)], 0.2, 2200), SHIP_OUT)
    write_ogg("tie_interceptor_engine", calm_tie(rng, 175.0, [(900, 260, 1.0), (1400, 300, 0.6), (2600, 500, 0.18)], 0.25, 2500), SHIP_OUT)
    write_ogg("falcon_engine", calm_engine(rng, (28, 180), (400, 1200), 0.14, [(110.0, 0.06), (165.0, 0.035), (55.0, 0.05)], 1.5, 1200, 0.45),
              SHIP_OUT)
    write_ogg("star_destroyer_engine", calm_engine(rng, (20, 120), (250, 700), 0.08, [(41.0, 0.08), (61.5, 0.05), (82.0, 0.03)], 0.5, 700, 0.5),
              SHIP_OUT)
    for i, (start, end) in enumerate(((1250, 160), (1150, 150), (1350, 170)), start=1):
        write_ogg("xwing_laser%d" % i, soft_laser(rng, 0.32, start, end, 0.8, 0.25, 4500), SHIP_OUT)
    for i, start in enumerate((1600, 1750, 1680), start=1):
        write_ogg("tie_laser%d" % i, soft_laser(rng, 0.28, start, 220, 0.5, 0.3, 5500, 0.65), SHIP_OUT)
    for i, start in enumerate((1950, 2100, 2020), start=1):
        write_ogg("interceptor_laser%d" % i, soft_laser(rng, 0.24, start, 260, 0.4, 0.3, 6000, 0.6), SHIP_OUT)
    for i, start in enumerate((900, 980), start=1):
        write_ogg("falcon_laser%d" % i, soft_laser(rng, 0.38, start, 110, 1.0, 0.2, 3800, 0.72), SHIP_OUT)
    write_ogg("turbolaser1", turbolaser(rng), SHIP_OUT)
    write_ogg("turbolaser2", turbolaser(rng, 1.0), SHIP_OUT)
    write_ogg("laser_impact1", laser_impact(rng), SHIP_OUT)
    write_ogg("laser_impact2", laser_impact(rng, 0.5), SHIP_OUT)
    write_ogg("laser_impact_heavy", laser_impact(rng, 0.7, heavy=True), SHIP_OUT)
    write_ogg("small_blast", small_blast(rng), SHIP_OUT)
    write_ogg("land", thud(rng, 0.7, 220, 9, 0.15), SHIP_OUT)
    write_ogg("impact", thud(rng, 1.0, 500, 5, 0.5), SHIP_OUT)
    write_ogg("power_down", power(rng, False), SHIP_OUT)
    write_ogg("power_up", power(rng, True), SHIP_OUT)
    write_ogg("flyby", flyby(rng, 1.4, 200, 2600, 0.6), SHIP_OUT)
    write_ogg("flyby_heavy", flyby(rng, 3.0, 30, 600, 0.7), SHIP_OUT)


# Phase 14: flight layers, the Falcon's own drive, hyperspace, destruction, interiors ------------------
# Engine loops of 8 s (twice Phase 13) with slow movements inside, so a loop is hard to hear. Each
# ship has two layers that the game mixes by flight state (ShipEngineSound): the engine itself and
# a thrust layer that rises while speeding up. Speeding up and braking also play short one-shots.
# All built from scratch: noise bands, sines and envelopes, nothing recorded or taken from films.
LOOP14 = 8.0


def fast_lowpass(x, cutoff):
    """The one-pole low-pass of lowpass(), done in the frequency domain (fast for long loops; the
    same 6 dB per octave slope, applied circularly, so a loop stays a loop)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    return np.fft.irfft(spec / np.sqrt(1 + (f / cutoff) ** 2), len(x))


def falcon_drive(rng):
    """The Falcon's sublight drive: a deep, throaty rumble that 'purrs' (a rough beat at 18 Hz),
    an uneven mid growl that rises and falls twice per loop, and now and then a hull creak, a
    rattle and a hiss of venting coolant: an old freighter that has seen a lot. 8 s loop."""
    seconds = LOOP14
    t = t_axis(seconds)
    rumble = periodic_noise(seconds, 22, 140, rng)
    purr = 0.72 + 0.28 * np.sin(2 * np.pi * 18.0 * t) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.5 * t))
    growl_f = 73.0 * (1.0 + 0.02 * np.sin(2 * np.pi * 0.25 * t))
    phase = 2 * np.pi * np.cumsum(growl_f) / RATE
    growl = (np.sin(phase) + 0.55 * np.sin(2 * phase + 0.4) + 0.35 * np.sin(3 * phase) + 0.18 * np.sin(5 * phase)) * \
        (0.55 + 0.45 * np.sin(2 * np.pi * 0.25 * t) ** 2)
    rush = periodic_noise(seconds, 280, 1100, rng) * 0.16 * (0.7 + 0.3 * np.sin(2 * np.pi * 0.375 * t))
    whine = np.sin(2 * np.pi * 296.0 * t) * 0.025 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.125 * t))
    loop = soft_clip(rumble * purr * 0.9 + growl * 0.32 + rush + whine, 1.3)
    loop = fast_lowpass(loop, 1100)
    # Events: wrap around the end, so the loop has no seam.
    events = np.zeros_like(t)
    for start, kind in ((1.3, "creak"), (3.9, "rattle"), (5.6, "vent"), (7.1, "creak")):
        n = int(start * RATE)
        if kind == "creak":
            d = 0.45
            tt = t_axis(d)
            ev = np.sin(2 * np.pi * (180 + 70 * np.sin(2 * np.pi * 3 * tt)) * tt) * np.exp(-tt * 6) * 0.06
        elif kind == "rattle":
            d = 0.35
            tt = t_axis(d)
            ev = bandpass_noise(d, 900, 3000, rng) * (np.sin(2 * np.pi * 34 * tt) > 0.3) * np.exp(-tt * 9) * 0.05
        else:
            d = 0.9
            tt = t_axis(d)
            ev = bandpass_noise(d, 1200, 4500, rng) * np.sin(np.pi * tt / d) ** 2 * 0.045
        idx = (np.arange(len(ev)) + n) % len(t)
        events[idx] += ev
    return normalize(loop + events, 0.45)


def thrust_layer(rng, roar, rush, tone_hz, rough, cutoff, peak):
    """The thrust layer of an engine: a broad roar and a brighter rushing band that the game fades
    in while the ship speeds up. Optional rough beat (TIE scream) and a tone on the loop grid."""
    seconds = LOOP14
    t = t_axis(seconds)
    body = periodic_noise(seconds, roar[0], roar[1], rng)
    air = periodic_noise(seconds, rush[0], rush[1], rng) * 0.5
    swell = 0.8 + 0.2 * np.sin(2 * np.pi * 0.375 * t)
    tone = 0.0
    if tone_hz:
        tone = sum(np.sin(2 * np.pi * tone_hz * h * t + h) / h for h in range(1, 6)) * 0.12
    beat = 1.0 + rough * np.sin(2 * np.pi * 24.0 * t)
    return normalize(fast_lowpass(soft_clip((body + air) * swell * beat + tone, 1.4), cutoff), peak)


def boost(rng, seconds, low, high, rise, peak, clank=False):
    """Speeding up hard: a surge of roar that rises in pitch, optionally with a mechanical clank."""
    t = t_axis(seconds)
    swell = np.clip(t / 0.08, 0, 1) * np.exp(-t * 2.2)
    noise = bandpass_noise(seconds, low, high, rng)
    f = rise[0] + (rise[1] - rise[0]) * (1 - np.exp(-t * 3))
    tone = np.sin(2 * np.pi * np.cumsum(f) / RATE) * 0.25 + np.sin(4 * np.pi * np.cumsum(f) / RATE) * 0.1
    out = (noise + tone) * swell
    if clank:
        out += sum(np.sin(2 * np.pi * fr * t) * np.exp(-t * 30) for fr in (210, 377, 590)) * 0.25
    return normalize(lowpass(soft_clip(out, 1.4), high), peak)


def brake(rng, seconds, low, high, peak):
    """Braking: a falling exhale of the engines with a hiss of the manoeuvring jets."""
    t = t_axis(seconds)
    env = np.clip(t / 0.05, 0, 1) * np.exp(-t * 2.8)
    noise = bandpass_noise(seconds, low, high, rng)
    f = 260 * np.exp(-t * 1.6) + 60
    tone = np.sin(2 * np.pi * np.cumsum(f) / RATE) * 0.3
    hiss = bandpass_noise(seconds, 1800, 5000, rng) * np.exp(-t * 7) * 0.25
    return normalize(lowpass((noise * 0.8 + tone) * env + hiss, high), peak)


def hyper_charge(rng, seconds=1.6):
    """The hyperdrive spooling up: a rising, beating whine over a growing low hum."""
    t = t_axis(seconds)
    f = 90 + 520 * (t / seconds) ** 2.2
    ph = 2 * np.pi * np.cumsum(f) / RATE
    whine = (np.sin(ph) + 0.5 * np.sin(2.01 * ph) + 0.25 * np.sin(3.02 * ph)) * (t / seconds) ** 1.2
    hum = np.sin(2 * np.pi * 55 * t) * 0.5 * np.clip(t / 0.4, 0, 1)
    air = bandpass_noise(seconds, 300, 3000, rng) * (t / seconds) ** 3 * 0.4
    fade = np.clip((seconds - t) / 0.05, 0, 1)
    return normalize(lowpass(soft_clip(whine + hum + air, 1.3), 4200) * fade, 0.6)


def hyper_jump(rng, seconds=2.2):
    """The jump: a deep boom, then a long rushing sweep that falls away."""
    t = t_axis(seconds)
    boom = np.sin(2 * np.pi * (48 + 60 * np.exp(-t * 10)) * t) * np.exp(-t * 3.0)
    sweep_f = 2600 * np.exp(-t * 1.8) + 120
    sweep = bandpass_noise(seconds, 100, 6000, rng)
    sweep = fast_lowpass(sweep, 2500) * np.exp(-t * 1.2) * np.clip(t / 0.03, 0, 1)
    zing = np.sin(2 * np.pi * np.cumsum(sweep_f) / RATE) * np.exp(-t * 2.5) * 0.3
    fade = np.clip((seconds - t) / 0.3, 0, 1)
    return normalize(soft_clip(boom * 1.1 + sweep + zing, 1.6) * fade, 0.85)


def hyper_arrive(rng, seconds=1.4):
    """Dropping out of hyperspace: a sweep falling in from above and a soft thump."""
    t = t_axis(seconds)
    f = 1800 * np.exp(-t * 4) + 70
    zing = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t * 3) * 0.4
    air = fast_lowpass(bandpass_noise(seconds, 200, 5000, rng), 3000) * np.exp(-t * 4) * np.clip(t / 0.02, 0, 1)
    thump = np.sin(2 * np.pi * 50 * t) * np.exp(-t * 9) * 0.9
    return normalize(soft_clip(zing + air + thump, 1.4), 0.7)


def hyper_fail(rng, seconds=0.7):
    """The jump cannot start: two falling, dull blips and a short fizzle."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for start, f in ((0.0, 520), (0.22, 390)):
        tt = np.maximum(t - start, 0)
        out += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 14) * (t >= start)
    fizz = bandpass_noise(seconds, 800, 3000, rng) * np.exp(-np.maximum(t - 0.4, 0) * 10) * (t > 0.4) * 0.2
    return normalize(lowpass(out + fizz, 3000), 0.45)


def big_explosion(rng, seconds=3.6):
    """A ship breaking apart: a first blast, two secondary blasts, a long rumble and debris."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for start, size in ((0.0, 1.0), (0.45, 0.7), (1.05, 0.55)):
        tt = np.maximum(t - start, 0)
        gate = t >= start
        blast = bandpass_noise(seconds, 30, 1800, rng) * np.exp(-tt * 5) * gate
        boom = np.sin(2 * np.pi * (40 + 30 * np.exp(-tt * 12)) * tt) * np.exp(-tt * 4) * gate
        out += (blast + boom) * size * np.clip(tt / 0.004, 0, 1)
    rumble = bandpass_noise(seconds, 20, 140, rng) * np.exp(-t * 0.9) * 0.6
    debris = sum(np.sin(2 * np.pi * f * t) * np.exp(-np.maximum(t - d, 0) * 18) * (t > d)
                 for f, d in ((520, 0.8), (690, 1.3), (410, 1.7), (880, 2.1), (610, 2.6))) * 0.1
    fade = np.clip((seconds - t) / 0.4, 0, 1)
    return normalize(fast_lowpass(soft_clip(out + rumble + debris, 2.0), 5000) * fade, 0.9)


def alarm(rng, seconds=1.2):
    """Hull critical: a soft two-tone warble, three times (quiet, not shrill)."""
    t = t_axis(seconds)
    f = np.where((t * 5).astype(int) % 2 == 0, 660, 520)
    tone = np.sin(2 * np.pi * np.cumsum(f) / RATE)
    gate = np.clip(np.sin(np.pi * (t % 0.4) / 0.4) * 1.5, 0, 1)
    return normalize(lowpass(tone * gate * (t < 1.2), 2200), 0.35)


def ramp(rng, seconds=2.0):
    """A boarding ramp: a hydraulic hiss, a low motor and a clunk when it locks."""
    t = t_axis(seconds)
    hiss = bandpass_noise(seconds, 1500, 5000, rng) * np.exp(-t * 4) * 0.5
    motor = (np.sin(2 * np.pi * (95 + 15 * t) * t) + 0.4 * np.sin(2 * np.pi * 190 * t)) * np.clip(t / 0.1, 0, 1) * np.clip((1.6 - t) / 0.15, 0, 1) * 0.4
    clunk = bandpass_noise(seconds, 60, 900, rng) * np.exp(-np.maximum(t - 1.6, 0) * 25) * (t > 1.6)
    return normalize(lowpass(hiss + motor + clunk, 4000), 0.55)


def lift(rng, seconds=2.4):
    """A turbolift: a rising hum with a soft swoosh, and a chime at the end."""
    t = t_axis(seconds)
    f = 120 + 160 * np.sin(np.pi * np.minimum(t / 2.0, 1.0) / 2)
    hum = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.clip(t / 0.2, 0, 1) * np.clip((2.0 - t) / 0.3, 0, 1) * 0.5
    air = fast_lowpass(bandpass_noise(seconds, 200, 2500, rng), 1500) * np.sin(np.pi * np.minimum(t / 2.0, 1.0)) * 0.4
    chime = sum(np.sin(2 * np.pi * fr * np.maximum(t - 2.0, 0)) * np.exp(-np.maximum(t - 2.0, 0) * 6) * (t > 2.0) for fr in (880, 1320)) * 0.2
    return normalize(hum + air + chime, 0.5)


def interior(rng, seconds, hum_hz, hum_level, air_band, beeps, vents):
    """The air of a ship's inside: a low machine hum, air from the vents, quiet console beeps and
    now and then a hiss; 16 s loop with wrapped events (no seam)."""
    t = t_axis(seconds)
    hum = sum(np.sin(2 * np.pi * hum_hz * h * t + h) / h ** 1.5 for h in range(1, 5)) * hum_level
    air = fast_lowpass(periodic_noise(seconds, air_band[0], air_band[1], rng), air_band[1]) * 0.25
    air *= 0.85 + 0.15 * np.sin(2 * np.pi * (2 / seconds) * t)
    events = np.zeros_like(t)
    for start, f in beeps:
        d = 0.18
        tt = t_axis(d)
        ev = np.sin(2 * np.pi * f * tt) * np.sin(np.pi * tt / d) ** 2 * 0.05
        idx = (np.arange(len(ev)) + int(start * RATE)) % len(t)
        events[idx] += ev
    for start in vents:
        d = 1.2
        tt = t_axis(d)
        ev = bandpass_noise(d, 1000, 4000, rng) * np.sin(np.pi * tt / d) ** 2 * 0.06
        idx = (np.arange(len(ev)) + int(start * RATE)) % len(t)
        events[idx] += ev
    return normalize(fast_lowpass(hum + air + events, 3000), 0.4)


def phase14():
    """Phase 14: the Falcon's own drive, thrust layers, boost and brake, hyperspace, the big
    explosion, the hull alarm, ramps, turbolifts and the interiors. Own random stream."""
    rng = np.random.default_rng(1977_14)
    write_ogg("falcon_engine", falcon_drive(rng), SHIP_OUT)
    write_ogg("falcon_thrust", thrust_layer(rng, (30, 260), (500, 2200), 88.0, 0.12, 1800, 0.45), SHIP_OUT)
    write_ogg("xwing_thrust", thrust_layer(rng, (40, 300), (700, 2800), 0.0, 0.0, 2200, 0.4), SHIP_OUT)
    write_ogg("tie_thrust", thrust_layer(rng, (60, 280), (800, 3200), 165.0, 0.3, 2600, 0.38), SHIP_OUT)
    write_ogg("star_destroyer_thrust", thrust_layer(rng, (18, 140), (200, 800), 41.0, 0.0, 800, 0.5), SHIP_OUT)
    write_ogg("falcon_boost", boost(rng, 1.6, 40, 1600, (120, 330), 0.6, clank=True), SHIP_OUT)
    write_ogg("boost", boost(rng, 1.2, 60, 2600, (180, 420), 0.55), SHIP_OUT)
    write_ogg("brake", brake(rng, 1.1, 80, 1800, 0.45), SHIP_OUT)
    write_ogg("falcon_brake", brake(rng, 1.5, 40, 1300, 0.5), SHIP_OUT)
    write_ogg("hyperspace_charge", hyper_charge(rng), SHIP_OUT)
    write_ogg("hyperspace_jump", hyper_jump(rng), SHIP_OUT)
    write_ogg("hyperspace_arrive", hyper_arrive(rng), SHIP_OUT)
    write_ogg("hyperspace_fail", hyper_fail(rng), SHIP_OUT)
    write_ogg("explode_big", big_explosion(rng), SHIP_OUT)
    write_ogg("alarm", alarm(rng), SHIP_OUT)
    write_ogg("ramp", ramp(rng), SHIP_OUT)
    write_ogg("lift", lift(rng), SHIP_OUT)
    write_ogg("interior_falcon", interior(rng, 16.0, 60.0, 0.5, (150, 900), ((2.1, 1180), (2.35, 880), (9.4, 990), (12.8, 1320)), (5.0, 13.9)),
              SHIP_OUT)
    write_ogg("interior_bridge", interior(rng, 16.0, 45.0, 0.6, (100, 700), ((1.0, 1500), (3.2, 1250), (3.4, 1500), (6.6, 1100), (10.1, 1400),
                                                                              (13.3, 1250)), (8.0,)), SHIP_OUT)


# Planet ambiences (Phase 10) ---------------------------------------------------------------------
# Loops of AMBIENT_SECONDS. Every slow modulation runs a whole number of times per loop, and every
# event (a gust, a bird, a crackle) that runs over the end wraps to the start, so they loop
# without a seam.
AMBIENT_SECONDS = 16.0


def lfo(t, cycles, phase=0.0, depth=1.0):
    """0..1 modulation that runs exactly `cycles` times per loop."""
    return 1.0 - depth * 0.5 * (1.0 + np.cos(2 * np.pi * cycles * t / AMBIENT_SECONDS + phase))


def place(loop, start, event):
    """Adds an event at `start` (samples), wrapping what runs over the end to the start."""
    n = len(loop)
    idx = (np.arange(len(event)) + start) % n
    np.add.at(loop, idx, event)


def chirp(rng, f0, f1, seconds, level):
    t = t_axis(seconds)
    freq = f0 + (f1 - f0) * (t / seconds) ** 0.7
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    envelope = np.sin(np.pi * t / seconds) ** 2
    return np.sin(phase) * envelope * level


def desert_wind(rng):
    """Tatooine: a dry, hot wind over sand, with slow gusts and a faint hiss of blown sand."""
    t = t_axis(AMBIENT_SECONDS)
    wind = periodic_noise(AMBIENT_SECONDS, 120, 700, rng) * (0.35 + 0.65 * lfo(t, 2, 0.4)) * (0.7 + 0.3 * lfo(t, 5, 1.3))
    sand = periodic_noise(AMBIENT_SECONDS, 2500, 7000, rng) * 0.12 * lfo(t, 2, 0.9) ** 2
    return normalize(wind + sand, 0.5)


def blizzard(rng):
    """Hoth: a cold blizzard, a brighter roar and a howl that rises and falls."""
    t = t_axis(AMBIENT_SECONDS)
    roar = periodic_noise(AMBIENT_SECONDS, 300, 2600, rng) * (0.45 + 0.55 * lfo(t, 3, 0.2))
    # The howl: a narrow band of noise whose loudness follows a slow wave.
    howl = periodic_noise(AMBIENT_SECONDS, 620, 760, rng) * 0.9 * lfo(t, 2, 2.0) ** 2
    howl += periodic_noise(AMBIENT_SECONDS, 930, 1010, rng) * 0.5 * lfo(t, 3, 0.7) ** 2
    snow = periodic_noise(AMBIENT_SECONDS, 4000, 9000, rng) * 0.08
    return normalize(roar + howl + snow, 0.5)


def lava(rng):
    """Mustafar: a deep rumble of lava, with bubbling and sharp crackles."""
    t = t_axis(AMBIENT_SECONDS)
    loop = periodic_noise(AMBIENT_SECONDS, 25, 140, rng) * (0.6 + 0.4 * lfo(t, 3, 0.5))
    loop += periodic_noise(AMBIENT_SECONDS, 150, 400, rng) * 0.15
    for _ in range(18):
        # A bubble: a short, low, rising blip.
        seconds = rng.uniform(0.08, 0.18)
        place(loop, int(rng.uniform(0, AMBIENT_SECONDS) * RATE), chirp(rng, rng.uniform(90, 140), rng.uniform(200, 320), seconds, 0.5))
    for _ in range(40):
        # A crackle: a click of noise that dies away fast.
        n = int(rng.uniform(0.01, 0.04) * RATE)
        event = rng.standard_normal(n) * np.exp(-np.arange(n) / (n / 5)) * rng.uniform(0.1, 0.35)
        place(loop, int(rng.uniform(0, AMBIENT_SECONDS) * RATE), event)
    return normalize(loop, 0.5)


def forest(rng):
    """Endor: a soft wind in tall trees, insects and the calls of birds far away."""
    t = t_axis(AMBIENT_SECONDS)
    loop = periodic_noise(AMBIENT_SECONDS, 180, 1100, rng) * 0.35 * (0.5 + 0.5 * lfo(t, 2, 1.0))
    loop += periodic_noise(AMBIENT_SECONDS, 4200, 5200, rng) * 0.05 * (0.6 + 0.4 * lfo(t, 16, 0.0))
    for _ in range(7):
        start = int(rng.uniform(0, AMBIENT_SECONDS) * RATE)
        base = rng.uniform(1800, 3200)
        for k in range(rng.integers(2, 5)):
            call = chirp(rng, base * rng.uniform(1.0, 1.2), base * rng.uniform(0.7, 1.4), rng.uniform(0.07, 0.16), rng.uniform(0.12, 0.22))
            place(loop, start + int(k * rng.uniform(0.12, 0.2) * RATE), call)
    return normalize(loop, 0.45)


def lake(rng):
    """Naboo: a gentle breeze, water lapping at a shore and a few bright birds."""
    t = t_axis(AMBIENT_SECONDS)
    loop = periodic_noise(AMBIENT_SECONDS, 150, 900, rng) * 0.25 * (0.6 + 0.4 * lfo(t, 2, 2.2))
    # Lapping water: low noise in soft waves, a little more than one every two seconds.
    loop += periodic_noise(AMBIENT_SECONDS, 60, 500, rng) * 0.45 * lfo(t, 7, 0.0) ** 3
    for _ in range(5):
        start = int(rng.uniform(0, AMBIENT_SECONDS) * RATE)
        notes = rng.choice([1568.0, 1760.0, 1976.0, 2349.0, 2637.0], size=3)
        for k, f in enumerate(notes):
            place(loop, start + int(k * 0.18 * RATE), chirp(rng, f, f * 1.04, 0.14, 0.13))
    return normalize(loop, 0.45)


def city(rng):
    """Coruscant: the deep hum of a city without end and the whoosh of traffic passing far away."""
    t = t_axis(AMBIENT_SECONDS)
    # 60 Hz and its harmonics, all on the loop grid.
    hum = sum(level * np.sin(2 * np.pi * f * t) for f, level in [(60.0, 0.3), (120.0, 0.16), (180.0, 0.06)])
    loop = hum * (0.8 + 0.2 * lfo(t, 1, 0.0)) + periodic_noise(AMBIENT_SECONDS, 80, 500, rng) * 0.25
    for _ in range(9):
        seconds = rng.uniform(1.2, 2.6)
        n = int(seconds * RATE)
        swell = np.sin(np.pi * np.arange(n) / n) ** 2
        whoosh = periodic_noise(seconds, rng.uniform(300, 600), rng.uniform(1500, 3000), rng) * swell * rng.uniform(0.15, 0.3)
        place(loop, int(rng.uniform(0, AMBIENT_SECONDS) * RATE), whoosh)
    return normalize(loop, 0.45)


def ambient():
    """The planet ambiences of Phase 10, with their own random stream."""
    rng = np.random.default_rng(1010)
    out = os.path.join(os.path.dirname(OUT), "ambient")
    write_ogg("tatooine", desert_wind(rng), out)
    write_ogg("hoth", blizzard(rng), out)
    write_ogg("mustafar", lava(rng), out)
    write_ogg("endor", forest(rng), out)
    write_ogg("naboo", lake(rng), out)
    write_ogg("coruscant", city(rng), out)


# Atmosphere (Phase 11 G) -------------------------------------------------------------------------
def blaster_draw(rng, seconds=0.42):
    """Drawing a blaster: a short servo whir that rises, then the click of the grip locking in."""
    t = t_axis(seconds)
    whir = np.sin(2 * np.pi * (380 * t + 900 * t * t)) * np.minimum(1, t * 30) * np.exp(-t * 6) * (t < 0.3)
    rustle = bandpass_noise(seconds, 800, 3500, rng) * np.exp(-t * 14) * 0.35
    click = np.zeros_like(t)
    n = int(0.28 * RATE)
    tt = t[: len(t) - n]
    click[n:] = bandpass_noise(seconds, 2500, 9000, rng)[: len(tt)] * np.exp(-tt * 350) + np.sin(2 * np.pi * 2100 * tt) * np.exp(-tt * 60) * 0.25
    return normalize(0.45 * whir + rustle + click, 0.5)


def blaster_holster(rng, seconds=0.4):
    """Holstering: a release click, a falling whir and a soft leather thump."""
    t = t_axis(seconds)
    click = bandpass_noise(seconds, 2500, 9000, rng) * np.exp(-t * 380)
    whir = np.sin(2 * np.pi * (900 * t - 700 * t * t)) * np.exp(-t * 9) * (t > 0.03) * 0.4
    thump = np.zeros_like(t)
    n = int(0.22 * RATE)
    tt = t[: len(t) - n]
    thump[n:] = (np.sin(2 * np.pi * 110 * tt) * 0.6 + bandpass_noise(seconds, 200, 1200, rng)[: len(tt)] * 0.5) * np.exp(-tt * 40)
    return normalize(click + whir + thump, 0.5)


def menu_sweep(rng, seconds=0.3):
    """A page of the menu coming up: a soft, airy holo sweep, quiet enough for every tab switch."""
    t = t_axis(seconds)
    sweep = bandpass_noise(seconds, 1500, 6000, rng) * np.sin(np.pi * t / seconds) ** 2 * 0.5
    tone = np.sin(2 * np.pi * (600 * t + 1500 * t * t)) * np.sin(np.pi * t / seconds) ** 3 * 0.25
    return normalize(sweep + tone, 0.3)


def atmosphere():
    """The atmosphere sounds of Phase 11 G, with their own random stream."""
    rng = np.random.default_rng(1138)
    out = os.path.join(os.path.dirname(OUT), "blaster")
    write_ogg("draw", blaster_draw(rng), out)
    write_ogg("holster", blaster_holster(rng), out)
    write_ogg("menu_sweep", menu_sweep(rng), os.path.join(os.path.dirname(OUT), "ui"))


def main():
    if "--ambient" in sys.argv:
        ambient()
        return

    if "--blaster" in sys.argv:
        blasters()
        return

    if "--phase11-blaster" in sys.argv:
        phase11_blasters()
        return

    if "--droids" in sys.argv:
        droids()
        return

    if "--atmosphere" in sys.argv:
        atmosphere()
        return

    if "--ships" in sys.argv:
        ships()
        return

    if "--imperial-ships" in sys.argv:
        imperial_ships()
        return

    if "--phase14" in sys.argv:
        phase14()
        return

    if "--phase13-ships" in sys.argv:
        phase13_ships()
        return

    if "--phase11-combat" in sys.argv:
        phase11_combat()
        return

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
