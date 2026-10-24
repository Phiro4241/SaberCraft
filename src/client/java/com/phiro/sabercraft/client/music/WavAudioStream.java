package com.phiro.sabercraft.client.music;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.lwjgl.BufferUtils;

import net.minecraft.client.sounds.AudioStream;

/**
 * Streams an uncompressed WAV file (Phase 8, Star Wars music mode) for the sound engine, which
 * itself only decodes Ogg Vorbis. Java's own audio classes read the file and convert it to 16 bit
 * signed PCM, mono or stereo, which OpenAL plays directly.
 */
public final class WavAudioStream implements AudioStream {
	private final AudioInputStream input;
	private final AudioFormat format;

	public WavAudioStream(InputStream raw) throws IOException {
		try {
			AudioInputStream source = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
			AudioFormat from = source.getFormat();

			if (from.getChannels() < 1 || from.getChannels() > 2) {
				source.close();
				throw new IOException("Only mono and stereo WAV files can be played, this one has " + from.getChannels() + " channels");
			}

			this.format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, from.getSampleRate(), 16, from.getChannels(), from.getChannels() * 2,
					from.getSampleRate(), false);
			this.input = AudioSystem.getAudioInputStream(this.format, source);
		} catch (UnsupportedAudioFileException | IllegalArgumentException error) {
			throw new IOException("Unsupported WAV file: " + error.getMessage(), error);
		}
	}

	@Override
	public AudioFormat getFormat() {
		return this.format;
	}

	@Override
	public ByteBuffer read(int expectedSize) throws IOException {
		int frame = this.format.getFrameSize();
		byte[] bytes = new byte[Math.max(frame, expectedSize - expectedSize % frame)];
		int filled = 0;

		while (filled < bytes.length) {
			int read = this.input.read(bytes, filled, bytes.length - filled);

			if (read < 0) {
				break;
			}

			filled += read;
		}

		filled -= filled % frame;
		ByteBuffer out = BufferUtils.createByteBuffer(filled);
		out.put(bytes, 0, filled).flip();
		return out;
	}

	@Override
	public void close() throws IOException {
		this.input.close();
	}
}
