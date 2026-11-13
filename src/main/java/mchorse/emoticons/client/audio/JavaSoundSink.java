package mchorse.emoticons.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays PCM through Java Sound. It's independent from Minecraft's OpenAL
 * sound system, so the volume has to be applied by the player.
 */
public class JavaSoundSink implements AudioSink
{
    private SourceDataLine line;

    @Override
    public void open(int sampleRate, int channels) throws Exception
    {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        /* About 125 ms of buffer, aligned to the frame size, so volume
         * changes are heard quickly */
        int frame = channels * 2;
        int buffer = (sampleRate * frame / 8) / frame * frame;

        this.line = (SourceDataLine) AudioSystem.getLine(info);
        this.line.open(format, buffer);
        this.line.start();
    }

    @Override
    public void write(byte[] data, int length)
    {
        if (this.line != null)
        {
            this.line.write(data, 0, length);
        }
    }

    @Override
    public void close(boolean flush)
    {
        if (this.line == null)
        {
            return;
        }

        try
        {
            if (flush)
            {
                this.line.stop();
                this.line.flush();
            }
            else
            {
                this.line.drain();
            }
        }
        finally
        {
            this.line.close();
            this.line = null;
        }
    }
}
