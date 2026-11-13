package mchorse.emoticons.client.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mchorse.emoticons.common.emotes.Emote;
import mchorse.emoticons.common.emotes.Emotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom emote sounds
 *
 * Plays MP3 files from "config/emoticons/sounds/" when an emote starts. Which
 * file goes with which emote is set in "config/emoticons/sounds.json". The
 * file is checked again every time an emote starts, so changes apply without
 * restarting the game.
 *
 * Everything here is client side: every player hears the sounds from their
 * own files, nothing is sent over the network.
 */
@SideOnly(Side.CLIENT)
public class EmoteSounds
{
    private static final int MAX_PLAYING = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String DEFAULT_CONFIG = "{\n"
        + "  \"_help\": \"Put .mp3 files into config/emoticons/sounds/ and list them under \\\"emotes\\\" by emote name (see _example). Changes apply the next time an emote starts. category: which volume slider it follows (master, music, records, weather, blocks, hostile, neutral, players, ambient, voice). range: how many blocks away it can be heard. loop: repeat the file until the emote ends. start: seconds to skip at the start of the file. A listed emote replaces its original sound.\",\n"
        + "  \"category\": \"records\",\n"
        + "  \"master_volume\": 1.0,\n"
        + "  \"default_range\": 24.0,\n"
        + "  \"emotes\": {},\n"
        + "  \"_example\": {\n"
        + "    \"floss\": {\"file\": \"my_song.mp3\", \"volume\": 0.8, \"range\": 20.0, \"loop\": false, \"start\": 0.0}\n"
        + "  }\n"
        + "}\n";

    private static Config config = new Config();
    private static boolean namesPrinted;
    private static long lastCheck;
    private static long lastModified = -1;

    private static final Map<Integer, Playing> playing = new HashMap<Integer, Playing>();
    private static final Set<String> warned = new HashSet<String>();

    public static class Config
    {
        public String category = "records";
        public float master_volume = 1;
        public float default_range = 24;
        public Map<String, Entry> emotes = new LinkedHashMap<String, Entry>();
    }

    public static class Entry
    {
        public String file = "";
        public float volume = 1;
        public float range = -1;
        public boolean loop = false;
        public float start = 0;
    }

    private static class Playing
    {
        public EntityLivingBase entity;
        public Mp3Player player;
        public Entry entry;
    }

    /**
     * Create the folder and sounds.json if they don't exist yet
     */
    public static void init()
    {
        getSoundsFolder().mkdirs();
        checkConfig();
    }

    /* Config */

    public static File getFolder()
    {
        return new File(Loader.instance().getConfigDir(), "emoticons");
    }

    public static File getSoundsFolder()
    {
        return new File(getFolder(), "sounds");
    }

    private static void warnOnce(String message)
    {
        if (warned.add(message))
        {
            System.err.println("[Emoticons] " + message);
        }
    }

    /**
     * Read sounds.json (again, if it changed). It's looked at once a second
     * at most.
     */
    private static void checkConfig()
    {
        long now = System.currentTimeMillis();

        if (now - lastCheck < 1000)
        {
            return;
        }

        lastCheck = now;

        File file = new File(getFolder(), "sounds.json");

        try
        {
            if (!file.exists())
            {
                getSoundsFolder().mkdirs();

                Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);

                try
                {
                    writer.write(DEFAULT_CONFIG);
                }
                finally
                {
                    writer.close();
                }
            }

            /* Emotes register after the game starts, so print their names later */
            if (!namesPrinted && !Emotes.EMOTES.isEmpty())
            {
                namesPrinted = true;

                System.out.println("[Emoticons] Emote names for sounds.json: " + getEmoteNames());
            }

            if (file.lastModified() == lastModified)
            {
                return;
            }

            lastModified = file.lastModified();

            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            Config loaded;

            try
            {
                loaded = GSON.fromJson(reader, Config.class);
            }
            finally
            {
                reader.close();
            }

            if (loaded == null)
            {
                loaded = new Config();
            }

            /* Emote names are case insensitive */
            Map<String, Entry> emotes = new HashMap<String, Entry>();

            if (loaded.emotes != null)
            {
                for (Map.Entry<String, Entry> entry : loaded.emotes.entrySet())
                {
                    if (entry.getValue() != null)
                    {
                        emotes.put(entry.getKey().toLowerCase(), entry.getValue());
                    }
                }
            }

            loaded.emotes = emotes;
            config = loaded;
            warned.clear();

            System.out.println("[Emoticons] Loaded " + emotes.size() + " custom emote sound(s) from " + file.getPath());
        }
        catch (Exception e)
        {
            warnOnce("Couldn't read " + file.getPath() + " (keeping the previous settings): " + e);
        }
    }

    private static String getEmoteNames()
    {
        List<String> names = new ArrayList<String>(Emotes.EMOTES.keySet());

        Collections.sort(names);

        return names.toString();
    }

    /**
     * Find the mp3 file of an entry, only inside the sounds folder
     */
    private static File resolve(String name, String emote)
    {
        try
        {
            File folder = getSoundsFolder().getCanonicalFile();
            File file = new File(folder, name).getCanonicalFile();

            if (!file.getPath().startsWith(folder.getPath() + File.separator))
            {
                warnOnce("Sound of '" + emote + "' must be inside " + folder.getPath() + ": " + name);

                return null;
            }

            if (!name.toLowerCase().endsWith(".mp3"))
            {
                warnOnce("Sound of '" + emote + "' must be an .mp3 file: " + name);

                return null;
            }

            if (!file.isFile())
            {
                warnOnce("Sound of '" + emote + "' not found: " + file.getPath());

                return null;
            }

            return file;
        }
        catch (Exception e)
        {
            warnOnce("Bad sound path for '" + emote + "': " + e);

            return null;
        }
    }

    private static Entry getEntry(String emote)
    {
        checkConfig();

        Entry entry = config.emotes.get(emote.toLowerCase());

        if (entry == null || entry.file == null || entry.file.isEmpty())
        {
            return null;
        }

        return resolve(entry.file, emote) == null ? null : entry;
    }

    /**
     * Whether the emote has a custom sound that replaces its original one
     */
    public static boolean hasCustom(Emote emote)
    {
        return emote != null && getEntry(emote.name) != null;
    }

    /* Playback */

    /**
     * Called when an entity's emote changes (null when it stops)
     */
    public static synchronized void onEmote(EntityLivingBase entity, Emote emote)
    {
        stop(entity.getEntityId());

        if (emote == null)
        {
            return;
        }

        Entry entry = getEntry(emote.name);

        if (entry == null)
        {
            return;
        }

        if (playing.size() >= MAX_PLAYING)
        {
            warnOnce("Too many custom emote sounds playing, skipping " + emote.name);

            return;
        }

        File file = resolve(entry.file, emote.name);

        if (file == null)
        {
            return;
        }

        Playing p = new Playing();

        p.entity = entity;
        p.entry = entry;
        p.player = new Mp3Player(file, entry.loop, entry.start, new JavaSoundSink());

        playing.put(entity.getEntityId(), p);

        /* Set the volume before it starts so there's no blast */
        update(p, Minecraft.getMinecraft());

        p.player.play();
    }

    private static void stop(int id)
    {
        Playing p = playing.remove(id);

        if (p != null)
        {
            p.player.stop();
        }
    }

    public static synchronized void stopAll()
    {
        for (Playing p : playing.values())
        {
            p.player.stop();
        }

        playing.clear();
    }

    /**
     * Called every client tick
     */
    public static synchronized void tick()
    {
        if (playing.isEmpty())
        {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.world == null || mc.player == null)
        {
            stopAll();

            return;
        }

        Iterator<Playing> it = playing.values().iterator();

        while (it.hasNext())
        {
            Playing p = it.next();

            if (p.player.isFinished() || p.entity.isDead || p.entity.getHealth() <= 0 || p.entity.world != mc.world)
            {
                p.player.stop();
                it.remove();

                continue;
            }

            p.player.setPaused(mc.isGamePaused());
            update(p, mc);
        }
    }

    /**
     * Find a sound category by its name (master, music, records, weather,
     * blocks, hostile, neutral, players, ambient or voice), records if it's
     * not one of them
     */
    private static SoundCategory parseCategory(String name)
    {
        if (name != null)
        {
            for (SoundCategory category : SoundCategory.values())
            {
                if (category.name().equalsIgnoreCase(name) || category.getName().equalsIgnoreCase(name))
                {
                    return category;
                }
            }
        }

        return SoundCategory.RECORDS;
    }

    /**
     * Set the volume of each ear based on the distance and the direction of
     * the entity, and on the volume settings of the game
     */
    private static void update(Playing p, Minecraft mc)
    {
        Entity listener = mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.player;

        if (listener == null)
        {
            return;
        }

        double dx = p.entity.posX - listener.posX;
        double dy = p.entity.posY - listener.posY;
        double dz = p.entity.posZ - listener.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float range = p.entry.range > 0 ? p.entry.range : config.default_range;

        if (range <= 0)
        {
            range = 24;
        }

        float attenuation = distance >= range ? 0 : 1 - (float) (distance / range);

        /* Volume sliders of the game */
        GameSettings settings = mc.gameSettings;
        SoundCategory category = parseCategory(config.category);

        float volume = settings.getSoundLevel(SoundCategory.MASTER);

        if (category != SoundCategory.MASTER)
        {
            volume *= settings.getSoundLevel(category);
        }

        volume *= config.master_volume * p.entry.volume * attenuation;

        /* Pan: -1 is fully to the left, 1 is fully to the right */
        float pan = 0;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal > 0.01)
        {
            double yaw = Math.toRadians(listener.rotationYaw);
            double rightX = -Math.cos(yaw);
            double rightZ = -Math.sin(yaw);

            pan = (float) ((dx * rightX + dz * rightZ) / horizontal);

            /* No panning when it's right next to you */
            pan *= (float) Math.min(1, distance / 3);
        }

        float left = volume * (1 - Math.max(0, pan) * 0.8F);
        float right = volume * (1 + Math.min(0, pan) * 0.8F);

        p.player.setGain(left, right);
    }
}
