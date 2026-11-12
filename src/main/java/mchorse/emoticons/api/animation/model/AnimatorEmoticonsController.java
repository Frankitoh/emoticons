package mchorse.emoticons.api.animation.model;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector4f;

import com.google.common.collect.Maps;

import mchorse.emoticons.common.emotes.Emote;
import mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh;
import mchorse.emoticons.skin_n_bones.api.animation.AnimationMeshConfig;
import mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorController;
import mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorHeldItemConfig;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.mclib.client.render.RenderLightmap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


@SideOnly(Side.CLIENT)
public class AnimatorEmoticonsController extends AnimatorController
{
    private static final Map<String, ResourceLocation> ARMOR_TEXTURE_RES_MAP = Maps.<String, ResourceLocation>newHashMap();

    /**
     * Default armor models, the same ones vanilla's LayerBipedArmor passes to
     * Forge. If Forge hands back one of these, the item has no custom model.
     */
    private static final ModelBiped DEFAULT_LEGS = new ModelBiped(0.5F);
    private static final ModelBiped DEFAULT_ARMOR = new ModelBiped(1.0F);

    /**
     * Custom armor model classes that were already logged (log once per class)
     */
    private static final Set<Class> LOGGED_MODELS = new HashSet<Class>();

    /**
     * Armor pieces that come with their own model (skirts, etc.). They can't
     * be drawn with the BOBJ armor meshes, so they're drawn separately.
     */
    private final Map<EntityEquipmentSlot, CustomArmor> customArmor = new EnumMap<EntityEquipmentSlot, CustomArmor>(EntityEquipmentSlot.class);

    private static boolean renderFailed;

    private static class CustomArmor
    {
        public EntityEquipmentSlot slot;
        public ModelBiped model;
        public ResourceLocation texture;
        public int color;
    }

    private static final EntityEquipmentSlot[] ARMOR_SLOTS = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};

    public ItemStack itemSlot = ItemStack.EMPTY;
    public float itemSlotScale = 0;

    public AnimatorEmoticonsController(String animationName, NBTTagCompound userData)
    {
        super(animationName, userData);
    }

    public void render(Emote emote, EntityLivingBase entity, double x, double y, double z, int i, float partialTicks)
    {
        this.e = emote;
        this.render(entity, x, y, z, i, partialTicks);
        this.e = null;
    }

    /**
     * Render current animation
     */
    @Override
    public void renderAnimation(EntityLivingBase entity, AnimationMesh mesh, float yaw, float partialTicks)
    {
        this.updateArmor(entity);

        super.renderAnimation(entity, mesh, yaw, partialTicks);

        BOBJArmature original = mesh.getArmature();
        BOBJArmature armature = this.animator.useArmature(original);

        this.renderCustomArmor(entity, armature, partialTicks);

        if (this.e != null && this.emote != null)
        {
            this.e.renderEmote(entity, armature, this, (int) this.emote.getTick(0), partialTicks);
        }
    }

    /**
     * Render hand held items and skin layer
     */
    @Override
    protected void renderItems(EntityLivingBase entity, BOBJArmature armature)
    {
        if (!this.userConfig.renderHeldItems)
        {
            return;
        }

        float scale = this.userConfig.scaleItems;

        ItemStack mainItem = entity.getHeldItemMainhand();
        ItemStack offItem = entity.getHeldItemOffhand();

        if (!this.itemSlot.isEmpty())
        {
            if (this.itemSlotScale > 0)
            {
                for (AnimatorHeldItemConfig itemConfig : this.userConfig.rightHands.values())
                {
                    this.renderItem(entity, this.itemSlot, armature, itemConfig, TransformType.THIRD_PERSON_RIGHT_HAND, scale * this.itemSlotScale);
                }
            }
        }
        else if (!mainItem.isEmpty() && this.userConfig.rightHands != null)
        {
            for (AnimatorHeldItemConfig itemConfig : this.userConfig.rightHands.values())
            {
                this.renderItem(entity, mainItem, armature, itemConfig, TransformType.THIRD_PERSON_RIGHT_HAND, scale);
            }
        }

        if (!offItem.isEmpty() && this.userConfig.leftHands != null)
        {
            for (AnimatorHeldItemConfig itemConfig : this.userConfig.leftHands.values())
            {
                this.renderItem(entity, offItem, armature, itemConfig, TransformType.THIRD_PERSON_LEFT_HAND, scale);
            }
        }
    }

    /**
     * Update armor slots
     */
    private void updateArmor(EntityLivingBase entity)
    {
        this.customArmor.clear();

        this.updateArmorSlot("armor_helmet", entity, EntityEquipmentSlot.HEAD);
        this.updateArmorSlot("armor_chest", entity, EntityEquipmentSlot.CHEST);
        this.updateArmorSlot("armor_leggings", entity, EntityEquipmentSlot.LEGS);
        this.updateArmorSlot("armor_feet", entity, EntityEquipmentSlot.FEET);
    }

    /**
     * Update separate armor slots
     */
    private void updateArmorSlot(String id, EntityLivingBase entity, EntityEquipmentSlot slot)
    {
        AnimationMeshConfig config = this.userConfig.meshes.get(id);

        if (config == null)
        {
            return;
        }

        if (this.userData.hasKey("Meshes", Constants.NBT.TAG_COMPOUND) && this.userData.getCompoundTag("Meshes").hasKey(id))
        {
            return;
        }

        ItemStack stack = entity.getItemStackFromSlot(slot);

        if (stack.getItem() instanceof ItemArmor)
        {
            ItemArmor item = (ItemArmor) stack.getItem();

            config.visible = true;
            config.texture = this.getArmorResource(entity, stack, slot, null);
            config.color = 0xffffffff;

            if (item.hasOverlay(stack))
            {
                config.color = item.getColor(stack);
            }

            /* Armor with its own model (skirts from other mods and so on)
             * can't be drawn with the BOBJ armor meshes, it would look like
             * regular pants. Don't draw it at all. */
            ModelBiped custom = getCustomModel(entity, stack, slot);

            if (custom != null)
            {
                config.visible = false;

                CustomArmor armor = new CustomArmor();

                armor.slot = slot;
                armor.model = custom;
                armor.texture = config.texture;
                armor.color = config.color;

                this.customArmor.put(slot, armor);

                if (LOGGED_MODELS.add(custom.getClass()))
                {
                    System.out.println("[Emoticons] Custom armor model: " + custom.getClass().getName() + " (" + stack.getItem().getRegistryName() + ") slot " + slot);
                    dumpModel(custom);
                }
            }
        }
        else
        {
            config.visible = false;
            config.color = 0xffffffff;
        }
    }

    /**
     * Ask Forge for the armor model of the item. Returns null when the item
     * doesn't override the default model.
     */
    private static ModelBiped getCustomModel(EntityLivingBase entity, ItemStack stack, EntityEquipmentSlot slot)
    {
        ModelBiped def = slot == EntityEquipmentSlot.LEGS ? DEFAULT_LEGS : DEFAULT_ARMOR;
        ModelBiped model = net.minecraftforge.client.ForgeHooksClient.getArmorModel(entity, stack, slot, def);

        return model == def ? null : model;
    }

    /**
     * Whether the entity wears armor that comes with its own model (skirts
     * from other mods and so on). Emoticons can't draw those, so they only
     * show up when the game itself renders the player.
     */
    public static boolean hasCustomArmorModel(EntityLivingBase entity)
    {
        for (EntityEquipmentSlot slot : ARMOR_SLOTS)
        {
            ItemStack stack = entity.getItemStackFromSlot(slot);

            if (stack.getItem() instanceof ItemArmor && getCustomModel(entity, stack, slot) != null)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Draw armor pieces that have a custom model. Only the body part of the
     * model (with its children and anything the model draws on its own) is
     * attached to the body bone. Legs, arms and head are hidden, because the
     * rigid boxes don't follow the bending legs of the Emoticons model.
     */
    private void renderCustomArmor(EntityLivingBase entity, BOBJArmature armature, float partialTicks)
    {
        if (this.customArmor.isEmpty() || entity.isInvisible() || renderFailed)
        {
            return;
        }

        BOBJBone body = armature.bones.get("body");
        BOBJBone anchor = armature.bones.get("anchor");

        if (body == null)
        {
            return;
        }

        boolean flag = RenderLightmap.set(entity, partialTicks);

        GlStateManager.enableRescaleNormal();

        for (CustomArmor armor : this.customArmor.values())
        {
            ModelBiped model = armor.model;

            GlStateManager.pushMatrix();

            try
            {
                this.prepareModel(model, armor.slot, entity);

                Minecraft.getMinecraft().renderEngine.bindTexture(armor.texture);
                GlStateManager.color((armor.color >> 16 & 255) / 255.0F, (armor.color >> 8 & 255) / 255.0F, (armor.color & 255) / 255.0F, 1);

                /* BOBJ model: Y up, front looks toward +Z, feet at Y = 0.
                 * Biped model: Y down, front looks toward -Z, feet at 24 px */
                this.setupHangingMatrix(armature, body, anchor);
                GlStateManager.translate(0, 1.5F, 0);
                GlStateManager.scale(1, -1, -1);

                model.render(entity, 0, 0, 0, 0, 0, 0.0625F);
            }
            catch (Exception e)
            {
                renderFailed = true;
                e.printStackTrace();
            }

            GlStateManager.popMatrix();
        }

        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.disableRescaleNormal();

        if (flag)
        {
            RenderLightmap.unset();
        }
    }

    /**
     * Setup the matrix of something that hangs from the hips, like a skirt.
     * It follows the position of the hips (body bone), but only the rotation
     * of the root bone, so it doesn't swing out when the torso leans.
     */
    private void setupHangingMatrix(BOBJArmature armature, BOBJBone body, BOBJBone anchor)
    {
        /* Rest position of the hips (head of the body bone) */
        float hx = body.boneMat.m03;
        float hy = body.boneMat.m13;
        float hz = body.boneMat.m23;

        Vector4f hip = new Vector4f(hx, hy, hz, 1);

        armature.matrices[body.index].transform(hip);
        GlStateManager.translate(hip.x, hip.y, hip.z);

        if (anchor != null)
        {
            Matrix4f rotation = new Matrix4f(armature.matrices[anchor.index]);

            rotation.m03 = 0;
            rotation.m13 = 0;
            rotation.m23 = 0;

            this.setupMatrix(rotation);
        }

        GlStateManager.translate(-hx, -hy, -hz);
    }

    /**
     * Put the model into its rest pose and show only the body
     */
    private void prepareModel(ModelBiped model, EntityEquipmentSlot slot, EntityLivingBase entity)
    {
        model.isSneak = false;
        model.isRiding = false;
        model.isChild = false;
        model.swingProgress = 0;
        model.leftArmPose = ModelBiped.ArmPose.EMPTY;
        model.rightArmPose = ModelBiped.ArmPose.EMPTY;

        model.setRotationAngles(0, 0, 0, 0, 0, 0.0625F, entity);

        resetRotation(model.bipedHead);
        resetRotation(model.bipedHeadwear);
        resetRotation(model.bipedBody);
        resetRotation(model.bipedRightArm);
        resetRotation(model.bipedLeftArm);
        resetRotation(model.bipedRightLeg);
        resetRotation(model.bipedLeftLeg);

        model.setVisible(false);
        model.bipedBody.showModel = slot == EntityEquipmentSlot.LEGS || slot == EntityEquipmentSlot.CHEST;
    }

    private static void resetRotation(ModelRenderer part)
    {
        part.rotateAngleX = part.rotateAngleY = part.rotateAngleZ = 0;
    }

    /**
     * Print the structure of a custom armor model into the log, to see what
     * it's made of
     */
    private static void dumpModel(ModelBiped model)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("[Emoticons] Model dump of ").append(model.getClass().getName()).append(" (extends ").append(model.getClass().getSuperclass().getName()).append(")\n");

        dumpPart(sb, "bipedHead", model.bipedHead, 1);
        dumpPart(sb, "bipedHeadwear", model.bipedHeadwear, 1);
        dumpPart(sb, "bipedBody", model.bipedBody, 1);
        dumpPart(sb, "bipedRightArm", model.bipedRightArm, 1);
        dumpPart(sb, "bipedLeftArm", model.bipedLeftArm, 1);
        dumpPart(sb, "bipedRightLeg", model.bipedRightLeg, 1);
        dumpPart(sb, "bipedLeftLeg", model.bipedLeftLeg, 1);

        for (Class c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass())
        {
            if (c.getName().startsWith("net.minecraft"))
            {
                continue;
            }

            for (Field field : c.getDeclaredFields())
            {
                if (Modifier.isStatic(field.getModifiers()))
                {
                    continue;
                }

                try
                {
                    field.setAccessible(true);

                    Object value = field.get(model);

                    if (value instanceof ModelRenderer)
                    {
                        dumpPart(sb, "extra " + c.getSimpleName() + "." + field.getName(), (ModelRenderer) value, 1);
                    }
                    else if (value instanceof ModelRenderer[])
                    {
                        ModelRenderer[] array = (ModelRenderer[]) value;

                        for (int i = 0; i < array.length; i++)
                        {
                            dumpPart(sb, "extra " + c.getSimpleName() + "." + field.getName() + "[" + i + "]", array[i], 1);
                        }
                    }
                }
                catch (Exception e)
                {}
            }
        }

        System.out.println(sb.toString());
    }

    private static void dumpPart(StringBuilder sb, String name, ModelRenderer part, int depth)
    {
        for (int i = 0; i < depth; i++)
        {
            sb.append("  ");
        }

        if (part == null)
        {
            sb.append(name).append(": null\n");

            return;
        }

        sb.append(name).append(String.format(": show=%s hidden=%s point=(%.1f, %.1f, %.1f) offset=(%.2f, %.2f, %.2f) rot=(%.2f, %.2f, %.2f)",
            part.showModel, part.isHidden, part.rotationPointX, part.rotationPointY, part.rotationPointZ,
            part.offsetX, part.offsetY, part.offsetZ, part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ));

        if (part.cubeList != null)
        {
            for (ModelBox box : part.cubeList)
            {
                sb.append(String.format(" box[(%.1f, %.1f, %.1f) -> (%.1f, %.1f, %.1f)]", box.posX1, box.posY1, box.posZ1, box.posX2, box.posY2, box.posZ2));
            }
        }

        sb.append("\n");

        if (part.childModels != null)
        {
            for (ModelRenderer child : part.childModels)
            {
                dumpPart(sb, "child", child, depth + 1);
            }
        }
    }

    /**
     * More generic ForgeHook version of the above function, it allows for Items to have more control over what texture they provide.
     *
     * @param entity Entity wearing the armor
     * @param stack ItemStack for the armor
     * @param slot Slot ID that the item is in
     * @param type Subtype, can be null or "overlay"
     * @return ResourceLocation pointing at the armor's texture
     */
    private ResourceLocation getArmorResource(net.minecraft.entity.Entity entity, ItemStack stack, EntityEquipmentSlot slot, String type)
    {
        ItemArmor item = (ItemArmor) stack.getItem();
        String texture = item.getArmorMaterial().getName();
        String domain = "minecraft";
        int idx = texture.indexOf(':');
        if (idx != -1)
        {
            domain = texture.substring(0, idx);
            texture = texture.substring(idx + 1);
        }
        String s1 = String.format("%s:textures/models/armor/%s_layer_%d%s.png", domain, texture, (isLegSlot(slot) ? 2 : 1), type == null ? "" : String.format("_%s", type));

        s1 = net.minecraftforge.client.ForgeHooksClient.getArmorTexture(entity, stack, s1, slot, type);
        ResourceLocation resourcelocation = ARMOR_TEXTURE_RES_MAP.get(s1);

        if (resourcelocation == null)
        {
            resourcelocation = new ResourceLocation(s1);
            ARMOR_TEXTURE_RES_MAP.put(s1, resourcelocation);
        }

        return resourcelocation;
    }

    private boolean isLegSlot(EntityEquipmentSlot slotIn)
    {
        return slotIn == EntityEquipmentSlot.LEGS;
    }
}
