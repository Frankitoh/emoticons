package mchorse.emoticons.client.audio;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Keeps the volume of custom emote sounds up to date and stops them when
 * their player goes away
 */
@SideOnly(Side.CLIENT)
public class EmoteSoundsHandler
{
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            EmoteSounds.tick();
        }
    }
}
