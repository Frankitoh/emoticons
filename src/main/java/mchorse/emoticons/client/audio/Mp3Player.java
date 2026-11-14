package mchorse.emoticons.client.audio;

import mchorse.emoticons.client.audio.jl.Bitstream;
import mchorse.emoticons.client.audio.jl.Decoder;
import mchorse.emoticons.client.audio.jl.DecoderException;
import mchorse.emoticons.client.audio.jl.Header;
import mchorse.emoticons.client.audio.jl.SampleBuffer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Streams an MP3 file into an {@link AudioSink} on its own thread. It
 * doesn't depend on Minecraft.
 */
public class Mp3Player implements Runnable
{
    private final File file;
    private final boolean loop;
    private final float start;
    private final AudioSink sink;

    private volatile boolean stopped;
    private volatile boolean paused;
    private volatile boolean finished;
    private volatile float left = 1;
    private volatile float right = 1;

    private boolean opened;
    private byte[] bytes = new byte[0];

    /**
     * @param start seconds to skip at the start of the file (on every loop)
     */
    public Mp3Player(File file, boolean loop, float start, AudioSink sink)
    {
        this.file = file;
        this.loop = loop;
        this.start = start;
        this.sink = sink;
    }

    public void play()
    {
        Thread thread = new Thread(this, "Emoticons MP3 player");

        thread.setDaemon(true);
        thread.start();
    }

    public void stop()
    {
        this.stopped = true;
    }

    public boolean isFinished()
    {
        return this.finished;
    }

    public void setPaused(boolean paused)
    {
        this.paused = paused;
    }

    /**
     * Set the volume of each channel (1 = file's own volume)
     */
    public void setGain(float left, float right)
    {
        this.left = left;
        this.right = right;
    }

    @Override
    public void run()
    {
        try
        {
            while (!this.stopped)
            {
                boolean played = this.playOnce();

                /* Don't spin on an empty or broken file */
                if (!played || !this.loop)
                {
                    break;
                }
            }
        }
        catch (Throwable e)
        {
            System.err.println("[Emoticons] Couldn't play " + this.file.getName() + ": " + e);
            e.printStackTrace();
        }
        finally
        {
            try
            {
                this.sink.close(this.stopped);
            }
            catch (Throwable e)
            {}

            this.finished = true;
        }
    }

    /**
     * Play the file once, returns whether anything was played
     */
    private boolean playOnce() throws Exception
    {
        InputStream in = new BufferedInputStream(new FileInputStream(this.file), 1 << 16);

        try
        {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();
            float skip = this.start * 1000F;
            boolean played = false;

            while (!this.stopped)
            {
                Header header = bitstream.readFrame();

                if (header == null)
                {
                    break;
                }

                if (skip > 0)
                {
                    skip -= header.ms_per_frame();
                    bitstream.closeFrame();

                    continue;
                }

                SampleBuffer buffer;

                try
                {
                    buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                }
                catch (DecoderException e)
                {
                    /* Bad frame, skip it */
                    bitstream.closeFrame();

                    continue;
                }

                int channels = buffer.getChannelCount();

                if (!this.opened)
                {
                    this.sink.open(buffer.getSampleFrequency(), channels);
                    this.opened = true;
                }

                while (this.paused && !this.stopped)
                {
                    Thread.sleep(25);
                }

                if (this.stopped)
                {
                    break;
                }

                int length = this.convert(buffer.getBuffer(), buffer.getBufferLength(), channels);

                this.sink.write(this.bytes, length);

                bitstream.closeFrame();
                played = true;
            }

            bitstream.close();

            return played;
        }
        finally
        {
            in.close();
        }
    }

    /**
     * Convert samples into 16-bit little endian bytes, applying the volume
     */
    private int convert(short[] samples, int count, int channels)
    {
        if (this.bytes.length < count * 2)
        {
            this.bytes = new byte[count * 2];
        }

        float left = this.left;
        float right = this.right;
        float mono = (left + right) * 0.5F;

        for (int i = 0; i < count; i++)
        {
            float gain = channels == 2 ? ((i & 1) == 0 ? left : right) : mono;
            int value = (int) (samples[i] * gain);

            if (value > Short.MAX_VALUE)
            {
                value = Short.MAX_VALUE;
            }
            else if (value < Short.MIN_VALUE)
            {
                value = Short.MIN_VALUE;
            }

            this.bytes[i * 2] = (byte) value;
            this.bytes[i * 2 + 1] = (byte) (value >> 8);
        }

        return count * 2;
    }
}
