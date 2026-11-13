package mchorse.emoticons.client.audio;

/**
 * Where decoded 16-bit little endian PCM goes
 */
public interface AudioSink
{
    /**
     * Open the output. Called once, right before the first write
     */
    public void open(int sampleRate, int channels) throws Exception;

    /**
     * Write PCM data (blocks until there's room)
     */
    public void write(byte[] data, int length);

    /**
     * Close the output
     *
     * @param flush true if playback was cut short (discard what's buffered),
     *              false if it ended on its own (let the buffer finish)
     */
    public void close(boolean flush);
}
